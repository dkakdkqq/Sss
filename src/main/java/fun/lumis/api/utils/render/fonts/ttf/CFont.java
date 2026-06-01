package fun.lumis.api.utils.render.fonts.ttf;

import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class CFont {
    protected static final int IMG_SIZE = 512;
    protected CharData[] charData = new CharData[1104];
    @Getter
    protected Font font;
    @Getter
    protected boolean antiAlias;
    @Getter
    protected boolean fractionalMetrics;
    protected int fontHeight = -1;
    protected int charOffset = 0;
    @Getter
    protected Identifier textureId;
    @Getter
    protected int glTextureId;
    private static int textureCounter = 0;

    public CFont(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
        this.setupTexture(font, antiAlias, fractionalMetrics, this.charData);
    }

    protected void setupTexture(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        BufferedImage img = this.generateFontImage(font, antiAlias, fractionalMetrics, chars);
        try {
            NativeImage nativeImage = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    nativeImage.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            this.glTextureId = texture.getGlId();
            String name = "cfont_" + textureCounter++;
            this.textureId = Identifier.of("customfont", name);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.textureId, texture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected BufferedImage generateFontImage(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        BufferedImage bufferedImage = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) bufferedImage.getGraphics();
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 0));
        g.fillRect(0, 0, IMG_SIZE, IMG_SIZE);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        FontMetrics fontMetrics = g.getFontMetrics();
        int charHeight = 0;
        int positionX = 0;
        int positionY = 1;
        for (int i = 0; i < chars.length; ++i) {
            char ch = (char) i;
            if ((ch <= '\u040f' || ch >= '\u0450') && ch >= '\u0100') continue;
            CharData charData = new CharData();
            Rectangle2D dimensions = fontMetrics.getStringBounds(String.valueOf(ch), g);
            charData.width = dimensions.getBounds().width + 8;
            charData.height = dimensions.getBounds().height;
            if (positionX + charData.width >= IMG_SIZE) {
                positionX = 0;
                positionY += charHeight;
                charHeight = 0;
            }
            if (charData.height > charHeight) {
                charHeight = charData.height;
            }
            charData.storedX = positionX;
            charData.storedY = positionY;
            if (charData.height > this.fontHeight) {
                this.fontHeight = charData.height;
            }
            chars[i] = charData;
            g.drawString(String.valueOf(ch), positionX + 2, positionY + fontMetrics.getAscent());
            positionX += charData.width;
        }
        return bufferedImage;
    }

    public void drawChar(CharData[] chars, char c, float x, float y, Matrix4f matrix, BufferBuilder buffer) {
        try {
            if (chars[c] == null) return;
            this.drawQuad(x, y, chars[c].width, chars[c].height, chars[c].storedX, chars[c].storedY, chars[c].width, chars[c].height, matrix, buffer);
        } catch (Exception ignored) {
        }
    }

    protected void drawQuad(float x, float y, float width, float height, float srcX, float srcY, float srcWidth, float srcHeight, Matrix4f matrix, BufferBuilder buffer) {
        float renderSRCX = srcX / IMG_SIZE;
        float renderSRCY = srcY / IMG_SIZE;
        float renderSRCWidth = srcWidth / IMG_SIZE;
        float renderSRCHeight = srcHeight / IMG_SIZE;

        buffer.vertex(matrix, x + width, y, 0).texture(renderSRCX + renderSRCWidth, renderSRCY);
        buffer.vertex(matrix, x, y, 0).texture(renderSRCX, renderSRCY);
        buffer.vertex(matrix, x, y + height, 0).texture(renderSRCX, renderSRCY + renderSRCHeight);
        buffer.vertex(matrix, x, y + height, 0).texture(renderSRCX, renderSRCY + renderSRCHeight);
        buffer.vertex(matrix, x + width, y + height, 0).texture(renderSRCX + renderSRCWidth, renderSRCY + renderSRCHeight);
        buffer.vertex(matrix, x + width, y, 0).texture(renderSRCX + renderSRCWidth, renderSRCY);
    }

    public int getStringHeight(String text) {
        return this.getFontHeight();
    }

    public int getFontHeight() {
        return (this.fontHeight - 8) / 2;
    }

    public int getStringWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            if (c >= this.charData.length || this.charData[c] == null) continue;
            width += this.charData[c].width - 8 + this.charOffset;
        }
        return width / 2;
    }

    public void setAntiAlias(boolean antiAlias) {
        if (this.antiAlias != antiAlias) {
            this.antiAlias = antiAlias;
            this.setupTexture(this.font, antiAlias, this.fractionalMetrics, this.charData);
        }
    }

    public void setFractionalMetrics(boolean fractionalMetrics) {
        if (this.fractionalMetrics != fractionalMetrics) {
            this.fractionalMetrics = fractionalMetrics;
            this.setupTexture(this.font, this.antiAlias, fractionalMetrics, this.charData);
        }
    }

    public void setFont(Font font) {
        this.font = font;
        this.setupTexture(font, this.antiAlias, this.fractionalMetrics, this.charData);
    }

    protected static class CharData {
        public int width;
        public int height;
        public int storedX;
        public int storedY;

        protected CharData() {
        }
    }
}