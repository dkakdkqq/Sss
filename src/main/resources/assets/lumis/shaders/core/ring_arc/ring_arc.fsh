#version 150

#moj_import <lumis:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform float Radius;
uniform float Thickness;
uniform float StartAngle;
uniform float EndAngle;
uniform float Smoothness;
uniform vec4 ColorModulator;

out vec4 OutColor;

void main() {
    vec2 center = Size * 0.5;
    vec2 p = center - (FragCoord * Size);

    float dist = length(p);
    float outer = Radius;
    float inner = max(0.0, Radius - Thickness);
    float ringOuter = 1.0 - smoothstep(outer - Smoothness, outer, dist);
    float ringInner = smoothstep(inner, inner + Smoothness, dist);
    float ring = ringOuter * ringInner;

    float angle = atan(p.y, p.x);
    if (angle < 0.0) angle += 6.28318530718;
    float a = angle;
    if (a < StartAngle) a += 6.28318530718;
    float inArc = step(a, EndAngle);

    float alpha = ring * inArc;
    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);

    if (finalColor.a == 0.0) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
