package fun.lumis.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.api.events.implement.EventFireWork;
import fun.lumis.api.utils.player.BoostUtils;
import fun.lumis.client.modules.impl.movement.ElytraBoost;

@Mixin(FireworkRocketEntity.class)
public abstract class FireWorkRocketEntityMixin extends ProjectileEntity {

    @Unique
    private Vec3d rotation;

    @Shadow
    private LivingEntity shooter;

    public FireWorkRocketEntityMixin(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        new EventFireWork((FireworkRocketEntity) (Object) this).call();
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"
            )
    )
    public Vec3d captureRotation(Vec3d original) {
        this.rotation = original;
        return this.rotation;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;add(DDD)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    public Vec3d modifyBoost(Vec3d velocity, double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ElytraBoost elytraBoost = ElytraBoost.INSTANCE;

        if (mc.player == null || !mc.player.isGliding()) {
            return defaultBoost(velocity);
        }

        if (elytraBoost == null || !elytraBoost.isEnable()) {
            return defaultBoost(velocity);
        }

        return handleElytraBoost(mc, elytraBoost, velocity);
    }

    @Unique
    private Vec3d handleElytraBoost(MinecraftClient mc, ElytraBoost elytraBoost, Vec3d velocity) {
        String modeName = elytraBoost.getMode().getCurrent();
        Vec3d boost;

        switch (modeName) {
            case "LonyGrief":
                boost = BoostUtils.getBoost(mc.player);
                break;
            case "SlimeWorld":
                boost = BoostUtils.getBoostslime(mc.player);
                break;
            case "BravoHVH":
                boost = BoostUtils.getBoostbravo(mc.player);
                break;
            case "ReallyWorld":
                boost = BoostUtils.getBoostrw(mc.player);
                break;
            case "Custom":
            default:
                Vec2f customBoost = elytraBoost.getBoostV2();
                boost = new Vec3d(customBoost.x, customBoost.y, customBoost.x);
                break;
        }

        return velocity.add(
                this.rotation.x * 0.1D + (this.rotation.x * boost.x - velocity.x) * 0.5D,
                this.rotation.y * 0.1D + (this.rotation.y * boost.y - velocity.y) * 0.5D,
                this.rotation.z * 0.1D + (this.rotation.z * boost.z - velocity.z) * 0.5D
        );
    }

    @Unique
    private Vec3d defaultBoost(Vec3d velocity) {
        return velocity.add(
                this.rotation.x * 0.1D + (this.rotation.x * 1.5D - velocity.x) * 0.5D,
                this.rotation.y * 0.1D + (this.rotation.y * 1.5D - velocity.y) * 0.5D,
                this.rotation.z * 0.1D + (this.rotation.z * 1.5D - velocity.z) * 0.5D
        );
    }
}
