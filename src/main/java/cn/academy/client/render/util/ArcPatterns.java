package cn.academy.client.render.util;

/** Original named pattern families, twenty generated templates per family. */
public final class ArcPatterns {
    public static final ArcFactory.Arc[] weakArc = create(.15, 6, .1, 1.1);
    public static final ArcFactory.Arc[] thinContiniousArc = create(.2, 5, .08, 1.2);
    public static final ArcFactory.Arc[] chargingArc = create(.3, 5, .1, 1.2);
    public static final ArcFactory.Arc[] strongArc = create(.3, 5, .3, 1.4);
    public static final ArcFactory.Arc[] aoeArc = create(.28, 5, .13, 1.2);
    public static ArcFactory configured(double branches, int passes, double width, double offset) {
        ArcFactory factory = new ArcFactory();
        factory.branchFactor = branches; factory.passes = passes; factory.width = width; factory.maxOffset = offset;
        return factory;
    }
    private static ArcFactory.Arc[] create(double branches, int passes, double width, double offset) {
        return configured(branches, passes, width, offset).generateList(20, 20, 20);
    }
    private ArcPatterns() {}
}
