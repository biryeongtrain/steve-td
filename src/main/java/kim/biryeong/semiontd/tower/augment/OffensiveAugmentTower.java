package kim.biryeong.semiontd.tower.augment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Attacking augment bodies. All consumable state belongs to the logical tower, not its entity. */
public final class OffensiveAugmentTower extends AugmentTower {
    private static final ResourceLocation CHARGE_SCAN = id("augment_capacitor_scan");
    private static final ResourceLocation SHELL = id("augment_ordnance_shell");
    private static final TowerDataKey<BattleState> BATTLE = key("augment_offensive_battle", BattleState.class);
    private static final TowerDataKey<CapacitorState> CAPACITOR = key("augment_capacitor", CapacitorState.class);
    private static final TowerDataKey<CocoonState> COCOON = key("augment_cocoon", CocoonState.class);
    private static final TowerDataKey<OrdnanceState> ORDNANCE = key("augment_ordnance", OrdnanceState.class);
    private static final TowerDataKey<UUID> LOCKED_TARGET = key("augment_giant_target", UUID.class);
    private static final TowerDataKey<Double> SPECIAL_DAMAGE = key("augment_offensive_special_damage", Double.class);
    private static final TowerDataKey<ActionCounts> ACTION_COUNTS = key("augment_offensive_action_counts", ActionCounts.class);

    public OffensiveAugmentTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public boolean canAttackTarget(SemionTowerEntity source, SemionMonsterEntity target) {
        if (!validEnemy(source, target)) return false;
        if (is(AugmentTowers.STARLIGHT_COCOON) && !hatched()) return false;
        double distance = source.distanceToSqr(target);
        double minimum = is(AugmentTowers.GIANT_HUNTER) ? value("minimumRange", 3) : 0;
        return inRange(distance, minimum, source.attackRange());
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity source,
            List<SemionMonsterEntity> candidates) {
        if (!is(AugmentTowers.GIANT_HUNTER) || source == null || candidates == null) return Optional.empty();
        List<SemionMonsterEntity> eligible = candidates.stream()
                .filter(target -> canAttackTarget(source, target) && target.runtimeMonster() != null).toList();
        UUID locked = getDataOrDefault(LOCKED_TARGET, null);
        Optional<SemionMonsterEntity> previous = eligible.stream()
                .filter(target -> target.runtimeMonster().logicalId().equals(locked)).findFirst();
        if (previous.isPresent()) return previous;
        Optional<SemionMonsterEntity> selected = eligible.stream().min(Comparator
                .comparingDouble((SemionMonsterEntity target) -> -target.runtimeMonster().maxHealth())
                .thenComparingDouble(source::distanceToSqr)
                .thenComparing(target -> target.runtimeMonster().logicalId()));
        setData(LOCKED_TARGET, selected.map(target -> target.runtimeMonster().logicalId()).orElse(null));
        return selected;
    }

    @Override
    public double modifyResolvedAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double ignored) {
        if (is(AugmentTowers.GIANT_HUNTER)) {
            if (target == null || target.runtimeMonster() == null) return type().damage();
            var monster = target.runtimeMonster();
            boolean boss = monster.origin() == MonsterOrigin.NATURAL_WAVE && monster.id().equals("warden_boss_15");
            return type().damage() + monster.maxHealth()
                    * value(boss ? "bossMaxHealthDamageRatio" : "maxHealthDamageRatio", boss ? 0.02 : 0.08);
        }
        if (is(AugmentTowers.CAPACITOR_POST)) return type().damage() + charges() * value("chargeDamage", 70);
        if (is(AugmentTowers.STARLIGHT_COCOON)) return hatched() ? value("hatchedDamage", 110) : 0;
        return type().damage();
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        if (is(AugmentTowers.STARLIGHT_COCOON)) return hatched() ? value("hatchedRange", 5) : 0;
        return baseRange;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        return is(AugmentTowers.STARLIGHT_COCOON) && hatched()
                ? integer("hatchedIntervalTicks", 30) : baseIntervalTicks;
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return is(AugmentTowers.STARLIGHT_COCOON) && hatched()
                ? value("hatchedHealth", 600) : super.builderCurrentMaxHealth();
    }

    @Override
    public double effectBaseMaxHealth() {
        return is(AugmentTowers.STARLIGHT_COCOON) && hatched()
                ? value("hatchedHealth", 600) : super.effectBaseMaxHealth();
    }

    @Override
    public EntityVisual visual() {
        return is(AugmentTowers.STARLIGHT_COCOON) && hatched()
                ? EntityVisual.vanilla("minecraft:iron_golem") : super.visual();
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        super.onWaveStarted(lane, round);
        if (battle().round() == round) return;
        setData(BATTLE, new BattleState(round, true));
        setData(SPECIAL_DAMAGE, 0.0);
        setData(ACTION_COUNTS, ActionCounts.EMPTY);
        removeData(LOCKED_TARGET);
        if (is(AugmentTowers.CAPACITOR_POST)) setData(CAPACITOR, CapacitorState.EMPTY);
        if (is(AugmentTowers.STARLIGHT_COCOON)) setData(COCOON, cocoon().start(round));
        if (is(AugmentTowers.ORDNANCE_FACTORY)) setData(ORDNANCE,
                ordnance().startCombat(round, integer("emeraldPerShell", 100), integer("maxShells", 4)));
        updateName(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (!is(AugmentTowers.CAPACITOR_POST) || !battle().active() || health() <= 0) return;
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null || !source.isAlive()) return;
        int candidates = SemionTdApi.areaEffects().applyToMonsters(
                MonsterAreaEffectRequest.aroundTower(CHARGE_SCAN, source, source.attackRange(), AreaVfxSpec.none())
                        .withFilter(target -> validEnemy(source, target)).nearestTargets(1),
                target -> AreaEffectOutcome.UNCHANGED).candidateCount();
        CapacitorState previous = capacitor();
        CapacitorState next = previous.advance(candidates > 0, integer("chargeTicks", 40), integer("maxCharges", 3));
        setData(CAPACITOR, next);
        if (previous.charges() != next.charges()) updateName(lane);
    }

    /** Called once by the primary-attack result boundary, only after positive health damage. */
    public void onPrimaryAttack(SemionMonsterEntity target, double outgoingDamage, DamageType damageType, Vec3 beforePosition) {
        if (!battle().active() || target == null || outgoingDamage <= 0) return;
        PlayerLane lane = attachedLane();
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) return;
        if (is(AugmentTowers.CAPACITOR_POST) && charges() > 0) {
            setData(ACTION_COUNTS, actionCounts().chargedShot());
            setData(CAPACITOR, CapacitorState.EMPTY);
            TowerVfxService.showProphecyLightning(source, beforePosition == null ? target.position() : beforePosition);
            updateName(lane);
        } else if (is(AugmentTowers.ORDNANCE_FACTORY)) {
            if (target.runtimeMonster() == null || AugmentEconomyService.isLowPressure(target.runtimeMonster())) return;
            long now = source.level().getGameTime();
            OrdnanceState previous = ordnance();
            OrdnanceState next = previous.fire(now, integer("shellIntervalTicks", 100));
            if (next == previous) return;
            setData(ORDNANCE, next);
            setData(ACTION_COUNTS, actionCounts().shellFired());
            fireShell(source, target, beforePosition == null ? target.position() : beforePosition);
            updateName(lane);
        }
    }

    private void fireShell(SemionTowerEntity source, SemionMonsterEntity primary, Vec3 center) {
        double radius = value("shellRadius", 3);
        List<SemionMonsterEntity> candidates = new ArrayList<>();
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(SHELL, source, center, radius,
                Set.of(), target -> validEnemy(source, target), AreaVfxSpec.none());
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            candidates.add(target);
            return AreaEffectOutcome.UNCHANGED;
        });
        Set<UUID> selected = new HashSet<>();
        candidates.stream().sorted(Comparator
                .comparingInt((SemionMonsterEntity target) -> target == primary ? 0 : 1)
                .thenComparingDouble(target -> target.position().distanceToSqr(center))
                .thenComparing(target -> target.runtimeMonster().logicalId()))
                .limit(integer("shellTargets", 5)).forEach(target -> selected.add(target.getUUID()));
        TowerAreaDamage.apply(this, source, new MonsterAreaEffectRequest(SHELL, source, center, radius,
                        Set.of(), target -> selected.contains(target.getUUID()) && validEnemy(source, target),
                        AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)), target -> value("shellDamage", 120), true,
                (target, dealt, killed) -> {
                    setData(SPECIAL_DAMAGE, specialDamageDealt() + dealt);
                    recordAugmentSpecialDamage(dealt);
                }, DamageType.PHYSICAL);
    }

    /** The successful purchase boundary filters phase, origin, attack-only type, and absent contracts. */
    public void onPaidSummon(UUID transactionId, long actualEmerald) {
        if (!is(AugmentTowers.ORDNANCE_FACTORY)) return;
        setData(ORDNANCE, ordnance().purchase(transactionId, actualEmerald));
        updateName(attachedLane());
    }

    /** Runs after normal round restoration, only if a real next preparation phase opens. */
    public void beginPrepare(PlayerLane lane, int round) {
        if (is(AugmentTowers.ORDNANCE_FACTORY)) setData(ORDNANCE, ordnance().prepare(round));
        if (is(AugmentTowers.STARLIGHT_COCOON)) {
            CocoonState previous = cocoon();
            CocoonState next = previous.hatch(round, integer("hatchWaves", 2));
            setData(COCOON, next);
            if (!previous.hatched() && next.hatched()) {
                syncMaxHealth(value("hatchedHealth", 600), false);
                syncHealth(currentMaxHealth());
                onStateChanged(lane);
                recordTelemetry(lane, round, "HATCHED");
                runtimeEntity(lane).ifPresent(source -> TowerVfxService.showAreaEffect(source,
                        id("augment_cocoon_hatch"), AreaVfxStyles.BUFF, source.position(), 2,
                        List.of(source.position()), 1, 1, 0));
            }
        }
        updateName(lane);
    }

    /** survives means the participant advances; elimination and match shutdown must pass false. */
    public void settleRound(PlayerLane lane, int round, boolean survives) {
        BattleState battle = battle();
        if (battle.round() != round || !battle.active()) return;
        if (is(AugmentTowers.STARLIGHT_COCOON)) {
            setData(COCOON, cocoon().settle(round, survives && !isDestroyed(lane)));
        }
        if (is(AugmentTowers.CAPACITOR_POST)) setData(CAPACITOR, CapacitorState.EMPTY);
        if (is(AugmentTowers.ORDNANCE_FACTORY)) setData(ORDNANCE, ordnance().endCombat());
        setData(BATTLE, new BattleState(round, false));
        updateName(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        if (is(AugmentTowers.STARLIGHT_COCOON)) setData(COCOON, cocoon().destroyed(battle().round()));
        super.onDeath(lane);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        updateName(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        super.configureEntityAfterSpawn(entity, lane);
        updateName(lane);
    }

    private void updateName(PlayerLane lane) {
        runtimeEntity(lane).ifPresent(source -> {
            String suffix = "";
            if (is(AugmentTowers.CAPACITOR_POST)) suffix = " · 충전 " + charges() + "/" + integer("maxCharges", 3);
            if (is(AugmentTowers.STARLIGHT_COCOON) && !hatched()) suffix = " · 부화 " + cocoon().successes() + "/" + integer("hatchWaves", 2);
            if (is(AugmentTowers.ORDNANCE_FACTORY)) suffix = " · 포탄 " + preparedShells() + "/" + integer("maxShells", 4);
            source.setCustomName(Component.literal((hatched() ? "별빛 파수꾼" : type().displayName()) + suffix));
            source.setCustomNameVisible(true);
        });
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>();
        lines.add("외부 회복·전투 버프 없음 · 복제 불가");
        if (is(AugmentTowers.GIANT_HUNTER)) lines.add("최소 사거리 " + oneDecimal(value("minimumRange", 3)) + " · 최대 체력 우선, 대상 고정");
        if (is(AugmentTowers.CAPACITOR_POST)) lines.add("충전 " + charges() + "/" + integer("maxCharges", 3)
                + " · 다음 추가 마법 피해 " + oneDecimal(charges() * value("chargeDamage", 70)));
        if (is(AugmentTowers.STARLIGHT_COCOON)) lines.add(hatched() ? "별빛 파수꾼 · 부화 완료 · 슬롯 2칸"
                : "부화 " + cocoon().successes() + "/" + integer("hatchWaves", 2) + " · 슬롯 2칸 · 공격 없음");
        if (is(AugmentTowers.ORDNANCE_FACTORY)) {
            long paid = ordnance().paidEmerald();
            lines.add("적격 구매 " + paid + " · " + (ordnance().combat() ? "남은" : "준비") + " 포탄 " + preparedShells()
                    + "/" + integer("maxShells", 4) + " · 잔여 " + paid % integer("emeraldPerShell", 100) + " 이월 안 됨");
        }
        return List.copyOf(lines);
    }

    public boolean hatched() { return is(AugmentTowers.STARLIGHT_COCOON) && cocoon().hatched(); }
    public int charges() { return capacitor().charges(); }
    public int preparedShells() {
        return ordnance().combat() ? ordnance().shells() : Math.min(integer("maxShells", 4),
                (int) Math.min(Integer.MAX_VALUE, ordnance().paidEmerald() / integer("emeraldPerShell", 100)));
    }
    public double specialDamageDealt() { return getDataOrDefault(SPECIAL_DAMAGE, 0.0); }

    @Override
    public AugmentTelemetrySnapshot.TowerSample telemetrySample(int round, long tick, int towerRef, String eventType) {
        ActionCounts counts = battle().round() == round ? actionCounts() : ActionCounts.EMPTY;
        return new AugmentTelemetrySnapshot.TowerSample(round, tick, towerRef, type().id(), eventType,
                TowerCapacity.slotCost(this), null, null, null, null,
                is(AugmentTowers.CAPACITOR_POST) ? counts.capacitorChargedShots() : null,
                is(AugmentTowers.ORDNANCE_FACTORY) ? counts.ordnanceShellsFired() : null,
                is(AugmentTowers.STARLIGHT_COCOON) ? cocoon().successes() : null,
                is(AugmentTowers.STARLIGHT_COCOON) ? hatched() : null);
    }

    private boolean is(TowerType expected) { return AugmentTowers.is(type(), expected); }
    private int integer(String key, int fallback) { return Math.max(1, (int) value(key, fallback)); }
    private BattleState battle() { return getDataOrDefault(BATTLE, new BattleState(0, false)); }
    private CapacitorState capacitor() { return getDataOrDefault(CAPACITOR, CapacitorState.EMPTY); }
    private CocoonState cocoon() { return getDataOrDefault(COCOON, CocoonState.EMPTY); }
    private OrdnanceState ordnance() { return getDataOrDefault(ORDNANCE, OrdnanceState.EMPTY); }
    private ActionCounts actionCounts() { return getDataOrDefault(ACTION_COUNTS, ActionCounts.EMPTY); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("semion-td", path); }
    private static <T> TowerDataKey<T> key(String path, Class<T> type) { return TowerDataKey.of(id(path), type); }
    private static boolean validEnemy(SemionTowerEntity source, SemionMonsterEntity target) {
        return source != null && source.isValidAttackTarget(target)
                && target.runtimeMonster().origin() != MonsterOrigin.BUILDER_PROXY;
    }
    static boolean inRange(double distanceSquared, double minimum, double maximum) {
        return Double.isFinite(distanceSquared) && distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
    }

    private record BattleState(int round, boolean active) {}

    private record ActionCounts(long capacitorChargedShots, long ordnanceShellsFired) {
        private static final ActionCounts EMPTY = new ActionCounts(0, 0);
        ActionCounts chargedShot() { return new ActionCounts(capacitorChargedShots + 1, ordnanceShellsFired); }
        ActionCounts shellFired() { return new ActionCounts(capacitorChargedShots, ordnanceShellsFired + 1); }
    }

    record CapacitorState(int charges, int idleTicks) {
        static final CapacitorState EMPTY = new CapacitorState(0, 0);
        CapacitorState advance(boolean enemyInRange, int chargeTicks, int maxCharges) {
            if (enemyInRange || charges >= maxCharges) return new CapacitorState(charges, 0);
            return idleTicks + 1 >= chargeTicks ? new CapacitorState(charges + 1, 0) : new CapacitorState(charges, idleTicks + 1);
        }
    }

    record CocoonState(int startedRound, int destroyedRound, int settledRound, int successes, boolean hatched) {
        static final CocoonState EMPTY = new CocoonState(0, 0, 0, 0, false);
        CocoonState start(int round) {
            return round <= startedRound ? this : new CocoonState(round, destroyedRound, settledRound, successes, hatched);
        }
        CocoonState destroyed(int round) { return new CocoonState(startedRound, round, settledRound, successes, hatched); }
        CocoonState settle(int round, boolean survives) {
            if (round != startedRound || round <= settledRound || hatched) return this;
            return new CocoonState(startedRound, destroyedRound, round,
                    successes + (survives && destroyedRound != round ? 1 : 0), false);
        }
        CocoonState hatch(int nextRound, int required) {
            return hatched || nextRound <= settledRound || successes < required ? this
                    : new CocoonState(startedRound, destroyedRound, settledRound, successes, true);
        }
    }

    record OrdnanceState(int round, Set<UUID> transactions, long paidEmerald, int shells, long lastShot, boolean combat) {
        static final OrdnanceState EMPTY = new OrdnanceState(0, Set.of(), 0, 0, Long.MIN_VALUE, false);
        OrdnanceState { transactions = Set.copyOf(transactions); }
        OrdnanceState prepare(int nextRound) {
            return nextRound <= round ? this : new OrdnanceState(nextRound, Set.of(), 0, 0, Long.MIN_VALUE, false);
        }
        OrdnanceState purchase(UUID transactionId, long emerald) {
            if (round <= 0 || combat || transactionId == null || emerald <= 0 || transactions.contains(transactionId)) return this;
            Set<UUID> ids = new HashSet<>(transactions);
            ids.add(transactionId);
            long paid = paidEmerald > Long.MAX_VALUE - emerald ? Long.MAX_VALUE : paidEmerald + emerald;
            return new OrdnanceState(round, ids, paid, shells, lastShot, false);
        }
        OrdnanceState startCombat(int waveRound, int emeraldPerShell, int maxShells) {
            if (combat || waveRound < round) return this;
            OrdnanceState prepared = prepare(waveRound);
            return new OrdnanceState(waveRound, prepared.transactions, prepared.paidEmerald,
                    (int) Math.min(maxShells, prepared.paidEmerald / emeraldPerShell), Long.MIN_VALUE, true);
        }
        OrdnanceState fire(long now, int interval) {
            if (!combat || shells <= 0 || (lastShot != Long.MIN_VALUE && now - lastShot < interval)) return this;
            return new OrdnanceState(round, transactions, paidEmerald, shells - 1, now, true);
        }
        OrdnanceState endCombat() { return new OrdnanceState(round, transactions, 0, 0, lastShot, false); }
    }
}
