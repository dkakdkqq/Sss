package fun.lumis.api.utils.baritone;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BaritoneAntiStuck {

    private static final String PROTECTED_BLOCK_MESSAGE = "Извините, но вы не можете сломать блок здесь";
    private static final long STUCK_TIMEOUT_MS = 7_000L;
    private static final double PROGRESS_DISTANCE_SQ = 1.0D;
    private static final int RECOVERY_TICKS = 12;
    private static final double PRIVATE_ESCAPE_DISTANCE_SQ = 50.0D * 50.0D;
    private static final long PRIVATE_ESCAPE_TIMEOUT_MS = 25_000L;
    private static final double SIDE_OFFSET = 0.95D;
    private static final double FORWARD_OFFSET = 0.35D;

    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static final String INPUT_ENUM_CLASS = "baritone.api.utils.input.Input";

    private static Vec3d anchorPos;
    private static long lastProgressAtMs;
    private static int recoveryTicksRemaining;
    private static boolean strafeRightNext;
    private static boolean privateEscapePending;
    private static boolean privateEscapeActive;
    private static boolean privateEscapeRight;
    private static Vec3d privateEscapeStartPos;
    private static long privateEscapeStartedAtMs;

    private BaritoneAntiStuck() {
    }

    public static void onGameMessage(String message) {
        if (message == null || !message.contains(PROTECTED_BLOCK_MESSAGE)) {
            return;
        }
        privateEscapePending = true;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        try {
            Object baritone = getPrimaryBaritone();
            if (baritone == null) {
                resetState();
                return;
            }

            Object pathing = invoke(baritone, "getPathingBehavior");
            Object input = invoke(baritone, "getInputOverrideHandler");
            if (pathing == null || input == null || !Boolean.TRUE.equals(invoke(pathing, "isPathing"))) {
                clearRecovery(input);
                resetTracking();
                return;
            }

            long now = System.currentTimeMillis();
            Vec3d currentPos = mc.player.getPos();

            if (anchorPos == null) {
                anchorPos = currentPos;
                lastProgressAtMs = now;
            }

            if (privateEscapePending && isMiningNow(mc, input)) {
                startPrivateEscape(mc, currentPos);
                privateEscapePending = false;
            }

            if (privateEscapeActive) {
                if (currentPos.squaredDistanceTo(privateEscapeStartPos) >= PRIVATE_ESCAPE_DISTANCE_SQ
                        || now - privateEscapeStartedAtMs >= PRIVATE_ESCAPE_TIMEOUT_MS) {
                    clearAllKeys(input);
                    privateEscapeActive = false;
                    anchorPos = currentPos;
                    lastProgressAtMs = now;
                    return;
                }

                applyPrivateEscapeInput(mc, input);
                anchorPos = currentPos;
                lastProgressAtMs = now;
                return;
            }

            if (recoveryTicksRemaining > 0) {
                applyRecoveryInput(mc, input);
                recoveryTicksRemaining--;
                if (recoveryTicksRemaining <= 0) {
                    clearAllKeys(input);
                    anchorPos = mc.player.getPos();
                    lastProgressAtMs = now;
                }
                return;
            }

            if (isMiningNow(mc, input)) {
                anchorPos = currentPos;
                lastProgressAtMs = now;
                return;
            }

            if (!isTryingToMove(input)) {
                anchorPos = currentPos;
                lastProgressAtMs = now;
                return;
            }

            if (currentPos.squaredDistanceTo(anchorPos) >= PROGRESS_DISTANCE_SQ) {
                anchorPos = currentPos;
                lastProgressAtMs = now;
                return;
            }

            if (now - lastProgressAtMs < STUCK_TIMEOUT_MS) {
                return;
            }

            recoveryTicksRemaining = RECOVERY_TICKS;
            strafeRightNext = chooseRecoverySide(mc, strafeRightNext, true);
            applyRecoveryInput(mc, input);
            anchorPos = currentPos;
            lastProgressAtMs = now;
        } catch (Throwable ignored) {
            resetState();
        }
    }

    private static Object getPrimaryBaritone() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
        Object provider = apiClass.getMethod("getProvider").invoke(null);
        return provider == null ? null : provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
    }

    private static boolean isMiningNow(MinecraftClient mc, Object input) throws ReflectiveOperationException {
        return (mc.interactionManager != null && mc.interactionManager.isBreakingBlock())
                || isInputForcedDown(input, "CLICK_LEFT");
    }

    private static boolean isTryingToMove(Object input) throws ReflectiveOperationException {
        return isInputForcedDown(input, "MOVE_FORWARD")
                || isInputForcedDown(input, "MOVE_BACK")
                || isInputForcedDown(input, "MOVE_LEFT")
                || isInputForcedDown(input, "MOVE_RIGHT")
                || isInputForcedDown(input, "JUMP");
    }

    private static void startPrivateEscape(MinecraftClient mc, Vec3d currentPos) {
        privateEscapeActive = true;
        privateEscapeStartPos = currentPos;
        privateEscapeStartedAtMs = System.currentTimeMillis();
        privateEscapeRight = chooseRecoverySide(mc, privateEscapeRight, false);
    }

    private static void applyRecoveryInput(MinecraftClient mc, Object input) throws ReflectiveOperationException {
        clearAllKeys(input);
        setInputForceState(input, "MOVE_FORWARD", true);
        setInputForceState(input, strafeRightNext ? "MOVE_RIGHT" : "MOVE_LEFT", true);
        if (mc.player != null && mc.player.isOnGround()) {
            setInputForceState(input, "JUMP", true);
        }
    }

    private static void applyPrivateEscapeInput(MinecraftClient mc, Object input) throws ReflectiveOperationException {
        clearAllKeys(input);
        setInputForceState(input, "MOVE_BACK", true);
        setInputForceState(input, privateEscapeRight ? "MOVE_RIGHT" : "MOVE_LEFT", true);
        if (mc.player != null && mc.player.isOnGround()) {
            setInputForceState(input, "JUMP", true);
        }
    }

    private static boolean chooseRecoverySide(MinecraftClient mc, boolean fallbackRight, boolean moveForward) {
        if (mc.player == null) {
            return fallbackRight;
        }

        double yawRad = Math.toRadians(mc.player.getYaw());
        Vec3d forwardDirection = new Vec3d(-MathHelper.sin((float) yawRad), 0.0D, MathHelper.cos((float) yawRad));
        Vec3d left = new Vec3d(forwardDirection.z, 0.0D, -forwardDirection.x);
        Vec3d right = left.multiply(-1.0D);
        Vec3d direction = moveForward ? forwardDirection : forwardDirection.multiply(-1.0D);

        double leftScore = freeSpaceScore(mc, left.multiply(SIDE_OFFSET).add(direction.multiply(FORWARD_OFFSET)));
        double rightScore = freeSpaceScore(mc, right.multiply(SIDE_OFFSET).add(direction.multiply(FORWARD_OFFSET)));

        if (leftScore == rightScore) {
            return fallbackRight;
        }

        return rightScore > leftScore;
    }

    private static double freeSpaceScore(MinecraftClient mc, Vec3d offset) {
        Box shifted = mc.player.getBoundingBox().offset(offset);
        double score = 0.0D;
        if (mc.world.isSpaceEmpty(mc.player, shifted)) {
            score += 1.0D;
        }
        if (mc.world.isSpaceEmpty(mc.player, shifted.offset(0.0D, 1.0D, 0.0D))) {
            score += 0.35D;
        }
        return score;
    }

    private static void clearRecovery(Object input) {
        if (recoveryTicksRemaining > 0 && input != null) {
            try {
                clearAllKeys(input);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        recoveryTicksRemaining = 0;
        if (privateEscapeActive && input != null) {
            try {
                clearAllKeys(input);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        privateEscapeActive = false;
        privateEscapePending = false;
    }

    private static void resetTracking() {
        anchorPos = null;
        lastProgressAtMs = 0L;
    }

    private static void resetState() {
        recoveryTicksRemaining = 0;
        anchorPos = null;
        lastProgressAtMs = 0L;
        privateEscapePending = false;
        privateEscapeActive = false;
        privateEscapeStartPos = null;
        privateEscapeStartedAtMs = 0L;
    }

    private static boolean isInputForcedDown(Object inputOverrideHandler, String inputName) throws ReflectiveOperationException {
        Object input = getInputEnum(inputName);
        Object result = inputOverrideHandler.getClass()
                .getMethod("isInputForcedDown", input.getClass())
                .invoke(inputOverrideHandler, input);
        return Boolean.TRUE.equals(result);
    }

    private static void setInputForceState(Object inputOverrideHandler, String inputName, boolean forced) throws ReflectiveOperationException {
        Object input = getInputEnum(inputName);
        inputOverrideHandler.getClass()
                .getMethod("setInputForceState", input.getClass(), boolean.class)
                .invoke(inputOverrideHandler, input, forced);
    }

    private static void clearAllKeys(Object inputOverrideHandler) throws ReflectiveOperationException {
        inputOverrideHandler.getClass().getMethod("clearAllKeys").invoke(inputOverrideHandler);
    }

    private static Object getInputEnum(String inputName) throws ReflectiveOperationException {
        Class<?> inputEnum = Class.forName(INPUT_ENUM_CLASS);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) inputEnum.asSubclass(Enum.class), inputName);
        return value;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}
