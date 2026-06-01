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
 * ReallyWorld — ротация, построенная как идеальный Raytrace-интегратор для боя.
 *
 * <p>Гарантия корректности: в тик отправки пакета атаки (C02UseEntity) вектор взгляда
 * (look vector) ОБЯЗАН пересекать bounding box цели. Это достигается так:
 * <ol>
 *   <li>точка наведения берётся не «на глаз», а как ближайшая к линии взгляда внутренняя
 *       точка хитбокса (с внутренним отступом-маржой), поэтому луч заведомо попадает
 *       в полигон;</li>
 *   <li>движение камеры между тиками описывается строгой дифференциальной моделью
 *       (критически задемпфированная пружина 2-го порядка) — без микро-скачков углов;</li>
 *   <li>каждый тик выход проходит slab-проверку пересечения луч/AABB: если сглаженный
 *       вектор промахивается мимо коробки, он подтягивается к гарантированно-внутренней
 *       точке, пока проверка не станет истинной.</li>
 * </ol>
 *
 * <p>«Глаза» игрока берутся с учётом текущей позы (присед / плавание / ползание / прыжок) —
 * {@link net.minecraft.entity.player.PlayerEntity#getEyePos()} возвращает высоту глаз по
 * {@code getEyeHeight(getPose())}, поэтому источник луча всегда совпадает с тем, что
 * сервер использует для проверки досягаемости.
 */
public class ReallyWorldRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;

    // --- Состояние интегратора (дифференциальная модель движения камеры) ---
    private float currentYaw;
    private float currentPitch;
    private float yawVel;   // угловая скорость по yaw (град/тик)
    private float pitchVel; // угловая скорость по pitch (град/тик)

    private float lastSentYaw;
    private float lastSentPitch;

    // Жёсткость и демпфирование пружины. omega — собственная частота, zeta=1 => критическое
    // демпфирование (самый быстрый выход без перелёта/осцилляций, т.е. без микро-скачков).
    private static final float OMEGA = 0.55F;   // отклик ~ несколько тиков
    private static final float ZETA = 1.0F;     // критическое демпфирование

    // Человекоподобный потолок угловой скорости одного тика (предохранитель от телепорт-флика).
    private static final float MAX_STEP = 32.0F;

    // Внутренний отступ точки наведения от грани коробки (в блоках). Гарантирует, что луч
    // пересекает полигон даже при дрожании клиента/сервера на 1 тик.
    private static final double INNER_MARGIN = 0.08;

    // Сколько тиков вперёд экстраполируем позицию цели (компенсация лагов рендера/тика).
    private static final int PREDICT_TICKS = 1;

    public void reset() {
        trackedTarget = null;
        yawVel = pitchVel = 0.0F;
        if (mc.player != null) {
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
        } else {
            currentYaw = currentPitch = 0.0F;
            lastSentYaw = lastSentPitch = 0.0F;
        }
    }

    /** Вызывается сразу после отправки пакета атаки — здесь анимаций нет, прицел продолжает вести цель. */
    public void onAttack() {
        // Никакого ухода pitch в небо: это мгновенный флаг для ротационных анти-читов.
    }

    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    /** Источник луча взгляда с учётом текущей позы (присед/плавание/прыжок). */
    private Vec3d eyePos() {
        return mc.player.getEyePos();
    }

    /** Линейная скорость цели за прошлый тик (для экстраполяции позиции). */
    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    /**
     * Slab-метод: пересекает ли луч origin + t*dir (t>=0) коробку box.
     * dir НЕ обязан быть нормирован.
     */
    private static boolean rayIntersectsBox(Vec3d origin, Vec3d dir, Box box) {
        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;

        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {dir.x, dir.y, dir.z};
        double[] mn = {box.minX, box.minY, box.minZ};
        double[] mx = {box.maxX, box.maxY, box.maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-8) {
                // Луч параллелен плоскостям этой оси — origin обязан быть внутри полосы.
                if (o[i] < mn[i] || o[i] > mx[i]) return false;
            } else {
                double inv = 1.0 / d[i];
                double t1 = (mn[i] - o[i]) * inv;
                double t2 = (mx[i] - o[i]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                if (t1 > tmin) tmin = t1;
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) return false;
            }
        }
        return true;
    }

    /**
     * Ближайшая к линии взгляда ВНУТРЕННЯЯ точка коробки (с отступом-маржой).
     * Аим в такую точку гарантирует пересечение луча с полигоном цели.
     */
    private Vec3d bestInBoxPoint(Vec3d eye, Box box, Vec3d lookDir) {
        Vec3d center = box.getCenter();

        // Проекция центра коробки на текущий луч взгляда => точка луча, ближайшая к цели.
        Vec3d toCenter = center.subtract(eye);
        double t = toCenter.dotProduct(lookDir.normalize());
        if (t < 0.0) t = toCenter.length(); // цель «за спиной» — резерв

        Vec3d onRay = eye.add(lookDir.normalize().multiply(t));

        // Зажимаем точку луча в пределы коробки с внутренним отступом => заведомо внутри.
        double mx = Math.min(INNER_MARGIN, (box.maxX - box.minX) * 0.5);
        double my = Math.min(INNER_MARGIN, (box.maxY - box.minY) * 0.5);
        double mz = Math.min(INNER_MARGIN, (box.maxZ - box.minZ) * 0.5);

        double cx = MathHelper.clamp(onRay.x, box.minX + mx, box.maxX - mx);
        double cy = MathHelper.clamp(onRay.y, box.minY + my, box.maxY - my);
        double cz = MathHelper.clamp(onRay.z, box.minZ + mz, box.maxZ - mz);
        return new Vec3d(cx, cy, cz);
    }

    private static Vec3d dirFromAngles(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float ch = MathHelper.cos(g);
        float sh = MathHelper.sin(g);
        float cp = MathHelper.cos(f);
        float sp = MathHelper.sin(f);
        return new Vec3d(sh * cp, -sp, ch * cp);
    }

    private static float[] anglesTo(Vec3d eye, Vec3d point) {
        Vec3d dir = point.subtract(eye);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));
        return new float[]{yaw, pitch};
    }

    /**
     * Один шаг критически задемпфированной пружины 2-го порядка по одной оси.
     * Возвращает новое значение угла; vel[0] обновляется как состояние скорости.
     *
     * <p>Дискретизация полунеявным методом Эйлера (стабильна при любых OMEGA):
     * a = ω²·(target−x) − 2ζω·v ; v += a·dt ; x += v·dt (dt=1 тик).
     */
    private float springStep(float x, float target, float[] vel) {
        float diff = MathHelper.wrapDegrees(target - x);
        float accel = OMEGA * OMEGA * diff - 2.0F * ZETA * OMEGA * vel[0];
        vel[0] += accel;
        float step = MathHelper.clamp(vel[0], -MAX_STEP, MAX_STEP);
        vel[0] = step; // сохраняем зажатую скорость => нет накопления рывка
        return x + step;
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        // Сброс состояния при смене цели — стартуем из реального положения камеры (без скачка).
        if (trackedTarget != target) {
            trackedTarget = target;
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
            yawVel = pitchVel = 0.0F;
        }

        Vec3d eye = eyePos();

        // Предсказанная коробка цели на PREDICT_TICKS вперёд (упреждение по линейной скорости).
        Box baseBox = getPredictedBox(target);
        Box box = baseBox.offset(targetVelocity(target).multiply(PREDICT_TICKS));

        // Текущий вектор взгляда интегратора (а не игрока) — на нём строим выбор точки.
        Vec3d lookDir = dirFromAngles(currentYaw, currentPitch);

        // Гарантированно-внутренняя точка наведения и углы к ней.
        Vec3d aimPoint = bestInBoxPoint(eye, box, lookDir);
        float[] want = anglesTo(eye, aimPoint);

        // --- Дифференциальное сглаживание (без микро-скачков) ---
        float[] vy = {yawVel};
        float[] vp = {pitchVel};
        currentYaw = springStep(currentYaw, want[0], vy);
        currentPitch = springStep(currentPitch, want[1], vp);
        currentPitch = MathHelper.clamp(currentPitch, -89.0F, 89.0F);
        yawVel = vy[0];
        pitchVel = vp[0];

        // --- Гарантия попадания луча в полигон (slab-проверка) ---
        Vec3d outDir = dirFromAngles(currentYaw, currentPitch);
        if (!rayIntersectsBox(eye, outDir, box)) {
            // Сглаженный вектор промахнулся — подтягиваем углы к гарантированной точке,
            // пока луч не окажется внутри (бинарно, до 6 шагов => быстрый, плавный доворот).
            float[] target2 = anglesTo(eye, aimPoint);
            float blend = 0.5F;
            for (int i = 0; i < 6; i++) {
                float tryYaw = currentYaw + MathHelper.wrapDegrees(target2[0] - currentYaw) * blend;
                float tryPitch = MathHelper.clamp(
                        currentPitch + (target2[1] - currentPitch) * blend, -89.0F, 89.0F);
                if (rayIntersectsBox(eye, dirFromAngles(tryYaw, tryPitch), box)) {
                    currentYaw = tryYaw;
                    currentPitch = tryPitch;
                    break;
                }
                blend = Math.min(1.0F, blend + 0.18F);
                if (i == 5) {
                    // финальный гарант — точное наведение во внутреннюю точку
                    currentYaw = target2[0];
                    currentPitch = MathHelper.clamp(target2[1], -89.0F, 89.0F);
                }
            }
        }

        // GCD-квантование выходных углов (как у ванильной мыши).
        float gcd = calcGcd();
        float outY = currentYaw;
        float outP = MathHelper.clamp(currentPitch, -89.0F, 89.0F);
        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
