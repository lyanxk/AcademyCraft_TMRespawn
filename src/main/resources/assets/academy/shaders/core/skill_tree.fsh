#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float AlphaCutoff;
uniform int Monochrome;
uniform int UseProgress;
uniform float Progress;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord0);
    if (UseProgress != 0) {
        // Original skill_progbar ignores vertex color and uses a strict comparison after filtering.
        fragColor = Progress > texture(Sampler1, texCoord0).r ? sampleColor : vec4(0.0);
        return;
    }
    vec4 color = sampleColor * vertexColor * ColorModulator;
    if (AlphaCutoff >= 0.0 && color.a <= AlphaCutoff) discard;
    if (Monochrome != 0) color.rgb = vec3((color.r + color.g + color.b) / 3.0);
    fragColor = color;
}
