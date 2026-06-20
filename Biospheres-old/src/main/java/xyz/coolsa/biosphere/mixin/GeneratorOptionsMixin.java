package xyz.coolsa.biosphere.mixin;
//Copied from Modern Beta because I dum, but modified because I smort enough to do that at least.

import com.google.common.base.MoreObjects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.coolsa.biosphere.Biospheres;
import xyz.coolsa.biosphere.BiospheresBiomeSource;
import xyz.coolsa.biosphere.BiospheresChunkGenerator;
import xyz.coolsa.biosphere.OverworldBiosphereGen;

import java.util.Properties;
import java.util.Random;

@Mixin(GeneratorOptions.class)
public class GeneratorOptionsMixin {
    @Unique private static final String BIOSPHERE_LEVEL_TYPE = "biosphere";
    @Unique private static final String ALT_BIOSPHERE_LEVEL_TYPE = "alt-biosphere";

    @Inject(method = "fromProperties", at = @At("HEAD"), cancellable = true)
    private static void injectServerGeneratorType(DynamicRegistryManager dynamicRegistryManager, Properties properties, CallbackInfoReturnable<GeneratorOptions> cir) {
        if (properties.get("level-type") == null) {
            return;
        }

        String levelType = properties.get("level-type").toString().trim().toLowerCase();

        // get or generate seed
        String seedField = (String) MoreObjects.firstNonNull(properties.get("level-seed"), "");
        long seed = new Random().nextLong();

        if (!seedField.isEmpty()) {
            try {
                long parsedSeed = Long.parseLong(seedField);
                if (parsedSeed != 0L) {
                    seed = parsedSeed;
                }
            } catch (NumberFormatException var14) {
                seed = seedField.hashCode();
            }
        }
        Registry<DimensionType> dimensionTypeRegistry = dynamicRegistryManager.get(Registry.DIMENSION_TYPE_KEY);
        Registry<ChunkGeneratorSettings> chunkGeneratorSettingsRegistry = dynamicRegistryManager.get(Registry.CHUNK_GENERATOR_SETTINGS_KEY);
        Registry<Biome> biomeRegistry = dynamicRegistryManager.get(Registry.BIOME_KEY);

        SimpleRegistry<DimensionOptions> dimensionOptions = DimensionType.createDefaultDimensionOptions(
                dimensionTypeRegistry,
                biomeRegistry,
                chunkGeneratorSettingsRegistry,
                seed
        );

        String generate_structures = (String) properties.get("generate-structures");
        boolean generateStructures = generate_structures == null || Boolean.parseBoolean(generate_structures);

        // If "biosphere"
        if (levelType.equals(BIOSPHERE_LEVEL_TYPE)) {
            ChunkGenerator biospheresChunkGenerator = new BiospheresChunkGenerator(new BiospheresBiomeSource(biomeRegistry, seed), seed, Biospheres.bsconfig.sphereRadius * 4, Biospheres.bsconfig.sphereRadius, Biospheres.bsconfig.lakeRadius, Biospheres.bsconfig.shoreRadius);

            // return Biospheres
            cir.setReturnValue(new GeneratorOptions(
                    seed,
                    generateStructures,
                    false,
                    GeneratorOptions.getRegistryWithReplacedOverworldGenerator(dimensionTypeRegistry, dimensionOptions, biospheresChunkGenerator)));
        } else if (levelType.equals(ALT_BIOSPHERE_LEVEL_TYPE)) {
            ChunkGenerator altBiospheresChunkGenerator = new OverworldBiosphereGen(new VanillaLayeredBiomeSource(seed, false, false, biomeRegistry), seed, () -> { return (ChunkGeneratorSettings)chunkGeneratorSettingsRegistry.getOrThrow(ChunkGeneratorSettings.OVERWORLD);});

            // return Alt-Biospheres
            cir.setReturnValue(new GeneratorOptions(
                    seed,
                    generateStructures,
                    false,
                    GeneratorOptions.getRegistryWithReplacedOverworldGenerator(dimensionTypeRegistry, dimensionOptions, altBiospheresChunkGenerator)));
        }
    }
}
