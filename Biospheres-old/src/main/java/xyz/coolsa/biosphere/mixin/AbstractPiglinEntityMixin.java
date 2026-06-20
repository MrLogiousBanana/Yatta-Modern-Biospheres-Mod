package xyz.coolsa.biosphere.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractPiglinEntity.class)
public class AbstractPiglinEntityMixin extends HostileEntity {
    protected AbstractPiglinEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Redirect(method = "shouldZombify", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;isPiglinSafe()Z"))
    private boolean biomeZombify(DimensionType dimensionType) {
        return this.getEntityWorld().getBiome(this.getBlockPos()).getCategory() == Biome.Category.NETHER || dimensionType.isPiglinSafe();
    }
}
