package fun.lumis.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.combat.components.RotationsSystem;

/**
 * Polar — ротация, описывающая ошибку прицела в полярных координатах (радиус + угол),
 * настроенная против ротационных проверок Polar AC на 1.21.4.
 *
 * <p>Ядро — полярная модель ошибки: вектор {@code (Δyaw, Δpitch)} раскладывается в радиус
 * {@code r} (полная угловая дистанция до цели) и фазу {@code θ}. Радиус сокращается по
 * нелинейной кривой, фаза получает спиральный снос — траектория прицела выходит
 * криволинейной, а не прямой.
 *
 * <p>Поверх ядра — слой человекоподобности под конкретные эвристики Polar:
 * <ul>
 *   <li><b>GCD-консистентность</b> (главная проверка Polar): каждая дельта углов между
 *       пакетами кратна шагу мыши {@code gcd}; квантование идёт с накоплением от
 *       последнего <i>отправленного</i> угла, без дрейфа float;</li>
 *   <li><b>Время реакции</b>: при появлении новой цели прицел не телепортируется —
 *       идёт пред-наводка с микро-джиттером в течение реакционной паузы;</li>
 *   <li><b>Точка наведения внутри хитбокса</b> с апериодичным дрейфом (не центр — люди
 *       не целятся в идеальный центр), что ломает проверку «aim-snap to center»;</li>
 *   <li><b>Угловой шум</b>, ограниченный долей <i>углового</i> размера цели — прицел
 *       гарантированно остаётся внутри полигона на любой дистанции, но скорость/ускорение
 *       углов перестают быть детерминированными (анти «smooth-aim/consistency»);</li>
 *   <li><b>Недетерминированная скорость подхода</b>: коэффициент сокрашения радиуса берётся
 *       со случайным разбросом на каждый сегмент — нет постоянной экспоненты затухания.</li>
 * </ul>
 * Источник луча — {@link net.minecraft.entity.player.PlayerEntity#getEyePos()} (учитывает
 * позу 1.21.4: присед/плавание/ползание), позиция цели упреждается по линейной скорости.
 */
public class PolarRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;

    // --- Состояние интегратора (углы прицела, ведущиеся независимо от игрока) ---
    private float currentYaw;
    private float currentPitch;

    // Последние ОТПРАВЛЕННЫЕ углы — база GCD-квантования (накопление без дрейфа float).
    private float lastSentYaw;
    private float lastSentPitch;

    // --- Точка наведения внутри хитбокса (дрейф) ---
    private Vec3d aimOffset = Vec3d.ZERO;
    private long lastRepick;
    private long repickInterval;

    // --- Угловой шум (мультисинус) ---
    private float noiseAngle;

    // --- Недетерминированная скорость подхода (разброс на сегмент) ---
    private float approachFar;
    private float approachNear;
    private float sweepMax; // текущий спиральный снос фазы (град), рандомизируется

    // --- Время реакции ---
    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;

    // Угол (в градусах ошибки), выше которого подход считается «дальним».
    private static final float FAR_RADIUS = 45.0F;
    // Человекоподобный потолок угловой скорости одного тика.
    private static final float MAX_STEP = 32.0F;
    // Радиус, ниже которого снимаем спиральный снос (режим сопровождения).
    private static final float SNAP_RADIUS = 1.2F;
    // Упреждение позиции цели на N тиков вперёд.
    private static final int PREDICT_TICKS = 1;
    // Базовая амплитуда углового шума (масштабируется дистанцией и угловым размером цели).
    private static final float NOISE_AMPLITUDE = 1.6F;

    public void reset() {
        trackedTarget = null;
        aimOffset = Vec3d.ZERO;
        noiseAngle = 0.0F;
        lastRepick = 0;
        repickInterval = 0;
        reactionComplete = false;
        reactionMs = 0;
        firstSeenTime = 0;
        rollApproach();
        if (mc.player != null) {
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
        } else {
            currentYaw = currentPitch = 0.0F;
            lastSentYaw = lastSentPitch = 0.0F;
        }
    }

    /** Вызывается сразу после отправки пакета атаки — прицел продолжает вести цель. */
    public void onAttack() {
        // Никакого ухода pitch в небо: мгновенный флаг для ротационных анти-читов.
    }

    /** Шаг мыши (GCD) от текущей чувствительности — тот же расчёт, что у ванильного клиента. */
    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    /** Случайный разброс параметров подхода на сегмент — убирает детерминизм скорости. */
    private void rollApproach() {
        approachFar = 0.36F + (float) (Math.random() * 0.12F);   // 0.36..0.48
        approachNear = 0.14F + (float) (Math.random() * 0.08F);  // 0.14..0.22
        sweepMax = 9.0F + (float) (Math.random() * 8.0F);        // 9..17 град
    }

    /** Линейная скорость цели за прошлый тик (для упреждения позиции). */
    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    /** Реакционная пауза в мс по начальному угловому отклонению (дальше цель — дольше реакция). */
    private int computeReaction(float angle) {
        if (angle > 130.0F) return 140 + (int) (Math.random() * 90);
        if (angle > 70.0F) return 90 + (int) (Math.random() * 60);
        if (angle > 30.0F) return 45 + (int) (Math.random() * 35);
        return 12 + (int) (Math.random() * 20);
    }

    /** Начальное угловое отклонение от текущего взгляда игрока до центра цели. */
    private float measureAngle(LivingEntity target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d mid = target.getBoundingBox().getCenter();
        Vec3d delta = mid.subtract(eyes);
        float needYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float needPitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
        float dYaw = Math.abs(MathHelper.wrapDegrees(needYaw - mc.player.getYaw()));
        float dPitch = Math.abs(needPitch - mc.player.getPitch());
        return dYaw + dPitch;
    }

    /** Перевыбор точки наведения внутри хитбокса (гаусс-подобный сдвиг от центра, внутри полигона). */
    private void selectAimPoint(LivingEntity target) {
        Box box = target.getBoundingBox();
        double hw = (box.maxX - box.minX) * 0.5;
        double hh = (box.maxY - box.minY) * 0.5;
        double hd = (box.maxZ - box.minZ) * 0.5;

        // Гаусс-подобный сдвиг (сумма двух равномерных) масштабом до ~0.6 полуразмера —
        // точка заведомо остаётся внутри коробки, но не совпадает с центром.
        double gx = ((Math.random() + Math.random()) - 1.0) * 0.6;
        double gy = ((Math.random() + Math.random()) - 1.0) * 0.6;
        double gz = ((Math.random() + Math.random()) - 1.0) * 0.6;

        aimOffset = new Vec3d(gx * hw, gy * hh, gz * hd);
        lastRepick = System.currentTimeMillis();
        repickInterval = 260 + (long) (Math.random() * 520); // апериодичная смена точки
    }

    /** Мультичастотный угловой шум (yaw, pitch) — недетерминированная микро-составляющая взгляда. */
    private float[] generateNoise(float dist) {
        noiseAngle += 0.040F + (float) (Math.random() * 0.020F);

        float scale = MathHelper.clamp(dist / 4.5F, 0.25F, 1.0F);
        float amp = NOISE_AMPLITUDE * scale;

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

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        float gcd = calcGcd();

        // --- Сброс состояния при смене цели — стартуем из реального положения камеры ---
        if (trackedTarget != target) {
            trackedTarget = target;
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
            noiseAngle = (float) (Math.random() * Math.PI * 2);
            rollApproach();
            selectAimPoint(target);

            reactionMs = computeReaction(measureAngle(target));
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        Vec3d eye = mc.player.getEyePos();

        // Предсказанная коробка цели (упреждение по линейной скорости) и точка наведения в ней.
        Box box = getPredictedBox(target);
        Vec3d predictedCenter = box.getCenter().add(targetVelocity(target).multiply(PREDICT_TICKS));
        float distance = (float) eye.distanceTo(predictedCenter);

        // --- Время реакции: пред-наводка с микро-джиттером, без мгновенного снапа ---
        if (!reactionComplete) {
            long elapsed = System.currentTimeMillis() - firstSeenTime;
            if (elapsed < reactionMs) {
                float jitterY = ((float) Math.random() - 0.5F) * 0.22F;
                float jitterP = ((float) Math.random() - 0.5F) * 0.14F;
                emit(lastSentYaw + jitterY, lastSentPitch + jitterP, gcd);
                return;
            }
            reactionComplete = true;
        }

        // --- Апериодичная смена точки наведения внутри хитбокса ---
        if (System.currentTimeMillis() - lastRepick >= repickInterval) {
            selectAimPoint(target);
            rollApproach();
        }

        Vec3d aimPoint = predictedCenter.add(aimOffset);
        Vec3d dir = aimPoint.subtract(eye);
        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

        // --- Декартова ошибка -> полярные координаты (радиус r, фаза theta) ---
        float errYaw = MathHelper.wrapDegrees(wantYaw - currentYaw);
        float errPitch = wantPitch - currentPitch;
        float r = (float) Math.sqrt(errYaw * errYaw + errPitch * errPitch);

        if (r > 1.0E-4F) {
            float theta = (float) Math.atan2(errPitch, errYaw);

            // Сокращение радиуса по нелинейной (рандомизированной) кривой подхода.
            float t = MathHelper.clamp(r / FAR_RADIUS, 0.0F, 1.0F);
            float approach = MathHelper.lerp(t, approachNear, approachFar);
            float newR = r * (1.0F - approach);

            // Спиральный снос фазы, убывающий вместе с радиусом.
            float sweep = (r > SNAP_RADIUS) ? (float) Math.toRadians(sweepMax * t) : 0.0F;
            float newTheta = theta + sweep;

            // Полярные координаты -> новый вектор ошибки -> целевые углы.
            float goalYaw = wantYaw - newR * (float) Math.cos(newTheta);
            float goalPitch = wantPitch - newR * (float) Math.sin(newTheta);

            // Предохранитель от телепорт-флика: потолок угловой скорости одного тика.
            float stepYaw = MathHelper.clamp(MathHelper.wrapDegrees(goalYaw - currentYaw), -MAX_STEP, MAX_STEP);
            float stepPitch = MathHelper.clamp(goalPitch - currentPitch, -MAX_STEP, MAX_STEP);

            currentYaw += stepYaw;
            currentPitch = MathHelper.clamp(currentPitch + stepPitch, -89.0F, 89.0F);
        }

        // --- Угловой шум, ограниченный долей УГЛОВОГО размера цели (прицел не выходит из хитбокса) ---
        float[] noise = generateNoise(distance);
        float halfW = (float) Math.max(0.3, (box.maxX - box.minX) * 0.5);
        float halfH = (float) Math.max(0.4, (box.maxY - box.minY) * 0.5);
        float dd = Math.max(0.8F, distance);
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dd));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dd));

        float devYaw = MathHelper.clamp(noise[0], -angHalfYaw * 0.4F, angHalfYaw * 0.4F);
        float devPitch = MathHelper.clamp(noise[1], -angHalfPitch * 0.4F, angHalfPitch * 0.4F);

        emit(currentYaw + devYaw, currentPitch + devPitch, gcd);
    }

    /**
     * GCD-квантование и отправка. Дельта {@code (out - lastSent)} приводится к кратному gcd —
     * именно это проверяет Polar (любая дробная часть, не делящаяся на шаг мыши, = флаг).
     */
    private void emit(float yaw, float pitch, float gcd) {
        float outY = yaw;
        float outP = MathHelper.clamp(pitch, -89.0F, 89.0F);
        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
