package fun.lumis.client.modules.impl.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.AllayEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.AllayEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventAttackEntity;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class Satellite extends Module {

    private static final Identifier ALLAY_TEXTURE = Identifier.ofVanilla("textures/entity/allay/allay.png");
    private static final long ATTACK_FOLLOW_TIMEOUT_MS = 3600L;
    private static final long ATTACK_LAUNCH_DURATION_MS = 560L;
    private static final long ATTACK_RETURN_DURATION_MS = 920L;
    public static Satellite INSTANCE = new Satellite();

    public final ModeSetting shoulder = new ModeSetting("Плечо", "Правое", "Правое", "Левое");
    public final FloatSetting scale = new FloatSetting("Размер", 0.38f, 0.15f, 1.25f, 0.01f);
    public final FloatSetting offsetX = new FloatSetting("Смещение X", 0.0f, -1.0f, 1.0f, 0.01f);
    public final FloatSetting offsetY = new FloatSetting("Смещение Y", 0.18f, -1.0f, 1.0f, 0.01f);
    public final FloatSetting offsetZ = new FloatSetting("Смещение Z", 0.0f, -1.0f, 1.0f, 0.01f);
    public final FloatSetting rotateX = new FloatSetting("Поворот X", 0.0f, -180.0f, 180.0f, 1.0f);
    public final FloatSetting rotateY = new FloatSetting("Поворот Y", 0.0f, -180.0f, 180.0f, 1.0f);
    public final FloatSetting rotateZ = new FloatSetting("Поворот Z", 0.0f, -180.0f, 180.0f, 1.0f);
    public final BooleanSetting showSelf = new BooleanSetting("Показывать на себе", true);
    public final BooleanSetting showOthers = new BooleanSetting("Показывать на других", true);
    public final BooleanSetting showFriends = new BooleanSetting("Показывать на друзьях", true);
    public final BooleanSetting attackEnemies = new BooleanSetting("Атаковать врагов", true);
    public final BooleanSetting idleAnimation = new BooleanSetting("Idle-анимация", true);
    public final FloatSetting idleSpeed = new FloatSetting("Скорость idle", 1.0f, 0.1f, 3.0f, 0.05f)
            .visible(() -> idleAnimation.isState());
    public final FloatSetting idleStrength = new FloatSetting("Сила idle", 0.35f, 0.0f, 1.5f, 0.05f)
            .visible(() -> idleAnimation.isState());

    private final AllayEntityRenderState attackState = new AllayEntityRenderState();
    private AllayEntityModel attackModel;
    private int attackTargetId = Integer.MIN_VALUE;
    private long attackStartedAt;
    private long lastAttackAt;
    private long attackReturnStartedAt;
    private Vec3d attackReturnStartPos = new Vec3d(0.0, 0.0, 0.0);
    private float attackOrbitSeed;
    private float attackCurveSide;
    private float attackCurveLift;
    private float attackCurveDepth;
    private float attackRadiusJitter;
    private float attackHeightJitter;
    private float attackBobSeed;
    private float attackOrbitSpeed;
    private float attackOrbitDirection;
    private float attackLookYaw;
    private float attackLookPitch;
    private boolean attackLookInitialized;

    public Satellite() {
        super("Satellite", "Питомец-аллей на плече", ModuleCategory.RENDER);
        addSettings(
                shoulder,
                scale,
                offsetX,
                offsetY,
                offsetZ,
                rotateX,
                rotateY,
                rotateZ,
                showSelf,
                showOthers,
                showFriends,
                attackEnemies,
                idleAnimation,
                idleSpeed,
                idleStrength
        );
    }

    @Override
    public void onDisable() {
        clearAttackTarget();
        super.onDisable();
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (!attackEnemies.isState() || event == null || event.getPlayer() == null || event.getTarget() == null || mc.player == null) {
            return;
        }

        if (event.getPlayer().getId() != mc.player.getId() || event.getTarget() == mc.player) {
            return;
        }

        long now = System.currentTimeMillis();
        if (attackTargetId != event.getTarget().getId()) {
            attackStartedAt = now;
            randomizeAttackPath(now);
        }

        attackTargetId = event.getTarget().getId();
        lastAttackAt = now;
        attackReturnStartedAt = 0L;
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!attackEnemies.isState()) {
            clearAttackTarget();
            return;
        }

        updateAttackLifecycle();
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null || event == null) {
            return;
        }

        float tickDelta = event.getTickDelta();
        long now = System.currentTimeMillis();

        Entity target = updateAttackLifecycle();
        if (target == null) {
            return;
        }

        ensureAttackModel();
        if (attackModel == null) {
            return;
        }

        renderAttackSatellite(event, target, getAttackRenderPosition(target, tickDelta, now), tickDelta, now);
    }

    private void renderAttackSatellite(Event3DRender event, Entity target, Vec3d renderPos, float tickDelta, long now) {
        Vec3d cameraPos = event.getCamera().getPos();
        Vec3d targetPos = getInterpolatedEntityPos(target, tickDelta);
        float elapsed = (now - attackStartedAt) / 1000.0f;

        Vec3d focusPos = targetPos.add(0.0, target.getHeight() * 0.56, 0.0);
        float desiredYaw = getLookYaw(renderPos, focusPos);
        float desiredPitch = getLookPitch(renderPos, focusPos);

        if (!attackLookInitialized) {
            attackLookYaw = desiredYaw;
            attackLookPitch = desiredPitch;
            attackLookInitialized = true;
        } else {
            attackLookYaw = MathHelper.lerpAngleDegrees(0.32f, attackLookYaw, desiredYaw);
            attackLookPitch = MathHelper.lerp(0.24f, attackLookPitch, desiredPitch);
        }

        float headYaw = MathHelper.clamp(MathHelper.wrapDegrees(desiredYaw - attackLookYaw), -85.0f, 85.0f);

        MatrixStack matrices = event.getMatrices();
        matrices.push();
        matrices.translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - attackLookYaw));
        matrices.scale(scale.get(), scale.get(), scale.get());
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.translate(0.0f, -1.501f, 0.0f);

        attackState.age = mc.player.age + tickDelta + elapsed * 20.0f;
        attackState.limbFrequency = elapsed * 6.4f;
        attackState.limbAmplitudeMultiplier = 0.72f + MathHelper.sin(elapsed * 7.0f + attackBobSeed) * 0.12f;
        attackState.yawDegrees = headYaw;
        attackState.pitch = attackLookPitch;
        attackState.invisible = false;
        attackState.invisibleToPlayer = false;
        attackState.hasOutline = false;
        attackState.shaking = false;
        attackState.baby = false;
        attackState.touchingWater = target.isTouchingWater();
        attackState.bodyYaw = attackLookYaw;
        attackState.baseScale = 1.0f;
        attackState.ageScale = 1.0f;
        attackState.pose = target instanceof LivingEntity living ? living.getPose() : EntityPose.STANDING;
        attackState.deathTime = 0.0f;
        attackState.hurt = false;
        attackState.dancing = false;
        attackState.spinning = false;
        attackState.spinningAnimationTicks = 0.0f;
        attackState.itemHoldAnimationTicks = 0.65f;

        attackModel.setAngles(attackState);
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(attackModel.getLayer(ALLAY_TEXTURE));
        attackModel.render(matrices, vertexConsumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        immediate.draw();
        matrices.pop();
    }

    public boolean shouldRender(PlayerEntityRenderState playerState) {
        if (!isEnable() || mc.player == null || mc.world == null || playerState == null || playerState.spectator) {
            return false;
        }

        boolean self = playerState.id == mc.player.getId();
        if (self) {
            if (hasActiveAttackTarget()) {
                return false;
            }

            return shouldRenderOwnShoulderPet();
        }

        Entity entity = mc.world.getEntityById(playerState.id);
        if (entity instanceof PlayerEntity player
                && Lumis.INSTANCE != null
                && Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString())) {
            return showFriends.isState();
        }

        return showOthers.isState();
    }

    public boolean isLeftShoulder() {
        return shoulder.is("Левое");
    }

    public boolean hasActiveAttackTarget() {
        return updateAttackLifecycle() != null;
    }

    private boolean shouldRenderOwnShoulderPet() {
        return showSelf.isState() && !mc.options.getPerspective().isFirstPerson();
    }

    private Entity updateAttackLifecycle() {
        if (!attackEnemies.isState() || mc.world == null || mc.player == null || attackTargetId == Integer.MIN_VALUE) {
            return null;
        }

        Entity target = mc.world.getEntityById(attackTargetId);
        if (target == null || target.isRemoved() || target == mc.player) {
            clearAttackTarget();
            return null;
        }

        if (target instanceof LivingEntity living && !living.isAlive()) {
            clearAttackTarget();
            return null;
        }

        if (mc.player.squaredDistanceTo(target) > 4096.0) {
            clearAttackTarget();
            return null;
        }

        long now = System.currentTimeMillis();
        if (attackReturnStartedAt == 0L && now - lastAttackAt > ATTACK_FOLLOW_TIMEOUT_MS) {
            float elapsed = (now - attackStartedAt) / 1000.0f;
            attackReturnStartPos = getOrbitPosition(target, getInterpolatedEntityPos(target, 1.0f), elapsed);
            attackReturnStartedAt = now;
        }

        if (attackReturnStartedAt != 0L && now - attackReturnStartedAt > ATTACK_RETURN_DURATION_MS) {
            clearAttackTarget();
            return null;
        }

        return target;
    }

    private Vec3d getAttackRenderPosition(Entity target, float tickDelta, long now) {
        Vec3d shoulderPos = getShoulderWorldPosition(tickDelta);
        Vec3d targetPos = getInterpolatedEntityPos(target, tickDelta);
        float elapsed = (now - attackStartedAt) / 1000.0f;

        Vec3d orbitPos = getOrbitPosition(target, targetPos, elapsed);
        if (attackReturnStartedAt == 0L) {
            float launchProgress = MathHelper.clamp((now - attackStartedAt) / (float) ATTACK_LAUNCH_DURATION_MS, 0.0f, 1.0f);
            if (launchProgress < 1.0f) {
                return buildLaunchCurve(shoulderPos, orbitPos, launchProgress);
            }
            return orbitPos;
        }

        float returnProgress = MathHelper.clamp((now - attackReturnStartedAt) / (float) ATTACK_RETURN_DURATION_MS, 0.0f, 1.0f);
        return buildReturnCurve(attackReturnStartPos, shoulderPos, returnProgress);
    }

    private Vec3d getOrbitPosition(Entity target, Vec3d targetPos, float elapsed) {
        double baseRadius = Math.max(0.86, target.getWidth() * 1.05 + 0.46) * attackRadiusJitter;
        double angle = attackOrbitSeed * 0.017453292F + elapsed * attackOrbitSpeed * attackOrbitDirection;
        double radiusPulse = Math.sin(elapsed * 1.25f + attackBobSeed * 0.45f) * 0.07;
        double orbitRadius = baseRadius + radiusPulse;
        double orbitX = Math.cos(angle) * orbitRadius;
        double orbitZ = Math.sin(angle) * orbitRadius;
        double orbitY = targetPos.y
                + target.getHeight() * (0.78 + attackHeightJitter)
                + Math.sin(elapsed * 2.9f + attackBobSeed) * 0.20
                + Math.cos(elapsed * 1.8f + attackBobSeed * 0.8f) * 0.08;
        return new Vec3d(targetPos.x + orbitX, orbitY, targetPos.z + orbitZ);
    }

    private Vec3d buildLaunchCurve(Vec3d start, Vec3d end, float progress) {
        float eased = easeInOut(progress);
        Vec3d direction = end.subtract(start);
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        if (horizontal.lengthSquared() < 1.0E-4) {
            horizontal = new Vec3d(0.0, 0.0, 1.0);
        } else {
            horizontal = horizontal.normalize();
        }

        Vec3d sideways = new Vec3d(horizontal.z, 0.0, -horizontal.x).normalize();
        Vec3d lift = new Vec3d(0.0, attackCurveLift, 0.0);
        Vec3d control1 = start.add(sideways.multiply(attackCurveSide * 0.52)).add(lift.multiply(0.82));
        Vec3d control2 = end.add(sideways.multiply(-attackCurveSide * 0.28)).add(horizontal.multiply(attackCurveDepth * 0.18)).add(lift.multiply(0.58));
        return cubicBezier(start, control1, control2, end, eased);
    }

    private Vec3d buildReturnCurve(Vec3d start, Vec3d end, float progress) {
        float eased = easeInOut(progress);
        Vec3d direction = end.subtract(start);
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        if (horizontal.lengthSquared() < 1.0E-4) {
            horizontal = new Vec3d(0.0, 0.0, 1.0);
        } else {
            horizontal = horizontal.normalize();
        }

        Vec3d sideways = new Vec3d(horizontal.z, 0.0, -horizontal.x).normalize();
        Vec3d lift = new Vec3d(0.0, attackCurveLift * 0.72, 0.0);
        Vec3d control1 = start.add(sideways.multiply(-attackCurveSide * 0.24)).add(lift.multiply(0.62));
        Vec3d control2 = end.add(sideways.multiply(attackCurveSide * 0.30)).add(horizontal.multiply(-attackCurveDepth * 0.10)).add(lift.multiply(0.22));
        Vec3d bezier = cubicBezier(start, control1, control2, end, eased);
        return eased > 0.985f ? end : bezier;
    }

    private Vec3d getShoulderWorldPosition(float tickDelta) {
        Vec3d playerPos = getInterpolatedEntityPos(mc.player, tickDelta);
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, mc.player.prevBodyYaw, mc.player.bodyYaw);
        float yawRad = bodyYaw * 0.017453292F;

        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
        double side = (isLeftShoulder() ? 1.0 : -1.0) * mc.player.getWidth() * 0.42;
        double height = mc.player.getHeight() - (mc.player.isSneaking() ? 0.38 : 0.24);
        double back = 0.0;

        Vec3d shoulderPos = playerPos
                .add(0.0, height, 0.0)
                .add(right.multiply(side))
                .add(forward.multiply(back))
                .add(right.multiply(offsetX.get() * 0.65))
                .add(0.0, offsetY.get() * 0.45, 0.0)
                .add(forward.multiply(offsetZ.get() * 0.35));

        if (idleAnimation.isState()) {
            float time = (mc.player.age + tickDelta) * (0.7f + idleSpeed.get() * 0.65f);
            float bob = MathHelper.sin(time * 0.42f) * 0.03f * idleStrength.get();
            shoulderPos = shoulderPos.add(0.0, bob, 0.0);
        }

        return shoulderPos;
    }

    private Vec3d getInterpolatedEntityPos(Entity entity, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.prevX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.prevY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ())
        );
    }

    private Vec3d cubicBezier(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        float inv = 1.0f - t;
        double w0 = inv * inv * inv;
        double w1 = 3.0 * inv * inv * t;
        double w2 = 3.0 * inv * t * t;
        double w3 = t * t * t;
        return new Vec3d(
                p0.x * w0 + p1.x * w1 + p2.x * w2 + p3.x * w3,
                p0.y * w0 + p1.y * w1 + p2.y * w2 + p3.y * w3,
                p0.z * w0 + p1.z * w1 + p2.z * w2 + p3.z * w3
        );
    }

    private float easeInOut(float value) {
        float clamped = MathHelper.clamp(value, 0.0f, 1.0f);
        return clamped * clamped * clamped * (clamped * (clamped * 6.0f - 15.0f) + 10.0f);
    }

    private void ensureAttackModel() {
        if (attackModel != null || mc == null) {
            return;
        }

        attackModel = new AllayEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.ALLAY));
    }

    private void randomizeAttackPath(long now) {
        attackOrbitSeed = randomRange(0.0f, 360.0f);
        attackCurveSide = randomRange(-1.10f, 1.10f);
        attackCurveLift = randomRange(0.48f, 0.96f);
        attackCurveDepth = randomRange(-0.42f, 0.42f);
        attackRadiusJitter = randomRange(0.92f, 1.24f);
        attackHeightJitter = randomRange(-0.06f, 0.14f);
        attackBobSeed = randomRange(0.0f, 6.2831855f);
        attackOrbitSpeed = randomRange(1.7f, 2.45f);
        attackOrbitDirection = Math.random() > 0.5 ? 1.0f : -1.0f;
    }

    private float randomRange(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }

    private float getLookYaw(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    }

    private float getLookPitch(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontalDistance)), -35.0f, 35.0f);
    }

    private void clearAttackTarget() {
        attackTargetId = Integer.MIN_VALUE;
        attackStartedAt = 0L;
        lastAttackAt = 0L;
        attackReturnStartedAt = 0L;
        attackReturnStartPos = new Vec3d(0.0, 0.0, 0.0);
        attackLookYaw = 0.0f;
        attackLookPitch = 0.0f;
        attackLookInitialized = false;
    }
}
