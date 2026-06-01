package fun.lumis.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface ILivingEntity {
    @Accessor("lastAttackedTicks") int getLastAttackedTicks();
    @Accessor("jumpingCooldown") void setJumpingCooldown(int jumpingCooldown);

    @Accessor("serverYaw") double getResolveYaw();
    @Accessor("serverPitch") double getResolvePitch();
}