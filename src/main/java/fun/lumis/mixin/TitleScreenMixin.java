package fun.lumis.mixin;

import fun.lumis.client.ui.mainmenu.LumisMainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Заменяет ванильное главное меню (TitleScreen) на кастомное {@link LumisMainMenu}.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void lumis$replaceTitleScreen(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        if (mc.currentScreen instanceof LumisMainMenu) {
            return;
        }
        mc.setScreen(new LumisMainMenu());
        ci.cancel();
    }
}
