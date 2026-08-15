package kim.biryeong.semiontd.tower.mage;

import java.util.ArrayList;
import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

public final class MageTowerLifecycle {
    private MageTowerLifecycle() {
    }

    public static void finishRound(PlayerLane lane, UUID owner) {
        boolean hasCore = MageTowerRuntime.hasCore(lane, owner);
        int naturalMana = hasCore ? MageBalance.coreMana() : 0;
        for (Tower tower : new ArrayList<>(lane.towers())) {
            if (!owner.equals(tower.ownerPlayer())) {
                continue;
            }
            if (tower instanceof MageWizardTower wizard) {
                if (!tower.isDestroyed(lane)) {
                    naturalMana += wizard.naturalManaProduction();
                }
                wizard.finishRound();
            } else if (tower instanceof MageProphetTower prophet && !tower.isDestroyed(lane)) {
                naturalMana += prophet.naturalManaProduction();
            }
        }
        if (hasCore) {
            MageStates.state(owner).addMana(naturalMana);
        }
        MageTowerRuntime.restoreTemporaryTowers(lane, owner);
    }

}
