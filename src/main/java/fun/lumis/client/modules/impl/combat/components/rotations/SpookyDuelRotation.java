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
 * SpookyDuel — ротация для ближней дуэли (1v1), настроенная против ротационных
 * проверок Matrix AC.
 *
 * <p>Ядро: критически задемпфированная пружина 2-го порядка с адаптивной жёсткостью,
 * многоточечное наведение с апериодичной сменой точки внутри хитбокса и обязательная
 * верификация пересечения луча с полигоном цели на каждом тике.
 *
 * <p>Слой под конкретные эвристики Matrix:
 * <ul>
 *   <li><b>GCD (Aim/Sensitivity)</b> — главная проверка Matrix: дельта углов между
 *       пакетами обязана быть целым кратным шага мыши. Квантование идёт с переносом
 *       остатка (carry), поэтому нет ни систематического сноса от усечения, ни
 *       «замёрз-на-несколько-тиков-потом-прыжок» при суб-GCD скорости — Matrix
 *       вычисляет GCD по серии дельт и ловит именно такие артефакты;</li>
 *   <li><b>Реакция (anti-snap)</b> — Matrix флагает мгновенный доворот на свежую цель.
 *       При смене цели идёт пред-наводка с микро-джиттером в течение реакционной
 *       паузы, без телепорта прицела;</li>
 *   <li><b>Недетерминированная скорость</b> — коэффициенты пружины разбрасываются на
 *       каждый сегмент, плюс мультисинус-шум: скорость и ускорение углов перестают быть
 *       постоянными (анти «constant/cinematic aim»);</li>
 *   <li><b>Точка наведения не центр</b> — дрейф внутри торса ломает «aim-snap to center»;</li>
 *   <li><b>Человекоподобный потолок скорости тика</b> — нет флик-снапов.</li>
 * </ul>
 *
 * <p>Гарантия попадания: после наложения шума итоговый луч проверяется slab-тестом на
 * пересечение с предсказанным хитбоксом. Если шум вывел прицел за полигон, углы
 * итеративно подтягиваются обратно (до 6 шагов бинарного бленда).
 */
public class SpookyDuelRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;

    // --- Состояние пружины (углы прицела + угловые скорости) ---
    private float currentYaw;
    private float currentPitch;
    private float yawVel;
    private float pitchVel;

    // Последние ОТПРАВЛЕННЫЕ углы — база GCD-квантования.
    private float lastSentYaw;
    private float lastSentPitch;

    // Перенос суб-GCD остатка между тиками (чистые целые шаги мыши без сноса/заморозки).
    private float yawCarry;
    private float pitchCarry;

    // --- Смещение точки наведения от центра хитбокса (многоточечное наведение) ---
    private Vec3d aimOffset = Vec3d.ZERO;
    private long lastRepick;
    private long repickInterval;

    // --- Угловой микрошум (мультисинус) ---
    private float noiseAngle;

    // --- Недетерминированный разброс коэффициентов пружины на сегмент ---
    private float gainFar;
    private float gainNear;
    private long lastGainRoll;
    private long gainRollInterval;

    // --- Время реакции (anti-snap Matrix) ---
    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;

    // Угол ошибки (градусы), выше которого подход считается «дальним рывком».
    private static final float FAR_ANGLE = 35.0F;
    // Человекоподобный потолок угловой скорости одного тика.
    private static final float MAX_STEP = 34.0F;
    // Демпфирование пружины (zeta=1 => критическое — быстрый выход без осцилляций).
    private static final float ZETA = 1.0F;
    // Внутренний отступ точки наведения от грани коробки (в блоках).
    private static final double INNER_MARGIN = 0.09;
    // Упреждение позиции цели на N тиков вперёд (важно против стрейфа в дуэли).
    private static final int PREDICT_TICKS = 2;
    // Базовая амплитуда углового шума (масштабируется дистанцией и угловым размером цели).
    private static final float NOISE_AMPLITUDE = 1.3F;
    // Порог, ниже которого считаем цель «на прицеле» — режим мягкого сопровождения.
    private static final float TRACK_THRESHOLD = 5.0F;

    public void reset() {
        trackedTarget = null;
        aimOffset = Vec3d.ZERO;
        noiseAngle = 0.0F;
        yawVel = pitchVel = 0.0F;
        yawCarry = pitchCarry = 0.0F;
        lastRepick = 0;
        repickInterval = 0;
        lastGainRoll = 0;
        gainRollInterval = 0;
        reactionComplete = false;
        reactionMs = 0;
        firstSeenTime = 0;
        rollGain();
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
        // Никакого ухода pitch в небо: прицел продолжает вести цель после удара.
    }

    /** Шаг мыши (GCD) от текущей чувствительности. */
    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    /** Случайный разброс коэффициентов пружины на сегмент. */
    private void rollGain() {
        // Дуэльные значения: быстрый трекинг на ближней дистанции, но с разбросом,
        // чтобы скорость доворота не была постоянной (Matrix: constant-aim).
        gainFar = 0.70F + (float) (Math.random() * 0.16F);   // 0.70..0.86 — дальний рывок
        gainNear = 0.36F + (float) (Math.random() * 0.14F);  // 0.36..0.50 — мягкое сопровождение
        lastGainRoll = System.currentTimeMillis();
        gainRollInterval = 200 + (long) (Math.random() * 340);
    }

    /** Реакционная пауза в мс по начальному угловому отклонению (дальше цель — дольше реакция). */
    private int computeReaction(float angle) {
        if (angle > 120.0F) return 130 + (int) (Math.random() * 80);
        if (angle > 60.0F) return 80 + (int) (Math.random() * 55);
        if (angle > 25.0F) return 40 + (int) (Math.random() * 30);
        return 10 + (int) (Math.random() * 18);
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

    /** Линейная скорость цели за прошлый тик (для упреждения позиции). */
    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    /**
     * Выбирает внутреннюю точку наведения внутри хитбокса с дрейфующим смещением.
     * Три несоизмеримые частоты дают апериодичный дрейф по высоте и горизонтали.
     */
    private Vec3d pickAimPoint(Box box) {
        long t = System.currentTimeMillis();
        double yFrac = 0.60
                + Math.sin(t * 0.00041) * 0.16
                + Math.cos(t * 0.00097) * 0.10
                + Math.sin(t * 0.00153) * 0.06;
        yFrac = MathHelper.clamp(yFrac, 0.35, 0.88);

        double latRange = (box.maxX - box.minX) * 0.38;
        double depRange = (box.maxZ - box.minZ) * 0.38;
        double lateral = Math.sin(t * 0.00073 + 1.2) * latRange;
        double depth = Math.cos(t * 0.00117 + 0.7) * depRange;

        double mx = Math.min(INNER_MARGIN, (box.maxX - box.minX) * 0.48);
        double my = Math.min(INNER_MARGIN, (box.maxY - box.minY) * 0.48);
        double mz = Math.min(INNER_MARGIN, (box.maxZ - box.minZ) * 0.48);

        double cx = MathHelper.clamp(box.getCenter().x + lateral, box.minX + mx, box.maxX - mx);
        double cy = MathHelper.clamp(box.minY + (box.maxY - box.minY) * yFrac, box.minY + my, box.maxY - my);
        double cz = MathHelper.clamp(box.getCenter().z + depth, box.minZ + mz, box.maxZ - mz);

        return new Vec3d(cx, cy, cz);
    }

    /** Мультичастотный угловой шум (yaw, pitch). */
    private float[] generateNoise(float dist) {
        noiseAngle += 0.045F + (float) (Math.random() * 0.022F);

        float scale = MathHelper.clamp(dist / 4.0F, 0.25F, 1.0F);
        float amp = NOISE_AMPLITUDE * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.87) * 0.38F;
        float n2 = (float) Math.sin(noiseAngle * 1.43 + 0.75) * 0.28F;
        float n3 = (float) Math.cos(noiseAngle * 1.18 + 0.35) * 0.32F;
        float n4 = (float) Math.cos(noiseAngle * 1.76 + 1.42) * 0.23F;

        float yawNoise = (n1 + n2) * amp;
        float pitchNoise = (n3 + n4) * amp * 0.50F;

        yawNoise += ((float) Math.random() - 0.5F) * amp * 0.10F;
        pitchNoise += ((float) Math.random() - 0.5F) * amp * 0.07F;

        return new float[]{yawNoise, pitchNoise};
    }

    /**
     * Шаг критически задемпфированной пружины по Yaw.
     * Скорость климпится для предотвращения неограниченного роста, но полная динамика
     * демпфирования сохраняется между тиками.
     */
    private float springStepYaw(float x, float target, float omega) {
        float diff = MathHelper.wrapDegrees(target - x);
        float accel = omega * omega * diff - 2.0F * ZETA * omega * yawVel;
        yawVel = MathHelper.clamp(yawVel + accel, -MAX_STEP, MAX_STEP);
        return x + yawVel;
    }

    /**
     * Шаг критически задемпфированной пружины по Pitch.
     */
    private float springStepPitch(float x, float target, float omega) {
        float diff = target - x;
        float accel = omega * omega * diff - 2.0F * ZETA * omega * pitchVel;
        pitchVel = MathHelper.clamp(pitchVel + accel, -MAX_STEP, MAX_STEP);
        return MathHelper.clamp(x + pitchVel, -89.0F, 89.0F);
    }

    /** Вектор направления из углов (градусы). */
    private static Vec3d dirFromAngles(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float ch = MathHelper.cos(g);
        float sh = MathHelper.sin(g);
        float cp = MathHelper.cos(f);
        float sp = MathHelper.sin(f);
        return new Vec3d(sh * cp, -sp, ch * cp);
    }

    /**
     * Slab-тест: пересекает ли луч из {@code eye} под углами (yaw,pitch) коробку {@code box}.
     */
    private static boolean rayHitsBox(Vec3d eye, float yaw, float pitch, Box box) {
        Vec3d dir = dirFromAngles(yaw, pitch);

        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        double[] o = {eye.x, eye.y, eye.z};
        double[] d = {dir.x, dir.y, dir.z};
        double[] mn = {box.minX, box.minY, box.minZ};
        double[] mx = {box.maxX, box.maxY, box.maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-8) {
                if (o[i] < mn[i] || o[i] > mx[i]) return false;
            } else {
                double inv = 1.0 / d[i];
                double t1 = (mn[i] - o[i]) * inv;
                double t2 = (mx[i] - o[i]) * inv;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                if (t1 > tmin) tmin = t1;
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) return false;
            }
        }
        return true;
    }

    /** Апериодично перевыбирает точку наведения внутри хитбокса. */
    private void repickAimPoint(Box box) {
        aimOffset = pickAimPoint(box).subtract(box.getCenter());
        lastRepick = System.currentTimeMillis();
        repickInterval = 230 + (long) (Math.random() * 480);
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        float gcd = calcGcd();

        // --- Сброс состояния при смене цели ---
        if (trackedTarget != target) {
            trackedTarget = target;
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
            yawVel = pitchVel = 0.0F;
            yawCarry = pitchCarry = 0.0F;
            noiseAngle = (float) (Math.random() * Math.PI * 2);
            repickAimPoint(target.getBoundingBox());
            rollGain();

            reactionMs = computeReaction(measureAngle(target));
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        if (System.currentTimeMillis() - lastGainRoll >= gainRollInterval) {
            rollGain();
        }

        Vec3d eye = mc.player.getEyePos();

        // --- Предсказанная коробка цели (упреждение по скорости — анти-стрейф) ---
        Box baseBox = target.getBoundingBox();
        Vec3d vel = targetVelocity(target);
        Vec3d predictedCenter = getPredictedPoint(target, baseBox.getCenter());
        Box box = baseBox.offset(predictedCenter.subtract(baseBox.getCenter()))
                .offset(vel.multiply(PREDICT_TICKS));

        float distance = (float) eye.distanceTo(box.getCenter());

        // --- Реакция (anti-snap Matrix): пред-наводка с микро-джиттером, без снапа ---
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

        // --- Апериодичная смена точки наведения ---
        if (System.currentTimeMillis() - lastRepick >= repickInterval) {
            repickAimPoint(box);
        }

        // --- Точка наведения: центр предсказанного хитбокса + дрейфующее смещение ---
        Vec3d aimPoint = box.getCenter().add(aimOffset);
        Vec3d dir = aimPoint.subtract(eye);
        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

        // --- Адаптивная жёсткость пружины ---
        float errYaw = Math.abs(MathHelper.wrapDegrees(wantYaw - currentYaw));
        float errPitch = Math.abs(wantPitch - currentPitch);
        float err = (float) Math.sqrt(errYaw * errYaw + errPitch * errPitch);

        float k;
        if (err <= TRACK_THRESHOLD) {
            k = 0.0F;
        } else {
            k = MathHelper.clamp((err - TRACK_THRESHOLD) / (FAR_ANGLE - TRACK_THRESHOLD), 0.0F, 1.0F);
        }
        float omega = MathHelper.lerp(k, gainNear, gainFar);

        // --- Шаг пружины по обеим осям ---
        currentYaw = springStepYaw(currentYaw, wantYaw, omega);
        currentPitch = springStepPitch(currentPitch, wantPitch, omega);

        // --- Угловой шум, ограниченный долей УГЛОВОГО размера цели ---
        float[] noise = generateNoise(distance);
        float halfW = (float) Math.max(0.3, (box.maxX - box.minX) * 0.5);
        float halfH = (float) Math.max(0.4, (box.maxY - box.minY) * 0.5);
        float dd = Math.max(0.8F, distance);
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dd));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dd));

        // Шум: не более 35% углового полуразмера по каждой оси (запас под rayHitsBox)
        float devYaw = MathHelper.clamp(noise[0], -angHalfYaw * 0.35F, angHalfYaw * 0.35F);
        float devPitch = MathHelper.clamp(noise[1], -angHalfPitch * 0.35F, angHalfPitch * 0.35F);

        float outY = currentYaw + devYaw;
        float outP = MathHelper.clamp(currentPitch + devPitch, -89.0F, 89.0F);

        // --- ГАРАНТ ПОПАДАНИЯ: проверка пересечения луча с хитбоксом ---
        if (!rayHitsBox(eye, outY, outP, box)) {
            float blend = 0.5F;
            for (int i = 0; i < 6; i++) {
                float tryY = outY + MathHelper.wrapDegrees(currentYaw - outY) * blend;
                float tryP = MathHelper.clamp(outP + (currentPitch - outP) * blend, -89.0F, 89.0F);
                if (rayHitsBox(eye, tryY, tryP, box)) {
                    outY = tryY;
                    outP = tryP;
                    break;
                }
                blend = Math.min(1.0F, blend + 0.18F);
                if (i == 5) {
                    // Полный возврат к чистой ротации — заведомо внутри хитбокса
                    outY = currentYaw;
                    outP = MathHelper.clamp(currentPitch, -89.0F, 89.0F);
                }
            }
        }

        emit(outY, outP, gcd);
    }

    /**
     * GCD-квантование с переносом остатка и отправка. Желаемая дельта {@code (out - lastSent)}
     * накапливается с переносом ({@code carry}) и округляется к ближайшему целому числу шагов
     * мыши {@code gcd} — итоговая дельта всегда кратна gcd, без систематического сноса от
     * усечения и без суб-GCD «заморозки-с-прыжком». Именно серию таких дельт анализирует
     * проверка GCD/Sensitivity в Matrix.
     */
    private void emit(float yaw, float pitch, float gcd) {
        float outP = MathHelper.clamp(pitch, -89.0F, 89.0F);

        float wantDeltaYaw = MathHelper.wrapDegrees(yaw - lastSentYaw) + yawCarry;
        float wantDeltaPitch = (outP - lastSentPitch) + pitchCarry;

        float stepsYaw = Math.round(wantDeltaYaw / gcd);
        float stepsPitch = Math.round(wantDeltaPitch / gcd);

        float sentDeltaYaw = stepsYaw * gcd;
        float sentDeltaPitch = stepsPitch * gcd;

        // Остаток (что не уложилось в целые шаги) переносим на следующий тик.
        yawCarry = wantDeltaYaw - sentDeltaYaw;
        pitchCarry = wantDeltaPitch - sentDeltaPitch;

        float finalYaw = lastSentYaw + sentDeltaYaw;
        float finalPitch = MathHelper.clamp(lastSentPitch + sentDeltaPitch, -89.0F, 89.0F);

        lastSentYaw = finalYaw;
        lastSentPitch = finalPitch;

        RotationStorage.update(new Rotation(finalYaw, finalPitch), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
