package kim.biryeong.semiontd.tower.magicschool;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.Upgrade;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;

public abstract class MagicSchoolWizardTower extends ProductionTower {
    private int spellWave;
    private int spellAggroBonus;
    private int spellAttackCount;
    private boolean firstDisarmUsed;
    private long nextProtectionVisualTick;
    private long nextRennervateTick;
    private long nextLeviosaTick;
    private long nextTransferTick;
    private boolean potionUsed;
    private static final TowerDataKey<String> SELECTED_SPELL = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_selected_spell"), String.class);
    private static final TowerDataKey<Double> PROFICIENCY = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_proficiency"), Double.class);
    private static final TowerDataKey<Integer> LAST_PROFICIENCY_ROUND = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_proficiency_round"), Integer.class);
    private static final TowerDataKey<String> LAST_WAVE_SPELL = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_last_wave_spell"), String.class);
    private static final TowerDataKey<Integer> LAST_SPELL_ROUND = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_last_spell_round"), Integer.class);

    protected MagicSchoolWizardTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        selectSpell(MagicSchoolSpell.EXPELLIARMUS);
    }

    public final MagicSchoolSpell selectedSpell() {
        return MagicSchoolSpell.find(getDataOrDefault(SELECTED_SPELL, MagicSchoolSpell.EXPELLIARMUS.id()))
                .filter(spell -> spell.requiredSpellTier() <= maxSpellTier())
                .orElse(MagicSchoolSpell.EXPELLIARMUS);
    }

    public final int maxSpellTier() {
        if (MagicSchoolTowers.belongsToHouse(type(), MagicSchoolTowers.RAVENCLAW)) return 5;
        int base = MagicSchoolTowers.isArchWizard(type()) ? 4 : MagicSchoolTowers.isHouseWizard(type()) ? 3 : 2;
        return base + (MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.ADVANCED_SPELLS) ? 1 : 0);
    }

    public enum SpellChangeResult {
        CHANGED, ALREADY_SELECTED, INVALID_TOWER, INVALID_PHASE, UNAVAILABLE, NOT_ENOUGH_EMERALDS
    }

    public final boolean canManageSpells(SemionGame game, UUID player) {
        return game != null && ownerPlayer().equals(player) && !isTemporaryCopy() && game.isActiveParticipant(player)
                && game.playerLane(player).map(lane -> lane.towers().contains(this)).orElse(false);
    }

    public final SpellChangeResult changeSpell(SemionGame game, UUID player, MagicSchoolSpell spell) {
        Objects.requireNonNull(spell, "spell");
        if (!canManageSpells(game, player)) return SpellChangeResult.INVALID_TOWER;
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) return SpellChangeResult.INVALID_PHASE;
        if (selectedSpell() == spell) return SpellChangeResult.ALREADY_SELECTED;
        if (spellUnavailableReason(spell) != null) return SpellChangeResult.UNAVAILABLE;
        if (!game.players().get(player).economy().spendEmerald(spellChangeCost(spell))) {
            return SpellChangeResult.NOT_ENOUGH_EMERALDS;
        }
        assignSpell(spell);
        return SpellChangeResult.CHANGED;
    }

    final boolean selectSpell(MagicSchoolSpell spell) {
        Objects.requireNonNull(spell, "spell");
        if (spellUnavailableReason(spell) != null) return false;
        assignSpell(spell);
        return true;
    }

    private void assignSpell(MagicSchoolSpell spell) {
        setData(SELECTED_SPELL, spell.id());
        runtimeEntity(attachedLane()).ifPresent(entity -> {
            if (spellWave > 0) MagicSchoolSpellCombat.applySelfProtection(this, entity);
            entity.refreshCombatStats();
        });
    }

    public final String spellUnavailableReason(MagicSchoolSpell spell) {
        if (spell == MagicSchoolSpell.MUGGLE_WAND && !augmentSnapshot().has(MagicSchoolAugments.MUGGLE_WAND)) {
            return "전용 실버 증강 '머글의 지팡이'가 필요합니다.";
        }
        if (spell.requiredSpellTier() > maxSpellTier()) return "이 마법사는 " + maxSpellTier() + "단계 주문까지 사용할 수 있습니다.";
        if (spell.curse()) {
            if (!augmentSnapshot().has(MagicSchoolAugments.UNFORGIVABLE_CURSES)) return "전용 프리즘 증강 '용서받지 못할 저주'가 필요합니다.";
        } else if (!MagicSchoolCurriculum.isSpellTierUnlocked(ownerPlayer(), spell.tier())) {
            return "호그와트 커리큘럼에서 해당 주문 단계를 해금하세요.";
        }
        if (spell.curse() && attachedLane() != null && attachedLane().towers().stream().anyMatch(other ->
                other != this && !other.isTemporaryCopy() && other instanceof MagicSchoolWizardTower wizard
                        && ownerPlayer().equals(other.ownerPlayer()) && wizard.selectedSpell() == spell)) {
            return "다른 마법사가 이미 이 저주를 장착하고 있습니다.";
        }
        return null;
    }

    public final double maxProficiency() {
        double base = TowerBalanceRuntime.ability(type().id(), "maxProficiency",
                MagicSchoolTowers.isFreshman(type()) ? 100 : MagicSchoolTowers.isHouseWizard(type()) ? 300 : 1000);
        return base + (MagicSchoolTowers.isArchWizard(type()) && augmentSnapshot().has(MagicSchoolAugments.GRADUATE_SCHOOL)
                ? augmentSnapshot().parameter(MagicSchoolAugments.GRADUATE_SCHOOL, "proficiencyCapBonus", 250) : 0);
    }

    public final double proficiency() {
        return Math.min(maxProficiency(), Math.max(0, getDataOrDefault(PROFICIENCY, 0.0)));
    }

    public final double gainProficiency(double baseAmount, PlayerLane lane) {
        if (!Double.isFinite(baseAmount) || baseAmount <= 0 || isTemporaryCopy()) return 0;
        double previous = proficiency();
        double bonus = TowerBalanceRuntime.ability(type().id(), "proficiencyGainBonus", 0);
        double next = Math.min(maxProficiency(), previous + baseAmount * (1.0 + bonus)
                * MagicSchoolCurriculum.lessonMultiplier(ownerPlayer(), Upgrade.MAGIC_HISTORY)
                * (augmentSnapshot().has(MagicSchoolAugments.GRADUATE_SCHOOL)
                        ? 1 + augmentSnapshot().parameter(MagicSchoolAugments.GRADUATE_SCHOOL, "proficiencyGainBonus", .3) : 1));
        setData(PROFICIENCY, next);
        refreshCurriculumStats(lane);
        return next - previous;
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        if (currentRound <= 0 || currentRound <= getDataOrDefault(LAST_PROFICIENCY_ROUND, 0) || isTemporaryCopy()) return;
        setData(LAST_PROFICIENCY_ROUND, currentRound);
        if (health() <= 0) return;
        var spell = selectedSpell();
        spellAggroBonus = spell == MagicSchoolSpell.PROTEGO || spell == MagicSchoolSpell.PROTEGO_MAXIMA
                ? spell.ticks("waveAggroBonus") : 0;
        gainProficiency(TowerBalanceRuntime.ability(MagicSchoolTowers.CONFIG_ID, "waveProficiencyBase", 10) + currentRound, lane);
        if (hasMentor(lane)) {
            gainProficiency(TowerBalanceRuntime.ability(type().id(), "mentorProficiency",
                    MagicSchoolTowers.isFreshman(type()) ? 8 : 15), lane);
        }
        spellWave = currentRound;
        applySpellPractice(lane, currentRound, spell);
        applyDuelingPractice(lane);
        if (health() <= 0) return;
        spellAttackCount = 0;
        firstDisarmUsed = false;
        nextProtectionVisualTick = 0;
        runtimeEntity(lane).ifPresent(entity -> {
            MagicSchoolSpellCombat.onWaveStarted(this, entity);
            nextRennervateTick = entity.level().getGameTime() + MagicSchoolSpell.RENNERVATE.ticks("intervalTicks");
            nextLeviosaTick = entity.level().getGameTime() + MagicSchoolSpell.WINGARDIUM_LEVIOSA.ticks("intervalTicks");
        });
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        nextTransferTick = 0;
        potionUsed = false;
        spellWave = 0;
        spellAggroBonus = 0;
        spellAttackCount = 0;
        firstDisarmUsed = false;
        nextProtectionVisualTick = 0;
        nextRennervateTick = 0;
        nextLeviosaTick = 0;
        super.resetForRound(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previous) {
        super.copyRuntimeStateFrom(previous);
        if (previous instanceof MagicSchoolWizardTower wizard) {
            if (wizard.isGraduationTo(type())) setData(PROFICIENCY, 0.0);
            spellWave = wizard.spellWave;
            spellAggroBonus = wizard.spellAggroBonus;
            spellAttackCount = wizard.spellAttackCount;
            firstDisarmUsed = wizard.firstDisarmUsed;
            nextProtectionVisualTick = wizard.nextProtectionVisualTick;
            nextRennervateTick = wizard.nextRennervateTick;
            nextLeviosaTick = wizard.nextLeviosaTick;
            nextTransferTick = wizard.nextTransferTick;
            potionUsed = wizard.potionUsed;
        }
    }

    private void applySpellPractice(PlayerLane lane, int currentRound, MagicSchoolSpell spell) {
        boolean repeated = getDataOrDefault(LAST_SPELL_ROUND, 0) == currentRound - 1
                && spell.id().equals(getDataOrDefault(LAST_WAVE_SPELL, ""));

        setData(LAST_WAVE_SPELL, spell.id());
        setData(LAST_SPELL_ROUND, currentRound);
        if (!MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.SPELL_PRACTICE)) return;
        double amount = spell.requiredSpellTier() * MagicSchoolCurriculum.value("spellPracticePerTier", 2)
                + (repeated ? MagicSchoolCurriculum.value("spellPracticeRepeatBonus", 2) : 0);
        gainProficiency(amount, lane);
    }

    private void applyDuelingPractice(PlayerLane lane) {
        if (!MagicSchoolCurriculum.enabled(ownerPlayer(), Upgrade.DUELING_PRACTICE)) return;
        double lost = Math.min(health(), currentMaxHealth() * MagicSchoolCurriculum.value("duelingPracticeHealthRatio", .15));
        if (lost <= 0) return;

        syncHealth(health() - lost);
        runtimeEntity(lane).ifPresent(entity -> entity.setHealth((float) health()));
        boolean died = health() <= 0;
        gainProficiency(lost * MagicSchoolCurriculum.value("duelingPracticeProficiencyRatio", .30), lane);
        if (died) {
            syncHealth(0);
            runtimeEntity(lane).ifPresent(entity -> entity.setHealth(0));
        }
    }

    private boolean hasMentor(PlayerLane lane) {
        if (lane == null || !MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.MENTOR)) return false;
        int tier = ProductionTowerCatalog.entry(type()).map(ProductionTowerCatalog.CatalogEntry::tier).orElse(0);
        double radius = TowerBalanceRuntime.ability(MagicSchoolTowers.CONFIG_ID, "mentorRadius", 1);
        return lane.towers().stream().anyMatch(other -> other != this && other instanceof MagicSchoolWizardTower
                && !other.isTemporaryCopy() && other.health() > 0 && ownerPlayer().equals(other.ownerPlayer())
                && ProductionTowerCatalog.entry(other.type()).map(entry -> entry.tier() > tier).orElse(false)
                && squaredDistance(position(), other.position()) <= radius * radius + 1.0E-9);
    }

    private static double squaredDistance(GridPosition first, GridPosition second) {
        double dx = (double) first.x() - second.x();
        double dy = (double) first.y() - second.y();
        double dz = (double) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public final void refreshCurriculumStats(PlayerLane lane) {
        runtimeEntity(lane).ifPresentOrElse(SemionTowerEntity::refreshMaxHealthEffects,
                () -> syncMaxHealth(effectBaseMaxHealth(), true));
    }

    private double wandBonus(String key) {
        return MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.CUSTOM_WANDS)
                ? TowerBalanceRuntime.ability(type().id(), key, 0) : 0;
    }

    public boolean hasFreeSpellChanges() {
        return false;
    }

    @Override
    protected int builderAggroPriority() {
        return super.builderAggroPriority() + spellAggroBonus;
    }

    public final long spellChangeCost(long standardCost) {
        return hasFreeSpellChanges() ? 0 : Math.max(0, standardCost);
    }

    public final long spellChangeCost(MagicSchoolSpell spell) {
        return spellChangeCost(spell.changeCost());
    }

    private double proficiencyGrowth(String key) {
        return TowerBalanceRuntime.ability(type().id(), key, MagicSchoolTowers.proficiencyPerPoint(type()));
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damageAmount) {
        return super.modifyAttackDamage(source, target, damageAmount)
                * (1 + proficiency() * proficiencyGrowth("proficiencyDamagePerPoint"))
                * MagicSchoolCurriculum.lessonMultiplier(ownerPlayer(), Upgrade.SPELL_POWER)
                * (1 + wandBonus("wandDamageBonus"));
    }

    @Override
    public double effectBaseMaxHealth() {
        return super.effectBaseMaxHealth()
                * (1 + proficiency() * proficiencyGrowth("proficiencyHealthPerPoint"))
                * MagicSchoolCurriculum.lessonMultiplier(ownerPlayer(), Upgrade.DARK_ARTS_DEFENSE)
                * (1 + wandBonus("wandHealthBonus"));
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        return Math.max(1, super.adjustAttackInterval(baseIntervalTicks)
                - (int) Math.round(wandBonus("wandAttackIntervalReduction")));
    }

    @Override
    protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {
        runtimeEntity(lane).ifPresentOrElse(entity -> entity.refreshMaxHealthEffects(false),
                () -> syncMaxHealth(effectBaseMaxHealth(), false));
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        if (isGraduationTo(option.targetType())) {
            return (!MagicSchoolTowers.isFreshman(type()) || MagicSchoolCurriculum.hasSortingHat(ownerPlayer()))
                    && proficiency() + 1.0E-6 >= maxProficiency();
        }
        return super.meetsUpgradeRequirements(lane, option);
    }

    @Override
    public boolean showsUnavailableUpgrade(PlayerLane lane, TowerUpgradeOption option) {
        return isGraduationTo(option.targetType());
    }

    @Override
    public List<String> upgradeTooltipLines(TowerUpgradeOption option) {
        if (!isGraduationTo(option.targetType())) return super.upgradeTooltipLines(option);
        var lines = new ArrayList<String>();
        if (MagicSchoolTowers.isFreshman(type())) {
            lines.add("기숙사 배정 모자: " + (MagicSchoolCurriculum.hasSortingHat(ownerPlayer()) ? "구매 완료" : "커리큘럼에서 구매 필요"));
        }
        lines.add("필요 주문 숙련도: " + number(proficiency()) + " / " + number(maxProficiency()));
        lines.add("진급 시 주문 숙련도가 0으로 초기화됩니다.");
        return lines;
    }

    private boolean isGraduationTo(TowerType target) {
        return MagicSchoolTowers.isFreshman(type()) && MagicSchoolTowers.isHouseWizard(target)
                || MagicSchoolTowers.isHouseWizard(type()) && MagicSchoolTowers.archWizardFor(type()).id().equals(target.id());
    }

    @Override
    public final DamageType primaryDamageType() {
        return selectedSpell() == MagicSchoolSpell.MUGGLE_WAND ? DamageType.PHYSICAL : DamageType.MAGIC;
    }

    @Override
    public void recordDamageDealt(SemionMonsterEntity target, double dealtDamage, DamageType damageType) {
        super.recordDamageDealt(target, dealtDamage, damageType);
        if (target == null || dealtDamage <= 0 || isTemporaryCopy()
                || target.activeTimedEffectMagnitude(kim.biryeong.semiontd.effect.TimedEffectType.MONSTER_LUMOS) <= 0
                || !MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.SPELL_TRANSFER)) return;

        runtimeEntity(attachedLane()).filter(source -> source.level().getGameTime() >= nextTransferTick).ifPresent(source -> {

            nextTransferTick = source.level().getGameTime() + MagicSchoolCurriculum.integer("spellTransferCooldownTicks", 70);
            MagicSchoolSpellCombat.transfer(this, source, target, dealtDamage);
        });
    }

    @Override
    public boolean preventLethalDamage(SemionTowerEntity entity, DamageSource source, double finalHealthDamage) {
        if (entity == null || !entity.isAlive() || finalHealthDamage < entity.getHealth() || finalHealthDamage <= 0
                || spellWave <= 0 || potionUsed || isTemporaryCopy()
                || !MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.POTIONS)) return false;
        potionUsed = true;
        recordDamageTaken(entity.getHealth());
        double restored = currentMaxHealth() * MagicSchoolCurriculum.value("potionsHealRatio", .08);
        entity.setHealth((float) restored);
        syncHealth(entity.getHealth());
        recordHealingDone(restored);
        entity.setInvulnerableTime(0);
        entity.playHealingAnimation();
        return true;
    }

    @Override
    public void onKill(SemionTowerEntity source, SemionMonsterEntity target, double damageAmount) {
        super.onKill(source, target, damageAmount);
        MagicSchoolTransfiguration.onKill(this, source, target);
    }

    @Override
    public void onIgniteKill(SemionMonsterEntity target) {
        runtimeEntity(attachedLane()).ifPresent(source -> MagicSchoolTransfiguration.onKill(this, source, target));
    }

    @Override
    protected DamageResult damageResolvedBasicAttackTargetResult(SemionTowerEntity source, SemionMonsterEntity target, double outgoingDamage) {
        double damage = spellPrimaryDamage(target, outgoingDamage);
        return damageResolvedTargetResult(source, target, damage, primaryDamageType());
    }

    public double spellPrimaryDamage(SemionMonsterEntity target, double attackDamage) {
        if (selectedSpell() == MagicSchoolSpell.MUGGLE_WAND) return selectedSpell().value("fixedDamage");
        if (selectedSpell() == MagicSchoolSpell.AVADA_KEDAVRA) {
            return target == null ? 0 : MagicSchoolSpellCombat.maximumHealth(target) * selectedSpell().value("maxHealthMultiplier");
        }
        return attackDamage * selectedSpell().damageMultiplier();
    }

    @Override
    public DamageResult damageBasicAttackTargetResult(SemionTowerEntity source, SemionMonsterEntity target, double baseDamage, DamageType type) {
        return damageResolvedTargetResult(source, target,
                spellPrimaryDamage(target, resolveBasicAttackOutgoingDamage(source, target, baseDamage)), type);
    }

    @Override
    public double spellAttackSpeedBonus() {
        return selectedSpell() == MagicSchoolSpell.AVADA_KEDAVRA ? -selectedSpell().value("attackSpeedPenalty") : 0;
    }

    @Override
    public int resolveFinalAttackInterval(int intervalTicks) {
        if (selectedSpell() == MagicSchoolSpell.MUGGLE_WAND) return selectedSpell().ticks("fixedIntervalTicks");
        if (selectedSpell().curse() && proficiency() <= MagicSchoolCurriculum.value("curseProficiencyThreshold", 500)) {
            return Math.max(1, (int) Math.ceil(intervalTicks
                    / MagicSchoolCurriculum.value("curseLowProficiencyAttackSpeedMultiplier", .1)));
        }
        return intervalTicks;
    }

    protected Comparator<SemionMonsterEntity> houseTargetOrder(SemionTowerEntity source) {
        return Comparator.comparingDouble(source::distanceToSqr);
    }

    @Override
    public final Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        if (source == null || candidates == null) return Optional.empty();
        return candidates.stream().filter(target -> target != null && source.isValidAttackTarget(target)
                        && source.distanceToSqr(target) <= source.attackRange() * source.attackRange())
                .min(Comparator.comparingInt((SemionMonsterEntity target) -> MagicSchoolSpellCombat.targetPriority(this, target))
                        .thenComparing(houseTargetOrder(source)).thenComparingDouble(source::distanceToSqr)
                        .thenComparingInt(SemionMonsterEntity::getId));
    }

    @Override public final boolean supportsForcedAttackTargeting() { return true; }

    @Override
    public final Optional<SemionMonsterEntity> selectForcedAttackTarget(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        return selectAttackTarget(source, candidates);
    }

    @Override
    public List<String> runtimeDetailLines() {
        var lines = new ArrayList<>(List.of("주문: " + selectedSpell().displayName(), selectedSpell().effectDescription(),
                "주문 숙련도: " + number(proficiency()) + " / " + number(maxProficiency()),
                "숙련도 강화: 공격력 +" + number(proficiency() * proficiencyGrowth("proficiencyDamagePerPoint") * 100)
                        + "%, 체력 +" + number(proficiency() * proficiencyGrowth("proficiencyHealthPerPoint") * 100) + "%"));
        if (hasFreeSpellChanges()) lines.add("주문 변경 비용: 무료");
        lines.add("사용 가능한 주문: " + maxSpellTier() + "단계까지 (저주는 5단계 취급)");
        if (spellAggroBonus > 0) lines.add("전투 시작 보호 주문: 어그로 +" + spellAggroBonus + " (라운드 종료까지)");
        if (MagicSchoolCurriculum.purchased(ownerPlayer(), Upgrade.POTIONS)) {
            lines.add("마법약 제조 수업: " + (potionUsed ? "이번 라운드 사용 완료" : "치명적 피해 방어 1회 남음"));
        }
        return lines;
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        entity.refreshMaxHealthEffects();
        entity.setCustomNameVisible(false);
        FakePlayerTowerVisuals.attach(entity, this);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        refreshCurriculumStats(lane);
        super.onStateChanged(lane);
        FakePlayerTowerVisuals.refresh(this);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        FakePlayerTowerVisuals.remove(this);
        super.onRemoved(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        FakePlayerTowerVisuals.remove(this);
        MagicSchoolTransfiguration.onWizardDeath(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        FakePlayerTowerVisuals.tick(this);
        if (spellWave > 0 && health() > 0) {
            runtimeEntity(lane).filter(entity -> entity.isAlive() && !entity.isRemoved()
                    && entity.activeEffectMagnitude(TimedEffectType.TOWER_PROTEGO) > 0
                    && entity.level().getGameTime() >= nextProtectionVisualTick).ifPresent(entity -> {
                TowerVfxService.showMagicSchoolVisual(entity,
                        selectedSpell(), MagicSchoolSpellVfx.Kind.SHIELD,
                        entity.position().add(0, .35, 0), .7);
                nextProtectionVisualTick = entity.level().getGameTime() + 10;
            });
        }
        if (spellWave > 0 && health() > 0 && !isTemporaryCopy()) {
            runtimeEntity(lane).filter(entity -> entity.isAlive() && entity.level().getGameTime() >= nextLeviosaTick).ifPresent(entity -> {
                long now = entity.level().getGameTime();
                int interval = MagicSchoolSpell.WINGARDIUM_LEVIOSA.ticks("intervalTicks");

                nextLeviosaTick += ((now - nextLeviosaTick) / interval + 1) * interval;
                if (selectedSpell() == MagicSchoolSpell.WINGARDIUM_LEVIOSA) MagicSchoolSpellCombat.leviosa(entity);
            });
        }
        if (spellWave > 0 && health() > 0 && selectedSpell() == MagicSchoolSpell.RENNERVATE) {
            runtimeEntity(lane).filter(entity -> entity.level().getGameTime() >= nextRennervateTick).ifPresent(entity -> {
                MagicSchoolSpellCombat.rennervate(entity);
                nextRennervateTick = entity.level().getGameTime() + selectedSpell().ticks("intervalTicks");
            });
        }
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target,
            double attemptedDamage, double outgoingDamage, double dealtDamage, boolean killedTarget) {
        super.onAttackResolved(source, target, attemptedDamage, outgoingDamage, dealtDamage, killedTarget);
        FakePlayerTowerVisuals.playAttack(this);
        if (source == null || target == null) return;
        if (selectedSpell() == MagicSchoolSpell.EXPECTO_PATRONUM) spellAttackCount++;
        boolean firstAttack = !firstDisarmUsed;
        firstDisarmUsed = true;
        if (dealtDamage > 0) {
            MagicSchoolSpellCombat.onHit(this, source, target, firstAttack, spellAttackCount);
        }
    }
}
