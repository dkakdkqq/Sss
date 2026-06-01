package fun.lumis.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fun.lumis.Lumis;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.impl.render.Removals;
import fun.lumis.client.modules.impl.render.WorldTweaks;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "getFogModifier(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/BackgroundRenderer$StatusEffectFogModifier;", at = @At("HEAD"), cancellable = true)
    private static void lumis$getFogModifier(Entity entity, float tickDelta, CallbackInfoReturnable<Object> cir) {
        if (ModuleClass.INSTANCE == null) return;

        Removals removals = ModuleClass.removals;
        if (removals != null && removals.isEnabled("Плохие эффекты")) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void lumis$applyFog(
            net.minecraft.client.render.Camera camera,
            BackgroundRenderer.FogType fogType,
            org.joml.Vector4f color,
            float viewDistance,
            boolean thickenFog,
            float tickDelta,
            CallbackInfoReturnable<Fog> cir
    ) {
        if (ModuleClass.INSTANCE == null) return;

        WorldTweaks tweaks = ModuleClass.worldTweaks;
        if (tweaks == null || !tweaks.isFogEnabled()) return;

        float fogDistance = Math.max(12.0f, tweaks.getFogDistance());
        float fogEnd = Math.min(viewDistance, fogDistance);
        float fogStart = Math.max(0.0f, fogEnd * 0.05f);
        int color1 = tweaks.getFogColor();

        cir.setReturnValue(new Fog(
                fogStart,
                fogEnd,
                FogShape.SPHERE,
                ColorUtils.redf(color1),
                ColorUtils.greenf(color1),
                ColorUtils.bluef(color1),
                1.0f
        ));
    }
}
