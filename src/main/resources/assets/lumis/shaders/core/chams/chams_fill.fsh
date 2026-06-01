#version 150

uniform float time;
uniform float speedX;
uniform float speedY;
uniform float scale;
uniform float density;
uniform float glowStrength;

in vec2 TexCoord;
in vec4 FragColor;
out vec4 OutColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i + vec2(0.0, 0.0));
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
    float densityMix = clamp((density - 0.5) / 2.5, 0.0, 1.0);
    vec2 flow = TexCoord * scale;
    vec2 drift = vec2(time * speedX, time * speedY);

    vec2 warp = vec2(
        fbm(flow * 0.85 + drift * 0.55 + vec2(0.0, 4.1)),
        fbm(flow * 0.80 - drift * 0.42 + vec2(3.7, 1.8))
    );
    vec2 q = flow + (warp - 0.5) * mix(1.3, 2.5, densityMix);

    float mist = fbm(q * 0.70 - drift * 0.18 + vec2(4.2, 8.1));

    float diagonal1 = q.x * 1.02 + q.y * 0.38 + time * (speedX * 0.80 + speedY * 0.25);
    float diagonal2 = q.x * -0.58 + q.y * 1.10 - time * (speedY * 0.90);

    float band1 = 1.0 - abs(sin(diagonal1 * 1.85 + mist * 4.8));
    float band2 = 1.0 - abs(sin(diagonal2 * 1.45 - mist * 3.2));
    band1 = pow(clamp(band1, 0.0, 1.0), mix(3.3, 7.0, densityMix));
    band2 = pow(clamp(band2, 0.0, 1.0), mix(3.8, 7.8, densityMix));

    float veins = ridged(q * 1.90 + vec2(mist * 2.7, mist * 1.9) - drift * 0.55);
    veins = pow(clamp(veins, 0.0, 1.0), mix(2.0, 3.8, densityMix));

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
    float brightVeins = pow(clamp(max(veins, band1), 0.0, 1.0), 1.35);
    float glow = (brightVeins * 0.95 + micro * 0.55) * glowStrength;

    vec3 rgb = FragColor.rgb * (0.24 + mist * 0.20 + core * 0.88 + glow * 0.52);
    float alpha = FragColor.a * clamp(0.24 + core * 0.74 + glow * 0.20, 0.0, 1.0);

    if (alpha <= 0.002) {
        discard;
    }

    OutColor = vec4(rgb, alpha);
}
