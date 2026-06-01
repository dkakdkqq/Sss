package fun.lumis.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.implement.EventMoveInput;
import fun.lumis.client.modules.impl.movement.Sprint;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (!EventInvoker.hasListeners(EventMoveInput.class)) {
            return;
        }

        EventMoveInput eventInput = new EventMoveInput(
                this.movementForward,
                this.movementSideways,
                this.playerInput.jump(),
                this.playerInput.sneak()
        );
        eventInput.call();

        final float forward = eventInput.getForward();
        final float strafe = eventInput.getStrafe();

        this.playerInput = new PlayerInput(
                forward > 0.0F,
                forward < 0.0F,
                strafe > 0.0F,
                strafe < 0.0F,
                eventInput.isJump(),
                eventInput.isSneak(),
                this.playerInput.sprint()
        );
        this.movementForward = forward;
        this.movementSideways = strafe;
    }
}
