package kim.biryeong.semiontd.tower.mage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class MageStates {
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private MageStates() {
    }

    public static synchronized PlayerState state(UUID playerId) {
        return STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
    }

    public static synchronized void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static final class PlayerState {
        private int mana;
        private boolean startingManaGranted;

        public int mana() {
            mana = Math.min(mana, capacity());
            return mana;
        }

        public int capacity() {
            return value("manaCapacity", MageBalance.MANA_CAPACITY);
        }

        public void grantStartingMana() {
            if (startingManaGranted) {
                return;
            }
            startingManaGranted = true;
            addMana(value("startingMana", MageBalance.STARTING_MANA));
        }

        public boolean canSpend(int amount) {
            return amount >= 0 && mana() >= amount;
        }

        public boolean spend(int amount) {
            if (!canSpend(amount)) {
                return false;
            }
            mana -= amount;
            return true;
        }

        public int addMana(int amount) {
            int previous = mana;
            mana = Math.min(capacity(), Math.max(0, mana + Math.max(0, amount)));
            return mana - previous;
        }

        public void clearMana() {
            mana = 0;
        }

        public int loseRatio(double ratio) {
            int previous = mana();
            double clamped = Math.max(0.0, Math.min(1.0, ratio));
            mana = Math.max(0, (int) Math.floor(previous * (1.0 - clamped)));
            return previous - mana;
        }

        private static int value(String key, int fallback) {
            return TowerBalanceRuntime.abilityInt(MageBalance.GLOBAL_ID, key, fallback);
        }
    }
}
