package fun.lumis.client.modules.impl.combat.components.rotations.sloth;

import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Динамическая ротация без привязки к фиксированной скорости.
 *
 * <p>Угловая скорость не является константой и не зависит линейно от расстояния до цели.
 * Каждый "разворот" (сегмент) описывается случайным сплайном Безье (кубическая кривая со
 * случайными контрольными точками), а прогресс вдоль сегмента продвигается с переменным
 * шагом и плавным затуханием. На выходе — доля оставшейся дельты, которую нужно пройти за
 * текущий тик, благодаря чему траектория выглядит естественно даже при экстремально высоких
 * скоростях разворота.</p>
 */
public class DynamicRotationCurve {

    // Контрольные точки кубической кривой Безье. P0 = (0,0), P3 = (1,1).
    private double c1x, c1y, c2x, c2y;

    private float progress;     // 0..1 — положение вдоль текущего сегмента разворота
    private float lastEased;    // последнее значение кривой (для расчёта приращения)
    private float incBase;      // базовое приращение прогресса за тик
    private float decay;        // коэффициент плавного затухания скорости к концу
    private boolean active;

    public void reset() {
        active = false;
        progress = 0f;
        lastEased = 0f;
        randomizeShape(45f);
    }

    public boolean isActive() {
        return active;
    }

    public float getProgress() {
        return progress;
    }

    /** Генерирует случайную форму кривой и параметры сегмента под заданную суммарную дельту. */
    private void randomizeShape(float totalDeltaDeg) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Случайные контрольные точки: первая управляет "разгоном", вторая — "торможением".
        // Иногда — лёгкий S-образный профиль, иногда — резкий старт с плавным завершением.
        c1x = r.nextDouble(0.05, 0.42);
        c1y = r.nextDouble(0.00, 0.40);
        c2x = r.nextDouble(0.58, 0.96);
        c2y = r.nextDouble(0.62, 1.00);

        // Нелинейная (sqrt) зависимость скорости от величины разворота: чем больше дельта,
        // тем больше базовый шаг, но рост сублинейный — не пропорционально расстоянию.
        float norm = MathHelper.clamp(totalDeltaDeg / 130f, 0.015f, 1.0f);
        incBase = 0.055f + (float) Math.sqrt(norm) * 0.135f + (float) r.nextDouble(0.0, 0.05);

        // Затухание: 0.55..0.95 — насколько сильно скорость падает к завершению сегмента.
        decay = 0.55f + (float) r.nextDouble(0.0, 0.40);
    }

    /** Начинает новый сегмент разворота под суммарную угловую дельту (в градусах). */
    public void newSegment(float totalDeltaDeg) {
        randomizeShape(totalDeltaDeg);
        progress = 0f;
        lastEased = 0f;
        active = true;
    }

    /** Значение кубической кривой Безье по параметру t (используем t как приближение по X). */
    private float bezier(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        double u = 1.0 - t;
        // y(t) = 3u²t·P1y + 3ut²·P2y + t³ (P0y = 0, P3y = 1)
        double y = 3.0 * u * u * t * c1y + 3.0 * u * t * t * c2y + t * t * t;
        return (float) MathHelper.clamp(y, 0.0, 1.0);
    }

    /**
     * Продвигает прогресс по сегменту и возвращает долю ОСТАВШЕЙСЯ дельты, которую следует
     * пройти в этом тике. Шаг прогресса переменный (джиттер) и затухает к концу разворота,
     * поэтому угловая скорость не фиксирована.
     *
     * @param remainingDeltaDeg текущая оставшаяся угловая дистанция до цели (в градусах)
     * @return доля [0..1] оставшейся дельты на этот тик
     */
    public float step(float remainingDeltaDeg) {
        if (!active) {
            newSegment(remainingDeltaDeg);
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Джиттер скорости в пределах сегмента — траектория не "машинная".
        float jitter = 0.72f + r.nextFloat() * 0.62f;

        // Плавное затухание: ближе к концу сегмента шаг меньше (естественное "доведение").
        float fade = 1.0f - decay * smoothStep(progress);

        float inc = incBase * jitter * Math.max(0.18f, fade);
        progress = Math.min(1.0f, progress + inc);

        float eased = bezier(progress);
        float denom = Math.max(1.0e-3f, 1.0f - lastEased);
        float frac = (eased - lastEased) / denom;
        lastEased = eased;

        if (progress >= 1.0f) {
            active = false;
        }

        return MathHelper.clamp(frac, 0f, 1f);
    }

    /**
     * Режим непрерывного сопровождения (цель почти на прицеле): небольшой случайный коэффициент
     * без жёсткой константы. Сбрасывает сегмент, чтобы следующий крупный разворот стартовал заново.
     */
    public float track() {
        if (active) {
            active = false;
            progress = 0f;
            lastEased = 0f;
        }
        return 0.32f + ThreadLocalRandom.current().nextFloat() * 0.34f;
    }

    private static float smoothStep(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
