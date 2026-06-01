package fun.lumis.api.utils.render.glow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ProjectionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.awt.*;

public class GlowProgram {
    private static GlowProgram instance;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Framebuffer glowBuffer;
    private int lastWidth;
    private int lastHeight;

    private float glowRadius = 10f;
    private float glowIntensity = 1f;
    private Color glowColor = Color.WHITE;

    private Matrix4f savedProjection;
    private int savedFbo;

    // Настройки качества (можно менять)
    private static final int RINGS = 6;           // Количество колец
    private static final int ANGLES_PER_RING = 12; // Точек на кольцо

    public static GlowProgram getInstance() {
        if (instance == null) {
            instance = new GlowProgram();
        }
        return instance;
    }

    private void checkFramebuffers() {
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (glowBuffer == null || lastWidth != width || lastHeight != height) {
            if (glowBuffer != null) {
                glowBuffer.delete();
            }
            glowBuffer = new SimpleFramebuffer(width, height, false);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public void begin(float radius, Color color) {
        begin(radius, 1f, color);
    }

    public void begin(float radius, float intensity, Color color) {
        checkFramebuffers();

        this.glowRadius = radius;
        this.glowIntensity = intensity;
        this.glowColor = color;

        savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        savedFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glowBuffer.fbo);
        GL11.glViewport(0, 0, lastWidth, lastHeight);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        RenderSystem.clear(GL30.GL_COLOR_BUFFER_BIT);
        RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
    }

    public void end(MatrixStack matrices, GlowCallback contentCallback) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
        GL11.glViewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);

        renderGlow(matrices);

        if (contentCallback != null) {
            contentCallback.render();
        }
    }

    private float gaussian(float x, float sigma) {
        return (float) Math.exp(-(x * x) / (2.0f * sigma * sigma));
    }

    private void renderGlow(MatrixStack matrices) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableDepthTest();

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        RenderSystem.setShaderTexture(0, glowBuffer.getColorAttachment());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float r = glowColor.getRed() / 255f;
        float g = glowColor.getGreen() / 255f;
        float b = glowColor.getBlue() / 255f;
        float baseAlpha = (glowColor.getAlpha() / 255f) * glowIntensity;

        float sigma = glowRadius * 0.4f;

        // Предрассчитываем веса для каждого кольца
        float[] ringWeights = new float[RINGS];
        float totalWeight = 0f;

        for (int i = 0; i < RINGS; i++) {
            float distance = (glowRadius * (i + 1)) / RINGS;
            ringWeights[i] = gaussian(distance, sigma);
            totalWeight += ringWeights[i];
        }

        // Нормализуем
        for (int i = 0; i < RINGS; i++) {
            ringWeights[i] /= totalWeight;
        }

        // ===== ОДИН DRAW CALL =====
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        for (int ring = 0; ring < RINGS; ring++) {
            float distance = (glowRadius * (ring + 1)) / RINGS;
            float alpha = baseAlpha * ringWeights[ring] * 0.7f;

            if (alpha < 0.001f) continue;
            alpha = Math.min(alpha, 1.0f);

            for (int angle = 0; angle < ANGLES_PER_RING; angle++) {
                float a1 = (float)(angle * 2.0 * Math.PI) / ANGLES_PER_RING;
                float ox = (float) Math.cos(a1) * distance;
                float oy = (float) Math.sin(a1) * distance;

                buffer.vertex(matrix, ox, oy, 0)
                        .texture(0, 1).color(r, g, b, alpha);
                buffer.vertex(matrix, ox, height + oy, 0)
                        .texture(0, 0).color(r, g, b, alpha);
                buffer.vertex(matrix, width + ox, height + oy, 0)
                        .texture(1, 0).color(r, g, b, alpha);
                buffer.vertex(matrix, width + ox, oy, 0)
                        .texture(1, 1).color(r, g, b, alpha);

                // Промежуточные точки для более плавного свечения
                if (ring > 0) {
                    float a2 = (float)((angle + 0.5) * 2.0 * Math.PI) / ANGLES_PER_RING;
                    float innerDist = distance * 0.6f;
                    float ox2 = (float) Math.cos(a2) * innerDist;
                    float oy2 = (float) Math.sin(a2) * innerDist;
                    float alpha2 = alpha * 0.5f;

                    buffer.vertex(matrix, ox2, oy2, 0)
                            .texture(0, 1).color(r, g, b, alpha2);
                    buffer.vertex(matrix, ox2, height + oy2, 0)
                            .texture(0, 0).color(r, g, b, alpha2);
                    buffer.vertex(matrix, width + ox2, height + oy2, 0)
                            .texture(1, 0).color(r, g, b, alpha2);
                    buffer.vertex(matrix, width + ox2, oy2, 0)
                            .texture(1, 1).color(r, g, b, alpha2);
                }
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // Сброс
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        RenderSystem.setShaderTexture(0, 0);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void startGlow(float radius, int color, GlowCallback callback, MatrixStack matrices) {
        startGlow(radius, 1f, color, callback, matrices);
    }

    public static void startGlow(float radius, float intensity, int color, GlowCallback callback, MatrixStack matrices) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;

        GlowProgram glow = GlowProgram.getInstance();
        glow.begin(radius, intensity, new Color(r, g, b, a));
        callback.render();
        glow.end(matrices, callback);
    }

    public void cleanup() {
        if (glowBuffer != null) {
            glowBuffer.delete();
            glowBuffer = null;
        }
    }
}