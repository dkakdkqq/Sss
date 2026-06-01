package fun.lumis.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.implement.EventAttackEntity;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    public void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (player != null && target != null) {
                EventAttackEntity event = new EventAttackEntity(player, target);
                EventInvoker.invoke(event);
                if (event.isCancelled()) ci.cancel();
            }
        } catch (Exception e) {
        }
    }
}
