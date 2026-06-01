package fun.lumis.api.storages.implement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.NeuroPattern;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.client.modules.impl.combat.components.gcd.GCDUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CopyOnWriteArrayList;

public class NeuroAuraStorage implements QClient {

    private static final long MIN_RECORD_INTERVAL = 50L;
    private static final int MAX_FRAMES = 20000;
    private static final String PATTERNS_DIRECTORY = "data_patterns";
    private static final String LEGACY_PATTERNS_DIRECTORY = "neuro_patterns";
    private static final String PRIMARY_EXTENSION = ".data";
    private static final String LEGACY_EXTENSION = ".neuro";
    private static final float SYNC_SCORE_THRESHOLD = 45.0f;
    private static final float MAX_YAW_CORRECTION = 8.0f;
    private static final float MAX_PITCH_CORRECTION = 6.0f;

    @Getter
    private final List<NeuroPattern> recordedPatterns = new CopyOnWriteArrayList<>();
    @Getter
    @Setter
    private boolean isRecording = false;
    @Getter
    @Setter
    private boolean isUsingNeuro = false;
    @Getter
    @Setter
    private boolean showStats = true;
    @Getter
    @Setter
    private String currentPatternName = null;
    @Getter
    private String lastDebugMessage = "Готов!";
    @Getter
    private int recordedThisSession = 0;

    private long lastRecordTime = 0L;
    private float prevRecordYaw = 0f;
    private float prevRecordPitch = 0f;
    private boolean hasRecordedBefore = false;

    private final List<Frame> frames = new CopyOnWriteArrayList<>();
    private int playbackIndex = -1;
    private int ticksSinceSync = 0;
    private float smoothedYawDelta = 0.0f;
    private float smoothedPitchDelta = 0.0f;
    private float smoothedOutputYaw = Float.NaN;
    private float smoothedOutputPitch = Float.NaN;
    private float yawSpeedFactor = 1.0f;
    private float pitchSpeedFactor = 1.0f;
    private int speedProfileTicks = 0;
    private Vec3d currentAimPoint = null;
    private Vec3d targetRandomPoint = null;
    private int aimPointTicks = 0;
    private LivingEntity lastAimTarget = null;
    private boolean lastWasIdle = true;
    private int attackCount = 0;
    private float randomXOffset = 0f;
    private float randomYRatio = 0.66f;
    private float randomZOffset = 0f;

    public NeuroAuraStorage() {
        createPatternsDirectory();
    }

    private void createPatternsDirectory() {
        try {
            Path path = Paths.get(PATTERNS_DIRECTORY);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            lastDebugMessage = "§cОшибка папки";
        }
    }

    public void recordTick(LivingEntity target, float currentYaw, float currentPitch) {
        if (!isRecording || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRecordTime < MIN_RECORD_INTERVAL) {
            return;
        }

        float deltaYaw = 0f;
        float deltaPitch = 0f;

        if (hasRecordedBefore) {
            deltaYaw = MathHelper.wrapDegrees(currentYaw - prevRecordYaw);
            deltaPitch = currentPitch - prevRecordPitch;
        }

        float angleYaw = 0f;
        float anglePitch = 0f;
        double distance = 0.0;
        boolean hasTarget = target != null;

        if (hasTarget) {
            AimData aimData = getAimData(target, currentYaw, currentPitch, null, true);
            angleYaw = aimData.angleYaw;
            anglePitch = aimData.anglePitch;
            distance = aimData.distance;
        }

        Frame frame = new Frame();
        frame.deltaYaw = deltaYaw;
        frame.deltaPitch = deltaPitch;
        frame.angleYaw = angleYaw;
        frame.anglePitch = anglePitch;
        frame.distance = distance;
        frame.hasTarget = hasTarget;
        frame.smoothness = calculateSmoothness(deltaYaw, deltaPitch);

        frames.add(frame);
        while (frames.size() > MAX_FRAMES) {
            frames.remove(0);
        }

        if (hasTarget) {
            boolean crit = mc.player.fallDistance > 0 && !mc.player.isOnGround();
            String type = target instanceof PlayerEntity ? "player" : "mob";
            recordedPatterns.add(new NeuroPattern(
                    angleYaw, anglePitch, deltaYaw, deltaPitch,
                    distance, crit, 0, type, frame.smoothness
            ));
            while (recordedPatterns.size() > MAX_FRAMES) {
                recordedPatterns.remove(0);
            }
        }

        prevRecordYaw = currentYaw;
        prevRecordPitch = currentPitch;
        hasRecordedBefore = true;
        lastRecordTime = now;
        recordedThisSession++;

        if (recordedThisSession % 20 == 0) {
            lastDebugMessage = "§aЗапись: §f" + frames.size();
        }
    }

    public Rotation getNeuroRotation(LivingEntity target, float currentYaw, float currentPitch, boolean idle) {
        if (!isUsingNeuro || target == null || mc.player == null || frames.isEmpty()) {
            resetState();
            return null;
        }

        if (!idle && lastWasIdle) {
            rollNewRandomPoint();
            attackCount++;
        }
        lastWasIdle = idle;

        boolean needSync = playbackIndex < 0 || playbackIndex >= frames.size();
        AimData aimData = getAimData(target, currentYaw, currentPitch, null, idle);
        boolean airborne = !mc.player.isOnGround() || mc.player.getVelocity().y != 0.0;

        if (Math.abs(aimData.angleYaw) > 110.0f) {
            needSync = true;
            smoothedYawDelta = 0.0f;
            smoothedPitchDelta = 0.0f;
            smoothedOutputYaw = currentYaw;
            smoothedOutputPitch = currentPitch;
        }

        if (!needSync && ticksSinceSync >= 5) {
            Frame currentFrame = frames.get(playbackIndex);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(currentFrame.angleYaw - aimData.angleYaw));
            float pitchDiff = Math.abs(currentFrame.anglePitch - aimData.anglePitch);
            float distDiff = (float) Math.abs(currentFrame.distance - aimData.distance);
            if (yawDiff + pitchDiff + distDiff * 0.3f > SYNC_SCORE_THRESHOLD) {
                needSync = true;
            }
        }

        if (needSync) {
            playbackIndex = findBest(aimData.angleYaw, aimData.anglePitch, aimData.distance);
            ticksSinceSync = 0;
        }

        Frame frame = frames.get(playbackIndex);
        aimData = getAimData(target, currentYaw, currentPitch, frame, idle);

        float applyYaw = frame.deltaYaw;
        float applyPitch = frame.deltaPitch;
        updateSpeedProfile(idle, airborne, aimData);

        if (Math.abs(frame.angleYaw) > 3f && Math.abs(aimData.angleYaw) > 3f && Math.signum(frame.angleYaw) != Math.signum(aimData.angleYaw)) {
            applyYaw = -applyYaw;
        }

        if (Math.abs(frame.anglePitch) > 3f && Math.abs(aimData.anglePitch) > 3f && Math.signum(frame.anglePitch) != Math.signum(aimData.anglePitch)) {
            applyPitch = -applyPitch;
        }

        applyYaw = adaptRecordedDelta(applyYaw, aimData.angleYaw, frame.smoothness, idle, MAX_YAW_CORRECTION);
        applyPitch = adaptRecordedDelta(applyPitch, aimData.anglePitch, frame.smoothness, idle, MAX_PITCH_CORRECTION);

        if (Math.abs(aimData.angleYaw) < 32.0f) {
            applyYaw = MathHelper.lerp(0.58f, applyYaw, aimData.angleYaw);
        }

        if (Math.abs(aimData.anglePitch) < 24.0f) {
            applyPitch = MathHelper.lerp(0.52f, applyPitch, aimData.anglePitch);
        }

        smoothedYawDelta = smoothDelta(smoothedYawDelta, applyYaw, frame.smoothness);
        smoothedPitchDelta = smoothDelta(smoothedPitchDelta, applyPitch, frame.smoothness);

        float quantizedYaw = quantizeToMouseStep(smoothedYawDelta, aimData.angleYaw);
        float quantizedPitch = quantizeToMouseStep(smoothedPitchDelta, aimData.anglePitch);
        quantizedYaw += getMicroJitter(true, idle, airborne, aimData);
        quantizedPitch += getMicroJitter(false, idle, airborne, aimData);

        float rawYaw = MathHelper.wrapDegrees(currentYaw + quantizedYaw);
        float rawPitch = MathHelper.clamp(currentPitch + quantizedPitch, -90f, 90f);
        float finalYaw = smoothOutputRotation(rawYaw, currentYaw, frame.smoothness, idle, true);
        float finalPitch = smoothOutputRotation(rawPitch, currentPitch, frame.smoothness, idle, false);

        playbackIndex++;
        ticksSinceSync++;

        int skipped = 0;
        while (playbackIndex < frames.size() && !frames.get(playbackIndex).hasTarget && skipped < 5) {
            playbackIndex++;
            skipped++;
        }

        if (playbackIndex >= frames.size()) {
            float newAngleYaw = MathHelper.wrapDegrees(aimData.perfectYaw - finalYaw);
            float newAnglePitch = aimData.perfectPitch - finalPitch;
            playbackIndex = findBest(newAngleYaw, newAnglePitch, aimData.distance);
            ticksSinceSync = 0;
        }

        lastDebugMessage = String.format("§a[%d/%d] dY%.2f dP%.2f", playbackIndex, frames.size(), quantizedYaw, quantizedPitch);
        return new Rotation(finalYaw, finalPitch);
    }

    private void rollNewRandomPoint() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        randomXOffset = r.nextFloat(-0.38f, 0.38f);
        randomYRatio = r.nextFloat(0.40f, 0.85f);
        randomZOffset = r.nextFloat(-0.38f, 0.38f);
    }

    private AimData getAimData(LivingEntity target, float currentYaw, float currentPitch, Frame frame, boolean relaxed) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d point = selectAimPoint(target, relaxed);
        double distance = eyePos.distanceTo(point);

        double dx = point.x - eyePos.x;
        double dy = point.y - eyePos.y;
        double dz = point.z - eyePos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float perfectYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float perfectPitch = (float) Math.toDegrees(Math.atan2(-dy, distXZ));

        AimData aimData = new AimData();
        aimData.targetPoint = point;
        aimData.distance = distance;
        aimData.perfectYaw = perfectYaw;
        aimData.perfectPitch = perfectPitch;
        aimData.angleYaw = MathHelper.wrapDegrees(perfectYaw - currentYaw);
        aimData.anglePitch = perfectPitch - currentPitch;
        return aimData;
    }

    private float adaptRecordedDelta(float recordedDelta, float currentAngle, float smoothness, boolean idle, float maxCorrection) {
        float correctionWeight = idle ? 0.14f : 0.045f;
        float correctionLimit = idle ? maxCorrection * 0.65f : maxCorrection * 0.30f;
        float correction = MathHelper.clamp(currentAngle - recordedDelta, -correctionLimit, correctionLimit);
        float result = recordedDelta + correction * correctionWeight;

        if (Math.abs(currentAngle) < Math.abs(result) && Math.signum(currentAngle) == Math.signum(result)) {
            result = currentAngle;
        }

        float preserveFactor = idle
                ? MathHelper.clamp(1.0f - smoothness * 0.22f, 0.80f, 0.97f)
                : MathHelper.clamp(1.0f - smoothness * 0.10f, 0.91f, 0.99f);
        result *= preserveFactor;

        if (Math.abs(currentAngle) <= GCDUtil.getGCDValue()) {
            return currentAngle;
        }

        return result;
    }

    private Vec3d selectAimPoint(LivingEntity target, boolean relaxed) {
        if (target != lastAimTarget) {
            lastAimTarget = target;
            currentAimPoint = null;
            targetRandomPoint = null;
            aimPointTicks = 0;
            rollNewRandomPoint();
        }

        Box box = target.getBoundingBox();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d stablePoint = new Vec3d(
                box.getCenter().x,
                box.minY + box.getLengthY() * 0.72,
                box.getCenter().z
        );
        if (box.expand(0.12).contains(eyePos) || eyePos.squaredDistanceTo(stablePoint) <= 2.25) {
            currentAimPoint = stablePoint;
            targetRandomPoint = stablePoint;
            aimPointTicks = 0;
            return stablePoint;
        }

        double xCenter = (box.minX + box.maxX) * 0.5;
        double zCenter = (box.minZ + box.maxZ) * 0.5;
        double halfW = box.getLengthX() * 0.5;
        double halfD = box.getLengthZ() * 0.5;
        double height = box.getLengthY();

        Vec3d desired = new Vec3d(
                xCenter + halfW * randomXOffset,
                box.minY + height * randomYRatio,
                zCenter + halfD * randomZOffset
        );

        if (targetRandomPoint == null) {
            targetRandomPoint = desired;
        } else {
            float driftLerp = relaxed ? 0.13f : 0.07f;
            targetRandomPoint = new Vec3d(
                    MathHelper.lerp(driftLerp, targetRandomPoint.x, desired.x),
                    MathHelper.lerp(driftLerp, targetRandomPoint.y, desired.y),
                    MathHelper.lerp(driftLerp, targetRandomPoint.z, desired.z)
            );
        }

        if (currentAimPoint == null) {
            currentAimPoint = targetRandomPoint;
            aimPointTicks = 0;
            return currentAimPoint;
        }

        float pointLerp = relaxed ? 0.11f : 0.055f;
        currentAimPoint = new Vec3d(
                MathHelper.lerp(pointLerp, currentAimPoint.x, targetRandomPoint.x),
                MathHelper.lerp(pointLerp, currentAimPoint.y, targetRandomPoint.y),
                MathHelper.lerp(pointLerp, currentAimPoint.z, targetRandomPoint.z)
        );
        aimPointTicks++;
        return currentAimPoint;
    }

    private float smoothDelta(float current, float target, float smoothness) {
        float lerpFactor = MathHelper.clamp(0.035f + (1.0f - smoothness) * 0.12f, 0.035f, 0.15f);
        return current + (target - current) * lerpFactor;
    }

    private float smoothOutputRotation(float targetRotation, float currentRotation, float smoothness, boolean idle, boolean yawAxis) {
        float previous = yawAxis ? smoothedOutputYaw : smoothedOutputPitch;
        if (Float.isNaN(previous)) {
            previous = currentRotation;
        }

        float delta = yawAxis
                ? MathHelper.wrapDegrees(targetRotation - previous)
                : targetRotation - previous;

        float maxStep = yawAxis
                ? (idle ? 1.68f : 0.86f)
                : (idle ? 1.26f : 0.62f);
        float lerpFactor = idle
                ? MathHelper.clamp(0.08f + (1.0f - smoothness) * 0.09f, 0.08f, 0.17f)
                : MathHelper.clamp(0.04f + (1.0f - smoothness) * 0.055f, 0.04f, 0.095f);

        maxStep *= yawAxis ? yawSpeedFactor : pitchSpeedFactor;
        lerpFactor *= yawAxis ? yawSpeedFactor : pitchSpeedFactor;

        float smoothed = previous + MathHelper.clamp(delta * lerpFactor, -maxStep, maxStep);
        if (yawAxis) {
            smoothed = MathHelper.wrapDegrees(smoothed);
            smoothedOutputYaw = smoothed;
        } else {
            smoothed = MathHelper.clamp(smoothed, -90f, 90f);
            smoothedOutputPitch = smoothed;
        }
        return smoothed;
    }

    private void updateSpeedProfile(boolean idle, boolean airborne, AimData aimData) {
        if (speedProfileTicks > 0) {
            speedProfileTicks--;
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float anglePressure = MathHelper.clamp((Math.abs(aimData.angleYaw) + Math.abs(aimData.anglePitch)) / 35.0f, 0.0f, 1.0f);
        float baseYawMin = idle ? 1.06f : 0.96f;
        float baseYawMax = idle ? 1.34f : 1.12f;
        float basePitchMin = idle ? 1.00f : 0.90f;
        float basePitchMax = idle ? 1.24f : 1.05f;

        yawSpeedFactor = random.nextFloat(baseYawMin, baseYawMax + anglePressure * (idle ? 0.10f : 0.16f));
        pitchSpeedFactor = random.nextFloat(basePitchMin, basePitchMax + anglePressure * (idle ? 0.08f : 0.12f));

        if (!idle && anglePressure > 0.58f) {
            yawSpeedFactor = Math.max(yawSpeedFactor, 1.08f + anglePressure * 0.24f);
            pitchSpeedFactor = Math.max(pitchSpeedFactor, 1.00f + anglePressure * 0.18f);
        }

        if (airborne) {
            yawSpeedFactor *= 0.97f;
            pitchSpeedFactor *= 0.95f;
        }

        speedProfileTicks = random.nextInt(idle ? 3 : 2, idle ? 7 : 5);
    }

    private float getMicroJitter(boolean yawAxis, boolean idle, boolean airborne, AimData aimData) {
        float gcd = GCDUtil.getGCDValue();
        if (gcd <= 0.0f) {
            return 0.0f;
        }

        float pressure = Math.abs(yawAxis ? aimData.angleYaw : aimData.anglePitch);
        if (!idle && pressure > (yawAxis ? 10.0f : 7.0f)) {
            return 0.0f;
        }

        float amplitude = yawAxis ? gcd * 0.018f : gcd * 0.012f;
        if (airborne) {
            amplitude *= 0.35f;
        }
        float wave = (float) Math.sin((mc.player.age + (yawAxis ? 0.0f : 7.0f)) * (idle ? 0.42f : 0.28f));
        return wave * amplitude;
    }

    private float quantizeToMouseStep(float delta, float remainingAngle) {
        float gcd = GCDUtil.getGCDValue();
        if (gcd <= 0.0f) {
            return delta;
        }

        float limited = delta;
        if (Math.abs(remainingAngle) < Math.abs(limited) && Math.signum(remainingAngle) == Math.signum(limited)) {
            limited = remainingAngle;
        }

        float quantized = Math.round(limited / gcd) * gcd;
        if (quantized == 0.0f && Math.abs(remainingAngle) >= gcd * 0.35f && Math.abs(limited) > 0.001f) {
            quantized = Math.signum(limited) * gcd;
        }

        if (Math.abs(remainingAngle) < Math.abs(quantized) && Math.signum(remainingAngle) == Math.signum(quantized)) {
            quantized = remainingAngle;
        }

        return quantized;
    }

    private float calculateSmoothness(float deltaYaw, float deltaPitch) {
        float magnitude = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        float base = 1.0f - magnitude / 18.0f;
        float periodic = (float) Math.sin((recordedThisSession + mc.player.age * 0.31f) * 0.34f) * 0.012f;
        float noise = ThreadLocalRandom.current().nextFloat(-0.008f, 0.008f);
        return MathHelper.clamp(base + periodic + noise, 0.22f, 0.88f);
    }

    private int findBest(float angleYaw, float anglePitch, double distance) {
        int best = 0;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (!frame.hasTarget) {
                continue;
            }

            float yawDiff = Math.abs(MathHelper.wrapDegrees(frame.angleYaw - angleYaw));
            float pitchDiff = Math.abs(frame.anglePitch - anglePitch);
            float distanceDiff = (float) Math.abs(frame.distance - distance);
            float score = yawDiff + pitchDiff + distanceDiff * 0.3f;

            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }

        return best;
    }

    public boolean savePatterns(String profileName) {
        if (frames.isEmpty()) {
            lastDebugMessage = "§cНет записей";
            return false;
        }

        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(PATTERNS_DIRECTORY + "/" + profileName + PRIMARY_EXTENSION))) {
            SaveData data = new SaveData();
            data.patterns = new ArrayList<>(recordedPatterns);
            data.frames = new ArrayList<>(frames);
            out.writeObject(data);
            currentPatternName = profileName;
            lastDebugMessage = "§aСохранено " + frames.size();
            return true;
        } catch (IOException e) {
            lastDebugMessage = "§cОшибка сохранения";
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean loadPatterns(String profileName) {
        File file = resolveProfileFile(profileName);
        if (!file.exists()) {
            lastDebugMessage = "§eНе найдено: " + profileName;
            return false;
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = in.readObject();
            recordedPatterns.clear();
            frames.clear();

            if (obj instanceof SaveData data) {
                if (data.patterns != null) {
                    recordedPatterns.addAll(data.patterns);
                }
                if (data.frames != null) {
                    frames.addAll(data.frames);
                }
            } else if (obj instanceof List<?> list) {
                recordedPatterns.addAll((List<NeuroPattern>) list);
            }

            if (frames.isEmpty() && !recordedPatterns.isEmpty()) {
                rebuildFramesFromPatterns();
            }

            currentPatternName = profileName;
            resetState();
            lastDebugMessage = "§aЗагружено " + frames.size();
            return !frames.isEmpty();
        } catch (IOException | ClassNotFoundException e) {
            lastDebugMessage = "§cОшибка загрузки";
            return false;
        }
    }

    private void rebuildFramesFromPatterns() {
        for (NeuroPattern pattern : recordedPatterns) {
            Frame frame = new Frame();
            frame.deltaYaw = pattern.getDeltaYaw();
            frame.deltaPitch = pattern.getDeltaPitch();
            frame.angleYaw = pattern.getYaw();
            frame.anglePitch = pattern.getPitch();
            frame.distance = pattern.getDistance();
            frame.hasTarget = true;
            frame.smoothness = MathHelper.clamp(pattern.getSmoothness(), 0.18f, 0.9f);
            frames.add(frame);
        }
    }

    public boolean deletePatterns(String profileName) {
        File primaryFile = new File(PATTERNS_DIRECTORY + "/" + profileName + PRIMARY_EXTENSION);
        File legacyFile = new File(LEGACY_PATTERNS_DIRECTORY + "/" + profileName + LEGACY_EXTENSION);
        boolean deleted = false;

        if (primaryFile.exists()) {
            deleted = primaryFile.delete();
        }
        if (legacyFile.exists()) {
            deleted = legacyFile.delete() || deleted;
        }

        if (deleted) {
            if (profileName.equals(currentPatternName)) {
                currentPatternName = null;
            }
            lastDebugMessage = "§aУдалено";
            return true;
        }

        return false;
    }

    public int getPatternCount() {
        return recordedPatterns.size();
    }

    public int getFrameCount() {
        return frames.size();
    }

    public void startRecording() {
        recordedPatterns.clear();
        frames.clear();
        isRecording = true;
        isUsingNeuro = false;
        recordedThisSession = 0;
        lastRecordTime = 0L;
        currentPatternName = null;
        hasRecordedBefore = false;
        prevRecordYaw = 0f;
        prevRecordPitch = 0f;
        resetState();
        lastDebugMessage = "§aЗапись";
    }

    public void stopRecording() {
        isRecording = false;
        lastDebugMessage = "§eСтоп: " + frames.size();
    }

    public void clearPatterns() {
        recordedPatterns.clear();
        frames.clear();
        isRecording = false;
        isUsingNeuro = false;
        recordedThisSession = 0;
        currentPatternName = null;
        hasRecordedBefore = false;
        prevRecordYaw = 0f;
        prevRecordPitch = 0f;
        resetState();
        lastDebugMessage = "§eОчищено";
    }

    public void resetState() {
        playbackIndex = -1;
        ticksSinceSync = 0;
        smoothedYawDelta = 0.0f;
        smoothedPitchDelta = 0.0f;
        smoothedOutputYaw = Float.NaN;
        smoothedOutputPitch = Float.NaN;
        yawSpeedFactor = 1.0f;
        pitchSpeedFactor = 1.0f;
        speedProfileTicks = 0;
        currentAimPoint = null;
        targetRandomPoint = null;
        aimPointTicks = 0;
        lastAimTarget = null;
        lastWasIdle = true;
        attackCount = 0;
        rollNewRandomPoint();
    }

    public String getStatusString() {
        String status = "§8[§bData§8] §f" + frames.size();
        if (isRecording) {
            status += " §a[REC]";
        }
        if (isUsingNeuro) {
            status += " §b[ON " + playbackIndex + "]";
        }
        return status;
    }

    public List<String> getPatternNames() {
        List<String> names = new ArrayList<>();
        collectPatternNames(names, new File(PATTERNS_DIRECTORY), PRIMARY_EXTENSION);
        collectPatternNames(names, new File(LEGACY_PATTERNS_DIRECTORY), LEGACY_EXTENSION);
        return names;
    }

    private void collectPatternNames(List<String> names, File directory, String extension) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(extension));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName().replace(extension, "");
            if (!names.contains(name)) {
                names.add(name);
            }
        }
    }

    private File resolveProfileFile(String profileName) {
        File primaryFile = new File(PATTERNS_DIRECTORY + "/" + profileName + PRIMARY_EXTENSION);
        if (primaryFile.exists()) {
            return primaryFile;
        }
        return new File(LEGACY_PATTERNS_DIRECTORY + "/" + profileName + LEGACY_EXTENSION);
    }

    private static class AimData {
        Vec3d targetPoint;
        float perfectYaw;
        float perfectPitch;
        float angleYaw;
        float anglePitch;
        double distance;
    }

    private static class Frame implements Serializable {
        private static final long serialVersionUID = 7L;
        float deltaYaw;
        float deltaPitch;
        float angleYaw;
        float anglePitch;
        double distance;
        boolean hasTarget;
        float smoothness;
    }

    private static class SaveData implements Serializable {
        private static final long serialVersionUID = 7L;
        List<NeuroPattern> patterns;
        List<Frame> frames;
    }
}
