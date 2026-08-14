package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
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
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

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

    // ------------------------------------------------------------------
    // 개화 - 계열 지형 칸 수에 비례한 피해 증가
    // ------------------------------------------------------------------

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
    public double currentMaxHealth() {
        return applyTraitMaxHealth(maxHealth() * (1.0 + growthBonus()));
    }

    /**
     * 자기 지형 위에서 라운드를 넘길 때마다 성장 스택이 쌓입니다. 잔디는 최대 체력으로, 회백토는
     * 피해로 환산됩니다.
     */
    @Override
    public void resetForRound(PlayerLane lane) {
        if (standingSoil() != null) {
            setData(GROWTH_ROUNDS, growthRounds() + 1);
        }
        super.resetForRound(lane);
    }

    private int growthRounds() {
        return Math.max(0, getDataOrDefault(GROWTH_ROUNDS, 0));
    }

    private double growthBonus() {
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

        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                        AreaEffectIds.tower(this, "petal_burst"),
                        towerEntity,
                        target,
                        radius,
                        AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
                )
                .withFilter(monster -> withinCone(towerEntity, target, monster, coneDegrees));

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
                        AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
                )
                .withFilter(monster -> monster != null && !monster.getUUID().equals(target.getUUID()));
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
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target ->
                heal(target, healPercent) ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED);
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

    private boolean heal(AreaTowerTarget target, double percent) {
        double amount = target.tower().currentMaxHealth() * percent;
        if (amount <= 0.0) {
            return false;
        }
        SemionTowerEntity entity = target.entity().orElse(null);
        if (entity == null) {
            return false;
        }
        if (!entity.receiveHealing(amount)) {
            return false;
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
                        AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF)
                )
                .withFilter(monster -> standsOnSoil(monster, soil));
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            double previous = monster.activeTimedEffectMagnitude(effectType);
            monster.applyTimedEffect(effectType, magnitude, durationTicks);
            boolean changed = Double.compare(previous, monster.activeTimedEffectMagnitude(effectType)) != 0;
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

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    @Override
    public List<String> runtimeDetailLines() {
        PlantSoil soil = standingSoil();
        List<String> lines = new ArrayList<>();
        if (soil == null) {
            lines.add("맨땅 위라 지형 효과가 없습니다.");
            return lines;
        }
        lines.add(soil.displayName() + " 위 · 지형 " + PlantSoilStates.count(ownerPlayer(), soil) + "칸");
        lines.add("개화 피해 +" + percentInteger(bloomBonus()));
        switch (soil) {
            case MEADOW -> {
                lines.add("성장 최대 체력 +" + percentInteger(growthBonus())
                        + " · " + growthRounds() + "라운드째");
                long diamondPerWave = diamondPerWave();
                if (diamondPerWave > 0L) {
                    lines.add("웨이브 정산 다이아 +" + diamondPerWave);
                }
                double novaRadius = TowerBalanceRuntime.ability(type().id(), "novaRadius", 0.0);
                if (novaRadius > 0.0) {
                    lines.add("광역 반경 " + oneDecimal(novaRadius)
                            + " · 피해 " + percentInteger(TowerBalanceRuntime.ability(type().id(), "novaDamageRatio", 0.0)));
                }
            }
            case MYCELIUM -> lines.add("균사 취약 +"
                    + percentInteger(soilValue(soil, "environmentDamageTakenBonus")));
            case DESERT -> lines.add("공속 감소 -" + percentInteger(scaled(soil, "attackSpeedReduction"))
                    + ", 가시 반사 " + percentInteger(scaled(soil, "thornReflectRatio"))
                    + " +" + oneDecimal(type().damage()));
            case PODZOL -> {
                lines.add("사거리 +" + oneDecimal(scaled(soil, "rangeBonus"))
                        + ", 공격 속도 +" + percentInteger(scaled(soil, "attackSpeedBonus")));
                lines.add("성장 피해 +" + percentInteger(damageGrowthBonus())
                        + " · " + growthRounds() + "라운드째");
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PlantSoil family() {
        return PlantTowers.soilOf(type());
    }

    private PlantSoil standingSoil() {
        return PlantSoilStates.soilAt(ownerPlayer(), position());
    }

    private boolean standsOn(PlantSoil soil) {
        return standingSoil() == soil;
    }

    private int tier() {
        return PlantTowers.tierOf(type());
    }

    /**
     * Soil values are shared by the whole family; {@code soilPower} scales them per tier.
     */
    private double scaled(PlantSoil soil, String key) {
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

    private int soilInt(PlantSoil soil, String key) {
        return TowerBalanceRuntime.abilityInt(soil.configId(), key);
    }

    private double global(String key) {
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

    private static ResourceLocation plantId(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "plant/" + path);
    }
}
