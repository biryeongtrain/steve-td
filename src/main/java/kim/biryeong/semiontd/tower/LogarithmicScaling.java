package kim.biryeong.semiontd.tower;

public final class LogarithmicScaling {
    private LogarithmicScaling() {
    }

    public static double logarithmicBonus(double rawBonus, double softCap) {
        return logarithmicBonus(rawBonus, softCap, softCap);
    }

    public static double logarithmicBonus(double rawBonus, double threshold, double scale) {
        if (!Double.isFinite(rawBonus) || rawBonus <= 0.0 || !Double.isFinite(threshold) || threshold <= 0.0 || !Double.isFinite(scale) || scale <= 0.0) {return 0.0;}
        return rawBonus <= threshold ? rawBonus : threshold + scale * Math.log1p((rawBonus - threshold) / scale);
    }
}
