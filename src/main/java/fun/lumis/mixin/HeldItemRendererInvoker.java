package fun.lumis.mixin;

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
public interface HeldItemRendererInvoker {
    @Accessor("mainHand")
    ItemStack whylol$getMainHand();

    @Accessor("offHand")
    ItemStack whylol$getOffHand();

    @Invoker("renderFirstPersonItem")
    void whylol$callRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                          float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light);

    @Invoker("applyEquipOffset")
    void whylol$applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);

    @Invoker("swingArm")
    void whylol$callSwingArm(float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm);

    @Invoker("renderArmHoldingItem")
    void whylol$renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                     float equipProgress, float swingProgress, Arm arm);

    @Invoker("renderMapInBothHands")
    void whylol$renderMapInBothHands(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                     float pitch, float equipProgress, float swingProgress);

    @Invoker("renderMapInOneHand")
    void whylol$renderMapInOneHand(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                   float equipProgress, Arm arm, float swingProgress, ItemStack item);

    @Invoker("renderItem")
    void whylol$renderItem(LivingEntity entity, ItemStack item, ModelTransformationMode modelTransformationMode,
                           boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);
}
