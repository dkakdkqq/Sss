package fun.lumis.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.implement.*;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.api.utils.player.ViaProtocolUtils;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.movement.Sprint;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends PlayerEntity implements QClient {

    public ClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    public abstract void closeScreen();

    @Inject(method = "tick", at = @At(value = "HEAD", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V"))
    private void onTick(CallbackInfo ci) {
        if (EventInvoker.hasListeners(EventUpdate.class)) {
            new EventUpdate().call();
        }
    }

    @Inject(method = "tick", at = @At(value = "TAIL", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V"))
    private void onTickPost(CallbackInfo ci) {
        if (EventInvoker.hasListeners(EventUpdatePost.class)) {
            new EventUpdatePost().call();
        }

        if (shouldSyncRotation()) {
            this.headYaw = this.getYaw();
            this.prevHeadYaw = this.getYaw();
            this.bodyYaw = this.getYaw();
            this.prevBodyYaw = this.getYaw();
        }
    }

    @Unique
    private boolean shouldSyncRotation() {
        return ModuleClass.aura.isEnable() &&
                Aura.clientLook.isState() &&
                RotationStorage.instance.isRotating();
    }

    @Redirect(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z", ordinal = 1),
            require = 0
    )
    private boolean onSprintKeyPressed(KeyBinding instance) {
        if (ViaProtocolUtils.isTargetProtocolBelowOneNineteen() && (this.horizontalCollision || this.collidedSoftly)) {
            return false;
        }

        EventSprint event = new EventSprint();
        event.call();
        if (event.isCancelled()) {
            return false;
        }
        return instance.isPressed();
    }

    @Redirect(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"
            ),
            require = 0
    )
    private boolean onSlowDownRedirect(ClientPlayerEntity player) {
        if (player.isUsingItem()) {
            EventSlowWalking event = new EventSlowWalking();
            event.call();
            return player.isUsingItem() && player.getVehicle() == null && !event.isCancelled();
        }
        return player.isUsingItem() && player.getVehicle() == null;
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    public void pushOutOfBlocks(double x, double z, CallbackInfo ci) {
        if (ModuleClass.noPush.isEnable() && ModuleClass.noPush.getCollisionList().is("Блоки")) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void onMoveHook(MovementType movementType, Vec3d movement, @NotNull CallbackInfo ci) {
        EventMove event = new EventMove(movement);
        event.call();

        if (!event.isCancelled() && event.getMovePos().equals(movement)) {
            return;
        }

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        double d = this.getX();
        double e = this.getZ();
        super.move(movementType, event.getMovePos());
        float f = (float) Math.sqrt(Math.pow(this.getX() - d, 2) + Math.pow(this.getZ() - e, 2));
        this.updateLimbs(f);
        ci.cancel();
    }

    @Inject(method = "closeHandledScreen", at = @At("HEAD"), cancellable = true)
    private void onCloseHandledScreen(CallbackInfo ci) {
        int syncId = this.currentScreenHandler.syncId;
        EventCloseInv event = new EventCloseInv(syncId);
        event.call();
        if (!event.isCancelled()) {
            this.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
        }
        this.closeScreen();
        ci.cancel();
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleClass.lockSlot != null && ModuleClass.lockSlot.isCurrentSlotLockedForDrop()) {
            cir.setReturnValue(false);
        }
    }
}
