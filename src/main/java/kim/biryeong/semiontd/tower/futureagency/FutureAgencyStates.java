package kim.biryeong.semiontd.tower.futureagency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FutureAgencyStates {
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private FutureAgencyStates() {}

    public static PlayerState state(UUID playerId) {
        return STATES.computeIfAbsent(playerId, PlayerState::new);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) STATES.remove(playerId);
    }

    public enum Stage {ESCAPEE, REBUILDER, COMMANDER}

    public static final class PlayerState {
        private final EnumMap<FutureAgencyPolicy, Integer> policies = new EnumMap<>(FutureAgencyPolicy.class);
        private Stage stage = Stage.ESCAPEE;
        private boolean worldSaved;
        private int policySelections;
        private int offerRound = -1;
        private int selectedRound = -1;
        private List<FutureAgencyPolicy> offers = List.of();

        private PlayerState(UUID playerId) {}

        public Stage stage() {return stage;}
        public boolean reconstructed() {return stage != Stage.ESCAPEE;}
        public boolean commander() {return stage == Stage.COMMANDER;}
        public boolean worldSaved() {return worldSaved;}
        public int policySelections() {return policySelections;}
        public int stacks(FutureAgencyPolicy policy) {return policies.getOrDefault(policy, 0);}
        public Map<FutureAgencyPolicy, Integer> policyStacks() {return Map.copyOf(policies);}
        public List<FutureAgencyPolicy> offers() {return offers;}
        public boolean selectedThisRound() {return selectedRound == offerRound && offerRound >= 0;}

        public void reconstruct() {
            if (stage == Stage.ESCAPEE) stage = Stage.REBUILDER;
        }

        public void promoteCommander() {
            if (reconstructed() && policySelections >= 5) stage = Stage.COMMANDER;
        }

        public void openRound(int round) {
            if (!reconstructed() || worldSaved || round == offerRound) return;
            offerRound = round;
            selectedRound = -1;
            ArrayList<FutureAgencyPolicy> eligible = new ArrayList<>();
            for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
                if (stacks(policy) < policy.maxStacks()) eligible.add(policy);
            }
            Collections.shuffle(eligible);
            offers = List.copyOf(eligible.subList(0, Math.min(3, eligible.size())));
        }

        public boolean canChoose(FutureAgencyPolicy policy) {
            return !worldSaved && !selectedThisRound() && policy != null && offers.contains(policy)
                    && stacks(policy) < policy.maxStacks();
        }

        public boolean choose(FutureAgencyPolicy policy) {
            if (!canChoose(policy)) return false;
            policies.merge(policy, 1, Integer::sum);
            policySelections++;
            selectedRound = offerRound;
            offers = List.of();
            return true;
        }

        public void saveWorld() {
            if (commander() && policySelections >= 10) {
                worldSaved = true;
                offers = List.of();
            }
        }
    }
}
