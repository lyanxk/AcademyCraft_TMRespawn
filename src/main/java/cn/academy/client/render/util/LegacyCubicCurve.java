package cn.academy.client.render.util;

/** LambdaLib2 CubicCurve interpolation, including endpoint extrapolation (MIT, META-INF/LICENSE-LambdaLib2.txt). */
public final class LegacyCubicCurve {
    private final double[] x,y;
    public LegacyCubicCurve(double... points) {
        x=new double[points.length/2];y=new double[x.length];
        for(int i=0;i<x.length;i++){x[i]=points[2*i];y[i]=points[2*i+1];}
    }
    private double slope(int i) {
        if(x.length<2)return 0;
        if(i==0)return (y[1]-y[0])/(x[1]-x[0]);
        if(i==x.length-1)return (y[i]-y[i-1])/(x[i]-x[i-1]);
        return .5*((y[i+1]-y[i])/(x[i+1]-x[i])+(y[i]-y[i-1])/(x[i]-x[i-1]));
    }
    public double at(double value) {
        if(x.length==0)return 0;int i=0;while(i<x.length && x[i]<value)i++;
        if(i==0)return y[0]+slope(0)*(value-x[0]);
        if(i==x.length)return y[i-1]+slope(i-1)*(value-x[i-1]);
        double length=x[i]-x[i-1],t=(value-x[i-1])/length,a=y[i-1],b=y[i],m=slope(i-1)*length,n=slope(i)*length;
        return t*t*t*(m+n+2*a-2*b)+t*t*(-2*m-n-3*a+3*b)+t*m+a;
    }
}
