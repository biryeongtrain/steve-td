package kim.biryeong.semiontd.tower.hero;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.server.level.ServerPlayer;

public final class HeroPartyStates {
    private static final Map<UUID, HeroPartyState> STATES = new ConcurrentHashMap<>();

    private HeroPartyStates() {
    }

    public static HeroPartyState state(UUID playerId) {
        return STATES.computeIfAbsent(playerId, HeroPartyState::new);
    }

    public static Optional<HeroPartyState> find(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : STATES.get(playerId));
    }

    public static void clear(UUID playerId) {
        HeroPartyState removed = playerId == null ? null : STATES.remove(playerId);
        if (removed != null) {
            removed.clearBossBar();
        }
    }

    public static boolean hasActiveHero(SemionGame game, UUID playerId) {
        return game != null && game.playerLane(playerId)
                .map(lane -> lane.towers().stream().anyMatch(tower ->
                        tower.ownerPlayer().equals(playerId) && HeroPartyTowers.isHero(tower.type())))
                .orElse(false);
    }

    public static boolean hasActiveCompanion(SemionGame game, UUID playerId, HeroCompanionRole role) {
        return game != null && role != null && game.playerLane(playerId)
                .map(lane -> lane.towers().stream().anyMatch(tower ->
                        tower.ownerPlayer().equals(playerId)
                                && HeroPartyTowers.role(tower.type()).filter(role::equals).isPresent()))
                .orElse(false);
    }

    public static boolean commitCompanion(UUID playerId, HeroCompanionRole role) {
        return state(playerId).commit(role);
    }

    public static ActionResult purchaseWeapon(SemionGame game, UUID playerId, HeroWeapon weapon) {
        SemionPlayer player = eligibleShopPlayer(game, playerId);
        if (player == null) {
            return shopFailure(game, playerId);
        }
        HeroPartyState state = state(playerId);
        if (weapon == null) {
            return ActionResult.UNKNOWN_WEAPON;
        }
        if (state.owns(weapon)) {
            return ActionResult.ALREADY_OWNED;
        }
        long cost = HeroPartyBalance.weaponPurchaseCost(weapon);
        if (!player.economy().spendMineral(cost)) {
            return ActionResult.NOT_ENOUGH_DIAMOND;
        }
        state.addWeapon(weapon);
        refreshParty(game, playerId);
        return ActionResult.SUCCESS;
    }

    public static ActionResult upgradeWeapon(SemionGame game, UUID playerId, HeroWeapon weapon) {
        SemionPlayer player = eligibleShopPlayer(game, playerId);
        if (player == null) {
            return shopFailure(game, playerId);
        }
        HeroPartyState state = state(playerId);
        if (weapon == null) {
            return ActionResult.UNKNOWN_WEAPON;
        }
        if (!state.owns(weapon)) {
            return ActionResult.NOT_OWNED;
        }
        int nextLevel = state.weaponLevel(weapon) + 1;
        if (nextLevel > HeroPartyBalance.MAX_WEAPON_LEVEL) {
            return ActionResult.MAX_LEVEL;
        }
        long cost = HeroPartyBalance.weaponUpgradeCost(nextLevel);
        if (!player.economy().spendMineral(cost)) {
            return ActionResult.NOT_ENOUGH_DIAMOND;
        }
        state.upgradeWeapon(weapon);
        refreshParty(game, playerId);
        return ActionResult.SUCCESS;
    }

    public static ActionResult equipWeapon(SemionGame game, UUID playerId, HeroWeapon weapon) {
        if (eligibleShopPlayer(game, playerId) == null) {
            return shopFailure(game, playerId);
        }
        HeroPartyState state = state(playerId);
        if (weapon == null) {
            return ActionResult.UNKNOWN_WEAPON;
        }
        if (!state.equip(weapon)) {
            return ActionResult.NOT_OWNED;
        }
        refreshParty(game, playerId);
        FakePlayerTowerVisuals.refreshOwner(playerId);
        return ActionResult.SUCCESS;
    }

    public static ActionResult upgradeArmor(SemionGame game, UUID playerId) {
        SemionPlayer player = eligibleShopPlayer(game, playerId);
        if (player == null) {
            return shopFailure(game, playerId);
        }
        HeroPartyState state = state(playerId);
        int nextLevel = state.armorLevel() + 1;
        if (nextLevel > HeroPartyBalance.MAX_ARMOR_LEVEL) {
            return ActionResult.MAX_LEVEL;
        }
        long cost = HeroPartyBalance.armorUpgradeCost(nextLevel);
        if (!player.economy().spendMineral(cost)) {
            return ActionResult.NOT_ENOUGH_DIAMOND;
        }
        state.upgradeArmor();
        refreshParty(game, playerId);
        FakePlayerTowerVisuals.refreshOwner(playerId);
        return ActionResult.SUCCESS;
    }

    public static ActionResult toggleArmorVisibility(SemionGame game, UUID playerId) {
        if (game == null || playerId == null || !game.players().containsKey(playerId)) {
            return ActionResult.PLAYER_NOT_IN_GAME;
        }
        if (!hasActiveHero(game, playerId)) {
            return ActionResult.HERO_REQUIRED;
        }
        state(playerId).toggleArmorVisibility();
        FakePlayerTowerVisuals.refreshOwner(playerId);
        return ActionResult.SUCCESS;
    }

    public static void assignQuest(JobContext context, int round) {
        if (context == null) {
            return;
        }
        ServerPlayer player = onlinePlayer(context.game(), context.player().uuid());
        state(context.player().uuid()).assignQuest(context.game(), round, player);
    }

    public static void finishQuest(JobContext context) {
        if (context == null) {
            return;
        }
        UUID playerId = context.player().uuid();
        PlayerLane lane = context.game().playerLane(playerId).orElse(null);
        int reward = state(playerId).finishQuest(lane, onlinePlayer(context.game(), playerId));
        if (reward > 0) {
            refreshParty(context.game(), playerId);
        }
    }

    public static void refreshParty(SemionGame game, UUID playerId) {
        if (game == null || playerId == null) {
            return;
        }
        game.playerLane(playerId).ifPresent(lane -> {
            for (Tower tower : lane.towers()) {
                if (tower.ownerPlayer().equals(playerId) && tower instanceof HeroPartyTower heroPartyTower) {
                    heroPartyTower.refreshPartyStats(lane);
                }
            }
        });
    }

    private static SemionPlayer eligibleShopPlayer(SemionGame game, UUID playerId) {
        if (game == null || playerId == null || game.phase() != RoundPhase.PREPARE_AND_SUMMON || !hasActiveHero(game, playerId)) {
            return null;
        }
        return game.players().get(playerId);
    }

    private static ActionResult shopFailure(SemionGame game, UUID playerId) {
        if (game == null || playerId == null || !game.players().containsKey(playerId)) {
            return ActionResult.PLAYER_NOT_IN_GAME;
        }
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return ActionResult.INVALID_PHASE;
        }
        return ActionResult.HERO_REQUIRED;
    }

    private static ServerPlayer onlinePlayer(SemionGame game, UUID playerId) {
        if (game == null || playerId == null) {
            return null;
        }
        return game.playerLane(playerId)
                .map(PlayerLane::arenaWorld)
                .map(world -> world.getServer().getPlayerList().getPlayer(playerId))
                .orElse(null);
    }

    public enum ActionResult {
        SUCCESS,
        PLAYER_NOT_IN_GAME,
        INVALID_PHASE,
        HERO_REQUIRED,
        UNKNOWN_WEAPON,
        ALREADY_OWNED,
        NOT_OWNED,
        MAX_LEVEL,
        NOT_ENOUGH_DIAMOND
    }
}
