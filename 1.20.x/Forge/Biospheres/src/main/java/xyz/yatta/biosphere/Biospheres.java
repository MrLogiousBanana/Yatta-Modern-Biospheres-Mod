package xyz.yatta.biosphere;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@Mod("biospheres")
public class Biospheres {
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

	public Biospheres() {
		IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
		saveDaData();

		modEventBus.addListener(this::registerCodecs);

		MinecraftForge.EVENT_BUS.addListener(this::onPlayerJoin);
		MinecraftForge.EVENT_BUS.addListener(this::onPlayerRespawn);
		MinecraftForge.EVENT_BUS.addListener(this::onCheckSpawn);

		System.out.println("Loaded Biospheres Mod!");
	}

	private void registerCodecs(RegisterEvent event) {
		event.register(BuiltInRegistries.CHUNK_GENERATOR.key(), helper -> {
			helper.register(new ResourceLocation("yatta", "biosphere"), BiospheresChunkGenerator.CODEC);
		});
		event.register(BuiltInRegistries.BIOME_SOURCE.key(), helper -> {
			helper.register(new ResourceLocation("yatta", "biosphere_biomes"), BiospheresBiomeSource.CODEC);
		});
	}

	private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
			handlePlayerSpawn(player);
		}
	}

	private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
			handlePlayerSpawn(player);
		}
	}

	private void handlePlayerSpawn(net.minecraft.server.level.ServerPlayer player) {
		if (player.serverLevel().getChunkSource().getGenerator() instanceof BiospheresChunkGenerator) {
			if (player.getRespawnPosition() == null) {
				net.minecraft.core.BlockPos currentPos = player.blockPosition();
				if (currentPos.getY() > 60) {
					for (int y = currentPos.getY(); y > 0; y--) {
						net.minecraft.world.level.block.state.BlockState state = player.serverLevel().getBlockState(new net.minecraft.core.BlockPos(currentPos.getX(), y, currentPos.getZ()));
						if (!state.isAir() && !state.is(Blocks.GLASS) && !state.is(Blocks.RED_STAINED_GLASS) && !state.is(Blocks.TINTED_GLASS) && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.is(net.minecraft.tags.BlockTags.LOGS)) {
							// Found the solid ground
							player.teleportTo(player.serverLevel(), currentPos.getX() + 0.5, y + 1, currentPos.getZ() + 0.5, Collections.emptySet(), player.getYRot(), player.getXRot());
							break;
						}
					}
				}
			}
		}
	}

	private void onCheckSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
		if (event.getEntity() instanceof net.minecraft.world.entity.monster.Slime && !(event.getEntity() instanceof net.minecraft.world.entity.monster.MagmaCube)) {
			net.minecraft.server.level.ServerLevel level = event.getLevel().getLevel();
			if (level.getChunkSource().getGenerator() instanceof BiospheresChunkGenerator gen) {
				net.minecraft.core.BlockPos center = gen.getNearestCenterSphere(event.getEntity().blockPosition());
				if (!BiospheresChunkGenerator.isSlimeSphere(center.getX(), center.getZ(), gen.seed)) {
					net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(event.getEntity().blockPosition());
					if (!biome.is(net.minecraft.tags.BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)) {
						event.setSpawnCancelled(true);
						event.setCanceled(true);
					}
				}
			}
		}
	}
}
