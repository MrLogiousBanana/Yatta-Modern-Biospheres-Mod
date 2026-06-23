package xyz.yatta.biosphere;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class Biospheres implements ModInitializer {
	public static BiosphereConfig bsconfig = new BiosphereConfig(48, 5, 12);
	public Gson daData = new GsonBuilder().setPrettyPrinting().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
	Path configPath = Paths.get("config/biospheres.json");

	public void saveDaData() {
		try{
			if (configPath.toFile().exists()) {
				bsconfig = daData.fromJson(new String(Files.readAllBytes(configPath)), BiosphereConfig.class);
			} else {
				Files.write(configPath, Collections.singleton(daData.toJson(bsconfig)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onInitialize() {
		saveDaData();
		Registry.register(Registries.CHUNK_GENERATOR, new Identifier("yatta","biosphere"), BiospheresChunkGenerator.CODEC);
		Registry.register(Registries.BIOME_SOURCE, new Identifier("yatta","biosphere_biomes"), BiospheresBiomeSource.CODEC);

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			handlePlayerSpawn(handler.getPlayer());
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			handlePlayerSpawn(newPlayer);
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof net.minecraft.entity.mob.SlimeEntity && !(entity instanceof net.minecraft.entity.mob.MagmaCubeEntity)) {
				if (world.getChunkManager().getChunkGenerator() instanceof BiospheresChunkGenerator gen) {
					net.minecraft.util.math.BlockPos center = gen.getNearestCenterSphere(entity.getBlockPos());
					if (!BiospheresChunkGenerator.isSlimeSphere(center.getX(), center.getZ(), gen.seed)) {
						net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome> biome = world.getBiome(entity.getBlockPos());
						if (!biome.isIn(net.minecraft.registry.tag.BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)) {
							entity.discard();
						}
					}
				}
			}
		});

		System.out.println("Loaded Biospheres Mod!");
	}

	private void handlePlayerSpawn(net.minecraft.server.network.ServerPlayerEntity player) {
		if (player.getServerWorld().getChunkManager().getChunkGenerator() instanceof BiospheresChunkGenerator) {
			if (player.getSpawnPointPosition() == null) {
				net.minecraft.util.math.BlockPos currentPos = player.getBlockPos();
				if (currentPos.getY() > 60) {
					for (int y = currentPos.getY(); y > 0; y--) {
						net.minecraft.block.BlockState state = player.getServerWorld().getBlockState(new net.minecraft.util.math.BlockPos(currentPos.getX(), y, currentPos.getZ()));
						if (!state.isAir() && !state.isOf(Blocks.GLASS) && !state.isOf(Blocks.RED_STAINED_GLASS) && !state.isOf(Blocks.TINTED_GLASS) && !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) && !state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
							// Found the solid ground
							player.teleport(player.getServerWorld(), currentPos.getX() + 0.5, y + 1, currentPos.getZ() + 0.5, player.getYaw(), player.getPitch());
							break;
						}
					}
				}
			}
		}
	}
}
