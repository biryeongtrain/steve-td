package kim.biryeong.semiontd.tower.adversary;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.PlayerLane;

public final class AdversaryProgressStates {
    private static final Map<UUID, AdversaryProgressState> STATES = new ConcurrentHashMap<>();

    private AdversaryProgressStates() {
    }

    public static AdversaryProgressState state(UUID ownerPlayer) {
        if (ownerPlayer == null) {
            throw new IllegalArgumentException("Adversary progress requires an owner player.");
        }
        return STATES.computeIfAbsent(ownerPlayer, ignored -> new AdversaryProgressState());
    }

    public static Optional<AdversaryProgressState> find(UUID ownerPlayer) {
        return Optional.ofNullable(ownerPlayer == null ? null : STATES.get(ownerPlayer));
    }

    public static FoxForm currentForm(UUID ownerPlayer) {
        return ownerPlayer == null ? FoxForm.BASE : state(ownerPlayer).currentForm();
    }

    public static void clear(UUID ownerPlayer) {
        if (ownerPlayer != null) {
            STATES.remove(ownerPlayer);
        }
    }

    public static void clearAllForTesting() {
        STATES.clear();
    }

    public static void noteScoringKind(UUID ownerPlayer, RivalKind kind) {
        if (ownerPlayer != null && kind != null) {
            state(ownerPlayer).noteScoringKind(kind);
        }
    }

    /**
     * Records a kill that was produced by the owner's Adversary fox.
     *
     * <p>The caller is deliberately the fox's {@code onKill} hook.  A generic
     * last-hit player id cannot distinguish the fox from another tower owned by
     * the same player, so rival towers never infer score from nearby-death
     * notifications.</p>
     *
     * @return {@code true} when the kill awarded rival score
     */
    public static boolean recordFoxKill(UUID ownerPlayer, Monster monster, PlayerLane lane) {
        if (ownerPlayer == null || monster == null) {
            return false;
        }

        if (AdversaryRivalTower.isOwnedRival(monster, ownerPlayer)) {
            if (lane == null) {
                return false;
            }
            UUID rivalId = AdversaryRivalTower.logicalRivalIdOf(monster).orElse(null);
            if (rivalId == null) {
                return false;
            }
            return lane.towers().stream()
                    .filter(AdversaryRivalTower.class::isInstance)
                    .map(AdversaryRivalTower.class::cast)
                    .filter(tower -> ownerPlayer.equals(tower.ownerPlayer()))
                    .filter(tower -> rivalId.equals(tower.rivalId()))
                    .findFirst()
                    .map(tower -> tower.creditFoxKill(lane, monster))
                    .orElse(false);
        }

        return false;
    }

    public static void reconcileLane(UUID ownerPlayer, PlayerLane lane) {
        if (ownerPlayer == null || lane == null) {
            return;
        }
        Collection<RivalContribution> contributions = lane.towers().stream()
                .filter(tower -> ownerPlayer.equals(tower.ownerPlayer()))
                .filter(RivalProgressSource.class::isInstance)
                .map(RivalProgressSource.class::cast)
                .map(RivalProgressSource::snapshot)
                .toList();
        state(ownerPlayer).reconcileRivals(contributions);
    }
}
