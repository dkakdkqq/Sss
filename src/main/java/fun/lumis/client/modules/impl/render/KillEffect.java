package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventAttackEntity;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;

import java.util.*;

public class KillEffect extends Module {

    public static KillEffect INSTANCE = new KillEffect();
    private static final Identifier GLOW_TEX = Identifier.of("lumis", "textures/particle/bloom.png");
    private static final float DURATION = 1.5f;
    private static final float HEIGHT = 4.0f;
    private static final float MAX_RADIUS = 1f;
    private static final int SLICES = 40;
    private final Map<Entity, Vec3d> trackedEntities = new IdentityHashMap<>();
    private final List<ActiveEffect> effects = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", "Эффект при исчезновении цели", ModuleCategory.RENDER);
    }

    @Override
    public void onDisable() {
        trackedEntities.clear();
        effects.clear();
        super.onDisable();
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (mc.player == null || mc.world == null) return;
        Entity target = event.getTarget();
        if (target instanceof LivingEntity && target != mc.player) {
            trackedEntities.put(target, target.getPos());
        }
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null) return;
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Entity, Vec3d>> trackIterator = trackedEntities.entrySet().iterator();
        while (trackIterator.hasNext()) {
            Map.Entry<Entity, Vec3d> entry = trackIterator.next();
            Entity entity = entry.getKey();
            if (entity.isRemoved() || !entity.isAlive()) {
                effects.add(new ActiveEffect(entry.getValue(), currentTime));
                trackIterator.remove();
            } else {
                entry.setValue(entity.getPos());
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEX);
        Iterator<ActiveEffect> effectIterator = effects.iterator();
        while (effectIterator.hasNext()) {
            ActiveEffect effect = effectIterator.next();
            float progress = (currentTime - effect.startTime) / (DURATION * 1000);
            if (progress >= 1.0f) {
                effectIterator.remove();
                continue;
            }
            renderEffect(event.getMatrices(), effect, mc.gameRenderer.getCamera().getPos(), progress);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderEffect(MatrixStack matrices, ActiveEffect effect, Vec3d cameraPos, float progress) {
        int color = ColorUtils.getThemeColor();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float globalAlpha = progress < 0.15f ? progress / 0.15f : (progress > 0.75f ? (1.0f - progress) / 0.25f : 1.0f);
        float sliceHeight = HEIGHT / SLICES;
        for (int i = 0; i < SLICES; i++) {
            float t = (float) i / SLICES;
            float y = t * HEIGHT;
            float radius = MAX_RADIUS * MathHelper.sin((float) (Math.PI * t));
            float sliceAlpha = (1.0f - Math.abs(2.0f * t - 1.0f) * 0.25f) * globalAlpha;
            Vec3d pos = effect.position.add(0, y, 0);
            renderGlow(matrices, cameraPos, pos, radius * 2.1f, r, g, b, sliceAlpha * 0.22f);
            renderGlow(matrices, cameraPos, pos, radius * 1.15f, r, g, b, sliceAlpha * 0.48f);
            renderGlow(matrices, cameraPos, pos, radius * 0.55f, r, g, b, sliceAlpha * 0.85f);
        }
        for (int i = 0; i < 10; i++) {
            float t = (float) i / 10.0f;
            float spread = 1.0f - t;
            float bottomRadius = MAX_RADIUS * 3.6f * spread;
            float bottomAlpha = spread * spread * globalAlpha * 0.38f;
            Vec3d bPos = effect.position.add(0, t * 0.45f, 0);
            renderGlow(matrices, cameraPos, bPos, bottomRadius, r, g, b, bottomAlpha);
            renderGlow(matrices, cameraPos, bPos, bottomRadius * 0.35f, r, g, b, bottomAlpha * 1.7f);
        }
    }

    private void renderGlow(MatrixStack matrices, Vec3d cameraPos, Vec3d position, float size, float r, float g, float b, float a) {
        if (a <= 0.01f) return;
        matrices.push();
        matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float half = size * 0.5f;
        int rInt = Math.min(255, (int) (r * 255));
        int gInt = Math.min(255, (int) (g * 255));
        int bInt = Math.min(255, (int) (b * 255));
        int aInt = Math.min(255, (int) (a * 255));
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, half, half, 0).texture(1, 0).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(rInt, gInt, bInt, aInt);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private static class ActiveEffect {
        final Vec3d position;
        final long startTime;
        ActiveEffect(Vec3d position, long startTime) {
            this.position = position;
            this.startTime = startTime;
        }
    }
}