package kim.biryeong.semiontd.tower.pet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;

/**
 * Every pet builder tower, owner and companion alike.
 *
 * <p>Owners never attack; they hand out bond each round, and bond is the only thing that makes a
 * companion stronger over time. Species boosts (pack, solo, heal) sit on top of the bond multiplier.
 */
public class PetTower extends ProductionTower {
    private GridPosition loyalOwnerPosition;
    private TowerType loyalOwnerType;
    private int yardCompanions;
    private int packSize;
    private boolean soloCat;
    private boolean ownerActive;

    private double bond;
    private int killsTowardPraise;
    private double praiseThisRound;
    private PlayerLane currentLane;

    public PetTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PetTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public final PetRole role() {
        return PetTowers.roleOf(type());
    }

    public final boolean isOwner() {
        return PetTowers.isOwner(type());
    }

    public final boolean isCompanion() {
        return PetTowers.isCompanion(type());
    }

    /** Grid position of the owner tower this companion is loyal to, or null when it has none. */
    public final GridPosition loyalOwnerPosition() {
        return loyalOwnerPosition;
    }

    public final TowerType loyalOwnerType() {
        return loyalOwnerType;
    }

    /** Companions counted in the yard this companion belongs to, including itself. */
    public final int yardCompanions() {
        return yardCompanions;
    }

    /** Dogs in this dog's connected pack, counting itself; 0 for other species. */
    public final int packSize() {
        return packSize;
    }

    public final boolean isSoloCat() {
        return soloCat;
    }

    public final boolean hasActiveOwner() {
        return ownerActive;
    }

    /** A companion with no living owner keeps only a fraction of its output. */
    public final boolean isLost() {
        return isCompanion() && !ownerActive;
    }

    public final double bond() {
        return bond;
    }

    public final double bondCap() {
        return PetBalance.bondCap(type(), loyalOwnerType);
    }

    /** True once the companion has grown enough to unlock its next tier. */
    public final boolean isAdult() {
        double required = PetBalance.bondToUpgrade(type());
        return required > 0.0 && bond >= required;
    }

    /**
     * Companions are placed as pups and visibly grow up. Reaching adult size doubles as the
     * "ready to upgrade" signal, so the player can read it off the board instead of the menu.
     */
    public final double renderScale() {
        double base = type().visual().scale();
        if (!isCompanion()) {
            return base;
        }
        boolean grown = PetBalance.bondToUpgrade(type()) <= 0.0 || isAdult();
        return base * (grown ? PetBalance.ADULT_SCALE : PetBalance.PUP_SCALE);
    }

    final void bindLoyalOwner(GridPosition position) {
        loyalOwnerPosition = position;
    }

    final void updateYardState(int yardCompanions, int packSize, boolean soloCat, boolean ownerActive,
                               TowerType ownerType) {
        this.yardCompanions = yardCompanions;
        this.packSize = packSize;
        this.soloCat = soloCat;
        this.ownerActive = ownerActive;
        this.loyalOwnerType = ownerType;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
        PetBondService.refresh(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        super.configureEntityAfterSpawn(entity, lane);
        if (isOwner()) {
            FakePlayerTowerVisuals.attach(entity, this);
        }
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        if (isOwner()) {
            FakePlayerTowerVisuals.refresh(this);
        }
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        currentLane = lane;
        if (isOwner()) {
            FakePlayerTowerVisuals.tick(this);
        }
    }

    @Override
    public void onDeath(PlayerLane lane) {
        super.onDeath(lane);
        if (isOwner()) {
            FakePlayerTowerVisuals.remove(this);
        }
        PetBondService.refresh(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        if (isOwner()) {
            FakePlayerTowerVisuals.remove(this);
        }
        super.onRemoved(lane);
        PetBondService.refresh(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        currentLane = lane;
        PetBondService.refresh(lane);
        praiseThisRound = 0.0;
        if (!isCompanion() || !ownerActive) {
            return;
        }
        addBond(PetBalance.bondPerRound()
                + PetBalance.bondGrant(loyalOwnerType, yardCompanions)
                + PetBalance.walkBond(loyalOwnerType, yardCompanions));
    }

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        super.onAttackResolved(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (killedTarget) {
            recordPraise();
        }
        if (role() == PetRole.BIRD && dealtDamage > 0.0) {
            healYardmate(towerEntity, dealtDamage * PetBalance.healRatio(type()));
        }
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double damage = super.modifyAttackDamage(towerEntity, target, damageAmount);
        if (!isCompanion()) {
            return damage;
        }
        return damage * companionDamageMultiplier();
    }

    @Override
    protected double builderCurrentMaxHealth() {
        double base = super.builderCurrentMaxHealth();
        return isCompanion() ? base * PetBalance.healthMultiplier(bond) : base;
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        if (option == null || !isCompanion()) {
            return super.meetsUpgradeRequirements(lane, option);
        }
        return isAdult() && super.meetsUpgradeRequirements(lane, option);
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>(super.runtimeDetailLines());
        if (isOwner()) {
            return lines;
        }
        if (isLost()) {
            lines.add("길잃음 주인이 없어 출력 " + percent(PetBalance.lostPetMultiplier()));
            return lines;
        }
        lines.add("유대 " + oneDecimal(bond) + "/" + oneDecimal(bondCap())
                + " (공격력 +" + percent(PetBalance.attackMultiplier(bond) - 1.0)
                + ", 체력 +" + percent(PetBalance.healthMultiplier(bond) - 1.0) + ")");
        double required = PetBalance.bondToUpgrade(type());
        if (required > 0.0) {
            lines.add("승급 자격 " + (isAdult() ? "충족 (성체)" : "유대 " + oneDecimal(required) + " 필요"));
        }
        lines.add("마당 반려 " + yardCompanions + "/" + PetBalance.YARD_TILES);
        switch (role()) {
            case DOG -> lines.add("무리 " + packSize + "마리, 공격력 +"
                    + percent(PetBalance.packBonus(type(), packSize)));
            case CAT -> lines.add("독립 " + (soloCat
                    ? "활성, 공격력 +" + percent(PetBalance.soloBonus(type()))
                    : "비활성 (같은 마당에 다른 고양이가 있습니다)"));
            case BIRD -> lines.add("회복 입힌 피해의 " + percent(PetBalance.healRatio(type())));
            default -> {
            }
        }
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        super.copyRuntimeStateFrom(previousTower);
        if (previousTower instanceof PetTower previous) {
            loyalOwnerPosition = previous.loyalOwnerPosition;
            loyalOwnerType = previous.loyalOwnerType;
            yardCompanions = previous.yardCompanions;
            packSize = previous.packSize;
            soloCat = previous.soloCat;
            ownerActive = previous.ownerActive;
            bond = previous.bond;
            killsTowardPraise = previous.killsTowardPraise;
            praiseThisRound = previous.praiseThisRound;
            currentLane = previous.currentLane;
        }
    }

    final double companionDamageMultiplier() {
        double multiplier = PetBalance.attackMultiplier(bond);
        switch (role()) {
            case DOG -> multiplier *= 1.0 + PetBalance.packBonus(type(), packSize);
            case CAT -> {
                if (soloCat) {
                    multiplier *= 1.0 + PetBalance.soloBonus(type());
                }
            }
            default -> {
            }
        }
        if (isLost()) {
            multiplier *= PetBalance.lostPetMultiplier();
        }
        return multiplier;
    }

    /** Bond is capped, and growing the cap must not leave the tower sitting below its new maximum. */
    final void addBond(double amount) {
        if (!isCompanion() || !Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        double cap = bondCap();
        double previousMaxHealth = currentMaxHealth();
        double previousBond = bond;
        bond = cap > 0.0 ? Math.min(cap, bond + amount) : bond + amount;
        if (bond <= previousBond) {
            return;
        }
        double gainedMaxHealth = currentMaxHealth() - previousMaxHealth;
        if (gainedMaxHealth > 0.0 && health() > 0.0) {
            syncHealth(health() + gainedMaxHealth);
        }
        if (currentLane != null) {
            onStateChanged(currentLane);
        }
    }

    private void recordPraise() {
        if (!isCompanion()) {
            return;
        }
        int perBond = PetBalance.praiseKillsPerBond();
        double cap = PetBalance.praiseCapPerRound();
        killsTowardPraise++;
        while (killsTowardPraise >= perBond) {
            killsTowardPraise -= perBond;
            if (praiseThisRound >= cap) {
                // Past the round's cap the kill is still spent, so a big round cannot bank praise
                // and dump it into the next one the moment the cap resets.
                continue;
            }
            praiseThisRound += 1.0;
            addBond(1.0);
        }
    }

    private void healYardmate(SemionTowerEntity source, double amount) {
        if (amount <= 0.0 || currentLane == null) {
            return;
        }
        PetTower patient = currentLane.towers().stream()
                .filter(PetTower.class::isInstance)
                .map(PetTower.class::cast)
                .filter(PetTower::isCompanion)
                .filter(other -> other.health() > 0.0)
                .filter(this::sharesYard)
                .filter(other -> other.health() < other.currentMaxHealth())
                .min(Comparator.comparingDouble(PetTower::healthRatio))
                .orElse(null);
        if (patient == null) {
            return;
        }
        SemionTowerEntity target = towerEntityOf(patient, currentLane);
        if (target != null && healTarget(target, amount)) {
            TowerVfxService.showPetHeal(source, target);
        }
    }

    private boolean sharesYard(PetTower other) {
        return loyalOwnerPosition != null && loyalOwnerPosition.equals(other.loyalOwnerPosition());
    }

    private double healthRatio() {
        double max = currentMaxHealth();
        return max <= 0.0 ? 1.0 : health() / max;
    }

    private static SemionTowerEntity towerEntityOf(Tower tower, PlayerLane lane) {
        if (!(tower instanceof EntityBackedTower backed) || lane == null || lane.arenaWorld() == null) {
            return null;
        }
        if (backed.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity
                ? towerEntity
                : null;
    }
}
