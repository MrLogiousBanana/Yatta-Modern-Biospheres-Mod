package xyz.coolsa.biosphere.mixin;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BedBlock.class)
public class BedBlockMixin extends HorizontalFacingBlock implements BlockEntityProvider {
    protected BedBlockMixin(Settings settings) {
        super(settings);
    }

    @Redirect(method = "onUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BedBlock;isOverworld(Lnet/minecraft/world/World;)Z"))
    public boolean isOverworldBiome(World world, BlockState state, World bixin, BlockPos pos) {
        return world.getBiome(pos).getCategory() != Biome.Category.NETHER;
    }

    @Shadow
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
