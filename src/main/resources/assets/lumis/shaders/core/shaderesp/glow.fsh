#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec3 color;
uniform vec3 color2;
uniform float exposure;
uniform float time;
uniform float animate;

in vec2 TexCoord;
out vec4 OutColor;

void main() {
    vec2 uv = TexCoord;
    vec4 bloom = texture(Sampler0, uv);
    vec4 mask = texture(Sampler1, uv);

    float maskValue = max(max(mask.r, mask.g), max(mask.b, mask.a));
    float bloomValue = clamp(bloom.a, 0.0, 1.0);
    float edgeMask = smoothstep(0.03, 0.24, maskValue);
    float ring = max(bloomValue - edgeMask * 0.24, 0.0);
    ring = smoothstep(0.01, 0.19, ring);
    ring = pow(clamp(ring, 0.0, 1.0), 1.15);

    float pulse = 1.0;
    if (animate > 0.5) {
        pulse = 0.96 + 0.04 * sin(time * 1.35);
    }

    float intensity = clamp(ring * exposure * pulse, 0.0, 0.38);
    if (intensity <= 0.001) discard;

    vec3 grad = mix(color, color2, uv.y);
    OutColor = vec4(grad, intensity);
}
