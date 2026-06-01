package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.ShaderUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.mixin.WorldRendererAccessor;

import java.util.ArrayList;
import java.util.List;

public class ShaderEsp extends Module {

    public static ShaderEsp INSTANCE = new ShaderEsp();
    private static final float EPSILON = 0.001f;
    private static final long OUTLINE_RETRY_DELAY_MS = 3000L;
    private static final double MAX_RANGE = 256.0;
    private static final float FILL_ALPHA = 0.7f;
    private static final int FILL_MIN_ITERATIONS = 2;
    private static final float GLOW_VALUE = 0.55f;
    private static final float WIDTH_VALUE = 0.9f;

    private final ListSetting targets = new ListSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Кристаллы", true),
            new BooleanSetting("Предметы", false),
            new BooleanSetting("Себя", false)
    );

    private final BooleanSetting fill = new BooleanSetting("Заливка", false);

    private final List<Framebuffer> bloomBuffers = new ArrayList<>();
    private Framebuffer depthCopyBuffer;
    private int bloomWidth = -1;
    private int bloomHeight = -1;
    private boolean outlineReady;
    private boolean hasOutlineTargetsCached;
    private long nextOutlineRetryAt;

    public ShaderEsp() {
        super("ShaderESP", "Красивая обводка энтити", ModuleCategory.RENDER);
        addSettings(targets, fill);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        outlineReady = false;
        nextOutlineRetryAt = 0L;
        tryEnsureOutlineProcessor();
    }

    @Override
    public void onDisable() {
        for (Framebuffer fb : bloomBuffers) {
            fb.delete();
        }
        bloomBuffers.clear();
        if (depthCopyBuffer != null) {
            depthCopyBuffer.delete();
            depthCopyBuffer = null;
        }
        bloomWidth = -1;
        bloomHeight = -1;
        outlineReady = false;
        hasOutlineTargetsCached = false;
        nextOutlineRetryAt = 0L;
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable()) return;
        if (mc.world == null || mc.worldRenderer == null) {
            outlineReady = false;
            hasOutlineTargetsCached = false;
            return;
        }
        hasOutlineTargetsCached = hasOutlineTargets();
        if (!hasOutlineTargetsCached) {
            outlineReady = false;
            return;
        }
        if (!outlineReady && System.currentTimeMillis() >= nextOutlineRetryAt) {
            tryEnsureOutlineProcessor();
        }
    }

    @EventLink(priority = Priority.HIGHEST)
    public void onRender2D(EventRender.Default event) {
        if (!isEnable() || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        boolean hasGlow = GLOW_VALUE > EPSILON;
        boolean hasFill = fill.isState();
        if (!hasGlow && !hasFill) return;
        if (!hasOutlineTargetsCached) return;
        if (!tryEnsureOutlineProcessor()) return;

        Framebuffer outlineBuffer = getOutlineSourceFramebuffer();
        if (outlineBuffer == null || outlineBuffer.getColorAttachment() == 0) return;

        Framebuffer mainBuffer = mc.getFramebuffer();

        ensureDepthCopyBuffer(mainBuffer.textureWidth, mainBuffer.textureHeight);

        int iterations = Math.max(1, Math.min(8, (int) Math.ceil(WIDTH_VALUE * 1.25f)));
        int fillTexture = 0;
        if (hasFill) {
            int fillIterations = Math.max(FILL_MIN_ITERATIONS, Math.min(6, iterations + 1));
            fillTexture = runKawaseBloom(outlineBuffer.getColorAttachment(), fillIterations);
        }
        int blurredTexture = hasGlow
                ? runKawaseBloom(outlineBuffer.getColorAttachment(), iterations)
                : fillTexture;
        int color = getOutlineColor();

        mainBuffer.beginWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.colorMask(true, true, true, false);

        if (hasFill) {
            ShaderProgram fillShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderEspFill);
            if (fillShader != null) {
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SrcFactor.SRC_ALPHA,
                        GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SrcFactor.ZERO,
                        GlStateManager.DstFactor.ONE
                );
                RenderSystem.setShader(ShaderUtils.shaderEspFill);
                RenderSystem.setShaderTexture(0, outlineBuffer.getColorAttachment());
                RenderSystem.setShaderTexture(1, fillTexture == 0 ? blurredTexture : fillTexture);
                setUniform(fillShader, "color", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
                setUniform(fillShader, "alpha", FILL_ALPHA);
                setUniform(fillShader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
                drawFullscreenQuad();
            }
        }

        if (hasGlow) {
            ShaderProgram glowShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderEspGlow);
            if (glowShader != null) {
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SrcFactor.SRC_ALPHA,
                        GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SrcFactor.ZERO,
                        GlStateManager.DstFactor.ONE
                );
                RenderSystem.setShader(ShaderUtils.shaderEspGlow);
                RenderSystem.setShaderTexture(0, blurredTexture);
                RenderSystem.setShaderTexture(1, outlineBuffer.getColorAttachment());
                setUniform(glowShader, "color", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
                setUniform(glowShader, "color2", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
                setUniform(glowShader, "exposure", 0.015f + GLOW_VALUE * 0.065f);
                setUniform(glowShader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
                setUniform(glowShader, "animate", 1.0f);
                drawFullscreenQuadWithDepthTest(mainBuffer, outlineBuffer);
            }
        }

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(1, 0);
        mainBuffer.beginWrite(true);
    }

    private void drawFullscreenQuadWithDepthTest(Framebuffer mainBuffer, Framebuffer outlineBuffer) {
        if (depthCopyBuffer == null) {
            drawFullscreenQuad();
            return;
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainBuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyBuffer.fbo);
        GL30.glBlitFramebuffer(
                0, 0, mainBuffer.textureWidth, mainBuffer.textureHeight,
                0, 0, depthCopyBuffer.textureWidth, depthCopyBuffer.textureHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, outlineBuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainBuffer.fbo);
        GL30.glBlitFramebuffer(
                0, 0, outlineBuffer.textureWidth, outlineBuffer.textureHeight,
                0, 0, mainBuffer.textureWidth, mainBuffer.textureHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        mainBuffer.beginWrite(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);

        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthCopyBuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainBuffer.fbo);
        GL30.glBlitFramebuffer(
                0, 0, depthCopyBuffer.textureWidth, depthCopyBuffer.textureHeight,
                0, 0, mainBuffer.textureWidth, mainBuffer.textureHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        mainBuffer.beginWrite(false);
    }

    private void ensureDepthCopyBuffer(int width, int height) {
        if (depthCopyBuffer != null) {
            if (depthCopyBuffer.textureWidth != width || depthCopyBuffer.textureHeight != height) {
                depthCopyBuffer.delete();
                depthCopyBuffer = null;
            }
        }
        if (depthCopyBuffer == null) {
            depthCopyBuffer = new SimpleFramebuffer(width, height, true);
        }
    }

    private boolean tryEnsureOutlineProcessor() {
        if (mc.world == null || mc.worldRenderer == null) {
            outlineReady = false;
            return false;
        }
        Framebuffer outlines = getOutlineSourceFramebuffer();
        if (outlines != null && outlines.getColorAttachment() != 0) {
            outlineReady = true;
            return true;
        }
        if (outlineReady) {
            outlineReady = false;
        }
        if (System.currentTimeMillis() < nextOutlineRetryAt) {
            return false;
        }
        try {
            mc.worldRenderer.loadEntityOutlinePostProcessor();
            outlines = getOutlineSourceFramebuffer();
            outlineReady = outlines != null && outlines.getColorAttachment() != 0;
            if (!outlineReady) {
                nextOutlineRetryAt = System.currentTimeMillis() + OUTLINE_RETRY_DELAY_MS;
            }
            return outlineReady;
        } catch (Throwable ignored) {
            outlineReady = false;
            nextOutlineRetryAt = System.currentTimeMillis() + OUTLINE_RETRY_DELAY_MS;
            return false;
        }
    }

    private Framebuffer getOutlineSourceFramebuffer() {
        if (mc.worldRenderer instanceof WorldRendererAccessor accessor) {
            Framebuffer raw = accessor.lumis$getEntityOutlineFramebufferRaw();
            if (raw != null && raw.getColorAttachment() != 0) {
                return raw;
            }
        }
        return mc.worldRenderer.getEntityOutlinesFramebuffer();
    }

    public boolean shouldOutline(Entity entity) {
        if (!isEnable() || entity == null || mc.player == null || mc.world == null) return false;
        if (!entity.isAlive()) return false;
        if (entity.isRemoved()) return false;
        if (entity == mc.player && !targets.is("Себя")) return false;
        if (entity.squaredDistanceTo(mc.player) > MAX_RANGE * MAX_RANGE) return false;

        if (entity instanceof PlayerEntity) {
            return targets.is("Игроки");
        }
        if (entity instanceof EndCrystalEntity) {
            return targets.is("Кристаллы");
        }
        if (entity instanceof ItemEntity) {
            return targets.is("Предметы");
        }
        return false;
    }

    private boolean hasOutlineTargets() {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        for (Entity entity : mc.world.getEntities()) {
            if (shouldOutline(entity)) {
                return true;
            }
        }
        return false;
    }

    public int getOutlineColor() {
        return ColorUtils.setAlphaColor(ColorUtils.getThemeColor(), 255) & 0xFFFFFF;
    }

    private int runKawaseBloom(int sourceTexture, int iterations) {
        ensureBloomBuffers(iterations);
        if (bloomBuffers.isEmpty()) {
            return sourceTexture;
        }

        int currentTexture = sourceTexture;
        ShaderProgram downShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderHandsKawaseDown);
        ShaderProgram upShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderHandsKawaseUp);
        if (downShader == null || upShader == null) {
            return currentTexture;
        }

        for (int i = 0; i < iterations; i++) {
            Framebuffer dst = bloomBuffers.get(i);
            dst.setClearColor(0f, 0f, 0f, 0f);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseDown);
            RenderSystem.setShaderTexture(0, currentTexture);
            setHandsKawaseUniforms(downShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            drawFullscreenQuad();
            currentTexture = dst.getColorAttachment();
        }

        for (int i = iterations - 1; i >= 1; i--) {
            Framebuffer dst = bloomBuffers.get(i - 1);
            dst.setClearColor(0f, 0f, 0f, 0f);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseUp);
            RenderSystem.setShaderTexture(0, currentTexture);
            setHandsKawaseUniforms(upShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            setUniform(upShader, "color", 1.0f, 1.0f, 1.0f);
            drawFullscreenQuad();
            currentTexture = dst.getColorAttachment();
        }

        mc.getFramebuffer().beginWrite(true);
        return currentTexture;
    }

    private void ensureBloomBuffers(int iterations) {
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();

        if (bloomWidth != w || bloomHeight != h) {
            for (Framebuffer fb : bloomBuffers) {
                fb.delete();
            }
            bloomBuffers.clear();
            bloomWidth = w;
            bloomHeight = h;
        }

        while (bloomBuffers.size() > iterations) {
            int last = bloomBuffers.size() - 1;
            bloomBuffers.get(last).delete();
            bloomBuffers.remove(last);
        }

        for (int i = 0; i < iterations; i++) {
            int tw = Math.max(2, w >> (i + 1));
            int th = Math.max(2, h >> (i + 1));
            if (i >= bloomBuffers.size()) {
                Framebuffer fb = new SimpleFramebuffer(tw, th, false);
                setLinearFiltering(fb);
                bloomBuffers.add(fb);
                continue;
            }

            Framebuffer fb = bloomBuffers.get(i);
            if (fb.textureWidth != tw || fb.textureHeight != th) {
                fb.delete();
                fb = new SimpleFramebuffer(tw, th, false);
                setLinearFiltering(fb);
                bloomBuffers.set(i, fb);
            }
        }
    }

    private void setLinearFiltering(Framebuffer fb) {
        RenderSystem.bindTexture(fb.getColorAttachment());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.bindTexture(0);
    }

    private void setUniform(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }

    private void setHandsKawaseUniforms(ShaderProgram shader, int texWidth, int texHeight, float offset) {
        setUniform(shader, "uSize", Math.max(1, texWidth), Math.max(1, texHeight));
        setUniform(shader, "uOffset", offset, offset);
        setUniform(shader, "uHalfPixel", 0.5f / Math.max(1, texWidth), 0.5f / Math.max(1, texHeight));
    }

    private void drawFullscreenQuad() {
        float width = Math.max(mc.getWindow().getScaledWidth(), 1);
        float height = Math.max(mc.getWindow().getScaledHeight(), 1);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(0, height, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(width, height, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(width, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}

