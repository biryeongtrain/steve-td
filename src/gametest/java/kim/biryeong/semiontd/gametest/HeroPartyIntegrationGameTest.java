package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.HeroPartyTowerJob;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroCompanionSkins;
import kim.biryeong.semiontd.tower.hero.HeroPartyState;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.hero.HeroPlayerVisuals;
import kim.biryeong.semiontd.tower.hero.HeroWeapon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class HeroPartyIntegrationGameTest {
    @GameTest
    public void heroCommandTreeExposesShopQuestPartyAndCompanion(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var hero = dispatcher.getRoot().getChild("semiontd").getChild("hero");
        if (!check(context, hero != null, "Expected /semiontd hero to be registered.")) {
            return;
        }
        for (String child : List.of("skin", "shop", "quest", "party", "companion")) {
            if (!check(context, hero.getChild(child) != null, "Missing /semiontd hero " + child + ".")) {
                return;
            }
        }
        for (String command : List.of(
                "semiontd hero shop",
                "semiontd hero skin",
                "semiontd hero quest",
                "semiontd hero party",
                "semiontd hero companion knight"
        )) {
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (!check(context, !parsed.getReader().canRead(), "Command did not parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void weightedPlacementEquipmentQuestAndFakePlayerLifecycleWorkTogether(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID ownerId = UUID.nameUUIDFromBytes("hero-party-gametest-owner".getBytes(StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        boolean closed = false;
        try {
            if (!check(context, game.selectJob(ownerId, HeroPartyTowerJob.ID), "Hero Party job should be selectable.")) {
                return;
            }
            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(new AssignedParticipant(ownerId, "hero", TeamId.RED, 1)),
                    Set.of(),
                    1
            );
            if (!check(context, game.start(context.getLevel().getServer(), plan), "Hero Party game should start.")) {
                return;
            }
            PlayerLane lane = game.playerLane(ownerId).orElseThrow();
            BlockPos heroPos = BlockPos.containing(lane.laneLayout().positionAt(0.30));
            BlockPos knightPos = nearbyPosition(lane, heroPos);
            var trackingViewer = context.makeMockServerPlayerInLevel();
            trackingViewer.snapTo(heroPos.getX() + 0.5, heroPos.getY() + 1.0, heroPos.getZ() + 0.5);

            if (!equals(context, TowerPlacementResult.TOWER_NOT_ALLOWED,
                    ProductionTowerService.placeTower(game, ownerId, heroPos, HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1).id()),
                    "Companions must be blocked before the Hero is placed.")) {
                return;
            }
            if (!equals(context, TowerPlacementResult.SUCCESS,
                    ProductionTowerService.placeTower(game, ownerId, heroPos, HeroPartyTowers.HERO_ID),
                    "Hero should use three tower slots.")) {
                return;
            }
            if (!equals(context, TowerPlacementResult.SUCCESS,
                    ProductionTowerService.placeTower(game, ownerId, knightPos, HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1).id()),
                    "A T1 companion should fill the remaining two slots.")) {
                return;
            }
            BlockPos archerPos = nearbyPosition(lane, knightPos);
            if (!equals(context, 5, game.towerCapacityUsed(ownerId), "Hero plus T1 companion should use five slots.")) {
                return;
            }
            if (!equals(context, 2, activeVisualProfileIds().size(), "Each Hero Party tower should own one fake-player visual.")) {
                return;
            }
            game.tick(context.getLevel().getServer());
            if (!equals(context, 2, activeVisualProfileIds().size(), "Hero Party visuals must survive the first tower sync tick.")) {
                return;
            }
            for (UUID profileId : activeVisualProfileIds()) {
                if (!check(context, context.getLevel().getServer().getPlayerList().getPlayer(profileId) == null,
                        "Fake players must not be registered as server participants.")) {
                    return;
                }
            }

            Set<UUID> defaultVisualIds = activeVisualProfileIds();
            HeroCompanionSkinPreference knightSkin = new HeroCompanionSkinPreference(
                    "SkinSource",
                    UUID.nameUUIDFromBytes("hero-party-skin-source".getBytes(StandardCharsets.UTF_8)).toString(),
                    "test-texture-value",
                    ""
            );
            HeroCompanionSkins.set(ownerId, HeroCompanionRole.KNIGHT, knightSkin);
            HeroPlayerVisuals.refreshSkin(ownerId, HeroCompanionRole.KNIGHT);
            Set<UUID> skinnedVisualIds = activeVisualProfileIds();
            if (!equals(context, 2, skinnedVisualIds.size(), "Changing a skin should replace one visual without adding another.")) {
                return;
            }
            if (!check(context, !skinnedVisualIds.equals(defaultVisualIds),
                    "Changing a skin should replace the companion profile UUID.")) {
                return;
            }

            long diamondAtLimit = game.players().get(ownerId).economy().diamond();
            if (!equals(context, TowerPlacementResult.TOWER_LIMIT_REACHED,
                    ProductionTowerService.placeTower(game, ownerId, archerPos, HeroPartyTowers.companion(HeroCompanionRole.ARCHER, 1).id()),
                    "A second companion should be rejected at the initial five-slot limit.")) {
                return;
            }
            if (!equals(context, diamondAtLimit, game.players().get(ownerId).economy().diamond(),
                    "Rejected placement must not spend diamonds.")) {
                return;
            }
            if (!equals(context, TowerUpgradeResult.TOWER_LIMIT_REACHED,
                    ProductionTowerService.upgradeTower(game, ownerId, knightPos, HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2).id()),
                    "T1 to T2 should be rejected until one more slot is bought.")) {
                return;
            }
            if (!equals(context, diamondAtLimit, game.players().get(ownerId).economy().diamond(),
                    "Rejected upgrade must not spend diamonds.")) {
                return;
            }

            game.players().get(ownerId).economy().addDiamond(1_000);
            game.players().get(ownerId).economy().addEmerald(game.economyConfig().towerLimit().initialPurchaseEmeraldCost());
            if (!check(context, game.purchaseTowerLimit(ownerId), "Buying one slot should succeed.")) {
                return;
            }
            if (!equals(context, TowerUpgradeResult.SUCCESS,
                    ProductionTowerService.upgradeTower(game, ownerId, knightPos, HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2).id()),
                    "T1 to T2 should fit after buying one slot.")) {
                return;
            }
            if (!equals(context, 6, game.towerCapacityUsed(ownerId), "Hero plus T2 companion should use six slots.")) {
                return;
            }
            if (!equals(context, skinnedVisualIds, activeVisualProfileIds(),
                    "Upgrading a companion should retain its deterministic skin profile.")) {
                return;
            }

            var heroTower = lane.towerAt(kim.biryeong.semiontd.game.GridPosition.from(heroPos));
            heroTower.syncHealth(80.0);
            long beforeEquipment = game.players().get(ownerId).economy().diamond();
            if (!equals(context, HeroPartyStates.ActionResult.SUCCESS,
                    HeroPartyStates.purchaseWeapon(game, ownerId, HeroWeapon.GREATSWORD),
                    "Greatsword purchase should succeed during prepare.")) {
                return;
            }
            if (!equals(context, HeroPartyStates.ActionResult.SUCCESS,
                    HeroPartyStates.upgradeWeapon(game, ownerId, HeroWeapon.GREATSWORD),
                    "Greatsword +1 should succeed during prepare.")) {
                return;
            }
            if (!equals(context, HeroPartyStates.ActionResult.SUCCESS,
                    HeroPartyStates.equipWeapon(game, ownerId, HeroWeapon.GREATSWORD),
                    "Owned weapon should equip without being consumed.")) {
                return;
            }
            if (!equals(context, HeroPartyStates.ActionResult.SUCCESS,
                    HeroPartyStates.upgradeArmor(game, ownerId),
                    "Armor +1 should succeed during prepare.")) {
                return;
            }
            if (!equals(context, beforeEquipment - 270, game.players().get(ownerId).economy().diamond(),
                    "Equipment should charge 100 + 80 + 90 diamonds.")) {
                return;
            }
            if (!equals(context, 220.0, heroTower.currentMaxHealth(), "Armor +1 should add 60 maximum health.")) {
                return;
            }
            if (!equals(context, 110.0, heroTower.health(), "Armor upgrades should preserve current health ratio.")) {
                return;
            }
            HeroPartyState state = HeroPartyStates.state(ownerId);
            if (!equals(context, HeroPartyStates.ActionResult.SUCCESS,
                    HeroPartyStates.toggleArmorVisibility(game, ownerId),
                    "Armor visuals should be hideable.")) {
                return;
            }
            if (!check(context, !state.armorVisible(), "The armor visibility setting should be disabled.")) {
                return;
            }
            if (!equals(context, 220.0, heroTower.currentMaxHealth(),
                    "Hiding armor must keep its maximum-health bonus.")) {
                return;
            }
            if (!check(context, state.owns(HeroWeapon.SWORD) && state.owns(HeroWeapon.GREATSWORD),
                    "Changing equipment must preserve previously owned weapons.")) {
                return;
            }

            for (int tick = 0; tick < SemionGame.DEFAULT_PREPARE_TICKS; tick++) {
                game.tick(context.getLevel().getServer());
            }
            if (!equals(context, RoundPhase.LANE_WAVE, game.phase(), "Game should enter the wave phase.")) {
                return;
            }
            if (!check(context, state.quest() != null && state.quest().heroPresentAtStart(),
                    "The current quest should lock in the Hero and equipped weapon at wave start.")) {
                return;
            }
            long duringWave = game.players().get(ownerId).economy().diamond();
            if (!equals(context, HeroPartyStates.ActionResult.INVALID_PHASE,
                    HeroPartyStates.purchaseWeapon(game, ownerId, HeroWeapon.LONGBOW),
                    "Equipment changes must be blocked during combat.")) {
                return;
            }
            if (!equals(context, duringWave, game.players().get(ownerId).economy().diamond(),
                    "Blocked equipment changes must not spend diamonds.")) {
                return;
            }

            game.close();
            closed = true;
            if (!check(context, activeVisualProfileIds().isEmpty(),
                    "Closing the match should remove every fake-player visual.")) {
                return;
            }
            if (!check(context, HeroPartyStates.find(ownerId).isEmpty(), "Closing the match should clear Hero Party state.")) {
                return;
            }
            if (!equals(context, knightSkin,
                    HeroCompanionSkins.preference(ownerId, HeroCompanionRole.KNIGHT).orElse(null),
                    "Match cleanup should not clear account skin settings.")) {
                return;
            }
            context.succeed();
        } finally {
            if (!closed) {
                game.close();
            }
            HeroCompanionSkins.clearAll();
        }
    }

    private static BlockPos nearbyPosition(PlayerLane lane, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (!candidate.equals(origin)
                        && lane.canPlaceTowerAt(candidate)
                        && !lane.hasTowerAt(kim.biryeong.semiontd.game.GridPosition.from(candidate))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Could not find a nearby tower position.");
    }

    private static boolean check(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> activeVisualProfileIds() {
        try {
            var method = HeroPlayerVisuals.class.getDeclaredMethod("activeProfileIdsForTesting");
            method.setAccessible(true);
            return (Set<UUID>) method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to inspect Hero fake-player visuals.", exception);
        }
    }

    private static boolean equals(GameTestHelper context, Object expected, Object actual, String message) {
        return check(context, java.util.Objects.equals(expected, actual), message + " Expected " + expected + ", got " + actual + ".");
    }
}
