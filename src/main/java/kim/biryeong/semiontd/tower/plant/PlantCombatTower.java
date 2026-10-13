package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Combat half of the plant builder.
 *
 * <p>These towers can only be planted on their own family's {@link PlantSoil}, are rooted, and take
 * their power from the soil they stand on rather than from base stats:
 *
 * <ul>
 *   <li>잔디 - regenerates during combat and grows max health every round.</li>
 *   <li>균사 - makes monsters standing on it weaker and plants consumable mines.</li>
 *   <li>사암 - slows monster attacks and reflects melee damage as thorns.</li>
 *   <li>회백토 - grants attack range and attack speed.</li>
 * </ul>
 *
 * <p>On top of that, every plant blooms: standing on its own soil grants a damage bonus that scales
 * with how many tiles the family owns, capped by {@code bloomDamageCap}.
 */
public class PlantCombatTower extends ProductionTower {
    private static final TowerDataKey<Integer> GROWTH_ROUNDS =
            TowerDataKey.of(plantId("growth_rounds"), Integer.class);
    private static final TowerDataKey<Boolean> PLACED = TowerDataKey.of(plantId("growth_placed"), Boolean.class);

    /** 이 타워에 잔디 회복이 마지막으로 들어온 게임 시각. 겹치기 감산 판정에만 씁니다. */
    private static final TowerDataKey<Long> LAST_MEADOW_HEAL_TICK =
            TowerDataKey.of(plantId("last_meadow_heal_tick"), Long.class);

    public PlantCombatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PlantCombatTower(
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
    public boolean canChaseTargets() {
        return false;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        if (!getDataOrDefault(PLACED, false)) {
            if (augmentSnapshot().has(PlantAugments.GROWTH)) {
                int highest = lane.towers().stream().filter(PlantCombatTower.class::isInstance)
                        .map(PlantCombatTower.class::cast)
                        .filter(plant -> plant != this && ownerPlayer().equals(plant.ownerPlayer()) && plant.family() == family())
                        .mapToInt(PlantCombatTower::growthRounds).max().orElse(0);
                setData(GROWTH_ROUNDS, (int) Math.floor(highest
                        * augmentSnapshot().parameter(PlantAugments.GROWTH, "inheritRatio", 0.25)));
            }
            setData(PLACED, true);
        }
        super.onPlaced(lane);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        return super.modifyAttackDamage(source, target, damage) * (1.0 + PlantAugments.worldTreeBonus(this));
    }

    // ------------------------------------------------------------------
    // 개화 - 계열 지형 칸 수에 비례한 피해 증가
    // ------------------------------------------------------------------

    /** 개화 피해: 계열 지형 위에 서 있으면 그 계열 칸 수에 비례합니다. */
    public double bloomBonus() {
        PlantSoil soil = family();
        if (soil == null || standingSoil() != soil) {
            return 0.0;
        }
        double cap = global("bloomDamageCap");
        double bonus = PlantSoilStates.count(ownerPlayer(), soil) * global("bloomDamagePerTile");
        return Math.max(0.0, Math.min(cap, bonus));
    }

    @Override
    public double modifyOutgoingDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount * (1.0 + bloomBonus() + damageGrowthBonus()) * rollCritMultiplier(towerEntity);
    }

    /**
     * 회백토 계열의 치명타입니다. 초치명타를 먼저 굴리고, 실패하면 일반 치명타를 굴립니다.
     *
     * <p>스플래시와 광역은 이미 치명타가 반영된 피해를 비율로 받아 쓰므로 중복으로 굴리지 않습니다.
     */
    private double rollCritMultiplier(SemionTowerEntity towerEntity) {
        if (towerEntity == null) {
            return 1.0;
        }
        RandomSource random = towerEntity.getRandom();
        double superChance = ability("superCritChance");
        if (superChance > 0.0 && random.nextDouble() < superChance) {
            return Math.max(1.0, ability("superCritMultiplier"));
        }
        double critChance = ability("critChance");
        if (critChance > 0.0 && random.nextDouble() < critChance) {
            return Math.max(1.0, ability("critMultiplier"));
        }
        return 1.0;
    }

    /**
     * 회백토의 라운드 누적 피해 성장입니다. 잔디가 체력을 키우듯 회백토는 피해를 키웁니다.
     */
    public double damageGrowthBonus() {
        if (!standsOn(PlantSoil.PODZOL)) {
            return 0.0;
        }
        double cap = scaled(PlantSoil.PODZOL, "damageGrowthCap");
        return Math.max(0.0, Math.min(cap, growthRounds() * scaled(PlantSoil.PODZOL, "damageGrowthPerRound")));
    }

    // ------------------------------------------------------------------
    // 회백토 - 사거리와 공격 속도
    // ------------------------------------------------------------------

    @Override
    public double adjustAttackRange(double baseRange) {
        if (!standsOn(PlantSoil.PODZOL)) {
            return baseRange;
        }
        return baseRange + scaled(PlantSoil.PODZOL, "rangeBonus");
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (!standsOn(PlantSoil.PODZOL)) {
            return baseIntervalTicks;
        }
        double bonus = Math.max(0.0, scaled(PlantSoil.PODZOL, "attackSpeedBonus"));
        return Math.max(minimumAttackIntervalTicks(), (int) Math.ceil(baseIntervalTicks / (1.0 + bonus)));
    }

    // ------------------------------------------------------------------
    // 잔디 - 라운드마다 최대 체력 성장
    // ------------------------------------------------------------------

    @Override
    protected double builderCurrentMaxHealth() {
        return applyTraitMaxHealth(maxHealth() * (1.0 + growthBonus())) * (1.0 + PlantAugments.worldTreeBonus(this));
    }

    /**
     * 자기 지형 위에서 라운드를 넘길 때마다 성장 스택이 쌓입니다. 잔디는 최대 체력으로, 회백토는
     * 피해로 환산됩니다.
     */
    /** 라운드를 넘길 때 성장 스택이 쌓이는지. 기본은 지형 위에 서 있을 때입니다. */
    protected boolean growsThisRound() {
        return standingSoil() != null;
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        if (growsThisRound()) {
            setData(GROWTH_ROUNDS, growthRounds() + 1);
        }
        super.resetForRound(lane);
    }

    public int growthRounds() {
        return Math.max(0, getDataOrDefault(GROWTH_ROUNDS, 0));
    }

    public void addGrowthRounds(int rounds, PlayerLane lane) {
        double ratio = health() / Math.max(1.0, currentMaxHealth());
        setData(GROWTH_ROUNDS, growthRounds() + Math.max(0, rounds));
        syncHealth(currentMaxHealth() * ratio);
        onStateChanged(lane);
    }

    double growthBonus() {
        if (!standsOn(PlantSoil.MEADOW)) {
            return 0.0;
        }
        double cap = scaled(PlantSoil.MEADOW, "maxHealthGrowthCap");
        return Math.max(0.0, Math.min(cap, growthRounds() * scaled(PlantSoil.MEADOW, "maxHealthGrowthPerRound")));
    }

    // ------------------------------------------------------------------
    // 균사 - 취약 지형과 소모성 지뢰
    // ------------------------------------------------------------------

    /**
     * 라일락처럼 {@code splashRadius} 를 가진 타워만 주변 적까지 함께 때립니다.
     */
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
        if (towerEntity == null || target == null || resolvedOutgoingDamage <= 0.0) {
            return;
        }
        applySplash(towerEntity, target, resolvedOutgoingDamage);
        applyNova(towerEntity, target, resolvedOutgoingDamage);
    }

    /**
     * 대상 주변으로 퍼지는 광역입니다 (라일락의 부채꼴 꽃가루, 물병 식물의 착탄 포격).
     *
     * <p>{@code splashConeDegrees} 가 있으면 원형 대신 대상 지점에서 타워 반대편으로 뻗는 부채꼴이
     * 됩니다. {@code splashMissingHealthRatio} 는 맞은 적이 잃은 체력에 비례한 추가 피해입니다.
     */
    private void applySplash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double outgoingDamage) {
        double radius = ability("splashRadius");
        double ratio = ability("splashDamageRatio");
        if (radius <= 0.0) {
            return;
        }
        double coneDegrees = ability("splashConeDegrees");
        double missingHealthRatio = ability("splashMissingHealthRatio");
        double snare = ability("snareMoveSpeedReduction");
        int snareTicks = abilityTicks("snareDurationTicks");
        if (ratio <= 0.0 && missingHealthRatio <= 0.0 && snare <= 0.0) {
            return;
        }

        // 포격에 직접 맞은 대상은 중복 피해를 막으려고 광역 판정에서 빠집니다
        // (aroundTarget 이 대상 UUID 를 제외 목록에 넣습니다). 그래서 속박까지 같이 빠져
        // "맞은 적이 발이 묶인다"는 물병 식물의 핵심이 정작 맞은 적에게만 안 걸렸습니다.
        // 피해는 이미 들어갔으니 여기서는 속박만 따로 겁니다.
        if (snare > 0.0 && snareTicks > 0 && target != null
                && target.runtimeMonster() != null && target.runtimeMonster().isAlive()) {
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, snare, snareTicks);
        }

        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                        AreaEffectIds.tower(this, "petal_burst"),
                        towerEntity,
                        target,
                        radius,
                        AreaVfxSpec.none()
                )
                .withFilter(monster -> withinCone(towerEntity, target, monster, coneDegrees));
        showSplash(towerEntity, target, radius, coneDegrees, snare > 0.0 && snareTicks > 0);

        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            double damage = outgoingDamage * ratio + missingHealthDamage(monster, missingHealthRatio);
            boolean killed = damage > 0.0
                    && damageResolvedTargetResult(towerEntity, monster, damage, DamageType.MAGIC).killed();
            if (!killed && snare > 0.0 && snareTicks > 0) {
                monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, snare, snareTicks);
            }
            if (killed) {
                onKill(towerEntity, monster, damage);
                return AreaEffectOutcome.KILLED;
            }
            return AreaEffectOutcome.APPLIED;
        });
    }

    /** 광역 연출: 곡사면 물병 포격, 부채꼴이면 라일락 꽃가루, 그 밖에는 꽃잎 고리를 맞은 자리에 띄웁니다. */
    private void showSplash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double radius, double coneDegrees,
            boolean snare) {
        if (!(towerEntity.level() instanceof ServerLevel level)) {
            return;
        }
        long seed = PlantDisplayVfx.seed(level);
        Vec3 from = towerEntity.position();
        Vec3 at = target.position();
        double arcHeight = ability("lobArcHeight");
        if (arcHeight > 0.0) {
            Vector3f end = new Vector3f((float) (at.x - from.x), (float) (at.y - from.y), (float) (at.z - from.z));
            PlantDisplayVfx.play(level, PlantDisplayVfx.pitcherLob(end, arcHeight, radius, snare, seed), from);
        } else if (coneDegrees > 0.0) {
            Vec3 axis = flatten(at.subtract(from));
            float yaw = axis.lengthSqr() < 1.0E-6 ? 0.0F : kim.biryeong.semiontd.vfx.DisplayShapes.yawOf(axis.x, axis.z);
            PlantDisplayVfx.play(level, PlantDisplayVfx.lilacCone(yaw, radius, coneDegrees, seed), at);
        } else {
            PlantDisplayVfx.play(level, PlantDisplayVfx.tulipNova(radius, seed), at);
        }
    }

    /** 대상이 잃은 체력에 비례한 추가 피해입니다. 두들겨 맞은 적일수록 더 아픕니다. */
    private static double missingHealthDamage(SemionMonsterEntity monster, double ratio) {
        if (ratio <= 0.0 || monster == null || monster.runtimeMonster() == null) {
            return 0.0;
        }
        double missing = monster.runtimeMonster().maxHealth() - monster.runtimeMonster().health();
        return missing <= 0.0 ? 0.0 : missing * ratio;
    }

    /**
     * 대상 지점을 꼭짓점으로, 타워에서 대상을 향하는 방향으로 뻗는 부채꼴 판정입니다.
     * {@code degrees} 가 0 이거나 360 이상이면 원형 그대로 둡니다.
     */
    private static boolean withinCone(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            SemionMonsterEntity candidate,
            double degrees
    ) {
        if (degrees <= 0.0 || degrees >= 360.0 || source == null || target == null || candidate == null) {
            return true;
        }
        Vec3 axis = flatten(target.position().subtract(source.position()));
        Vec3 toCandidate = flatten(candidate.position().subtract(target.position()));
        if (axis.lengthSqr() < 1.0E-6 || toCandidate.lengthSqr() < 1.0E-6) {
            return true;
        }
        double cos = axis.normalize().dot(toCandidate.normalize());
        return cos >= Math.cos(Math.toRadians(degrees / 2.0));
    }

    private static Vec3 flatten(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    /** 타워 자신을 중심으로 터지는 광역입니다 (튤립 계열). 주 대상은 이미 맞았으니 제외합니다. */
    private void applyNova(SemionTowerEntity towerEntity, SemionMonsterEntity target, double outgoingDamage) {
        double radius = TowerBalanceRuntime.ability(type().id(), "novaRadius", 0.0);
        double ratio = TowerBalanceRuntime.ability(type().id(), "novaDamageRatio", 0.0);
        if (radius <= 0.0 || ratio <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                        AreaEffectIds.tower(this, "bloom_nova"),
                        towerEntity,
                        radius,
                        AreaVfxSpec.none()
                )
                .withFilter(monster -> monster != null && !monster.getUUID().equals(target.getUUID()));
        if (towerEntity.level() instanceof ServerLevel level) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.tulipNova(radius, PlantDisplayVfx.seed(level)), towerEntity.position());
        }
        damageArea(towerEntity, request, outgoingDamage * ratio);
    }

    private void damageArea(SemionTowerEntity towerEntity, MonsterAreaEffectRequest request, double damage) {
        if (damage <= 0.0) {
            return;
        }
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            boolean killed = damageResolvedTargetResult(towerEntity, monster, damage, DamageType.MAGIC).killed();
            if (killed) {
                onKill(towerEntity, monster, damage);
                return AreaEffectOutcome.KILLED;
            }
            return AreaEffectOutcome.APPLIED;
        });
    }

    // ------------------------------------------------------------------
    // 모래 - 가시 반사
    // ------------------------------------------------------------------

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (towerEntity == null || damageSource == null || damageAmount <= 0.0
                || !standsOn(PlantSoil.DESERT)) {
            return;
        }
        if (!(damageSource.getEntity() instanceof SemionMonsterEntity attacker)) {
            return;
        }
        // 사암 계열은 스스로 공격하지 않습니다. 공격력은 전부 반사 피해에 얹힙니다.
        double reflect = damageAmount * scaled(PlantSoil.DESERT, "thornReflectRatio") + type().damage();
        if (reflect <= 0.0) {
            return;
        }
        damageTarget(towerEntity, attacker, reflect, DamageType.MAGIC);
    }

    // ------------------------------------------------------------------
    // 지형 펄스 - 잔디 재생 / 균사 취약 / 사암 공속 약화
    // ------------------------------------------------------------------

    @Override
    protected boolean execute(PlayerLane lane) {
        PlantSoil soil = standingSoil();
        SemionTowerEntity source = towerEntity(lane).orElse(null);
        if (soil != null && source != null) {
            switch (soil) {
                case MEADOW -> applyMeadowSupport(lane, source);
                case DESERT -> applySoilAura(source, soil);
                case MYCELIUM, PODZOL -> {
                    // 회백토는 상시 효과, 균사는 지형 자체 효과라 펄스에서 할 일이 없습니다.
                }
            }
        }
        // 항상 true 를 돌려 펄스 간격만큼 쉬게 합니다.
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, globalTicks("soilPulseIntervalTicks"));
    }

    /**
     * 잔디는 후방 지원 지형입니다. 자기만 회복하지 않고 주변 아군 타워를 함께 회복시키고,
     * 그동안 쌓은 성장 체력의 일부를 최대 체력 버프로 나눠 줍니다.
     */
    private void applyMeadowSupport(PlayerLane lane, SemionTowerEntity source) {
        double radius = scaled(PlantSoil.MEADOW, "supportRadius");
        double healPercent = scaled(PlantSoil.MEADOW, "healPercentPerPulse");
        if (radius <= 0.0 || healPercent <= 0.0) {
            return;
        }
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "meadow_support"),
                source,
                radius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.none()
        );
        ServerLevel level = source.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        boolean[] healedAny = {false};
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            if (!heal(target, healPercent)) {
                return AreaEffectOutcome.UNCHANGED;
            }
            healedAny[0] = true;
            target.entity().ifPresent(entity -> PlantDisplayVfx.play(level,
                    PlantDisplayVfx.meadowHeal(PlantDisplayVfx.seed(level) + entity.getId()), entity.position()));
            return AreaEffectOutcome.APPLIED;
        });
        if (healedAny[0]) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.meadowPulse(radius, PlantDisplayVfx.seed(level)), source.position());
        }
    }

    /**
     * 이 타워가 라인 전체에 기여하는 최대 체력 보너스입니다.
     *
     * <p>{@link PlantSoilEnvironment} 가 라인의 모든 잔디 타워 몫을 합산해 라인 안 모든 타워에게
     * 같은 값으로 겁니다. 거리 제한이 없습니다.
     */
    public double sharedGrowthBonus() {
        if (!standsOn(PlantSoil.MEADOW)) {
            return 0.0;
        }
        return Math.max(0.0, growthBonus() * soilValue(PlantSoil.MEADOW, "growthShareRatio"));
    }

    /**
     * 회백토가 라인 전체에 나눠 주는 피해 성장입니다.
     *
     * <p>잔디가 최대 체력을 나누듯 회백토는 피해를 나눕니다. 나누기 전의 {@link #damageGrowthBonus()}
     * 는 회백토 위에 선 자기 자신에게만 붙고, 여기서 계산한 몫은 계열과 무관하게 라인 안 모든
     * 타워에게 같은 값으로 걸립니다.
     */
    public double sharedDamageGrowthBonus() {
        if (!standsOn(PlantSoil.PODZOL)) {
            return 0.0;
        }
        return Math.max(0.0, damageGrowthBonus() * soilValue(PlantSoil.PODZOL, "growthShareRatio"));
    }

    /**
     * 잔디 회복 한 번. 이미 다른 잔디가 이번 주기에 회복시킨 대상이면 절반만 들어갑니다.
     *
     * <p>잔디를 여러 개 겹쳐 두면 회복이 그대로 곱절이 됩니다. 한 대상에 붙는 두 번째부터를
     * 깎아, 겹치기 자체는 유효하되 개수만큼 선형으로 늘어나지는 않게 합니다.
     *
     * <p>겹침 판정은 대상 타워에 마지막으로 회복이 들어온 시각을 적어 두고 봅니다. 잔디들의
     * 펄스는 서로 맞춰져 있지 않아서 "같은 펄스"라는 게 없기 때문에, 펄스 간격만큼의 창을
     * 두고 그 안에 겹치면 나중 것을 깎습니다.
     */
    private boolean heal(AreaTowerTarget target, double percent) {
        Tower healed = target.tower();
        double amount = healed.currentMaxHealth() * percent;
        if (amount <= 0.0) {
            return false;
        }
        SemionTowerEntity entity = target.entity().orElse(null);
        if (entity == null) {
            return false;
        }
        long now = entity.level().getGameTime();
        long window = Math.max(1, globalTicks("soilPulseIntervalTicks"));
        Long lastHealedTick = healed.getDataOrDefault(LAST_MEADOW_HEAL_TICK, null);
        boolean overlapping = lastHealedTick != null && now - lastHealedTick < window;
        if (overlapping) {
            amount *= Math.max(0.0, 1.0 - global("meadowHealOverlapReduction"));
        }
        if (amount <= 0.0 || !healTarget(entity, amount)) {
            return false;
        }
        // 창은 첫 회복에만 엽니다. 깎인 회복까지 시각을 갱신하면 창이 계속 밀려나서, 잔디가
        // 둘만 돼도 온전한 회복이 두 번 다시 오지 않습니다 - 겹침 감산이 아니라 상시 감산이
        // 됩니다. 창을 고정해 두면 창마다 온전한 회복 하나 + 나머지 절반으로 정리됩니다.
        if (!overlapping) {
            healed.setData(LAST_MEADOW_HEAL_TICK, now);
        }
        entity.playHealingAnimation();
        return true;
    }

    /**
     * 민들레 계열이 생존한 웨이브를 마칠 때 만드는 다이아입니다.
     */
    public long diamondPerWave() {
        // 정산 시점에는 클리어한 라인의 타워가 이미 최종 방어 위치로 이동해 있습니다.
        if (PlantSoilStates.soilAt(ownerPlayer(), originalPosition()) != PlantSoil.MEADOW) {
            return 0L;
        }
        return Math.max(0L, Math.round(TowerBalanceRuntime.ability(type().id(), "diamondPerWave", 0.0)));
    }

    /**
     * 사암 전투 타워가 자기 주변 사암 위의 적에게 거는 공격 속도 감소입니다. 지형 자체 효과보다 값이
     * 커서, 같은 칸에 겹치면 더 강한 쪽(타워)이 적용됩니다.
     */
    private void applySoilAura(SemionTowerEntity source, PlantSoil soil) {
        int durationTicks = soilTicks(soil, "debuffDurationTicks");
        double magnitude = scaled(soil, "attackSpeedReduction");
        if (durationTicks <= 0 || magnitude <= 0.0) {
            return;
        }
        TimedEffectType effectType = TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                        AreaEffectIds.tower(this, "soil_" + soil.key()),
                        source,
                        auraRadius(),
                        AreaVfxSpec.none()
                )
                .withFilter(monster -> standsOnSoil(monster, soil));
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            double previous = monster.activeTimedEffectMagnitude(effectType);
            monster.applyTimedEffect(effectType, magnitude, durationTicks);
            boolean changed = Double.compare(previous, monster.activeTimedEffectMagnitude(effectType)) != 0;
            if (changed && monster.level() instanceof ServerLevel level) {
                PlantDisplayVfx.play(level, PlantDisplayVfx.sandSlow(PlantDisplayVfx.seed(level) + monster.getId()), monster.position());
            }
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
    }

    private boolean standsOnSoil(SemionMonsterEntity monster, PlantSoil soil) {
        return monster != null
                && PlantSoilStates.soilAtColumn(ownerPlayer(), Mth.floor(monster.getX()), Mth.floor(monster.getZ()))
                == soil;
    }

    /**
     * 지형 장판은 사거리를 따라가되 상한을 둡니다. 식물은 사거리가 길어 장판이 레인을 통째로 덮으면
     * 지형을 넓히는 의미가 사라집니다.
     */
    private double auraRadius() {
        PlantSoil soil = standingSoil();
        // 공격하지 않는 계열은 사거리가 0 이라 장판 크기를 지형에서 직접 받습니다.
        double explicit = soil == null ? 0.0 : soilValue(soil, "auraRadius");
        if (explicit > 0.0) {
            return explicit;
        }
        double radius = Math.max(global("soilAuraMinRadius"), type().range());
        double max = global("soilAuraMaxRadius");
        return max > 0.0 ? Math.min(max, radius) : radius;
    }

    @Override
    public List<String> runtimeDetailLines() {
        return PlantTowerStatsView.create(this);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PlantSoil family() {
        return PlantTowers.soilOf(type());
    }

    PlantSoil standingSoil() {
        return deployedAtFinalDefense() || PlantAugments.inWorldTree(this)
                ? family() : PlantSoilStates.soilAt(ownerPlayer(), position());
    }

    /** 이 지형의 효과를 받는지. 기본은 그 지형 위에 서 있을 때이고, 정원사는 균사를 뺀 모든 지형입니다. */
    protected boolean standsOn(PlantSoil soil) {
        return standingSoil() == soil;
    }

    private int tier() {
        return PlantTowers.tierOf(type());
    }

    /**
     * Soil values are shared by the whole family; {@code soilPower} scales them per tier.
     */
    protected double scaled(PlantSoil soil, String key) {
        return soilValue(soil, key) * Math.max(0.0, TowerBalanceRuntime.ability(type().id(), "soilPower", 1.0));
    }

    protected double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key, 0.0);
    }

    protected int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key, 0);
    }

    private double soilValue(PlantSoil soil, String key) {
        return TowerBalanceRuntime.ability(soil.configId(), key);
    }

    private int soilTicks(PlantSoil soil, String key) {
        return TowerBalanceRuntime.abilityTicks(soil.configId(), key);
    }

    protected double global(String key) {
        return TowerBalanceRuntime.ability(PlantTowers.GLOBAL_CONFIG_ID, key);
    }

    private int globalTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(PlantTowers.GLOBAL_CONFIG_ID, key);
    }

    protected Optional<SemionTowerEntity> towerEntity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static Identifier plantId(String path) {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "plant/" + path);
    }
}
