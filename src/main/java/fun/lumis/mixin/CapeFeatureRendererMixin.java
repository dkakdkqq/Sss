package fun.lumis.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.impl.render.Chams;

@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin implements QClient {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lumis$hideCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState playerState, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (ModuleClass.INSTANCE == null || mc.world == null) {
            return;
        }

        Chams chams = ModuleClass.chams;
        if (chams == null || !chams.isEnable()) {
            return;
        }

        Entity entity = mc.world.getEntityById(playerState.id);
        if (entity instanceof PlayerEntity player && chams.shouldHideItemsAndCape(player)) {
            ci.cancel();
        }
    }
}
