package kim.biryeong.semiontd.tower.mage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;

public final class MageProphetTower extends ProductionTower {
    private PlayerLane currentLane;
    private boolean armed;
    private boolean succeeded;

    public MageProphetTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition position) {
        super(type, owner, team, laneId, position);
    }

    public MageProphetTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition original, GridPosition current) {
        super(type, owner, team, laneId, original, current);
    }

    Optional<String> prediction() {
        return MageTowers.predictionFor(type());
    }

    boolean succeeded() {
        return succeeded;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previous) {
        if (previous instanceof MageProphetTower prophet) {
            double ratio = prophet.currentMaxHealth() <= 0.0 ? 1.0 : prophet.health() / prophet.currentMaxHealth();
            syncHealth(currentMaxHealth() * Math.max(0.0, Math.min(1.0, ratio)));
        }
        armed = false;
        succeeded = false;
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        return prediction().isEmpty()
                && MageTowers.predictionFor(option.targetType()).isPresent()
                && MageTowerRuntime.hasCore(lane, ownerPlayer());
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        currentLane = lane;
        armed = prediction().isPresent() && !isDestroyed(lane);
        succeeded = false;
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        if (!armed || succeeded || isDestroyed(lane) || !MageTowerRuntime.hasCore(lane, ownerPlayer())) {
            super.tick(lane);
            return;
        }
        String expected = prediction().orElse(null);
        SemionMonsterEntity target = MageTowerRuntime.liveMonsters(lane).stream()
                .filter(entity -> MageTowerRuntime.isIncome(entity.runtimeMonster()))
                .filter(entity -> expected.equals(entity.runtimeMonster().id()))
                .findFirst()
                .orElse(null);
        SemionTowerEntity source = MageTowerRuntime.entity(lane, this);
        if (target == null || source == null) {
            return;
        }
        DamageResult result = damageResolvedTargetResult(
                source, target, Math.max(1.0, target.runtimeMonster().maxHealth() * 1_000_000.0), DamageType.TRUE
        );
        if (result.killed()) {
            TowerVfxService.showProphecyLightning(source, target);
            onKill(source, target, result.outgoingDamage());
            succeeded = true;
            MageStates.state(ownerPlayer()).addMana(intAbility("prophecyReward", MageBalance.PROPHECY_REWARD));
        }
    }

    int naturalManaProduction() {
        return armed && !succeeded ? 0 : intAbility("prophetMana", MageBalance.PROPHET_MANA);
    }

    @Override
    public List<String> runtimeDetailLines() {
        MageStates.PlayerState state = MageStates.state(ownerPlayer());
        List<String> lines = new ArrayList<>();
        lines.add("<aqua>마나</aqua> <white>" + state.mana() + "/" + state.capacity() + "</white>");
        lines.add(prediction().map(value -> "<light_purple>예언</light_purple> <white>"
                        + type().displayName().replaceFirst("^예언: ", "") + "</white>")
                .orElse("<gray>예언 없음</gray>"));
        if (succeeded) {
            lines.add("<green>예언 성공</green> <white>+" + intAbility("prophecyReward", MageBalance.PROPHECY_REWARD) + " 마나</white>");
        } else if (armed) {
            lines.add("<yellow>예언 대기 중</yellow>");
        }
        lines.add("<green>예상 자연 생산</green> <white>+" + naturalManaProduction() + "</white>");
        return List.copyOf(lines);
    }

    private static int intAbility(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(MageBalance.GLOBAL_ID, key, fallback);
    }
}
