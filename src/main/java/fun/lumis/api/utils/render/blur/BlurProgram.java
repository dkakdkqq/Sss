package fun.lumis.api.utils.render.blur;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import org.lwjgl.opengl.GL30;
import fun.lumis.api.QClient;
import fun.lumis.api.utils.render.ShaderUtils;

public class BlurProgram implements QClient {

    private static BlurProgram instance;

    @Getter
    private static Framebuffer buffer1;
    @Getter
    private static Framebuffer buffer2;

    private int lastWidth = -1;
    private int lastHeight = -1;
    private long lastUpdateTime = 0;
    private boolean requestedThisFrame = true;

    @Setter
    private float blurOffset = 1.0f;

    private final int iterations = 4;

    public static BlurProgram getInstance() {
        if (instance == null) {
            instance = new BlurProgram();
        }
        return instance;
    }

    public void beginFrame() {
        boolean shouldDraw = requestedThisFrame;
        requestedThisFrame = false;
        if (!shouldDraw) {
            return;
        }
        draw();
    }

    public void request() {
        requestedThisFrame = true;
    }

    private void draw() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < 16) {
            return;
        }
        lastUpdateTime = currentTime;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (buffer1 == null || buffer2 == null || lastWidth != width || lastHeight != height) {
            if (buffer1 != null) {
                buffer1.delete();
            }
            if (buffer2 != null) {
                buffer2.delete();
            }
            buffer1 = new SimpleFramebuffer(width, height, false);
            buffer2 = new SimpleFramebuffer(width, height, false);

            setLinearFiltering(buffer1);
            setLinearFiltering(buffer2);

            lastWidth = width;
            lastHeight = height;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram kawaseDown = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.kawaseDown);
        ShaderProgram kawaseUp = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.kawaseUp);

        buffer1.setClearColor(0, 0, 0, 0);
        buffer1.clear();
        buffer1.beginWrite(true);

        RenderSystem.setShader(ShaderUtils.kawaseDown);
        mc.getFramebuffer().beginRead();
        RenderSystem.setShaderTexture(0, mc.getFramebuffer().getColorAttachment());

        setKawaseUniforms(kawaseDown, width, height);
        drawQuad(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());

        mc.getFramebuffer().endRead();
        buffer1.endWrite();

        Framebuffer[] buffers = new Framebuffer[]{buffer1, buffer2};

        for (int i = 1; i < iterations; i++) {
            int srcIndex = (i + 1) % 2;
            int dstIndex = i % 2;

            Framebuffer src = buffers[srcIndex];
            Framebuffer dst = buffers[dstIndex];

            dst.setClearColor(0, 0, 0, 0);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.kawaseDown);
            src.beginRead();
            RenderSystem.setShaderTexture(0, src.getColorAttachment());

            setKawaseUniforms(kawaseDown, src.textureWidth, src.textureHeight);
            drawQuad(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());

            src.endRead();
            dst.endWrite();
        }

        for (int i = 0; i < iterations; i++) {
            int srcIndex = i % 2;
            int dstIndex = (i + 1) % 2;

            Framebuffer src = buffers[srcIndex];
            Framebuffer dst = buffers[dstIndex];

            dst.setClearColor(0, 0, 0, 0);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.kawaseUp);
            src.beginRead();
            RenderSystem.setShaderTexture(0, src.getColorAttachment());

            setKawaseUniforms(kawaseUp, src.textureWidth, src.textureHeight);
            drawQuad(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());

            src.endRead();
            dst.endWrite();
        }

        RenderSystem.disableBlend();
        mc.getFramebuffer().beginWrite(true);
        RenderSystem.setShaderTexture(0, 0);
    }

    private void setLinearFiltering(Framebuffer framebuffer) {
        RenderSystem.bindTexture(framebuffer.getColorAttachment());
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        RenderSystem.bindTexture(0);
    }

    private void setKawaseUniforms(ShaderProgram shader, int texWidth, int texHeight) {
        GlUniform resolutionUniform = shader.getUniform("Resolution");
        GlUniform offsetUniform = shader.getUniform("Offset");
        GlUniform saturationUniform = shader.getUniform("Saturation");
        GlUniform tintIntensityUniform = shader.getUniform("TintIntensity");
        GlUniform tintColorUniform = shader.getUniform("TintColor");

        if (resolutionUniform != null) resolutionUniform.set(1.0f / texWidth, 1.0f / texHeight);
        if (offsetUniform != null) offsetUniform.set(blurOffset);
        if (saturationUniform != null) saturationUniform.set(1.0f);
        if (tintIntensityUniform != null) tintIntensityUniform.set(0.0f);
        if (tintColorUniform != null) tintColorUniform.set(1.0f, 1.0f, 1.0f);
    }

    private void drawQuad(float width, float height) {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        builder.vertex(0, height, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        builder.vertex(width, height, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        builder.vertex(width, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public static int getTexture() {
        getInstance().request();
        return buffer1 != null ? buffer1.getColorAttachment() : 0;
    }
}
