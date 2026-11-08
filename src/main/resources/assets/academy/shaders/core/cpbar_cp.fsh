#version 150

in vec2 texCoord0;
in vec4 vertexColor;
uniform vec4 ColorModulator;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
out vec4 fragColor;

// Original cpbar_cp.frag: the ability emblem is an alpha cutout, not an extra icon.
void main() {
    vec2 temp = (texCoord0 * vec2(964, 147) - vec2(857, 43)) / vec2(65, 65);
    float maskColor;
    if (temp.s < 0 || temp.s > 1 || temp.t < 0 || temp.t > 1) maskColor = 1;
    else maskColor = 1 - texture(Sampler1, temp).a;
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vertexColor * ColorModulator * vec4(texColor.rgb, texColor.a * maskColor);
}
