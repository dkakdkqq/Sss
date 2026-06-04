package fun.lumis.client.modules.impl.combat.components.rotations.sloth;

import fun.lumis.api.QClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Отвод от хитбокса (Target Desynchronization).
 *
 * <p>Периодически и кратковременно уводит вектор прицела к кромке полигона цели (а при
 * желании — впритык к ней), держит его там короткое время, после чего плавно возвращает
 * на одну из точек многоточечного наведения.</p>
 *
 * <p>Ключевое отличие от наивного «шума в градусах»: смещение выражается в ДОЛЯХ углового
 * полуразмера цели ({@code -1..+1}, где {@code ±1} — ровно кромка хитбокса), а перевод в
 * градусы и финальный гарант «луч всё ещё пересекает коробку» делаются на стороне ротации
 * ({@link fun.lumis.client.modules.impl.combat.components.rotations.SlothRotation}).
 * Поэтому отвод одинаково «доходит до края» на любой дистанции и не зависит от того,
 * насколько мал угловой размер цели.</p>
 *
 * <ul>
 *   <li><b>Магнитуда:</b> до ~0.96 полуразмера (у самой кромки), направление случайное;</li>
 *   <li><b>Фазы:</b> резкий уход (DEVIATE) → удержание у кромки (HOLD) → плавный возврат (RETURN);</li>
 *   <li><b>Тайминги:</b> уход 40–160 мс, удержание 0–90 мс, возврат 70–220 мс;</li>
 *   <li><b>Частота:</b> апериодичный интервал между отводами (несоизмеримый джиттер).</li>
 * </ul>
 */
public class TargetDesync implements QClient {

    private enum Phase {
        IDLE,     // ожидание следующего отвода
        DEVIATE,  // активный уход к кромке хитбокса
        HOLD,     // короткое удержание у кромки
        RETURN    // плавный возврат на точку наведения
    }

    private Phase phase = Phase.IDLE;

    private long phaseStart;
    private int deviateDurationMs;
    private int holdDurationMs;
    private int returnDurationMs;

    private long lastCycleEnd;
    private long nextIntervalMs;

    // Целевая и текущая ДОЛЯ углового полуразмера по каждой оси (-1..+1, ±1 = кромка).
    private float targetFracYaw, targetFracPitch;
    private float appliedFracYaw, appliedFracPitch;
    private float baseFracYaw, baseFracPitch;

    private boolean justReturned;

    private final float[] out = new float[2];

    public void reset() {
        phase = Phase.IDLE;
        appliedFracYaw = appliedFracPitch = 0f;
        targetFracYaw = targetFracPitch = 0f;
        baseFracYaw = baseFracPitch = 0f;
        lastCycleEnd = System.currentTimeMillis();
        nextIntervalMs = computeNextInterval();
        justReturned = false;
    }

    /**
     * Был ли только что завершён цикл отвода. Сбрасывается при чтении — вызывающая сторона
     * по нему пересобирает точку многоточечного наведения для возврата.
     */
    public boolean consumeJustReturned() {
        boolean v = justReturned;
        justReturned = false;
        return v;
    }

    public boolean isDeviating() {
        return phase == Phase.DEVIATE || phase == Phase.HOLD;
    }

    /** Динамический, апериодичный интервал между отводами (мс). */
    private long computeNextInterval() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double t = System.currentTimeMillis() / 1000.0;
        double wobble = Math.sin(t * 0.53) * 150.0 + Math.cos(t * 1.27) * 95.0;
        long base = 380 + (long) (r.nextDouble() * 900.0);
        return Math.max(160L, (long) (base + wobble));
    }

    /**
     * Запускает фазу ухода: выбирает случайное направление в плоскости и магнитуду
     * (в долях полуразмера), выводящую вектор к самой кромке хитбокса.
     */
    private void beginDeviation() {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Случайное направление в плоскости (yaw/pitch) + лёгкая эллиптичность (по pitch меньше).
        double dir = r.nextDouble() * Math.PI * 2.0;

        // До самой кромки: 0.55..0.96 полуразмера. ±1 — ровно грань хитбокса.
        float reach = 0.55f + r.nextFloat() * 0.41f;

        targetFracYaw = MathHelper.clamp((float) (Math.cos(dir) * reach), -1.0f, 1.0f);
        targetFracPitch = MathHelper.clamp((float) (Math.sin(dir) * reach * 0.85f), -1.0f, 1.0f);

        deviateDurationMs = 40 + r.nextInt(121);   // 40..160 мс — резкий, но не телепортный уход
        holdDurationMs = r.nextInt(91);            // 0..90 мс удержание у кромки
        returnDurationMs = 70 + r.nextInt(151);    // 70..220 мс плавный возврат

        baseFracYaw = appliedFracYaw;
        baseFracPitch = appliedFracPitch;

        phase = Phase.DEVIATE;
        phaseStart = System.currentTimeMillis();
    }

    /**
     * Обновляет состояние и возвращает текущее смещение в долях углового полуразмера
     * {@code {fracYaw, fracPitch}} (диапазон −1..+1, где ±1 — кромка хитбокса).
     *
     * @param target  цель (для совместимости/будущих расширений)
     * @param enabled активна ли система отводов
     */
    public float[] update(LivingEntity target, boolean enabled) {
        out[0] = 0f;
        out[1] = 0f;

        if (!enabled || target == null || mc.player == null) {
            // Плавно гасим возможный остаток смещения.
            appliedFracYaw *= 0.6f;
            appliedFracPitch *= 0.6f;
            phase = Phase.IDLE;
            out[0] = appliedFracYaw;
            out[1] = appliedFracPitch;
            return out;
        }

        long now = System.currentTimeMillis();

        switch (phase) {
            case IDLE -> {
                if (now - lastCycleEnd >= nextIntervalMs) {
                    beginDeviation();
                }
            }
            case DEVIATE -> {
                float t = MathHelper.clamp((now - phaseStart) / (float) deviateDurationMs, 0f, 1f);
                float curve = easeOutCubic(t);
                appliedFracYaw = MathHelper.lerp(curve, baseFracYaw, targetFracYaw);
                appliedFracPitch = MathHelper.lerp(curve, baseFracPitch, targetFracPitch);

                if (t >= 1f) {
                    phase = Phase.HOLD;
                    phaseStart = now;
                }
            }
            case HOLD -> {
                appliedFracYaw = targetFracYaw;
                appliedFracPitch = targetFracPitch;
                if (now - phaseStart >= holdDurationMs) {
                    phase = Phase.RETURN;
                    phaseStart = now;
                    baseFracYaw = appliedFracYaw;
                    baseFracPitch = appliedFracPitch;
                }
            }
            case RETURN -> {
                float t = MathHelper.clamp((now - phaseStart) / (float) returnDurationMs, 0f, 1f);
                float curve = easeInOut(t);
                appliedFracYaw = MathHelper.lerp(curve, baseFracYaw, 0f);
                appliedFracPitch = MathHelper.lerp(curve, baseFracPitch, 0f);

                if (t >= 1f) {
                    appliedFracYaw = 0f;
                    appliedFracPitch = 0f;
                    phase = Phase.IDLE;
                    lastCycleEnd = now;
                    nextIntervalMs = computeNextInterval();
                    justReturned = true;
                }
            }
        }

        out[0] = appliedFracYaw;
        out[1] = appliedFracPitch;
        return out;
    }

    /** Плавный выход без «перелёта» — уход не выходит за пределы заданного смещения. */
    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float p = 1f - t;
        return 1f - p * p * p;
    }

    private static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }
}
