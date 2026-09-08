package kim.biryeong.semiontd.tower.gamble;

import java.util.Arrays;
import java.util.Optional;

public enum GambleBet {
    ODD("bet_odd", "홀에 걸기"),
    EVEN("bet_even", "짝에 걸기"),
    TWO_DICE("roll_two_dice", "주사위 두 개 굴리기"),
    SLOTS("spin_slots", "슬롯머신 돌리기");

    private final String upgradeId;
    private final String displayName;

    GambleBet(String upgradeId, String displayName) {
        this.upgradeId = upgradeId;
        this.displayName = displayName;
    }

    public String upgradeId() {
        return upgradeId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean matchesParity(int die) {
        return this == ODD ? die % 2 == 1 : this == EVEN && die % 2 == 0;
    }

    public static Optional<GambleBet> fromUpgradeId(String id) {
        return Arrays.stream(values()).filter(value -> value.upgradeId.equals(id)).findFirst();
    }
}
