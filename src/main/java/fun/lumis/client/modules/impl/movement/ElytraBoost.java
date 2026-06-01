package fun.lumis.client.modules.impl.movement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

@Getter
@Setter
public class ElytraBoost extends Module {

    private static final String[] RANGE_LABELS = {"0 - 5", "5 - 10", "10 - 15", "15 - 20", "20 - 25", "25 - 30", "30 - 35", "35 - 40", "40 - 45"};
    private static final long DEBUG_MESSAGE_INTERVAL_MS = 800L;
    public static ElytraBoost INSTANCE = new ElytraBoost();

    private final FloatSetting[] yawSpeeds = new FloatSetting[9];
    private final FloatSetting[] pitchSpeeds = new FloatSetting[9];

    private final ModeSetting mode = new ModeSetting("Сервер", "Custom", "Custom", "LonyGrief", "BravoHVH", "ReallyWorld", "SlimeWorld");
    private final BooleanSetting debug = new BooleanSetting("Дебаг", false)
            .visible(this::isCustomMode);
    private long lastDebugMessageAt;

    public ElytraBoost() {
        super("ElytraBoost", "Ускоряет на элитрах", ModuleCategory.MOVEMENT);

        for (int i = 0; i < yawSpeeds.length; i++) {
            yawSpeeds[i] = new FloatSetting("yaw " + RANGE_LABELS[i], 1.5f, 1.5f, 2.5f, 0.01f)
                    .visible(this::isCustomMode);
        }

        for (int i = 0; i < pitchSpeeds.length; i++) {
            pitchSpeeds[i] = new FloatSetting("pitch " + RANGE_LABELS[i], 1.5f, 1.5f, 2.5f, 0.01f)
                    .visible(this::isCustomMode);
        }

        addSettings(mode, debug);
        addSettings(yawSpeeds);
        addSettings(pitchSpeeds);
    }

    public boolean isCustomMode() {
        return mode.is("Custom");
    }

    public Vec2f getBoostV2() {
        float yaw = mc.player != null ? mc.player.getYaw() : 0.0f;
        float pitch = mc.player != null ? mc.player.getPitch() : 0.0f;

        Aura aura = Aura.INSTANCE;
        if (aura != null && aura.isEnable() && aura.getTarget() != null) {
            Vec2f rotations = aura.getTargetRotations();
            if (rotations != null) {
                yaw = rotations.x;
                pitch = rotations.y;
            }
        }

        float normalizedYaw = convertValToRange(MathHelper.wrapDegrees(yaw));
        float normalizedPitch = convertValToRange(Math.abs(pitch));
        int yawIndex = getRangeIndex(normalizedYaw, yawSpeeds.length);
        int pitchIndex = getRangeIndex(normalizedPitch, pitchSpeeds.length);
        float yawSpeed = yawSpeeds[yawIndex].getValue().floatValue();
        float pitchSpeed = pitchSpeeds[pitchIndex].getValue().floatValue();

        if (pitchSpeed > yawSpeed) {
            yawSpeed = pitchSpeed;
        }

        logDebug(yawIndex, yawSpeed, pitchIndex, pitchSpeed);
        return new Vec2f(yawSpeed, pitchSpeed);
    }

    private void logDebug(int yawIndex, float yawSpeed, int pitchIndex, float pitchSpeed) {
        if (!debug.isState()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDebugMessageAt < DEBUG_MESSAGE_INTERVAL_MS) {
            return;
        }

        lastDebugMessageAt = now;
        ChatUtils.sendMessage(
                "yaw " + RANGE_LABELS[yawIndex] + ": " + yawSpeed
                        + " | pitch " + RANGE_LABELS[pitchIndex] + ": " + pitchSpeed
        );
    }

    private int getRangeIndex(float value, int length) {
        return Math.min((int) (value / 5.0F), length - 1);
    }

    private float convertValToRange(float value) {
        float result = Math.abs(value);
        if (result > 90.0F) {
            result = 180.0F - result;
        }
        if (result > 45.0F) {
            result = 90.0F - result;
        }
        return result;
    }
}
