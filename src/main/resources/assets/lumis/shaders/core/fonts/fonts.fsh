#version 150

uniform sampler2D Sampler0;
uniform vec2 TextureSize;
uniform float Range;
uniform float EdgeStrength;
uniform float Thickness;
uniform vec4 Color;
uniform int Outline;
uniform float OutlineThickness;
uniform vec4 OutlineColor;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord);

    float dx = dFdx(texCoord.x) * TextureSize.x;
    float dy = dFdy(texCoord.y) * TextureSize.y;
    float toPixels = Range * inversesqrt(dx * dx + dy * dy);

    float sigDist = median(texColor.r, texColor.g, texColor.b) - 0.5 + Thickness;

    float alpha = smoothstep(-EdgeStrength, EdgeStrength, sigDist * toPixels);

    if (Outline == 1) {
        float outlineAlpha = smoothstep(-EdgeStrength, EdgeStrength, (sigDist + OutlineThickness) * toPixels) - alpha;
        float finalAlpha = alpha * Color.a + outlineAlpha * Color.a;
        fragColor = vec4(mix(OutlineColor.rgb, Color.rgb, alpha), finalAlpha);
        return;
    }

    fragColor = vec4(Color.rgb, Color.a * alpha);
}