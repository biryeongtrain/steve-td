package kim.biryeong.semiontd.tower.nether;

import java.util.List;

record NetherBloodChargeSnapshot(double naturalHealthLoss, List<Double> charges) {
    NetherBloodChargeSnapshot {
        charges = List.copyOf(charges);
    }
}
