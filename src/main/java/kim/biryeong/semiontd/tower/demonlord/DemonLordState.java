package kim.biryeong.semiontd.tower.demonlord;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.tower.LogarithmicScaling;
import kim.biryeong.semiontd.tower.TowerRoundMetricsTracker;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the demon lord player carries between ticks: the boss-bar health pool, the kill-fed
 * level, the active barrier and the per-skill cooldowns.
 *
 * <p>Health lives here rather than on the vanilla player. The match already blocks vanilla damage
 * to participants ({@code SemionPlayerProtectionService}), and routing demon lord damage into a
 * separate pool keeps that guarantee intact - the player never actually dies, respawns or drops
 * items; they simply fall out of combat when the pool empties.
 */
public final class DemonLordState {
    private final UUID playerId;
    private final Map<DemonLordSkill, Long> cooldownReadyTick = new EnumMap<>(DemonLordSkill.class);
    private final Map<DemonLordStat, Integer> statPoints = new EnumMap<>(DemonLordStat.class);
    private int unspentPoints;

    private int level = 1;
    private double experience;
    private double health;
    private double shield;
    private long shieldExpiryTick;
    private boolean inCombat;
    private boolean centralDefense;
    private boolean pendingSpawn;
    private boolean combatKitGranted;
    private boolean loadoutDirty = true;
    private int lastSelectedSlot = -1;
    private int laneId = -1;
    private TeamId teamId;
    private long lastBladeAttackTick = Long.MIN_VALUE;
    private TowerType pendingBombardment;
    private long pendingBombardmentTick;
    private HellfireZone zone;
    private RiftCleave rift;
    private DemonLordFiend fiend;
    private AbyssVortex vortex;
    private double roundPhysicalDamageDealt;
    private double roundMagicDamageDealt;
    private TowerRoundMetricsTracker roundMetricsTracker;
    private int roundMetricsTick;
    private final DemonLordAugments augments = new DemonLordAugments();
    private AugmentSnapshot augmentSnapshot = AugmentSnapshot.none();
    private double fortitudeBonus;

    public void syncTraits(kim.biryeong.semiontd.trait.TraitLoadout loadout) {
        fortitudeBonus = kim.biryeong.semiontd.trait.TraitEffects.hyperCarryMaxHealthBonus(loadout);
        health = Math.min(health, maxHealth());
    }
    private DemonLordLoadout loadout = new DemonLordLoadout();
    /** 파멸의 계약을 건 뒤 시작된 웨이브 수. 0이면 아직 계약이 시작되지 않았습니다. */
    private int pactRoundsServed;
    /** 날아가는 중인 검기. */
    private final java.util.List<DemonLordPassives.BladeWave> bladeWaves = new java.util.ArrayList<>();
    /** Q(버리기)를 누르면 클라이언트가 팔도 휘두릅니다. 그 휘두름으로 검기가 나가지 않게 이 틱까지 무시합니다. */
    private long ignoreSwingUntil = Long.MIN_VALUE;
    /** 계약이 끝났다는 알림을 다음 서비스 틱에 한 번 띄웁니다. 라운드 종료 처리에는 플레이어 핸들이 없습니다. */
    private boolean pactEndedNotice;
    private boolean autoIncomeEnabled = true;
    /** 플레이어가 막대로 고른 값. 고르지 않았으면 {@code null}이고 설정 기본값을 씁니다. */
    private Double autoIncomeThreshold;

    public void syncAugments(AugmentSnapshot snapshot) {
        if (augmentSnapshot == snapshot) return;
        double ratio = healthRatio();
        augmentSnapshot = snapshot == null ? AugmentSnapshot.none() : snapshot;
        health = maxHealth() * ratio;
    }

    public void settleTargetedAugments(int round) {
        double ratio = healthRatio();
        augments.settleTargeted(augmentSnapshot, round, inCombat && health > 0);
        health = maxHealth() * ratio;
    }

    public String targetedAugmentDetails(String id) {
        var progress = augments.targetedProgress();
        return switch (id) {
            case "overheat_core" -> "열화 " + progress.heat() + "/" + (int) augmentSnapshot.parameter(id, "maxStacks", 5);
            case "battlefield_mastery" -> "숙련 " + progress.mastery() + "/" + (int) augmentSnapshot.parameter(id, "maxStacks", 4)
                    + " · 조건 피해 " + Math.round(progress.enemyDamage()) + "/"
                    + Math.round(progress.openingHealth() * augmentSnapshot.parameter(id, "damageThreshold", .40));
            default -> "";
        };
    }

    DemonLordAugments augments() {
        return augments;
    }

    public DemonLordState(UUID playerId) {
        this.playerId = playerId;
        this.health = maxHealth();
    }

    public UUID playerId() {
        return playerId;
    }

    // ---------------------------------------------------------------- health

    public double health() {
        return health;
    }

    public double maxHealth() {
        double base = global("baseMaxHealth", 450.0);
        double perLevel = global("maxHealthPerLevel", 52.5);
        double allocated = points(DemonLordStat.MAX_HEALTH) * global("statHealthPerPoint", 40.0);
        double scaledLevelBonus = LogarithmicScaling.logarithmicBonus(
                perLevel * (level - 1),
                global("healthBonusThreshold", 500.0),
                global("healthBonusScale", 500.0)
        );
        return Math.max(1.0, (base + scaledLevelBonus + allocated) * (1.0 + augments.maxHealthBonus(augmentSnapshot) + fortitudeBonus)
                * pact("healthMultiplier", 2.5));
    }

    // ------------------------------------------------------------------ 스탯

    public int points(DemonLordStat stat) {
        return stat == null ? 0 : Math.max(0, statPoints.getOrDefault(stat, 0));
    }

    public int unspentPoints() {
        return Math.max(0, unspentPoints);
    }

    /**
     * Spends one point. Returns false when there is nothing left to spend.
     *
     * <p>Deliberately one at a time: allocation is permanent for the match, and a misclick that
     * dumped ten points at once would be unrecoverable.
     */
    public boolean allocate(DemonLordStat stat) {
        if (stat == null || unspentPoints <= 0) {
            return false;
        }
        double previousMaxHealth = stat == DemonLordStat.MAX_HEALTH && inCombat ? maxHealth() : 0.0;
        unspentPoints--;
        statPoints.merge(stat, 1, Integer::sum);
        // 체력에 찍으면 늘어난 만큼 즉시 채워, 전투 중 투자해도 바로 값을 합니다.
        if (stat == DemonLordStat.MAX_HEALTH && inCombat) {
            health += Math.max(0.0, maxHealth() - previousMaxHealth);
        }
        return true;
    }

    /** 받는 피해 감소율. 포인트를 쌓아도 100%에는 닿지 않습니다. */
    public double damageReduction() {
        double perPoint = global("statDefensePerPoint", 0.02);
        double cap = Math.min(0.9, Math.max(0.0, global("statDefenseCap", 0.6)));
        double reduction = Math.max(0.0, Math.min(cap, points(DemonLordStat.DEFENSE) * perPoint));
        if (loadout.hasPassive(DemonLordPassive.DREAD)) {
            reduction = Math.min(0.9, reduction + DemonLordPassive.DREAD.ability("defenseBonus", 0.1));
        }
        return pactActive() ? Math.min(0.9, reduction + DemonLordPassive.DOOM_PACT.ability("defenseBonus", 0.3)) : reduction;
    }

    /**
     * 쿨타임 배율. 지정한 포인트마다 절반이 되는 지수 감쇠라 0 에는 닿지 않습니다.
     *
     * <p>기본값에서 10 포인트면 절반, 20 포인트면 4분의 1 입니다. 선형으로 깎으면 어느 지점에서
     * 쿨타임이 사라져 스킬을 무한히 쓰게 되므로 곱연산으로 접근시킵니다.
     */
    public double cooldownMultiplier() {
        double halving = Math.max(1.0, global("statCooldownHalvingPoints", 10.0));
        return Math.pow(0.5, points(DemonLordStat.COOLDOWN) / halving) * pact("cooldownMultiplier", 0.6);
    }

    /** 스킬 사거리·반경 배율입니다. */
    public double skillRangeMultiplier() {
        return (1.0 + points(DemonLordStat.SKILL_RANGE) * global("statSkillRangePerPoint", 0.03)) * pact("rangeMultiplier", 1.3);
    }

    /** 이동 속도 증가율입니다. */
    public double moveSpeedBonus() {
        double perPoint = global("statMoveSpeedPerPoint", 0.03);
        double cap = Math.max(0.0, global("statMoveSpeedCap", 0.5));
        double bonus = Math.max(0.0, Math.min(cap, points(DemonLordStat.MOVE_SPEED) * perPoint));
        return pactActive() ? bonus + DemonLordPassive.DOOM_PACT.ability("moveSpeedBonus", 0.25) : bonus;
    }

    public double shield() {
        return shield;
    }

    public double healthRatio() {
        double max = maxHealth();
        return max <= 0.0 ? 0.0 : Math.min(1.0, health / max);
    }

    /**
     * Applies incoming damage to the barrier first, then to the health pool.
     *
     * @return {@code true} when this hit emptied the pool and the player drops out of combat
     */
    public boolean applyDamage(double amount) {
        return applyDamage(amount, augmentSnapshot, 0L);
    }

    boolean applyDamage(double amount, AugmentSnapshot snapshot, long gameTime) {
        return applyDamage(amount, snapshot, gameTime, false);
    }

    boolean applyDamage(double amount, AugmentSnapshot snapshot, long gameTime, boolean enemyDamage) {
        if (amount <= 0.0 || !inCombat) {
            return false;
        }
        double remaining = amount * (1.0 - damageReduction()) * (1.0 - augments.damageReduction(snapshot));
        if (shield > 0.0) {
            double absorbed = Math.min(shield, remaining);
            shield -= absorbed;
            remaining -= absorbed;
        }
        if (remaining <= 0.0) {
            return false;
        }
        double before = health;
        health = Math.max(0.0, health - remaining);
        if (enemyDamage) augments.recordEnemyDamage(before - health);
        if (roundMetricsTracker != null) {
            roundMetricsTracker.recordDamageTaken(before - health);
            roundMetricsTracker.updateAlive(health > 0.0);
        }
        if (augments.activatePhase(this, snapshot, gameTime) && roundMetricsTracker != null) {
            roundMetricsTracker.updateAlive(health > 0.0);
        }
        return health <= 0.0;
    }

    public void heal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        double before = health;
        health = Math.min(maxHealth(), health + amount);
        if (roundMetricsTracker != null) {
            roundMetricsTracker.recordHealingDone(health - before);
        }
    }

    /** Barriers do not stack; recasting refreshes to the larger of the two shields. */
    public void grantShield(double amount, long expiryTick) {
        if (amount >= shield) {
            shield = amount;
            shieldExpiryTick = expiryTick;
        }
    }

    /** Drops an expired barrier. Called once per tick before damage is applied. */
    public void expireShieldIfNeeded(long gameTime) {
        if (shield > 0.0 && gameTime >= shieldExpiryTick) {
            shield = 0.0;
        }
    }

    public void clearShield() {
        shield = 0.0;
        shieldExpiryTick = 0L;
    }

    // ----------------------------------------------------------------- level

    public int level() {
        return level;
    }

    public double experience() {
        return experience;
    }

    /**
     * Restores kill-fed progress onto a freshly built state.
     *
     * <p>The state object is torn down and rebuilt whenever the player leaves and re-enters the
     * builder's tick path, and a rebuilt state starts at level 1. Progression is the only thing the
     * demon lord grows, so losing it mid-match wipes the whole run - {@link DemonLordStates} hands
     * it back through here.
     */
    void restoreProgression(
            int restoredLevel,
            double restoredExperience,
            Map<DemonLordStat, Integer> restoredPoints,
            int restoredUnspent
    ) {
        level = Math.max(1, Math.min(maxLevel(), restoredLevel));
        experience = Math.max(0.0, restoredExperience);
        statPoints.clear();
        if (restoredPoints != null) {
            restoredPoints.forEach((stat, spent) -> {
                if (stat != null && spent != null && spent > 0) {
                    statPoints.put(stat, spent);
                }
            });
        }
        unspentPoints = Math.max(0, restoredUnspent);
        health = Math.min(health, maxHealth());
    }

    /** 찍어 둔 스탯의 사본. 상태가 버려졌다 되살아날 때 넘겨받기 위한 것입니다. */
    Map<DemonLordStat, Integer> statPointsView() {
        return new EnumMap<>(statPoints);
    }

    public int maxLevel() {
        return Math.max(1, (int) global("maxLevel", 30.0));
    }

    /** Experience needed to move from {@code level} to the next one. */
    public double experienceForNextLevel() {
        double base = global("experienceBase", 12.0);
        double growth = Math.max(1.0, global("experienceGrowth", 1.25));
        return base * Math.pow(growth, level - 1);
    }

    /**
     * Feeds a kill into the level curve.
     *
     * <p>Levelling raises the health ceiling, and the gained headroom is granted immediately so a
     * level-up in the middle of a fight actually helps instead of only mattering next round.
     *
     * @return the number of levels gained
     */
    public int addExperience(double amount) {
        if (amount <= 0.0 || level >= maxLevel()) {
            return 0;
        }
        experience += amount;
        int gained = 0;
        while (level < maxLevel() && experience >= experienceForNextLevel()) {
            experience -= experienceForNextLevel();
            double previousMax = maxHealth();
            level++;
            gained++;
            unspentPoints += Math.max(0, (int) global("statPointsPerLevel", 1.0));
            health += Math.max(0.0, maxHealth() - previousMax);
        }
        if (level >= maxLevel()) {
            experience = 0.0;
        }
        return gained;
    }

    /** Scales every skill and blade hit. Levels grow it, and 공격력 points grow it further. */
    public double damageMultiplier() {
        double scaledLevelBonus = LogarithmicScaling.logarithmicBonus(
                global("damagePerLevel", 0.05) * (level - 1),
                global("damageBonusThreshold", 0.5),
                global("damageBonusScale", 0.5)
        );
        double allocated = points(DemonLordStat.ATTACK) * global("statAttackPerPoint", 0.04);
        double dread = loadout.hasPassive(DemonLordPassive.DREAD) ? DemonLordPassive.DREAD.ability("damageBonus", 0.1) : 0.0;
        return (1.0 + scaledLevelBonus + allocated + dread) * pact("damageMultiplier", 2.5);
    }

    // ------------------------------------------------------------ 파멸의 계약

    /** 파멸의 계약이 걸려 있는지. 장착한 순간부터 능력치가 오릅니다. */
    public boolean pactActive() {
        return loadout != null && loadout.hasPassive(DemonLordPassive.DOOM_PACT);
    }

    private double pact(String key, double fallback) {
        return pactActive() ? Math.max(0.0, DemonLordPassive.DOOM_PACT.ability(key, fallback)) : 1.0;
    }

    public int pactRoundsServed() {
        return pactRoundsServed;
    }

    public int pactRounds() {
        return Math.max(1, (int) DemonLordPassive.DOOM_PACT.ability("rounds", 5.0));
    }

    /** 웨이브가 시작될 때: 계약 중이면 한 라운드를 셉니다. */
    void countPactRound() {
        if (pactActive()) {
            pactRoundsServed++;
        }
    }

    /** 계약을 새로 걸거나 시작 전에 뺐을 때 셈을 지웁니다. */
    void resetPactCount() {
        pactRoundsServed = 0;
    }

    void restorePact(int served) {
        pactRoundsServed = Math.max(0, served);
    }

    /**
     * 라운드가 끝날 때: 계약한 라운드를 다 채웠으면 대가를 치릅니다. 레벨이 1로, 경험치·찍은 스탯·남은 포인트가
     * 0으로 돌아가고 계약 패시브도 사라집니다(환불 없음).
     *
     * @return 이번에 계약이 끝났으면 {@code true}
     */
    public boolean settlePact() {
        if (!pactActive() || pactRoundsServed < pactRounds()) {
            return false;
        }
        loadout.passivesView().forEach((slot, entry) -> {
            if (entry.passive() == DemonLordPassive.DOOM_PACT) {
                loadout.removePassive(slot);
            }
        });
        pactRoundsServed = 0;
        level = 1;
        experience = 0.0;
        statPoints.clear();
        unspentPoints = 0;
        health = Math.min(health, maxHealth());
        loadoutDirty = true;
        pactEndedNotice = true;
        return true;
    }

    public boolean consumePactEndedNotice() {
        boolean notice = pactEndedNotice;
        pactEndedNotice = false;
        return notice;
    }

    // ------------------------------------------------------------ 검기

    public java.util.List<DemonLordPassives.BladeWave> bladeWaves() {
        return bladeWaves;
    }

    public void ignoreSwingUntil(long gameTime) {
        ignoreSwingUntil = gameTime;
    }

    public boolean swingIgnored(long gameTime) {
        return gameTime <= ignoreSwingUntil;
    }

    public double bladeDamage() {
        return global("bladeDamage", 19.0) * damageMultiplier();
    }

    public void recordDamageDealt(double amount, DamageType damageType) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        if (damageType == DamageType.MAGIC) {
            roundMagicDamageDealt += amount;
        } else {
            roundPhysicalDamageDealt += amount;
        }
        if (roundMetricsTracker != null) {
            roundMetricsTracker.recordDamageDealt(amount, damageType);
        }
    }

    public void recordKill() {
        if (roundMetricsTracker != null) {
            roundMetricsTracker.recordKill();
        }
    }

    public void tickRoundMetrics() {
        if (roundMetricsTracker == null) {
            return;
        }
        roundMetricsTracker.setCurrentTick(roundMetricsTick++);
        roundMetricsTracker.recordSurvivalTick(inCombat && health > 0.0);
    }

    public TowerRoundMetricsTracker roundMetricsTracker() {
        return roundMetricsTracker;
    }

    public TowerRoundMetricsSnapshot roundMetrics() {
        return roundMetricsTracker == null ? null : roundMetricsTracker.snapshot();
    }

    public void removeRoundMetrics() {
        if (roundMetricsTracker != null) {
            roundMetricsTracker.markRemoved();
        }
    }

    public double roundPhysicalDamageDealt() {
        return roundPhysicalDamageDealt;
    }

    public double roundMagicDamageDealt() {
        return roundMagicDamageDealt;
    }

    /**
     * Vanilla-style swing charge, so mashing the button is worse than timing swings.
     *
     * <p>Returns the fraction of the attack interval that has elapsed since the last swing, clamped
     * to 1. Callers turn it into a damage multiplier; a fully charged swing is 1.0.
     */
    public double bladeChargeScale(long gameTime, int intervalTicks) {
        if (intervalTicks <= 0 || lastBladeAttackTick == Long.MIN_VALUE) {
            return 1.0;
        }
        double elapsed = gameTime - lastBladeAttackTick;
        double interval = Math.max(1, Math.ceil(intervalTicks / (1.0
                + kim.biryeong.semiontd.augment.AugmentCombat.beneficialBonus(augmentSnapshot, "attackSpeedBonus"))));
        return Math.max(0.0, Math.min(1.0, elapsed / interval));
    }

    /** Every swing resets the charge, including the weak ones. */
    public void recordBladeAttack(long gameTime) {
        lastBladeAttackTick = gameTime;
    }

    // ---------------------------------------------------- delayed 마도 폭격

    /**
     * 마도 폭격은 먼저 솟아오른 뒤 정점에서 쏩니다. 시전 시점이 아니라 발사 시점의 시선으로
     * 조준해야 하므로, 발사를 예약해 두고 서비스 틱이 처리합니다.
     */
    public void queueBombardment(TowerType altar, long fireTick) {
        pendingBombardment = altar;
        pendingBombardmentTick = fireTick;
    }

    public boolean bombardmentReady(long gameTime) {
        return pendingBombardment != null && gameTime >= pendingBombardmentTick;
    }

    public TowerType consumeBombardment() {
        TowerType altar = pendingBombardment;
        pendingBombardment = null;
        return altar;
    }

    public void clearPendingSkills() {
        bladeWaves.clear();
        pendingBombardment = null;
        zone = null;
        rift = null;
        vortex = null;
    }

    // ------------------------------------------------------------ 심연 소용돌이

    /**
     * 심연 소용돌이. 지정한 지점에 머물며 범위 안의 적을 매 틱 중심으로 끌어당기고, 일정 간격으로 피해를 줍니다.
     * 한 번에 하나만 두며 다시 시전하면 이전 소용돌이를 대체합니다.
     */
    public record AbyssVortex(
            TowerType altarType,
            Vec3 centre,
            double radius,
            double pullStrength,
            int damageIntervalTicks,
            long expiryTick,
            long nextDamageTick
    ) {
        public AbyssVortex afterDamage() {
            return new AbyssVortex(altarType, centre, radius, pullStrength, damageIntervalTicks, expiryTick,
                    nextDamageTick + damageIntervalTicks);
        }
    }

    public void openVortex(AbyssVortex opened) {
        vortex = opened;
    }

    public AbyssVortex vortex() {
        return vortex;
    }

    public void closeVortex() {
        vortex = null;
    }

    // ------------------------------------------------------------ 균열참 파동

    /**
     * 균열참이 앞으로 밀어 보내는 폭발 파동.
     *
     * <p>시전 때 벽까지 몇 번 터질 수 있는지({@code count}) 미리 정해 두고, {@code intervalTicks}마다 한 칸씩
     * 전진하며 터뜨립니다. 연출도 같은 횟수와 간격으로 미리 짜 두므로 피해와 그림이 어긋나지 않습니다.
     * 한 번에 하나만 유지하며 다시 시전하면 이전 파동을 대체합니다.
     *
     * @param nextIndex 다음에 터질 파동 번호(1부터)
     */
    public record RiftCleave(
            TowerType altarType,
            Vec3 origin,
            Vec3 direction,
            double firstOffset,
            double spacing,
            double radius,
            int count,
            int intervalTicks,
            int nextIndex,
            long nextTick
    ) {
        public Vec3 centre(int index) {
            return origin.add(direction.scale(firstOffset + spacing * index));
        }

        public RiftCleave advanced() {
            return new RiftCleave(altarType, origin, direction, firstOffset, spacing, radius, count, intervalTicks,
                    nextIndex + 1, nextTick + intervalTicks);
        }
    }

    // ------------------------------------------------------------ 마수 소환

    /** 지금 레인에 나와 있는 마수. 한 번에 하나만 두고, 다시 부르면 이전 마수는 돌려보냅니다. */
    public DemonLordFiend fiend() {
        return fiend;
    }

    public void summonFiend(DemonLordFiend summoned) {
        fiend = summoned;
    }

    public void clearFiend() {
        fiend = null;
    }

    public void queueRift(RiftCleave cleave) {
        rift = cleave;
    }

    public RiftCleave rift() {
        return rift;
    }

    public void clearRift() {
        rift = null;
    }

    // -------------------------------------------------- 지옥불 낙인 장판

    /**
     * 지옥불 낙인이 남긴 장판.
     *
     * <p>한 번에 하나만 유지합니다. 다시 시전하면 새 장판이 이전 것을 대체하므로, 여러 장을 겹쳐
     * 깔아 피해를 중첩시킬 수 없습니다.
     */
    public record HellfireZone(
            TowerType altarType,
            Vec3 centre,
            double radius,
            double damage,
            double damageTakenBonus,
            int tickIntervalTicks,
            long expiryTick,
            long nextPulseTick
    ) {
    }

    public void placeZone(HellfireZone newZone) {
        zone = newZone;
    }

    public HellfireZone zone() {
        return zone;
    }

    public void clearZone() {
        zone = null;
    }

    // ---------------------------------------------------------------- combat

    public boolean inCombat() {
        return inCombat;
    }

    public boolean centralDefense() {
        return centralDefense;
    }

    public void enterCentralDefense() {
        centralDefense = true;
    }

    /** Before clearing, fight only this lane; afterwards, fight only final-defense monsters. */
    public boolean canFight(Monster monster) {
        if (monster == null) {
            return false;
        }
        if (centralDefense) {
            return monster.inFinalDefenseCombat() && !monster.isRemoved() && monster.health() > 0.0;
        }
        return monster.isAlive() && monster.targetLaneId() == laneId;
    }

    /**
     * Called at wave start: full health, no barrier, every cooldown cleared.
     *
     * <p>Wave start, not round start — the prepare phase has to stay non-combat so the shop hotbar
     * survives, and this doubles as the revive point for anyone knocked out of the last wave.
     *
     * <p>The actual teleport to lane centre is deferred to {@code pendingSpawn}, because jobs run
     * without a {@code ServerPlayer} handle and the service tick has one.
     */
    public void enterCombat() {
        augments.reset();
        inCombat = true;
        centralDefense = false;
        pendingSpawn = true;
        health = maxHealth();
        clearShield();
        clearPendingSkills();
        cooldownReadyTick.clear();
        roundPhysicalDamageDealt = 0.0;
        roundMagicDamageDealt = 0.0;
        roundMetricsTracker = new TowerRoundMetricsTracker("semion-td:demon_lord", true);
        roundMetricsTick = 0;
        loadoutDirty = true;
    }

    public boolean consumePendingSpawn() {
        boolean pending = pendingSpawn;
        pendingSpawn = false;
        return pending;
    }

    /** Called when the pool empties. Skills stop working and monsters stop caring. */
    public void leaveCombat() {
        augments.reset();
        inCombat = false;
        centralDefense = false;
        health = 0.0;
        if (roundMetricsTracker != null) {
            roundMetricsTracker.updateAlive(false);
        }
        clearShield();
        clearPendingSkills();
        loadoutDirty = true;
    }

    /**
     * Called when the round ends with the demon lord still standing.
     *
     * <p>Combat has to end somewhere. Without this the flag survives the wave and the next prepare
     * phase inherits the skill hotbar, which leaves no way to reach the shop.
     *
     * <p>Not {@link #leaveCombat()}: the pool never emptied, so health stays where the wave left it
     * and the boss bar reads 대기 instead of 전투 제외. The next wave refills it anyway.
     */
    public void standDown() {
        augments.reset();
        if (!inCombat) {
            return;
        }
        inCombat = false;
        centralDefense = false;
        clearShield();
        clearPendingSkills();
        loadoutDirty = true;
    }

    // ------------------------------------------------------------- cooldowns

    public boolean isSkillReady(DemonLordSkill skill, long gameTime) {
        Long ready = cooldownReadyTick.get(skill);
        return ready == null || gameTime >= ready;
    }

    public void startCooldown(DemonLordSkill skill, long gameTime, int cooldownTicks) {
        cooldownReadyTick.put(skill, gameTime + Math.max(1, cooldownTicks));
    }

    void resetSkillCooldowns() {
        cooldownReadyTick.clear();
    }

    void reduceAttackSkillCooldowns(int ticks) {
        cooldownReadyTick.replaceAll((skill, ready) -> skill == DemonLordSkill.DEMON_BARRIER
                ? ready : ready - Math.max(0, ticks));
    }

    void refundCooldown(DemonLordSkill skill, int ticks) {
        cooldownReadyTick.computeIfPresent(skill, (ignored, ready) -> ready - Math.max(0, ticks));
    }

    public int remainingCooldownTicks(DemonLordSkill skill, long gameTime) {
        Long ready = cooldownReadyTick.get(skill);
        if (ready == null) {
            return 0;
        }
        return (int) Math.max(0L, ready - gameTime);
    }

    // ------------------------------------------------------------- 스킬 배정

    public DemonLordLoadout loadout() {
        return loadout;
    }

    void restoreLoadout(DemonLordLoadout restored) {
        loadout = restored == null ? new DemonLordLoadout() : restored;
        loadoutDirty = true;
    }

    // --------------------------------------------------------- 자동 인컴 전송

    public boolean autoIncomeEnabled() {
        return autoIncomeEnabled;
    }

    public void setAutoIncomeEnabled(boolean enabled) {
        autoIncomeEnabled = enabled;
    }

    /** 에메랄드가 한도의 이 비율 이상이면 자동으로 인컴을 보냅니다. */
    public double autoIncomeThreshold() {
        double value = autoIncomeThreshold != null ? autoIncomeThreshold
                : global("autoIncomeThreshold", DEFAULT_AUTO_INCOME_THRESHOLD);
        return Math.max(0.0, Math.min(1.0, value));
    }

    public void setAutoIncomeThreshold(double ratio) {
        autoIncomeThreshold = Math.max(0.0, Math.min(1.0, ratio));
    }

    Double autoIncomeThresholdOverride() {
        return autoIncomeThreshold;
    }

    void restoreAutoIncome(boolean enabled, Double threshold) {
        autoIncomeEnabled = enabled;
        autoIncomeThreshold = threshold;
    }

    public static final double DEFAULT_AUTO_INCOME_THRESHOLD = 0.7;

    // ---------------------------------------------------------------- hotbar

    public boolean loadoutDirty() {
        return loadoutDirty;
    }

    public void markLoadoutDirty() {
        loadoutDirty = true;
    }

    public void clearLoadoutDirty() {
        loadoutDirty = false;
    }

    /** Lane the demon lord defends. Kept here so monster goals can match without a game lookup. */
    public int laneId() {
        return laneId;
    }

    public void setLaneId(int laneId) {
        this.laneId = laneId;
    }

    public void setTeamId(TeamId teamId) {
        this.teamId = teamId;
    }

    /** True while the hotbar is holding the combat kit instead of the normal match tools. */
    public boolean combatKitGranted() {
        return combatKitGranted;
    }

    public void setCombatKitGranted(boolean granted) {
        combatKitGranted = granted;
    }

    public int lastSelectedSlot() {
        return lastSelectedSlot;
    }

    public void setLastSelectedSlot(int slot) {
        lastSelectedSlot = slot;
    }

    private double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(DemonLordTowers.GLOBAL_CONFIG_ID, key, fallback);
    }
}
