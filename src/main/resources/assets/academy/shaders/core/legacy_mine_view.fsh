#version 150
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vertexColor * texture(Sampler0, texCoord0);
    // Original world alpha test: GREATER .1, using the measured RGBA8 compatibility conversion.
    float legacyAlpha = floor(clamp(fragColor.a, 0.0, 1.0) * 4096.0) / 4096.0;
    if (roundEven(legacyAlpha * 255.0) <= 25.0) discard;
}
