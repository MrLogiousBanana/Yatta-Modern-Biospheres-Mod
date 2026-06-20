package xyz.coolsa.biosphere.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin extends Block {
    @Shadow public static boolean isNether(World world) { return false; }

    public RespawnAnchorBlockMixin(Settings settings) {
        super(settings);
    }
    @Redirect(method = "onUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/RespawnAnchorBlock;isNether(Lnet/minecraft/world/World;)Z"))
    public boolean isNetherBiome(World world, BlockState state, World bixin, BlockPos pos) {
        return world.getBiome(pos).getCategory() == Biome.Category.NETHER || isNether(world);
    }
}
