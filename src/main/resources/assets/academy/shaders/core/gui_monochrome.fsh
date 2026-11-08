#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 result = vertexColor * texture(Sampler0, texCoord0) * ColorModulator;
    // LambdaLib2 legacy_shader/mono.frag uses the arithmetic mean, not luminance weights.
    float value = (result.r + result.g + result.b) / 3.0;
    fragColor = vec4(value, value, value, result.a);
}
