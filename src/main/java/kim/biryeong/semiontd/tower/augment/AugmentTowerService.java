package kim.biryeong.semiontd.tower.augment;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Augment checks supplement the existing placement transaction; they never spend resources themselves. */
public final class AugmentTowerService {
    private AugmentTowerService() {}

    public static boolean canUse(SemionPlayer player, TowerType type) {
        String augment = AugmentTowers.augmentId(type);
        return augment != null && player.augments().hasSelected(augment)
                && (!AugmentTowers.isFreeCall(type) || player.augments().canUseTowerCall(augment));
    }

    public static boolean underPlacementLimit(PlayerLane lane, UUID playerId, TowerType type) {
        return lane.towers().stream().filter(t -> playerId.equals(t.ownerPlayer()) && AugmentTowers.is(t.type(), type)).count()
                < AugmentTowers.placementLimit(type);
    }

    public static boolean legalPosition(PlayerLane lane, GridPosition position, TowerType type) {
        return !AugmentTowers.is(type, AugmentTowers.AMBUSH_WORKSHOP) || AmbushMines.canPlace(lane, position);
    }

    public static void onPlaced(SemionGame game, SemionPlayer player, Tower tower) {
        if (!AugmentTowers.isAugment(tower.type())) return;
        if (AugmentTowers.isFreeCall(tower.type())) player.augments().markTowerCallConsumed(AugmentTowers.augmentId(tower.type()));
        if (tower instanceof OffensiveAugmentTower offensive) offensive.beginPrepare(tower.attachedLane(), game.currentRound());
    }

    /** Selection eligibility does not reserve money, a tile, a slot, or a call token. */
    public static boolean canSelect(SemionGame game, SemionPlayer player, String augmentId) {
        TowerType type = AugmentTowers.all().stream().filter(t -> AugmentTowers.augmentId(t).equals(normalize(augmentId)))
                .map(t -> ProductionTowerCatalog.entry(t).map(ProductionTowerCatalog.CatalogEntry::type).orElse(t)).findFirst().orElse(null);
        if (type == null || player == null) return false;
        if (AugmentTowers.is(type, AugmentTowers.FOLDING_BARRICADE) || AugmentTowers.is(type, AugmentTowers.PULSE_RELAY)
                || AugmentTowers.is(type, AugmentTowers.BARRIER_CORE)) return true;
        PlayerLane lane = game.playerLane(player.uuid()).orElse(null);
        if (lane == null || lane.arenaWorld() == null || !game.canFitTower(player.uuid(), type)
                || !underPlacementLimit(lane, player.uuid(), type) || player.economy().mineral() < type.mineralCost()) return false;
        if (AugmentTowers.is(type, AugmentTowers.ORDNANCE_FACTORY) && player.economy().emerald() < 100) return false;
        var bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                var resolved = TowerPlacementPositions.resolve(lane, new BlockPos(x, bounds.max().getY() + 1, z));
                if (resolved.isEmpty()) continue;
                GridPosition position = GridPosition.from(resolved.get());
                if (lane.towers().stream().anyMatch(t -> t.position().x() == position.x() && t.position().z() == position.z())) continue;
                if (legalPosition(lane, position, type)) return true;
            }
        }
        return false;
    }

    public static String placementProblem(PlayerLane lane, TowerType type) {
        if (AugmentTowers.is(type, AugmentTowers.AMBUSH_WORKSHOP)
                && AmbushMines.entranceDirection(lane.laneLayout().pathPoints()).isEmpty()) {
            return "몬스터 경로에 수평 진행 구간이 없어 지뢰 방향을 정할 수 없습니다.";
        }
        return "세 지뢰 지점 모두 레인 안의 지상에 있어야 합니다.";
    }

    public static List<Vec3> minePreview(PlayerLane lane, GridPosition position) { return AmbushMines.preview(lane, position); }

    public static void onPrimaryAttack(Tower tower, SemionMonsterEntity target, double outgoing, DamageType damageType, Vec3 beforePosition) {
        if (tower instanceof OffensiveAugmentTower offensive) {
            offensive.onPrimaryAttack(target, outgoing, damageType, beforePosition);
            return;
        }
        PlayerLane lane = tower.attachedLane();
        if (lane == null || tower.isAugmentTower()) return;
        for (Tower candidate : lane.towers()) {
            if (candidate instanceof AugmentTower support && tower.ownerPlayer().equals(candidate.ownerPlayer())) {
                support.onLinkedPrimaryAttack(tower, target, outgoing, damageType);
            }
        }
    }

    public static double redirectDamage(Tower tower, double damage) {
        PlayerLane lane = tower.attachedLane();
        if (lane == null || tower.isAugmentTower() || damage <= 0) return damage;
        for (Tower candidate : lane.towers()) {
            if (candidate instanceof AugmentTower support && tower.ownerPlayer().equals(candidate.ownerPlayer())) {
                damage = support.redirectDamage(tower, damage);
            }
        }
        return damage;
    }

    public static void beginPrepare(SemionGame game) {
        for (SemionPlayer player : game.players().values()) game.playerLane(player.uuid()).ifPresent(lane -> {
            for (Tower tower : lane.towers()) if (tower instanceof OffensiveAugmentTower offensive) {
                offensive.beginPrepare(lane, game.currentRound());
            }
        });
    }

    public static void settleRound(SemionGame game, boolean advancing) {
        for (SemionPlayer player : game.players().values()) game.playerLane(player.uuid()).ifPresent(lane -> {
            boolean survives = advancing && game.teams().containsKey(player.teamId()) && !game.teams().get(player.teamId()).eliminated();
            for (Tower tower : lane.towers()) if (tower instanceof AugmentTower augment
                    && player.uuid().equals(tower.ownerPlayer()) && tower.currentRound() == game.currentRound()) {
                if (tower instanceof OffensiveAugmentTower offensive) offensive.settleRound(lane, game.currentRound(), survives);
                augment.recordTelemetry(lane, game.currentRound(), "ROUND_END");
            }
        });
    }

    public static void onPaidSummon(SemionGame game, UUID playerId, UUID transactionId, long actualEmerald, boolean eligible) {
        if (!eligible || game.phase() != RoundPhase.PREPARE_AND_SUMMON || actualEmerald <= 0 || transactionId == null) return;
        game.playerLane(playerId).ifPresent(lane -> {
            for (Tower tower : lane.towers()) if (tower instanceof OffensiveAugmentTower offensive
                    && tower.ownerPlayer().equals(playerId) && AugmentTowers.is(tower.type(), AugmentTowers.ORDNANCE_FACTORY)) {
                offensive.onPaidSummon(transactionId, actualEmerald);
            }
        });
    }

    private static String normalize(String id) { return id == null || id.contains(":") ? id : "semiontd:" + id; }
}
