package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

public final class HeroPartyState {
    private final UUID playerId;
    private final EnumSet<HeroCompanionRole> committedCompanions = EnumSet.noneOf(HeroCompanionRole.class);
    private final EnumSet<HeroWeapon> ownedWeapons = EnumSet.of(HeroWeapon.SWORD);
    private final EnumMap<HeroWeapon, Integer> weaponLevels = new EnumMap<>(HeroWeapon.class);
    private HeroWeapon equippedWeapon = HeroWeapon.SWORD;
    private int armorLevel;
    private boolean armorVisible = true;
    private int adventurePoints;
    private HeroQuest currentQuest;
    private HeroQuestKind previousQuestKind;
    private boolean partyDeathThisWave;
    private ServerBossEvent questBossBar;

    HeroPartyState(UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        for (HeroWeapon weapon : HeroWeapon.values()) {
            weaponLevels.put(weapon, 0);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public Set<HeroCompanionRole> committedCompanions() {
        return Set.copyOf(committedCompanions);
    }

    public boolean isCommitted(HeroCompanionRole role) {
        return role != null && committedCompanions.contains(role);
    }

    public boolean canCommit(HeroCompanionRole role) {
        return role != null && (isCommitted(role) || committedCompanions.size() < HeroPartyBalance.MAX_COMPANIONS);
    }

    public boolean commit(HeroCompanionRole role) {
        return role != null && (committedCompanions.contains(role)
                || committedCompanions.size() < HeroPartyBalance.MAX_COMPANIONS && committedCompanions.add(role));
    }

    public Set<HeroWeapon> ownedWeapons() {
        return Set.copyOf(ownedWeapons);
    }

    public boolean owns(HeroWeapon weapon) {
        return weapon != null && ownedWeapons.contains(weapon);
    }

    boolean addWeapon(HeroWeapon weapon) {
        return weapon != null && ownedWeapons.add(weapon);
    }

    public HeroWeapon equippedWeapon() {
        return equippedWeapon;
    }

    boolean equip(HeroWeapon weapon) {
        if (!owns(weapon)) {
            return false;
        }
        equippedWeapon = weapon;
        return true;
    }

    public int weaponLevel(HeroWeapon weapon) {
        return weapon == null ? 0 : weaponLevels.getOrDefault(weapon, 0);
    }

    boolean upgradeWeapon(HeroWeapon weapon) {
        int current = weaponLevel(weapon);
        if (!owns(weapon) || current >= HeroPartyBalance.MAX_WEAPON_LEVEL) {
            return false;
        }
        weaponLevels.put(weapon, current + 1);
        return true;
    }

    public int armorLevel() {
        return armorLevel;
    }

    public boolean armorVisible() {
        return armorVisible;
    }

    boolean toggleArmorVisibility() {
        armorVisible = !armorVisible;
        return armorVisible;
    }

    boolean upgradeArmor() {
        if (armorLevel >= HeroPartyBalance.MAX_ARMOR_LEVEL) {
            return false;
        }
        armorLevel++;
        return true;
    }

    public int adventurePoints() {
        return adventurePoints;
    }

    public HeroQuestSnapshot quest() {
        return currentQuest == null ? null : currentQuest.snapshot();
    }

    void assignQuest(SemionGame game, int round, ServerPlayer player) {
        List<WaveMonsterEntry> entries = game == null ? List.of() : game.upcomingWaveEntries(playerId);
        int totalCount = entries.stream().mapToInt(WaveMonsterEntry::count).sum();
        double totalHealth = entries.stream().mapToDouble(entry -> entry.health() * entry.count()).sum();
        double partyHealth = game == null ? 0.0 : game.playerLane(playerId)
                .map(lane -> lane.towers().stream()
                        .filter(tower -> tower.ownerPlayer().equals(playerId) && HeroPartyTowers.isHeroPartyTower(tower.type()))
                        .mapToDouble(tower -> tower.currentMaxHealth())
                        .sum())
                .orElse(0.0);
        ArrayList<HeroQuestKind> candidates = new ArrayList<>(eligibleQuestKinds(game, entries));
        if (candidates.size() > 1 && previousQuestKind != null) {
            candidates.removeIf(kind -> kind == previousQuestKind);
        }
        HeroQuestKind selected = candidates.get(Math.floorMod(Objects.hash(playerId, round), candidates.size()));
        HeroWeapon requiredWeapon = requiredWeapon(selected, round);
        double target = questTarget(selected, round, totalCount, totalHealth, partyHealth);
        int reward = questReward(round);
        currentQuest = new HeroQuest(round, selected, requiredWeapon, Math.max(1.0, target), reward);
        previousQuestKind = selected;
        partyDeathThisWave = false;
        showBossBar(player);
    }

    void markHeroAtWaveStart(HeroWeapon weapon, ServerPlayer player) {
        if (currentQuest == null) {
            return;
        }
        currentQuest.heroPresentAtStart = true;
        currentQuest.lockedWeapon = weapon == null ? equippedWeapon : weapon;
        updateBossBar(player);
    }

    void recordWeaponAttack(HeroWeapon weapon, double dealtDamage, boolean killed, ServerPlayer player) {
        if (!canProgress(weapon)) {
            return;
        }
        switch (currentQuest.kind) {
            case WEAPON_DAMAGE, PARTY_DAMAGE -> addProgress(dealtDamage, player);
            case WEAPON_KILLS, HERO_KILLS -> {
                if (killed) {
                    addProgress(1.0, player);
                }
            }
            default -> {
            }
        }
    }

    void recordCompanionAttack(
            HeroCompanionRole role,
            double dealtDamage,
            boolean killed,
            boolean boss,
            ServerPlayer player
    ) {
        if (!canProgress(null)) {
            return;
        }
        switch (currentQuest.kind) {
            case PARTY_DAMAGE -> addProgress(dealtDamage, player);
            case COMPANION_KILLS -> {
                if (killed) {
                    addProgress(1.0, player);
                }
            }
            case ARCHER_BOSS_DAMAGE -> {
                if (role == HeroCompanionRole.ARCHER && boss) {
                    addProgress(dealtDamage, player);
                }
            }
            default -> {
            }
        }
    }

    void recordSpecial(HeroQuestKind kind, HeroWeapon weapon, double amount, ServerPlayer player) {
        if (currentQuest != null && currentQuest.kind == kind && canProgress(weapon)) {
            addProgress(amount, player);
        }
    }

    void recordHealing(HeroWeapon weapon, double amount, ServerPlayer player) {
        recordSpecial(HeroQuestKind.TOME_HEALING, weapon, amount, player);
    }

    void recordPartyDeath() {
        partyDeathThisWave = true;
    }

    int finishQuest(PlayerLane lane, ServerPlayer player) {
        if (currentQuest == null) {
            return 0;
        }
        boolean cleared = lane != null && lane.clearedThisRound();
        if (cleared) {
            boolean completed = switch (currentQuest.kind) {
                case WAVE_CLEAR -> true;
                case CLEAN_CLEAR -> !lane.leakedThisRound() && !lane.laneDefenseBroken() && !partyDeathThisWave;
                case PARTY_SURVIVAL -> !partyDeathThisWave;
                case HERO_SURVIVAL -> hasLivingHero(lane);
                default -> false;
            };
            if (completed) {
                currentQuest.progress = currentQuest.target;
            }
        }
        currentQuest.completed = currentQuest.heroPresentAtStart && currentQuest.progress + 1.0E-6 >= currentQuest.target;
        currentQuest.failed = !currentQuest.completed;
        int reward = currentQuest.completed ? currentQuest.reward : 0;
        adventurePoints += reward;
        updateBossBar(player);
        return reward;
    }

    void clearBossBar() {
        if (questBossBar != null) {
            questBossBar.removeAllPlayers();
            questBossBar = null;
        }
    }

    private boolean canProgress(HeroWeapon weapon) {
        return currentQuest != null
                && currentQuest.heroPresentAtStart
                && !currentQuest.completed
                && !currentQuest.failed
                && (currentQuest.requiredWeapon == null || currentQuest.requiredWeapon == weapon)
                && (currentQuest.requiredWeapon == null || currentQuest.lockedWeapon == currentQuest.requiredWeapon);
    }

    private void addProgress(double amount, ServerPlayer player) {
        if (!Double.isFinite(amount) || amount <= 0.0 || currentQuest == null) {
            return;
        }
        currentQuest.progress = Math.min(currentQuest.target, currentQuest.progress + amount);
        updateBossBar(player);
    }

    private HeroWeapon selectedOwnedWeapon(int round, int salt) {
        List<HeroWeapon> weapons = ownedWeapons.stream().sorted().toList();
        return weapons.get(Math.floorMod(Objects.hash(playerId, round, salt), weapons.size()));
    }

    List<HeroQuestKind> eligibleQuestKinds(SemionGame game, List<WaveMonsterEntry> entries) {
        int totalCount = entries == null ? 0 : entries.stream().mapToInt(WaveMonsterEntry::count).sum();
        ArrayList<HeroQuestKind> candidates = new ArrayList<>(List.of(
                HeroQuestKind.WAVE_CLEAR,
                HeroQuestKind.CLEAN_CLEAR,
                HeroQuestKind.HERO_KILLS,
                HeroQuestKind.PARTY_DAMAGE,
                HeroQuestKind.HERO_SURVIVAL,
                HeroQuestKind.PARTY_SURVIVAL,
                HeroQuestKind.WEAPON_DAMAGE
        ));
        if (totalCount >= 8) {
            candidates.add(HeroQuestKind.WEAPON_KILLS);
        }
        if (weaponLevel(HeroWeapon.GREATSWORD) >= 1) {
            candidates.add(HeroQuestKind.GREATSWORD_MULTI_HIT);
        }
        if (weaponLevel(HeroWeapon.LONGBOW) >= 3) {
            candidates.add(HeroQuestKind.LONGBOW_MARK_DAMAGE);
        }
        if (weaponLevel(HeroWeapon.STAFF) >= 1) {
            candidates.add(HeroQuestKind.STAFF_SPECIAL_HITS);
        }
        if (weaponLevel(HeroWeapon.SWORD) >= 3) {
            candidates.add(HeroQuestKind.SWORD_DAMAGE_PREVENTED);
        }
        if (weaponLevel(HeroWeapon.TOME) >= 1 && hasActiveCompanion(game)) {
            candidates.add(HeroQuestKind.TOME_HEALING);
        }
        if (hasActiveCompanion(game)) {
            candidates.add(HeroQuestKind.COMPANION_KILLS);
        }
        if (activeCompanionTier(game, HeroCompanionRole.KNIGHT) >= 2) {
            candidates.add(HeroQuestKind.KNIGHT_GUARD);
        }
        if (hasActiveCompanion(game, HeroCompanionRole.ARCHER)
                && entries != null
                && entries.stream().anyMatch(entry -> entry.id().toLowerCase(java.util.Locale.ROOT).contains("boss"))) {
            candidates.add(HeroQuestKind.ARCHER_BOSS_DAMAGE);
        }
        if (hasActiveCompanion(game, HeroCompanionRole.MAGE)) {
            candidates.add(HeroQuestKind.MAGE_SPLASH_HITS);
        }
        if (hasActiveCompanion(game, HeroCompanionRole.PRIEST)) {
            candidates.add(HeroQuestKind.PRIEST_HEALING);
        }
        if (hasActiveCompanion(game, HeroCompanionRole.ROGUE)) {
            candidates.add(HeroQuestKind.ROGUE_EXECUTE_HITS);
        }
        if (hasActiveCompanion(game, HeroCompanionRole.BARD)) {
            candidates.add(HeroQuestKind.BARD_AURA_SUPPORT);
        }
        return List.copyOf(candidates);
    }

    private HeroWeapon requiredWeapon(HeroQuestKind kind, int round) {
        if (kind == HeroQuestKind.WEAPON_KILLS) {
            return selectedOwnedWeapon(round, 1);
        }
        if (kind == HeroQuestKind.WEAPON_DAMAGE) {
            return selectedOwnedWeapon(round, 2);
        }
        return switch (kind) {
            case GREATSWORD_MULTI_HIT -> HeroWeapon.GREATSWORD;
            case LONGBOW_MARK_DAMAGE -> HeroWeapon.LONGBOW;
            case STAFF_SPECIAL_HITS -> HeroWeapon.STAFF;
            case SWORD_DAMAGE_PREVENTED -> HeroWeapon.SWORD;
            case TOME_HEALING -> HeroWeapon.TOME;
            default -> null;
        };
    }

    private boolean hasActiveCompanion(SemionGame game) {
        return game != null && game.playerLane(playerId)
                .map(lane -> lane.towers().stream().anyMatch(tower ->
                        tower.ownerPlayer().equals(playerId) && HeroPartyTowers.isCompanion(tower.type())))
                .orElse(false);
    }

    private boolean hasActiveCompanion(SemionGame game, HeroCompanionRole role) {
        return activeCompanionTier(game, role) > 0;
    }

    private int activeCompanionTier(SemionGame game, HeroCompanionRole role) {
        return game == null || role == null ? 0 : game.playerLane(playerId)
                .map(lane -> lane.towers().stream()
                        .filter(tower -> tower.ownerPlayer().equals(playerId))
                        .filter(tower -> HeroPartyTowers.role(tower.type()).filter(role::equals).isPresent())
                        .mapToInt(tower -> HeroPartyTowers.tier(tower.type()))
                        .max()
                        .orElse(0))
                .orElse(0);
    }

    private boolean hasLivingHero(PlayerLane lane) {
        return lane != null && lane.towers().stream().anyMatch(tower ->
                tower.ownerPlayer().equals(playerId)
                        && HeroPartyTowers.isHero(tower.type())
                        && tower.health() > 0.0);
    }

    static double questTarget(
            HeroQuestKind kind,
            int round,
            int totalCount,
            double totalHealth,
            double partyHealth
    ) {
        QuestDifficulty difficulty = QuestDifficulty.forRound(round);
        return switch (kind) {
            case WAVE_CLEAR, CLEAN_CLEAR, HERO_SURVIVAL, PARTY_SURVIVAL -> 1.0;
            case WEAPON_KILLS, HERO_KILLS ->
                    Math.ceil(Math.max(1, totalCount) * difficulty.heroKillRatio);
            case COMPANION_KILLS, GREATSWORD_MULTI_HIT, STAFF_SPECIAL_HITS,
                    MAGE_SPLASH_HITS, ROGUE_EXECUTE_HITS ->
                    Math.ceil(Math.max(1, totalCount) * difficulty.killRatio);
            case BARD_AURA_SUPPORT -> difficulty.bardSupportTarget;
            case WEAPON_DAMAGE, LONGBOW_MARK_DAMAGE ->
                    Math.max(1.0, totalHealth * difficulty.heroDamageRatio);
            case PARTY_DAMAGE, ARCHER_BOSS_DAMAGE ->
                    Math.max(1.0, totalHealth * difficulty.damageRatio);
            case TOME_HEALING, SWORD_DAMAGE_PREVENTED, KNIGHT_GUARD, PRIEST_HEALING ->
                    Math.max(1.0, partyHealth * difficulty.healingRatio);
        };
    }

    static int questReward(int round) {
        QuestDifficulty difficulty = QuestDifficulty.forRound(round);
        return difficulty.baseReward + Math.min(4, Math.max(0, round - 1) / 10);
    }

    private void showBossBar(ServerPlayer player) {
        if (player == null || currentQuest == null) {
            return;
        }
        if (questBossBar == null) {
            questBossBar = new ServerBossEvent(
                    questTitle(),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
        }
        questBossBar.addPlayer(player);
        updateBossBar(player);
    }

    private void updateBossBar(ServerPlayer player) {
        if (currentQuest == null) {
            return;
        }
        showBossBarIfMissing(player);
        if (questBossBar == null) {
            return;
        }
        questBossBar.setName(questTitle());
        questBossBar.setProgress((float) Math.max(0.0, Math.min(1.0, currentQuest.progress / currentQuest.target)));
    }

    private void showBossBarIfMissing(ServerPlayer player) {
        if (questBossBar == null && player != null) {
            questBossBar = new ServerBossEvent(
                    questTitle(),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            questBossBar.addPlayer(player);
        }
    }

    private Component questTitle() {
        if (currentQuest.completed) {
            return Component.literal("퀘스트 완료 - 모험 점수 +" + currentQuest.reward);
        }
        if (currentQuest.failed) {
            return Component.literal("퀘스트 실패 - " + currentQuest.label());
        }
        return Component.literal(currentQuest.label() + " " + format(currentQuest.progress) + "/" + format(currentQuest.target));
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private enum QuestDifficulty {
        C(5, 0.20, 0.15, 0.20, 0.15, 0.10, 10),
        B(10, 0.30, 0.22, 0.35, 0.20, 0.15, 20),
        A(15, 0.40, 0.30, 0.50, 0.25, 0.20, 35),
        S(20, 0.35, 0.28, 0.49, 0.21, 0.175, 35);

        private final int baseReward;
        private final double killRatio;
        private final double damageRatio;
        private final double healingRatio;
        private final double heroKillRatio;
        private final double heroDamageRatio;
        private final int bardSupportTarget;

        QuestDifficulty(
                int baseReward,
                double killRatio,
                double damageRatio,
                double healingRatio,
                double heroKillRatio,
                double heroDamageRatio,
                int bardSupportTarget
        ) {
            this.baseReward = baseReward;
            this.killRatio = killRatio;
            this.damageRatio = damageRatio;
            this.healingRatio = healingRatio;
            this.heroKillRatio = heroKillRatio;
            this.heroDamageRatio = heroDamageRatio;
            this.bardSupportTarget = bardSupportTarget;
        }

        private static QuestDifficulty forRound(int round) {
            if (round >= 20) {
                return S;
            }
            if (round >= 10) {
                return A;
            }
            if (round >= 5) {
                return B;
            }
            return C;
        }
    }

    private static final class HeroQuest {
        private final int round;
        private final HeroQuestKind kind;
        private final HeroWeapon requiredWeapon;
        private final double target;
        private final int reward;
        private double progress;
        private boolean heroPresentAtStart;
        private HeroWeapon lockedWeapon;
        private boolean completed;
        private boolean failed;

        private HeroQuest(int round, HeroQuestKind kind, HeroWeapon requiredWeapon, double target, int reward) {
            this.round = round;
            this.kind = kind;
            this.requiredWeapon = requiredWeapon;
            this.target = target;
            this.reward = reward;
        }

        private String label() {
            return requiredWeapon == null
                    ? kind.displayName()
                    : requiredWeapon.displayName() + " - " + kind.displayName();
        }

        private HeroQuestSnapshot snapshot() {
            return new HeroQuestSnapshot(
                    round,
                    kind,
                    requiredWeapon,
                    target,
                    progress,
                    reward,
                    heroPresentAtStart,
                    completed,
                    failed,
                    label()
            );
        }
    }

    public record HeroQuestSnapshot(
            int round,
            HeroQuestKind kind,
            HeroWeapon requiredWeapon,
            double target,
            double progress,
            int reward,
            boolean heroPresentAtStart,
            boolean completed,
            boolean failed,
            String label
    ) {
    }
}
