package kim.biryeong.semiontd.tower.queen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.game.TeamLaneGroup;

public final class QueenStates {
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private QueenStates() {}

    public static PlayerState state(UUID playerId) {
        return STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
    }

    public static void begin(UUID playerId, TeamLaneGroup laneGroup) {
        PlayerState state = new PlayerState();
        state.laneGroup = laneGroup;
        STATES.put(playerId, state);
    }

    public static void clear(UUID playerId) {
        PlayerState removed = STATES.remove(playerId);
        if (removed != null) removed.endRunner();
    }

    public static void clearAll() {
        STATES.values().forEach(PlayerState::endRunner);
        STATES.clear();
    }

    public static final class PlayerState {
        private TeamLaneGroup laneGroup;
        private double charge;
        private double executionHealth = QueenBalance.giantInitialExecutionHealth();
        private QueenGiantRunner runner;
        private QueenCard nextCard;

        public TeamLaneGroup laneGroup() {return laneGroup;}
        public double charge() {return charge;}
        public double executionHealth() {return executionHealth;}
        public boolean runnerActive() {return runner != null && runner.active();}

        public QueenCard peekNextCard() {
            if (nextCard == null) nextCard = QueenCard.random();
            return nextCard;
        }

        public QueenCard drawNextCard() {
            QueenCard drawn = peekNextCard();
            nextCard = null;
            return drawn;
        }

        public void addCharge(double amount) {
            if (!Double.isFinite(amount) || amount <= 0.0) return;
            charge += amount;
        }

        public boolean ready() {return charge >= QueenBalance.giantChargeTicks();}

        public void consumeCharge() {
            charge = Math.max(0.0, charge - QueenBalance.giantChargeTicks());
        }

        public void growExecutionHealth(double effectiveMaxHealth) {
            if (Double.isFinite(effectiveMaxHealth) && effectiveMaxHealth > 0.0) {
                double growthBase = Math.min(
                        effectiveMaxHealth,
                        executionHealth * QueenBalance.giantGrowthTargetCapMultiplier()
                );
                executionHealth += Math.max(QueenBalance.giantInitialExecutionHealth(), growthBase)
                        * QueenBalance.giantExecutionGrowthRatio();
            }
        }

        QueenGiantRunner runner() {return runner;}
        void runner(QueenGiantRunner runner) {this.runner = runner;}

        public void endRunner() {
            if (runner != null) runner.remove();
            runner = null;
        }
    }
}
