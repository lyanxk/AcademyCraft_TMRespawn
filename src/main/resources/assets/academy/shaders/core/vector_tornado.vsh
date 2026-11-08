#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in ivec2 UV1;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 lightCoord;
out float fogDepth;
void main() {
    vec4 eye = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eye;
    vertexColor = vec4(Color.rgb * (float(UV1.x) / 32767.0), Color.a);
    texCoord0 = UV0;
    // 1.12 EntityRenderer.enableLightmap: scale(1/256), then translate(8,8).
    // Interpolate UV, not already-sampled light colors: fixed-function multitexture samples per fragment.
    lightCoord = (vec2(UV2) + 8.0) / 256.0;
    fogDepth = abs(eye.z);
}
