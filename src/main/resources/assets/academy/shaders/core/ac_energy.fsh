#version 150

in vec4 vertexColor;
in float worldPhase;

uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    float pulse = 0.78 + 0.22 * sin(worldPhase * 3.0 + GameTime * 1800.0);
    vec4 color = vertexColor * ColorModulator;
    color.rgb *= 1.15 + pulse * 0.35;
    color.a *= pulse;
    if (color.a < 0.01) discard;
    fragColor = color;
}
