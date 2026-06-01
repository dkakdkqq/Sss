package fun.lumis.client.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireworkESP extends Module {

    public static FireworkESP INSTANCE = new FireworkESP();

    private final FloatSetting interval = new FloatSetting("Интервал (мс)", 100.0f, 10.0f, 1000.0f, 10.0f);
    private final FloatSetting lifetime = new FloatSetting("Время жизни (мс)", 1000.0f, 100.0f, 5000.0f, 100.0f);

    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Quaternionf lastCameraRotation = new Quaternionf();
    private Vec3d lastCameraPos = Vec3d.ZERO;
    private float lastTickDelta;
    private final Map<Integer, FireworkData> fireworks = new HashMap<>();

    public FireworkESP() {
        super("FireworkESP", "Показывает теги и трейлы фейерверков", ModuleCategory.RENDER);
        addSettings(interval, lifetime);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        fireworks.clear();
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        lastProjectionMatrix.set(event.getProjectionMatrix());
        lastCameraPos = event.getCamera().getPos();
        lastCameraRotation.set(event.getCamera().getRotation());
        lastTickDelta = event.getTickDelta();

        if (mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        fireworks.entrySet().removeIf(entry -> {
            Entity entity = mc.world.getEntityById(entry.getKey());
            boolean isDead = (entity == null || !entity.isAlive());
            entry.getValue().points.removeIf(p -> currentTime - p.timestamp > lifetime.get());
            return isDead && entry.getValue().points.isEmpty();
        });

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity && entity.isAlive()) {
                FireworkData data = fireworks.computeIfAbsent(entity.getId(), k -> new FireworkData());

                if (currentTime - data.lastSpawnTime >= interval.get()) {
                    Vec3d pos = new Vec3d(
                            MathHelper.lerp(lastTickDelta, entity.lastRenderX, entity.getX()),
                            MathHelper.lerp(lastTickDelta, entity.lastRenderY, entity.getY()) + 0.5,
                            MathHelper.lerp(lastTickDelta, entity.lastRenderZ, entity.getZ())
                    );
                    float ageInSeconds = entity.age / 20.0f;
                    data.points.add(new TrailPoint(pos, currentTime, ageInSeconds));
                    data.lastSpawnTime = currentTime;
                }
            }
        }
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = event.getContext().getMatrices();
        ItemStack icon = new ItemStack(Items.FIREWORK_ROCKET);
        Font font = Fonts.getFont("sf_regular", 14);
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<Integer, FireworkData> entry : fireworks.entrySet()) {
            FireworkData data = entry.getValue();

            for (TrailPoint p : data.points) {
                Vec3d screen = worldToScreen(p.pos);
                if (screen == null) continue;

                float progress = 1.0f - ((float) (currentTime - p.timestamp) / lifetime.get());
                progress = MathHelper.clamp(progress, 0.0f, 1.0f);
                String text = String.format("%.1fs", p.ageSec);

                renderIconRect(event, matrices, font, icon, screen, progress, text);
            }

            Entity entity = mc.world.getEntityById(entry.getKey());
            if (entity instanceof FireworkRocketEntity && entity.isAlive()) {
                Vec3d currentPos = new Vec3d(
                        MathHelper.lerp(lastTickDelta, entity.lastRenderX, entity.getX()),
                        MathHelper.lerp(lastTickDelta, entity.lastRenderY, entity.getY()) + 0.5,
                        MathHelper.lerp(lastTickDelta, entity.lastRenderZ, entity.getZ())
                );
                Vec3d screen = worldToScreen(currentPos);
                if (screen != null) {
                    String text = String.format("%.1fs", entity.age / 20.0f);
                    renderIconRect(event, matrices, font, icon, screen, 1.0f, text);
                }
            }
        }
    }

    private void renderIconRect(EventRender.Default event, MatrixStack matrices, Font font, ItemStack icon, Vec3d screen, float progress, String text) {
        float iconScale = 0.6f;
        float rectHeight = 12.0f;
        float padding = 2.5f;
        float gap = 2.0f;
        float textYOffset = 3.5f;

        float animScale = 0.35f + 0.65f * progress;
        int alpha = (int) (200 * progress);
        if (alpha <= 5) return;

        int bgColor = (alpha << 24) | 0x0A0A0A;
        int textColor = (alpha << 24) | 0xFFFFFF ;

        float textWidth = font != null ? font.getStringWidth(text) : 0;
        float iconWidth = 16.0f * iconScale;
        float totalWidth = padding + iconWidth + gap + textWidth + padding;

        matrices.push();
        matrices.translate(screen.x, screen.y, 0);
        matrices.scale(animScale, animScale, 1.0f);

        RenderUtils.drawRoundedRect(matrices, -totalWidth / 2.0f, -rectHeight / 2.0f, totalWidth, rectHeight, 0.0f, bgColor);

        float currentX = -totalWidth / 2.0f + padding;

        matrices.push();
        matrices.translate(currentX, -(16.0f * iconScale) / 2.0f, 0);
        matrices.scale(iconScale, iconScale, 1.0f);
        event.getContext().drawItem(icon, 0, 0);
        matrices.pop();

        currentX += iconWidth + gap;

        if (font != null) {
            font.drawString(matrices, text, currentX, -rectHeight / 2.0f + textYOffset + 0.5f, textColor);
        }

        matrices.pop();
    }

    private Vec3d worldToScreen(Vec3d worldPos) {
        Vector3f relative = new Vector3f(
                (float) (worldPos.x - lastCameraPos.x),
                (float) (worldPos.y - lastCameraPos.y),
                (float) (worldPos.z - lastCameraPos.z)
        );

        relative.rotate(new Quaternionf(lastCameraRotation).conjugate());

        Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0f);
        lastProjectionMatrix.transform(clip);

        float w = clip.w;
        if (w <= 0.00001f) return null;

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        float ndcZ = clip.z / w;

        float screenX = (ndcX * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * mc.getWindow().getScaledHeight();

        if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return null;
        }

        return new Vec3d(screenX, screenY, ndcZ);
    }

    private static class FireworkData {
        long lastSpawnTime;
        final List<TrailPoint> points = new ArrayList<>();
    }

    private static class TrailPoint {
        final Vec3d pos;
        final long timestamp;
        final float ageSec;

        TrailPoint(Vec3d pos, long timestamp, float ageSec) {
            this.pos = pos;
            this.timestamp = timestamp;
            this.ageSec = ageSec;
        }
    }
}