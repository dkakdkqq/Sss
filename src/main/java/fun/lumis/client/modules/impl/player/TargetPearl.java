package fun.lumis.client.modules.impl.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventMoveInput;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.Comparator;

public class TargetPearl extends Module {

    private static final double MAX_TRACK_DISTANCE = 256.0;
    private static final double MIN_LANDING_DISTANCE = 11.0;
    private static final long LOCAL_THROW_COOLDOWN_MS = 2500L;
    private static final float DIRECT_MIN_PITCH = -25.0f;
    private static final float DIRECT_MAX_PITCH = 35.0f;
    private static final float PITCH_STEP = 0.25f;

    public static final TargetPearl INSTANCE = new TargetPearl();

    private final ModeSetting mode = new ModeSetting("Тип", "Автоматический", "По бинду", "Автоматический");
    private final BindSetting bind = new BindSetting("Бинд", -1)
            .visible(() -> mode.is("По бинду"));
    private final BooleanSetting onlyTarget = new BooleanSetting("Только за противником", false);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Игнорировать друзей", true);

    private final TimerUtils timer = new TimerUtils();

    private EnderPearlEntity targetPearl;
    private int lastHandledPearlId = -1;
    private long nextThrowAt;
    private boolean isThrowing;
    private Vec2f serverRotation;

    public TargetPearl() {
        super("TargetPearl", "Автоматически бросает жемчуг в цель", ModuleCategory.PLAYER);
        addSettings(mode, bind, onlyTarget, ignoreFriends);
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        if (!mode.is("По бинду") || event.getKey() != bind.getKey()) {
            return;
        }

        if (canThrowNow()) {
            aimAndThrowPearl();
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            resetThrowState();
            return;
        }

        if (lastHandledPearlId != -1) {
            Entity handled = mc.world.getEntityById(lastHandledPearlId);
            if (!(handled instanceof EnderPearlEntity pearl) || !pearl.isAlive()) {
                lastHandledPearlId = -1;
            }
        }

        if (mode.is("Автоматический") && canThrowNow()) {
            aimAndThrowPearl();
        }
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        if (!isEnable() || !isThrowing || serverRotation == null) {
            return;
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0f && strafe == 0.0f) {
            return;
        }

        double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(serverRotation.x, forward, strafe)));
        float bestForward = 0.0f;
        float bestStrafe = 0.0f;
        float smallestDifference = Float.MAX_VALUE;

        for (float testForward = -1.0f; testForward <= 1.0f; testForward++) {
            for (float testStrafe = -1.0f; testStrafe <= 1.0f; testStrafe++) {
                if (testForward == 0.0f && testStrafe == 0.0f) {
                    continue;
                }

                double testAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(serverRotation.x, testForward, testStrafe)));
                float difference = Math.abs(MathHelper.wrapDegrees((float) (targetAngle - testAngle)));
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestForward = testForward;
                    bestStrafe = testStrafe;
                }
            }
        }

        event.setForward(bestForward);
        event.setStrafe(bestStrafe);
    }

    @Override
    public void onDisable() {
        resetThrowState();
        lastHandledPearlId = -1;
        nextThrowAt = 0L;
        timer.reset();
        super.onDisable();
    }

    private boolean canThrowNow() {
        if (System.currentTimeMillis() < nextThrowAt) {
            return false;
        }
        return !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))
                && timer.finished(1000L);
    }

    private void aimAndThrowPearl() {
        Vec3d landingPosition = getTargetPearlLandingPosition();
        if (landingPosition == null) {
            resetThrowState();
            return;
        }

        float[] rotations = calculateYawPitch(landingPosition);
        if (rotations == null || Float.isNaN(rotations[0]) || Float.isNaN(rotations[1])) {
            resetThrowState();
            return;
        }

        Vec3d trajectoryLanding = checkTrajectory(rotations[0], rotations[1]);
        double allowedError = Math.max(3.0, mc.player.getPos().distanceTo(landingPosition) * 0.12);
        if (trajectoryLanding == null || landingPosition.distanceTo(trajectoryLanding) > allowedError) {
            resetThrowState();
            return;
        }

        if (!hasPearl()) {
            resetThrowState();
            return;
        }

        float previousYaw = mc.player.getYaw();
        float previousPitch = mc.player.getPitch();
        isThrowing = true;
        serverRotation = new Vec2f(rotations[0], rotations[1]);

        try {
            mc.player.setYaw(rotations[0]);
            mc.player.setPitch(rotations[1]);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    rotations[0], rotations[1], mc.player.isOnGround(), mc.player.horizontalCollision
            ));
            InventoryUtils.swapAndUseHvH(Items.ENDER_PEARL);
            timer.reset();
            nextThrowAt = System.currentTimeMillis() + LOCAL_THROW_COOLDOWN_MS;
            if (targetPearl != null) {
                lastHandledPearlId = targetPearl.getId();
            }
        } finally {
            mc.player.setYaw(previousYaw);
            mc.player.setPitch(previousPitch);
            resetThrowState();
        }
    }

    private Vec3d getTargetPearlLandingPosition() {
        targetPearl = getTargetPearl();
        if (targetPearl == null || !targetPearl.isAlive()) {
            return null;
        }

        Vec3d landingPos = predictPearlLanding(targetPearl);
        if (landingPos == null || !isWithinRange(landingPos)) {
            return null;
        }

        return landingPos;
    }

    private EnderPearlEntity getTargetPearl() {
        Box searchBox = mc.player.getBoundingBox().expand(MAX_TRACK_DISTANCE);
        LivingEntity auraTarget = ModuleClass.INSTANCE != null ? ModuleClass.INSTANCE.aura.getTarget() : null;

        return mc.world.getOtherEntities(mc.player, searchBox, entity -> entity instanceof EnderPearlEntity pearl
                        && pearl.isAlive()
                        && pearl.getOwner() != mc.player
                        && pearl.getId() != lastHandledPearlId
                        && !isIgnoredFriend(pearl.getOwner())
                        && (!onlyTarget.isState() || auraTarget != null && pearl.getOwner() == auraTarget))
                .stream()
                .map(entity -> (EnderPearlEntity) entity)
                .filter(pearl -> getHorizontalDistanceTo(pearl) <= MAX_TRACK_DISTANCE)
                .min(Comparator.comparingDouble(this::getHorizontalDistanceTo))
                .orElse(null);
    }

    private boolean isIgnoredFriend(Entity owner) {
        if (!ignoreFriends.isState() || !(owner instanceof PlayerEntity player)) {
            return false;
        }
        return Lumis.INSTANCE != null
                && Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString());
    }

    private double getHorizontalDistanceTo(EnderPearlEntity pearl) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d pearlPos = pearl.getPos();
        double dx = pearlPos.x - playerPos.x;
        double dz = pearlPos.z - playerPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Vec3d predictPearlLanding(EnderPearlEntity pearl) {
        Vec3d position = pearl.getPos();
        Vec3d velocity = pearl.getVelocity();
        Vec3d lastPosition = position;

        for (int i = 0; i < 200; i++) {
            lastPosition = position;
            position = position.add(velocity);

            if (hitsBlock(lastPosition, position) || position.y <= mc.world.getBottomY()) {
                return new Vec3d(MathHelper.floor(lastPosition.x) + 0.5, MathHelper.floor(lastPosition.y), MathHelper.floor(lastPosition.z) + 0.5);
            }

            velocity = updatePearlMotion(velocity, position);
        }

        return new Vec3d(MathHelper.floor(lastPosition.x) + 0.5, MathHelper.floor(lastPosition.y), MathHelper.floor(lastPosition.z) + 0.5);
    }

    private Vec3d updatePearlMotion(Vec3d motion, Vec3d position) {
        BlockPos blockPos = BlockPos.ofFloored(position);
        if (mc.world.getBlockState(blockPos).isOf(net.minecraft.block.Blocks.WATER)) {
            return motion.multiply(0.8).add(0.0, -0.03, 0.0);
        }
        return motion.multiply(0.99).add(0.0, -0.03, 0.0);
    }

    private boolean isWithinRange(Vec3d landingPos) {
        double distanceToLanding = mc.player.getPos().distanceTo(landingPos);
        return distanceToLanding >= MIN_LANDING_DISTANCE && distanceToLanding <= MAX_TRACK_DISTANCE;
    }

    private float[] calculateYawPitch(Vec3d targetPosition) {
        Vec3d playerPosition = mc.player.getPos();
        double dx = targetPosition.x - playerPosition.x;
        double dy = targetPosition.y - mc.player.getEyeY();
        double dz = targetPosition.z - playerPosition.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double allowedError = Math.max(1.5, mc.player.getPos().distanceTo(targetPosition) * 0.08);
        TrajectoryCandidate directCandidate = findBestCandidate(targetPosition, yaw, DIRECT_MIN_PITCH, DIRECT_MAX_PITCH, allowedError, true);
        if (directCandidate != null) {
            return new float[]{yaw, MathHelper.clamp(directCandidate.pitch, -90.0f, 90.0f)};
        }

        TrajectoryCandidate fallbackCandidate = findBestCandidate(targetPosition, yaw, -85.0f, 85.0f, allowedError, false);
        if (fallbackCandidate == null) {
            double fallbackPitch = -Math.toDegrees(Math.atan2(dy, horizontalDistance)) + 5.0;
            return new float[]{yaw, MathHelper.clamp((float) fallbackPitch, -90.0f, 90.0f)};
        }

        return new float[]{yaw, MathHelper.clamp(fallbackCandidate.pitch, -90.0f, 90.0f)};
    }

    private TrajectoryCandidate findBestCandidate(Vec3d targetPosition, float yaw, float minPitch, float maxPitch, double allowedError, boolean preferDirect) {
        Vec3d playerPosition = mc.player.getPos();
        double velocity = 1.5;
        TrajectoryCandidate bestCandidate = null;

        for (float pitch = minPitch; pitch <= maxPitch; pitch += PITCH_STEP) {
            float pitchRad = (float) Math.toRadians(pitch);
            double vx = (-MathHelper.sin((float) Math.toRadians(yaw)) * MathHelper.cos(pitchRad)) * velocity;
            double vy = (-MathHelper.sin(pitchRad)) * velocity;
            double vz = (MathHelper.cos((float) Math.toRadians(yaw)) * MathHelper.cos(pitchRad)) * velocity;
            Vec3d pos = new Vec3d(playerPosition.x, mc.player.getEyeY(), playerPosition.z);
            Vec3d motion = new Vec3d(vx, vy, vz);

            int ticks = 0;
            for (int i = 0; i < 200; i++) {
                Vec3d previous = pos;
                pos = pos.add(motion);
                motion = updatePearlMotion(motion, pos);
                ticks++;

                if (hitsEntity(previous, pos)) {
                    break;
                }

                if (!hitsBlock(previous, pos) && pos.y > mc.world.getBottomY()) {
                    continue;
                }

                double distanceToTarget = pos.distanceTo(targetPosition);
                TrajectoryCandidate candidate = new TrajectoryCandidate(pitch, distanceToTarget, ticks, pos);
                if (isBetterCandidate(candidate, bestCandidate, allowedError, preferDirect)) {
                    bestCandidate = candidate;
                }
                break;
            }
        }

        if (bestCandidate == null || bestCandidate.distanceToTarget > allowedError) {
            return null;
        }
        return bestCandidate;
    }

    private boolean isBetterCandidate(TrajectoryCandidate candidate, TrajectoryCandidate currentBest, double allowedError, boolean preferDirect) {
        if (currentBest == null) {
            return true;
        }

        boolean candidateAccurate = candidate.distanceToTarget <= allowedError;
        boolean bestAccurate = currentBest.distanceToTarget <= allowedError;
        if (candidateAccurate != bestAccurate) {
            return candidateAccurate;
        }

        if (preferDirect && candidateAccurate && bestAccurate) {
            float candidatePitchAbs = Math.abs(candidate.pitch);
            float bestPitchAbs = Math.abs(currentBest.pitch);
            if (Math.abs(candidatePitchAbs - bestPitchAbs) > 0.01f) {
                return candidatePitchAbs < bestPitchAbs;
            }
            if (candidate.ticks != currentBest.ticks) {
                return candidate.ticks < currentBest.ticks;
            }
        }

        if (Math.abs(candidate.distanceToTarget - currentBest.distanceToTarget) > 0.01) {
            return candidate.distanceToTarget < currentBest.distanceToTarget;
        }

        if (candidate.ticks != currentBest.ticks) {
            return candidate.ticks < currentBest.ticks;
        }

        if (!preferDirect) {
            return Math.abs(candidate.pitch) < Math.abs(currentBest.pitch);
        }

        return false;
    }

    private Vec3d checkTrajectory(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double velocity = 1.5;

        double x = mc.player.getX() - MathHelper.cos(yawRad) * 0.16f;
        double y = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) - 0.1;
        double z = mc.player.getZ() - MathHelper.sin(yawRad) * 0.16f;

        double motionX = (-MathHelper.sin(yawRad) * MathHelper.cos(pitchRad)) * velocity;
        double motionY = (-MathHelper.sin(pitchRad)) * velocity;
        double motionZ = (MathHelper.cos(yawRad) * MathHelper.cos(pitchRad)) * velocity;

        Vec3d position = new Vec3d(x, y, z);
        Vec3d motion = new Vec3d(motionX, motionY, motionZ);

        for (int i = 0; i <= 200; i++) {
            Vec3d previous = position;
            position = position.add(motion);
            motion = updatePearlMotion(motion, position);

            if (hitsEntity(previous, position)) {
                return null;
            }

            if (hitsBlock(previous, position) || position.y <= mc.world.getBottomY()) {
                return new Vec3d(MathHelper.floor(position.x) + 0.5, MathHelper.floor(position.y), MathHelper.floor(position.z) + 0.5);
            }
        }

        return null;
    }

    private boolean hitsBlock(Vec3d from, Vec3d to) {
        return mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        )).getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
    }

    private boolean hitsEntity(Vec3d from, Vec3d to) {
        Box searchBox = new Box(from, to).expand(0.3);
        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBox, entity -> {
            if (!entity.isAlive() || entity.isSpectator() || entity.noClip) {
                return false;
            }
            if (entity == targetPearl) {
                return false;
            }
            return !(entity instanceof EnderPearlEntity);
        })) {
            if (entity.getBoundingBox().expand(0.25).raycast(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPearl() {
        return mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)
                || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)
                || InventoryUtils.find(Items.ENDER_PEARL, 0, 8) != -1
                || InventoryUtils.find(Items.ENDER_PEARL, 9, 45) != -1;
    }

    private void resetThrowState() {
        isThrowing = false;
        targetPearl = null;
        serverRotation = null;
    }

    private static double direction(float rotationYaw, float moveForward, float moveStrafing) {
        if (moveForward < 0.0f) rotationYaw += 180.0f;
        float forward = 1.0f;
        if (moveForward < 0.0f) forward = -0.5f;
        else if (moveForward > 0.0f) forward = 0.5f;
        if (moveStrafing > 0.0f) rotationYaw -= 90.0f * forward;
        if (moveStrafing < 0.0f) rotationYaw += 90.0f * forward;
        return Math.toRadians(rotationYaw);
    }

    private static final class TrajectoryCandidate {
        private final float pitch;
        private final double distanceToTarget;
        private final int ticks;
        private final Vec3d landingPos;

        private TrajectoryCandidate(float pitch, double distanceToTarget, int ticks, Vec3d landingPos) {
            this.pitch = pitch;
            this.distanceToTarget = distanceToTarget;
            this.ticks = ticks;
            this.landingPos = landingPos;
        }
    }
}
