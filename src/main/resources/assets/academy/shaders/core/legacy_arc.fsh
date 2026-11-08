#version 150

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // LambdaLib2 ShaderSimple: no monochrome conversion, fog, alpha discard or global tint.
    fragColor = vertexColor * texture(Sampler0, texCoord0);
}
