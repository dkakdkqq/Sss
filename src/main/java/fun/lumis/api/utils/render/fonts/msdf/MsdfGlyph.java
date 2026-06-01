package fun.lumis.api.utils.render.fonts.msdf;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

public final class MsdfGlyph {

    private final int code;
    private final float minU;
    private final float maxU;
    private final float minV;
    private final float maxV;
    private final float advance;
    private final float topPosition;
    private final float width;
    private final float height;

    public MsdfGlyph(int unicode, float advance,
                     float planeLeft, float planeTop, float planeRight, float planeBottom,
                     float atlasLeft, float atlasTop, float atlasRight, float atlasBottom,
                     float atlasWidth, float atlasHeight) {
        this.code = unicode;
        this.advance = advance;

        if (atlasLeft != 0 || atlasRight != 0 || atlasTop != 0 || atlasBottom != 0) {
            this.minU = atlasLeft / atlasWidth;
            this.maxU = atlasRight / atlasWidth;
            this.minV = 1.0f - atlasTop / atlasHeight;
            this.maxV = 1.0f - atlasBottom / atlasHeight;
        } else {
            this.minU = 0.0f;
            this.maxU = 0.0f;
            this.minV = 0.0f;
            this.maxV = 0.0f;
        }

        if (planeLeft != 0 || planeRight != 0 || planeTop != 0 || planeBottom != 0) {
            this.width = planeRight - planeLeft;
            this.height = planeTop - planeBottom;
            this.topPosition = planeTop;
        } else {
            this.width = 0.0f;
            this.height = 0.0f;
            this.topPosition = 0.0f;
        }
    }

    public float apply(Matrix4f matrix, VertexConsumer consumer, float size,
                       float x, float y, float z,
                       int red, int green, int blue, int alpha) {
        y -= this.topPosition * size;
        y -= 1f;

        float w = this.width * size;
        float h = this.height * size;

        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .texture(this.minU, this.minV);

        consumer.vertex(matrix, x, y + h, z)
                .color(red, green, blue, alpha)
                .texture(this.minU, this.maxV);

        consumer.vertex(matrix, x + w, y + h, z)
                .color(red, green, blue, alpha)
                .texture(this.maxU, this.maxV);

        consumer.vertex(matrix, x + w, y, z)
                .color(red, green, blue, alpha)
                .texture(this.maxU, this.minV);

        return this.width * (size - 1) + (Character.isSpaceChar(code) ? this.advance * size : 0);
    }

    public float getWidth(float size) {
        return this.width * (size - 1) + (Character.isSpaceChar(code) ? this.advance * size : 0);
    }

    public int getCharCode() {
        return code;
    }
}