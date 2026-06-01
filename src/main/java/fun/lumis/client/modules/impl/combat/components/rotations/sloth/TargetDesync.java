package fun.lumis.client.modules.impl.combat.components.rotations.sloth;

import fun.lumis.api.QClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Система микро-отводов (Target Desynchronization).
 *
 * <p>Периодически и кратковременно уводит вектор направления за пределы основного полигона
 * цели, после чего плавно возвращает его обратно (на одну из точек многоточечного наведения).</p>
 *
 * <ul>
 *   <li><b>Длительность увода:</b> случайное значение 10–200 мс.</li>
 *   <li><b>Направление увода:</b> случайный вектор смещения относительно границ цели —
 *       магнитуда рассчитывается так, чтобы вектор вышел за пределы bounding box.</li>
 *   <li><b>Частота:</b> интервалы между отводами рассчитываются динамически (несоизмеримый
 *       джиттер), чтобы исключить периодичность.</li>
 * </ul>
 *
 * <p>Возвращает накопленное смещение углов (Yaw/Pitch), которое накладывается поверх базовой
 * ротации. В фазе возврата смещение плавно сводится к нулю, и базовая ротация (нацеленная на
 * выбранную точку п.1) восстанавливается.</p>
 */
public class TargetDesync implements QClient {

    private enum Phase {
        IDLE,     // ожидание следующего отвода
        DEVIATE,  // активный увод за пределы цели
        RETURN    // плавный возврат на точку наведения
    }

    private Phase phase = Phase.IDLE;

    private long phaseStart;
    private int deviateDurationMs;   // 10..200 мс
    private int returnDurationMs;    // длительность плавного возврата

    private long lastCycleEnd;
    private long nextIntervalMs;     // динамический интервал до следующего отвода

    // Целевые величины смещения (магнитуда выхода за полигон) и текущие применённые значения.
    private float targetOffYaw, targetOffPitch;
    private float appliedOffYaw, appliedOffPitch;
    private float baseOffYaw, baseOffPitch; // смещение на момент начала возврата

    private boolean justReturned;    // флаг завершённого цикла (для пересбора точки п.1)

    private final float[] out = new float[2];

    public void reset() {
        phase = Phase.IDLE;
        appliedOffYaw = appliedOffPitch = 0f;
        targetOffYaw = targetOffPitch = 0f;
        baseOffYaw = baseOffPitch = 0f;
        lastCycleEnd = System.currentTimeMillis();
        nextIntervalMs = computeNextInterval();
        justReturned = false;
    }

    /**
     * Был ли только что завершён цикл отвода. Сбрасывается при чтении — используется
     * вызывающей стороной, чтобы пересобрать точку многоточечного наведения для возврата.
     */
    public boolean consumeJustReturned() {
        boolean v = justReturned;
        justReturned = false;
        return v;
    }

    public boolean isDeviating() {
        return phase == Phase.DEVIATE;
    }

    /** Динамический, апериодичный интервал между отводами (мс). */
    private long computeNextInterval() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // База + несколько несоизмеримых составляющих + случайный разброс => нет периодичности.
        double t = System.currentTimeMillis() / 1000.0;
        double wobble = Math.sin(t * 0.53) * 140.0 + Math.cos(t * 1.27) * 90.0;
        long base = 420 + (long) (r.nextDouble() * 980.0);
        return Math.max(180L, (long) (base + wobble));
    }

    /**
     * Запускает фазу увода: выбирает случайное направление и магнитуду, выводящую вектор
     * за границы полигона цели исходя из его угловых размеров на текущей дистанции.
     */
    private void beginDeviation(LivingEntity target, float distance) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Box box = target.getBoundingBox();
        double halfW = Math.max(0.3, (box.maxX - box.minX) * 0.5);
        double halfH = Math.max(0.4, (box.maxY - box.minY) * 0.5);

        float dist = Math.max(0.8f, distance);

        // Угловой полуразмер цели (в градусах) на текущей дистанции.
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dist));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dist));

        // ВНУТРИ полигона: 0.10..0.40 от полуразмера. Вектор остаётся НА цели (не за хитбоксом),
        // иначе удар приходится "мимо взгляда" — мгновенный флаг анти-чита.
        float inset = 0.10f + r.nextFloat() * 0.30f;

        // Случайное направление в плоскости (угол) + лёгкая асимметрия по осям.
        double dir = r.nextDouble() * Math.PI * 2.0;
        float yawMag = (float) (Math.cos(dir) * angHalfYaw * inset);
        float pitchMag = (float) (Math.sin(dir) * angHalfPitch * inset);

        // Жёсткий предохранитель: микро-отвод не должен сводить прицел с цели.
        targetOffYaw = MathHelper.clamp(yawMag, -3.0f, 3.0f);
        targetOffPitch = MathHelper.clamp(pitchMag, -2.0f, 2.0f);

        deviateDurationMs = 10 + r.nextInt(191);          // 10..200 мс
        returnDurationMs = 60 + r.nextInt(141);           // 60..200 мс плавный возврат

        baseOffYaw = appliedOffYaw;
        baseOffPitch = appliedOffPitch;

        phase = Phase.DEVIATE;
        phaseStart = System.currentTimeMillis();
    }

    /**
     * Обновляет состояние и возвращает текущее смещение углов {yawOffset, pitchOffset}.
     *
     * @param target   цель
     * @param distance дистанция до цели (метры)
     * @param enabled  активна ли система отводов
     */
    public float[] update(LivingEntity target, float distance, boolean enabled) {
        out[0] = 0f;
        out[1] = 0f;

        if (!enabled || target == null || mc.player == null) {
            // Плавно гасим возможный остаток смещения.
            appliedOffYaw *= 0.6f;
            appliedOffPitch *= 0.6f;
            phase = Phase.IDLE;
            out[0] = appliedOffYaw;
            out[1] = appliedOffPitch;
            return out;
        }

        long now = System.currentTimeMillis();

        switch (phase) {
            case IDLE -> {
                if (now - lastCycleEnd >= nextIntervalMs) {
                    beginDeviation(target, distance);
                }
            }
            case DEVIATE -> {
                float t = MathHelper.clamp((now - phaseStart) / (float) deviateDurationMs, 0f, 1f);
                float curve = easeOutCubic(t);
                appliedOffYaw = MathHelper.lerp(curve, baseOffYaw, targetOffYaw);
                appliedOffPitch = MathHelper.lerp(curve, baseOffPitch, targetOffPitch);

                if (t >= 1f) {
                    phase = Phase.RETURN;
                    phaseStart = now;
                    baseOffYaw = appliedOffYaw;
                    baseOffPitch = appliedOffPitch;
                }
            }
            case RETURN -> {
                float t = MathHelper.clamp((now - phaseStart) / (float) returnDurationMs, 0f, 1f);
                float curve = easeInOut(t);
                // Плавный возврат смещения к нулю -> базовая ротация снова на точке п.1.
                appliedOffYaw = MathHelper.lerp(curve, baseOffYaw, 0f);
                appliedOffPitch = MathHelper.lerp(curve, baseOffPitch, 0f);

                if (t >= 1f) {
                    appliedOffYaw = 0f;
                    appliedOffPitch = 0f;
                    phase = Phase.IDLE;
                    lastCycleEnd = now;
                    nextIntervalMs = computeNextInterval();
                    justReturned = true;
                }
            }
        }

        out[0] = appliedOffYaw;
        out[1] = appliedOffPitch;
        return out;
    }

    /** Плавный выход без "перелёта" — увод не выходит за пределы заданного смещения. */
    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float p = 1f - t;
        return 1f - p * p * p;
    }

    /** Лёгкий "перелёт" за границу для естественного резкого увода. */
    private static float easeOutBack(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = t - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }
}
