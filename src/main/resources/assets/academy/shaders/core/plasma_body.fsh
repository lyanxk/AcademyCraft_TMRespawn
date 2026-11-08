#version 150
// PlasmaBodyEffect original ray march; individual uniforms adapt Minecraft ShaderInstance.
out vec4 fragColor;

uniform int ballCount;
uniform vec4 Ball0;
uniform vec4 Ball1;
uniform vec4 Ball2;
uniform vec4 Ball3;
uniform vec4 Ball4;
uniform vec4 Ball5;
uniform vec4 Ball6;
uniform vec4 Ball7;
uniform vec4 Ball8;
uniform vec4 Ball9;
uniform vec4 Ball10;
uniform vec4 Ball11;
uniform vec4 Ball12;
uniform vec4 Ball13;
uniform vec4 Ball14;
uniform vec4 Ball15;
uniform float alpha;

in vec3 camspace;

float f(vec3 position) {
    vec4 balls[16] = vec4[16](Ball0, Ball1, Ball2, Ball3, Ball4, Ball5, Ball6, Ball7, Ball8, Ball9, Ball10, Ball11, Ball12, Ball13, Ball14, Ball15);
    float ret = 0.0;
    for (int i = 0; i < ballCount; ++i) {
        float distance = max(0.1, length(position - balls[i].xyz));

        ret += alpha * balls[i].w / (distance * distance);
    }
    return clamp(ret, 0, 2);
}

vec4 rayMarch(vec3 begin, vec3 dir) {
    dir *= 0.15;

    vec3 pos = begin;

    vec4 accum = vec4(0, 0, 0, 0);
    for (int i = 0; i < 20 && accum.a < 1; ++i) {
        float density = f(pos);

        float alpha = 0.075 * density;
        vec3 crl = mix(vec3(0.43, 0.74, 1), vec3(0.98, 0.51, 0.92), 1-density/2);

        accum.rgb = mix(accum.rgb, crl, (accum.a + alpha > 0.0 ? alpha / (accum.a + alpha) : 0.0));
        accum.a += alpha;

        pos += dir;
    }

    if (accum.a < 0.2) { // Make alpha in [0.2, 0.1] map to [0.2, 0], to make blending look normal in edge area
        accum.a = 2 * accum.a - 0.2;
    }
    // accum.a = clamp(accum.a, 0, 1); //* 0.8;
    return accum;
}

void main() {
    vec3 cam = camspace;
    cam.z = -cam.z;

    vec3 dir = normalize(cam);

    vec4 rc = rayMarch(cam - dir * 3, dir);
    rc.a = clamp(rc.a, 0, 1) * (0.5 + alpha * 0.5);

	fragColor = rc;
}
