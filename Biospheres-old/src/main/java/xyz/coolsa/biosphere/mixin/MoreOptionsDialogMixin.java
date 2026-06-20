package xyz.coolsa.biosphere.mixin;

import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.coolsa.biosphere.Biospheres;
import xyz.coolsa.biosphere.BiospheresChunkGenerator;


@Mixin(MoreOptionsDialog.class)
public class MoreOptionsDialogMixin {
    @Shadow private GeneratorOptions generatorOptions;
    @Shadow private ButtonWidget customizeTypeButton;



    @Inject(at = @At("RETURN"), method = "setVisible")
    private void injectBiospheres(boolean visible, CallbackInfo ci) {
//        if (true) {
//            this.customizeTypeButton.visible = visible;
//        }
    }
}
