package kim.biryeong.semiontd.tower.mage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
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
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public final class MageWizardTower extends ProductionTower {
    private PlayerLane currentLane;
    private boolean spellUsed;
    private boolean waveActive;
    private int spellCasts;
    private int castCooldown;
    private int manaRetryCooldown;
    private int missilesRemaining;
    private int missileCooldown;
    private int bombTicks = -1;
    private Vec3 bombCenter;
    private int collapseTicks = -1;

    public MageWizardTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition position) {
        super(type, owner, team, laneId, position);
    }

    public MageWizardTower(TowerType type, UUID owner, TeamId team, int laneId, GridPosition original, GridPosition current) {
        super(type, owner, team, laneId, original, current);
    }

    Optional<MageSpell> spell() {
        return MageTowers.spellFor(type());
    }

    boolean spellUsed() {
        return spellUsed;
    }

    int spellCasts() {
        return spellCasts;
    }

    String rankName() {
        if (spellCasts >= intAbility("archmageCasts", MageBalance.ARCHMAGE_CASTS)) {
            return "대마법사";
        }
        if (spellCasts >= intAbility("intermediateCasts", MageBalance.INTERMEDIATE_CASTS)) {
            return "중급 마법사";
        }
        return "초급 마법사";
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        updateEntityName(entity);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previous) {
        if (previous instanceof MageWizardTower wizard) {
            spellCasts = wizard.spellCasts;
            double ratio = wizard.currentMaxHealth() <= 0.0 ? 1.0 : wizard.health() / wizard.currentMaxHealth();
            syncHealth(currentMaxHealth() * Math.max(0.0, Math.min(1.0, ratio)));
        }
        resetSpellState();
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        Optional<MageSpell> selected = MageTowers.spellFor(option.targetType());
        return spell().isEmpty()
                && selected.isPresent()
                && MageTowerRuntime.hasCore(lane, ownerPlayer());
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        currentLane = lane;
        waveActive = true;
        spellUsed = false;
        missilesRemaining = 0;
        missileCooldown = 0;
        castCooldown = 0;
        manaRetryCooldown = 0;
        bombTicks = -1;
        bombCenter = null;
        collapseTicks = -1;
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        super.tick(lane);
        if (!waveActive) {
            return;
        }
        SemionTowerEntity source = MageTowerRuntime.entity(lane, this);
        MageSpell selected = spell().orElse(null);
        if (source == null || selected == null || !MageTowerRuntime.hasCore(lane, ownerPlayer())) {
            return;
        }
        if (missilesRemaining > 0) {
            tickMissiles(source);
            return;
        }
        if (bombTicks >= 0) {
            if (--bombTicks <= 0) {
                detonateBomb(source);
            }
            return;
        }
        if (collapseTicks >= 0) {
            if (--collapseTicks <= 0) {
                detonateCollapse(source);
            }
            return;
        }
        if (castCooldown > 0) {
            castCooldown--;
            return;
        }
        if (manaRetryCooldown > 0) {
            manaRetryCooldown--;
            return;
        }
        if (selected == MageSpell.MAGIC_AMPLIFICATION || selected == MageSpell.PROJECTILE_BARRIER) {
            if (!spellUsed) {
                tryBeginCast(selected);
            }
            return;
        }
        if (selected == MageSpell.DIMENSIONAL_COLLAPSE
                && !MageTowerRuntime.liveMonsters(currentLane).isEmpty()
                && tryBeginCast(selected)) {
            collapseTicks = ticks("collapseDelayTicks", MageBalance.DIMENSIONAL_COLLAPSE_DELAY_TICKS);
            return;
        }
        List<SemionMonsterEntity> targets = targetsInRange(source, spellRange(selected));
        if (targets.isEmpty()) {
            return;
        }
        SemionMonsterEntity primary = targets.getFirst();
        if (!tryBeginCast(selected)) {
            return;
        }
        switch (selected) {
            case MANA_MISSILE -> {
                missilesRemaining = intAbility("missileCount", MageBalance.MISSILE_COUNT);
                tickMissiles(source);
            }
            case WIND_CUTTER -> {
                castWindCutter(source, primary);
                beginCooldown(selected);
            }
            case MANA_BOMB -> {
                bombCenter = primary.position();
                bombTicks = ticks("manaBombDelayTicks", MageBalance.MANA_BOMB_DELAY_TICKS);
            }
            case CHAIN_LIGHTNING -> {
                castChainLightning(source, primary);
                beginCooldown(selected);
            }
            case FROST_WAVE -> {
                castFrostWave(source, primary);
                beginCooldown(selected);
            }
            case DIMENSIONAL_COLLAPSE -> { }
            default -> {
            }
        }
    }

    private void tickMissiles(SemionTowerEntity source) {
        if (missileCooldown-- > 0) {
            return;
        }
        MageSpell selected = MageSpell.MANA_MISSILE;
        List<SemionMonsterEntity> targets = targetsInRange(source, spellRange(selected));
        if (targets.isEmpty()) {
            return;
        }
        damageOne(source, targets.getFirst(), ability("missileDamage", MageBalance.MISSILE_DAMAGE));
        missilesRemaining--;
        missileCooldown = Math.max(1, ticks("missileIntervalTicks", MageBalance.MISSILE_INTERVAL_TICKS)) - 1;
        if (missilesRemaining <= 0) {
            beginCooldown(selected);
        }
    }

    private void castWindCutter(SemionTowerEntity source, SemionMonsterEntity primary) {
        Vec3 start = source.position();
        Vec3 direction = primary.position().subtract(start);
        double length = Math.max(0.001, direction.length());
        Vec3 normalized = direction.scale(1.0 / length);
        double maxRange = spellRange(MageSpell.WIND_CUTTER);
        double width = ability("windCutterWidth", MageBalance.WIND_CUTTER_WIDTH);
        int cap = intAbility("windCutterMaxTargets", MageBalance.WIND_CUTTER_MAX_TARGETS);
        List<SemionMonsterEntity> selected = MageTowerRuntime.prioritizedMonsters(currentLane).stream()
                .filter(target -> {
                    Vec3 offset = target.position().subtract(start);
                    double projection = offset.dot(normalized);
                    return projection >= 0.0 && projection <= maxRange
                            && offset.subtract(normalized.scale(projection)).lengthSqr() <= width * width;
                })
                .limit(cap)
                .toList();
        Set<UUID> ids = MageTowerRuntime.ids(selected);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "wind_cutter"), source, start.add(normalized.scale(maxRange * 0.5)),
                maxRange, Set.of(), target -> ids.contains(target.getUUID()), AreaVfxSpec.none()
        );
        TowerAreaDamage.apply(this, source, request,
                ignored -> spellDamage(ability("windCutterDamage", MageBalance.WIND_CUTTER_DAMAGE)), true,
                (target, damage, killed) -> {}, DamageType.MAGIC);
        TowerVfxService.showSecondaryAttack(source, primary);
    }

    private void castChainLightning(SemionTowerEntity source, SemionMonsterEntity primary) {
        List<SemionMonsterEntity> available = new ArrayList<>(MageTowerRuntime.liveMonsters(currentLane));
        SemionMonsterEntity current = primary;
        for (int index = 0; index < MageBalance.CHAIN_LIGHTNING_DAMAGE.length && current != null; index++) {
            damageOne(source, current, ability("chainDamage" + (index + 1), MageBalance.CHAIN_LIGHTNING_DAMAGE[index]));
            available.remove(current);
            Vec3 center = current.position();
            double jump = ability("chainJumpRange", MageBalance.CHAIN_LIGHTNING_JUMP_RANGE);
            current = available.stream()
                    .filter(candidate -> candidate.position().distanceToSqr(center) <= jump * jump)
                    .min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(center)))
                    .orElse(null);
        }
    }

    private void castFrostWave(SemionTowerEntity source, SemionMonsterEntity primary) {
        double radius = ability("frostWaveRadius", MageBalance.FROST_WAVE_RADIUS);
        int cap = intAbility("frostWaveMaxTargets", MageBalance.FROST_WAVE_MAX_TARGETS);
        Vec3 center = primary.position();
        List<SemionMonsterEntity> selected = nearest(center, radius, cap);
        Set<UUID> ids = MageTowerRuntime.ids(selected);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "frost_wave"), source, center, radius, Set.of(),
                target -> ids.contains(target.getUUID()), AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF)
        );
        TowerAreaDamage.apply(this, source, request,
                ignored -> spellDamage(ability("frostWaveDamage", MageBalance.FROST_WAVE_DAMAGE)), true,
                (target, damage, killed) -> {
                    double slow = ability("frostWaveSlow", MageBalance.FROST_WAVE_SLOW);
                    int duration = ticks("frostWaveDurationTicks", MageBalance.FROST_WAVE_DURATION_TICKS);
                    target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, duration);
                    target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, slow, duration);
                }, DamageType.MAGIC);
    }

    private void detonateBomb(SemionTowerEntity source) {
        double radius = ability("manaBombRadius", MageBalance.MANA_BOMB_RADIUS);
        int cap = intAbility("manaBombMaxTargets", MageBalance.MANA_BOMB_MAX_TARGETS);
        List<SemionMonsterEntity> selected = MageTowerRuntime.liveMonsters(currentLane).stream()
                .filter(target -> target.position().distanceToSqr(bombCenter) <= radius * radius)
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(bombCenter)))
                .limit(cap)
                .toList();
        Set<UUID> ids = MageTowerRuntime.ids(selected);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "mana_bomb"), source, bombCenter, radius, Set.of(),
                target -> ids.contains(target.getUUID()), AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION)
        );
        TowerAreaDamage.apply(this, source, request,
                ignored -> spellDamage(ability("manaBombDamage", MageBalance.MANA_BOMB_DAMAGE)), true,
                (target, damage, killed) -> {}, DamageType.MAGIC);
        bombTicks = -1;
        bombCenter = null;
        beginCooldown(MageSpell.MANA_BOMB);
    }

    private void detonateCollapse(SemionTowerEntity source) {
        double radius = ability("collapseRadius", MageBalance.DIMENSIONAL_COLLAPSE_RADIUS);
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "dimensional_collapse"), source, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.DRAGON_BREATH)
        );
        TowerAreaDamage.apply(this, source, request,
                ignored -> spellDamage(ability("collapseDamage", MageBalance.DIMENSIONAL_COLLAPSE_DAMAGE)), true,
                (target, damage, killed) -> {}, DamageType.MAGIC);
        collapseTicks = -1;
        beginCooldown(MageSpell.DIMENSIONAL_COLLAPSE);
    }

    private List<SemionMonsterEntity> nearest(Vec3 center, double radius, int cap) {
        return MageTowerRuntime.liveMonsters(currentLane).stream()
                .filter(target -> target.position().distanceToSqr(center) <= radius * radius)
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)))
                .limit(Math.max(0, cap))
                .toList();
    }

    private List<SemionMonsterEntity> targetsInRange(SemionTowerEntity source, double range) {
        return MageTowerRuntime.prioritizedMonsters(currentLane).stream()
                .filter(target -> target.position().distanceToSqr(source.position()) <= range * range)
                .toList();
    }

    private void damageOne(SemionTowerEntity source, SemionMonsterEntity target, double base) {
        Tower.DamageResult result = damageTargetResult(source, target, spellDamage(base), DamageType.MAGIC);
        TowerVfxService.showSecondaryAttack(source, target);
        if (result.killed()) {
            onKill(source, target, base);
        }
    }

    double spellDamage(double base) {
        if (currentLane == null) {
            return base;
        }
        return Math.max(0.0, base * currentSpellDamageMultiplier());
    }

    double currentSpellDamageMultiplier() {
        double radius = ability("supportRadius", MageBalance.SUPPORT_RADIUS);
        boolean amplified = MageTowerRuntime.nearbyWizards(currentLane, this, radius).stream()
                .anyMatch(MageWizardTower::activeAmplification);
        MageStates.PlayerState state = MageStates.state(ownerPlayer());
        double manaRatio = state.capacity() <= 0 ? 0.0 : state.mana() / (double) state.capacity();
        return spellDamageMultiplier(
                manaRatio,
                ability("manaDamageBonusAtCapacity", MageBalance.MANA_DAMAGE_BONUS_AT_CAPACITY),
                amplified,
                ability("amplificationBonus", MageBalance.AMPLIFICATION_BONUS),
                rankDamageMultiplier(),
                ability("maxSpellDamageMultiplier", MageBalance.MAX_SPELL_DAMAGE_MULTIPLIER)
        );
    }

    static double spellDamageMultiplier(
            double manaRatio,
            double manaBonus,
            boolean amplified,
            double amplificationBonus,
            double rankMultiplier,
            double maximum
    ) {
        double ratio = Math.max(0.0, Math.min(1.0, manaRatio));
        return Math.min(maximum, (1.0 + ratio * manaBonus + (amplified ? amplificationBonus : 0.0)) * rankMultiplier);
    }

    boolean activeAmplification() {
        return spell().orElse(null) == MageSpell.MAGIC_AMPLIFICATION && spellUsed;
    }

    private void showSupportVfx(MageSpell selected) {
        SemionTowerEntity source = MageTowerRuntime.entity(currentLane, this);
        if (source == null) {
            return;
        }
        double radius = ability("supportRadius", MageBalance.SUPPORT_RADIUS);
        List<Vec3> targets = MageTowerRuntime.nearbyWizards(currentLane, this, radius).stream()
                .map(tower -> MageTowerRuntime.entity(currentLane, tower))
                .filter(java.util.Objects::nonNull)
                .map(SemionTowerEntity::position)
                .toList();
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, selected.id()),
                AreaVfxStyles.BUFF,
                source.position(),
                radius,
                targets,
                targets.size(),
                targets.size(),
                0
        );
    }

    private boolean activeBarrierNear() {
        double radius = ability("supportRadius", MageBalance.SUPPORT_RADIUS);
        return MageTowerRuntime.nearbyWizards(currentLane, this, radius).stream()
                .anyMatch(tower -> tower.spell().orElse(null) == MageSpell.PROJECTILE_BARRIER && tower.spellUsed);
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity entity, DamageSource source, double amount) {
        if (source != null
                && source.getEntity() instanceof SemionMonsterEntity monster
                && monster.runtimeMonster() != null
                && monster.runtimeMonster().attackKind() == AttackKind.RANGED
                && activeBarrierNear()) {
            return amount * Math.max(0.0, 1.0 - ability("rangedBarrierReduction", MageBalance.RANGED_BARRIER_REDUCTION));
        }
        return amount;
    }

    int naturalManaProduction() {
        return spellUsed ? 0 : intAbility("idleWizardMana", MageBalance.IDLE_WIZARD_MANA);
    }

    void finishRound() {
        resetSpellState();
    }

    boolean tryBeginCast(MageSpell selected) {
        if (!MageStates.state(ownerPlayer()).spend(manaCost(selected))) {
            manaRetryCooldown = intAbility("manaRetryTicks", MageBalance.MANA_RETRY_TICKS);
            return false;
        }
        spellUsed = true;
        spellCasts++;
        updateEntityName(MageTowerRuntime.entity(currentLane, this));
        if (selected == MageSpell.MAGIC_AMPLIFICATION || selected == MageSpell.PROJECTILE_BARRIER) {
            showSupportVfx(selected);
        }
        return true;
    }

    private void beginCooldown(MageSpell selected) {
        castCooldown = Math.max(1, ticks(
                selected.id() + "CooldownTicks", selected.defaultCooldownTicks()
        ));
    }

    private double spellRange(MageSpell selected) {
        return ability(selected.id() + "Range", selected.defaultRange());
    }

    private double rankDamageMultiplier() {
        if (spellCasts >= intAbility("archmageCasts", MageBalance.ARCHMAGE_CASTS)) {
            return ability("archmageDamageMultiplier", MageBalance.ARCHMAGE_DAMAGE_MULTIPLIER);
        }
        if (spellCasts >= intAbility("intermediateCasts", MageBalance.INTERMEDIATE_CASTS)) {
            return ability("intermediateDamageMultiplier", MageBalance.INTERMEDIATE_DAMAGE_MULTIPLIER);
        }
        return 1.0;
    }

    private void updateEntityName(SemionTowerEntity entity) {
        if (entity == null) {
            return;
        }
        String suffix = spell().map(value -> " · " + value.displayName()).orElse("");
        entity.setCustomName(Component.literal(rankName() + suffix));
        entity.setCustomNameVisible(true);
    }

    private void resetSpellState() {
        spellUsed = false;
        waveActive = false;
        missilesRemaining = 0;
        missileCooldown = 0;
        castCooldown = 0;
        manaRetryCooldown = 0;
        bombTicks = -1;
        bombCenter = null;
        collapseTicks = -1;
    }

    @Override
    public List<String> runtimeDetailLines() {
        MageStates.PlayerState state = MageStates.state(ownerPlayer());
        List<String> lines = new ArrayList<>();
        lines.add("<aqua>마나</aqua> <white>" + state.mana() + "/" + state.capacity() + "</white>");
        lines.add("<gold>등급</gold> <white>" + rankName() + "</white> <gray>· 시전 " + spellCasts + "회</gray>");
        lines.add(spell().map(value -> "<light_purple>지속 주문</light_purple> <white>" + value.displayName()
                        + "</white> <gray>· 사거리 " + format(spellRange(value)) + " · 마나 " + manaCost(value) + "</gray>")
                .orElse("<gray>지속 주문 없음</gray>"));
        int manaDamagePercent = (int) Math.round(
                (state.capacity() <= 0 ? 0.0 : state.mana() / (double) state.capacity())
                        * ability("manaDamageBonusAtCapacity", MageBalance.MANA_DAMAGE_BONUS_AT_CAPACITY)
                        * 100.0
        );
        lines.add("<aqua>저장 마나 주문 피해</aqua> <green>+" + manaDamagePercent + "%</green>");
        lines.add("<light_purple>현재 주문 배율</light_purple> <white>" + format(currentSpellDamageMultiplier())
                + "배</white> <gray>· 상한 "
                + format(ability("maxSpellDamageMultiplier", MageBalance.MAX_SPELL_DAMAGE_MULTIPLIER)) + "배</gray>");
        int nextRank = spellCasts < intAbility("intermediateCasts", MageBalance.INTERMEDIATE_CASTS)
                ? intAbility("intermediateCasts", MageBalance.INTERMEDIATE_CASTS)
                : intAbility("archmageCasts", MageBalance.ARCHMAGE_CASTS);
        lines.add(spellCasts >= intAbility("archmageCasts", MageBalance.ARCHMAGE_CASTS)
                ? "<gold>최고 등급 달성</gold>"
                : "<gold>다음 등급</gold> <white>" + (nextRank - spellCasts) + "회 남음</white>");
        spell().ifPresent(value -> lines.add("<yellow>대상 상한</yellow> <white>" + targetLimit(value) + "</white>"));
        if (waveActive && spell().isPresent()) {
            lines.add(MageStates.state(ownerPlayer()).canSpend(manaCost(spell().orElseThrow()))
                    ? "<green>시전 가능</green> <gray>· 재사용 " + castCooldown + "tick</gray>"
                    : "<red>마나 부족 · 시전 중단</red>");
        }
        lines.add("<green>예상 자연 생산</green> <white>+" + naturalManaProduction() + "</white>");
        return List.copyOf(lines);
    }

    private String targetLimit(MageSpell spell) {
        return switch (spell) {
            case MANA_MISSILE -> "발사당 1기";
            case WIND_CUTTER -> intAbility("windCutterMaxTargets", MageBalance.WIND_CUTTER_MAX_TARGETS) + "기";
            case MANA_BOMB -> intAbility("manaBombMaxTargets", MageBalance.MANA_BOMB_MAX_TARGETS) + "기";
            case CHAIN_LIGHTNING -> MageBalance.CHAIN_LIGHTNING_DAMAGE.length + "기";
            case FROST_WAVE -> intAbility("frostWaveMaxTargets", MageBalance.FROST_WAVE_MAX_TARGETS) + "기";
            case DIMENSIONAL_COLLAPSE -> "자기 라인 전체";
            case MAGIC_AMPLIFICATION, PROJECTILE_BARRIER -> "지원 반경 내 마법사";
        };
    }

    private static int manaCost(MageSpell spell) {
        return TowerBalanceRuntime.abilityInt(MageBalance.GLOBAL_ID, spell.id() + "ManaCost", spell.defaultManaCost());
    }

    private static double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(MageBalance.GLOBAL_ID, key, fallback);
    }

    private static int intAbility(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(MageBalance.GLOBAL_ID, key, fallback);
    }

    private static int ticks(String key, int fallback) {
        return TowerBalanceRuntime.abilityTicks(MageBalance.GLOBAL_ID, key, fallback);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
