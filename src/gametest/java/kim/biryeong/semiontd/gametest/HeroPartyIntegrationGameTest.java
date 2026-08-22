package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
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
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroCompanionTower;
import kim.biryeong.semiontd.tower.hero.HeroCompanionSkins;
import kim.biryeong.semiontd.tower.hero.HeroQuestKind;
import kim.biryeong.semiontd.tower.hero.HeroPartyState;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import kim.biryeong.semiontd.tower.hero.HeroTower;
import kim.biryeong.semiontd.tower.hero.HeroWeapon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

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
            ServerPlayer heroFakePlayer = activeFakePlayers().stream()
                    .filter(fakePlayer -> FakePlayerTowerVisuals.resolveInteractionAnchor(
                            context.getLevel(), fakePlayer.getId()) instanceof SemionTowerEntity entity
                            && entity.runtimeTower() != null
                            && HeroPartyTowers.isHero(entity.runtimeTower().type()))
                    .findFirst()
                    .orElseThrow();
            var heroAnchor = FakePlayerTowerVisuals.resolveInteractionAnchor(
                    context.getLevel(), heroFakePlayer.getId()
            );
            heroAnchor.setYHeadRot(73.0F);
            heroAnchor.setXRot(-18.0F);
            game.tick(context.getLevel().getServer());
            if (!equals(context, 2, activeVisualProfileIds().size(), "Hero Party visuals must survive the first tower sync tick.")) {
                return;
            }
            if (!equals(context, 73.0F, heroFakePlayer.getYHeadRot(), "The Hero visual should face its attack target.")) {
                return;
            }
            if (!equals(context, -18.0F, heroFakePlayer.getXRot(), "The Hero visual should copy vertical aim.")) {
                return;
            }
            var packet = ServerboundInteractPacket.createInteractionPacket(
                    heroFakePlayer,
                    false,
                    InteractionHand.MAIN_HAND
            );
            if (!check(context, packet.getTarget(context.getLevel()) == heroAnchor,
                    "Right-clicking the Hero visual must resolve to its tower anchor.")) {
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
            FakePlayerTowerVisuals.refreshSkin(ownerId, HeroCompanionRole.KNIGHT);
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

            HeroTower heroTower = (HeroTower) lane.towerAt(kim.biryeong.semiontd.game.GridPosition.from(heroPos));
            SemionTowerEntity heroEntity = towerEntity(context, heroTower);
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
            if (!equals(context, beforeEquipment - 306, game.players().get(ownerId).economy().diamond(),
                    "Equipment should charge 100 + 80 + 126 diamonds.")) {
                return;
            }
            requireClose(253.0, heroTower.currentMaxHealth(),
                    "Greatsword health scaling should include Armor +1.");
            requireClose(101.2, heroTower.health(),
                    "Weapon and armor changes should preserve the current health ratio.");
            if (!equals(context, 40, heroTower.aggroPriority(),
                    "Greatsword should set the Hero's aggro priority.")) {
                return;
            }
            if (!equals(context, 19, heroTower.adjustAttackInterval(heroTower.type().attackIntervalTicks()),
                    "Greatsword +1 should reduce its attack interval by one tick.")) {
                return;
            }
            if (!equals(context, 40, heroEntity.aggroPriority(),
                    "Equipping a weapon should immediately synchronize entity aggro.")) {
                return;
            }
            if (!equals(context, 19, heroEntity.attackIntervalTicks(),
                    "Weapon upgrades should immediately synchronize the entity attack interval.")) {
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
            requireClose(253.0, heroTower.currentMaxHealth(),
                    "Hiding armor must keep its maximum-health bonus.");
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

    @GameTest
    public void focusFireDefenseStacksWithHeroArmorAndKnightReduction(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID ownerId = UUID.nameUUIDFromBytes("hero-party-focus-defense".getBytes(StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            require(game.selectJob(ownerId, HeroPartyTowerJob.ID), "Hero Party job should be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(new AssignedParticipant(ownerId, "hero-focus", TeamId.RED, 1)),
                    Set.of(),
                    1
            )), "Hero Party focus-defense game should start.");
            PlayerLane lane = game.playerLane(ownerId).orElseThrow();
            BlockPos heroPos = BlockPos.containing(lane.laneLayout().positionAt(0.30));
            require(ProductionTowerService.placeTower(game, ownerId, heroPos, HeroPartyTowers.HERO_ID)
                            == TowerPlacementResult.SUCCESS,
                    "The Hero must be placeable for the focus-defense test.");
            HeroTower hero = (HeroTower) lane.towerAt(GridPosition.from(heroPos));
            SemionTowerEntity heroEntity = towerEntity(context, hero);

            BlockPos knightPos = nearbyPosition(lane, heroPos);
            HeroCompanionTower knight = (HeroCompanionTower) ProductionTowerCatalog.find(
                            HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 4).id())
                    .orElseThrow()
                    .create(ownerId, TeamId.RED, 1, GridPosition.from(knightPos));
            lane.addTower(knight);
            SemionTowerEntity knightEntity = towerEntity(context, knight);

            for (int index = 0; index < 6; index++) {
                monsters.add(spawnFocusMonster(context, lane, "hero-focus-" + index,
                        heroEntity.position().add(index * 0.1, 0.0, 0.0), Optional.empty()));
            }
            monsters.forEach(monster -> monster.setTarget(heroEntity));
            requireClose(60.0, hero.modifyIncomingDamage(
                    heroEntity, heroEntity.damageSources().generic(), 100.0),
                    "Six attackers must activate maximum focus defense.");

            game.players().get(ownerId).economy().addDiamond(2_000);
            for (int level = 0; level < 5; level++) {
                require(HeroPartyStates.upgradeArmor(game, ownerId) == HeroPartyStates.ActionResult.SUCCESS,
                        "Hero armor upgrades must succeed during preparation.");
            }
            requireClose(48.0, hero.modifyIncomingDamage(
                    heroEntity, heroEntity.damageSources().generic(), 100.0),
                    "Maximum armor and maximum focus defense must reduce damage by 52% multiplicatively.");

            monsters.subList(0, 6).forEach(monster -> monster.setTarget(knightEntity));
            requireClose(48.0, knight.modifyIncomingDamage(
                    knightEntity, knightEntity.damageSources().generic(), 100.0),
                    "T4 Knight reduction and maximum focus defense must reduce damage by 52% multiplicatively.");

            monsters.forEach(monster -> monster.setTarget(heroEntity));
            heroEntity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, 0.10, 60);
            float healthBefore = heroEntity.getHealth();
            heroEntity.hurtServer(context.getLevel(), heroEntity.damageSources().generic(), 100.0F);
            requireClose(43.2, healthBefore - heroEntity.getHealth(),
                    "Priest-style timed reduction, armor, and focus defense must multiply independently.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Hero Party focus defense GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            game.close();
        }
    }

    @GameTest
    public void physicalSingleTargetRolesDealMoreDamageToIncomeMonsters(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID ownerId = UUID.nameUUIDFromBytes("hero-party-income-damage".getBytes(StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            require(game.selectJob(ownerId, HeroPartyTowerJob.ID), "Hero Party job should be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(new AssignedParticipant(ownerId, "hero-income", TeamId.RED, 1)),
                    Set.of(),
                    1
            )), "Hero Party income-damage game should start.");
            PlayerLane lane = game.playerLane(ownerId).orElseThrow();
            BlockPos heroPos = BlockPos.containing(lane.laneLayout().positionAt(0.30));
            require(ProductionTowerService.placeTower(game, ownerId, heroPos, HeroPartyTowers.HERO_ID)
                            == TowerPlacementResult.SUCCESS,
                    "The Hero must be placeable for the income-damage test.");
            HeroTower hero = (HeroTower) lane.towerAt(GridPosition.from(heroPos));
            SemionTowerEntity heroEntity = towerEntity(context, hero);
            SemionMonsterEntity normal = spawnFocusMonster(
                    context, lane, "hero-normal-target", heroEntity.position().add(1.0, 0.0, 0.0), Optional.empty()
            );
            SemionMonsterEntity income = spawnFocusMonster(
                    context, lane, "hero-income-target", heroEntity.position().add(2.0, 0.0, 0.0), Optional.of(TeamId.BLUE)
            );
            monsters.add(normal);
            monsters.add(income);

            requireClose(100.0, hero.modifyResolvedAttackDamage(heroEntity, normal, 100.0),
                    "The sword must keep its normal-monster damage.");
            requireClose(135.0, hero.modifyResolvedAttackDamage(heroEntity, income, 100.0),
                    "The sword must deal 35% more damage to income monsters.");
            game.players().get(ownerId).economy().addDiamond(200);
            require(HeroPartyStates.purchaseWeapon(game, ownerId, HeroWeapon.LONGBOW)
                            == HeroPartyStates.ActionResult.SUCCESS,
                    "The longbow purchase must succeed during preparation.");
            require(HeroPartyStates.equipWeapon(game, ownerId, HeroWeapon.LONGBOW)
                            == HeroPartyStates.ActionResult.SUCCESS,
                    "The longbow must equip during preparation.");
            requireClose(135.0, hero.modifyResolvedAttackDamage(heroEntity, income, 100.0),
                    "The longbow must deal 35% more damage to income monsters.");

            for (int tier = 1; tier <= 4; tier++) {
                HeroCompanionTower archer = (HeroCompanionTower) ProductionTowerCatalog.find(
                                HeroPartyTowers.companion(HeroCompanionRole.ARCHER, tier).id())
                        .orElseThrow()
                        .create(ownerId, TeamId.RED, 1, GridPosition.from(heroPos));
                requireClose(135.0, archer.modifyResolvedAttackDamage(null, income, 100.0),
                        "Every Archer tier must deal 35% more damage to income monsters.");
            }
            HeroCompanionTower mage = (HeroCompanionTower) ProductionTowerCatalog.find(
                            HeroPartyTowers.companion(HeroCompanionRole.MAGE, 3).id())
                    .orElseThrow()
                    .create(ownerId, TeamId.RED, 1, GridPosition.from(heroPos));
            requireClose(100.0, mage.modifyResolvedAttackDamage(null, income, 100.0),
                    "The Mage must keep its existing damage against income monsters.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Hero Party income damage GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            game.close();
        }
    }

    @GameTest
    public void companionAttackAbilitiesUnlockScaleAndKeepUpgradeCounters(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID ownerId = UUID.nameUUIDFromBytes("hero-party-attack-abilities".getBytes(StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        ArrayList<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            require(game.selectJob(ownerId, HeroPartyTowerJob.ID), "Hero Party job should be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(new AssignedParticipant(ownerId, "hero-abilities", TeamId.RED, 1)),
                    Set.of(),
                    1
            )), "Hero Party ability game should start.");
            PlayerLane lane = game.playerLane(ownerId).orElseThrow();
            BlockPos origin = BlockPos.containing(lane.laneLayout().positionAt(0.30));

            HeroCompanionTower knightT1 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 1);
            SemionMonsterEntity knightT1Target = abilityTarget(context, lane, monsters, "knight-t1", knightT1, 1.0);
            recordAttacks(context, knightT1, knightT1Target, 4, 100.0);
            requireClose(0.0, knightT1Target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "T1 Knight must not unlock Shield Bash.");
            lane.removeTower(knightT1);

            HeroCompanionTower knightT2 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 2);
            SemionMonsterEntity knightUpgradeTarget = abilityTarget(
                    context, lane, monsters, "knight-upgrade", knightT2, 1.0
            );
            recordAttacks(context, knightT2, knightUpgradeTarget, 3, 100.0);
            HeroCompanionTower knightT3 = createCompanion(ownerId, knightT2.position(), HeroCompanionRole.KNIGHT, 3);
            knightT3.copyFrom(knightT2, 0);
            require(lane.replaceTower(knightT2, knightT3), "Knight upgrade must replace the previous tower.");
            recordAttacks(context, knightT3, knightUpgradeTarget, 1, 100.0);
            requireClose(0.25, knightUpgradeTarget.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "T3 Knight must inherit the fourth-hit counter from T2.");
            requireClose(0.25, knightUpgradeTarget.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Shield Bash must slow both movement and attacks.");
            knightT3.onWaveStarted(lane, 2);
            SemionMonsterEntity knightResetTarget = abilityTarget(
                    context, lane, monsters, "knight-reset", knightT3, 1.2
            );
            recordAttacks(context, knightT3, knightResetTarget, 1, 100.0);
            requireClose(0.0, knightResetTarget.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Wave start must reset companion attack counters.");
            lane.removeTower(knightT3);

            HeroCompanionTower knightT4 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 4);
            SemionMonsterEntity knightT4Target = abilityTarget(context, lane, monsters, "knight-t4", knightT4, 1.0);
            recordAttacks(context, knightT4, knightT4Target, 3, 100.0);
            requireClose(0.35, knightT4Target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "T4 Shield Bash must trigger every third hit at 35%.");
            require(knightT4Target.activeTimedEffectTicks(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) == 60,
                    "T4 Shield Bash must last three seconds.");
            lane.removeTower(knightT4);

            verifyArcherAbilities(context, lane, ownerId, origin, monsters);
            verifyMageAbilities(context, lane, ownerId, origin, monsters);
            verifyRogueAbilitiesAndQuest(context, game, lane, ownerId, origin, monsters);
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Hero Party attack ability GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            monsters.forEach(SemionMonsterEntity::discard);
            game.close();
        }
    }

    @GameTest
    public void companionSupportAbilitiesUnlockScaleDoNotStackAndKeepAuraContinuous(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID ownerId = UUID.nameUUIDFromBytes("hero-party-support-abilities".getBytes(StandardCharsets.UTF_8));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        try {
            require(game.selectJob(ownerId, HeroPartyTowerJob.ID), "Hero Party job should be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(new AssignedParticipant(ownerId, "hero-support", TeamId.RED, 1)),
                    Set.of(),
                    1
            )), "Hero Party support game should start.");
            PlayerLane lane = game.playerLane(ownerId).orElseThrow();
            BlockPos origin = BlockPos.containing(lane.laneLayout().positionAt(0.30));

            verifyPriestAbilities(context, lane, ownerId, origin);
            verifyKnightGuard(context, lane, ownerId, origin);
            verifyBardAbilities(context, lane, ownerId, origin);
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Hero Party support ability GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            game.close();
        }
    }

    private static void verifyArcherAbilities(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin,
            List<SemionMonsterEntity> monsters
    ) {
        double[] expectedPierce = {0.0, 60.0, 60.0, 75.0};
        double[] expectedMark = {0.0, 0.0, 0.12, 0.15};
        int[] triggerCounts = {4, 4, 4, 3};
        for (int tier = 1; tier <= 4; tier++) {
            HeroCompanionTower archer = addCompanion(
                    context, lane, ownerId, origin, HeroCompanionRole.ARCHER, tier
            );
            SemionTowerEntity source = towerEntity(context, archer);
            SemionMonsterEntity primary = abilityTarget(
                    context, lane, monsters, "archer-primary-t" + tier, archer, 1.0
            );
            SemionMonsterEntity secondary = abilityTarget(
                    context, lane, monsters, "archer-secondary-t" + tier, archer, 1.5
            );
            if (tier == 2) {
                source.applyTimedEffect(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, 0.20, 100);
            }
            recordAttacks(context, archer, primary, triggerCounts[tier - 1], 100.0);
            double expected = tier == 2 ? 72.0 : expectedPierce[tier - 1];
            requireClose(1_000.0 - expected, secondary.runtimeMonster().health(),
                    "Archer pierce damage must unlock at T2 and use the shared damage pipeline once.");
            requireClose(expectedMark[tier - 1],
                    primary.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                    "Archer mark must unlock at T3 and strengthen at T4.");
            lane.removeTower(archer);
            primary.discard();
            secondary.discard();
        }
    }

    private static void verifyMageAbilities(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin,
            List<SemionMonsterEntity> monsters
    ) {
        double[] expectedLastSplash = {30.0, 40.0, 75.0, 105.0};
        double[] expectedSlow = {0.0, 0.20, 0.20, 0.30};
        int[] triggerCounts = {1, 1, 5, 4};
        for (int tier = 1; tier <= 4; tier++) {
            HeroCompanionTower mage = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.MAGE, tier);
            SemionMonsterEntity primary = abilityTarget(
                    context, lane, monsters, "mage-primary-t" + tier, mage, 1.0
            );
            SemionMonsterEntity secondary = abilityTarget(
                    context, lane, monsters, "mage-secondary-t" + tier, mage, 1.5
            );
            if (triggerCounts[tier - 1] > 1) {
                recordAttacks(context, mage, primary, triggerCounts[tier - 1] - 1, 100.0);
            }
            double healthBefore = secondary.runtimeMonster().health();
            recordAttacks(context, mage, primary, 1, 100.0);
            requireClose(expectedLastSplash[tier - 1], healthBefore - secondary.runtimeMonster().health(),
                    "Mage empowered splash must unlock at T3 and strengthen at T4.");
            requireClose(expectedSlow[tier - 1],
                    primary.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Mage slow must unlock at T2 and strengthen at T4.");
            requireClose(expectedSlow[tier - 1],
                    secondary.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Mage splash targets must receive the same slow.");
            lane.removeTower(mage);
            primary.discard();
            secondary.discard();
        }
    }

    private static void verifyRogueAbilitiesAndQuest(
            GameTestHelper context,
            SemionGame game,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin,
            List<SemionMonsterEntity> monsters
    ) throws ReflectiveOperationException {
        double[] expectedCombo = {0.0, 40.0, 40.0, 60.0};
        double[] expectedHaste = {0.0, 0.0, 0.20, 0.30};
        int[] triggerCounts = {4, 4, 4, 3};
        for (int tier = 1; tier <= 4; tier++) {
            HeroCompanionTower rogue = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.ROGUE, tier);
            SemionMonsterEntity target = abilityTarget(
                    context, lane, monsters, "rogue-target-t" + tier, rogue, 1.0
            );
            recordAttacks(context, rogue, target, triggerCounts[tier - 1], 100.0);
            requireClose(1_000.0 - expectedCombo[tier - 1], target.runtimeMonster().health(),
                    "Rogue combo must unlock at T2 and strengthen at T4.");
            rogue.onKill(towerEntity(context, rogue), target, 100.0);
            requireClose(expectedHaste[tier - 1],
                    towerEntity(context, rogue).activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                    "Rogue pursuit must unlock at T3 and strengthen at T4.");
            lane.removeTower(rogue);
            target.discard();
        }

        HeroCompanionTower questRogue = addCompanion(
                context, lane, ownerId, origin, HeroCompanionRole.ROGUE, 2
        );
        HeroPartyState state = HeroPartyStates.state(ownerId);
        assignQuestForTesting(state, game, HeroQuestKind.COMPANION_KILLS);
        markHeroAtWaveStartForTesting(state);
        SemionMonsterEntity questTarget = abilityTarget(
                context, lane, monsters, "rogue-quest-target", questRogue, 1.0
        );
        questTarget.runtimeMonster().syncHealth(30.0);
        questTarget.setHealth(30.0F);
        recordAttacks(context, questRogue, questTarget, 4, 100.0);
        require(!questTarget.isAlive(), "Rogue combo must propagate its kill through the shared damage path.");
        requireClose(1.0, state.quest().progress(), "The combo kill must count exactly once for companion quests.");
        lane.removeTower(questRogue);
    }

    private static void verifyPriestAbilities(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin
    ) {
        double[] expectedPrimaryHeal = {28.0, 42.0, 62.0, 90.0};
        double[] expectedSecondRatio = {0.0, 0.0, 0.50, 1.0};
        double[] expectedGuard = {0.0, 0.08, 0.10, 0.15};
        for (int tier = 1; tier <= 4; tier++) {
            HeroCompanionTower priest = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.PRIEST, tier);
            HeroCompanionTower first = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 1);
            HeroCompanionTower second = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.ARCHER, 1);
            woundTower(context, first, 100.0);
            woundTower(context, second, 100.0);
            priest.tick(lane);
            requireClose(first.currentMaxHealth() - 100.0 + expectedPrimaryHeal[tier - 1], first.health(),
                    "Priest primary healing must keep its configured tier value.");
            requireClose(second.currentMaxHealth() - 100.0
                            + expectedPrimaryHeal[tier - 1] * expectedSecondRatio[tier - 1],
                    second.health(), "Priest chain healing must unlock at T3 and strengthen at T4.");
            requireClose(expectedGuard[tier - 1],
                    towerEntity(context, first).activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Priest protection must unlock at T2 and strengthen at T4.");
            requireClose(tier >= 3 ? expectedGuard[tier - 1] : 0.0,
                    towerEntity(context, second).activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Priest protection must follow actual chain-heal targets.");
            lane.removeTower(priest);
            lane.removeTower(first);
            lane.removeTower(second);
        }
    }

    private static void verifyKnightGuard(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin
    ) {
        HeroCompanionTower target = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.ARCHER, 1);
        SemionTowerEntity targetEntity = towerEntity(context, target);
        HeroCompanionTower knightT2 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 2);
        knightT2.tick(lane);
        requireClose(0.0, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                "T2 Knight must not unlock Guard Formation.");
        lane.removeTower(knightT2);

        HeroCompanionTower knightT3 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 3);
        knightT3.tick(lane);
        requireClose(0.08, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                "T3 Guard Formation must reduce allied damage by 8%.");
        HeroCompanionTower knightT4 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.KNIGHT, 4);
        knightT4.tick(lane);
        knightT3.tick(lane);
        requireClose(0.12, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                "Multiple Knights must keep only the strongest same-owner guard.");
        lane.removeTower(knightT3);
        lane.removeTower(knightT4);
        lane.removeTower(target);
    }

    private static void verifyBardAbilities(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin
    ) {
        double[] expectedSpeed = {0.08, 0.11, 0.24, 0.33};
        double[] expectedDamage = {0.0, 0.03, 0.16, 0.25};
        for (int tier = 1; tier <= 4; tier++) {
            HeroCompanionTower target = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.ARCHER, 1);
            HeroCompanionTower bard = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.BARD, tier);
            int pulses = tier == 3 ? 5 : tier == 4 ? 4 : 1;
            for (int pulse = 0; pulse < pulses; pulse++) {
                advanceSupportPulse(bard, lane);
            }
            SemionTowerEntity targetEntity = towerEntity(context, target);
            requireClose(expectedSpeed[tier - 1],
                    targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                    "Bard attack-speed aura and Encore must follow tier progression.");
            requireClose(expectedDamage[tier - 1],
                    targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                    "Bard damage aura and Encore must follow tier progression.");
            lane.removeTower(bard);
            lane.removeTower(target);
        }

        HeroCompanionTower target = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.ARCHER, 1);
        HeroCompanionTower bardT2 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.BARD, 2);
        bardT2.tick(lane);
        SemionTowerEntity targetEntity = towerEntity(context, target);
        for (int tick = 0; tick < 21; tick++) {
            targetEntity.aiStep();
            bardT2.tick(lane);
            require(targetEntity.activeTimedEffectTicks(TimedEffectType.TOWER_ATTACK_SPEED_BONUS) > 0,
                    "Refreshing Bard aura must not leave a one-tick gap.");
        }
        lane.removeTower(bardT2);

        HeroCompanionTower bardT3 = addCompanion(context, lane, ownerId, origin, HeroCompanionRole.BARD, 3);
        for (int pulse = 0; pulse < 3; pulse++) {
            advanceSupportPulse(bardT3, lane);
        }
        HeroCompanionTower upgraded = createCompanion(ownerId, bardT3.position(), HeroCompanionRole.BARD, 4);
        upgraded.copyFrom(bardT3, 0);
        require(lane.replaceTower(bardT3, upgraded), "Bard upgrade must replace the previous tower.");
        advanceSupportPulse(upgraded, lane);
        requireClose(0.33, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Bard upgrades must inherit the support pulse count and trigger T4 Encore on pulse four.");
        requireClose(0.25, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                "Inherited Bard pulses must use the upgraded Encore strength.");
        upgraded.onWaveStarted(lane, 2);
        for (int tick = 0; tick < 40; tick++) {
            targetEntity.aiStep();
        }
        advanceSupportPulse(upgraded, lane);
        requireClose(0.18, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Wave start must reset Bard pulse count before the next Encore.");
        requireClose(0.10, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                "The first pulse after wave start must apply only the base T4 aura.");
        for (int pulse = 1; pulse < 4; pulse++) {
            advanceSupportPulse(upgraded, lane);
        }
        requireClose(0.33, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "T4 Bard must trigger Encore on the fourth pulse after wave reset.");
        lane.removeTower(upgraded);
        lane.removeTower(target);
    }

    private static HeroCompanionTower addCompanion(
            GameTestHelper context,
            PlayerLane lane,
            UUID ownerId,
            BlockPos origin,
            HeroCompanionRole role,
            int tier
    ) {
        BlockPos position = nearbyPosition(lane, origin);
        HeroCompanionTower tower = createCompanion(ownerId, GridPosition.from(position), role, tier);
        lane.addTower(tower);
        require(towerEntity(context, tower) != null, "Companion entity must spawn for ability tests.");
        return tower;
    }

    private static HeroCompanionTower createCompanion(
            UUID ownerId,
            GridPosition position,
            HeroCompanionRole role,
            int tier
    ) {
        return (HeroCompanionTower) ProductionTowerCatalog.find(HeroPartyTowers.companion(role, tier).id())
                .orElseThrow()
                .create(ownerId, TeamId.RED, 1, position);
    }

    private static SemionMonsterEntity abilityTarget(
            GameTestHelper context,
            PlayerLane lane,
            List<SemionMonsterEntity> monsters,
            String id,
            HeroCompanionTower source,
            double offset
    ) {
        SemionMonsterEntity target = spawnFocusMonster(
                context, lane, id, towerEntity(context, source).position().add(offset, 0.0, 0.0), Optional.empty()
        );
        monsters.add(target);
        return target;
    }

    private static void recordAttacks(
            GameTestHelper context,
            HeroCompanionTower tower,
            SemionMonsterEntity target,
            int count,
            double attemptedDamage
    ) {
        SemionTowerEntity source = towerEntity(context, tower);
        for (int attack = 0; attack < count; attack++) {
            source.recordAttack(target, attemptedDamage, attemptedDamage, attemptedDamage, false);
        }
    }

    private static void woundTower(GameTestHelper context, Tower tower, double missingHealth) {
        double health = Math.max(1.0, tower.currentMaxHealth() - missingHealth);
        tower.syncHealth(health);
        towerEntity(context, tower).setHealth((float) health);
    }

    private static void advanceSupportPulse(HeroCompanionTower tower, PlayerLane lane) {
        tower.tick(lane);
        for (int cooldown = 0; cooldown < 20; cooldown++) {
            tower.tick(lane);
        }
    }

    private static void assignQuestForTesting(
            HeroPartyState state,
            SemionGame game,
            HeroQuestKind expectedKind
    ) throws ReflectiveOperationException {
        var method = HeroPartyState.class.getDeclaredMethod(
                "assignQuest", SemionGame.class, int.class, ServerPlayer.class
        );
        method.setAccessible(true);
        for (int round = 1; round <= 200; round++) {
            method.invoke(state, game, round, null);
            if (state.quest() != null && state.quest().kind() == expectedKind) {
                return;
            }
        }
        throw new AssertionError("Could not select quest " + expectedKind + " for the GameTest.");
    }

    private static void markHeroAtWaveStartForTesting(HeroPartyState state) throws ReflectiveOperationException {
        var method = HeroPartyState.class.getDeclaredMethod(
                "markHeroAtWaveStart", HeroWeapon.class, ServerPlayer.class
        );
        method.setAccessible(true);
        method.invoke(state, HeroWeapon.SWORD, null);
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

    private static SemionMonsterEntity spawnFocusMonster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            net.minecraft.world.phys.Vec3 position,
            Optional<TeamId> senderTeam
    ) {
        Monster monster = new Monster(
                id, lane.teamId(), lane.laneId(), Optional.empty(), senderTeam,
                1_000.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "A focus-fire attacker must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, Tower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(
                ((kim.biryeong.semiontd.tower.EntityBackedTower) tower).entityId().orElseThrow()
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.05) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual + ".");
        }
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
            var method = FakePlayerTowerVisuals.class.getDeclaredMethod("activeProfileIdsForTesting");
            method.setAccessible(true);
            return (Set<UUID>) method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to inspect Hero fake-player visuals.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> activeFakePlayers() {
        try {
            var method = FakePlayerTowerVisuals.class.getDeclaredMethod("activeFakePlayersForTesting");
            method.setAccessible(true);
            return (List<ServerPlayer>) method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to inspect Hero fake players.", exception);
        }
    }

    private static boolean equals(GameTestHelper context, Object expected, Object actual, String message) {
        return check(context, java.util.Objects.equals(expected, actual), message + " Expected " + expected + ", got " + actual + ".");
    }
}
