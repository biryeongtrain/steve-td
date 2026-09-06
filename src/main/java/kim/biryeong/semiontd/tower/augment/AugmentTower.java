package kim.biryeong.semiontd.tower.augment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Logical support state survives entity recreation; only a new wave reloads limited uses. */
public class AugmentTower extends ProductionTower {
    private static final TowerDataKey<SupportState> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_tower_support"), SupportState.class);
    private static final SupportState EMPTY = new SupportState(-1, List.of(), Set.of(), 0, 0, false, false, 0);
    private static final TowerDataKey<Observations> OBSERVATIONS = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_tower_observations"), Observations.class);
    private static final TowerDataKey<RecordedStages> RECORDED_STAGES = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_tower_recorded_stages"), RecordedStages.class);
    private boolean restoringEntity;

    public AugmentTower(TowerType type, UUID owner, TeamId team, int lane, GridPosition original, GridPosition current) {
        super(type, owner, team, lane, original, current);
    }

    protected PlayerLane currentLane() { return attachedLane(); }
    protected double value(String key, double fallback) {
        return augmentSnapshot().parameter(AugmentTowers.augmentId(type()), key, fallback);
    }
    @Override public boolean isAugmentTower() { return true; }
    @Override public boolean receivesTraitEffects() { return false; }
    @Override public boolean canReceiveAllyHealing() { return false; }
    @Override public boolean triggersNearbyDeathEffects() { return false; }
    @Override public boolean canChaseTargets() { return false; }
    @Override public boolean canBeSold() { return !AugmentTowers.isFreeCall(type()); }
    @Override public boolean canAttackTarget(SemionTowerEntity entity, SemionMonsterEntity target) { return false; }

    @Override public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        if (AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)) AmbushMines.placed(this, lane);
        if (!restoringEntity && runtimeEntity(lane).isPresent() && lane.augmentTelemetry() != null
                && lane.augmentTelemetry().currentRound() != null) {
            recordTelemetry(lane, lane.augmentTelemetry().currentRound(), "PLACED");
        }
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        super.onWaveStarted(lane, round);
        if (state().round() == round) return;
        setData(OBSERVATIONS, new Observations(0, 0, 0));
        List<UUID> links = List.of();
        if (AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY)) {
            List<Tower> eligible = supportTargets(lane).stream().filter(AugmentCombat::isNormalAttacker).toList();
            if (!eligible.isEmpty()) {
                Tower first = eligible.getFirst();
                Tower second = eligible.stream().filter(t -> !t.type().id().equals(first.type().id())).findFirst().orElse(null);
                if (second != null) links = List.of(first.logicalId(), second.logicalId());
            }
        } else if (AugmentTowers.is(type(), AugmentTowers.BARRIER_CORE)) {
            links = supportTargets(lane).stream().limit(3).map(Tower::logicalId).toList();
        }
        setData(STATE, new SupportState(round, links, Set.of(), 0, 0, false, false, 0));
        if (AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)) AmbushMines.reload(this, lane, round);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (AugmentTowers.is(type(), AugmentTowers.EMERGENCY_BELL)) return ringBell(lane);
        if (AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)) {
            AmbushMines.tick(this, lane);
            return true;
        }
        if (!state().links().isEmpty()) {
            validateLinks(lane);
            var entity = runtimeEntity(lane).orElse(null);
            if (entity != null) for (Tower tower : linkedTowers(lane)) {
                TowerVfxService.showSecondaryAttack(entity, location(tower));
            }
        }
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        int interval = (int) value("checkTicks", AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP) ? 5 : 20);
        return Math.max(0, interval - 1);
    }

    private boolean ringBell(PlayerLane lane) {
        SupportState old = state();
        if (old.round() != currentRound() || old.healedIds().size() >= (int) value("maxHeals", 3)) return true;
        Tower target = supportTargets(lane).stream()
                .filter(t -> !old.healedIds().contains(t.logicalId()))
                .filter(t -> t.health() / t.currentMaxHealth() <= value("healthThreshold", .4))
                .min(Comparator.comparingDouble((Tower t) -> t.health() / t.currentMaxHealth())
                        .thenComparing(Tower::logicalId)).orElse(null);
        if (target == null) return true;
        double amount = Math.min(value("healCap", 90), target.currentMaxHealth() * value("healRatio", .25));
        double before = target.health();
        var entity = target instanceof EntityBackedTower backed ? backed.runtimeEntity(lane).orElse(null) : null;
        if (entity != null) {
            if (!healTarget(entity, amount)) return true;
            entity.playHealingAnimation();
        } else {
            target.syncHealth(before + amount);
            recordHealingDone(target.health() - before);
            if (target.health() <= before) return true;
        }
        Set<UUID> healed = new HashSet<>(old.healedIds());
        healed.add(target.logicalId());
        setData(STATE, new SupportState(old.round(), old.links(), healed, old.firstAttacks(), old.secondAttacks(),
                old.firstCharged(), old.secondCharged(), old.absorbed()));
        runtimeEntity(lane).ifPresent(source -> TowerVfxService.showSecondaryAttack(source, location(target)));
        if (lane.arenaWorld() != null) lane.arenaWorld().playSound(null, position().x() + .5, position().y() + 1,
                position().z() + .5, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, .7F, 1.2F);
        return true;
    }

    public void onLinkedPrimaryAttack(Tower attacker, SemionMonsterEntity target, double outgoing, DamageType damageType) {
        if (target == null || target.runtimeMonster() == null || AugmentEconomyService.isLowPressure(target.runtimeMonster())) return;
        PlayerLane lane = currentLane();
        if (!AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY) || !validateLinks(lane)) return;
        SupportState old = state();
        int index = old.links().indexOf(attacker.logicalId());
        if (index < 0) return;
        boolean charged = index == 0 ? old.firstCharged() : old.secondCharged();
        int first = old.firstAttacks(), second = old.secondAttacks();
        boolean firstCharge = old.firstCharged(), secondCharge = old.secondCharged();
        boolean createdCharge = false;
        if (index == 0) {
            firstCharge = false;
            if (++first >= (int) value("attacksPerCharge", 5)) {
                first = 0; createdCharge = !secondCharge; secondCharge = true;
            }
        } else {
            secondCharge = false;
            if (++second >= (int) value("attacksPerCharge", 5)) {
                second = 0; createdCharge = !firstCharge; firstCharge = true;
            }
        }
        setData(STATE, new SupportState(old.round(), old.links(), old.healedIds(), first, second,
                firstCharge, secondCharge, old.absorbed()));
        if (createdCharge) {
            Observations observed = observations();
            setData(OBSERVATIONS, new Observations(observed.relayCharges() + 1, observed.relayTriggers(), observed.mineExplosions()));
        }
        if (charged && target.isAlive() && attacker instanceof EntityBackedTower backed) {
            var source = backed.runtimeEntity(lane).orElse(null);
            if (source != null) {
                Observations observed = observations();
                setData(OBSERVATIONS, new Observations(observed.relayCharges(), observed.relayTriggers() + 1, observed.mineExplosions()));
                var result = attacker.damageAugmentTargetResult(source, target, outgoing * value("chargedDamageRatio", .5), damageType);
                attacker.recordAugmentSpecialDamage(result.dealtDamage());
                if (result.killed()) attacker.onKill(source, target, result.dealtDamage());
                TowerVfxService.showSecondaryAttack(source, target);
            }
        }
    }

    public double redirectDamage(Tower target, double damage) {
        if (damage <= 0 || !AugmentTowers.is(type(), AugmentTowers.BARRIER_CORE)
                || !validateLinks(currentLane()) || !state().links().contains(target.logicalId())) return damage;
        var entity = runtimeEntity(currentLane()).orElse(null);
        if (entity == null || !entity.isAlive()) return damage;
        double redirected = damage * Math.clamp(value("redirectRatio", .25), 0, 1);
        SupportState old = state();
        setData(STATE, new SupportState(old.round(), old.links(), old.healedIds(), 0, 0, false, false,
                old.absorbed() + Math.min(redirected, entity.getHealth())));
        entity.applyTransferredDamage(redirected);
        if (!entity.isAlive()) clearLinks();
        return damage - redirected;
    }

    private boolean validateLinks(PlayerLane lane) {
        if (lane == null || isDestroyed(lane) || state().links().isEmpty()) { clearLinks(); return false; }
        List<Tower> links = linkedTowers(lane);
        if (AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY)
                && (links.size() != 2 || links.stream().anyMatch(t -> t.isDestroyed(lane)))) {
            clearLinks(); return false;
        }
        return true;
    }

    private List<Tower> linkedTowers(PlayerLane lane) {
        return lane.towers().stream().filter(t -> state().links().contains(t.logicalId())).toList();
    }

    private void clearLinks() {
        SupportState old = state();
        setData(STATE, new SupportState(old.round(), List.of(), old.healedIds(), 0, 0, false, false, old.absorbed()));
    }

    @Override public void onDeath(PlayerLane lane) {
        recordTelemetry(lane, currentRound(), "DESTROYED");
        clearLinks();
        super.onDeath(lane);
    }
    @Override public void onRemoved(PlayerLane lane) {
        if (!restoringEntity && lane.augmentTelemetry() != null && lane.augmentTelemetry().currentRound() != null) {
            recordTelemetry(lane, lane.augmentTelemetry().currentRound(), "REMOVED");
        }
        super.onRemoved(lane);
    }
    @Override public void resetForRound(PlayerLane lane) {
        restoringEntity = true;
        try { super.resetForRound(lane); }
        finally { restoringEntity = false; }
    }
    @Override public void onNearbyTowerDeath(PlayerLane lane, Tower destroyed) {
        if (AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY) && state().links().contains(destroyed.logicalId())) clearLinks();
    }
    @Override public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        super.moveToFinalDefense(lane, position);
        if (AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)) AmbushMines.move(this, lane);
    }

    private List<Tower> supportTargets(PlayerLane lane) {
        var source = runtimeEntity(lane).orElse(null);
        if (source == null) return List.of();
        List<Tower> targets = new ArrayList<>();
        var request = TowerAreaEffectRequest.aroundTower(AreaEffectIds.tower(this, "augment_links"), source,
                Math.max(.01, type().range()), TowerAreaTargetMode.REGISTERED, AreaVfxSpec.none())
                .withFilter(t -> ownerPlayer().equals(t.tower().ownerPlayer()) && AugmentCombat.isNormalPermanent(t.tower())
                        && !t.tower().isDestroyed(lane));
        SemionTdApi.areaEffects().applyToTowers(request, target -> { targets.add(target.tower()); return AreaEffectOutcome.UNCHANGED; });
        Comparator<Tower> order = Comparator.comparingDouble((Tower t) -> location(t).distanceToSqr(source.position()));
        if (AugmentTowers.is(type(), AugmentTowers.BARRIER_CORE)) {
            order = order.thenComparingInt(t -> t.position().x()).thenComparingInt(t -> t.position().y()).thenComparingInt(t -> t.position().z());
        } else {
            order = order.thenComparing(Tower::logicalId);
        }
        targets.sort(order);
        return targets;
    }

    static Vec3 location(Tower tower) {
        if (tower instanceof EntityBackedTower backed && tower.attachedLane() != null) {
            var entity = backed.runtimeEntity(tower.attachedLane()).orElse(null);
            if (entity != null) return entity.position();
        }
        return new Vec3(tower.position().x() + .5, tower.position().y() + 1, tower.position().z() + .5);
    }

    private SupportState state() { return getDataOrDefault(STATE, EMPTY); }
    private Observations observations() { return getDataOrDefault(OBSERVATIONS, new Observations(0, 0, 0)); }

    void recordMineExplosion() {
        Observations observed = observations();
        setData(OBSERVATIONS, new Observations(observed.relayCharges(), observed.relayTriggers(), observed.mineExplosions() + 1));
    }

    public AugmentTelemetrySnapshot.TowerSample telemetrySample(int round, long tick, int towerRef, String eventType) {
        boolean relay = AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY);
        boolean observedRound = state().round() == round;
        return new AugmentTelemetrySnapshot.TowerSample(round, tick, towerRef, type().id().toString(), eventType,
                TowerCapacity.slotCost(this), AugmentTowers.is(type(), AugmentTowers.BARRIER_CORE)
                        ? (observedRound ? state().absorbed() : 0.0) : null,
                relay ? (observedRound ? observations().relayCharges() : 0L) : null,
                relay ? (observedRound ? observations().relayTriggers() : 0L) : null,
                AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)
                        ? (observedRound ? observations().mineExplosions() : 0L) : null,
                null, null, null, null);
    }

    /** The same lifecycle boundary can be reached by destruction, shutdown, or a repeated request. */
    public final void recordTelemetry(PlayerLane lane, int round, String eventType) {
        if (lane == null || lane.augmentTelemetry() == null || lane.augmentTelemetry().currentTick() == null) return;
        RecordedStages previous = getDataOrDefault(RECORDED_STAGES, new RecordedStages(-1, Set.of(), false, false));
        if ((eventType.equals("PLACED") && previous.placed()) || (eventType.equals("HATCHED") && previous.hatched())) return;
        Set<String> stages = new HashSet<>(previous.round() == round ? previous.stages() : Set.of());
        if (!stages.add(eventType)) return;
        setData(RECORDED_STAGES, new RecordedStages(round, stages,
                previous.placed() || eventType.equals("PLACED"), previous.hatched() || eventType.equals("HATCHED")));
        var telemetry = lane.augmentTelemetry();
        telemetry.recordTower(telemetrySample(round, telemetry.currentTick(), telemetry.towerRef(logicalId()), eventType));
    }
    @Override public List<String> runtimeDetailLines() {
        if (AugmentTowers.is(type(), AugmentTowers.EMERGENCY_BELL)) return List.of("구조 " + state().healedIds().size() + "/" + (int) value("maxHeals", 3),
                "체력 " + percentInteger(value("healthThreshold", .4)) + " 이하: 최대 체력 " + percentInteger(value("healRatio", .25)) + " 회복 (최대 " + oneDecimal(value("healCap", 90)) + ")");
        if (AugmentTowers.is(type(), AugmentTowers.PULSE_RELAY)) return List.of("연결 " + state().links().size() + "/2",
                "박동 " + state().firstAttacks() + "/" + (int) value("attacksPerCharge", 5) + " · " + state().secondAttacks() + "/" + (int) value("attacksPerCharge", 5),
                "충전 " + state().firstCharged() + " · " + state().secondCharged());
        if (AugmentTowers.is(type(), AugmentTowers.BARRIER_CORE)) return List.of("연결 " + state().links().size() + "/3", "대신 받은 피해 " + oneDecimal(state().absorbed()),
                "최종 피격량 " + percentInteger(value("redirectRatio", .25)) + " 전가 · 핵심 파괴 시 해제");
        if (AugmentTowers.is(type(), AugmentTowers.AMBUSH_WORKSHOP)) return AmbushMines.details(this);
        return List.of("비공격 · 우선 공격 대상", "외부 회복 없음 · 사망 효과 없음");
    }

    private record SupportState(int round, List<UUID> links, Set<UUID> healedIds, int firstAttacks,
            int secondAttacks, boolean firstCharged, boolean secondCharged, double absorbed) {
        SupportState { links = List.copyOf(links); healedIds = Set.copyOf(healedIds); }
    }
    private record Observations(long relayCharges, long relayTriggers, long mineExplosions) {}
    private record RecordedStages(int round, Set<String> stages, boolean placed, boolean hatched) {
        RecordedStages { stages = Set.copyOf(stages); }
    }
}
