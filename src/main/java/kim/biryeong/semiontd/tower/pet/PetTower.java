package kim.biryeong.semiontd.tower.pet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
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
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Every pet builder tower, owner and companion alike.
 *
 * <p>Owners never attack; they hand out bond each round, and bond is the only thing that makes a
 * companion stronger over time. Species boosts (pack, solo, heal) sit on top of the bond multiplier.
 */
public class PetTower extends ProductionTower {
    static final String LARGE_YARD = "job_pet_towers_s";
    static final String LEADER = "job_pet_towers_g1";
    static final String GROWN_UP = "job_pet_towers_g2";
    static final String FAMILY = "job_pet_towers_p";
    private GridPosition loyalOwnerPosition;
    private TowerType loyalOwnerType;
    private int yardCompanions;
    private int packSize;
    private boolean soloCat;
    private boolean ownerActive;
    private boolean familyYard;
    private int leaderHits;

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
        return isCompanion() && (required <= 0.0 || bond >= required);
    }

    public final boolean hasAdultCombatAbilities() {
        return isAdult() || (isCompanion() && augmentSnapshot().has(GROWN_UP));
    }

    public final int yardRadius() {
        return augmentSnapshot().has(LARGE_YARD)
                ? Math.max(1, (int) augmentSnapshot().parameter(LARGE_YARD, "yardRadius", 2))
                : PetBalance.YARD_RADIUS;
    }

    public final boolean hasFamilyYard() {
        return familyYard;
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
        return base * (isAdult() ? PetBalance.ADULT_SCALE : PetBalance.PUP_SCALE);
    }

    final void bindLoyalOwner(GridPosition position) {
        loyalOwnerPosition = position;
    }

    final void updateYardState(int yardCompanions, int packSize, boolean soloCat, boolean ownerActive,
                               TowerType ownerType) {
        updateYardState(yardCompanions, packSize, soloCat, ownerActive, ownerType, false);
    }

    final void updateYardState(int yardCompanions, int packSize, boolean soloCat, boolean ownerActive,
                               TowerType ownerType, boolean familyYard) {
        double previousMaxHealth = currentMaxHealth();
        double healthRatio = previousMaxHealth <= 0.0 ? 0.0 : health() / previousMaxHealth;
        this.yardCompanions = yardCompanions;
        this.packSize = packSize;
        this.soloCat = soloCat;
        this.ownerActive = ownerActive;
        this.loyalOwnerType = ownerType;
        this.familyYard = familyYard;
        double nextMaxHealth = currentMaxHealth();
        if (health() > 0.0 && Math.abs(nextMaxHealth - previousMaxHealth) > 1.0E-9) {
            syncHealth(nextMaxHealth * healthRatio);
            if (currentLane != null) {
                onStateChanged(currentLane);
            }
        }
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
        leaderHits = 0;
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
        if (role() == PetRole.BIRD && dealtDamage > 0.0) {
            healYardmate(towerEntity, dealtDamage * PetBalance.healRatio(type()));
        }
        if (role() == PetRole.CAT && hasAdultCombatAbilities() && dealtDamage > 0.0) {
            splash(towerEntity, target, resolvedOutgoingDamage);
        }
        if (dealtDamage > 0.0 && AugmentCombat.allowsTriggers() && isCompanion()
                && augmentSnapshot().has(LEADER)
                && logicalId().equals(augmentSnapshot().choice(LEADER).primaryTargetId())) {
            leaderHits++;
            if (leaderHits >= Math.max(1, (int) augmentSnapshot().parameter(LEADER, "hitsRequired", 3))) {
                leaderHits = 0;
                rallyYardmates(target);
            }
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        leaderHits = 0;
        super.resetForRound(lane);
        PetBondService.refresh(lane);
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        super.onKill(towerEntity, target, damageAmount);
        recordPraise();
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
        if (!isCompanion()) {
            return base;
        }
        double multiplier = PetBalance.healthMultiplier(bond);
        if (role() == PetRole.DOG || familyYard) {
            multiplier *= 1.0 + PetBalance.packHealthBonus(packBonusType(), packSize);
        }
        return base * multiplier;
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        double damage = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        if (role() != PetRole.DOG || !hasAdultCombatAbilities()) {
            return damage;
        }
        return damage * (1.0 - PetBalance.adultDamageReduction(type()));
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
            lines.add("마당 범위 " + yardRadius() + "칸");
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
        lines.add("마당 반려 " + yardCompanions + "/" + ((yardRadius() * 2 + 1) * (yardRadius() * 2 + 1) - 1));
        if (familyYard) {
            lines.add("우리 가족 활성: 모든 종 무리 참여, 고양이 독립, 새 동시 회복");
        }
        if (augmentSnapshot().has(LEADER)
                && logicalId().equals(augmentSnapshot().choice(LEADER).primaryTargetId())) {
            lines.add("우리 동네 대장: 공동 공격 적중 " + leaderHits + "/"
                    + (int) augmentSnapshot().parameter(LEADER, "hitsRequired", 3));
        }
        switch (role()) {
            case DOG -> {
                lines.add("무리 " + packSize + "마리, 공격력 +"
                        + percent(PetBalance.packBonus(type(), packSize)) + ", 체력 +"
                        + percent(PetBalance.packHealthBonus(type(), packSize)));
                if (hasAdultCombatAbilities()) {
                    lines.add("성체 효과 받는 피해 -" + percent(PetBalance.adultDamageReduction(type())));
                }
            }
            case CAT -> {
                lines.add("독립 " + (soloCat
                        ? "활성, 공격력 +" + percent(PetBalance.soloBonus(type()))
                        : "비활성 (같은 마당에 다른 고양이가 있습니다)"));
                if (hasAdultCombatAbilities()) {
                    lines.add("성체 효과 스플래시 " + oneDecimal(PetBalance.adultSplashRadius(type()))
                            + "칸, 최대 " + PetBalance.adultSplashMaxTargets(type()) + "마리, 피해 "
                            + percent(PetBalance.adultSplashDamageRatio(type())));
                }
            }
            case BIRD -> {
                int targets = familyYard ? (int) augmentSnapshot().parameter(FAMILY, "healTargets", 3)
                        : (int) augmentSnapshot().parameter(GROWN_UP, "healTargets", 1);
                lines.add("회복 입힌 피해의 " + percent(PetBalance.healRatio(type()))
                        + ", " + (augmentSnapshot().has(GROWN_UP) ? "자신 + 다른 반려 " : "반려 ")
                        + "최대 " + targets + "마리");
            }
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
            familyYard = previous.familyYard;
            leaderHits = previous.leaderHits;
            bond = previous.bond;
            killsTowardPraise = previous.killsTowardPraise;
            praiseThisRound = previous.praiseThisRound;
            currentLane = previous.currentLane;
        }
    }

    final double companionDamageMultiplier() {
        double multiplier = PetBalance.attackMultiplier(bond);
        switch (role()) {
            case CAT -> {
                if (soloCat) {
                    multiplier *= 1.0 + PetBalance.soloBonus(type());
                }
            }
            default -> {
            }
        }
        if (role() == PetRole.DOG || familyYard) {
            multiplier *= 1.0 + PetBalance.packBonus(packBonusType(), packSize);
        }
        if (isLost()) {
            multiplier *= PetBalance.lostPetMultiplier();
        }
        return multiplier;
    }

    private TowerType packBonusType() {
        return switch (PetTowers.tier(type())) {
            case 1 -> PetTowers.DOG_T1;
            case 2 -> PetTowers.DOG_T2;
            default -> PetTowers.DOG_T3;
        };
    }

    private void rallyYardmates(SemionMonsterEntity target) {
        if (currentLane == null || target == null) {
            return;
        }
        EnumSet<PetRole> species = EnumSet.of(role());
        int remaining = Math.max(0, (int) augmentSnapshot().parameter(LEADER, "maxAllies", 2));
        for (Tower tower : currentLane.towers()) {
            if (!(tower instanceof PetTower pet) || !pet.isCompanion() || pet.health() <= 0.0
                    || !sharesYard(pet) || species.contains(pet.role())) {
                continue;
            }
            SemionTowerEntity ally = towerEntityOf(pet, currentLane);
            if (ally == null || !ally.isValidAttackTarget(target)
                    || kim.biryeong.semiontd.tower.succubus.SuccubusDreams.isAsleep(ally)
                    || !pet.canAttackTarget(ally, target)
                    || ally.distanceToSqr(target) > ally.attackRange() * ally.attackRange()) {
                continue;
            }
            if (remaining-- <= 0) {
                break;
            }
            species.add(pet.role());
            AugmentCombat.additionalAttack(ally, target, augmentSnapshot().parameter(LEADER, "damageRatio", 1));
        }
    }

    /** Bond is capped, and growing the cap must not leave the tower sitting below its new maximum. */
    final void addBond(double amount) {
        if (!isCompanion() || !Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        double cap = bondCap();
        boolean wasAdult = isAdult();
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
            if (!wasAdult && isAdult()) {
                PetBondService.refresh(currentLane);
            }
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

    private void splash(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double resolvedOutgoingDamage
    ) {
        double radius = PetBalance.adultSplashRadius(type());
        int maxTargets = PetBalance.adultSplashMaxTargets(type());
        double ratio = PetBalance.adultSplashDamageRatio(type());
        if (source == null || primary == null || resolvedOutgoingDamage <= 0.0
                || radius <= 0.0 || maxTargets <= 0 || ratio <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "adult_cat_splash"),
                source,
                primary,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        ).nearestTargets(maxTargets);
        TowerAreaDamage.applyResolved(
                this,
                source,
                request,
                ignored -> resolvedOutgoingDamage * ratio,
                true,
                (target, damage, killed) -> {},
                type().primaryDamageType()
        );
    }

    private void healYardmate(SemionTowerEntity source, double amount) {
        if (amount <= 0.0 || currentLane == null) {
            return;
        }
        boolean selfHeal = augmentSnapshot().has(GROWN_UP);
        int maxPatients = familyYard ? Math.max(1, (int) augmentSnapshot().parameter(FAMILY, "healTargets", 3))
                : selfHeal ? Math.max(1, (int) augmentSnapshot().parameter(GROWN_UP, "healTargets", 1)) : 1;
        List<PetTower> patients = currentLane.towers().stream()
                .filter(PetTower.class::isInstance)
                .map(PetTower.class::cast)
                .filter(PetTower::isCompanion)
                .filter(other -> other.health() > 0.0)
                .filter(this::sharesYard)
                .filter(other -> !selfHeal || other != this)
                .filter(other -> other.health() < other.currentMaxHealth())
                .sorted(Comparator.comparingDouble(PetTower::healthRatio))
                .limit(maxPatients).toList();
        if (selfHeal) {
            healPatient(source, this, amount);
        }
        patients.forEach(patient -> healPatient(source, patient, amount));
    }

    private void healPatient(SemionTowerEntity source, PetTower patient, double amount) {
        SemionTowerEntity target = towerEntityOf(patient, currentLane);
        if (target != null && healTarget(target, amount)) {
            TowerVfxService.showPetHeal(source, target);
        }
    }

    private boolean sharesYard(PetTower other) {
        return loyalOwnerPosition != null && loyalOwnerPosition.equals(other.loyalOwnerPosition())
                && ownerPlayer().equals(other.ownerPlayer()) && teamId() == other.teamId() && laneId() == other.laneId();
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
