package fun.lumis.api.utils.render.fonts.msdf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import fun.lumis.api.QClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.stream.Collectors;

public final class MsdfFont implements QClient {

    private final String name;
    private final AbstractTexture texture;
    private final float atlasWidth;
    private final float atlasHeight;
    private final float range;
    private final float lineHeight;
    private final float ascender;
    private final float descender;
    private final HashMap<Integer, MsdfGlyph> glyphs;
    private boolean filtered = false;

    private MsdfFont(String name, AbstractTexture texture, float atlasWidth, float atlasHeight,
                     float range, float lineHeight, float ascender, float descender,
                     HashMap<Integer, MsdfGlyph> glyphs) {
        this.name = name;
        this.texture = texture;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.range = range;
        this.lineHeight = lineHeight;
        this.ascender = ascender;
        this.descender = descender;
        this.glyphs = glyphs;
    }

    public void setFiltered() {
        if (!filtered) {
            texture.setFilter(true, false);
            filtered = true;
        }
    }

    public int getTextureId() {
        return texture.getGlId();
    }

    public float getAtlasWidth() {
        return atlasWidth;
    }

    public float getAtlasHeight() {
        return atlasHeight;
    }

    public float getRange() {
        return range;
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public float getBaselineHeight() {
        return lineHeight + descender;
    }

    public String getName() {
        return name;
    }

    public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, float size, String text,
                            float thickness, float x, float y, float z,
                            int red, int green, int blue, int alpha) {
        text = replaceSymbols(text);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length()) {
                i++;
                continue;
            }

            MsdfGlyph glyph = glyphs.get((int) c);
            if (glyph != null) {
                x += glyph.apply(matrix, consumer, size, x, y, z, red, green, blue, alpha) + thickness;
            }
        }
    }

    public float getWidth(String text, float size) {
        text = replaceSymbols(text);
        float width = 0.0f;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length()) {
                i++;
                continue;
            }

            MsdfGlyph glyph = glyphs.get((int) c);
            if (glyph != null) {
                width += glyph.getWidth(size);
            }
        }

        return width;
    }

    private static String replaceSymbols(String text) {
        if (text == null) return "";
        return text
                .replace("ᴀ", "A").replace("ʙ", "B").replace("ᴄ", "C")
                .replace("ᴅ", "D").replace("ᴇ", "E").replace("ғ", "F")
                .replace("ɢ", "G").replace("ʜ", "H").replace("ɪ", "I")
                .replace("ᴊ", "J").replace("ᴋ", "K").replace("ʟ", "L")
                .replace("ᴍ", "M").replace("ɴ", "N").replace("ᴏ", "O")
                .replace("ᴘ", "P").replace("ǫ", "Q").replace("ʀ", "R")
                .replace("ꜱ", "S").replace("ᴛ", "T").replace("ᴜ", "U")
                .replace("ᴠ", "V").replace("ᴡ", "W").replace("ʏ", "Y")
                .replace("ᴢ", "Z").replace("ꜰ", "F");
    }

    private static String readResource(Identifier identifier) {
        try {
            InputStream inputStream = mc.getResourceManager().open(identifier);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String result = reader.lines().collect(Collectors.joining("\n"));
            reader.close();
            inputStream.close();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + identifier, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "?";
        private Identifier dataIdentifier;
        private Identifier atlasIdentifier;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder data(String dataFileName) {
            this.dataIdentifier = Identifier.of("lumis", "fonts/msdf/" + dataFileName + "/font.json");
            return this;
        }

        public Builder atlas(String atlasFileName) {
            this.atlasIdentifier = Identifier.of("lumis", "fonts/msdf/" + atlasFileName + "/font.png");
            return this;
        }

        public MsdfFont build() {
            String json = readResource(dataIdentifier);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonObject atlasObj = root.getAsJsonObject("atlas");
            float atlasWidth = atlasObj.get("width").getAsFloat();
            float atlasHeight = atlasObj.get("height").getAsFloat();
            float range = atlasObj.get("distanceRange").getAsFloat();

            JsonObject metricsObj = root.getAsJsonObject("metrics");
            float lineHeight = metricsObj.get("lineHeight").getAsFloat();
            float ascender = metricsObj.get("ascender").getAsFloat();
            float descender = metricsObj.get("descender").getAsFloat();

            HashMap<Integer, MsdfGlyph> glyphs = new HashMap<>();
            JsonArray glyphsArray = root.getAsJsonArray("glyphs");

            for (JsonElement element : glyphsArray) {
                JsonObject glyphObj = element.getAsJsonObject();

                int unicode = glyphObj.get("unicode").getAsInt();
                float advance = glyphObj.get("advance").getAsFloat();

                float planeLeft = 0, planeTop = 0, planeRight = 0, planeBottom = 0;
                if (glyphObj.has("planeBounds") && !glyphObj.get("planeBounds").isJsonNull()) {
                    JsonObject plane = glyphObj.getAsJsonObject("planeBounds");
                    planeLeft = plane.get("left").getAsFloat();
                    planeTop = plane.get("top").getAsFloat();
                    planeRight = plane.get("right").getAsFloat();
                    planeBottom = plane.get("bottom").getAsFloat();
                }

                float atlasLeft = 0, atlasTop = 0, atlasRight = 0, atlasBottom = 0;
                if (glyphObj.has("atlasBounds") && !glyphObj.get("atlasBounds").isJsonNull()) {
                    JsonObject atlas = glyphObj.getAsJsonObject("atlasBounds");
                    atlasLeft = atlas.get("left").getAsFloat();
                    atlasTop = atlas.get("top").getAsFloat();
                    atlasRight = atlas.get("right").getAsFloat();
                    atlasBottom = atlas.get("bottom").getAsFloat();
                }

                MsdfGlyph glyph = new MsdfGlyph(
                        unicode, advance,
                        planeLeft, planeTop, planeRight, planeBottom,
                        atlasLeft, atlasTop, atlasRight, atlasBottom,
                        atlasWidth, atlasHeight
                );

                glyphs.put(unicode, glyph);
            }

            AbstractTexture texture = mc.getTextureManager().getTexture(atlasIdentifier);

            return new MsdfFont(name, texture, atlasWidth, atlasHeight, range,
                    lineHeight, ascender, descender, glyphs);
        }
    }
}
