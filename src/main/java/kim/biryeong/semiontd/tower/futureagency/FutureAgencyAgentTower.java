package kim.biryeong.semiontd.tower.futureagency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public final class FutureAgencyAgentTower extends ProductionTower {
    private transient PlayerLane lane;
    private boolean withdrawn;
    private GridPosition carriedPosition;
    private double carriedHealth;
    private double copiedHealthRatio = 1.0;
    private boolean restoringCarry;
    private boolean carriedCopy;
    private boolean waveActive;

    public FutureAgencyAgentTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
                                  GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        this.lane = lane;
        if (withdrawn) return;
        boolean showArrival = restoringCarry;
        refreshPolicyHealth(false);
        if (restoringCarry && carriedPosition != null && carriedHealth > 0.0) {
            syncPosition(carriedPosition);
            syncHealth(carriedHealth);
        } else {
            syncHealth(currentMaxHealth() * copiedHealthRatio);
        }
        copiedHealthRatio = 1.0;
        super.onPlaced(lane);
        if (showArrival) showCarryVfx(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        this.lane = lane;
        if (!withdrawn) super.tick(lane);
    }

    @Override
    public GridPosition managementPosition() {return carriedCopy ? position() : originalPosition();}

    @Override
    public boolean reservesPlacementPosition(GridPosition position) {
        return !carriedCopy && position != null
                && (originalPosition().equals(position) || position().equals(this.position()));
    }

    @Override
    public int slotWeight() {return carriedCopy ? 0 : 1;}

    @Override
    public boolean canBeSold() {return !carriedCopy;}

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, kim.biryeong.semiontd.tower.TowerUpgradeOption option) {
        return !carriedCopy;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return FutureAgencyStates.state(ownerPlayer()).worldSaved();
    }

    @Override
    public boolean countsForLaneDefense() {return !withdrawn;}

    @Override
    public void onLaneCleared(PlayerLane lane) {
        if (carriedCopy && isDestroyed(lane)) {
            if (lane != null) lane.removeTower(this);
            return;
        }
        carryIntoNextRound(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        this.lane = lane;
        waveActive = true;
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        if (FutureAgencyStates.state(ownerPlayer()).worldSaved()) super.moveToFinalDefense(lane, position);
        else carryIntoNextRound(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        boolean restoreCarry = carriedCopy && withdrawn && !FutureAgencyStates.state(ownerPlayer()).worldSaved()
                && carriedPosition != null && carriedHealth > 0.0;
        waveActive = false;
        withdrawn = false;
        restoringCarry = restoreCarry;
        super.resetForRound(lane);
        refreshPolicyHealth(false);
        if (restoreCarry && lane == null) {
            syncPosition(carriedPosition);
            syncHealth(carriedHealth);
        }
        restoringCarry = false;
        carriedPosition = null;
        carriedHealth = 0.0;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof FutureAgencyAgentTower previous)) return;
        withdrawn = previous.withdrawn;
        carriedPosition = previous.carriedPosition;
        carriedHealth = previous.carriedHealth;
        carriedCopy = previous.carriedCopy;
        copiedHealthRatio = previous.health() / Math.max(1.0, previous.currentMaxHealth());
    }

    @Override
    public void onUpgradeCompleted(PlayerLane lane, Tower previousTower,
                                   kim.biryeong.semiontd.tower.TowerUpgradeOption option) {
        if (carriedCopy || lane == null) return;
        for (FutureAgencyAgentTower survivor : linkedSurvivors(lane)) {
            FutureAgencyAgentTower replacement = new FutureAgencyAgentTower(
                    type(), ownerPlayer(), teamId(), laneId(), originalPosition(), survivor.position());
            replacement.copyFrom(survivor, 0);
            lane.replaceTower(survivor, replacement);
        }
    }

    @Override
    public void onSold(PlayerLane lane) {
        if (carriedCopy || lane == null) return;
        for (FutureAgencyAgentTower survivor : linkedSurvivors(lane)) {
            lane.removeTower(survivor);
        }
    }

    @Override
    public Optional<Vec3> idleMovementTarget(SemionTowerEntity entity) {
        if (!waveActive || lane == null || withdrawn || FutureAgencyTowers.role(type()) == null) return Optional.empty();
        double progress = lane.laneLayout().progressAt(entity.position());
        return progress <= 0.01 ? Optional.empty()
                : Optional.of(lane.laneLayout().positionAt(Math.max(0.0, progress - 0.04)));
    }

    @Override
    public double adjustMovementSpeed(double baseSpeed) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        return Math.max(baseSpeed, 0.30) * (1.0 + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.FORWARD_DEPLOYMENT));
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        if (FutureAgencyTowers.role(type()) != FutureAgencyRole.COMBAT) return baseRange;
        return baseRange + FutureAgencyBalance.stacked(FutureAgencyStates.state(ownerPlayer()), FutureAgencyPolicy.LONG_RANGE_OPTICS);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        double bonus = FutureAgencyBalance.leaderAttackSpeed(state)
                + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.REACTION_TRAINING);
        if (FutureAgencyTowers.role(type()) == FutureAgencyRole.COMBAT) {
            bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.FAST_RELOAD);
        }
        return Math.max(1, (int) Math.ceil(baseIntervalTicks / (1.0 + bonus)));
    }

    @Override
    public int aggroPriority() {
        int base = super.aggroPriority();
        return FutureAgencyTowers.role(type()) == FutureAgencyRole.PROTECTION
                ? base + (int) Math.round(FutureAgencyBalance.stacked(
                FutureAgencyStates.state(ownerPlayer()), FutureAgencyPolicy.FORCED_TAUNT)) : base;
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        if (FutureAgencyTowers.role(type()) != FutureAgencyRole.COMBAT) return Optional.empty();
        return candidates.stream().max(Comparator.comparingDouble(target -> target.runtimeMonster().laneProgress()));
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        double bonus = FutureAgencyBalance.leaderDamage(state)
                + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.AGENCY_TACTICS);
        FutureAgencyRole role = FutureAgencyTowers.role(type());
        if (role == FutureAgencyRole.COMBAT) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.PRECISION_FIRE);
        if (state.worldSaved() && deployedAtFinalDefense()) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.CENTRAL_BATTLE);
        if (target != null && target.runtimeMonster() != null) {
            if (target.runtimeMonster().senderTeam().isPresent()) {
                bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.INCOME_INTERCEPTION);
            } else {
                bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.WAVE_ANALYSIS);
            }
            List<SummonRole> roles = target.runtimeMonster().summonRoles();
            if (roles.contains(SummonRole.TANK)) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.TANK_DEPARTMENT);
            if (roles.contains(SummonRole.SIEGE)) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.SIEGE_DEPARTMENT);
            if (roles.contains(SummonRole.SWARM) || roles.contains(SummonRole.RUSH)) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.SWARM_DEPARTMENT);
            if (target.runtimeMonster().health() <= target.runtimeMonster().maxHealth() * 0.30) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.EXECUTION_AUTHORITY);
            if (target.runtimeMonster().maxHealth() >= 500.0) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.HIGH_VALUE_TARGET);
            if (role == FutureAgencyRole.SUPPRESSION && state.stacks(FutureAgencyPolicy.DENSE_CONTROL) > 0) {
                long nearby = targets(lane, target.position(), FutureAgencyBalance.suppressionDenseRadius())
                        .stream().filter(other -> other != target).count();
                bonus += Math.min(FutureAgencyBalance.suppressionDenseCap(),
                        nearby * FutureAgencyBalance.policy(FutureAgencyPolicy.DENSE_CONTROL));
            }
        }
        return damage * (1.0 + bonus);
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity source, DamageSource damageSource, double damage) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        double reduction = FutureAgencyBalance.stacked(state, FutureAgencyPolicy.PROFESSIONAL_AGENTS);
        if (damageSource.getEntity() instanceof SemionMonsterEntity monster) {
            if (monster.runtimeMonster().attackKind() == AttackKind.RANGED) reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.RANGED_ARMOR);
            else reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.MELEE_TRAINING);
            if ("minecraft:warden".equals(monster.runtimeMonster().id())) reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.ANOMALY_DEPARTMENT);
        }
        if (FutureAgencyTowers.role(type()) == FutureAgencyRole.PROTECTION) {
            reduction += FutureAgencyBalance.agentAbility(type(), "damageReduction",
                    switch (FutureAgencyTowers.grade(type())) {
                        case 5 -> .08;
                        case 4 -> .12;
                        case 3 -> .16;
                        case 2 -> .20;
                        default -> .25;
                    });
            reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.SHOCK_ABSORPTION);
            if (health() <= currentMaxHealth() * .35) reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.LAST_BARRIER);
        } else if (hasEscort()) {
            reduction += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.ESCORT_FORMATION);
        }
        return damage * (1.0 - Math.min(FutureAgencyBalance.damageReductionCap(), reduction));
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity primary, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        if (dealt <= 0.0 || FutureAgencyTowers.role(type()) != FutureAgencyRole.SUPPRESSION || lane == null) return;
        int grade = FutureAgencyTowers.grade(type());
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        double radius = FutureAgencyBalance.agentAbility(type(), "suppressionRadius",
                switch (grade) {case 5 -> 1.25; case 4 -> 1.5; case 3 -> 1.75; case 2 -> 2.0; default -> 2.5;})
                + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.AREA_SUPPRESSION);
        int cap = (int) Math.round(FutureAgencyBalance.agentAbility(type(), "suppressionMaxTargets",
                switch (grade) {case 5 -> 3; case 4 -> 4; case 3 -> 5; case 2 -> 6; default -> 7;}))
                + (int) Math.round(FutureAgencyBalance.stacked(state, FutureAgencyPolicy.MULTI_TARGET));
        double ratio = FutureAgencyBalance.agentAbility(type(), "suppressionDamageRatio",
                switch (grade) {case 5 -> .40; case 4 -> .45; case 3 -> .50; case 2 -> .55; default -> .60;})
                + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.DISPERSION_WARHEAD);
        double slow = Math.min(FutureAgencyBalance.slowCap(),
                FutureAgencyBalance.agentAbility(type(), "suppressionSlow",
                        switch (grade) {case 5 -> .08; case 4 -> .12; case 3 -> .16; case 2 -> .20; default -> .25;})
                        + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.RESTRAINT_ROUNDS));
        applySlow(primary, slow);
        Set<UUID> selected = targets(lane, primary.position(), radius).stream().filter(target -> target != primary)
                .limit(Math.max(0, cap - 1L)).map(SemionMonsterEntity::getUUID)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (selected.isEmpty()) return;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "future_suppression"), source, primary, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF)).withFilter(target -> selected.contains(target.getUUID()));
        TowerAreaDamage.applyResolved(this, source, request, ignored -> outgoing * Math.min(1.0, ratio), true,
                (target, amount, secondaryKilled) -> {applySlow(target, slow); TowerVfxService.showSecondaryAttack(source, target);});
    }

    public void refreshPolicyHealth(boolean healIncrease) {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(ownerPlayer());
        double bonus = FutureAgencyBalance.leaderHealth(state)
                + FutureAgencyBalance.stacked(state, FutureAgencyPolicy.COMPOSITE_ARMOR);
        if (FutureAgencyTowers.role(type()) == FutureAgencyRole.PROTECTION) bonus += FutureAgencyBalance.stacked(state, FutureAgencyPolicy.CERAMIC_PLATES);
        syncMaxHealth(type().maxHealth() * (1.0 + bonus), healIncrease);
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("<light_purple>요원</light_purple> " + FutureAgencyTowers.grade(type()) + "급 " + FutureAgencyTowers.role(type()).displayName());
        if (carriedCopy) {
            double carryHealth = withdrawn && carriedHealth > 0.0 ? carriedHealth : health();
            lines.add("<aqua>연결 원본</aqua> <white>" + originalPosition().x() + ", " + originalPosition().z() + "</white>");
            lines.add("<aqua>이월 체력</aqua> <white>" + oneDecimal(carryHealth) + "/" + oneDecimal(currentMaxHealth()) + "</white>");
        } else {
            long linked = lane == null ? 0 : linkedSurvivors(lane).stream().filter(survivor -> !survivor.isDestroyed(lane)).count();
            lines.add("<aqua>연결 생존자</aqua> <white>" + Math.min(1, linked) + "/1</white>");
            lines.add("<gray>관리 위치</gray> <white>" + originalPosition().x() + ", " + originalPosition().z() + "</white>");
        }
        return List.copyOf(lines);
    }

    boolean carriedCopy() {return carriedCopy;}

    private void carryIntoNextRound(PlayerLane lane) {
        if (withdrawn || FutureAgencyStates.state(ownerPlayer()).worldSaved() || isDestroyed(lane)) return;
        GridPosition survivalPosition = position();
        double heal = currentMaxHealth() * FutureAgencyBalance.stacked(
                FutureAgencyStates.state(ownerPlayer()), FutureAgencyPolicy.EVAC_MEDICS);
        double survivalHealth = Math.min(currentMaxHealth(), health() + heal);
        if (!carriedCopy && lane != null) {
            List<FutureAgencyAgentTower> linked = linkedSurvivors(lane);
            FutureAgencyAgentTower survivor = linked.stream().filter(candidate -> !candidate.isDestroyed(lane))
                    .findFirst().orElse(null);
            for (FutureAgencyAgentTower candidate : linked) {
                if (candidate != survivor) lane.removeTower(candidate);
            }
            if (survivor == null) {
                survivor = new FutureAgencyAgentTower(
                        type(), ownerPlayer(), teamId(), laneId(), originalPosition(), survivalPosition);
                survivor.carriedCopy = true;
                survivor.withdrawn = true;
                survivor.carriedPosition = survivalPosition;
                survivor.carriedHealth = survivalHealth;
                lane.addTower(survivor);
            }
        } else {
            carriedPosition = survivalPosition;
            carriedHealth = survivalHealth;
        }
        withdrawn = true;
        showCarryVfx(lane);
        super.onRemoved(lane);
    }

    private List<FutureAgencyAgentTower> linkedSurvivors(PlayerLane lane) {
        if (lane == null) return List.of();
        return lane.towers().stream().filter(FutureAgencyAgentTower.class::isInstance)
                .map(FutureAgencyAgentTower.class::cast)
                .filter(candidate -> candidate.carriedCopy
                        && ownerPlayer().equals(candidate.ownerPlayer())
                        && laneId() == candidate.laneId()
                        && originalPosition().equals(candidate.originalPosition()))
                .toList();
    }

    private void showCarryVfx(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) return;
        if (lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity entity) {
            TowerVfxService.showTranscendence(List.of(entity));
        }
    }

    private boolean hasEscort() {
        if (lane == null || FutureAgencyStates.state(ownerPlayer()).stacks(FutureAgencyPolicy.ESCORT_FORMATION) == 0) return false;
        double radius = FutureAgencyBalance.escortRadius();
        double radiusSquared = radius * radius;
        return lane.towers().stream().filter(FutureAgencyAgentTower.class::isInstance)
                .map(FutureAgencyAgentTower.class::cast).filter(other -> other != this)
                .filter(other -> ownerPlayer().equals(other.ownerPlayer()))
                .filter(other -> FutureAgencyTowers.role(other.type()) == FutureAgencyRole.PROTECTION)
                .filter(other -> !other.isDestroyed(lane))
                .anyMatch(other -> distanceSquared(position(), other.position()) <= radiusSquared);
    }

    private static double distanceSquared(GridPosition a, GridPosition b) {
        double x=a.x()-b.x(), y=a.y()-b.y(), z=a.z()-b.z(); return x*x+y*y+z*z;
    }

    private static void applySlow(SemionMonsterEntity target, double slow) {
        target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, 40);
    }

    private static List<SemionMonsterEntity> targets(PlayerLane lane, Vec3 center, double radius) {
        if (lane == null || center == null) return List.of();
        double radiusSquared = radius * radius;
        return lane.activeMonsters().stream().filter(monster -> monster.hasMinecraftEntity())
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                .filter(entity -> entity.isAlive() && entity.position().distanceToSqr(center) <= radiusSquared)
                .sorted(Comparator.comparingDouble((SemionMonsterEntity entity) -> -entity.runtimeMonster().laneProgress())
                        .thenComparing(entity -> entity.getUUID().toString())).toList();
    }
}
