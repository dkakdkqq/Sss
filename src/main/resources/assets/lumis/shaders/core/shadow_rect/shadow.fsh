#version 150

uniform vec2 Size;
uniform float Softness;
uniform float Radius;
uniform vec4 TopLeftColor;
uniform vec4 TopRightColor;
uniform vec4 BottomLeftColor;
uniform vec4 BottomRightColor;

in vec2 texCoord;
out vec4 fragColor;

float dstfn(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 pixel = texCoord * Size;
    vec2 centre = 0.5 * Size;

    // Билинейная интерполяция 4 цветов
    vec4 topColor = mix(TopLeftColor, TopRightColor, texCoord.x);
    vec4 bottomColor = mix(BottomLeftColor, BottomRightColor, texCoord.x);
    vec4 color = mix(topColor, bottomColor, texCoord.y);

    // Расчёт тени
    float dist = dstfn(centre - pixel, centre - Radius - Softness, Radius);
    float shadow = 1.0 - smoothstep(-Softness, Softness, dist);

    fragColor = vec4(color.rgb, color.a * shadow);
}