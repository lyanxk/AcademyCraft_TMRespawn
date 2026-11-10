package cn.academy.client.render.util;

/** Original W/S/A/D delegates: most recently pressed wins; releasing it does not fall back to another held key. */
public final class StormWingControls {
    private int held,direction;
    public int update(int bits,boolean enabled) {
        bits&=15;
        if(!enabled){held=bits;direction=0;return 0;}
        if(direction!=0 && (bits & 1<<(direction-1))==0)direction=0;
        int pressed=bits & ~held;
        for(int i=1;i<=4;i++)if((pressed & 1<<(i-1))!=0)direction=i;
        held=bits;return direction;
    }
    public int direction(){return direction;}
    public void clear(){held=direction=0;}
}
