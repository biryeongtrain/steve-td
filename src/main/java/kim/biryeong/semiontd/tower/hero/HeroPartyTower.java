package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.server.level.ServerPlayer;

public abstract class HeroPartyTower extends ProductionTower {
    protected HeroPartyTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        applyPartyMaxHealth(false);
        super.onPlaced(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        entity.setCustomNameVisible(false);
        HeroPlayerVisuals.attach(entity, this);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        HeroPlayerVisuals.refresh(this);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        HeroPlayerVisuals.remove(this);
        super.onRemoved(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        HeroPartyStates.state(ownerPlayer()).recordPartyDeath();
        HeroPlayerVisuals.remove(this);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        HeroPlayerVisuals.tick(this);
    }

    @Override
    public DamageResult damageTargetResult(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage) {
        return super.damageTargetResult(towerEntity, target, baseDamage, primaryDamageType());
    }

    @Override
    public double modifyOutgoingDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount * HeroPartyBalance.partyDamageMultiplier(state().adventurePoints());
    }

    @Override
    public List<String> runtimeDetailLines() {
        HeroPartyState state = state();
        return List.of(
                "모험 점수: " + state.adventurePoints(),
                "파티 공격/회복: +" + oneDecimal((HeroPartyBalance.partyDamageMultiplier(state.adventurePoints()) - 1.0) * 100.0) + "%",
                "파티 최대 체력: +" + oneDecimal((HeroPartyBalance.partyHealthMultiplier(state.adventurePoints()) - 1.0) * 100.0) + "%"
        );
    }

    public final void refreshPartyStats(PlayerLane lane) {
        applyPartyMaxHealth(true);
        onStateChanged(lane);
    }

    protected final HeroPartyState state() {
        return HeroPartyStates.state(ownerPlayer());
    }

    protected double bonusFlatHealth() {
        return 0.0;
    }

    protected final List<SemionTowerEntity> partyEntities(SemionTowerEntity source) {
        if (source == null) {
            return List.of();
        }
        ArrayList<SemionTowerEntity> party = new ArrayList<>(source.level().getEntitiesOfClass(
                SemionTowerEntity.class,
                source.getBoundingBox().inflate(96.0),
                entity -> entity.isAlive()
                        && ownerPlayer().equals(entity.ownerPlayer())
                        && entity.runtimeTower() != null
                        && HeroPartyTowers.isHeroPartyTower(entity.runtimeTower().type())
        ));
        party.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(source)));
        return List.copyOf(party);
    }

    protected final double healPartyMember(SemionTowerEntity target, double amount) {
        if (target == null || amount <= 0.0) {
            return 0.0;
        }
        double previous = target.getHealth();
        target.receiveHealing(amount * HeroPartyBalance.partyHealingMultiplier(state().adventurePoints()));
        return Math.max(0.0, target.getHealth() - previous);
    }

    protected final ServerPlayer onlineOwner(SemionTowerEntity source) {
        return source == null || source.getServer() == null
                ? null
                : source.getServer().getPlayerList().getPlayer(ownerPlayer());
    }

    protected final SemionTowerEntity towerEntity(PlayerLane lane, Tower tower) {
        if (lane == null || lane.arenaWorld() == null || !(tower instanceof HeroPartyTower heroTower)) {
            return null;
        }
        return heroTower.entityId().isPresent()
                && lane.arenaWorld().getEntity(heroTower.entityId().getAsInt()) instanceof SemionTowerEntity entity
                ? entity
                : null;
    }

    private void applyPartyMaxHealth(boolean preserveRatio) {
        double previousMaximum = Math.max(1.0, currentMaxHealth());
        double ratio = preserveRatio ? health() / previousMaximum : 1.0;
        double next = type().maxHealth() * HeroPartyBalance.partyHealthMultiplier(state().adventurePoints())
                + Math.max(0.0, bonusFlatHealth());
        syncMaxHealth(next, false);
        syncHealth(currentMaxHealth() * Math.max(0.0, Math.min(1.0, ratio)));
    }
}
