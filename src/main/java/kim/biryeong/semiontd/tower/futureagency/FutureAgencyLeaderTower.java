package kim.biryeong.semiontd.tower.futureagency;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;

public final class FutureAgencyLeaderTower extends ProductionTower {
    public static final String RECONSTRUCT = "reconstruct_future_agency";
    public static final String PROMOTE_COMMANDER = "promote_future_commander";
    public static final String SAVE_WORLD = "save_the_world";
    private double copiedHealthRatio = 1.0;

    public FutureAgencyLeaderTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
                                   GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override public boolean canBeSold() {return false;}

    @Override
    public void onPlaced(PlayerLane lane) {
        laneForDetails = lane;
        syncHealth(currentMaxHealth() * copiedHealthRatio);
        copiedHealthRatio = 1.0;
        super.onPlaced(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        copiedHealthRatio = previousTower.health() / Math.max(1.0, previousTower.currentMaxHealth());
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        if (RECONSTRUCT.equals(option.id())) return type().id().equals(FutureAgencyTowers.ESCAPEE.id()) && !state.reconstructed();
        if (PROMOTE_COMMANDER.equals(option.id())) return type().id().equals(FutureAgencyTowers.REBUILDER.id())
                && state.policySelections() >= 5;
        if (SAVE_WORLD.equals(option.id())) return type().id().equals(FutureAgencyTowers.COMMANDER.id())
                && canSaveWorld(state, lane);
        return FutureAgencyPolicy.fromUpgradeId(option.id()).map(state::canChoose).orElse(false);
    }

    @Override
    public boolean showsUnavailableUpgrade(PlayerLane lane, TowerUpgradeOption option) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        return SAVE_WORLD.equals(option.id()) && state.reconstructed() && !state.worldSaved();
    }

    @Override
    public void onUpgradeApplied(PlayerLane lane, TowerUpgradeOption option) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        boolean worldSaved = state.worldSaved();
        if (RECONSTRUCT.equals(option.id())) state.reconstruct();
        else if (PROMOTE_COMMANDER.equals(option.id())) state.promoteCommander();
        else if (SAVE_WORLD.equals(option.id()) && canSaveWorld(state, lane)) state.saveWorld();
        else FutureAgencyPolicy.fromUpgradeId(option.id()).ifPresent(state::choose);
        refreshAgents(lane);
        if (!worldSaved && state.worldSaved()) showWorldSaveVfx(lane);
    }

    @Override
    public boolean upgradeCostAddsToSaleValue(TowerUpgradeOption option) {
        return !FutureAgencyPolicy.fromUpgradeId(option.id()).isPresent() && !SAVE_WORLD.equals(option.id());
    }

    @Override
    public List<String> upgradeTooltipLines(TowerUpgradeOption option) {
        if (SAVE_WORLD.equals(option.id())) {
            FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
            int roles = laneForDetails == null ? 0 : firstGradeRoles(laneForDetails);
            return List.of(
                    "<gold>이번에야말로.</gold>",
                    condition("기관 최고 지휘자", state.commander()),
                    progress("정책", state.policySelections(), 10),
                    progress("1급 역할", roles, 3),
                    "<gray>비용</gray> <gold>1500 다이아</gold>"
            );
        }
        return FutureAgencyPolicy.fromUpgradeId(option.id())
                .map(policy -> List.of(policy.configuredDescription()))
                .orElseGet(List::of);
    }

    @Override
    public List<String> runtimeDetailLines() {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        ArrayList<String> lines = new ArrayList<>();
        lines.add("<gold>기관 단계</gold> <white>" + switch (state.stage()) {
            case ESCAPEE -> "2054년으로부터의 도피자";
            case REBUILDER -> "기관 재건자";
            case COMMANDER -> "기관 최고 지휘자";
        } + "</white>");
        if (state.worldSaved()) lines.add("<red>세계 구원</red> <green>완료</green>");
        if (!state.offers().isEmpty()) {
            lines.add("<gold>이번 정책 " + state.selectionNumber() + "/" + state.selectionLimit() + "</gold> "
                    + state.offers().stream()
                    .map(FutureAgencyPolicy::displayName).collect(java.util.stream.Collectors.joining(" / ")));
        } else if (state.reconstructed()) lines.add("<gold>이번 정책</gold> <gray>선택 완료</gray>");
        List<String> selectedPolicies = state.policyStacks().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().ordinal()))
                .map(entry -> entry.getKey().displayName() + (entry.getValue() > 1 ? "×" + entry.getValue() : ""))
                .toList();
        for (int index = 0; index < selectedPolicies.size(); index += 4) {
            String prefix = index == 0 ? "<gold>선택 정책</gold> " : "<gray>계속</gray> ";
            lines.add(prefix + String.join(" / ", selectedPolicies.subList(index,
                    Math.min(index + 4, selectedPolicies.size()))));
        }
        return List.copyOf(lines);
    }

    private transient PlayerLane laneForDetails;

    @Override
    public void tick(PlayerLane lane) {
        laneForDetails = lane;
        super.tick(lane);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity source,
                                     kim.biryeong.semiontd.entity.monster.SemionMonsterEntity target,
                                     double damage) {
        return damage * (1.0 + FutureAgencyBalance.survivorDamage(
                FutureAgencyStates.state(ownerPlayer()), laneForDetails, ownerPlayer()));
    }

    private boolean canSaveWorld(FutureAgencyStates.PlayerState state, PlayerLane lane) {
        return !state.worldSaved() && state.commander() && state.policySelections() >= 10 && firstGradeRoles(lane) == 3;
    }

    private int firstGradeRoles(PlayerLane lane) {
        if (lane == null) return 0;
        java.util.EnumSet<FutureAgencyRole> roles = java.util.EnumSet.noneOf(FutureAgencyRole.class);
        lane.towers().stream().filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> FutureAgencyTowers.grade(tower.type()) == 1)
                .map(tower -> FutureAgencyTowers.role(tower.type())).filter(java.util.Objects::nonNull).forEach(roles::add);
        return roles.size();
    }

    private void refreshAgents(PlayerLane lane) {
        if (lane == null) return;
        for (Tower tower : lane.towers()) {
            if (tower instanceof FutureAgencyAgentTower agent && ownerPlayer().equals(agent.ownerPlayer())) {
                agent.refreshPolicyHealth(true);
                agent.onStateChanged(lane);
            }
        }
    }

    private void showWorldSaveVfx(PlayerLane lane) {
        if (lane == null) return;
        List<SemionTowerEntity> towers = lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer())
                        && FutureAgencyTowers.isFutureAgencyTower(tower.type()))
                .map(tower -> tower instanceof ProductionTower production && production.entityId().isPresent()
                        ? lane.arenaWorld().getEntity(production.entityId().getAsInt()) : null)
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .toList();
        TowerVfxService.showTranscendence(towers);
    }

    private static String condition(String label, boolean met) {
        return (met ? "<green>" : "<red>") + label + " " + (met ? "완료" : "필요")
                + (met ? "</green>" : "</red>");
    }

    private static String progress(String label, int current, int required) {
        boolean met = current >= required;
        return (met ? "<green>" : "<red>") + label + " " + current + "/" + required
                + (met ? "</green>" : "</red>");
    }
}
