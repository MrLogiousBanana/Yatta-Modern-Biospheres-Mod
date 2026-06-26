package xyz.yatta.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.util.RandomSource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class BiospheresChunkGenerator extends ChunkGenerator {

	public static final MapCodec<BiospheresChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
			.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
					Codec.LONG.fieldOf("seed").forGetter((generator) -> generator.seed),
					Codec.INT.fieldOf("sphere_distance").forGetter((generator) -> generator.sphereDistance),
					Codec.INT.fieldOf("sphere_radius").forGetter((generator) -> generator.sphereRadius),
					Codec.INT.fieldOf("lake_radius").forGetter((generator) -> generator.lakeRadius),
					Codec.INT.fieldOf("shore_radius").forGetter((generator) -> generator.shoreRadius))
			.apply(instance, instance.stable(BiospheresChunkGenerator::new)));

	protected final long seed;
	protected final int sphereDistance;
	protected final int sphereRadius;
	protected final int oreSphereRadius;
	protected final int lakeRadius;
	protected final int shoreRadius;
	protected final net.minecraft.world.level.levelgen.WorldgenRandom chunkRandom;
	protected final net.minecraft.world.level.levelgen.synth.NormalNoise noiseSampler;
	protected final BlockState defaultBlock;
	protected final BlockState defaultNetherBlock;
	protected final BlockState defaultFluid;
	protected final BlockState defaultBridge;
	protected final BlockState defaultEdge;
	protected double generatedSphereHeight;
	private Long actualSeed = null;

	private long extractSeedFromRandomState(Object randomState) {
		if (randomState == null) return 0;
		for (java.lang.reflect.Field f : randomState.getClass().getDeclaredFields()) {
			if (f.getType() == long.class) {
				try {
					f.setAccessible(true);
					long val = f.getLong(randomState);
					if (val != 0) return val;
				} catch (Exception ignored) {}
			}
		}
		return 0;
	}

	private long getActualSeed() {
		return this.actualSeed != null ? this.actualSeed : this.seed;
	}

	public static boolean isSlimeSphere(int centerX, int centerZ, long worldSeed) {
		long sphereSeed = worldSeed ^ (centerX * 341873128712L) ^ (centerZ * 132897987541L);
		java.util.Random r = new java.util.Random(sphereSeed);
		return r.nextFloat() < 0.01f;
	}

	public BiospheresChunkGenerator(BiomeSource biomeSource, long seed, int sphereDistance, int sphereRadius,
			int lakeRadius, int shoreRadius) {
		super(biomeSource);
		this.seed = seed;
		this.sphereDistance = sphereDistance;
		this.sphereRadius = sphereRadius;
		this.oreSphereRadius = 8;
		this.lakeRadius = 8;
		this.shoreRadius = shoreRadius;
		this.defaultBlock = Blocks.STONE.defaultBlockState();
		this.defaultNetherBlock = Blocks.NETHERRACK.defaultBlockState();
		this.defaultFluid = Blocks.WATER.defaultBlockState();
		this.defaultBridge = Blocks.OAK_PLANKS.defaultBlockState();
		this.defaultEdge = Blocks.OAK_FENCE.defaultBlockState();
		this.chunkRandom = new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed));
		this.chunkRandom.consumeCount(1000);
		// In 1.21.1 net.minecraft.world.level.levelgen.synth.NormalNoise is created via create() method
		this.noiseSampler = net.minecraft.world.level.levelgen.synth.NormalNoise.create(this.chunkRandom, -3, 1.0, 1.0, 1.0, 1.0);
		this.generatedSphereHeight = this.sphereRadius;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public void buildSurface(net.minecraft.server.level.WorldGenRegion region, net.minecraft.world.level.StructureManager structures, net.minecraft.world.level.levelgen.RandomState noiseConfig, net.minecraft.world.level.chunk.ChunkAccess chunk) {
		// Manual surface building in populateNoise
	}

	public BlockState getLakeBlock(BlockPos center, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		java.util.Random r = new java.util.Random(this.getActualSeed() + (long)center.getX() * 341873128712L + (long)center.getZ() * 132897987541L);
		int rng = r.nextInt(100);
		BlockState state = Blocks.AIR.defaultBlockState();
		if (rng < 40) {
			if (biome.is(BiomeTags.IS_NETHER)) {
				state = Blocks.LAVA.defaultBlockState();
			} else if (rng >= 30 && biome.value().getBaseTemperature() >= 1.0f) {
				state = Blocks.LAVA.defaultBlockState();
			} else {
				state = this.defaultFluid;
			}
		}
		return state;
	}

	private BlockState randomOreBlock(int x, int y, int z) {
		int cx = Math.floorDiv(x, 4);
		int cy = Math.floorDiv(y, 4);
		int cz = Math.floorDiv(z, 4);
		long cellSeed = this.getActualSeed() ^ (cx * 341873128712L) ^ (cy * 132897987541L) ^ (cz * 543897123984L);
		cellSeed ^= (cellSeed >>> 16);
		int targetX = cx * 4 + 1 + Math.abs((int)cellSeed % 2);
		int targetY = cy * 4 + 1 + Math.abs((int)(cellSeed >> 8) % 2);
		int targetZ = cz * 4 + 1 + Math.abs((int)(cellSeed >> 16) % 2);

		if (x == targetX && y == targetY && z == targetZ) {
			if (this.chunkRandom.nextFloat() < 0.7f) {
				return net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState();
			}
		}
		
		boolean isDeepslate = this.chunkRandom.nextBoolean();
		float rngChance = this.chunkRandom.nextFloat();
		
		if (rngChance < 0.5f) {
			return isDeepslate ? net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState() : defaultBlock;
		} else if (rngChance < 0.7f) {
			return net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState();
		}
		
		String targetPath = isDeepslate ? "ores_in_ground/deepslate" : "ores_in_ground/stone";
		java.util.Optional<net.minecraft.core.HolderSet.Named<net.minecraft.world.level.block.Block>> oresC = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", targetPath)));
		java.util.List<net.minecraft.world.level.block.Block> allOres = new java.util.ArrayList<>();
		if (oresC.isPresent()) {
			for (net.minecraft.core.Holder<net.minecraft.world.level.block.Block> holder : oresC.get()) allOres.add(holder.value());
		}
		
		if (!allOres.isEmpty()) {
			int idx = this.chunkRandom.nextInt(allOres.size());
			return allOres.get(idx).defaultBlockState();
		}
		
		int rng = this.chunkRandom.nextInt(16);
		BlockState ore;
		if (rng == 1) ore = net.minecraft.world.level.block.Blocks.EMERALD_ORE.defaultBlockState();
		else if (rng > 1 && rng <= 3) ore = net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState();
		else if (rng > 3 && rng <= 5) ore = net.minecraft.world.level.block.Blocks.LAPIS_ORE.defaultBlockState();
		else if (rng > 5 && rng <= 7) ore = net.minecraft.world.level.block.Blocks.REDSTONE_ORE.defaultBlockState();
		else if (rng > 7 && rng <= 10) ore = net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState();
		else ore = net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
		
		if (isDeepslate) {
			if (ore.is(net.minecraft.world.level.block.Blocks.EMERALD_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
			if (ore.is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
			if (ore.is(net.minecraft.world.level.block.Blocks.LAPIS_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
			if (ore.is(net.minecraft.world.level.block.Blocks.REDSTONE_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
			if (ore.is(net.minecraft.world.level.block.Blocks.GOLD_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
			if (ore.is(net.minecraft.world.level.block.Blocks.IRON_ORE)) return net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
		}
		
		return ore;
	}

	private BlockState randomCaveOre(int y) {
		float rng = this.chunkRandom.nextFloat();
		if (y < 0) {
			if (rng < 0.2f) return net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
			if (rng < 0.5f) return net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
			if (rng < 0.8f) return net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
			return net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
		} else {
			if (rng < 0.4f) return net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
			if (rng < 0.8f) return net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState();
			if (rng < 0.9f) return net.minecraft.world.level.block.Blocks.COPPER_ORE.defaultBlockState();
			return net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState();
		}
	}

	public double getSphereRadius(int centerX, int centerZ) {
		double maxRadius = this.sphereRadius * 1.1;
		double minRadius = this.sphereRadius * 0.4;
		java.util.Random random = new java.util.Random(this.getActualSeed() + 1L + (long) centerX * 341873128712L + (long) centerZ * 132897987541L);
		return random.nextDouble() * (maxRadius - minRadius) + minRadius;
	}

	private long extractSeedFromStructureManager(net.minecraft.world.level.StructureManager sm) {
		if (sm == null) return 0;
		try {
			for (java.lang.reflect.Field f : sm.getClass().getDeclaredFields()) {
				try {
					f.setAccessible(true);
					Object obj = f.get(sm);
					if (obj instanceof net.minecraft.world.level.ServerLevelAccessor sla) {
						long s = sla.getLevel().getSeed();
						if (s != 0) return s;
					}
				} catch (Exception ignored) {}
			}
		} catch (Exception e) {}
		return 0;
	}

	@Override
	public java.util.concurrent.CompletableFuture<net.minecraft.world.level.chunk.ChunkAccess> createBiomes(net.minecraft.world.level.levelgen.RandomState randomState, net.minecraft.world.level.levelgen.blending.Blender blender, net.minecraft.world.level.StructureManager structureManager, net.minecraft.world.level.chunk.ChunkAccess chunkAccess) {
		if (this.actualSeed == null) {
			long s = extractSeedFromStructureManager(structureManager);
			if (s == 0) {
				try {
					long rsSeed = randomState.getOrCreateRandomFactory(net.minecraft.resources.ResourceLocation.parse("biospheres:seed")).at(0, 0, 0).nextLong();
					if (rsSeed != 0) s = rsSeed;
				} catch (Exception ignored) {}
			}
			if (s != 0) {
				this.actualSeed = s;
				if (this.getBiomeSource() instanceof BiospheresBiomeSource bbs) {
					bbs.setWorldSeed(s);
				}
			}
		}
		return super.createBiomes(randomState, blender, structureManager, chunkAccess);
	}

	@Override
	public CompletableFuture<net.minecraft.world.level.chunk.ChunkAccess> fillFromNoise(Blender blender, net.minecraft.world.level.levelgen.RandomState noiseConfig, net.minecraft.world.level.StructureManager structureAccessor, net.minecraft.world.level.chunk.ChunkAccess chunk) {
		if (this.actualSeed == null) {
			long worldSeed = this.seed;
			boolean seedFound = false;
			if (this.getBiomeSource() instanceof BiospheresBiomeSource bbs) {
				if (bbs.hasActualSeed()) {
					worldSeed = bbs.getActualSeed();
					seedFound = true;
				}
			}
			
			if (!seedFound) {
				long s = extractSeedFromStructureManager(structureAccessor);
				if (s == 0) {
					try {
						long rsSeed = noiseConfig.getOrCreateRandomFactory(net.minecraft.resources.ResourceLocation.parse("biospheres:seed")).at(0, 0, 0).nextLong();
						if (rsSeed != 0) s = rsSeed;
					} catch (Exception ignored) {}
				}
				if (s != 0) {
					worldSeed = s;
					seedFound = true;
				}
			}

			if (!seedFound) {
				try {
					long rsSeed = noiseConfig.getOrCreateRandomFactory(net.minecraft.resources.ResourceLocation.parse("biospheres:seed")).at(0, 0, 0).nextLong();
					if (rsSeed != 0) worldSeed = rsSeed;
					if (worldSeed == 0L) worldSeed = this.seed ^ 0xDEADBEEFL;
				} catch (Exception e2) {
					worldSeed = this.seed ^ 0xCAFEBABEL;
				}
			}
			this.actualSeed = worldSeed;
			if (this.getBiomeSource() instanceof BiospheresBiomeSource bbs) {
				bbs.setWorldSeed(this.actualSeed);
			}
		}

		ChunkPos chunkPos = chunk.getPos();
		net.minecraft.core.BlockPos.MutableBlockPos current = new net.minecraft.core.BlockPos.MutableBlockPos();
		int xPos = chunkPos.getMinBlockX();
		int zPos = chunkPos.getMinBlockZ();
		
		Heightmap oceanHeight = chunk.getOrCreateHeightmapUnprimed(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG);

		BlockPos lastCenterPos = null;
		net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = null;
		BlockState fluidBlock = null;
		double sRadius = 0;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int realX = xPos + x;
				int realZ = zPos + z;
				current.set(realX, 0, realZ);
				
				BlockPos centerPos = this.getNearestCenterSphere(current);
				if (!centerPos.equals(lastCenterPos)) {
					lastCenterPos = centerPos;
					biome = this.biomeSource.getNoiseBiome(centerPos.getX() >> 2, centerPos.getY() >> 2, centerPos.getZ() >> 2, noiseConfig.sampler());
					fluidBlock = this.getLakeBlock(centerPos, biome);
					sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());
				}
				
				double radialDistance = Math.sqrt(current.distSqr(new net.minecraft.core.Vec3i(centerPos.getX(), 0, centerPos.getZ())));

				if (radialDistance <= sRadius) {
					double noise = this.noiseSampler.getValue(realX / 8.0, 0, realZ / 8.0) / 16;
					double sphereHeight = Math.sqrt(sRadius * sRadius
							- Math.pow(centerPos.getX() - realX, 2)
							- Math.pow(realZ - centerPos.getZ(), 2));
					
					for (int y = centerPos.getY() - (int) sphereHeight; y <= centerPos.getY() + sphereHeight; y++) {
						double lakeDistance = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(realX, y, realZ)));
						double lakeDistance2d = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(realX, centerPos.getY(), realZ)));
						
						double noiseTemp = (noise + y / centerPos.getY());
						BlockState blockState = Blocks.AIR.defaultBlockState();
						
						if (y * noiseTemp < centerPos.getY()) {
							if (biome.is(BiomeTags.IS_NETHER)) {
								blockState = this.defaultNetherBlock;
							} else {
								blockState = this.defaultBlock;
							}
						}
						
						if ((blockState.equals(this.defaultBlock) || blockState.equals(this.defaultNetherBlock))
								&& lakeDistance2d <= this.lakeRadius && !fluidBlock.isAir()) {
							if (y >= centerPos.getY() && (!fluidBlock.equals(Blocks.STONE.defaultBlockState()) && !fluidBlock.equals(Blocks.NETHERRACK.defaultBlockState()))) {
								blockState = Blocks.AIR.defaultBlockState();
							} else if (lakeDistance <= this.lakeRadius) {
								double belowDist = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(realX, y - 1, realZ)));
								if (belowDist > this.lakeRadius && fluidBlock.equals(net.minecraft.world.level.block.Blocks.WATER.defaultBlockState())) {
									java.util.Random lakeRandom = new java.util.Random(centerPos.asLong());
									boolean hasKelp = lakeRandom.nextBoolean();
									if (this.chunkRandom.nextInt(4) == 0) {
										if (hasKelp && this.chunkRandom.nextBoolean()) {
											blockState = net.minecraft.world.level.block.Blocks.KELP.defaultBlockState();
										} else {
											blockState = net.minecraft.world.level.block.Blocks.SEAGRASS.defaultBlockState();
										}
									} else {
										blockState = fluidBlock;
									}
								} else {
									blockState = fluidBlock;
								}
							}
						}

						// Manual surface layer replacement
						if (blockState.equals(this.defaultBlock) && !biome.is(BiomeTags.IS_NETHER)) {
							int depth = 0;
							while (depth < 4) {
								int testY = y + depth + 1;
								double testNoiseTemp = (noise + testY / centerPos.getY());
								if (testY * testNoiseTemp >= centerPos.getY()) {
									break;
								}
								depth++;
							}
							
							if (depth < 4) {
								double shoreDist = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(realX, centerPos.getY(), realZ)));
								boolean isShore = !fluidBlock.isAir() && shoreDist > this.lakeRadius && shoreDist <= this.lakeRadius + this.shoreRadius;
								boolean isLakeBottom = !fluidBlock.isAir() && shoreDist <= this.lakeRadius && y <= centerPos.getY();
								
								boolean isSandy = biome.is(net.minecraft.world.level.biome.Biomes.DESERT) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH) || biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "sandy"))) || biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("biospheres", "surface_sand")));
								boolean isRedSand = biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS) || biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("biospheres", "surface_red_sand")));
								boolean isMushroom = biome.is(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS) || biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("biospheres", "surface_mycelium")));
								boolean isMuddy = biome.is(net.minecraft.world.level.biome.Biomes.SWAMP) || biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP) || biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("biospheres", "surface_mud")));
								
								boolean isDripstone = biome.is(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES);
								boolean isLush = biome.is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES);
								boolean isDeepDark = biome.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK);
								boolean isCave = isCaveBiome(biome);
								
								float temp = biome.value().getBaseTemperature();
								
								if (depth == 0) {
									if (isDripstone) {
										blockState = net.minecraft.world.level.block.Blocks.DRIPSTONE_BLOCK.defaultBlockState();
									} else if (isLush) {
										blockState = net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState();
									} else if (isDeepDark) {
										blockState = net.minecraft.world.level.block.Blocks.SCULK.defaultBlockState();
									} else if (isCave) {
										blockState = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
									} else if (isShore || isLakeBottom) {
										blockState = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
									} else if (isMushroom) {
										blockState = net.minecraft.world.level.block.Blocks.MYCELIUM.defaultBlockState();
									} else if (isRedSand) {
										blockState = net.minecraft.world.level.block.Blocks.RED_SAND.defaultBlockState();
									} else if (isSandy) {
										blockState = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
									} else if (isMuddy) {
										blockState = net.minecraft.world.level.block.Blocks.MUD.defaultBlockState();
									} else if (temp < 0.15f) {
										blockState = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
									} else {
										blockState = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
									}
								} else {
									if (isDripstone || isCave) {
										blockState = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
									} else if (isLush) {
										blockState = net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState();
									} else if (isDeepDark) {
										blockState = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState();
									} else if (isShore || isSandy) {
										blockState = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
									} else if (isRedSand) {
										blockState = net.minecraft.world.level.block.Blocks.RED_SAND.defaultBlockState();
									} else if (isMuddy) {
										blockState = net.minecraft.world.level.block.Blocks.MUD.defaultBlockState();
									} else {
										blockState = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
									}
								}
							} else {
								// Generate clay blobs (similar to granite/andesite/diorite)
								double clayNoise = this.noiseSampler.getValue(realX / 10.0, y / 10.0 + 500, realZ / 10.0);
								if (clayNoise > 0.6) {
									blockState = net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState();
								}
							}
						}

						if (y >= this.getMinY() && y < this.getGenDepth() + this.getMinY()) {
							current.set(realX, y, realZ);
							chunk.setBlockState(current, blockState, false);
							oceanHeight.update(x, y, z, blockState);
							worldSurface.update(x, y, z, blockState);
						}
					}
				}

				// Ore spheres are handled directly by makeBridges and finishBiospheres now.
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	public BlockPos getNearestCenterSphere(BlockPos pos) {
		int xPos = pos.getX();
		int zPos = pos.getZ();
		int centerX = (int) Math.round(xPos / (double) this.sphereDistance) * this.sphereDistance;
		int centerZ = (int) Math.round(zPos / (double) this.sphereDistance) * this.sphereDistance;
		java.util.Random r = new java.util.Random(seed + (long)centerX * 341873128712L + (long)centerZ * 132897987541L);
		double sRadius = this.getSphereRadius(centerX, centerZ);
		// Reduced variance for Y coordinate: base 60, +/- 5 blocks
		int centerY = 60 + (int)(r.nextFloat() * 10 - 5);
		return new BlockPos(centerX, centerY, centerZ);
	}

	class OreSphere {
		public final BlockPos center;
		public final int radius;
		public final BlockPos bridgeAttachPoint;
		public OreSphere(BlockPos center, int radius, BlockPos bridgeAttachPoint) {
			this.center = center;
			this.radius = radius;
			this.bridgeAttachPoint = bridgeAttachPoint;
		}
	}

	public OreSphere getOreSphereForBridge(BlockPos sphereA, BlockPos sphereB) {
		BlockPos first = sphereA.getX() < sphereB.getX() || sphereA.getZ() < sphereB.getZ() ? sphereA : sphereB;
		BlockPos second = first == sphereA ? sphereB : sphereA;
		
		this.chunkRandom.setLargeFeatureSeed(this.getActualSeed(), first.getX() + second.getX(), first.getZ() + second.getZ());
		
		if (this.chunkRandom.nextFloat() > 0.20f) { // 20% chance to have an ore sphere
			return null; 
		}
		
		int radius = this.chunkRandom.nextInt(3) + 6; // radius 6 to 8
		
		double targetRadiusA = this.getSphereRadius(first.getX(), first.getZ());
		double targetRadiusB = this.getSphereRadius(second.getX(), second.getZ());
		double L = this.sphereDistance - targetRadiusA - targetRadiusB;
		if (L <= 0) return null;
		
		double t = this.chunkRandom.nextFloat() * 0.4 + 0.3; 
		
		double attachX = first.getX() == second.getX() ? first.getX() : first.getX() + targetRadiusA + t * L;
		double attachZ = first.getZ() == second.getZ() ? first.getZ() : first.getZ() + targetRadiusA + t * L;
		
		double baseY = (first.getY() + 1) + t * (second.getY() - first.getY());
		double sag = Math.sin(t * Math.PI) * (L / 12.0);
		double attachY = baseY - sag;
		
		double offsetPercentage = this.chunkRandom.nextFloat() * 0.2 + 0.1;
		double offsetBlocks = offsetPercentage * L;
		offsetBlocks = Math.max(radius + 12, offsetBlocks);
		if (this.chunkRandom.nextBoolean()) offsetBlocks = -offsetBlocks;
		
		double oreX = attachX;
		double oreZ = attachZ;
		
		if (first.getX() == second.getX()) {
			oreX += offsetBlocks;
			attachX += (offsetBlocks > 0) ? 2 : -2;
		} else {
			oreZ += offsetBlocks;
			attachZ += (offsetBlocks > 0) ? 2 : -2;
		}
		
		double oreY = attachY + (this.chunkRandom.nextFloat() * 0.4 - 0.2) * L; 
		
		return new OreSphere(new BlockPos((int)Math.round(oreX), (int)Math.round(oreY), (int)Math.round(oreZ)), radius, new BlockPos((int)Math.round(attachX), (int)Math.round(attachY), (int)Math.round(attachZ)));
	}

	@Override
	public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel world, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.world.level.StructureManager structureAccessor) {
		super.applyBiomeDecoration(world, chunk, structureAccessor);

		ChunkPos chunkPos = chunk.getPos();
		BlockPos centerPos = this.getNearestCenterSphere(new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ()));
		net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome;
		if (this.getBiomeSource() instanceof BiospheresBiomeSource) {
			biome = ((BiospheresBiomeSource) this.getBiomeSource()).getBiomeForSphere(centerPos.getX() >> 2, centerPos.getZ() >> 2);
		} else {
			biome = chunk.getNoiseBiome(centerPos.getX() >> 2, 0, centerPos.getZ() >> 2);
		}

		if (isCaveBiome(biome)) {
			net.minecraft.world.level.levelgen.XoroshiroRandomSource random = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(this.getActualSeed() ^ chunkPos.toLong());
			double sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());

			for (int i = 0; i < 20; i++) {
				int x = chunkPos.getMinBlockX() + random.nextInt(16);
				int z = chunkPos.getMinBlockZ() + random.nextInt(16);
				int y = this.getMinY() + random.nextInt(this.getGenDepth());

				if (centerPos.distSqr(new net.minecraft.core.Vec3i(x, y, z)) <= sRadius * sRadius) {
					BlockState oreState = getOreForHeight(y, random);
					if (oreState != null) {
						int veinSize = 3 + random.nextInt(5);
						net.minecraft.core.BlockPos.MutableBlockPos current = new net.minecraft.core.BlockPos.MutableBlockPos(x, y, z);
						for (int v = 0; v < veinSize; v++) {
							if (centerPos.distSqr(current) <= sRadius * sRadius) {
								BlockState state = chunk.getBlockState(current);
								if (state.is(net.minecraft.world.level.block.Blocks.STONE) || state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) {
									chunk.setBlockState(current, oreState, false);
								}
							}
							current.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
						}
					}
				}
			}
		}

		this.finishBiospheres(world, chunk);
	}

	private BlockState getOreForHeight(int y, net.minecraft.world.level.levelgen.XoroshiroRandomSource random) {
		if (y < 0) {
			int r = random.nextInt(100);
			if (r < 30) return net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
			if (r < 50) return net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
			if (r < 70) return net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
			if (r < 85) return net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
			if (r < 95) return net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
		} else if (y < 64) {
			int r = random.nextInt(100);
			if (r < 40) return net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
			if (r < 70) return net.minecraft.world.level.block.Blocks.COPPER_ORE.defaultBlockState();
			if (r < 90) return net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState();
			if (r < 95) return net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState();
		} else {
			int r = random.nextInt(100);
			if (r < 60) return net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState();
			if (r < 90) return net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
			if (r < 95) return net.minecraft.world.level.block.Blocks.EMERALD_ORE.defaultBlockState();
		}
		return null;
	}

	public BlockPos[] getClosestSpheres(BlockPos centerPos) {
		BlockPos[] nesw = new BlockPos[4];
		for (int i = 0; i < 4; i++) {
			int xMod = centerPos.getX();
			int zMod = centerPos.getZ();
			if (i / 2 < 1) {
				xMod += (int) Math.round(Math.pow(-1, i) * this.sphereDistance);
			} else {
				zMod += (int) Math.round(Math.pow(-1, i) * this.sphereDistance);
			}
			nesw[i] = this.getNearestCenterSphere(new BlockPos(xMod, 0, zMod));
		}
		return nesw;
	}

	public void finishBiospheres(net.minecraft.world.level.WorldGenLevel world, net.minecraft.world.level.chunk.ChunkAccess chunk) {
		net.minecraft.world.level.ChunkPos chunkPos = chunk.getPos();
		
		java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> protectedBoxes = new java.util.ArrayList<>();
		net.minecraft.world.level.StructureManager structureManager = world.getLevel().structureManager();
		net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry = world.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

		for (java.util.Map.Entry<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> entry : chunk.getAllReferences().entrySet()) {
			net.minecraft.world.level.levelgen.structure.Structure structure = entry.getKey();
			boolean isUnderground = structure.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_STRUCTURES ||
									structure.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.STRONGHOLDS;
			if (isUnderground) {
				net.minecraft.resources.ResourceLocation id = structureRegistry.getKey(structure);
				if (id != null) {
					boolean isVanilla = id.getNamespace().equals("minecraft");
					boolean isStronghold = id.getPath().equals("stronghold");
					if (!isVanilla || isStronghold) {
						java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> starts = structureManager.startsForStructure(net.minecraft.core.SectionPos.of(chunk.getPos(), 0), structure);
						for (net.minecraft.world.level.levelgen.structure.StructureStart start : starts) {
							if (start != null && start.isValid()) {
								for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
									protectedBoxes.add(piece.getBoundingBox());
								}
							}
						}
					}
				}
			}
		}

		net.minecraft.core.BlockPos.MutableBlockPos current = new net.minecraft.core.BlockPos.MutableBlockPos();
		
		BlockPos lastCenterPos = null;
		net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = null;
		double sRadius = 0;
		BlockPos[] nesw = null;
		OreSphere[] oreSpheres = null;
		
		for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
			for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
				current.set(x, 0, z);
				
				BlockPos centerPos = this.getNearestCenterSphere(current);
				if (!centerPos.equals(lastCenterPos)) {
					lastCenterPos = centerPos;
					if (this.getBiomeSource() instanceof BiospheresBiomeSource) {
						biome = ((BiospheresBiomeSource) this.getBiomeSource()).getBiomeForSphere(centerPos.getX() >> 2, centerPos.getZ() >> 2);
					} else {
						biome = chunk.getNoiseBiome(chunkPos.getMinBlockX() >> 2, 0, chunkPos.getMinBlockZ() >> 2);
					}
					sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());
					nesw = this.getClosestSpheres(centerPos);
					oreSpheres = new OreSphere[4];
					for (int i = 0; i < 4; i++) {
						oreSpheres[i] = this.getOreSphereForBridge(centerPos, nesw[i]);
					}
				}

				if (isCaveBiome(biome)) {
					for (int y = this.getMinY(); y < centerPos.getY() + sRadius - 1; y++) {
						current.set(x, y, z);
						if (centerPos.distSqr(current) <= (sRadius - 1) * (sRadius - 1)) {
							BlockState state = chunk.getBlockState(current);
							if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
								if (this.chunkRandom.nextFloat() < 0.006f) {
									BlockState ore = randomCaveOre(y, state.is(Blocks.DEEPSLATE));
									chunk.setBlockState(current, ore, false);
									net.minecraft.core.BlockPos below = current.below();
									if (below.getY() >= this.getMinY()) {
										BlockState belowState = chunk.getBlockState(below);
										if (belowState.is(Blocks.STONE) || belowState.is(Blocks.DEEPSLATE)) {
											chunk.setBlockState(below, ore, false);
										}
									}
								}
							}
						}
					}
				}
				
				double radialDistance = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(x, centerPos.getY(), z)));
				double noise = this.noiseSampler.getValue(x / 8.0, 0, z / 8.0) / 16;
				
				if (radialDistance <= sRadius + 16) {
					double sphereHeight = Math.sqrt(sRadius * sRadius
							- Math.pow(centerPos.getX() - x, 2)
							- Math.pow(z - centerPos.getZ(), 2));
					
					for (int y = centerPos.getY() - (int) sphereHeight; y <= sphereHeight + centerPos.getY(); y++) {
						double newRadialDistance = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(x, y, z)));
						double noiseTemp = (noise + y / centerPos.getY());
						BlockState blockState = null;
						
						if (newRadialDistance <= sRadius - 1) {
							continue;
						}

						// We use the chunk's biome instead of world.getBiome to prevent out of bounds
						if (y * noiseTemp >= centerPos.getY()) {
							if (biome.is(BiomeTags.IS_NETHER)) {
								blockState = Blocks.RED_STAINED_GLASS.defaultBlockState();
							} else if (isCaveBiome(biome)) {
								blockState = Blocks.TINTED_GLASS.defaultBlockState();
							} else {
								blockState = Blocks.GLASS.defaultBlockState();
							}
						} else {
							if (biome.is(BiomeTags.IS_NETHER)) {
								blockState = this.defaultNetherBlock;
							} else {
								blockState = this.defaultBlock;
								// Generate clay blobs (similar to granite/andesite/diorite)
								double clayNoise = this.noiseSampler.getValue(x / 10.0, y / 10.0 + 500, z / 10.0);
								if (clayNoise > 0.6) {
									blockState = net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState();
								}
							}
						}
						
						if (blockState != null && y >= this.getMinY() && y < this.getGenDepth() + this.getMinY()) {
							current.set(x, y, z);
							chunk.setBlockState(current, blockState, false);
						}
					}
					
					double largerSphereHeight = Math.sqrt((sRadius + 16) * (sRadius + 16)
							- Math.pow(centerPos.getX() - x, 2)
							- Math.pow(z - centerPos.getZ(), 2));
							
					for (int y = this.getMinY(); y < this.getGenDepth() + this.getMinY(); y++) {
						double newRadialDistance = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(x, y, z)));
						if (newRadialDistance >= sRadius) {
							// Note: finishBiospheres runs AFTER features, replacing blocks outside sphere with AIR
							if (y >= this.getMinY() && y < this.getGenDepth() + this.getMinY()) {
								if (isInsideProtectedStructure(x, y, z, protectedBoxes)) continue;
								current.set(x, y, z);
								if (!chunk.getBlockState(current).isAir()) {
									BlockState state = chunk.getBlockState(current);
									if (state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL)) continue;
									chunk.setBlockState(current, Blocks.AIR.defaultBlockState(), false);
								}
							}
						} else if (newRadialDistance > sRadius - 1.0) {
							current.set(x, y, z);
							double noiseTemp = (noise + y / centerPos.getY());
							BlockState expected;
							if (y * noiseTemp >= centerPos.getY()) {
								if (biome.is(BiomeTags.IS_NETHER)) {
									expected = Blocks.RED_STAINED_GLASS.defaultBlockState();
								} else if (isCaveBiome(biome)) {
									expected = Blocks.TINTED_GLASS.defaultBlockState();
								} else {
									expected = Blocks.GLASS.defaultBlockState();
								}
							} else {
								expected = biome.is(BiomeTags.IS_NETHER) ? this.defaultNetherBlock : this.defaultBlock;
							}
							if (!chunk.getBlockState(current).equals(expected)) {
								chunk.setBlockState(current, expected, false);
							}
						} else {
							if (isCaveBiome(biome)) {
								current.set(x, y, z);
								BlockState currState = chunk.getBlockState(current);
								if (currState.is(Blocks.STONE) || currState.is(Blocks.DEEPSLATE)) {
									if (this.chunkRandom.nextFloat() < 0.015f) { // 1.5% extra ore
										chunk.setBlockState(current, randomCaveOre(y), false);
									}
								}
							}
						}
					}
				} else {
					for (int y = this.getMinY(); y < this.getGenDepth() + this.getMinY(); y++) {
						if (isInsideProtectedStructure(x, y, z, protectedBoxes)) continue;
						current.set(x, y, z);
						if (!chunk.getBlockState(current).isAir()) {
							BlockState state = chunk.getBlockState(current);
							if (state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL)) continue;
							chunk.setBlockState(current, Blocks.AIR.defaultBlockState(), false);
						}
					}
				}
				
				if (x == centerPos.getX() && z == centerPos.getZ()) {
					int topY = centerPos.getY() + (int) sRadius - 1;
					if (topY >= this.getMinY() && topY < this.getGenDepth() + this.getMinY()) {
						BlockState topGlass;
						if (biome.is(BiomeTags.IS_NETHER)) topGlass = Blocks.RED_STAINED_GLASS.defaultBlockState();
						else if (isCaveBiome(biome)) topGlass = Blocks.TINTED_GLASS.defaultBlockState();
						else topGlass = Blocks.GLASS.defaultBlockState();
						
						current.set(centerPos.getX(), topY, centerPos.getZ());
						chunk.setBlockState(current, topGlass, false);
					}
				}

				current.set(x, 0, z);
				this.makeBridges(current, centerPos, nesw, chunk, current, sRadius, oreSpheres);
				this.makeOreSpheres(current, chunk, current, oreSpheres);
			}
		}
	}

	public void makeOreSpheres(BlockPos pos, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos.MutableBlockPos current, OreSphere[] oreSpheres) {
		int x = pos.getX();
		int z = pos.getZ();
		for (int i = 0; i < 4; i++) {
			OreSphere ore = oreSpheres[i];
			if (ore == null) continue;
			
			double dx = x - ore.center.getX();
			double dz = z - ore.center.getZ();
			double distXZSq = dx * dx + dz * dz;
			
			if (distXZSq <= ore.radius * ore.radius) {
				for (int y = ore.center.getY() - ore.radius; y <= ore.center.getY() + ore.radius; y++) {
					double dy = y - ore.center.getY();
					double distSq = distXZSq + dy * dy;
					
					if (distSq <= ore.radius * ore.radius) {
						boolean isSurface = false;
						double rSq = ore.radius * ore.radius;
						if ((dx+1)*(dx+1) + dy*dy + dz*dz > rSq) isSurface = true;
						else if ((dx-1)*(dx-1) + dy*dy + dz*dz > rSq) isSurface = true;
						else if (dx*dx + (dy+1)*(dy+1) + dz*dz > rSq) isSurface = true;
						else if (dx*dx + (dy-1)*(dy-1) + dz*dz > rSq) isSurface = true;
						else if (dx*dx + dy*dy + (dz+1)*(dz+1) > rSq) isSurface = true;
						else if (dx*dx + dy*dy + (dz-1)*(dz-1) > rSq) isSurface = true;
						
						BlockState state;
						if (isSurface) {
							state = net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState();
						} else {
							this.chunkRandom.setSeed(this.getActualSeed() + x * 341873128712L + y * 132897987541L + z * 543897123984L);
							state = randomOreBlock(x, y, z);
						}
						this.safeSetBlock(chunk, current, x, y, z, state);
					}
				}
			}
			
			boolean isBridgeZ = (ore.center.getX() == ore.bridgeAttachPoint.getX());
			boolean isBridgeX = (ore.center.getZ() == ore.bridgeAttachPoint.getZ());
			
			if (isBridgeZ) {
				if (x >= ore.bridgeAttachPoint.getX() - 1 && x <= ore.bridgeAttachPoint.getX() + 1) {
					int minZ = Math.min(ore.bridgeAttachPoint.getZ(), ore.center.getZ());
					int maxZ = Math.max(ore.bridgeAttachPoint.getZ(), ore.center.getZ());
					if (z >= minZ && z <= maxZ) {
						if (distXZSq > ore.radius * ore.radius) {
							double t = (double)(z - ore.bridgeAttachPoint.getZ()) / (ore.center.getZ() - ore.bridgeAttachPoint.getZ());
							int y = (int) Math.round(ore.bridgeAttachPoint.getY() + t * (ore.center.getY() - ore.bridgeAttachPoint.getY()));
							
							this.chunkRandom.setLargeFeatureSeed(this.seed, x, z);
							if (this.chunkRandom.nextFloat() > 0.15f) {
								this.safeSetBlock(chunk, current, x, y - 1, z, this.defaultBridge);
								this.safeSetBlock(chunk, current, x, y, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
								this.safeSetBlock(chunk, current, x, y + 1, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
			} else if (isBridgeX) {
				if (z >= ore.bridgeAttachPoint.getZ() - 1 && z <= ore.bridgeAttachPoint.getZ() + 1) {
					int minX = Math.min(ore.bridgeAttachPoint.getX(), ore.center.getX());
					int maxX = Math.max(ore.bridgeAttachPoint.getX(), ore.center.getX());
					if (x >= minX && x <= maxX) {
						if (distXZSq > ore.radius * ore.radius) {
							double t = (double)(x - ore.bridgeAttachPoint.getX()) / (ore.center.getX() - ore.bridgeAttachPoint.getX());
							int y = (int) Math.round(ore.bridgeAttachPoint.getY() + t * (ore.center.getY() - ore.bridgeAttachPoint.getY()));
							
							this.chunkRandom.setLargeFeatureSeed(this.seed, x, z);
							if (this.chunkRandom.nextFloat() > 0.15f) {
								this.safeSetBlock(chunk, current, x, y - 1, z, this.defaultBridge);
								this.safeSetBlock(chunk, current, x, y, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
								this.safeSetBlock(chunk, current, x, y + 1, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
			}
		}
	}

	private int getSurfaceHeightAt(int realX, int realZ, int cy) {
		double noise = this.noiseSampler.getValue(realX / 8.0, 0, realZ / 8.0) / 16.0;
		if (noise >= 0) {
			return cy - 1;
		} else {
			double exactY = cy / (noise + 1.0);
			int maxIntY = (int) Math.ceil(exactY) - 1;
			if (maxIntY == exactY) maxIntY--;
			return maxIntY;
		}
	}

	public void makeBridges(BlockPos pos, BlockPos centerPos, BlockPos[] nesw, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos.MutableBlockPos current, double sRadius, OreSphere[] oreSpheres) {
		int cx = centerPos.getX();
		int cy = centerPos.getY();
		int cz = centerPos.getZ();
		int x = pos.getX();
		int z = pos.getZ();

		for (int i = 0; i < 4; i++) {
			double targetRadius = this.getSphereRadius(nesw[i].getX(), nesw[i].getZ());
			double L = this.sphereDistance - sRadius - targetRadius;
			if (L <= 0) continue;

			double t = -100;
			boolean isOnXAxis = false;
			boolean isPositive = false;
			boolean onBridge = false;

			if (i == 0 && z >= cz - 2 && z <= cz + 2 && x >= cx + (int)sRadius - 3 && x <= nesw[i].getX() - (int)targetRadius + 3) {
				t = (x - (cx + sRadius)) / L;
				isOnXAxis = true;
				isPositive = true;
				onBridge = true;
			} else if (i == 1 && z >= cz - 2 && z <= cz + 2 && x <= cx - (int)sRadius + 3 && x >= nesw[i].getX() + (int)targetRadius - 3) {
				t = ((cx - sRadius) - x) / L;
				isOnXAxis = true;
				isPositive = false;
				onBridge = true;
			} else if (i == 2 && x >= cx - 2 && x <= cx + 2 && z >= cz + (int)sRadius - 3 && z <= nesw[i].getZ() - (int)targetRadius + 3) {
				t = (z - (cz + sRadius)) / L;
				isOnXAxis = false;
				isPositive = true;
				onBridge = true;
			} else if (i == 3 && x >= cx - 2 && x <= cx + 2 && z <= cz - (int)sRadius + 3 && z >= nesw[i].getZ() + (int)targetRadius - 3) {
				t = ((cz - sRadius) - z) / L;
				isOnXAxis = false;
				isPositive = false;
				onBridge = true;
			}

			if (onBridge) {
				int attachX1 = cx, attachZ1 = cz;
				int attachX2 = nesw[i].getX(), attachZ2 = nesw[i].getZ();
				
				if (i == 0) { attachX1 += (int)sRadius; attachX2 -= (int)targetRadius; }
				else if (i == 1) { attachX1 -= (int)sRadius; attachX2 += (int)targetRadius; }
				else if (i == 2) { attachZ1 += (int)sRadius; attachZ2 -= (int)targetRadius; }
				else if (i == 3) { attachZ1 -= (int)sRadius; attachZ2 += (int)targetRadius; }
				
				int h1 = this.getSurfaceHeightAt(attachX1, attachZ1, cy);
				int h2 = this.getSurfaceHeightAt(attachX2, attachZ2, nesw[i].getY());
				
				double effectiveT = Math.max(0, Math.min(1, t));
				double baseY = (h1 + 1) + effectiveT * (h2 - h1);
				double sag = Math.sin(effectiveT * Math.PI) * (L / 12.0);
				int finalY = (int) Math.round(baseY - sag);
				
				boolean clearOnly = false;
				if (i == 0 && (x < cx + (int)sRadius || x > nesw[i].getX() - (int)targetRadius)) clearOnly = true;
				else if (i == 1 && (x > cx - (int)sRadius || x < nesw[i].getX() + (int)targetRadius)) clearOnly = true;
				else if (i == 2 && (z < cz + (int)sRadius || z > nesw[i].getZ() - (int)targetRadius)) clearOnly = true;
				else if (i == 3 && (z > cz - (int)sRadius || z < nesw[i].getZ() + (int)targetRadius)) clearOnly = true;
				
				boolean cn1 = true, cp1 = true, cn2 = true, cp2 = true;
				if (i == 0) {
					if (x == cx + (int)sRadius) { cn1 = false; cn2 = false; }
					if (x == nesw[i].getX() - (int)targetRadius) { cp1 = false; cp2 = false; }
					if (oreSpheres != null && oreSpheres[i] != null) {
						int ax = oreSpheres[i].bridgeAttachPoint.getX();
						int az = oreSpheres[i].bridgeAttachPoint.getZ();
						if (az == cz - 2) {
							if (x == ax - 2) cp1 = false;
							if (x == ax + 2) cn1 = false;
						} else if (az == cz + 2) {
							if (x == ax - 2) cp2 = false;
							if (x == ax + 2) cn2 = false;
						}
					}
				} else if (i == 1) {
					if (x == cx - (int)sRadius) { cp1 = false; cp2 = false; }
					if (x == nesw[i].getX() + (int)targetRadius) { cn1 = false; cn2 = false; }
					if (oreSpheres != null && oreSpheres[i] != null) {
						int ax = oreSpheres[i].bridgeAttachPoint.getX();
						int az = oreSpheres[i].bridgeAttachPoint.getZ();
						if (az == cz - 2) {
							if (x == ax - 2) cp1 = false;
							if (x == ax + 2) cn1 = false;
						} else if (az == cz + 2) {
							if (x == ax - 2) cp2 = false;
							if (x == ax + 2) cn2 = false;
						}
					}
				} else if (i == 2) {
					if (z == cz + (int)sRadius) { cn1 = false; cn2 = false; }
					if (z == nesw[i].getZ() - (int)targetRadius) { cp1 = false; cp2 = false; }
					if (oreSpheres != null && oreSpheres[i] != null) {
						int ax = oreSpheres[i].bridgeAttachPoint.getX();
						int az = oreSpheres[i].bridgeAttachPoint.getZ();
						if (ax == cx - 2) {
							if (z == az - 2) cp1 = false;
							if (z == az + 2) cn1 = false;
						} else if (ax == cx + 2) {
							if (z == az - 2) cp2 = false;
							if (z == az + 2) cn2 = false;
						}
					}
				} else if (i == 3) {
					if (z == cz - (int)sRadius) { cp1 = false; cp2 = false; }
					if (z == nesw[i].getZ() + (int)targetRadius) { cn1 = false; cn2 = false; }
					if (oreSpheres != null && oreSpheres[i] != null) {
						int ax = oreSpheres[i].bridgeAttachPoint.getX();
						int az = oreSpheres[i].bridgeAttachPoint.getZ();
						if (ax == cx - 2) {
							if (z == az - 2) cp1 = false;
							if (z == az + 2) cn1 = false;
						} else if (ax == cx + 2) {
							if (z == az - 2) cp2 = false;
							if (z == az + 2) cn2 = false;
						}
					}
				}
				
				this.fillBridgeSlice(new BlockPos(x, finalY, z), centerPos, chunk, current, isOnXAxis, isPositive, clearOnly, cn1, cp1, cn2, cp2);
			}
		}
	}

	private void safeSetBlock(net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos.MutableBlockPos current, int x, int y, int z, BlockState state) {
		if (y >= this.getMinY() && y < this.getGenDepth() + this.getMinY()) {
			chunk.setBlockState(current.set(x, y, z), state, false);
		}
	}

	public void fillBridgeSlice(BlockPos pos, BlockPos centerPos, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos.MutableBlockPos current, boolean isOnXAxis, boolean isPositive, boolean clearOnly, boolean cn1, boolean cp1, boolean cn2, boolean cp2) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int cx = centerPos.getX();
		int cz = centerPos.getZ();
		
		// Note: in finishBiospheres, modifying chunk blocks that are outside the current chunk boundary is problematic.
		// We only set blocks if they fall within the current chunk!
		if (x >= chunk.getPos().getMinBlockX() && x <= chunk.getPos().getMaxBlockX() &&
			z >= chunk.getPos().getMinBlockZ() && z <= chunk.getPos().getMaxBlockZ()) {
			
			if (!clearOnly) {
				this.safeSetBlock(chunk, current, x, y - 1, z, this.defaultBridge);
			}
			
			if (clearOnly) {
				for (int dy = 0; dy < 4; dy++) {
					net.minecraft.world.level.block.state.BlockState bs = chunk.getBlockState(new BlockPos(x, y + dy, z));
					if (bs.is(net.minecraft.world.level.block.Blocks.GLASS)) {
						this.safeSetBlock(chunk, current, x, y + dy, z, Blocks.AIR.defaultBlockState());
					}
				}
			} else {
				this.safeSetBlock(chunk, current, x, y, z, Blocks.AIR.defaultBlockState());
				this.safeSetBlock(chunk, current, x, y + 1, z, Blocks.AIR.defaultBlockState());
				this.safeSetBlock(chunk, current, x, y + 2, z, Blocks.AIR.defaultBlockState());
				this.safeSetBlock(chunk, current, x, y + 3, z, Blocks.AIR.defaultBlockState());
			}
			
			if (!clearOnly) {
				if (isOnXAxis) {
					if (z == cz - 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
							edge = edge.setValue(net.minecraft.world.level.block.FenceBlock.EAST, cp1).setValue(net.minecraft.world.level.block.FenceBlock.WEST, cn1);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					} else if (z == cz + 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
							edge = edge.setValue(net.minecraft.world.level.block.FenceBlock.EAST, cp2).setValue(net.minecraft.world.level.block.FenceBlock.WEST, cn2);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					}
				} else {
					if (x == cx - 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
							edge = edge.setValue(net.minecraft.world.level.block.FenceBlock.SOUTH, cp1).setValue(net.minecraft.world.level.block.FenceBlock.NORTH, cn1);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					} else if (x == cx + 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
							edge = edge.setValue(net.minecraft.world.level.block.FenceBlock.SOUTH, cp2).setValue(net.minecraft.world.level.block.FenceBlock.NORTH, cn2);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					}
				}
			}
		}
	}

	@Override
	public int getBaseHeight(int x, int z, net.minecraft.world.level.levelgen.Heightmap.Types heightmap, net.minecraft.world.level.LevelHeightAccessor world, net.minecraft.world.level.levelgen.RandomState noiseConfig) {
		BlockPos centerPos = this.getNearestCenterSphere(new BlockPos(x, 0, z));
		double radialDistance = Math.sqrt(Math.pow(x - centerPos.getX(), 2) + Math.pow(z - centerPos.getZ(), 2));
		if (radialDistance < sphereRadius) {
			return centerPos.getY();
		}
		return world.getMinBuildHeight();
	}

	@Override
	public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, net.minecraft.world.level.LevelHeightAccessor world, net.minecraft.world.level.levelgen.RandomState noiseConfig) {
		return new net.minecraft.world.level.NoiseColumn(world.getMinBuildHeight(), new BlockState[0]);
	}

	private boolean isCaveBiome(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		net.minecraft.tags.TagKey<Biome> CAVES_TAG = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "caves"));
		net.minecraft.tags.TagKey<Biome> IS_CAVE_TAG = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "is_cave"));
		if (biome.is(CAVES_TAG) || biome.is(IS_CAVE_TAG)) return true;
		if (biome.is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES)) return true;
		if (biome.is(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES)) return true;
		if (biome.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)) return true;
		return false;
	}

	@Override
	public void addDebugScreenInfo(List<String> text, net.minecraft.world.level.levelgen.RandomState noiseConfig, BlockPos pos) {}

	@Override
	public int getGenDepth() { return 384; }

	@Override
	public int getSeaLevel() { return 63; }

	@Override
	public int getMinY() { return -64; }

	@Override
	public void applyCarvers(net.minecraft.server.level.WorldGenRegion region, long seed, net.minecraft.world.level.levelgen.RandomState noiseConfig, net.minecraft.world.level.biome.BiomeManager biomeAccess, net.minecraft.world.level.StructureManager structureAccessor, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.world.level.levelgen.GenerationStep.Carving carverStep) {}

	public void spawnOriginalMobs(net.minecraft.server.level.WorldGenRegion region) {}

	private boolean isInsideProtectedStructure(int x, int y, int z, java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> protectedBoxes) {
		for (net.minecraft.world.level.levelgen.structure.BoundingBox box : protectedBoxes) {
			if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY() && z >= box.minZ() && z <= box.maxZ()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void createStructures(net.minecraft.core.RegistryAccess registryManager, net.minecraft.world.level.chunk.ChunkGeneratorStructureState placementCalculator, net.minecraft.world.level.StructureManager structureAccessor, net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager) {
		super.createStructures(registryManager, placementCalculator, structureAccessor, chunk, structureTemplateManager);

		net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry = registryManager.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

		java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> starts = chunk.getAllStarts();
		for (java.util.Map.Entry<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> entry : starts.entrySet()) {
			net.minecraft.world.level.levelgen.structure.Structure structure = entry.getKey();
			net.minecraft.world.level.levelgen.structure.StructureStart start = entry.getValue();

			if (start != null && start.isValid()) {
				net.minecraft.world.level.ChunkPos cPos = start.getChunkPos();
				net.minecraft.core.BlockPos startPos = new net.minecraft.core.BlockPos(cPos.getMinBlockX(), 0, cPos.getMinBlockZ());
				net.minecraft.core.BlockPos centerPos = this.getNearestCenterSphere(startPos);
				double distance = Math.sqrt(centerPos.distSqr(new net.minecraft.core.Vec3i(startPos.getX(), centerPos.getY(), startPos.getZ())));
				double sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());

				net.minecraft.resources.ResourceLocation id = structureRegistry.getKey(structure);
				if (id == null) continue;

				boolean isVanilla = id.getNamespace().equals("minecraft");
				boolean isStronghold = id.getPath().equals("stronghold");

				if (structure.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_STRUCTURES ||
					structure.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.STRONGHOLDS) {
					if (isVanilla && !isStronghold) {
						chunk.setStartForStructure(structure, net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
					}
				} else if (structure.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES) {
					if (distance > sRadius * 0.6) {
						chunk.setStartForStructure(structure, net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
					}
				}
			}
		}





}



	private BlockState randomCaveOre(int y, boolean deepslate) {
		float r = this.chunkRandom.nextFloat();
		if (y < 0) {
			if (r < 0.1f) return deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DIAMOND_ORE.defaultBlockState();
			if (r < 0.3f) return deepslate ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState();
			if (r < 0.6f) return deepslate ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState();
			if (r < 0.8f) return deepslate ? Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState() : Blocks.LAPIS_ORE.defaultBlockState();
			return deepslate ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
		} else {
			if (r < 0.3f) return Blocks.COAL_ORE.defaultBlockState();
			if (r < 0.6f) return Blocks.IRON_ORE.defaultBlockState();
			if (r < 0.8f) return Blocks.COPPER_ORE.defaultBlockState();
			return Blocks.GOLD_ORE.defaultBlockState();
		}
	}
}





