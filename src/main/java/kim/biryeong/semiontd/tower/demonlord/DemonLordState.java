package kim.biryeong.semiontd.tower.demonlord;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.LogarithmicScaling;
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
    private long lastBladeAttackTick = Long.MIN_VALUE;
    private TowerType pendingBombardment;
    private long pendingBombardmentTick;
    private HellfireZone zone;
    private double roundPhysicalDamageDealt;
    private double roundMagicDamageDealt;

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
        double rawBonus = perLevel * (level - 1) + allocated;
        double scaledBonus = LogarithmicScaling.logarithmicBonus(
                rawBonus,
                global("healthBonusThreshold", 500.0),
                global("healthBonusScale", 500.0)
        );
        return Math.max(1.0, base + scaledBonus);
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
        return Math.max(0.0, Math.min(cap, points(DemonLordStat.DEFENSE) * perPoint));
    }

    /**
     * 쿨타임 배율. 지정한 포인트마다 절반이 되는 지수 감쇠라 0 에는 닿지 않습니다.
     *
     * <p>기본값에서 10 포인트면 절반, 20 포인트면 4분의 1 입니다. 선형으로 깎으면 어느 지점에서
     * 쿨타임이 사라져 스킬을 무한히 쓰게 되므로 곱연산으로 접근시킵니다.
     */
    public double cooldownMultiplier() {
        double halving = Math.max(1.0, global("statCooldownHalvingPoints", 10.0));
        return Math.pow(0.5, points(DemonLordStat.COOLDOWN) / halving);
    }

    /** 스킬 사거리·반경 배율입니다. */
    public double skillRangeMultiplier() {
        return 1.0 + points(DemonLordStat.SKILL_RANGE) * global("statSkillRangePerPoint", 0.03);
    }

    /** 이동 속도 증가율입니다. */
    public double moveSpeedBonus() {
        double perPoint = global("statMoveSpeedPerPoint", 0.03);
        double cap = Math.max(0.0, global("statMoveSpeedCap", 0.5));
        return Math.max(0.0, Math.min(cap, points(DemonLordStat.MOVE_SPEED) * perPoint));
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
        if (amount <= 0.0 || !inCombat) {
            return false;
        }
        double remaining = amount * (1.0 - damageReduction());
        if (shield > 0.0) {
            double absorbed = Math.min(shield, remaining);
            shield -= absorbed;
            remaining -= absorbed;
        }
        if (remaining <= 0.0) {
            return false;
        }
        health = Math.max(0.0, health - remaining);
        return health <= 0.0;
    }

    public void heal(double amount) {
        if (amount <= 0.0) {
            return;
        }
        health = Math.min(maxHealth(), health + amount);
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
        double rawBonus = global("damagePerLevel", 0.05) * (level - 1)
                + points(DemonLordStat.ATTACK) * global("statAttackPerPoint", 0.04);
        return 1.0 + LogarithmicScaling.logarithmicBonus(
                rawBonus,
                global("damageBonusThreshold", 0.5),
                global("damageBonusScale", 0.5)
        );
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
        return Math.max(0.0, Math.min(1.0, elapsed / intervalTicks));
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
        pendingBombardment = null;
        zone = null;
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
        if (monster == null || !monster.isAlive()) {
            return false;
        }
        return centralDefense
                ? monster.inFinalDefenseCombat()
                : monster.targetLaneId() == laneId;
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
        inCombat = true;
        centralDefense = false;
        pendingSpawn = true;
        health = maxHealth();
        clearShield();
        clearPendingSkills();
        cooldownReadyTick.clear();
        roundPhysicalDamageDealt = 0.0;
        roundMagicDamageDealt = 0.0;
        loadoutDirty = true;
    }

    public boolean consumePendingSpawn() {
        boolean pending = pendingSpawn;
        pendingSpawn = false;
        return pending;
    }

    /** Called when the pool empties. Skills stop working and monsters stop caring. */
    public void leaveCombat() {
        inCombat = false;
        centralDefense = false;
        health = 0.0;
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

    public int remainingCooldownTicks(DemonLordSkill skill, long gameTime) {
        Long ready = cooldownReadyTick.get(skill);
        if (ready == null) {
            return 0;
        }
        return (int) Math.max(0L, ready - gameTime);
    }

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
