package fun.lumis.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererAccessor {

    @Accessor("mainHand")
    ItemStack getMainHand();

    @Accessor("offHand")
    ItemStack getOffHand();

    @Accessor("equipProgressMainHand")
    float getEquipProgressMainHand();

    @Accessor("prevEquipProgressMainHand")
    float getPrevEquipProgressMainHand();

    @Accessor("equipProgressOffHand")
    float getEquipProgressOffHand();

    @Accessor("prevEquipProgressOffHand")
    float getPrevEquipProgressOffHand();

    @Accessor("client")
    MinecraftClient getClient();

    @Invoker("renderFirstPersonItem")
    void invokeRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);

    @Invoker("applyEquipOffset")
    void invokeApplyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);

    @Invoker("swingArm")
    void invokeSwingArm(float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm);

    @Invoker("renderArmHoldingItem")
    void invokeRenderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm);

    @Invoker("renderMapInBothHands")
    void invokeRenderMapInBothHands(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float pitch, float equipProgress, float swingProgress);

    @Invoker("renderMapInOneHand")
    void invokeRenderMapInOneHand(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, Arm arm, float swingProgress, ItemStack item);

    @Invoker("applySwingOffset")
    void invokeApplySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    @Invoker("renderItem")
    void invokeRenderItem(LivingEntity entity, ItemStack item, ModelTransformationMode modelTransformationMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);
}