package xyz.yatta.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.BiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BiospheresBiomeSource extends BiomeSource {

	public static final MapCodec<BiospheresBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
			.group(
					RegistryOps.retrieveGetter(Registries.BIOME),
					Codec.LONG.fieldOf("seed").forGetter(BiospheresBiomeSource::getSeed),
					Codec.INT.fieldOf("sphere_distance").forGetter(generator -> generator.sphereDistance),
					Codec.INT.fieldOf("sphere_radius").forGetter(generator -> generator.sphereRadius)
			).apply(instance, instance.stable(BiospheresBiomeSource::new)));

	private final HolderGetter<Biome> biomeLookup;
	private final long seed;
	protected final int sphereDistance;
	protected final int sphereRadius;
	
	private Long actualSeed = null;

	public void setWorldSeed(long s) {
		if (this.actualSeed == null && s != 0) this.actualSeed = s;
	}

	public boolean hasActualSeed() {
		return this.actualSeed != null;
	}

	public long getActualSeed() {
		return this.actualSeed != null ? this.actualSeed : this.seed;
	}

	private List<Holder<Biome>> availableBiomes = null;
	private List<Holder<Biome>> caveBiomes = null;

	public BiospheresBiomeSource(HolderGetter<Biome> biomeLookup, long long_1, int sphereDistance, int sphereRadius) {
		this.seed = long_1;
		this.biomeLookup = biomeLookup;
		this.sphereDistance = sphereDistance;
		this.sphereRadius = sphereRadius;
	}

	public HolderGetter<Biome> getBiomeLookup() {
		return this.biomeLookup;
	}

	public long getSeed() {
		return this.seed;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}
	
	private void initBiomes() {
		if (this.availableBiomes == null) {
			Optional<HolderSet.Named<Biome>> overworldBiomes = biomeLookup.get(BiomeTags.IS_OVERWORLD);
			if (overworldBiomes.isPresent()) {
				this.availableBiomes = overworldBiomes.get().stream().collect(Collectors.toList());
			} else {
				// Fallback if tag is somehow missing
				this.availableBiomes = ImmutableList.of(biomeLookup.getOrThrow(Biomes.PLAINS));
			}

			// Collect cave biomes
			this.caveBiomes = new java.util.ArrayList<>();
			net.minecraft.tags.TagKey<Biome> CAVES_TAG = net.minecraft.tags.TagKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "caves"));
			net.minecraft.tags.TagKey<Biome> IS_CAVE_TAG = net.minecraft.tags.TagKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "is_cave"));
			
			biomeLookup.get(CAVES_TAG).ifPresent(tag -> this.caveBiomes.addAll(tag.stream().collect(Collectors.toList())));
			biomeLookup.get(IS_CAVE_TAG).ifPresent(tag -> {
				for (Holder<Biome> b : tag) {
					if (!this.caveBiomes.contains(b)) this.caveBiomes.add(b);
				}
			});

			if (this.caveBiomes.isEmpty()) {
				try { this.caveBiomes.add(biomeLookup.getOrThrow(Biomes.LUSH_CAVES)); } catch (Exception ignored) {}
				try { this.caveBiomes.add(biomeLookup.getOrThrow(Biomes.DRIPSTONE_CAVES)); } catch (Exception ignored) {}
				try { this.caveBiomes.add(biomeLookup.getOrThrow(Biomes.DEEP_DARK)); } catch (Exception ignored) {}
			}
			if (this.caveBiomes.isEmpty()) {
				this.caveBiomes.add(this.availableBiomes.get(0)); // Ultimate fallback
			}
		}
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		this.initBiomes();
		return Stream.concat(
			this.availableBiomes.stream(),
			Stream.of(biomeLookup.getOrThrow(Biomes.THE_VOID))
		);
	}

	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
		if (this.actualSeed == null) {
			net.minecraft.world.level.biome.Climate.TargetPoint tp1 = noise.sample(1337, 64, 7331);
			net.minecraft.world.level.biome.Climate.TargetPoint tp2 = noise.sample(-7331, 64, -1337);
			net.minecraft.world.level.biome.Climate.TargetPoint tp3 = noise.sample(5003, 64, -2003);
			long seedHash = tp1.temperature() ^ Long.rotateLeft(tp2.humidity(), 21) ^ Long.rotateLeft(tp3.continentalness(), 42);
			if (seedHash != 0) {
				this.setWorldSeed(seedHash);
			}
		}

		int centerX = (int) Math.round(x * 4 / (double) this.sphereDistance) * this.sphereDistance;
		int centerZ = (int) Math.round(z * 4 / (double) this.sphereDistance) * this.sphereDistance;
		double currentRadius = this.getSphereRadius(centerX, centerZ);
		
		if (this.getDistanceFromSphere(x + 1, z + 1) < currentRadius + 6) {
			return this.getBiomeForSphere(x, z);
		}
		return biomeLookup.getOrThrow(Biomes.THE_VOID);
	}

	public double getSphereRadius(int centerX, int centerZ) {
		double maxRadius = this.sphereRadius * 1.1;
		double minRadius = this.sphereRadius * 0.8;
		java.util.Random random = new java.util.Random(this.getActualSeed() + 1L + (long) centerX * 341873128712L + (long) centerZ * 132897987541L);
		return random.nextDouble() * (maxRadius - minRadius) + minRadius;
	}

	public double getDistanceFromSphere(int biomeX, int biomeZ) {
		int centerX = (int) Math.round(biomeX * 4 / (double) this.sphereDistance) * this.sphereDistance;
		int centerZ = (int) Math.round(biomeZ * 4 / (double) this.sphereDistance) * this.sphereDistance;
		BlockPos center = new BlockPos(centerX, 0, centerZ);
		return Math.sqrt(center.distSqr(new net.minecraft.core.Vec3i(biomeX * 4, 0, biomeZ * 4)));
	}

	public Holder<Biome> getBiomeForSphere(int biomeX, int biomeZ) {
		this.initBiomes();
		int centerX = (int) Math.round(biomeX * 4 / (double) this.sphereDistance) * this.sphereDistance;
		int centerZ = (int) Math.round(biomeZ * 4 / (double) this.sphereDistance) * this.sphereDistance;
		java.util.Random r = new java.util.Random(this.getActualSeed() + (long)centerX * 341873128712L + (long)centerZ * 132897987541L);
		
		if (r.nextInt(50) == 0 && !this.caveBiomes.isEmpty()) {
			return this.caveBiomes.get(r.nextInt(this.caveBiomes.size()));
		}
		
		int randomChoice = r.nextInt(this.availableBiomes.size());
		return this.availableBiomes.get(randomChoice);
	}
}

