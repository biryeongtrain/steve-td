package kim.biryeong.semiontd.tower.mage;

import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.game.PlayerLane;

public final class MageAugments {
    static final String REFUND = "job_mage_towers_s";
    static final String FLOOD = "job_mage_towers_g1";
    static final String DOUBLE = "job_mage_towers_g2";
    static final String WORLD = "job_mage_towers_p";

    private MageAugments() {}

    public static void onSelected(PlayerLane lane, String cardId, AugmentConfig config) {
        if (lane != null && (FLOOD.equals(cardId) || ("semiontd:" + FLOOD).equals(cardId))) {
            MageStates.state(lane.ownerPlayer()).addMana((int) config.parameter(FLOOD, "initialMana", 300));
        }
    }
}
