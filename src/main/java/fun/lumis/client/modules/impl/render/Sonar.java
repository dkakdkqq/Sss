package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventChunkReload;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.ShaderUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
public class Sonar extends Module {

    public static Sonar INSTANCE = new Sonar();

    private final FloatSetting duration = new FloatSetting("Длительность", 5.6f, 0.8f, 10.0f, 0.1f);
    private final FloatSetting alpha = new FloatSetting("Яркость", 1.0f, 0.1f, 1.0f, 0.01f);
    private final FloatSetting widthMul = new FloatSetting("Ширина", 1.0f, 0.35f, 2.2f, 0.05f);
    private final FloatSetting sharpness = new FloatSetting("Резкость", 24f, 4f, 80f, 1f);

    private Framebuffer depthCopyBuffer;
    private int lastFbWidth = -1;
    private int lastFbHeight = -1;

    private long currentStart;
    private Vec3d center = Vec3d.ZERO;

    public Sonar() {
        super("Sonar", "Сканирует новые чанки", ModuleCategory.RENDER);
        addSettings(duration, alpha, widthMul, sharpness);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            ping(mc.player.getPos());
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        currentStart = 0L;
        deleteDepthCopyFramebuffer();
        super.onDisable();
    }

    @EventLink
    public void onChunkReload(EventChunkReload event) {
        if (mc.player != null) {
            ping(mc.player.getPos());
        }
    }

    public void renderFromMixin(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d camPos) {
        if (mc.player == null || mc.world == null || currentStart <= 0L) {
            return;
        }

        float durationMs = duration.get() * 1000f;
        float elapsed = System.currentTimeMillis() - currentStart;
        if (elapsed >= durationMs) {
            currentStart = 0L;
            return;
        }

        Framebuffer framebuffer = mc.getFramebuffer();
        ensureDepthCopyFramebuffer(framebuffer.textureWidth, framebuffer.textureHeight);
        if (depthCopyBuffer == null) {
            return;
        }
        depthCopyBuffer.copyDepthFrom(framebuffer);

        Matrix4f invView = new Matrix4f(positionMatrix).invert();
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();

        float far = mc.gameRenderer.getFarPlaneDistance();
        float t = MathHelper.clamp(elapsed / durationMs, 0f, 1f);
        float r1 = lerp(1f, far, (float) Easings.QUINT_OUT.ease(t));
        float r2 = lerp(1f, far, (float) Easings.QUART_IN_OUT.ease(t));
        float baseRadius = MathHelper.lerp(0.85f, r1, r2);

        float alphaPc = 1f - t;
        float alphaWave = (alphaPc > 0.5f ? 1f - alphaPc : alphaPc) * 2f;
        alphaWave = Math.min(alphaWave * 1.75f, 1f);
        float baseAlpha = MathHelper.clamp(alpha.get() * alphaWave, 0f, 1f);

        int c1 = ColorUtils.getThemeColor(0);
        int c2 = ColorUtils.getThemeColor(90);
        int c3 = ColorUtils.getThemeColor(180);
        int c4 = ColorUtils.getThemeColor(270);

        float baseWidth = MathHelper.clamp(6f + baseRadius * (0.18f * widthMul.get()), 4f, Math.max(10f, far * 0.42f));
        float baseSharp = sharpness.get();

        renderPass(invView, invProj, camPos, framebuffer,
                baseRadius,
                baseWidth,
                baseSharp,
                applyAlpha(c1, baseAlpha),
                applyAlpha(c2, baseAlpha),
                applyAlpha(c3, baseAlpha),
                applyAlpha(c4, baseAlpha));

        RenderSystem.defaultBlendFunc();
    }

    private void renderPass(Matrix4f invView, Matrix4f invProj, Vec3d camPos,
                            Framebuffer framebuffer,
                            float radius, float width, float sharp,
                            int outerColor, int midColor, int innerColor, int scanlineColor) {
        if (radius <= 0.001f || width <= 0.001f) {
            return;
        }

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.scanEffect);

        GlUniform invViewUniform = shader.getUniform("invViewMat");
        GlUniform invProjUniform = shader.getUniform("invProjMat");
        GlUniform posUniform = shader.getUniform("pos");
        GlUniform centerUniform = shader.getUniform("center");
        GlUniform radiusUniform = shader.getUniform("radius");
        GlUniform widthUniform = shader.getUniform("width");
        GlUniform sharpnessUniform = shader.getUniform("sharpness");
        GlUniform outerColorUniform = shader.getUniform("outerColor");
        GlUniform midColorUniform = shader.getUniform("midColor");
        GlUniform innerColorUniform = shader.getUniform("innerColor");
        GlUniform scanlineColorUniform = shader.getUniform("scanlineColor");
        GlUniform debugModeUniform = shader.getUniform("DebugMode");

        if (invViewUniform != null) invViewUniform.set(invView);
        if (invProjUniform != null) invProjUniform.set(invProj);
        if (posUniform != null) posUniform.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        if (centerUniform != null) centerUniform.set((float) center.x, (float) center.y, (float) center.z);
        if (radiusUniform != null) radiusUniform.set(radius);
        if (widthUniform != null) widthUniform.set(width);
        if (sharpnessUniform != null) sharpnessUniform.set(sharp);
        if (outerColorUniform != null) setColor(outerColorUniform, outerColor);
        if (midColorUniform != null) setColor(midColorUniform, midColor);
        if (innerColorUniform != null) setColor(innerColorUniform, innerColor);
        if (scanlineColorUniform != null) setColor(scanlineColorUniform, scanlineColor);
        if (debugModeUniform != null) debugModeUniform.set(0);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        int depthTex = depthCopyBuffer.getDepthAttachment();
        if (depthTex == 0) {
            depthTex = mc.getFramebuffer().getDepthAttachment();
        }
        RenderSystem.bindTexture(depthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        framebuffer.beginWrite(false);
        RenderSystem.setShaderTexture(0, depthTex);
        RenderSystem.setShader(ShaderUtils.scanEffect);
        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawFullscreenQuad() {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f);
        buffer.vertex(-1f, 1f, 0f).texture(0f, 1f);
        buffer.vertex(1f, 1f, 0f).texture(1f, 1f);
        buffer.vertex(1f, -1f, 0f).texture(1f, 0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void ensureDepthCopyFramebuffer(int width, int height) {
        if (depthCopyBuffer == null || lastFbWidth != width || lastFbHeight != height) {
            deleteDepthCopyFramebuffer();
            depthCopyBuffer = new SimpleFramebuffer(width, height, true);
            lastFbWidth = width;
            lastFbHeight = height;
        }
    }

    private void deleteDepthCopyFramebuffer() {
        if (depthCopyBuffer != null) {
            depthCopyBuffer.delete();
            depthCopyBuffer = null;
        }
        lastFbWidth = -1;
        lastFbHeight = -1;
    }

    private void ping(Vec3d pos) {
        currentStart = System.currentTimeMillis();
        center = pos;
    }

    private void setColor(GlUniform uniform, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;
        uniform.set(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    private int applyAlpha(int color, float alphaMul) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        a = (int) (a * MathHelper.clamp(alphaMul, 0f, 1f));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

