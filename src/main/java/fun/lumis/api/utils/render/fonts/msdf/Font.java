package fun.lumis.api.utils.render.fonts.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import fun.lumis.api.QClient;
import fun.lumis.api.utils.render.ShaderUtils;

public class Font implements QClient {
    private static final char FORMATTING_CODE_PREFIX = '\u00A7';

    private final MsdfFont font;
    private final float size;

    public Font(MsdfFont font, float size) {
        this.font = font;
        this.size = size;
    }

    public Font(String name, float size) {
        this.font = MsdfFont.builder().atlas(name).data(name).build();
        this.size = size;
    }

    public void drawString(MatrixStack matrixStack, String text, double x, double y, int color) {
        draw(matrixStack, text, (float) x, (float) y, color);
    }

    public void drawString(MatrixStack matrixStack, String text, float x, float y, int color) {
        draw(matrixStack, text, x, y, color);
    }

    public void drawString(String text, float x, float y, int color) {
        MatrixStack stack = new MatrixStack();
        draw(stack, text, x, y, color);
    }

    public void drawCenteredString(MatrixStack matrixStack, String text, double x, double y, int color) {
        draw(matrixStack, text, (float) (x - getStringWidth(text) / 2.0), (float) y, color);
    }

    public void drawCenteredString(MatrixStack matrixStack, String text, float x, float y, int color) {
        draw(matrixStack, text, x - getStringWidth(text) / 2f, y, color);
    }

    public void drawRight(MatrixStack matrixStack, String text, double x, double y, int color) {
        draw(matrixStack, text, (float) (x - getStringWidth(text)), (float) y, color);
    }

    public void drawRight(MatrixStack matrixStack, String text, float x, float y, int color) {
        draw(matrixStack, text, x - getStringWidth(text), y, color);
    }

    public void draw(MatrixStack stack, String text, double x, double y, int color) {
        draw(stack, text, (float) x, (float) y, color);
    }

    public void draw(MatrixStack stack, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;

        float localSize = size * 0.5f;
        if (!hasDrawableGlyphs(text, localSize)) return;
        y -= 1.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.fontsMsdf);
        if (shader == null) return;

        setupShaderUniforms(shader, color);

        RenderSystem.setShaderTexture(0, font.getTextureId());
        font.setFiltered();

        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        font.applyGlyphs(matrix, buffer, localSize, text, 0,
                x, y + font.getBaselineHeight() * localSize, 0,
                255, 255, 255, 255);

        RenderSystem.setShader(ShaderUtils.fontsMsdf);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void drawGradientStringHorizontal(String text, float x, float y, int leftColor, int rightColor) {
        MatrixStack stack = new MatrixStack();
        drawGradientStringHorizontal(stack, text, x, y, leftColor, rightColor);
    }

    public void drawGradientStringHorizontal(MatrixStack stack, String text, float x, float y, int leftColor, int rightColor) {
        if (text == null || text.isEmpty()) return;

        float totalWidth = getStringWidth(text);
        float currentX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            float charWidth = getStringWidth(charStr);

            float progress = totalWidth > 0 ? (currentX - x) / totalWidth : 0;
            int color = interpolateColor(leftColor, rightColor, progress);

            draw(stack, charStr, currentX, y, color);
            currentX += charWidth;
        }
    }

    public void drawGradientStringHorizontal(MatrixStack stack, String text, float x, float y, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        if (text == null || text.isEmpty()) return;

        float totalWidth = getStringWidth(text);
        float currentX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            float charWidth = getStringWidth(charStr);

            float progress = totalWidth > 0 ? (currentX - x) / totalWidth : 0;

            int topColor = interpolateColor(topLeftColor, topRightColor, progress);
            int bottomColor = interpolateColor(bottomLeftColor, bottomRightColor, progress);
            int color = interpolateColor(topColor, bottomColor, 0.5f);

            draw(stack, charStr, currentX, y, color);
            currentX += charWidth;
        }
    }

    public void drawGradientStringVertical(MatrixStack stack, String text, float x, float y, int topColor, int bottomColor) {
        if (text == null || text.isEmpty()) return;
        int color = interpolateColor(topColor, bottomColor, 0.5f);
        draw(stack, text, x, y, color);
    }

    public void drawStringWithFade(MatrixStack stack, String text, float x, float y, float maxWidth, int color) {
        if (text == null || text.isEmpty()) return;
        if (maxWidth <= 1f) return;

        int originalAlpha = (color >>> 24) & 0xFF;
        if (originalAlpha == 0) originalAlpha = 255;
        if (originalAlpha <= 4) return;

        float localSize = size * 0.5f;
        y -= 1.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.fontsMsdf);
        if (shader == null) return;

        GlUniform textureSizeUniform = shader.getUniform("TextureSize");
        GlUniform rangeUniform = shader.getUniform("Range");
        GlUniform thicknessUniform = shader.getUniform("Thickness");
        GlUniform edgeStrengthUniform = shader.getUniform("EdgeStrength");
        GlUniform colorUniform = shader.getUniform("Color");
        GlUniform outlineUniform = shader.getUniform("Outline");
        GlUniform outlineThicknessUniform = shader.getUniform("OutlineThickness");
        GlUniform outlineColorUniform = shader.getUniform("OutlineColor");

        if (textureSizeUniform != null) textureSizeUniform.set(font.getAtlasWidth(), font.getAtlasHeight());
        if (rangeUniform != null) rangeUniform.set(font.getRange());
        if (thicknessUniform != null) thicknessUniform.set(0f);
        if (edgeStrengthUniform != null) edgeStrengthUniform.set(0.5f);
        if (outlineUniform != null) outlineUniform.set(0);
        if (outlineThicknessUniform != null) outlineThicknessUniform.set(0f);
        if (outlineColorUniform != null) outlineColorUniform.set(1f, 1f, 1f, 1f);

        RenderSystem.setShaderTexture(0, font.getTextureId());
        font.setFiltered();

        float currentX = x;
        float fadeZoneWidth = 25f;
        float fadeStartX = x + maxWidth - fadeZoneWidth;

        for (int i = 0; i < text.length(); i++) {
            String charStr = String.valueOf(text.charAt(i));
            float charWidth = getStringWidth(charStr);

            if (currentX > x + maxWidth && i > 0) {
                break;
            }

            int finalColor = color;
            if (currentX > fadeStartX) {
                float progressIntoFade = (currentX - fadeStartX) / fadeZoneWidth;
                progressIntoFade = Math.max(0.0f, Math.min(1.0f, progressIntoFade));

                float fadeFactor = (float) Math.cos(progressIntoFade * Math.PI / 2.0);

                int newAlpha = (int) (originalAlpha * fadeFactor);
                finalColor = (color & 0x00FFFFFF) | (newAlpha << 24);
            }

            if (((finalColor >>> 24) & 0xFF) > 4) {
                float[] rgba = extractRgba(finalColor);
                if (colorUniform != null) colorUniform.set(rgba[0], rgba[1], rgba[2], rgba[3]);

                Matrix4f matrix = stack.peek().getPositionMatrix();
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                font.applyGlyphs(matrix, buffer, localSize, charStr, 0,
                        currentX, y + font.getBaselineHeight() * localSize, 0,
                        255, 255, 255, 255);

                RenderSystem.setShader(ShaderUtils.fontsMsdf);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }

            currentX += charWidth;
        }

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void drawAnimatedGradientStringHorizontal(String text, float x, float y, int leftColor, int rightColor, float speed) {
        MatrixStack stack = new MatrixStack();
        drawAnimatedGradientStringHorizontal(stack, text, x, y, leftColor, rightColor, speed, 1.15f);
    }

    public void drawAnimatedGradientStringHorizontal(MatrixStack stack, String text, float x, float y, int leftColor, int rightColor, float speed) {
        drawAnimatedGradientStringHorizontal(stack, text, x, y, leftColor, rightColor, speed, 1.15f);
    }

    public void drawAnimatedGradientStringHorizontal(String text, float x, float y, int leftColor, int rightColor, float speed, float waveScale) {
        MatrixStack stack = new MatrixStack();
        drawAnimatedGradientStringHorizontal(stack, text, x, y, leftColor, rightColor, speed, waveScale);
    }

    public void drawAnimatedGradientStringHorizontal(MatrixStack stack, String text, float x, float y, int leftColor, int rightColor, float speed, float waveScale) {
        if (text == null || text.isEmpty()) return;

        float totalWidth = getStringWidth(text);
        float currentX = x;
        double timeOffset = (System.currentTimeMillis() * 0.001d * Math.max(0.01f, speed)) % 2.0d;
        float safeWaveScale = Math.max(0.01f, waveScale);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            float charWidth = getStringWidth(charStr);

            float baseProgress = totalWidth > 0 ? (currentX - x) / totalWidth : 0f;
            float animatedProgress = pingPong01(baseProgress * safeWaveScale + (float) timeOffset);
            int color = interpolateColor(leftColor, rightColor, animatedProgress);

            draw(stack, charStr, currentX, y, color);
            currentX += charWidth;
        }
    }

    public void drawStringWithOutline(MatrixStack stack, String text, float x, float y, int color, int outlineColor) {
        if (text == null || text.isEmpty()) return;

        draw(stack, text, x - 1, y, outlineColor);
        draw(stack, text, x + 1, y, outlineColor);
        draw(stack, text, x, y - 1, outlineColor);
        draw(stack, text, x, y + 1, outlineColor);
        draw(stack, text, x, y, color);
    }

    public void drawStringWithShadow(MatrixStack stack, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;

        int shadowColor = 0x55000000;
        draw(stack, text, x + 1, y + 1, shadowColor);
        draw(stack, text, x, y, color);
    }

    public void drawParagraph(MatrixStack stack, String text, double x, double y, int defaultColor) {
        drawParagraph(stack, text, (float) x, (float) y, defaultColor);
    }

    public void drawParagraph(MatrixStack stack, String text, float x, float y, int defaultColor) {
        if (text == null || text.isEmpty()) return;

        float localSize = size * 0.5f;
        y -= 1.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.fontsMsdf);
        if (shader == null) return;

        GlUniform textureSizeUniform = shader.getUniform("TextureSize");
        GlUniform rangeUniform = shader.getUniform("Range");
        GlUniform thicknessUniform = shader.getUniform("Thickness");
        GlUniform edgeStrengthUniform = shader.getUniform("EdgeStrength");
        GlUniform colorUniform = shader.getUniform("Color");

        if (textureSizeUniform != null) textureSizeUniform.set(font.getAtlasWidth(), font.getAtlasHeight());
        if (rangeUniform != null) rangeUniform.set(font.getRange());
        if (thicknessUniform != null) thicknessUniform.set(0f);
        if (edgeStrengthUniform != null) edgeStrengthUniform.set(0.5f);

        RenderSystem.setShaderTexture(0, font.getTextureId());
        font.setFiltered();

        float currentX = x;
        int currentColor = defaultColor;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == FORMATTING_CODE_PREFIX && i + 1 < text.length()) {
                if (!segment.isEmpty()) {
                    drawSegment(stack, colorUniform, segment.toString(), currentX,
                            y + font.getBaselineHeight() * localSize, localSize, currentColor);
                    currentX += getStringWidth(segment.toString());
                    segment.setLength(0);
                }

                char code = text.charAt(i + 1);
                int newColor = getColorFromCode(code, defaultColor);
                if (newColor != -1) {
                    currentColor = newColor;
                }
                i++;
            } else {
                segment.append(c);
            }
        }

        if (!segment.isEmpty()) {
            drawSegment(stack, colorUniform, segment.toString(), currentX,
                    y + font.getBaselineHeight() * localSize, localSize, currentColor);
        }

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawSegment(MatrixStack stack, GlUniform colorUniform, String text, float x, float y, float size, int color) {
        if (!hasDrawableGlyphs(text, size)) return;

        float[] rgba = extractRgba(color);
        if (colorUniform != null) colorUniform.set(rgba[0], rgba[1], rgba[2], rgba[3]);

        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        font.applyGlyphs(matrix, buffer, size, text, 0, x, y, 0, 255, 255, 255, 255);

        RenderSystem.setShader(ShaderUtils.fontsMsdf);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private boolean hasDrawableGlyphs(String text, float renderSize) {
        return text != null && !text.isEmpty() && font.getWidth(text, renderSize) > 0.0f;
    }

    private void setupShaderUniforms(ShaderProgram shader, int color) {
        GlUniform textureSizeUniform = shader.getUniform("TextureSize");
        GlUniform rangeUniform = shader.getUniform("Range");
        GlUniform thicknessUniform = shader.getUniform("Thickness");
        GlUniform edgeStrengthUniform = shader.getUniform("EdgeStrength");
        GlUniform colorUniform = shader.getUniform("Color");
        GlUniform outlineUniform = shader.getUniform("Outline");
        GlUniform outlineThicknessUniform = shader.getUniform("OutlineThickness");
        GlUniform outlineColorUniform = shader.getUniform("OutlineColor");

        if (textureSizeUniform != null) textureSizeUniform.set(font.getAtlasWidth(), font.getAtlasHeight());
        if (rangeUniform != null) rangeUniform.set(font.getRange());
        if (thicknessUniform != null) thicknessUniform.set(0f);
        if (edgeStrengthUniform != null) edgeStrengthUniform.set(0.5f);
        if (outlineUniform != null) outlineUniform.set(0);
        if (outlineThicknessUniform != null) outlineThicknessUniform.set(0f);
        if (outlineColorUniform != null) outlineColorUniform.set(0f, 0f, 0f, 1f);

        float[] rgba = extractRgba(color);
        if (colorUniform != null) colorUniform.set(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private int getColorFromCode(char code, int defaultColor) {
        int alpha = (defaultColor >> 24) & 0xFF;
        if (alpha == 0) alpha = 255;

        return switch (code) {
            case '0' -> (alpha << 24) | 0x000000;
            case '1' -> (alpha << 24) | 0x0000AA;
            case '2' -> (alpha << 24) | 0x00AA00;
            case '3' -> (alpha << 24) | 0x00AAAA;
            case '4' -> (alpha << 24) | 0xAA0000;
            case '5' -> (alpha << 24) | 0xAA00AA;
            case '6' -> (alpha << 24) | 0xFFAA00;
            case '7' -> (alpha << 24) | 0xAAAAAA;
            case '8' -> (alpha << 24) | 0x555555;
            case '9' -> (alpha << 24) | 0x5555FF;
            case 'a', 'A' -> (alpha << 24) | 0x55FF55;
            case 'b', 'B' -> (alpha << 24) | 0x55FFFF;
            case 'c', 'C' -> (alpha << 24) | 0xFF5555;
            case 'd', 'D' -> (alpha << 24) | 0xFF55FF;
            case 'e', 'E' -> (alpha << 24) | 0xFFFF55;
            case 'f', 'F' -> (alpha << 24) | 0xFFFFFF;
            case 'r', 'R' -> defaultColor;
            default -> -1;
        };
    }

    private float[] extractRgba(int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;
        return new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
    }

    public static int interpolateColor(int color1, int color2, float progress) {
        progress = Math.max(0f, Math.min(1f, progress));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        if (a1 == 0) a1 = 255;
        if (a2 == 0) a2 = 255;

        int a = (int) (a1 + (a2 - a1) * progress);
        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float pingPong01(float value) {
        float wrapped = value % 2.0f;
        if (wrapped < 0.0f) wrapped += 2.0f;
        return wrapped > 1.0f ? 2.0f - wrapped : wrapped;
    }

    public float getStringWidth(String text) {
        if (text == null) return 0;
        return font.getWidth(stripFormattingCodes(text), size) / 2f;
    }

    public float getWidth(String text) {
        return getStringWidth(text);
    }

    public float getHeight() {
        return size;
    }

    public float getFontHeight() {
        return size;
    }

    public MsdfFont getFont() {
        return font;
    }

    public float getSize() {
        return size;
    }

    private String stripFormattingCodes(String text) {
        if (text == null || text.indexOf(FORMATTING_CODE_PREFIX) < 0) {
            return text;
        }

        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == FORMATTING_CODE_PREFIX && i + 1 < text.length()) {
                i++;
                continue;
            }
            clean.append(current);
        }
        return clean.toString();
    }
}
