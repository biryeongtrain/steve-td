package kim.biryeong.semiontd.trait;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import net.minecraft.resources.ResourceLocation;

public final class TraitSelectionSession {
    private final ParticipantSelectionPlan plan;
    private final Set<UUID> activeParticipantIds;
    private final Map<UUID, MutableLoadout> selections = new HashMap<>();
    private int remainingTicks;

    public enum SelectionResult {
        SELECTED,
        NOT_PARTICIPANT,
        UNKNOWN_TRAIT,
        DUPLICATE_TRAIT,
        STARTED,
        DISABLED
    }

    public TraitSelectionSession(ParticipantSelectionPlan plan, Map<UUID, TraitLoadout> preselectedLoadouts, int durationTicks) {
        this.plan = plan;
        this.activeParticipantIds = plan.activeParticipants().stream()
                .map(AssignedParticipant::uuid)
                .collect(Collectors.toCollection(HashSet::new));
        this.remainingTicks = Math.max(0, durationTicks);
        Map<UUID, TraitLoadout> preselected = preselectedLoadouts == null ? Map.of() : preselectedLoadouts;
        for (UUID playerId : activeParticipantIds) {
            TraitLoadout loadout = preselected.get(playerId);
            if (loadout != null) {
                selections.put(playerId, MutableLoadout.complete(loadout));
            }
        }
    }

    public ParticipantSelectionPlan plan() {
        return plan;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public int remainingSeconds() {
        return Math.max(1, (remainingTicks + 19) / 20);
    }

    public SelectionResult select(UUID playerId, TraitSlot slot, ResourceLocation traitId) {
        if (!activeParticipantIds.contains(playerId)) {
            return SelectionResult.NOT_PARTICIPANT;
        }
        ResourceLocation normalizedTraitId = traitId == null ? BuiltInTraits.NONE_ID : traitId;
        if (TraitRegistry.find(normalizedTraitId).isEmpty()) {
            return SelectionResult.UNKNOWN_TRAIT;
        }
        MutableLoadout selection = selections.computeIfAbsent(playerId, ignored -> new MutableLoadout());
        ResourceLocation other = selection.traitId(opposite(slot));
        if (!TraitLoadout.isNone(normalizedTraitId) && normalizedTraitId.equals(other)) {
            return SelectionResult.DUPLICATE_TRAIT;
        }
        selection.set(slot, normalizedTraitId);
        return SelectionResult.SELECTED;
    }

    public TraitLoadout loadoutOrDefault(UUID playerId) {
        MutableLoadout selection = selections.get(playerId);
        return selection == null ? TraitLoadout.none() : selection.toLoadoutFillingNone();
    }

    public boolean complete() {
        return activeParticipantIds.stream().allMatch(playerId -> {
            MutableLoadout selection = selections.get(playerId);
            return selection != null && selection.complete();
        });
    }

    public boolean tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        return remainingTicks <= 0;
    }

    public TraitSelectionSnapshot snapshot() {
        Map<UUID, TraitLoadout> loadouts = new HashMap<>();
        for (UUID playerId : activeParticipantIds) {
            loadouts.put(playerId, loadoutOrDefault(playerId));
        }
        return new TraitSelectionSnapshot(loadouts);
    }

    private static TraitSlot opposite(TraitSlot slot) {
        return slot == TraitSlot.PRIMARY ? TraitSlot.SECONDARY : TraitSlot.PRIMARY;
    }

    private static final class MutableLoadout {
        private ResourceLocation primary;
        private ResourceLocation secondary;

        static MutableLoadout complete(TraitLoadout loadout) {
            MutableLoadout mutable = new MutableLoadout();
            mutable.primary = loadout.primaryTraitId();
            mutable.secondary = loadout.secondaryTraitId();
            return mutable;
        }

        ResourceLocation traitId(TraitSlot slot) {
            return slot == TraitSlot.PRIMARY ? primary : secondary;
        }

        void set(TraitSlot slot, ResourceLocation traitId) {
            if (slot == TraitSlot.PRIMARY) {
                primary = traitId;
            } else {
                secondary = traitId;
            }
        }

        boolean complete() {
            return primary != null && secondary != null;
        }

        TraitLoadout toLoadoutFillingNone() {
            return new TraitLoadout(primary == null ? BuiltInTraits.NONE_ID : primary, secondary == null ? BuiltInTraits.NONE_ID : secondary);
        }
    }
}
