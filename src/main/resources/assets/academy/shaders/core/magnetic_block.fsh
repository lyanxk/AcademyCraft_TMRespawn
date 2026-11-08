#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 lightCoord;
in float fogDepth;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator * texture(Sampler2, lightCoord);
    float legacyAlpha = floor(clamp(color.a, 0.0, 1.0) * 4096.0) / 4096.0;
    if (roundEven(legacyAlpha * 255.0) <= 25.0) discard;
    // Original ordinary world fog is GL_LINEAR and does not change fragment alpha.
    float amount = FogEnd > FogStart ? clamp((fogDepth - FogStart) / (FogEnd - FogStart), 0.0, 1.0) : 0.0;
    fragColor = vec4(mix(color.rgb, FogColor.rgb, amount), color.a);
}
