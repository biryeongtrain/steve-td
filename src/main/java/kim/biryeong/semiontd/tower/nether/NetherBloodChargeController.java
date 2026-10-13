package kim.biryeong.semiontd.tower.nether;

import java.util.ArrayDeque;

final class NetherBloodChargeController {
    private double naturalHealthLoss;
    private final ArrayDeque<Double> charges = new ArrayDeque<>();

    void recordNaturalLoss(double loss, double threshold) {
        naturalHealthLoss += loss;
        if (threshold <= 0) {
            return;
        }
        while (naturalHealthLoss + 1.0e-9 >= threshold) {
            naturalHealthLoss = Math.max(0, naturalHealthLoss - threshold);
            charges.addLast(threshold);
        }
    }

    double consume() {
        return charges.isEmpty() ? 0 : charges.removeFirst();
    }

    int chargeCount() {
        return charges.size();
    }

    void resetRound() {
        naturalHealthLoss = 0;
        charges.clear();
    }

    NetherBloodChargeSnapshot snapshot() {
        return new NetherBloodChargeSnapshot(naturalHealthLoss, java.util.List.copyOf(charges));
    }

    void restore(NetherBloodChargeSnapshot snapshot) {
        naturalHealthLoss = snapshot.naturalHealthLoss();
        charges.clear();
        charges.addAll(snapshot.charges());
    }
}
