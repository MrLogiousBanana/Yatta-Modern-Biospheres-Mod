package xyz.yatta.biosphere;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public abstract class BiosphereBiomesFluidMethodImGoodAtNames {
    public static int significantlyBetterLavaFlowSpeedGetter(WorldView world, BlockPos pos, BlockState blockState) {
        if (blockState.isOf(Blocks.LAVA)) {
            return world.getBiome(pos).isIn(BiomeTags.IS_NETHER) ? 10 : 30;
        } else {
            return 5; // Water
        }
    }
}
