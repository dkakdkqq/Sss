package fun.lumis.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.combat.components.RotationsSystem;
import fun.lumis.client.modules.impl.combat.components.rotations.sloth.DynamicRotationCurve;
import fun.lumis.client.modules.impl.combat.components.rotations.sloth.MultiPointTargeting;
import fun.lumis.client.modules.impl.combat.components.rotations.sloth.TargetDesync;

/**
 * SlothAC — ротация для AttackAura, объединяющая три компонента:
 * <ol>
 *   <li>{@link MultiPointTargeting} — многоточечное наведение с гауссовым распределением весов;</li>
 *   <li>{@link DynamicRotationCurve} — динамическая угловая скорость на случайных кривых Безье;</li>
 *   <li>{@link TargetDesync} — система микро-отводов за пределы полигона цели.</li>
 * </ol>
 */
public class SlothRotation extends RotationsSystem implements QClient {

    private final MultiPointTargeting multiPoint = new MultiPointTargeting();
    private final DynamicRotationCurve curve = new DynamicRotationCurve();
    private final TargetDesync desync = new TargetDesync();

    private LivingEntity trackedTarget;

    private float currentYaw;
    private float currentPitch;

    private float smoothYaw;
    private float smoothPitch;

    // Текущее смещение точки наведения относительно центра bounding box (из MultiPointTargeting).
    private Vec3d aimOffset = Vec3d.ZERO;
    private long lastRepick;
    private long repickInterval;

    private float noiseAngle;
    private final float noiseAmplitude = 1.8F;

    private int hitPhase;
    private int hitTimer;
    private float pitchBeforeHit;

    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;

    private float lastSentYaw;
    private float lastSentPitch;

    // Порог, ниже которого считаем, что цель практически на прицеле (режим сопровождения).
    private static final float TRACK_THRESHOLD = 6.0F;

    public void reset() {
        trackedTarget = null;
        aimOffset = Vec3d.ZERO;
        noiseAngle = 0.0F;
        hitPhase = hitTimer = 0;
        firstSeenTime = 0;
        reactionComplete = false;
        reactionMs = 0;
        lastRepick = 0;
        repickInterval = 0;

        curve.reset();
        desync.reset();
        multiPoint.reset();

        if (mc.player != null) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;
        } else {
            currentYaw = currentPitch = 0.0F;
            lastSentYaw = lastSentPitch = 0.0F;
            smoothYaw = smoothPitch = 0.0F;
        }
    }

    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    public void onAttack() {
        // Анимация удара ОТКЛЮЧЕНА: прежний вариант уводил pitch к -89° (взгляд в небо)
        // на ~2 секунды после каждого удара — мгновенный флаг для ротационных анти-читов
        // (удар приходится "не глядя на цель"). Прицел теперь просто продолжает вести цель.
    }

    private float measureAngle(LivingEntity e) {
        if (mc.player == null) return 0.0F;

        Vec3d eyes = mc.player.getEyePos();
        Vec3d mid = e.getBoundingBox().getCenter();
        Vec3d delta = mid.subtract(eyes);

        float needYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float needPitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));

        float dYaw = Math.abs(MathHelper.wrapDegrees(needYaw - mc.player.getYaw()));
        float dPitch = Math.abs(needPitch - mc.player.getPitch());

        return dYaw + dPitch;
    }

    private int computeReaction(float angle) {
        if (angle > 130.0F) return 140 + (int) (Math.random() * 90);
        if (angle > 70.0F) return 90 + (int) (Math.random() * 60);
        if (angle > 30.0F) return 45 + (int) (Math.random() * 35);
        return 12 + (int) (Math.random() * 20);
    }

    private boolean isMovingForward() {
        if (mc.player == null) return false;
        return mc.options.forwardKey.isPressed();
    }

    private boolean isOvertakingTarget(LivingEntity target) {
        if (mc.player == null || target == null) return false;

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();

        Vec3d playerVel = new Vec3d(
                mc.player.getX() - mc.player.prevX,
                mc.player.getY() - mc.player.prevY,
                mc.player.getZ() - mc.player.prevZ
        );

        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );

        Vec3d toTarget = targetPos.subtract(playerPos).normalize();

        double playerSpeedToTarget = playerVel.dotProduct(toTarget);
        double targetSpeedToPlayer = targetVel.dotProduct(toTarget.multiply(-1));

        double relativeSpeed = playerSpeedToTarget + targetSpeedToPlayer;

        double distance = Math.sqrt(
                Math.pow(playerPos.x - targetPos.x, 2) +
                        Math.pow(playerPos.z - targetPos.z, 2)
        );

        return relativeSpeed > 0.05 && distance < 4.0;
    }

    private float[] generateNoise(float dist) {
        noiseAngle += 0.042F + (float) (Math.random() * 0.018F);

        float scale = MathHelper.clamp(dist / 4.5F, 0.25F, 1.0F);
        float amp = noiseAmplitude * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.87) * 0.38F;
        float n2 = (float) Math.sin(noiseAngle * 1.43 + 0.75) * 0.28F;
        float n3 = (float) Math.cos(noiseAngle * 1.18 + 0.35) * 0.32F;
        float n4 = (float) Math.cos(noiseAngle * 1.76 + 1.42) * 0.23F;

        float yawNoise = (n1 + n2) * amp;
        float pitchNoise = (n3 + n4) * amp * 0.52F;

        yawNoise += ((float) Math.random() - 0.5F) * amp * 0.13F;
        pitchNoise += ((float) Math.random() - 0.5F) * amp * 0.09F;

        return new float[]{yawNoise, pitchNoise};
    }

    private float smoothStep(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private float accelCurve(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - x) * (1.0F - x);
    }

    private float smoothLerp(float from, float to, float alpha) {
        alpha = MathHelper.clamp(alpha, 0.0F, 1.0F);
        float delta = MathHelper.wrapDegrees(to - from);
        return from + delta * alpha;
    }

    private float calculateCurrentAngle(float targetYaw, float targetPitch) {
        float dYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float dPitch = Math.abs(targetPitch - currentPitch);
        return dYaw + dPitch;
    }

    /**
     * Перевыбирает точку многоточечного наведения (компонент 1) и планирует следующий
     * апериодичный момент пересбора.
     */
    private void selectAimPoint(LivingEntity target, boolean force) {
        double sigma = 0.15 + Math.random() * 0.06;
        MultiPointTargeting.AimPoint point = force
                ? multiPoint.reselect(target, sigma)
                : multiPoint.selectPoint(multiPoint.computePoints(target, sigma));
        if (point != null) {
            aimOffset = point.offset;
        }
        lastRepick = System.currentTimeMillis();
        repickInterval = 260 + (long) (Math.random() * 520); // динамический интервал смены точки
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        boolean playerFlying = mc.player.isGliding();

        if (trackedTarget != target) {
            trackedTarget = target;

            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;

            hitPhase = hitTimer = 0;
            noiseAngle = (float) (Math.random() * Math.PI * 2);

            curve.reset();
            desync.reset();
            multiPoint.reset();
            selectAimPoint(target, false);

            float angleDiff = measureAngle(target);
            reactionMs = computeReaction(angleDiff);
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetCenter = getPredictedPoint(target, target.getBoundingBox().getCenter());
        float distance = (float) eyePos.distanceTo(targetCenter);

        float gcd = calcGcd();

        // --- Время реакции (пре-наводка с микро-джиттером) ---
        if (!reactionComplete) {
            long elapsed = System.currentTimeMillis() - firstSeenTime;

            if (elapsed < reactionMs) {
                float jitterY = ((float) Math.random() - 0.5F) * 0.22F;
                float jitterP = ((float) Math.random() - 0.5F) * 0.14F;

                float outY = lastSentYaw + jitterY;
                float outP = MathHelper.clamp(lastSentPitch + jitterP, -89.0F, 89.0F);

                outY -= (outY - lastSentYaw) % gcd;
                outP -= (outP - lastSentPitch) % gcd;

                lastSentYaw = outY;
                lastSentPitch = outP;

                RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
                return;
            }

            reactionComplete = true;
        }

        float[] noise = generateNoise(distance);

        // --- Анимация удара по pitch (крит-движение) ---
        if (hitPhase > 0) {
            hitTimer++;

            int upDuration = 25;
            int downDuration = 20;
            float targetPitchUp = -89.0F;

            if (hitPhase == 1) {
                float t = hitTimer / (float) upDuration;
                t = MathHelper.clamp(t, 0.0F, 1.0F);
                float curved = accelCurve(t);
                currentPitch = MathHelper.lerp(curved, pitchBeforeHit, targetPitchUp);

                if (hitTimer >= upDuration) {
                    hitPhase = 2;
                    hitTimer = 0;
                }
            } else if (hitPhase == 2) {
                float goal = pitchBeforeHit;
                float t = hitTimer / (float) downDuration;
                t = MathHelper.clamp(t, 0.0F, 1.0F);
                float curved = smoothStep(t);
                currentPitch = MathHelper.lerp(curved, targetPitchUp, goal);

                if (hitTimer >= downDuration) {
                    hitPhase = 0;
                    hitTimer = 0;
                }
            }

            float outY = currentYaw + noise[0];
            float outP = MathHelper.clamp(currentPitch + noise[1], -89.0F, 89.0F);

            outY -= (outY - lastSentYaw) % gcd;
            outP -= (outP - lastSentPitch) % gcd;

            lastSentYaw = outY;
            lastSentPitch = outP;

            RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
            return;
        }

        // --- Компонент 1: многоточечное наведение ---
        // Возврат после микро-отвода => пересобрать точку наведения.
        if (desync.consumeJustReturned()) {
            selectAimPoint(target, true);
        }
        // Динамическая (апериодичная) смена точки наведения.
        if (System.currentTimeMillis() - lastRepick >= repickInterval) {
            selectAimPoint(target, false);
        }

        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );

        int predictTicks = shouldUseElytraPredict(target) ? 0 : 2;
        Vec3d predictedCenter = targetCenter.add(targetVel.multiply(predictTicks));
        Vec3d aimPos = predictedCenter.add(aimOffset);
        Vec3d direction = aimPos.subtract(eyePos);

        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalLength()));

        float diffYaw = MathHelper.wrapDegrees(wantYaw - currentYaw);
        float diffPitch = wantPitch - currentPitch;
        float totalDelta = (float) Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);

        // --- Компонент 2: динамическая угловая скорость без фиксированной константы ---
        float baseFactor;
        if (totalDelta > TRACK_THRESHOLD) {
            if (!curve.isActive()) {
                curve.newSegment(totalDelta);
            }
            baseFactor = curve.step(totalDelta);
        } else {
            baseFactor = curve.track();
        }

        // Контекстный множитель (не линейный по дистанции): полёт/обгон цели => мягче.
        float speedMultiplier = 1.0F;
        if (playerFlying) {
            float currentAngle = calculateCurrentAngle(wantYaw, wantPitch);
            if (currentAngle > 120.0F) {
                speedMultiplier = 0.30F;
            } else if (currentAngle > 80.0F) {
                float t = (currentAngle - 80.0F) / 40.0F;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.55F, 0.30F);
            } else if (currentAngle > 25.0F) {
                float t = (currentAngle - 25.0F) / 55.0F;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.80F, 0.55F);
            } else {
                speedMultiplier = 0.80F + (0.20F * (1.0F - currentAngle / 25.0F));
            }
        } else if (isMovingForward() || isOvertakingTarget(target)) {
            speedMultiplier = 0.68F;
        }

        float factor = MathHelper.clamp(baseFactor * speedMultiplier, 0.0F, 1.0F);

        // Предохранитель от «телепорт-флика»: ограничиваем угловую скорость одного тика
        // человекоподобным максимумом, чтобы не было мгновенных доворотов на 90°+.
        float maxStep = 34.0F;
        float moveYaw = MathHelper.clamp(diffYaw * factor, -maxStep, maxStep);
        float movePitch = MathHelper.clamp(diffPitch * factor, -maxStep, maxStep);

        currentYaw += moveYaw;
        currentPitch += movePitch;
        currentPitch = MathHelper.clamp(currentPitch, -89.0F, 89.0F);

        // Лёгкое выходное сглаживание поверх кривой.
        float smoothFactor = playerFlying ? (0.45F + speedMultiplier * 0.35F) : 0.86F;
        smoothYaw = smoothLerp(smoothYaw, currentYaw, smoothFactor);
        smoothPitch = smoothLerp(smoothPitch, currentPitch, smoothFactor * 0.95F);

        // --- Компонент 3: микро-отводы (Target Desynchronization) ---
        float[] dev = desync.update(target, distance, true);

        // Итоговое отклонение (шум + микро-отвод) ограничиваем долей УГЛОВОГО размера цели,
        // чтобы прицел гарантированно оставался ВНУТРИ хитбокса на любой дистанции
        // (фиксированный шум в градусах на расстоянии выходит за маленький угловой хитбокс).
        var box = target.getBoundingBox();
        float halfW = (float) Math.max(0.3, (box.maxX - box.minX) * 0.5);
        float halfH = (float) Math.max(0.4, (box.maxY - box.minY) * 0.5);
        float dd = Math.max(0.8F, distance);
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dd));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dd));

        float devYaw = MathHelper.clamp(noise[0] + dev[0], -angHalfYaw * 0.4F, angHalfYaw * 0.4F);
        float devPitch = MathHelper.clamp(noise[1] + dev[1], -angHalfPitch * 0.4F, angHalfPitch * 0.4F);

        float outY = smoothYaw + devYaw;
        float outP = MathHelper.clamp(smoothPitch + devPitch, -89.0F, 89.0F);

        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
