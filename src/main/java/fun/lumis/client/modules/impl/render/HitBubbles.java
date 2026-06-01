package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventAttackEntity;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;

import java.util.concurrent.CopyOnWriteArrayList;

public class HitBubbles extends Module {

    public static HitBubbles INSTANCE = new HitBubbles();

    private static final long LIFE_MS = 1600L;

    private final CopyOnWriteArrayList<HitBubble> bubbles = new CopyOnWriteArrayList<>();
    private final Identifier bubbleTexture = Identifier.of("lumis", "textures/hitbubble/bubble.png");

    public HitBubbles() {
        super("HitBubbles", "Круг при ударе игрока", ModuleCategory.RENDER);
    }

    @Override
    public void onDisable() {
        bubbles.clear();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        long now = System.currentTimeMillis();
        bubbles.removeIf(b -> now - b.spawnTime() >= LIFE_MS);
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (event == null || event.getTarget() == null) return;
        if (!(event.getTarget() instanceof LivingEntity living)) return;
        if (event.getPlayer() == null) return;

        Vec3d sideDir = getHitSideDirection(living, event.getPlayer().getPos());
        Vec3d pos = getHitPosition(living, sideDir);
        float sideYaw = (float) Math.toDegrees(Math.atan2(sideDir.x, sideDir.z));
        bubbles.add(new HitBubble(pos, System.currentTimeMillis(), (float) (Math.random() * 360.0), sideYaw));
    }

    @EventLink
    public void onWorldRender(Event3DRender event) {
        if (bubbles.isEmpty() || mc.player == null) return;

        MatrixStack stack = event.getMatrices();
        Vec3d cameraPos = event.getCamera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, bubbleTexture);

        long now = System.currentTimeMillis();
        for (HitBubble bubble : bubbles) {
            renderSingleBubble(stack, cameraPos, bubble, now);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderSingleBubble(MatrixStack stack, Vec3d cameraPos, HitBubble bubble, long now) {
        float progress = (now - bubble.spawnTime()) / (float) LIFE_MS;
        if (progress >= 1.0f) return;

        float inPhase = Math.max(0.0f, Math.min(1.0f, progress / 0.22f));
        float outPhase = Math.max(0.0f, Math.min(1.0f, (progress - 0.225f) / 0.40f));
        float scaleIn = inPhase * inPhase * (3.0f - 2.0f * inPhase);
        float scaleOut = 1.0f - outPhase * outPhase;
        float scale = 0.02f + 1.55f * scaleIn * scaleOut;
        float alpha = 1.0f - outPhase * outPhase * outPhase;
        float rotation = (now - bubble.spawnTime()) / 1.5f + bubble.spinSeed();

        Vec3d rel = bubble.pos().subtract(cameraPos);
        int color = ColorUtils.multAlpha(ColorUtils.getThemeColor(), alpha);

        stack.push();
        stack.translate(rel.x, rel.y, rel.z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bubble.sideYaw()));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-210.0f));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        drawTexturedQuad(stack, -scale * 0.5f, -scale * 0.5f, scale, scale, color);
        stack.pop();
    }

    private void drawTexturedQuad(MatrixStack stack, float x, float y, float width, float height, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;

        Matrix4f mat = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(mat, x, y, 0f).texture(0f, 0f).color(r, g, b, a);
        buffer.vertex(mat, x, y + height, 0f).texture(0f, 1f).color(r, g, b, a);
        buffer.vertex(mat, x + width, y + height, 0f).texture(1f, 1f).color(r, g, b, a);
        buffer.vertex(mat, x + width, y, 0f).texture(1f, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private Vec3d getHitSideDirection(LivingEntity target, Vec3d attackerPos) {
        Vec3d dir = attackerPos.subtract(target.getPos());
        dir = new Vec3d(dir.x, 0.0, dir.z);
        if (dir.lengthSquared() < 1.0E-4) {
            Vec3d fallback = target.getRotationVector();
            dir = new Vec3d(fallback.x, 0.0, fallback.z);
        }
        if (dir.lengthSquared() < 1.0E-4) dir = new Vec3d(0.0, 0.0, 1.0);
        return dir.normalize();
    }

    private Vec3d getHitPosition(LivingEntity target, Vec3d sideDir) {
        Vec3d head = new Vec3d(target.getX(), target.getY() + target.getHeight() + 0.18, target.getZ());
        return head.add(sideDir.multiply(0.1));
    }

    private record HitBubble(Vec3d pos, long spawnTime, float spinSeed, float sideYaw) {
    }
}

