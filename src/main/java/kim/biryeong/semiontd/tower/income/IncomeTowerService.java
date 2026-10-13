package kim.biryeong.semiontd.tower.income;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.demonlord.DemonLordIncome;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * 인컴 타워 설치·레벨업·판매·대상 지정, 그리고 준비 시간 종료 시 유닛 파견용 몬스터 생성.
 *
 * <p>마왕 전용입니다. 마왕은 인컴 몹을 사지 않고 이 타워로 인컴을 올리며 적 레인을 압박합니다.
 * 설치와 관리는 준비 단계에서만 됩니다. 비용은 에메랄드이고, 타워 수는 일반 타워와 같이 셉니다.
 */
public final class IncomeTowerService {
    private static final MonsterDataKey<Boolean> DISPATCH = MonsterDataKey.of(
            Identifier.fromNamespaceAndPath("semion-td", "income_tower_dispatch"), Boolean.class);

    public static boolean isDispatch(Monster monster) {
        return monster != null && monster.getData(DISPATCH).orElse(false);
    }

    public static List<String> description(SummonMonsterType unit) {
        return switch (unit.id()) {
            case "dark_priest", "ogre_champion" -> java.util.stream.Stream.concat(unit.description().stream(),
                    java.util.stream.Stream.of("주 대상 피해는 100%, 추가 광역 대상 피해는 각각 50%입니다.")).toList();
            case "dwarf_gunner" -> List.of("탄환으로 한 대상에게 물리 피해를 줍니다.");
            default -> unit.description();
        };
    }

    private IncomeTowerService() {
    }

    public enum Result {
        SUCCESS("완료했습니다."),
        INVALID_PHASE("준비 단계에서만 할 수 있습니다."),
        PLAYER_NOT_IN_GAME("현재 게임 참가자가 아닙니다."),
        PLAYER_TEAM_ELIMINATED("팀이 탈락했습니다."),
        UNKNOWN_LANE("담당 라인을 찾을 수 없습니다."),
        NOT_DEMON_LORD("인컴 타워는 마왕만 세울 수 있습니다."),
        UNKNOWN_UNIT("알 수 없는 인컴 유닛입니다."),
        OUTSIDE_LANE_AREA("라인 안에서만 설치할 수 있습니다."),
        OCCUPIED("이미 다른 타워가 있는 자리입니다."),
        TOWER_LIMIT_REACHED("타워 수가 가득 찼습니다."),
        NOT_ENOUGH_EMERALD("에메랄드가 부족합니다."),
        NO_TOWER("그 자리에 인컴 타워가 없습니다."),
        NOT_OWNED("자신의 인컴 타워만 관리할 수 있습니다."),
        MAX_LEVEL("이미 최고 레벨입니다.");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** 창에 보여 줄 유닛 목록. {@code summons.json}에서 꺼 둔 유닛은 빠집니다. */
    public static List<SummonMonsterType> units(SemionGame game) {
        return IncomeTowerBalance.UNIT_IDS.stream()
                .map(id -> game.summonShop().find(id))
                .flatMap(Optional::stream)
                .toList();
    }

    public static Optional<SummonMonsterType> unit(SemionGame game, String summonId) {
        return IncomeTowerBalance.isUnit(summonId) ? game.summonShop().find(summonId) : Optional.empty();
    }

    /** 레인에 세울 타워 모습: 유닛의 Blockbench 모델을 그대로 씁니다. */
    public static TowerType towerType(SummonMonsterType unit) {
        String entityTypeId = unit.entityTypeId();
        EntityVisual visual = unit.blockbenchModelId()
                .map(model -> EntityVisual.modeled(entityTypeId, model))
                .orElseGet(() -> EntityVisual.vanilla(entityTypeId));
        return new TowerType(
                IncomeTowerBalance.towerId(unit.id()),
                unit.displayName(),
                TowerCategory.SUPPORT,
                0,
                Math.max(1.0, unit.maxHealth()),
                0.0,
                0.0,
                20,
                0,
                List.of("준비 시간이 끝날 때마다 " + unit.displayName() + "을(를) 적 레인으로 보냅니다."),
                visual,
                List.of()
        );
    }

    public static long buildCost(SemionGame game, SummonMonsterType unit) {
        return game.summonsAreFree() ? 0 : unit.gasCost();
    }

    public static long upgradeCost(SemionGame game, IncomeTower tower) {
        if (game.summonsAreFree()) {
            return 0;
        }
        return unit(game, tower.summonId())
                .map(unit -> IncomeTowerBalance.upgradeCost(unit.gasCost(), tower.level()))
                .orElse(0L);
    }

    public static long incomeOf(SemionGame game, IncomeTower tower) {
        return unit(game, tower.summonId())
                .map(unit -> IncomeTowerBalance.incomeAt(unit.incomeGain(), tower.level()))
                .orElse(0L);
    }

    public static Result build(SemionGame game, UUID playerId, BlockPos blockPos, String summonId) {
        Context context = context(game, playerId, true);
        if (context.failure != null) {
            return context.failure;
        }
        if (!DemonLordIncome.isDemonLord(context.player)) {
            return Result.NOT_DEMON_LORD;
        }
        SummonMonsterType unit = unit(game, summonId).orElse(null);
        if (unit == null) {
            return Result.UNKNOWN_UNIT;
        }
        Optional<BlockPos> placement = TowerPlacementPositions.resolve(context.lane, blockPos);
        if (placement.isEmpty()) {
            return Result.OUTSIDE_LANE_AREA;
        }
        GridPosition position = GridPosition.from(placement.get());
        if (context.lane.towers().stream().anyMatch(tower ->
                tower.position().x() == position.x() && tower.position().z() == position.z())) {
            return Result.OCCUPIED;
        }
        TowerType type = towerType(unit);
        if (!game.canFitTower(playerId, type)) {
            return Result.TOWER_LIMIT_REACHED;
        }
        long cost = buildCost(game, unit);
        if (!context.player.economy().spendEmerald(cost)) {
            return Result.NOT_ENOUGH_EMERALD;
        }
        IncomeTower tower = new IncomeTower(type, playerId, context.player.teamId(), context.player.laneId(), position, unit.id());
        tower.recordPaidEmerald(cost);
        tower.recordPlacementEconomy(0, game.currentRound());
        context.lane.addTower(tower);
        context.player.economy().addIncome(IncomeTowerBalance.incomeAt(unit.incomeGain(), tower.level()));
        return Result.SUCCESS;
    }

    public static Result upgrade(SemionGame game, UUID playerId, GridPosition position) {
        Context context = context(game, playerId, true);
        if (context.failure != null) {
            return context.failure;
        }
        IncomeTower tower = ownedTower(context, playerId, position);
        if (tower == null) {
            return ownershipFailure(context, playerId, position);
        }
        if (tower.maxLevel()) {
            return Result.MAX_LEVEL;
        }
        SummonMonsterType unit = unit(game, tower.summonId()).orElse(null);
        if (unit == null) {
            return Result.UNKNOWN_UNIT;
        }
        long cost = upgradeCost(game, tower);
        if (!context.player.economy().spendEmerald(cost)) {
            return Result.NOT_ENOUGH_EMERALD;
        }
        tower.levelUp(cost);
        context.player.economy().addIncome(Math.max(0, unit.incomeGain()));
        tower.onStateChanged(context.lane);
        return Result.SUCCESS;
    }

    public static Result sell(SemionGame game, UUID playerId, GridPosition position) {
        Context context = context(game, playerId, true);
        if (context.failure != null) {
            return context.failure;
        }
        IncomeTower tower = ownedTower(context, playerId, position);
        if (tower == null) {
            return ownershipFailure(context, playerId, position);
        }
        long income = incomeOf(game, tower);
        if (!context.lane.removeTower(tower)) {
            return Result.NO_TOWER;
        }
        context.player.economy().addEmerald(IncomeTowerBalance.sellRefund(tower.paidEmerald()));
        context.player.economy().removeIncome(income);
        return Result.SUCCESS;
    }

    /**
     * 이 타워가 이번 라운드에 보낼 유닛 한 마리.
     *
     * <p>라운드 보정은 일반 소환 몹과 같은 라운드 곡선을 쓰고, 그 위에 레벨 보정을 곱합니다.
     */
    public static Optional<Monster> createDispatch(
            SemionGame game,
            SemionPlayer owner,
            IncomeTower tower,
            TeamId targetTeam,
            int targetLaneId
    ) {
        SummonMonsterType unit = unit(game, tower.summonId()).orElse(null);
        if (unit == null) {
            return Optional.empty();
        }
        Monster monster = unit.createMonster(new SummonContext(game, owner), targetTeam, targetLaneId, 1);
        monster.setData(DISPATCH, true);
        int round = game.currentRound();
        monster.applyAugmentBodyModifiers(IncomeTowerBalance.healthMultiplier(tower.level(), round),
                IncomeTowerBalance.attackDamageMultiplier(tower.level(), round));
        return Optional.of(monster);
    }

    private static IncomeTower ownedTower(Context context, UUID playerId, GridPosition position) {
        Tower tower = position == null ? null : context.lane.towerAt(position);
        return tower instanceof IncomeTower income && income.ownerPlayer().equals(playerId) ? income : null;
    }

    private static Result ownershipFailure(Context context, UUID playerId, GridPosition position) {
        Tower tower = position == null ? null : context.lane.towerAt(position);
        return tower instanceof IncomeTower ? Result.NOT_OWNED : Result.NO_TOWER;
    }

    private static Context context(SemionGame game, UUID playerId, boolean prepareOnly) {
        if (prepareOnly && game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return Context.failure(Result.INVALID_PHASE);
        }
        SemionPlayer player = game.players().get(playerId);
        if (player == null) {
            return Context.failure(Result.PLAYER_NOT_IN_GAME);
        }
        SemionTeam team = game.teams().get(player.teamId());
        if (team == null || team.eliminated()) {
            return Context.failure(Result.PLAYER_TEAM_ELIMINATED);
        }
        Optional<PlayerLane> lane = team.laneGroup().lane(player.laneId());
        if (lane.isEmpty()) {
            return Context.failure(Result.UNKNOWN_LANE);
        }
        return new Context(player, lane.get(), null);
    }

    private record Context(SemionPlayer player, PlayerLane lane, Result failure) {
        static Context failure(Result result) {
            return new Context(null, null, result);
        }
    }
}
