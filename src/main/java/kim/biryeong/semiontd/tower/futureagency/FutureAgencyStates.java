package kim.biryeong.semiontd.tower.futureagency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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
        private int selectionsThisRound;
        private int selectionLimit = 1;
        private int nextSelectionLimit = 1;
        private FutureAgencyPolicy lastChosen;
        private final EnumSet<FutureAgencyPolicy> shownThisRound = EnumSet.noneOf(FutureAgencyPolicy.class);
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
        public int selectionNumber() {return Math.min(selectionLimit, selectionsThisRound + 1);}
        public int selectionLimit() {return selectionLimit;}
        public boolean selectedThisRound() {return offerRound >= 0 && selectionsThisRound >= selectionLimit;}

        public void reconstruct() {
            if (stage == Stage.ESCAPEE) stage = Stage.REBUILDER;
        }

        public void promoteCommander() {
            if (reconstructed() && policySelections >= 5) stage = Stage.COMMANDER;
        }

        public void setNextSelectionLimit(int limit) {
            nextSelectionLimit = Math.max(1, limit);
        }

        public void openRound(int round) {
            if (!reconstructed() || round == offerRound) return;
            offerRound = round;
            selectionsThisRound = 0;
            selectionLimit = nextSelectionLimit;
            nextSelectionLimit = 1;
            lastChosen = null;
            shownThisRound.clear();
            rollOffers();
        }

        private void rollOffers() {
            ArrayList<FutureAgencyPolicy> eligible = new ArrayList<>();
            for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
                if (policy != lastChosen && stacks(policy) < policy.maxStacks()) eligible.add(policy);
            }
            ArrayList<FutureAgencyPolicy> fresh = new ArrayList<>(eligible);
            fresh.removeAll(shownThisRound);
            Collections.shuffle(fresh);
            Collections.shuffle(eligible);
            ArrayList<FutureAgencyPolicy> next = new ArrayList<>(3);
            for (FutureAgencyPolicy policy : fresh) {
                if (next.size() == 3) break;
                next.add(policy);
            }
            for (FutureAgencyPolicy policy : eligible) {
                if (next.size() == 3) break;
                if (!next.contains(policy)) next.add(policy);
            }
            offers = List.copyOf(next);
            shownThisRound.addAll(next);
        }

        public boolean canChoose(FutureAgencyPolicy policy) {
            return !selectedThisRound() && policy != null && offers.contains(policy)
                    && stacks(policy) < policy.maxStacks();
        }

        public boolean choose(FutureAgencyPolicy policy) {
            if (!canChoose(policy)) return false;
            policies.merge(policy, 1, Integer::sum);
            policySelections++;
            selectionsThisRound++;
            lastChosen = policy;
            if (selectedThisRound()) offers = List.of();
            else rollOffers();
            return true;
        }

        public void saveWorld() {
            if (commander() && policySelections >= 10) {
                worldSaved = true;
            }
        }
    }
}
