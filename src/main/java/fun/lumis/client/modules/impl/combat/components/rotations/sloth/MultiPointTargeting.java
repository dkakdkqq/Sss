package fun.lumis.client.modules.impl.combat.components.rotations.sloth;

import fun.lumis.api.QClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Многоточечное наведение (Multi-point Targeting).
 *
 * <p>Вместо фиксации на одной статической точке цели формируется массив анатомических
 * точек (голова, верхний/нижний торс, конечности, ноги). Веса точек распределяются по
 * Гауссу вокруг "фокуса", который непрерывно дрейфует во времени, поэтому выбор точек не
 * выглядит цикличным. Для каждой точки рассчитывается дельта углов (Yaw/Pitch) относительно
 * текущего взгляда игрока.</p>
 */
public class MultiPointTargeting implements QClient {

    /**
     * Анатомическая область цели. Координаты заданы в долях от bounding box:
     * yFrac — высота (0 = ноги, 1 = макушка), spreadX/spreadZ — допустимый разброс по
     * горизонтали/глубине, prior — априорный (базовый) вес области.
     */
    public enum Region {
        HEAD(0.93, 0.14, 0.14, 0.80),
        FACE(0.84, 0.18, 0.18, 0.78),
        UPPER_TORSO(0.72, 0.28, 0.26, 1.00),
        MID_TORSO(0.60, 0.32, 0.30, 1.00),
        LOWER_TORSO(0.48, 0.30, 0.28, 0.92),
        LEFT_ARM(0.66, 0.44, 0.20, 0.55),
        RIGHT_ARM(0.66, 0.44, 0.20, 0.55),
        LEFT_LEG(0.26, 0.22, 0.22, 0.48),
        RIGHT_LEG(0.26, 0.22, 0.22, 0.48),
        FEET(0.08, 0.20, 0.20, 0.32);

        public final double yFrac;
        public final double spreadX;
        public final double spreadZ;
        public final double prior;

        Region(double yFrac, double spreadX, double spreadZ, double prior) {
            this.yFrac = yFrac;
            this.spreadX = spreadX;
            this.spreadZ = spreadZ;
            this.prior = prior;
        }
    }

    /** Описание одной целевой точки и рассчитанной для неё дельты углов. */
    public static class AimPoint {
        public final Region region;
        public final Vec3d offset;   // смещение относительно центра bounding box
        public final Vec3d world;    // абсолютная позиция точки
        public final float yaw;
        public final float pitch;
        public final float yawDelta;
        public final float pitchDelta;
        public final float delta;    // суммарная угловая дистанция (гипотенуза)
        public float weight;         // вес по Гауссу (заполняется при распределении)

        AimPoint(Region region, Vec3d offset, Vec3d world,
                 float yaw, float pitch, float yawDelta, float pitchDelta) {
            this.region = region;
            this.offset = offset;
            this.world = world;
            this.yaw = yaw;
            this.pitch = pitch;
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
            this.delta = (float) Math.hypot(yawDelta, pitchDelta);
        }
    }

    private static final Region[] REGIONS = Region.values();

    /** Дрейфующий "фокус" гауссова распределения (в долях высоты). */
    private double focus = 0.7;
    private double focusPhaseA = Math.random() * Math.PI * 2.0;
    private double focusPhaseB = Math.random() * Math.PI * 2.0;
    private double focusPhaseC = Math.random() * Math.PI * 2.0;

    /** Штраф "усталости" по областям, чтобы исключить повторный выбор одной точки. */
    private final double[] fatigue = new double[REGIONS.length];

    private int lastRegionIndex = -1;
    private Region selectedRegion = Region.UPPER_TORSO;
    private Vec3d selectedOffset = new Vec3d(0, 0, 0);

    public void reset() {
        focus = 0.6 + Math.random() * 0.2;
        focusPhaseA = Math.random() * Math.PI * 2.0;
        focusPhaseB = Math.random() * Math.PI * 2.0;
        focusPhaseC = Math.random() * Math.PI * 2.0;
        java.util.Arrays.fill(fatigue, 0.0);
        lastRegionIndex = -1;
        selectedRegion = Region.UPPER_TORSO;
        selectedOffset = new Vec3d(0, 0, 0);
    }

    public Region getSelectedRegion() {
        return selectedRegion;
    }

    /** Текущее выбранное смещение относительно центра bounding box. */
    public Vec3d getSelectedOffset() {
        return selectedOffset;
    }

    /**
     * Дрейф фокуса: суперпозиция трёх несоизмеримых синусоид + лёгкий случайный шум.
     * Несоизмеримые частоты гарантируют апериодичность (нет цикличного повторения).
     */
    private void advanceFocus() {
        double t = System.currentTimeMillis() / 1000.0;
        double wander =
                Math.sin(t * 0.37 + focusPhaseA) * 0.16
                        + Math.sin(t * 0.91 + focusPhaseB) * 0.09
                        + Math.cos(t * 1.63 + focusPhaseC) * 0.05;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03;
        focus = MathHelper.clamp(0.62 + wander + noise, 0.30, 0.92);
    }

    /**
     * Рассчитывает массив целевых точек для цели и распределяет их веса по Гауссу.
     *
     * @param target цель
     * @param sigma  ширина гауссова окна (доли высоты); меньше — острее фокус
     * @return список точек с заполненными весами и дельтами углов
     */
    public List<AimPoint> computePoints(LivingEntity target, double sigma) {
        List<AimPoint> points = new ArrayList<>(REGIONS.length);
        if (mc.player == null || target == null) {
            return points;
        }

        advanceFocus();

        Box box = target.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        Vec3d center = box.getCenter();
        Vec3d eyes = mc.player.getEyePos();

        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();

        double inv2Sigma2 = 1.0 / (2.0 * sigma * sigma);

        for (int i = 0; i < REGIONS.length; i++) {
            Region region = REGIONS[i];

            // Лёгкий апериодичный сдвиг внутри области (несоизмеримые частоты + id сущности).
            double seed = System.currentTimeMillis() / 70.0 + target.getId() * 1.7 + i * 2.3;
            double lateral = Math.sin(seed * 0.83) * region.spreadX;
            double depthOff = Math.cos(seed * 1.27) * region.spreadZ;
            double sideBias = switch (region) {
                case LEFT_ARM, LEFT_LEG -> -0.55;
                case RIGHT_ARM, RIGHT_LEG -> 0.55;
                default -> 0.0;
            };

            // Удерживаем точку ВНУТРИ хитбокса (не у самой кромки): после добавления шума и
            // микро-отвода прицел иначе уходит за полигон цели — это банит ротационный анти-чит.
            double lateralFactor = MathHelper.clamp(lateral + sideBias, -0.5, 0.5);
            double depthFactor = MathHelper.clamp(depthOff, -0.5, 0.5);

            double ox = lateralFactor * (width * 0.5);
            double oy = (region.yFrac - 0.5) * height * 0.85;
            double oz = depthFactor * (depth * 0.5);

            Vec3d offset = new Vec3d(ox, oy, oz);
            Vec3d world = center.add(offset);

            Vec3d dir = world.subtract(eyes);
            float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

            float yawDelta = MathHelper.wrapDegrees(yaw - playerYaw);
            float pitchDelta = pitch - playerPitch;

            AimPoint point = new AimPoint(region, offset, world, yaw, pitch, yawDelta, pitchDelta);

            // Гауссов вес вокруг дрейфующего фокуса по высоте.
            double dy = region.yFrac - focus;
            double gauss = Math.exp(-(dy * dy) * inv2Sigma2);

            // Априорная заметность области и штраф усталости.
            double w = gauss * region.prior * (1.0 - fatigue[i]);

            // Мягкое предпочтение более близких по углу точек (без линейной зависимости).
            double proximity = 1.0 / (1.0 + Math.sqrt(point.delta) * 0.18);
            w *= 0.55 + 0.45 * proximity;

            point.weight = (float) Math.max(w, 1.0e-4);
            points.add(point);
        }

        return points;
    }

    /**
     * Выбирает точку по весам (рулетка), смещает фокус выбора и обновляет усталость,
     * чтобы избежать цикличного повторения. Возвращает выбранную точку.
     */
    public AimPoint selectPoint(List<AimPoint> points) {
        if (points.isEmpty()) {
            return null;
        }

        double total = 0.0;
        for (AimPoint p : points) {
            total += p.weight;
        }

        int chosen = 0;
        if (total > 0.0) {
            double r = ThreadLocalRandom.current().nextDouble() * total;
            double acc = 0.0;
            for (int i = 0; i < points.size(); i++) {
                acc += points.get(i).weight;
                if (r <= acc) {
                    chosen = i;
                    break;
                }
            }
        }

        AimPoint point = points.get(chosen);
        int regionIndex = point.region.ordinal();

        // Усталость: только что выбранная область временно "глушится" и медленно восстанавливается.
        for (int i = 0; i < fatigue.length; i++) {
            fatigue[i] *= 0.82;
        }
        fatigue[regionIndex] = MathHelper.clamp((float) (fatigue[regionIndex] + 0.6), 0.0f, 0.92f);

        lastRegionIndex = regionIndex;
        selectedRegion = point.region;
        selectedOffset = point.offset;
        return point;
    }

    /**
     * Принудительно пересобирает и выбирает новую точку (используется при возврате после
     * микро-отвода, чтобы вектор вернулся на одну из точек многоточечного наведения).
     */
    public AimPoint reselect(LivingEntity target, double sigma) {
        // Усилим штраф для текущей области, чтобы возврат пришёлся на другую точку.
        if (lastRegionIndex >= 0) {
            fatigue[lastRegionIndex] = MathHelper.clamp((float) (fatigue[lastRegionIndex] + 0.35), 0.0f, 0.95f);
        }
        return selectPoint(computePoints(target, sigma));
    }
}
