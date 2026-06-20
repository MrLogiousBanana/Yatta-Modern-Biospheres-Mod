package xyz.coolsa.biosphere.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FluidModificationItem;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BucketItem.class)
public class BucketItemMixin extends Item implements FluidModificationItem {
    public BucketItemMixin(Settings settings) { super(settings); }
    @Shadow
    public boolean placeFluid(@Nullable PlayerEntity player, World world, BlockPos pos, @Nullable BlockHitResult hitResult) {
        return false;
    }
    @Redirect(method = "placeFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;isUltrawarm()Z"))
    private boolean bucketBiome(DimensionType dimensionType, @Nullable PlayerEntity player, World world, BlockPos pos, @Nullable BlockHitResult hitResult) {
        return world.getBiome(hitResult.getBlockPos().offset(hitResult.getSide())).getCategory() == Biome.Category.NETHER || dimensionType.isUltrawarm();
    }
}
