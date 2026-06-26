package xyz.yatta.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.random.Random;

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
	protected final ChunkRandom chunkRandom;
	protected final OctavePerlinNoiseSampler noiseSampler;
	protected final BlockState defaultBlock;
	protected final BlockState defaultNetherBlock;
	protected final BlockState defaultFluid;
	protected final BlockState defaultBridge;
	protected final BlockState defaultEdge;
	protected double generatedSphereHeight;
	private Long actualSeed = null;

	private long getActualSeed() {
		return this.actualSeed != null ? this.actualSeed : this.seed;
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
		this.defaultBlock = Blocks.STONE.getDefaultState();
		this.defaultNetherBlock = Blocks.NETHERRACK.getDefaultState();
		this.defaultFluid = Blocks.WATER.getDefaultState();
		this.defaultBridge = Blocks.OAK_PLANKS.getDefaultState();
		this.defaultEdge = Blocks.OAK_FENCE.getDefaultState();
		this.chunkRandom = new ChunkRandom(new Xoroshiro128PlusPlusRandom(seed));
		this.chunkRandom.skip(1000);
		// In 1.21.1 OctavePerlinNoiseSampler is created via create() method
		this.noiseSampler = OctavePerlinNoiseSampler.create(this.chunkRandom, IntStream.rangeClosed(-3, 0));
		this.generatedSphereHeight = this.sphereRadius;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> getCodec() {
		return CODEC;
	}

	@Override
	public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
		// Manual surface building in populateNoise
	}

	public BlockState getLakeBlock(BlockPos center, RegistryEntry<Biome> biome) {
		java.util.Random r = new java.util.Random(this.getActualSeed() + (long)center.getX() * 341873128712L + (long)center.getZ() * 132897987541L);
		int rng = r.nextInt(100);
		BlockState state = Blocks.AIR.getDefaultState();
		if (rng < 40) {
			if (biome.isIn(BiomeTags.IS_NETHER)) {
				state = Blocks.LAVA.getDefaultState();
			} else if (rng >= 30 && biome.value().getTemperature() >= 1.0f) {
				state = Blocks.LAVA.getDefaultState();
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
				return net.minecraft.block.Blocks.LAVA.getDefaultState();
			}
		}
		
		boolean isDeepslate = this.chunkRandom.nextBoolean();
		float rngChance = this.chunkRandom.nextFloat();
		
		if (rngChance < 0.5f) {
			return isDeepslate ? net.minecraft.block.Blocks.DEEPSLATE.getDefaultState() : defaultBlock;
		} else if (rngChance < 0.7f) {
			return net.minecraft.block.Blocks.GRAVEL.getDefaultState();
		}
		
		String targetPath = isDeepslate ? "ores_in_ground/deepslate" : "ores_in_ground/stone";
		java.util.Optional<net.minecraft.registry.entry.RegistryEntryList.Named<net.minecraft.block.Block>> oresC = net.minecraft.registry.Registries.BLOCK.getEntryList(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, net.minecraft.util.Identifier.of("c", targetPath)));
		java.util.List<net.minecraft.block.Block> allOres = new java.util.ArrayList<>();
		if (oresC.isPresent()) {
			for (net.minecraft.registry.entry.RegistryEntry<net.minecraft.block.Block> holder : oresC.get()) allOres.add(holder.value());
		}
		
		if (!allOres.isEmpty()) {
			int idx = this.chunkRandom.nextInt(allOres.size());
			return allOres.get(idx).getDefaultState();
		}
		
		int rng = this.chunkRandom.nextInt(16);
		BlockState ore;
		if (rng == 1) ore = net.minecraft.block.Blocks.EMERALD_ORE.getDefaultState();
		else if (rng > 1 && rng <= 3) ore = net.minecraft.block.Blocks.DIAMOND_ORE.getDefaultState();
		else if (rng > 3 && rng <= 5) ore = net.minecraft.block.Blocks.LAPIS_ORE.getDefaultState();
		else if (rng > 5 && rng <= 7) ore = net.minecraft.block.Blocks.REDSTONE_ORE.getDefaultState();
		else if (rng > 7 && rng <= 10) ore = net.minecraft.block.Blocks.GOLD_ORE.getDefaultState();
		else ore = net.minecraft.block.Blocks.IRON_ORE.getDefaultState();
		
		if (isDeepslate) {
			if (ore.isOf(net.minecraft.block.Blocks.EMERALD_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_EMERALD_ORE.getDefaultState();
			if (ore.isOf(net.minecraft.block.Blocks.DIAMOND_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState();
			if (ore.isOf(net.minecraft.block.Blocks.LAPIS_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_LAPIS_ORE.getDefaultState();
			if (ore.isOf(net.minecraft.block.Blocks.REDSTONE_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_REDSTONE_ORE.getDefaultState();
			if (ore.isOf(net.minecraft.block.Blocks.GOLD_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_GOLD_ORE.getDefaultState();
			if (ore.isOf(net.minecraft.block.Blocks.IRON_ORE)) return net.minecraft.block.Blocks.DEEPSLATE_IRON_ORE.getDefaultState();
		}
		
		return ore;
	}

	public double getSphereRadius(int centerX, int centerZ) {
		double maxRadius = this.sphereRadius * 1.1;
		double minRadius = this.sphereRadius * 0.4;
		java.util.Random random = new java.util.Random(this.getActualSeed() + 1L + (long) centerX * 341873128712L + (long) centerZ * 132897987541L);
		return random.nextDouble() * (maxRadius - minRadius) + minRadius;
	}

	@Override
	public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		BlockPos.Mutable current = new BlockPos.Mutable();
		int xPos = chunkPos.getStartX();
		int zPos = chunkPos.getStartZ();
		
		Heightmap oceanHeight = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);

		BlockPos lastCenterPos = null;
		RegistryEntry<Biome> biome = null;
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
					biome = this.biomeSource.getBiome(centerPos.getX() >> 2, centerPos.getY() >> 2, centerPos.getZ() >> 2, noiseConfig.getMultiNoiseSampler());
					fluidBlock = this.getLakeBlock(centerPos, biome);
					sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());
				}
				
				double radialDistance = Math.sqrt(current.getSquaredDistance(centerPos.getX(), 0, centerPos.getZ()));

				if (radialDistance <= sRadius) {
					double noise = this.noiseSampler.sample(realX / 8.0, 0, realZ / 8.0, 1 / 16.0, 1 / 16.0, false) / 16;
					double sphereHeight = Math.sqrt(sRadius * sRadius
							- Math.pow(centerPos.getX() - realX, 2)
							- Math.pow(realZ - centerPos.getZ(), 2));
					
					for (int y = centerPos.getY() - (int) sphereHeight; y <= centerPos.getY() + sphereHeight; y++) {
						double lakeDistance = Math.sqrt(centerPos.getSquaredDistance(realX, y, realZ));
						double lakeDistance2d = Math.sqrt(centerPos.getSquaredDistance(realX, centerPos.getY(), realZ));
						
						double noiseTemp = (noise + y / centerPos.getY());
						BlockState blockState = Blocks.AIR.getDefaultState();
						
						if (y * noiseTemp < centerPos.getY()) {
							if (biome.isIn(BiomeTags.IS_NETHER)) {
								blockState = this.defaultNetherBlock;
							} else {
								blockState = this.defaultBlock;
							}
						}
						
						if ((blockState.equals(this.defaultBlock) || blockState.equals(this.defaultNetherBlock))
								&& lakeDistance2d <= this.lakeRadius && !fluidBlock.isAir()) {
							if (y >= centerPos.getY() && (!fluidBlock.equals(Blocks.STONE.getDefaultState()) && !fluidBlock.equals(Blocks.NETHERRACK.getDefaultState()))) {
								blockState = Blocks.AIR.getDefaultState();
							} else if (lakeDistance <= this.lakeRadius) {
								double belowDist = Math.sqrt(centerPos.getSquaredDistance(realX, y - 1, realZ));
								if (belowDist > this.lakeRadius && fluidBlock.equals(net.minecraft.block.Blocks.WATER.getDefaultState())) {
									java.util.Random lakeRandom = new java.util.Random(centerPos.asLong());
									boolean hasKelp = lakeRandom.nextBoolean();
									if (this.chunkRandom.nextInt(4) == 0) {
										if (hasKelp && this.chunkRandom.nextBoolean()) {
											blockState = net.minecraft.block.Blocks.KELP.getDefaultState();
										} else {
											blockState = net.minecraft.block.Blocks.SEAGRASS.getDefaultState();
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
						if (blockState.equals(this.defaultBlock) && !biome.isIn(BiomeTags.IS_NETHER)) {
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
								double shoreDist = Math.sqrt(centerPos.getSquaredDistance(realX, centerPos.getY(), realZ));
								boolean isShore = !fluidBlock.isAir() && shoreDist > this.lakeRadius && shoreDist <= this.lakeRadius + this.shoreRadius;
								boolean isLakeBottom = !fluidBlock.isAir() && shoreDist <= this.lakeRadius && y <= centerPos.getY();
								
								boolean isSandy = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DESERT) || biome.isIn(net.minecraft.registry.tag.BiomeTags.IS_BEACH) || biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("c", "sandy"))) || biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("biospheres", "surface_sand")));
								boolean isRedSand = biome.isIn(net.minecraft.registry.tag.BiomeTags.IS_BADLANDS) || biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("biospheres", "surface_red_sand")));
								boolean isMushroom = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.MUSHROOM_FIELDS) || biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("biospheres", "surface_mycelium")));
								boolean isMuddy = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.SWAMP) || biome.matchesKey(net.minecraft.world.biome.BiomeKeys.MANGROVE_SWAMP) || biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("biospheres", "surface_mud")));
								
								boolean isDripstone = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DRIPSTONE_CAVES);
								boolean isLush = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.LUSH_CAVES);
								boolean isDeepDark = biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DEEP_DARK);
								boolean isCave = isCaveBiome(biome);
								
								float temp = biome.value().getTemperature();
								
								if (depth == 0) {
									if (isDripstone) {
										blockState = net.minecraft.block.Blocks.DRIPSTONE_BLOCK.getDefaultState();
									} else if (isLush) {
										blockState = net.minecraft.block.Blocks.MOSS_BLOCK.getDefaultState();
									} else if (isDeepDark) {
										blockState = net.minecraft.block.Blocks.SCULK.getDefaultState();
									} else if (isCave) {
										blockState = net.minecraft.block.Blocks.STONE.getDefaultState();
									} else if (isShore || isLakeBottom) {
										blockState = net.minecraft.block.Blocks.SAND.getDefaultState();
									} else if (isMushroom) {
										blockState = net.minecraft.block.Blocks.MYCELIUM.getDefaultState();
									} else if (isRedSand) {
										blockState = net.minecraft.block.Blocks.RED_SAND.getDefaultState();
									} else if (isSandy) {
										blockState = net.minecraft.block.Blocks.SAND.getDefaultState();
									} else if (isMuddy) {
										blockState = net.minecraft.block.Blocks.MUD.getDefaultState();
									} else if (temp < 0.15f) {
										blockState = net.minecraft.block.Blocks.SNOW_BLOCK.getDefaultState();
									} else {
										blockState = net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState();
									}
								} else {
									if (isDripstone || isCave) {
										blockState = net.minecraft.block.Blocks.STONE.getDefaultState();
									} else if (isLush) {
										blockState = net.minecraft.block.Blocks.ROOTED_DIRT.getDefaultState();
									} else if (isDeepDark) {
										blockState = net.minecraft.block.Blocks.DEEPSLATE.getDefaultState();
									} else if (isShore || isSandy) {
										blockState = net.minecraft.block.Blocks.SAND.getDefaultState();
									} else if (isRedSand) {
										blockState = net.minecraft.block.Blocks.RED_SAND.getDefaultState();
									} else if (isMuddy) {
										blockState = net.minecraft.block.Blocks.MUD.getDefaultState();
									} else {
										blockState = net.minecraft.block.Blocks.DIRT.getDefaultState();
									}
								}
							} else {
								// Generate clay blobs (similar to granite/andesite/diorite)
								double clayNoise = this.noiseSampler.sample(realX / 10.0, y / 10.0 + 500, realZ / 10.0, 1 / 16.0, 1 / 16.0, false);
								if (clayNoise > 0.6) {
									blockState = net.minecraft.block.Blocks.CLAY.getDefaultState();
								}
							}
						}

						if (y >= this.getMinimumY() && y < this.getWorldHeight() + this.getMinimumY()) {
							current.set(realX, y, realZ);
							chunk.setBlockState(current, blockState, false);
							oceanHeight.trackUpdate(x, y, z, blockState);
							worldSurface.trackUpdate(x, y, z, blockState);
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
		int centerY = (int) ((Math.pow((r.nextFloat() % 0.7) - 0.5, 3) + 0.5)
				* (sRadius * 2 - sRadius * 4)) + (int)(sRadius * 2);
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
		
		this.chunkRandom.setCarverSeed(this.seed, first.getX() + second.getX(), first.getZ() + second.getZ());
		
		if (this.chunkRandom.nextFloat() > 0.40f) { // 40% chance to have an ore sphere
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
	public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
		super.generateFeatures(world, chunk, structureAccessor);
		this.finishBiospheres(world, chunk);
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

	public void finishBiospheres(StructureWorldAccess world, Chunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		
		java.util.List<net.minecraft.util.math.BlockBox> protectedBoxes = new java.util.ArrayList<>();
		net.minecraft.registry.Registry<net.minecraft.world.gen.structure.Structure> structureRegistry = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE);
		net.minecraft.world.gen.StructureAccessor structureAccessor = world.toServerWorld().getStructureAccessor();
		
		for (java.util.Map.Entry<net.minecraft.world.gen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> entry : chunk.getStructureReferences().entrySet()) {
			net.minecraft.world.gen.structure.Structure structure = entry.getKey();
			net.minecraft.world.gen.GenerationStep.Feature step = structure.getFeatureGenerationStep();
			boolean isUnderground = step == net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_STRUCTURES ||
									step == net.minecraft.world.gen.GenerationStep.Feature.STRONGHOLDS;
			if (isUnderground) {
				net.minecraft.util.Identifier id = structureRegistry.getId(structure);
				if (id != null) {
					boolean isVanilla = id.getNamespace().equals("minecraft");
					boolean isStronghold = id.getPath().equals("stronghold");
					if (!isVanilla || isStronghold) {
						java.util.List<net.minecraft.structure.StructureStart> starts = structureAccessor.getStructureStarts(net.minecraft.util.math.ChunkSectionPos.from(chunk.getPos(), 0), structure);
						for (net.minecraft.structure.StructureStart start : starts) {
							if (start != null && start.hasChildren()) {
								for (net.minecraft.structure.StructurePiece piece : start.getChildren()) {
									protectedBoxes.add(piece.getBoundingBox());
								}
							}
						}
					}
				}
			}
		}

		BlockPos.Mutable current = new BlockPos.Mutable();
		
		BlockPos lastCenterPos = null;
		RegistryEntry<Biome> biome = null;
		double sRadius = 0;
		BlockPos[] nesw = null;
		OreSphere[] oreSpheres = null;
		
		for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
			for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
				current.set(x, 0, z);
				
				BlockPos centerPos = this.getNearestCenterSphere(current);
				if (!centerPos.equals(lastCenterPos)) {
					lastCenterPos = centerPos;
					if (this.getBiomeSource() instanceof BiospheresBiomeSource) {
						biome = ((BiospheresBiomeSource) this.getBiomeSource()).getBiomeForSphere(centerPos.getX() >> 2, centerPos.getZ() >> 2);
					} else {
						biome = chunk.getBiomeForNoiseGen(chunkPos.getStartX() >> 2, 0, chunkPos.getStartZ() >> 2);
					}
					sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());
					nesw = this.getClosestSpheres(centerPos);
					oreSpheres = new OreSphere[4];
					for (int i = 0; i < 4; i++) {
						oreSpheres[i] = this.getOreSphereForBridge(centerPos, nesw[i]);
					}
				}
				
				double radialDistance = Math.sqrt(centerPos.getSquaredDistance(x, centerPos.getY(), z));
				double noise = this.noiseSampler.sample(x / 8.0, 0, z / 8.0, 1 / 16.0, 1 / 16.0, false) / 16;
				
				if (radialDistance <= sRadius + 16) {
					double sphereHeight = Math.sqrt(sRadius * sRadius
							- Math.pow(centerPos.getX() - x, 2)
							- Math.pow(z - centerPos.getZ(), 2));
					
					for (int y = centerPos.getY() - (int) sphereHeight; y <= sphereHeight + centerPos.getY(); y++) {
						double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
						double noiseTemp = (noise + y / centerPos.getY());
						BlockState blockState = null;
						
						if (newRadialDistance <= sRadius - 1) {
							continue;
						}

						// We use the chunk's biome instead of world.getBiome to prevent out of bounds
						if (y * noiseTemp >= centerPos.getY()) {
							if (biome.isIn(BiomeTags.IS_NETHER)) {
								blockState = Blocks.RED_STAINED_GLASS.getDefaultState();
							} else if (isCaveBiome(biome)) {
								blockState = Blocks.TINTED_GLASS.getDefaultState();
							} else {
								blockState = Blocks.GLASS.getDefaultState();
							}
						} else {
							if (biome.isIn(BiomeTags.IS_NETHER)) {
								blockState = this.defaultNetherBlock;
							} else {
								blockState = this.defaultBlock;
								// Generate clay blobs (similar to granite/andesite/diorite)
								double clayNoise = this.noiseSampler.sample(x / 10.0, y / 10.0 + 500, z / 10.0, 1 / 16.0, 1 / 16.0, false);
								if (clayNoise > 0.6) {
									blockState = net.minecraft.block.Blocks.CLAY.getDefaultState();
								}
							}
						}
						
						if (blockState != null && y >= this.getMinimumY() && y < this.getWorldHeight() + this.getMinimumY()) {
							current.set(x, y, z);
							chunk.setBlockState(current, blockState, false);
						}
					}
					
					double largerSphereHeight = Math.sqrt((sRadius + 16) * (sRadius + 16)
							- Math.pow(centerPos.getX() - x, 2)
							- Math.pow(z - centerPos.getZ(), 2));
							
					for (int y = this.getMinimumY(); y < this.getWorldHeight() + this.getMinimumY(); y++) {
						double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
						if (newRadialDistance >= sRadius) {
							// Note: finishBiospheres runs AFTER features, replacing blocks outside sphere with AIR
							if (y >= this.getMinimumY() && y < this.getWorldHeight() + this.getMinimumY()) {
								if (isInsideProtectedStructure(x, y, z, protectedBoxes)) continue;
								if (!chunk.getBlockState(current).isAir()) {
									chunk.setBlockState(current, Blocks.AIR.getDefaultState(), false);
								}
							}
						} else if (newRadialDistance > sRadius - 1.0) {
							current.set(x, y, z);
							double noiseTemp = (noise + y / centerPos.getY());
							BlockState expected;
							if (y * noiseTemp >= centerPos.getY()) {
								if (biome.isIn(BiomeTags.IS_NETHER)) {
									expected = Blocks.RED_STAINED_GLASS.getDefaultState();
								} else if (isCaveBiome(biome)) {
									expected = Blocks.TINTED_GLASS.getDefaultState();
								} else {
									expected = Blocks.GLASS.getDefaultState();
								}
							} else {
								expected = biome.isIn(BiomeTags.IS_NETHER) ? this.defaultNetherBlock : this.defaultBlock;
							}
							if (!chunk.getBlockState(current).equals(expected)) {
								chunk.setBlockState(current, expected, false);
							}
						}
					}
				} else {
					for (int y = this.getMinimumY(); y < this.getWorldHeight() + this.getMinimumY(); y++) {
						if (isInsideProtectedStructure(x, y, z, protectedBoxes)) continue;
						current.set(x, y, z);
						if (!chunk.getBlockState(current).isAir()) {
							chunk.setBlockState(current, Blocks.AIR.getDefaultState(), false);
						}
					}
				}
				
				if (x == centerPos.getX() && z == centerPos.getZ()) {
					int topY = centerPos.getY() + (int) sRadius - 1;
					if (topY >= this.getMinimumY() && topY < this.getWorldHeight() + this.getMinimumY()) {
						BlockState topGlass;
						if (biome.isIn(BiomeTags.IS_NETHER)) topGlass = Blocks.RED_STAINED_GLASS.getDefaultState();
						else if (isCaveBiome(biome)) topGlass = Blocks.TINTED_GLASS.getDefaultState();
						else topGlass = Blocks.GLASS.getDefaultState();
						
						current.set(centerPos.getX(), topY, centerPos.getZ());
						chunk.setBlockState(current, topGlass, false);
					}
				}

				current.set(x, 0, z);
				this.makeBridges(current, centerPos, nesw, chunk, current, sRadius);
				this.makeOreSpheres(current, chunk, current, oreSpheres);
			}
		}
	}

	public void makeOreSpheres(BlockPos pos, Chunk chunk, BlockPos.Mutable current, OreSphere[] oreSpheres) {
		int x = pos.getX();
		int z = pos.getZ();
		for (int i = 0; i < 4; i++) {
			OreSphere ore = oreSpheres[i];
			if (ore == null) continue;
			
			double distToOreCenter = Math.sqrt(Math.pow(ore.center.getX() - x, 2) + Math.pow(ore.center.getZ() - z, 2));
			
			if (distToOreCenter <= ore.radius) {
				double height = Math.sqrt(ore.radius * ore.radius - Math.pow(ore.center.getX() - x, 2) - Math.pow(ore.center.getZ() - z, 2));
				for (int y = ore.center.getY() - (int) height; y <= ore.center.getY() + height; y++) {
					double dist3D = Math.sqrt(Math.pow(distToOreCenter, 2) + Math.pow(y - ore.center.getY(), 2));
					BlockState state;
					if (dist3D >= ore.radius - 1.0) {
						state = Blocks.GLASS.getDefaultState();
					} else {
						this.chunkRandom.setSeed(this.getActualSeed() + x * 341873128712L + y * 132897987541L + z * 543897123984L);
						state = randomOreBlock(x, y, z);
					}
					this.safeSetBlock(chunk, current, x, y, z, state);
				}
			}
			
			boolean isBridgeZ = (ore.center.getX() == ore.bridgeAttachPoint.getX());
			boolean isBridgeX = (ore.center.getZ() == ore.bridgeAttachPoint.getZ());
			
			if (isBridgeZ) {
				if (x >= ore.bridgeAttachPoint.getX() - 1 && x <= ore.bridgeAttachPoint.getX() + 1) {
					int minZ = Math.min(ore.bridgeAttachPoint.getZ(), ore.center.getZ());
					int maxZ = Math.max(ore.bridgeAttachPoint.getZ(), ore.center.getZ());
					if (z >= minZ && z <= maxZ) {
						if (distToOreCenter > ore.radius) {
							double t = (double)(z - ore.bridgeAttachPoint.getZ()) / (ore.center.getZ() - ore.bridgeAttachPoint.getZ());
							int y = (int) Math.round(ore.bridgeAttachPoint.getY() + t * (ore.center.getY() - ore.bridgeAttachPoint.getY()));
							
							this.chunkRandom.setCarverSeed(this.seed, x, z);
							if (this.chunkRandom.nextFloat() > 0.15f) {
								this.safeSetBlock(chunk, current, x, y, z, this.defaultBridge);
							}
						}
					}
				}
			} else if (isBridgeX) {
				if (z >= ore.bridgeAttachPoint.getZ() - 1 && z <= ore.bridgeAttachPoint.getZ() + 1) {
					int minX = Math.min(ore.bridgeAttachPoint.getX(), ore.center.getX());
					int maxX = Math.max(ore.bridgeAttachPoint.getX(), ore.center.getX());
					if (x >= minX && x <= maxX) {
						if (distToOreCenter > ore.radius) {
							double t = (double)(x - ore.bridgeAttachPoint.getX()) / (ore.center.getX() - ore.bridgeAttachPoint.getX());
							int y = (int) Math.round(ore.bridgeAttachPoint.getY() + t * (ore.center.getY() - ore.bridgeAttachPoint.getY()));
							
							this.chunkRandom.setCarverSeed(this.seed, x, z);
							if (this.chunkRandom.nextFloat() > 0.15f) {
								this.safeSetBlock(chunk, current, x, y, z, this.defaultBridge);
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

	public void makeBridges(BlockPos pos, BlockPos centerPos, BlockPos[] nesw, Chunk chunk, BlockPos.Mutable current, double sRadius) {
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

			if (i == 0 && z >= cz - 2 && z <= cz + 2 && x >= cx + (int)sRadius - 4) {
				t = (x - (cx + sRadius)) / L;
				isOnXAxis = true;
				isPositive = true;
			} else if (i == 1 && z >= cz - 2 && z <= cz + 2 && x <= cx - (int)sRadius + 4) {
				t = ((cx - sRadius) - x) / L;
				isOnXAxis = true;
				isPositive = false;
			} else if (i == 2 && x >= cx - 2 && x <= cx + 2 && z >= cz + (int)sRadius - 4) {
				t = (z - (cz + sRadius)) / L;
				isOnXAxis = false;
				isPositive = true;
			} else if (i == 3 && x >= cx - 2 && x <= cx + 2 && z <= cz - (int)sRadius + 4) {
				t = ((cz - sRadius) - z) / L;
				isOnXAxis = false;
				isPositive = false;
			}

			if (t >= -1.5 / L && t <= 1.0 + 1.5 / L) {
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
				
				this.fillBridgeSlice(new BlockPos(x, finalY, z), centerPos, chunk, current, isOnXAxis, isPositive, clearOnly);
			}
		}
	}

	private void safeSetBlock(Chunk chunk, BlockPos.Mutable current, int x, int y, int z, BlockState state) {
		if (y >= this.getMinimumY() && y < this.getWorldHeight() + this.getMinimumY()) {
			chunk.setBlockState(current.set(x, y, z), state, false);
		}
	}

	public void fillBridgeSlice(BlockPos pos, BlockPos centerPos, Chunk chunk, BlockPos.Mutable current, boolean isOnXAxis, boolean isPositive, boolean clearOnly) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		int cx = centerPos.getX();
		int cz = centerPos.getZ();
		
		// Note: in finishBiospheres, modifying chunk blocks that are outside the current chunk boundary is problematic.
		// We only set blocks if they fall within the current chunk!
		if (x >= chunk.getPos().getStartX() && x <= chunk.getPos().getEndX() &&
			z >= chunk.getPos().getStartZ() && z <= chunk.getPos().getEndZ()) {
			
			if (!clearOnly) {
				this.safeSetBlock(chunk, current, x, y - 1, z, this.defaultBridge);
			}
			this.safeSetBlock(chunk, current, x, y, z, Blocks.AIR.getDefaultState());
			this.safeSetBlock(chunk, current, x, y + 1, z, Blocks.AIR.getDefaultState());
			this.safeSetBlock(chunk, current, x, y + 2, z, Blocks.AIR.getDefaultState());
			this.safeSetBlock(chunk, current, x, y + 3, z, Blocks.AIR.getDefaultState());
			
			if (!clearOnly) {
				if (isOnXAxis) {
					if (z == cz + 2 || z == cz - 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.block.FenceBlock) {
							edge = edge.with(net.minecraft.block.FenceBlock.EAST, true).with(net.minecraft.block.FenceBlock.WEST, true);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					}
				} else {
					if (x == cx + 2 || x == cx - 2) {
						BlockState edge = this.defaultEdge;
						if (edge.getBlock() instanceof net.minecraft.block.FenceBlock) {
							edge = edge.with(net.minecraft.block.FenceBlock.NORTH, true).with(net.minecraft.block.FenceBlock.SOUTH, true);
						}
						this.safeSetBlock(chunk, current, x, y, z, edge);
					}
				}
			}
		}
	}

	@Override
	public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
		BlockPos centerPos = this.getNearestCenterSphere(new BlockPos(x, 0, z));
		double radialDistance = Math.sqrt(Math.pow(x - centerPos.getX(), 2) + Math.pow(z - centerPos.getZ(), 2));
		if (radialDistance < sphereRadius) {
			return centerPos.getY();
		}
		return world.getBottomY();
	}

	@Override
	public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
		return new VerticalBlockSample(world.getBottomY(), new BlockState[0]);
	}

	private boolean isCaveBiome(RegistryEntry<Biome> biome) {
		net.minecraft.registry.tag.TagKey<Biome> CAVES_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("c", "caves"));
		net.minecraft.registry.tag.TagKey<Biome> IS_CAVE_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, net.minecraft.util.Identifier.of("c", "is_cave"));
		if (biome.isIn(CAVES_TAG) || biome.isIn(IS_CAVE_TAG)) return true;
		if (biome.matchesKey(net.minecraft.world.biome.BiomeKeys.LUSH_CAVES)) return true;
		if (biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DRIPSTONE_CAVES)) return true;
		if (biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DEEP_DARK)) return true;
		return false;
	}

	@Override
	public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {}

	@Override
	public int getWorldHeight() { return 384; }

	@Override
	public int getSeaLevel() { return 63; }

	@Override
	public int getMinimumY() { return -64; }

	@Override
	public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, net.minecraft.world.biome.source.BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, net.minecraft.world.gen.GenerationStep.Carver carverStep) {}

	@Override
	public void populateEntities(ChunkRegion region) {}

	@Override
	public void setStructureStarts(net.minecraft.registry.DynamicRegistryManager registryManager, net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator placementCalculator, net.minecraft.world.gen.StructureAccessor structureAccessor, Chunk chunk, net.minecraft.structure.StructureTemplateManager structureTemplateManager) {
		super.setStructureStarts(registryManager, placementCalculator, structureAccessor, chunk, structureTemplateManager);

		java.util.Map<net.minecraft.world.gen.structure.Structure, net.minecraft.structure.StructureStart> starts = chunk.getStructureStarts();
		for (java.util.Map.Entry<net.minecraft.world.gen.structure.Structure, net.minecraft.structure.StructureStart> entry : starts.entrySet()) {
			net.minecraft.world.gen.structure.Structure structure = entry.getKey();
			net.minecraft.structure.StructureStart start = entry.getValue();

			if (start != null && start.hasChildren()) {
				net.minecraft.util.Identifier id = registryManager.get(net.minecraft.registry.RegistryKeys.STRUCTURE).getId(structure);
				if (id != null) {
					String path = id.getPath();
					if (path.equals("mansion") || path.equals("trial_chambers")) {
						chunk.setStructureStart(structure, net.minecraft.structure.StructureStart.DEFAULT);
						continue;
					}
				}

				net.minecraft.util.math.ChunkPos cPos = start.getPos();
				net.minecraft.util.math.BlockPos startPos = new net.minecraft.util.math.BlockPos(cPos.getStartX(), 0, cPos.getStartZ());
				net.minecraft.util.math.BlockPos centerPos = this.getNearestCenterSphere(startPos);
				double distance = Math.sqrt(centerPos.getSquaredDistance(startPos.getX(), centerPos.getY(), startPos.getZ()));

				double sRadius = this.getSphereRadius(centerPos.getX(), centerPos.getZ());
				if (distance > sRadius) {
					chunk.setStructureStart(structure, net.minecraft.structure.StructureStart.DEFAULT);
				}
			}
		}
	}
	private boolean isInsideProtectedStructure(int x, int y, int z, java.util.List<net.minecraft.util.math.BlockBox> protectedBoxes) {
		for (net.minecraft.util.math.BlockBox box : protectedBoxes) {
			if (x >= box.getMinX() && x <= box.getMaxX() && y >= box.getMinY() && y <= box.getMaxY() && z >= box.getMinZ() && z <= box.getMaxZ()) {
				return true;
			}
		}
		return false;
	}
}