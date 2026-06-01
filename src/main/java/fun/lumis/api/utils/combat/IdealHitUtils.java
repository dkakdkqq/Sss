package fun.lumis.api.utils.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.Lumis;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.mixin.IEntity;

@UtilityClass
public class IdealHitUtils implements QClient {
    private static final int WATER_CRIT_INTENT_TICKS = 8;
    private static final int WATER_CRIT_CONTACT_TICKS = 10;
    private static final double WATER_CRIT_MIN_UPWARD_VELOCITY = 0.05;
    private static int lastWaterContactAge = Integer.MIN_VALUE;
    private static int lastWaterCritIntentAge = Integer.MIN_VALUE;

    public float getAICooldown() {
        if (mc.player.getMainHandStack().getItem() == Items.AIR) return 0.9f;

        if (mc.player.getMainHandStack().getItem() instanceof AxeItem || mc.player.getMainHandStack().getItem() instanceof ShovelItem)
            return 0.95f;
        return 0.93f;
    }

    public boolean canAIFall() {
        BlockPos posWater = BlockPos.ofFloored(mc.player.getPos().add(0, -0.4f, 0));
        if (mc.world.getBlockState(posWater).isOf(Blocks.WATER)) return true;
        return ((getBlock(0, 3, 0) == Blocks.AIR && getBlock(0, 2, 0) == Blocks.AIR && getBlock(0, 1, 0) == Blocks.AIR)
                || mc.player.fallDistance < (getBlock(0, 2, 0) != Blocks.AIR ? 0.08f : 0.6f)
                || mc.player.fallDistance > 1.2f);
    }

    public boolean canCritical(LivingEntity target) {
        updateWaterCritState();

        boolean packetCrits = ModuleClass.INSTANCE.packetCriticals.isEnable();
        boolean hasSlowFalling = mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        boolean inCobweb = isInCobweb();
        boolean smartCrit = ModuleClass.INSTANCE.aura.smartCrit.isState();

        if (packetCrits && inCobweb) {
            return true;
        }

        if (packetCrits && hasSlowFalling) {
            return mc.player.getVelocity().y < 0 && mc.player.fallDistance > 0;
        }

        if (isTryingWaterCrit()) {
            return isWaterCritWindow();
        }

        boolean isCritPossible = !mc.player.isOnGround()
                && mc.player.getVelocity().y < 0
                && mc.player.fallDistance > 0;

        if (isNoJumpDelayCeilingCritIntent()) {
            return isNoJumpDelayCeilingCritWindow();
        }

        if (isNoJumpDelayJumpCritIntent()) {
            return isNoJumpDelayJumpCritWindow();
        }

        if (cannotPerformCrit()) {
            return true;
        }

        if (smartCrit) {
            return mc.player.isOnGround() || isCritPossible;
        }

        return isCritPossible;
    }

    private boolean isNoJumpDelayCeilingCritIntent() {
        return ModuleClass.INSTANCE.noJumpDelay.isEnable()
                && mc.options != null
                && mc.options.jumpKey.isPressed()
                && hasLowCeilingForJumpCrit();
    }

    private boolean isNoJumpDelayJumpCritIntent() {
        return ModuleClass.INSTANCE.noJumpDelay.isEnable()
                && mc.options != null
                && mc.options.jumpKey.isPressed();
    }

    private boolean isNoJumpDelayCeilingCritWindow() {
        return mc.player != null
                && !mc.player.isOnGround()
                && mc.player.getVelocity().y <= 0.01
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying;
    }

    public boolean isNoJumpDelayJumpCritWindow() {
        return mc.player != null
                && mc.world != null
                && ModuleClass.INSTANCE.noJumpDelay.isEnable()
                && mc.options != null
                && mc.options.jumpKey.isPressed()
                && !mc.player.isOnGround()
                && mc.player.getVelocity().y < 0.0
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && !mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.isGliding()
                && !isInCobweb();
    }

    private boolean hasLowCeilingForJumpCrit() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        Box box = mc.player.getBoundingBox().contract(0.03);
        Box headBox = new Box(
                box.minX,
                box.maxY,
                box.minZ,
                box.maxX,
                box.maxY + 0.32,
                box.maxZ
        );

        for (BlockPos pos : BlockPos.iterate(
                MathHelper.floor(headBox.minX), MathHelper.floor(headBox.minY), MathHelper.floor(headBox.minZ),
                MathHelper.floor(headBox.maxX), MathHelper.floor(headBox.maxY), MathHelper.floor(headBox.maxZ))) {
            var state = mc.world.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public boolean canPacketCrit() {
        return isInCobweb() || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
    }

    private void updateWaterCritState() {
        if (mc.player == null || mc.world == null) {
            lastWaterContactAge = Integer.MIN_VALUE;
            lastWaterCritIntentAge = Integer.MIN_VALUE;
            return;
        }

        boolean nearWaterSurface = isNearWaterSurface();
        if (!nearWaterSurface) {
            return;
        }

        lastWaterContactAge = mc.player.age;

        if (isWaterCritIntentState()) {
            lastWaterCritIntentAge = mc.player.age;
        }
    }

    private boolean isWaterCritIntentState() {
        if (mc.player == null || mc.options == null) {
            return false;
        }

        return mc.options.jumpKey.isPressed()
                && !mc.player.isOnGround()
                && !mc.player.isSubmergedInWater()
                && mc.player.getVelocity().y > WATER_CRIT_MIN_UPWARD_VELOCITY;
    }

    private boolean isTryingWaterCrit() {
        if (mc.player == null || mc.options == null || !mc.options.jumpKey.isPressed()) {
            return false;
        }

        return mc.player.age - lastWaterCritIntentAge <= WATER_CRIT_INTENT_TICKS
                && mc.player.age - lastWaterContactAge <= WATER_CRIT_CONTACT_TICKS;
    }

    private boolean isWaterCritWindow() {
        return mc.player != null
                && !mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && mc.player.fallDistance > 0.0f
                && mc.player.getVelocity().y < 0.0;
    }

    private boolean isNearWaterSurface() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        BlockPos below = BlockPos.ofFloored(mc.player.getPos().add(0, -0.4f, 0));
        return mc.player.isTouchingWater()
                || mc.player.isSubmergedInWater()
                || mc.world.getBlockState(below).isOf(Blocks.WATER);
    }

    private boolean cannotPerformCrit() {
        double effectiveJumpHeight = mc.player.getStepHeight();
        Vec3d jumpVec = new Vec3d(0, effectiveJumpHeight, 0);
        Vec3d allowedMovement = ((IEntity) mc.player).invokeAdjustMovementForCollisions(jumpVec);

        boolean cobweb = isInCobweb();

        BlockPos posWater = BlockPos.ofFloored(mc.player.getPos().add(0, (mc.player.getHeight() / 2f), 0));

        return mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.world.getBlockState(posWater).isOf(Blocks.WATER)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || cobweb
                || mc.player.isGliding()
                || mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || mc.player.isTouchingWater()
                || (allowedMovement.y < mc.player.getStepHeight() - 0.5 && mc.player.isOnGround());
    }

    public boolean isInCobweb() {
        Box box = mc.player.getBoundingBox();
        for (BlockPos pos : BlockPos.iterate(
                MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ))) {
            if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    public Block getBlock(double x, double y, double z) {
        return mc.world.getBlockState(mc.player.getBlockPos().add((int) x, (int) y, (int) z)).getBlock();
    }

    public boolean findFall(float fallDistance) {
        Vec3d rotationVec = mc.player.getRotationVector();
        double tempVelocityX = mc.player.getVelocity().x;
        double tempVelocityY = mc.player.getVelocity().y;
        double tempVelocityZ = mc.player.getVelocity().z;

        float n = MathHelper.cos(mc.player.getPitch() * 0.017453292f);
        n = (float) (n * n * Math.min(rotationVec.length() / 0.4, 1.0));

        Vec3d vec3d = new Vec3d(tempVelocityX, tempVelocityY, tempVelocityZ).add(0.0, 0.08 * (-1.0 + n * 0.75), 0.0);
        tempVelocityY = vec3d.y * 0.9800000190734863;

        return tempVelocityY < fallDistance;
    }
}
