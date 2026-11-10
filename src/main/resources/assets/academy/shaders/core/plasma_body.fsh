#version 150
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
uniform float chargeProgress;
uniform float animationTime;
in vec3 camspace;

void main() {
    vec4 balls[16]=vec4[16](Ball0,Ball1,Ball2,Ball3,Ball4,Ball5,Ball6,Ball7,
        Ball8,Ball9,Ball10,Ball11,Ball12,Ball13,Ball14,Ball15);
    vec3 ray=normalize(vec3(camspace.xy,-camspace.z));
    float nearest=1e20;
    float coverage=0.0;
    float glow=0.0;
    vec3 surface=vec3(0.0);
    vec3 normal=vec3(0.0,0.0,1.0);
    // Analytic intersections preserve the sphere radius supplied by the renderer.
    // The luminous edge sits outside the sphere without changing its physical visual radius.
    for(int i=0;i<ballCount;i++) {
        vec3 center=balls[i].xyz;
        float radius=balls[i].w;
        float along=dot(ray,center);
        if(along<=0.0)continue;
        float perpendicular=length(center-ray*along);
        float edge=perpendicular-radius;
        glow=max(glow,exp(-max(edge,0.0)*8.0)*smoothstep(-.03,.035,edge));
        float discriminant=radius*radius-perpendicular*perpendicular;
        if(discriminant<=0.0)continue;
        float halfChord=sqrt(discriminant);
        float entry=max(0.0,along-halfChord);
        coverage=max(coverage,smoothstep(0.0,.10,halfChord));
        if(entry<nearest) {
            nearest=entry;
            surface=ray*entry-center;
            normal=normalize(surface);
        }
    }
    if(coverage<=0.0) {
        if(glow<.005)discard;
        fragColor=vec4(.18,.75,1.0,glow*.55*alpha);
        return;
    }
    float facing=max(0.0,dot(normal,-ray));
    float flow=sin(surface.x*4.0+sin(surface.y*3.0-animationTime*1.8)
        +surface.z*2.0+animationTime*2.4);
    float swirl=sin(surface.y*5.0+sin(surface.z*4.0+animationTime*1.4)+animationTime*2.0);
    vec3 color=mix(vec3(.12,.58,.94),vec3(.48,.83,1.0),smoothstep(.05,.9,facing));
    color=mix(color,vec3(.78,.93,1.0),pow(facing,12.0)*.35);
    color+=vec3(.025,.045,.055)*flow*swirl*(.4+.6*chargeProgress);
    // Intermittent discharge filaments are part of the plasma surface.
    float filament=pow(1.0-abs(sin(surface.x*5.0+surface.y*4.0+sin(surface.z*6.0+animationTime*3.0))),28.0);
    float flash=smoothstep(.5,.95,sin(animationTime*13.0+surface.y*2.0));
    color+=vec3(.3,.45,.5)*filament*flash*(1.0-facing*.7);
    fragColor=vec4(color,coverage*alpha);
}
