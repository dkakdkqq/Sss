package fun.lumis.mixin;

import com.google.common.base.MoreObjects;
import lombok.SneakyThrows;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.Lumis;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.render.hands.ShaderHandsRenderer;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.render.ShaderHands;
import fun.lumis.client.modules.impl.render.SwingAnimations;
import fun.lumis.client.modules.impl.render.ViewModel;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private float equipProgressMainHand;
    @Shadow private float prevEquipProgressMainHand;
    @Shadow protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);
    @Shadow private float prevEquipProgressOffHand;
    @Shadow private float equipProgressOffHand;
    @Shadow private ItemStack offHand;


    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void onRenderItemHead(float tickProgress, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, ClientPlayerEntity player, int light, CallbackInfo ci) {
        ShaderHands shaderHands = getShaderHands();
        if (shaderHands == null || !shaderHands.isEnable()) return;
        ShaderHandsRenderer.getInstance().captureBeforeHands();
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
    private void onRenderItemTail(float tickProgress, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, ClientPlayerEntity player, int light, CallbackInfo ci) {
        ShaderHands shaderHands = getShaderHands();
        if (shaderHands == null || !shaderHands.isEnable()) return;
        ShaderHandsRenderer.getInstance().captureAfterHands();
    }

    @Redirect(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void onRenderFirstPersonItemCall(HeldItemRenderer instance, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        Hand renderHand = hand;
        SwingAnimations tweaks = getTweaks();
        if (tweaks != null && tweaks.isEnable() && !tweaks.hmiEnable.isState() && tweaks.swapHands.isState()) {
            renderHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        ((HeldItemRendererInvoker) instance).whylol$callRenderFirstPersonItem(player, tickDelta, pitch, renderHand, swingProgress, stack, equipProgress, matrices, vertexConsumers, light);
    }

    @ModifyArg(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFFLnet/minecraft/util/Arm;)V"
            ),
            index = 5
    )
    private Arm swapEmptyHandArm(Arm arm) {
        SwingAnimations tweaks = getTweaks();
        if (tweaks != null && tweaks.isEnable() && !tweaks.hmiEnable.isState() && tweaks.swapHands.isState()) {
            return arm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
        }
        return arm;
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        ViewModel viewModel = getViewModel();
        if (viewModel == null || !viewModel.isEnable()) {
            return;
        }

        if (hand == Hand.MAIN_HAND) {
            matrices.translate(viewModel.mainHandX.get(), viewModel.mainHandY.get(), viewModel.mainHandZ.get());
        } else {
            matrices.translate(viewModel.offHandX.get(), viewModel.offHandY.get(), viewModel.offHandZ.get());
        }
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V",
                    ordinal = 2
            )
    )
    private void onSwingArm(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm) {
        SwingAnimations tweaks = getTweaks();
        if (tweaks == null || !tweaks.isEnable() || tweaks.hmiEnable.isState() || !tweaks.swingEnabled.isState()) {
            this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
            return;
        }
        Aura aura = ModuleClass.INSTANCE != null ? ModuleClass.INSTANCE.aura : null;
        if (tweaks.auraTargetOnly.isState()) {
            if (aura == null || !aura.isEnable() || aura.getTarget() == null || !aura.getTarget().isAlive()) {
                this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
                return;
            }
        }
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            Arm expectedSwingArm = net.minecraft.client.MinecraftClient.getInstance().player.getMainArm();
            if (tweaks.swapHands.isState()) {
                expectedSwingArm = expectedSwingArm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
            }
            if (arm != expectedSwingArm) {
                this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
                return;
            }
        }

        int i = arm == Arm.RIGHT ? 1 : -1;
        float strength = tweaks.swingStrength.get();
        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        switch (tweaks.swingType.getCurrent()) {
            case "Down" -> {
                matrices.translate(i * 0.56f, -0.32f, -0.72f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5 * strength));
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100 * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155 * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
            }
            case "Poke" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                float tilt = strength / 3f;
                matrices.translate(i * 0.56f, -0.52f, -0.72f);
                matrices.translate(0.0f, 0.0f, tilt * -anim);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(75f * i));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-75f * (strength / 4f) * anim - 60f) * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75f));
            }
            case "Static" -> {
                matrices.translate(i * 0.56f, -0.42f, -0.72f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -60f * strength));
                matrices.translate(0, -0.1, 0);
            }
            case "Feast" -> {
                matrices.translate(i * 0.56f, -0.32f, -0.72f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75 * i * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -65 * strength));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * i));
            }
            case "Akrien" -> {
                matrices.translate(i * 0.65f, -0.32f, -0.72f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5 * strength));
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100 * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155 * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 25 * strength));
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -25 * strength));
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin1 * 15 * strength));
                matrices.translate(sin2 * 0.18f * strength, sin2 * 0.59f * strength, 0);
            }
            case "Smooth" -> applySwingOffset(matrices, i, swingProgress, strength);
            case "Block" -> {
                if (swingProgress > 0) {
                    float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    matrices.translate(0.56f * i, equipProgress * -0.2f - 0.5f, -0.7f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -85f * strength));
                    matrices.translate(-0.1f * i, 0.28f, 0.2f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85f));
                } else {
                    float n = -0.4f * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    float m = 0.2f * MathHelper.sin(MathHelper.sqrt(swingProgress) * ((float) Math.PI * 2));
                    float f1 = -0.2f * MathHelper.sin(swingProgress * (float) Math.PI);
                    matrices.translate(n * i * strength, m * strength, f1 * strength);
                    applyEquipOffset(matrices, i, equipProgress);
                    applySwingOffset(matrices, i, swingProgress, strength);
                }
            }
            case "ToBack" -> {
                float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                matrices.translate(0.65f * i, -0.45f, -0.9f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((-30f * (1f - g * strength) - 30f) * i));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110f * i));
            }
            case "SelfBack" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                matrices.translate(0.65f * i, -0.3f, -0.8f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-70 * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100 - (60 * strength) * anim));
            }
            case "Break", "Брик" -> {
                matrices.translate(0.66F * i, -0.3F, -0.38F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270 * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * 10F * strength));

                matrices.scale(0.5F, 0.5F, 0.5F);
                matrices.translate(-0.1F * i, 0.2F, 0.0F);

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-10.0F * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-105F * i));
            }
            case "DropDown" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                applyEquipOffset(matrices, i, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tweaks.corner.get()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-tweaks.slant.get() * anim * strength));
            }
            case "Pander" -> {
                float panderAnim = MathHelper.sin(swingProgress * (float) Math.PI);
                float panderF = 1f - equipProgress;
                matrices.translate(i * 0.56f, -0.52f, -0.72f);
                matrices.translate((0.3f - panderAnim * 0.15f) * i, 0.2f - panderF * 0.12f, -0.15f - panderAnim * 0.13f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((76f - 10f * panderAnim) * i));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-16f - 8f * panderAnim) * i));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-83f - 26f * panderAnim));
            }
            case "Slant" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2.0) * 2.0);
                float rotate = 35.0f * strength;
                matrices.translate(i * 0.56f, -0.52f, -0.72f);
                matrices.translate(0.0f, 0.0f, -0.3f * anim * strength);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(anim * -rotate));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(anim * rotate));
            }
            default -> this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
        }
    }

    @Overwrite
    public void renderItem(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player, int light) {
        float f = player.getHandSwingProgress(tickDelta);
        Hand hand = (Hand) MoreObjects.firstNonNull(player.preferredHand, Hand.MAIN_HAND);
        float g = player.getLerpedPitch(tickDelta);
        HeldItemRenderer.HandRenderType handRenderType = ((HeldItemRenderer)(Object)this).getHandRenderType(player);
        float h = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float i = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
        float j;
        float k;
        if (handRenderType.renderMainHand) {
            j = hand == Hand.MAIN_HAND ? f : 0.0F;
            k = 1.0F - MathHelper.lerp(tickDelta, this.prevEquipProgressMainHand, this.equipProgressMainHand);
            this.renderFirstPersonItem(player, tickDelta, g, Hand.MAIN_HAND, j, this.mainHand, k, matrices, vertexConsumers, light);
        }

        if (handRenderType.renderOffHand) {
            j = hand == Hand.OFF_HAND ? f : 0.0F;
            k = 1.0F - MathHelper.lerp(tickDelta, this.prevEquipProgressOffHand, this.equipProgressOffHand);
            this.renderFirstPersonItem(player, tickDelta, g, Hand.OFF_HAND, j, this.offHand, k, matrices, vertexConsumers, light);
        }

        vertexConsumers.draw();
    }

    @Inject(method = "applyEatOrDrinkTransformation", at = @At("HEAD"), cancellable = true)
    private void onApplyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player, CallbackInfo ci) {
        SwingAnimations tweaks = getTweaks();
        if (tweaks == null || !tweaks.isEnable() || tweaks.hmiEnable.isState() || !tweaks.eatAnim.isState() || !player.isUsingItem()) {
            return;
        }

        applyEatOrDrinkTransformationCustom(matrices, tickDelta, arm, stack);
        ci.cancel();
    }

    private void applyEatOrDrinkTransformationCustom(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack) {
        if (net.minecraft.client.MinecraftClient.getInstance().player == null) {
            return;
        }

        float f = (float) net.minecraft.client.MinecraftClient.getInstance().player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float g = f / (float) stack.getMaxUseTime(net.minecraft.client.MinecraftClient.getInstance().player);
        float h;
        if (g < 0.8F) {
            h = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.005F);
            matrices.translate(0f, h, 0f);
        }

        h = 1.0F - (float) Math.pow(g, 27.0);
        int i = arm == Arm.RIGHT ? 1 : -1;

        float offsetX = 0f;
        float offsetY = 0f;
        float offsetZ = 0f;

        ViewModel viewModel = getViewModel();
        if (viewModel != null && viewModel.isEnable()) {
            if (arm == Arm.RIGHT) {
                offsetX = viewModel.mainHandX.get();
                offsetY = viewModel.mainHandY.get();
                offsetZ = viewModel.mainHandZ.get();
            } else {
                offsetX = viewModel.offHandX.get();
                offsetY = viewModel.offHandY.get();
                offsetZ = viewModel.offHandZ.get();
            }
        }

        matrices.translate(h * 0.6F * i + offsetX, h * -0.5F + offsetY, offsetZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * h * 90f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * h * 30f));
    }

    private void applyEquipOffset(MatrixStack matrices, int i, float equipProgress) {
        matrices.translate(i * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);
    }

    private void applySwingOffset(MatrixStack matrices, int i, float swingProgress, float strength) {
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.translate(0.56f * i, -0.52f, -0.72f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + f * -20f * strength)));
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -20f * strength));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80f * strength));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
    }

    private void callSwingArm(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm) {
        ((HeldItemRendererInvoker) instance).whylol$callSwingArm(swingProgress, equipProgress, matrices, armX, arm);
    }

    private SwingAnimations getTweaks() {
        if (ModuleClass.INSTANCE == null) {
            return null;
        }
        return ModuleClass.INSTANCE.swingAnimations;
    }

    private ViewModel getViewModel() {
        if (ModuleClass.INSTANCE == null) {
            return null;
        }
        return ModuleClass.INSTANCE.viewModel;
    }

    private ShaderHands getShaderHands() {
        if (ModuleClass.INSTANCE == null) {
            return null;
        }
        return ModuleClass.INSTANCE.shaderHands;
    }

}
