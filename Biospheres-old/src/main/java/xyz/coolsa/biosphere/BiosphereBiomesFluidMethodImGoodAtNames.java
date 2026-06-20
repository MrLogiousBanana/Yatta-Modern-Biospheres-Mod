package xyz.coolsa.biosphere;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

public abstract class BiosphereBiomesFluidMethodImGoodAtNames {
    public static int significantlyBetterLavaFlowSpeedGetter(WorldView world, BlockPos pos, BlockState blockState) {
        System.out.println(world);
        System.out.println(pos);
        System.out.println(blockState);
        if (blockState.getBlock() == Blocks.LAVA) {
            return world.getBiome(pos).getCategory() == Biome.Category.NETHER ? 10 : 30;
        } else {
            return 5; //Water
        }
    }
}
