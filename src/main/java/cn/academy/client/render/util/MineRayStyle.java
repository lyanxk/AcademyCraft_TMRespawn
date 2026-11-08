package cn.academy.client.render.util;

/** Renderer values after ExpertRayRenderer.doRender overwrites its constructor's alpha. */
public record MineRayStyle(String texture,String startup,double inner,double outer,double glow,int glowAlpha,
                           int red,int green,int blue,int innerAlpha,int outerRed,int outerGreen,int outerBlue) {
    public static final MineRayStyle[] STYLES={
            new MineRayStyle("mdray_small","basic",.03,.045,.3,127,216,248,216,230,106,242,106),
            new MineRayStyle("mdray_expert","expert",.045,.056,.5,127,216,248,216,180,106,242,106),
            new MineRayStyle("mdray_luck","luck",.04,.05,.45,153,241,229,247,230,205,166,232)
    };
}
