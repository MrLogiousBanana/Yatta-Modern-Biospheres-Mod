package xyz.coolsa.biosphere.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.Hoglin;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HoglinEntity.class)
public abstract class HoglinEntityMixin extends AnimalEntity implements Monster, Hoglin {
    protected HoglinEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Shadow public abstract int getMovementCooldownTicks();
    @Shadow public abstract PassiveEntity createChild(ServerWorld world, PassiveEntity entity);

    @Redirect(method = "canConvert", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;isPiglinSafe()Z"))
    private boolean biomeZombify(DimensionType dimensionType) {
        return this.getEntityWorld().getBiome(this.getBlockPos()).getCategory() == Biome.Category.NETHER  || dimensionType.isPiglinSafe();
    }

}
