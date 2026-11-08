#version 150

in vec2 texCoord0;
in vec4 vertexColor;
uniform vec4 ColorModulator;
uniform float TexOffset;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
out vec4 fragColor;

void main() {
    vec4 colorTex = vertexColor * ColorModulator * texture(Sampler0, texCoord0 + vec2(TexOffset, 0));
    float colorMask = texture(Sampler1, texCoord0).a;
    fragColor = vec4(colorTex.rgb, colorMask * colorTex.a);
}
