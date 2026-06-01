package fun.lumis.api.utils.render;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import fun.lumis.api.QClient;

@UtilityClass
public class ShaderUtils implements QClient {

    public final ShaderProgramKey roundedRect = register("rect", "rounded_rect", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey roundedRectOutline = register("rect", "rounded_rect_outline", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey ringArc = register("ring_arc", "ring_arc", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey roundedTexture = register("texture", "texture_rect", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey liquidGlass = register("liquidglass", "liquid", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey kawaseDown = register("kawase_down", "kawase_down", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey kawaseUp = register("kawase_up", "kawase_up", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey gradientRect = register("gradient_rect", "gradient", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shadowRect = register("shadow_rect", "shadow", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey shadow6Rect = register("shadow6", "shadow", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey fontsMsdf = register("fonts", "fonts", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey face = register("face", "face", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey gradient6Rect = register("gradient6", "gradient", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey sonar = register("sonar", "sonar", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey scanEffect = register("sonar", "scan_effect", VertexFormats.POSITION_TEXTURE);
    public final ShaderProgramKey blockOverlay = register("blockoverlay", "block_overlay", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey chamsFill = register("chams", "chams_fill", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderHandsMaskDiff = register("hands", "hands_mask_diff", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderHandsOverlay = register("hands", "hands_overlay", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderHandsGlow = register("hands", "hands_glow", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderHandsKawaseDown = register("hands", "hands_kawase_down", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderHandsKawaseUp = register("hands", "hands_kawase_up", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderEspGlow = register("shaderesp", "glow", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shaderEspFill = register("shaderesp", "fill", VertexFormats.POSITION_TEXTURE_COLOR);

    private ShaderProgramKey register(String shaderNamePackage, String shaderName, VertexFormat vertexFormat) {
        return new ShaderProgramKey(Identifier.of("lumis", "core/" + shaderNamePackage + "/" + shaderName), vertexFormat, Defines.EMPTY);
    }
}
