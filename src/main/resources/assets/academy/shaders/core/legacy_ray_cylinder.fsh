#version 150
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vertexColor;
    // RGBA8 compatibility-path reference for GEQUAL .1; half-way reference rounding is driver-sensitive.
    // NVIDIA compatibility path truncates to 12 fractional bits before its RGBA8 alpha comparison.
    float legacyAlpha = floor(clamp(fragColor.a, 0.0, 1.0) * 4096.0) / 4096.0;
    if (roundEven(legacyAlpha * 255.0) < 25.0) discard;
}
