package fun.lumis.client.modules.impl.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.FreeLookStorage;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.math.MathUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Arrows extends Module {

    public static Arrows INSTANCE = new Arrows();
    private static final Identifier FIRST_ARROW_TEXTURE = Identifier.of("lumis", "textures/arrows/arrow.png");
    private static final Identifier SECOND_ARROW_TEXTURE = Identifier.of("lumis", "textures/arrows/arr.png");
    private static final Identifier MAMA_ARROW_TEXTURE = Identifier.of("lumis", "textures/arrows/arrowsnurik.png");

    private final ModeSetting type = new ModeSetting("Вид", "Первый", "Первый", "Второй", "Третий");
    private final FloatSetting radius = new FloatSetting("Радиус", 58.0f, 30.0f, 120.0f, 1.0f);
    private final FloatSetting size = new FloatSetting("Размер", 13.0f, 8.0f, 28.0f, 0.5f);
    private final FloatSetting glowRadius = new FloatSetting("Свечение", 7.5f, 0.0f, 20.0f, 0.5f);

    private final Map<UUID, ArrowState> states = new HashMap<>();
    private final Set<UUID> seenPlayers = new HashSet<>();

    public Arrows() {
        super("Arrows", "Красивые стрелочки на энтити", ModuleCategory.RENDER);
        addSettings(type, radius, size, glowRadius);
    }

    @EventLink
    public void onRender(EventRender.Default event) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) {
            states.clear();
            return;
        }
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            fadeAllStates();
            return;
        }

        float partialTicks = event.getPartialTicks();
        float centerX = mc.getWindow().getScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getScaledHeight() * 0.5f;
        float arrowSize = size.get();
        float y = centerY - radius.get();
        float playerYaw = getReferenceYaw(partialTicks);
        Vec3d selfPos = getReferencePos(partialTicks);

        seenPlayers.clear();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || isGhostPlayer(player)) {
                continue;
            }

            UUID uuid = player.getUuid();
            ArrowState state = states.computeIfAbsent(uuid, id -> new ArrowState());
            seenPlayers.add(uuid);

            int color = getPlayerColor(player);
            float targetYaw = getRelativeYaw(player, partialTicks, playerYaw, selfPos);
            state.rotation = interpolateAngle(state.rotation, targetYaw, 0.18f);
            state.alpha = approach(state.alpha, 1.0f, 0.12f);
            float alpha = MathHelper.clamp(state.alpha, 0.0f, 1.0f);
            if (alpha <= 0.01f) {
                continue;
            }

            int drawColor = ColorUtils.applyAlpha(color, alpha);
            int shadowColor = ColorUtils.applyAlpha(ColorUtils.darken(color, 0.55f), alpha * 0.65f);
            renderArrow(event.getContext().getMatrices(), centerX, centerY, y, arrowSize, state.rotation, drawColor, shadowColor);
        }

        states.entrySet().removeIf(entry -> {
            if (seenPlayers.contains(entry.getKey())) {
                return false;
            }
            ArrowState state = entry.getValue();
            state.alpha = approach(state.alpha, 0.0f, 0.10f);
            return state.alpha <= 0.02f;
        });
    }

    private void renderArrow(MatrixStack matrices, float centerX, float centerY, float y, float size, float rotation, int color, int shadowColor) {

        Identifier ARROW;
        if (type.getIndex() == 0) {
            ARROW = FIRST_ARROW_TEXTURE;
        } else if (type.getIndex() == 1) {
            ARROW = SECOND_ARROW_TEXTURE;
        } else {
            ARROW = MAMA_ARROW_TEXTURE;
        }

        Identifier ARROW_TEXTURE = ARROW;
        matrices.push();
        matrices.translate(centerX, centerY, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        matrices.translate(-centerX, -centerY, 0.0f);

        float x = centerX - size * 0.5f;
        RenderUtils.drawImage(matrices, ARROW_TEXTURE, x, y + size * 0.08f, size, size, shadowColor);
        RenderUtils.drawImage(matrices, ARROW_TEXTURE, x, y, size, size, color);
        matrices.pop();
    }

    private void fadeAllStates() {
        states.entrySet().removeIf(entry -> {
            ArrowState state = entry.getValue();
            state.alpha = approach(state.alpha, 0.0f, 0.10f);
            return state.alpha <= 0.02f;
        });
    }

    private float approach(float current, float target, float factor) {
        factor = MathHelper.clamp(factor, 0.0f, 1.0f);
        return MathHelper.lerp(factor, current, target);
    }

    private int getPlayerColor(AbstractClientPlayerEntity player) {
        String name = player.getName().getString();
        boolean isFriend = Lumis.INSTANCE.friendStorage != null && Lumis.INSTANCE.friendStorage.isFriend(name);
        return isFriend ? ColorUtils.rgb(80, 170, 255) : ColorUtils.getThemeColor();
    }

    private float getRelativeYaw(Entity entity, float partialTicks, float playerYaw, Vec3d selfPos) {
        Vec3d entityPos = MathUtils.interpolate(entity, partialTicks);

        double dx = entityPos.x - selfPos.x;
        double dz = entityPos.z - selfPos.z;
        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        return MathHelper.wrapDegrees(yaw - playerYaw);
    }

    private float getReferenceYaw(float partialTicks) {
        if (FreeLookStorage.isActive()) {
            return FreeLookStorage.getFreeYaw();
        }
        return MathHelper.lerp(partialTicks, mc.player.prevYaw, mc.player.getYaw());
    }

    private Vec3d getReferencePos(float partialTicks) {
        if (FreeLookStorage.isActive() && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            return mc.gameRenderer.getCamera().getPos();
        }
        return MathUtils.interpolate(mc.player, partialTicks);
    }

    private float interpolateAngle(float current, float target, float factor) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + delta * factor;
    }

    private boolean isGhostPlayer(AbstractClientPlayerEntity player) {
        if (player.getCustomName() != null) {
            String name = player.getCustomName().getString();
            if (name != null && name.startsWith("Ghost_")) {
                return true;
            }
        }
        return "OtherClientPlayerEntity".equals(player.getClass().getSimpleName()) && player.getPitch() == -30.0f;
    }

    private static final class ArrowState {
        private float alpha;
        private float rotation;
    }
}

