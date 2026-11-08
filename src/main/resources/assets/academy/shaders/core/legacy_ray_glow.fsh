#version 150
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vertexColor * texture(Sampler0, texCoord0);
    // Preserve the old GREATER alpha test, including its fixed-point comparison.
    // NVIDIA compatibility path truncates to 12 fractional bits before its RGBA8 alpha comparison.
    float legacyAlpha = floor(clamp(fragColor.a, 0.0, 1.0) * 4096.0) / 4096.0;
    if (roundEven(legacyAlpha * 255.0) <= 13.0) discard;
}
