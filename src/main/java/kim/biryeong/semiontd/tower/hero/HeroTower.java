package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public final class HeroTower extends HeroPartyTower {
    private static final ResourceLocation SWORD_BURST = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_sword_burst");
    private static final ResourceLocation GREATSWORD_SWEEP = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_greatsword_sweep");
    private static final ResourceLocation STAFF_METEOR = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_staff_meteor");
    private static final ResourceLocation TOME_HOLY = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_tome_holy");
    private int attackCount;
    private int augmentAttackCount;
    private UUID lastAttackingCompanion;
    static final String JOINT_ATTACK = "job_hero_party_s";
    static final String PROTAGONIST = "job_hero_party_g1";
    static final String PARTY_ATTACK = "job_hero_party_g2";
    static final String LEGEND = "job_hero_party_p";

    public HeroTower(
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
    public DamageType primaryDamageType() {
        return weapon().magic() ? DamageType.MAGIC : DamageType.PHYSICAL;
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        return HeroPartyBalance.weaponRange(weapon());
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        HeroWeapon weapon = weapon();
        return HeroPartyBalance.weaponAttackInterval(weapon, state().weaponLevel(weapon));
    }

    @Override
    public double effectBaseMaxHealth() {
        return super.effectBaseMaxHealth() * HeroPartyBalance.weaponMaxHealthMultiplier(weapon())
                * (augmentSnapshot().has(LEGEND) ? 1.0 + parameter(LEGEND, "healthBonus", 1.5) : 1.0);
    }

    @Override
    protected double armorEffectRatio() {
        return 1.0;
    }

    @Override
    protected int builderAggroPriority() {
        return HeroPartyBalance.weaponAggroPriority(weapon());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        HeroWeapon weapon = weapon();
        double baseRatio = HeroPartyBalance.weaponDamage(weapon) / Math.max(0.01, type().damage());
        double resolved = damageAmount * baseRatio * HeroPartyBalance.weaponMultiplier(state().weaponLevel(weapon));
        resolved *= weaponDamageMultiplier();
        if (weapon == HeroWeapon.SWORD && weaponEffectLevel() >= 5 && (attackCount + 1) % 5 == 0) {
            resolved *= 2.20 * weaponEffectStrength();
        }
        return resolved;
    }

    @Override
    public double modifyResolvedAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        HeroWeapon weapon = weapon();
        int level = weaponEffectLevel();
        if (weapon == HeroWeapon.GREATSWORD && level >= 3 && healthRatio(target) <= 0.25) {
            damageAmount *= 1.0 + 0.35 * weaponEffectStrength();
        }
        if (weapon == HeroWeapon.LONGBOW && level >= 5 && target != null && target.runtimeMonster() != null) {
            double bonus = Math.min(
                    target.runtimeMonster().maxHealth() * 0.01,
                    HeroPartyBalance.weaponDamage(weapon) * HeroPartyBalance.weaponMultiplier(state().weaponLevel(weapon))
                            * weaponDamageMultiplier() * 2.0
            ) * weaponEffectStrength();
            damageAmount += bonus;
        }
        if ((weapon == HeroWeapon.SWORD || weapon == HeroWeapon.LONGBOW) && isIncomeTarget(target)) {
            damageAmount *= 1.0 + HeroPartyBalance.weaponIncomeDamageBonus(weapon);
        }
        return damageAmount;
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        double guardReduction = towerEntity == null
                ? 0.0
                : towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION);
        double beforeGuard = guardReduction >= 0.95 ? damageAmount : damageAmount / Math.max(0.05, 1.0 - guardReduction);
        double resolved = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        if (weapon() == HeroWeapon.SWORD && weaponEffectLevel() >= 3) {
            state().recordSpecial(
                    HeroQuestKind.SWORD_DAMAGE_PREVENTED,
                    HeroWeapon.SWORD,
                    Math.max(0.0, beforeGuard - damageAmount),
                    onlineOwner(towerEntity)
            );
        }
        return resolved;
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        attackCount = 0;
        augmentAttackCount = 0;
        lastAttackingCompanion = null;
        state().markHeroAtWaveStart(weapon(), lane == null || lane.arenaWorld() == null
                ? null
                : lane.arenaWorld().getServer().getPlayerList().getPlayer(ownerPlayer()));
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
        FakePlayerTowerVisuals.playAttack(this);
        HeroWeapon weapon = weapon();
        int level = weaponEffectLevel();
        int attackNumber = ++attackCount;
        state().recordWeaponAttack(weapon, dealtDamage, killedTarget, onlineOwner(towerEntity));

        switch (weapon) {
            case SWORD -> attackWithSword(towerEntity, target, attemptedDamage, level, attackNumber);
            case GREATSWORD -> attackWithGreatsword(towerEntity, target, attemptedDamage, level, attackNumber);
            case LONGBOW -> attackWithLongbow(towerEntity, target, attemptedDamage, dealtDamage, level, attackNumber);
            case STAFF -> attackWithStaff(towerEntity, target, attemptedDamage, level, attackNumber);
            case TOME -> attackWithTome(towerEntity, target, attemptedDamage, level, attackNumber);
        }
        if (dealtDamage > 0.0 && towerEntity != null && target != null && AugmentCombat.allowsTriggers()) {
            triggerAugments(towerEntity, target);
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof HeroTower hero) {
            attackCount = hero.attackCount;
            augmentAttackCount = hero.augmentAttackCount;
            lastAttackingCompanion = hero.lastAttackingCompanion;
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>(super.runtimeDetailLines());
        HeroPartyState state = state();
        lines.add("장비: " + state.equippedWeapon().displayName() + " +" + state.weaponLevel(state.equippedWeapon()));
        double incomeDamageBonus = HeroPartyBalance.weaponIncomeDamageBonus(state.equippedWeapon());
        if (incomeDamageBonus > 0.0) {
            lines.add("인컴/소환 피해: +" + Math.round(incomeDamageBonus * 100.0) + "%");
        }
        lines.add("갑옷: +" + state.armorLevel());
        if (augmentSnapshot().has(PROTAGONIST)) {
            lines.add("무기 고유 효과: +" + weaponEffectLevel() + " 해금 / 세기 " + weaponEffectStrength() + "배");
        }
        if (augmentSnapshot().has(JOINT_ATTACK) || augmentSnapshot().has(PARTY_ATTACK) || augmentSnapshot().has(LEGEND)) {
            lines.add("증강 기본 공격 적중: " + augmentAttackCount);
        }
        lines.add("확정 동료: " + state.committedCompanions().size() + "/" + HeroPartyBalance.MAX_COMPANIONS);
        HeroPartyState.HeroQuestSnapshot quest = state.quest();
        if (quest != null) {
            lines.add("퀘스트: " + quest.label() + " " + oneDecimal(quest.progress()) + "/" + oneDecimal(quest.target()));
        }
        return List.copyOf(lines);
    }

    private void attackWithSword(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            int level,
            int attackNumber
    ) {
        double strength = weaponEffectStrength();
        double secondaryBaseDamage = level >= 5 && attackNumber % 5 == 0 ? damage / (2.20 * strength) : damage;
        if (level >= 1) {
            hitExtraTargets(source, primary, secondaryBaseDamage * strength, HeroWeapon.SWORD, new double[]{0.50});
        }
        if (level >= 3 && attackNumber % 5 == 0) {
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, 0.20 * strength, 60);
        }
        if (level >= 5 && attackNumber % 5 == 0) {
            areaAttack(source, primary, SWORD_BURST, 3.0, secondaryBaseDamage * 0.80 * strength, HeroWeapon.SWORD, HeroQuestKind.WEAPON_DAMAGE, DamageType.PHYSICAL);
        }
    }

    private void attackWithGreatsword(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            int level,
            int attackNumber
    ) {
        if (level >= 1) {
            areaAttack(source, primary, GREATSWORD_SWEEP, 2.5, damage * 0.40 * weaponEffectStrength(), HeroWeapon.GREATSWORD, HeroQuestKind.GREATSWORD_MULTI_HIT, DamageType.PHYSICAL);
        }
        if (level >= 5 && attackNumber % 4 == 0) {
            areaAttack(source, primary, GREATSWORD_SWEEP, 3.5, damage * 1.50 * weaponEffectStrength(), HeroWeapon.GREATSWORD, HeroQuestKind.GREATSWORD_MULTI_HIT, DamageType.PHYSICAL);
        }
    }

    private void attackWithLongbow(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            double dealtDamage,
            int level,
            int attackNumber
    ) {
        if (level >= 1) {
            hitExtraTargets(source, primary, damage * weaponEffectStrength(), HeroWeapon.LONGBOW, new double[]{0.65, 0.40});
        }
        if (level >= 3 && primary != null) {
            if (primary.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0.0) {
                state().recordSpecial(HeroQuestKind.LONGBOW_MARK_DAMAGE, HeroWeapon.LONGBOW, dealtDamage, onlineOwner(source));
            }
            if (attackNumber % 4 == 0 && primary.isAlive()) {
                primary.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, 0.15 * weaponEffectStrength(), 80);
            }
        }
    }

    private void attackWithStaff(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            int level,
            int attackNumber
    ) {
        if (level >= 1) {
            hitExtraTargets(source, primary, damage * weaponEffectStrength(), HeroWeapon.STAFF, new double[]{0.60, 0.35});
        }
        if (level >= 3 && primary != null && primary.isAlive()) {
            primary.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 0.20 * weaponEffectStrength(), 50);
        }
        if (level >= 5 && attackNumber % 6 == 0) {
            areaAttack(source, primary, STAFF_METEOR, 3.0, damage * 1.80 * weaponEffectStrength(), HeroWeapon.STAFF, HeroQuestKind.STAFF_SPECIAL_HITS, DamageType.MAGIC);
        }
    }

    private void attackWithTome(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            int level,
            int attackNumber
    ) {
        if (level >= 1 && attackNumber % 3 == 0) {
            healLowest(source, 20.0 * HeroPartyBalance.weaponMultiplier(state().weaponLevel(weapon())) * weaponEffectStrength(),
                    1, level >= 3 ? 0.10 * weaponEffectStrength() : 0.0);
        }
        if (level >= 5 && attackNumber % 6 == 0) {
            healLowest(source, 12.0 * HeroPartyBalance.weaponMultiplier(state().weaponLevel(weapon())) * weaponEffectStrength(), Integer.MAX_VALUE, 0.0);
            areaAttack(source, primary, TOME_HOLY, 3.0, damage * 1.20 * weaponEffectStrength(), HeroWeapon.TOME, HeroQuestKind.WEAPON_DAMAGE, DamageType.MAGIC);
        }
    }

    private void hitExtraTargets(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double damage,
            HeroWeapon weapon,
            double[] ratios
    ) {
        if (source == null || ratios == null || ratios.length == 0) {
            return;
        }
        List<SemionMonsterEntity> candidates = source.level().getEntities(
                        source,
                        source.targetSearchBox(),
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster != primary
                                && monster.runtimeMonster() != null
                                && source.defendsLane(monster.runtimeMonster().targetLaneId())
                                && source.distanceToSqr(monster) <= source.attackRange() * source.attackRange())
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(source)))
                .limit(ratios.length)
                .toList();
        for (int index = 0; index < candidates.size(); index++) {
            DamageResult result = damageBasicAttackTargetResult(
                    source, candidates.get(index), damage * ratios[index], primaryDamageType()
            );
            if (result.killed()) {
                onKill(source, candidates.get(index), damage * ratios[index]);
            }
            state().recordWeaponAttack(weapon, result.dealtDamage(), result.killed(), onlineOwner(source));
            if (weapon == HeroWeapon.STAFF) {
                state().recordSpecial(HeroQuestKind.STAFF_SPECIAL_HITS, weapon, 1.0, onlineOwner(source));
            }
        }
    }

    private void areaAttack(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            ResourceLocation effectId,
            double radius,
            double damage,
            HeroWeapon weapon,
            HeroQuestKind specialQuest,
            DamageType damageType
    ) {
        if (source == null || primary == null || damage <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                effectId,
                source,
                primary,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyResolved(
                this,
                source,
                request,
                target -> resolveBasicAttackOutgoingDamage(source, target, damage),
                true,
                (target, dealt, killed) -> {
                    state().recordWeaponAttack(weapon, dealt, killed, onlineOwner(source));
                    if (specialQuest == HeroQuestKind.GREATSWORD_MULTI_HIT || specialQuest == HeroQuestKind.STAFF_SPECIAL_HITS) {
                        state().recordSpecial(specialQuest, weapon, 1.0, onlineOwner(source));
                    }
                },
                damageType
        );
    }

    private void healLowest(SemionTowerEntity source, double amount, int count, double reduction) {
        List<SemionTowerEntity> targets = partyEntities(source).stream()
                .filter(entity -> entity.getHealth() < entity.getMaxHealth())
                .sorted(Comparator.comparingDouble(entity -> entity.getHealth() / Math.max(1.0, entity.getMaxHealth())))
                .limit(Math.max(1, count))
                .toList();
        for (SemionTowerEntity target : targets) {
            double healed = healPartyMember(target, amount);
            if (reduction > 0.0 && healed > 0.0) {
                target.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, 60);
            }
            state().recordHealing(HeroWeapon.TOME, healed, onlineOwner(source));
        }
    }

    private HeroWeapon weapon() {
        return state().equippedWeapon();
    }

    int weaponEffectLevel() {
        return augmentSnapshot().has(PROTAGONIST) ? Math.max(5, state().weaponLevel(weapon())) : state().weaponLevel(weapon());
    }

    double weaponEffectStrength() {
        return augmentSnapshot().has(PROTAGONIST) && state().weaponLevel(weapon()) >= 5
                ? parameter(PROTAGONIST, "effectMultiplier", 1.5) : 1.0;
    }

    private double weaponDamageMultiplier() {
        return augmentSnapshot().has(LEGEND) ? 1.0 + parameter(LEGEND, "weaponDamageBonus", 2.0) : 1.0;
    }

    private double parameter(String card, String key, double fallback) {
        return augmentSnapshot().parameter(card, key, fallback);
    }

    private void triggerAugments(SemionTowerEntity source, SemionMonsterEntity target) {
        int hit = ++augmentAttackCount;
        PlayerLane lane = attachedLane();
        if (lane == null) {
            return;
        }
        List<HeroCompanionTower> companions = lane.towers().stream()
                .filter(HeroCompanionTower.class::isInstance).map(HeroCompanionTower.class::cast)
                .filter(companion -> ownerPlayer().equals(companion.ownerPlayer())).toList();
        if (augmentSnapshot().has(JOINT_ATTACK) && hit % (int) parameter(JOINT_ATTACK, "everyAttacks", 3) == 0) {
            HeroCompanionTower companion = nextCompanion(companions, lastAttackingCompanion);
            if (companion != null) {
                lastAttackingCompanion = companion.logicalId();
                SemionTowerEntity entity = towerEntity(lane, companion);
                if (entity != null && entity.attackRange() > 0.0) {
                    List<SemionMonsterEntity> candidates = new ArrayList<>();
                    SemionTdApi.areaEffects().applyToMonsters(MonsterAreaEffectRequest.aroundTower(
                            ResourceLocation.fromNamespaceAndPath("semiontd", JOINT_ATTACK), entity,
                            entity.attackRange(), AreaVfxSpec.none()).withFilter(entity::isValidAttackTarget), monster -> {
                                candidates.add(monster);
                                return AreaEffectOutcome.UNCHANGED;
                            });
                    SemionMonsterEntity selected = companion.selectAttackTarget(entity, candidates).orElse(null);
                    AugmentCombat.additionalAttack(entity, selected, 1.0);
                }
            }
        }
        double heroDamage = currentAttackDamage(source);
        List<SemionTowerEntity> living = companions.stream().map(companion -> towerEntity(lane, companion))
                .filter(entity -> entity != null && entity.isAlive() && !entity.isRemoved()).toList();
        if (augmentSnapshot().has(PARTY_ATTACK) && living.size() >= (int) parameter(PARTY_ATTACK, "companions", 4)
                && hit % (int) parameter(PARTY_ATTACK, "everyAttacks", 5) == 0) {
            double total = heroDamage + living.stream().mapToDouble(entity -> entity.runtimeTower()
                    .resolveBasicAttackOutgoingDamage(entity, null, entity.attackDamageAmount(null))).sum();
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    ResourceLocation.fromNamespaceAndPath("semiontd", PARTY_ATTACK), source, target.position(),
                    parameter(PARTY_ATTACK, "radius", 4), Set.of(), null, AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH))
                    .nearestTargets((int) parameter(PARTY_ATTACK, "maxTargets", 12));
            AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.applyResolved(this, source, request,
                    ignored -> total * parameter(PARTY_ATTACK, "damageRatio", 3),
                    true, (ignored, dealt, killed) -> {}, DamageType.MAGIC));
        }
        if (augmentSnapshot().has(LEGEND) && hit % (int) parameter(LEGEND, "everyAttacks", 3) == 0) {
            double length = parameter(LEGEND, "length", 12);
            double width = parameter(LEGEND, "width", 3);
            Vec3 direction = target.position().subtract(source.position()).multiply(1, 0, 1).normalize();
            if (direction.lengthSqr() < 1.0E-9) {
                direction = source.getLookAngle().multiply(1, 0, 1).normalize();
            }
            Vec3 forward = direction;
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    ResourceLocation.fromNamespaceAndPath("semiontd", LEGEND), source, source.position(),
                    Math.hypot(length, width / 2), Set.of(),
                    monster -> insideSlash(source.position(), forward, monster.position(), length, width),
                    AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)).nearestTargets((int) parameter(LEGEND, "maxTargets", 12));
            AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.applyResolved(this, source, request,
                    ignored -> heroDamage * parameter(LEGEND, "damageRatio", 5),
                    true, (ignored, dealt, killed) -> {}, DamageType.PHYSICAL));
        }
    }

    private double currentAttackDamage(SemionTowerEntity source) {
        double base = (type().damage() + permanentFlatDamageBonus())
                * (1.0 + source.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS))
                + source.activeTimedEffectMagnitude(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS)
                - source.activeTimedEffectMagnitude(TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION);
        double weaponDamage = base * HeroPartyBalance.weaponDamage(weapon()) / Math.max(0.01, type().damage())
                * HeroPartyBalance.weaponMultiplier(state().weaponLevel(weapon())) * weaponDamageMultiplier();
        return resolveBasicAttackOutgoingDamage(source, null, weaponDamage);
    }

    static HeroCompanionTower nextCompanion(List<HeroCompanionTower> companions, UUID previous) {
        int start = 0;
        for (int i = 0; i < companions.size(); i++) {
            if (companions.get(i).logicalId().equals(previous)) {
                start = i + 1;
                break;
            }
        }
        for (int i = 0; i < companions.size(); i++) {
            HeroCompanionTower companion = companions.get((start + i) % companions.size());
            if (companion.health() > 0.0) {
                return companion;
            }
        }
        return null;
    }

    static boolean insideSlash(Vec3 origin, Vec3 forward, Vec3 target, double length, double width) {
        Vec3 offset = target.subtract(origin);
        double along = offset.x * forward.x + offset.z * forward.z;
        double sideways = Math.abs(offset.x * forward.z - offset.z * forward.x);
        return along >= 0.0 && along <= length && sideways <= width / 2.0;
    }

    private static double healthRatio(SemionMonsterEntity target) {
        if (target == null || target.runtimeMonster() == null) {
            return 1.0;
        }
        return target.runtimeMonster().health() / Math.max(1.0, target.runtimeMonster().maxHealth());
    }
}
