package kim.biryeong.semiontd.tower.ocean;

final class OceanCurrentController {
    private int waveTicks;
    private double waterSpent;
    private int charges;

    void resetRound() {
        waveTicks = 0;
        waterSpent = 0;
        charges = 0;
    }

    void tick() {
        waveTicks++;
    }

    boolean tideActive(int period, int duration) {
        return waveTicks >= period && waveTicks % period < duration;
    }

    void recordSpent(double amount, double threshold) {
        waterSpent += amount;
        int gained = (int) Math.floor((waterSpent + 1.0E-9) / threshold);
        charges += gained;
        waterSpent = Math.max(0.0, waterSpent - gained * threshold);
    }

    boolean consume() {
        if (charges <= 0) {
            return false;
        }
        charges--;
        return true;
    }

    int charges() {
        return charges;
    }

    double waterSpent() {
        return waterSpent;
    }

    OceanCurrentSnapshot snapshot() {
        return new OceanCurrentSnapshot(waveTicks, waterSpent, charges);
    }

    void restore(OceanCurrentSnapshot snapshot) {
        waveTicks = snapshot.waveTicks();
        waterSpent = snapshot.waterSpent();
        charges = snapshot.charges();
    }
}
