package xyz.coolsa.biosphere.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.WetSpongeBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WetSpongeBlock.class)
public class WetSpongeBlockMixin {
    @Redirect(method = "onBlockAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;isUltrawarm()Z"))
    private boolean isNetherBiome(DimensionType dimensionType, BlockState state, World world, BlockPos pos) {
        return world.getBiome(pos).getCategory() == Biome.Category.NETHER || dimensionType.isUltrawarm();
    }
}
