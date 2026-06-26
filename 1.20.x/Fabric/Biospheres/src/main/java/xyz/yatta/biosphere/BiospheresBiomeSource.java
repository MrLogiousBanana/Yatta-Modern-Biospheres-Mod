package xyz.yatta.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper.Impl;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BiospheresBiomeSource extends BiomeSource {

	public static final Codec<BiospheresBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance
			.group(
					RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME),
					Codec.LONG.fieldOf("seed").forGetter(BiospheresBiomeSource::getSeed),
					Codec.INT.fieldOf("sphere_distance").forGetter(generator -> generator.sphereDistance),
					Codec.INT.fieldOf("sphere_radius").forGetter(generator -> generator.sphereRadius)
			).apply(instance, instance.stable(BiospheresBiomeSource::new)));

	private final RegistryEntryLookup<Biome> biomeLookup;
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

	private List<RegistryEntry<Biome>> availableBiomes = null;
	private List<RegistryEntry<Biome>> caveBiomes = null;

	public BiospheresBiomeSource(RegistryEntryLookup<Biome> biomeLookup, long long_1, int sphereDistance, int sphereRadius) {
		this.seed = long_1;
		this.biomeLookup = biomeLookup;
		this.sphereDistance = sphereDistance;
		this.sphereRadius = sphereRadius;
	}

	public RegistryEntryLookup<Biome> getBiomeLookup() {
		return this.biomeLookup;
	}

	public long getSeed() {
		return this.seed;
	}

	@Override
	protected Codec<? extends BiomeSource> getCodec() {
		return CODEC;
	}
	
	private void initBiomes() {
		if (this.availableBiomes == null) {
			Optional<RegistryEntryList.Named<Biome>> overworldBiomes = biomeLookup.getOptional(BiomeTags.IS_OVERWORLD);
			if (overworldBiomes.isPresent()) {
				this.availableBiomes = overworldBiomes.get().stream().collect(Collectors.toList());
			} else {
				// Fallback if tag is somehow missing
				this.availableBiomes = ImmutableList.of(biomeLookup.getOrThrow(BiomeKeys.PLAINS));
			}

			// Collect cave biomes
			this.caveBiomes = new java.util.ArrayList<>();
			net.minecraft.registry.tag.TagKey<Biome> CAVES_TAG = net.minecraft.registry.tag.TagKey.of(RegistryKeys.BIOME, net.minecraft.util.Identifier.of("c", "caves"));
			net.minecraft.registry.tag.TagKey<Biome> IS_CAVE_TAG = net.minecraft.registry.tag.TagKey.of(RegistryKeys.BIOME, net.minecraft.util.Identifier.of("c", "is_cave"));
			
			biomeLookup.getOptional(CAVES_TAG).ifPresent(tag -> this.caveBiomes.addAll(tag.stream().collect(Collectors.toList())));
			biomeLookup.getOptional(IS_CAVE_TAG).ifPresent(tag -> {
				for (RegistryEntry<Biome> b : tag) {
					if (!this.caveBiomes.contains(b)) this.caveBiomes.add(b);
				}
			});

			if (this.caveBiomes.isEmpty()) {
				try { this.caveBiomes.add(biomeLookup.getOrThrow(BiomeKeys.LUSH_CAVES)); } catch (Exception ignored) {}
				try { this.caveBiomes.add(biomeLookup.getOrThrow(BiomeKeys.DRIPSTONE_CAVES)); } catch (Exception ignored) {}
				try { this.caveBiomes.add(biomeLookup.getOrThrow(BiomeKeys.DEEP_DARK)); } catch (Exception ignored) {}
			}
			if (this.caveBiomes.isEmpty()) {
				this.caveBiomes.add(this.availableBiomes.get(0)); // Ultimate fallback
			}
		}
	}

	@Override
	protected Stream<RegistryEntry<Biome>> biomeStream() {
		this.initBiomes();
		return Stream.concat(
			this.availableBiomes.stream(),
			Stream.of(biomeLookup.getOrThrow(BiomeKeys.THE_VOID))
		);
	}

	@Override
	public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
		if (this.actualSeed == null) {
			net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseValuePoint tp1 = noise.sample(1337, 64, 7331);
			net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseValuePoint tp2 = noise.sample(-7331, 64, -1337);
			net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseValuePoint tp3 = noise.sample(5003, 64, -2003);
			long seedHash = tp1.temperatureNoise() ^ Long.rotateLeft(tp2.humidityNoise(), 21) ^ Long.rotateLeft(tp3.continentalnessNoise(), 42);
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
		return biomeLookup.getOrThrow(BiomeKeys.THE_VOID);
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
		return Math.sqrt(center.getSquaredDistance(biomeX * 4, 0, biomeZ * 4));
	}

	public RegistryEntry<Biome> getBiomeForSphere(int biomeX, int biomeZ) {
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
