#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec3 color;
uniform float alpha;
uniform float time;

in vec2 TexCoord;
out vec4 OutColor;

float max4(vec4 v) {
    return max(max(v.r, v.g), max(v.b, v.a));
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec2(13.1, 7.7);
        amplitude *= 0.5;
    }
    return value;
}

float ridged(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; i++) {
        float r = 1.0 - abs(noise(p) * 2.0 - 1.0);
        value += r * amplitude;
        p = p * 2.15 + vec2(4.7, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec2 uv = TexCoord;
    float raw = max4(texture(Sampler0, uv));
    float blur = texture(Sampler1, uv).a;

    float body = smoothstep(0.010, 0.070, raw);
    body = pow(clamp(body, 0.0, 1.0), 0.25);
    float softEdge = smoothstep(0.16, 0.40, blur) * (1.0 - body) * 0.12;
    float inside = clamp(body + softEdge, 0.0, 1.0);
    if (inside <= 0.001) {
        discard;
    }

    vec2 flow = uv * 1.35;
    vec2 drift = vec2(time * 0.22, time * 0.15);

    vec2 warp = vec2(
        fbm(flow * 0.85 + drift * 0.55 + vec2(0.0, 4.1)),
        fbm(flow * 0.80 - drift * 0.42 + vec2(3.7, 1.8))
    );
    vec2 q = flow + (warp - 0.5) * 1.7;

    float mist = fbm(q * 0.70 - drift * 0.18 + vec2(4.2, 8.1));
    float diagonal1 = q.x * 1.02 + q.y * 0.38 + time * 0.21;
    float diagonal2 = q.x * -0.58 + q.y * 1.10 - time * 0.13;

    float band1 = 1.0 - abs(sin(diagonal1 * 1.85 + mist * 4.8));
    float band2 = 1.0 - abs(sin(diagonal2 * 1.45 - mist * 3.2));
    band1 = pow(clamp(band1, 0.0, 1.0), 4.6);
    band2 = pow(clamp(band2, 0.0, 1.0), 5.1);

    float veins = ridged(q * 1.90 + vec2(mist * 2.7, mist * 1.9) - drift * 0.55);
    veins = pow(clamp(veins, 0.0, 1.0), 2.6);

    float micro = ridged(q * 3.6 - vec2(7.1, 2.6) + drift * 0.35);
    micro = pow(clamp(micro, 0.0, 1.0), 5.8);

    float energy = clamp(
        mist * 0.24 +
        band1 * 0.70 +
        band2 * 0.40 +
        veins * 0.84 +
        micro * 0.28,
        0.0, 1.0
    );

    float core = smoothstep(0.16, 0.98, energy);
    float glow = pow(clamp(max(veins, band1), 0.0, 1.0), 1.35) * 0.55 + micro * 0.22;

    vec3 rgb = color * (0.30 + mist * 0.18 + core * 0.82 + glow * 0.45);
    float outAlpha = clamp(alpha * inside * (0.34 + core * 0.66 + glow * 0.18), 0.0, 1.0);

    if (outAlpha <= 0.001) {
        discard;
    }

    OutColor = vec4(rgb, outAlpha);
}
