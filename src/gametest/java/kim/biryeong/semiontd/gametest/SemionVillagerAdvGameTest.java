package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.VillagerAdvTowerJob;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.villager.VillagerSplashTower;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.ui.SemionDialogService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

public final class SemionVillagerAdvGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void villagerAdvJobRegistersAndReusesVillagerStarters(GameTestHelper context) {
        UUID playerId = stableUuid("villager-adv-catalog-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, VillagerAdvTowerJob.ID);
        PlayerLane lane = redLane(game, 1);

        if (!assertTrue(context, JobRegistry.find(VillagerAdvTowerJob.ID).isPresent(), "Villager ADV job should be registered.")) {
            return;
        }

        List<ProductionTowerCatalog.CatalogEntry> starters = ProductionTowerService.availableTowers(game, playerId);
        Set<String> starterIds = starters.stream()
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .map(TowerType::id)
                .collect(Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        VillagerTowers.ADV_T1_SPLASH_TOWER.id(),
                        VillagerTowers.ADV_T1_GOLEM_TOWER.id(),
                        VillagerTowers.ADV_T1_ALLAY_TOWER.id(),
                        VillagerTowers.ADV_T1_CAT_TOWER.id()
                ),
                starterIds,
                "Villager ADV should expose the existing villager starter towers."
        )) {
            return;
        }
        List<String> splashDescription = starters.stream()
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(type -> type.id().equals(VillagerTowers.ADV_T1_SPLASH_TOWER.id()))
                .findFirst()
                .orElseThrow()
                .description();
        if (!assertTrue(context, splashDescription.stream().anyMatch(line -> line.contains("경험치 1마다 공격력이 0.15%")), "Villager ADV catalog should show experience growth lines.")) {
            return;
        }
        if (!assertTrue(context, splashDescription.stream().anyMatch(line -> line.contains("평판 1마다 공격력이 0.1%")), "Villager ADV catalog should show reputation growth lines.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.find(VillagerTowers.T1_SPLASH_TOWER.id()).isPresent(), "Base villager tower should remain registered separately.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.find(VillagerTowers.ADV_T1_SPLASH_TOWER.id()).isPresent(), "ADV villager tower should have its own catalog id.")) {
            return;
        }
        TowerBalanceConfig balance = TowerBalanceConfig.defaultConfig();
        if (!assertTrue(context, balance.towers().containsKey(VillagerTowers.ADV_T1_SPLASH_TOWER.id()), "ADV villager tower should have its own tower balance key.")) {
            return;
        }
        if (!assertEquals(context, 75L, balance.towers().get(VillagerTowers.T1_SPLASH_TOWER.id()).mineralCost(), "Base villager tower should keep its own balance.")) {
            return;
        }
        if (!assertAdvVanillaStats(context, balance)) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, towerPlacementPos(lane), AnimalTowers.T1_PIG_TOWER.id()),
                "Villager ADV should reject non-villager towers."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 240)
    public void villagerAdvWaveStartCalculatesExperienceAndGatesUpgrade(GameTestHelper context) {
        UUID playerId = stableUuid("villager-adv-experience-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, VillagerAdvTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        GridPosition gridPosition = GridPosition.from(towerPos);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, VillagerTowers.ADV_T1_SPLASH_TOWER.id()),
                "Villager ADV should place the reused villager ranged starter."
        )) {
            return;
        }
        Tower placedTower = lane.towerAt(gridPosition);
        TowerUpgradeOption splashUpgrade = ProductionTowerCatalog.upgrade(placedTower.type(), "villager_splash_t2").orElseThrow();
        if (!assertTrue(context, upgradeTooltipText(splashUpgrade, placedTower).contains("경험치 0.0/15.0"), "Villager ADV upgrade tooltip should show the experience requirement.")) {
            return;
        }

        VillagerAdvStates.onWaveStarted(game, 1);
        waitForAdvExperience(context, game, lane, gridPosition, 0, () -> {
            Tower tower = lane.towerAt(gridPosition);
            if (!assertEquals(
                    context,
                    TowerUpgradeResult.NOT_ENOUGH_ADV_EXPERIENCE,
                    ProductionTowerService.upgradeTower(game, playerId, towerPos, "villager_splash_t2"),
                    "Upgrade should be blocked until the tower has enough ADV experience."
            )) {
                return;
            }
            tower.setData(VillagerAdvStates.EXPERIENCE, 15.0);
            if (!assertEquals(
                    context,
                    TowerUpgradeResult.SUCCESS,
                    ProductionTowerService.upgradeTower(game, playerId, towerPos, "villager_splash_t2"),
                    "Upgrade should succeed once the ADV experience requirement is met."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void villagerAdvHalvesExistingVillagerSurvivalBonuses(GameTestHelper context) {
        UUID playerId = stableUuid("villager-adv-survival-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, VillagerAdvTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        SemionPlayer player = game.players().get(playerId);
        BlockPos base = towerPlacementPos(lane);

        VillagerSplashTower librarian = new VillagerSplashTower(
                VillagerTowers.ADV_T2_LIBRARIAN_TOWER,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base)
        );
        VillagerThornTower golem = new VillagerThornTower(
                VillagerTowers.ADV_T2_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.offset(1, 0, 0))
        );
        lane.addTower(librarian);
        lane.addTower(golem);
        librarian.setData(VillagerAdvStates.EXPERIENCE, 12.0);
        VillagerAdvStates.refreshTowerEffects(player, lane, librarian);
        VillagerAdvStates.refreshTowerEffects(player, lane, golem);

        lane.resetForRound();

        if (!assertClose(context, 102.5, librarian.modifyAttackDamage(null, null, 100.0), "ADV librarian survival damage bonus should be halved.")) {
            return;
        }
        if (!assertClose(context, VillagerTowers.ADV_T2_GOLEM_TOWER.maxHealth() * 1.05, golem.currentMaxHealth(), "ADV golem survival health bonus should be halved.")) {
            return;
        }
        if (!assertTrue(context, SemionDialogService.towerRuntimeDetailLines(librarian).stream().anyMatch(line -> line.contains("피해 +2.5%")), "ADV librarian runtime detail should show the reduced survival bonus.")) {
            return;
        }
        if (!assertTrue(context, SemionDialogService.towerRuntimeDetailLines(librarian).stream().anyMatch(line -> line.contains("경험치 12.0/100.0")), "ADV runtime detail should show current tower experience.")) {
            return;
        }
        if (!assertTrue(context, SemionDialogService.towerRuntimeDetailLines(golem).stream().anyMatch(line -> line.contains("체력 +5.0%")), "ADV golem runtime detail should show the reduced survival bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerAdvTimedEffectsExposeUiLabelsAndMaxHealthHealing(GameTestHelper context) {
        UUID playerId = stableUuid("villager-adv-effect-owner");
        BlockPos anchor = context.absolutePos(BlockPos.ZERO);
        TowerType type = new TowerType(
                "villager_adv_effect_probe",
                "Villager ADV Effect Probe",
                TowerCategory.DIRECT,
                0,
                100.0,
                4.0,
                10.0,
                20,
                0,
                List.of()
        );
        TestTower tower = new TestTower(type, playerId, TeamId.RED, 1, GridPosition.from(anchor));
        tower.syncHealth(50.0);
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        context.getLevel().addFreshEntity(entity);

        entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, 0.50, 72000);
        if (!assertClose(context, 150.0, tower.currentMaxHealth(), "Max health effect should update the runtime tower max health.")) {
            return;
        }
        if (!assertClose(context, 100.0, tower.health(), "Max health increase should heal by the added max-health delta.")) {
            return;
        }

        entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, 2.50, 72000);
        if (!assertClose(context, 2.50, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Timed effects should not clamp high config-driven magnitudes.")) {
            return;
        }

        entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_INCOME_DAMAGE_BONUS, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_WAVE_DAMAGE_BONUS, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_HEAL_AMOUNT_BONUS, 0.10, 72000);
        entity.applyTimedEffect(TimedEffectType.TOWER_ABILITY_INTERVAL_REDUCTION, 0.10, 72000);

        String body = timedEffectBody(entity);
        if (!assertTrue(context, body.contains("피해 증가") && body.contains("공속 증가") && body.contains("받피 감소"), "Base tower timed effects should remain visible.")) {
            return;
        }
        if (!assertTrue(context, body.contains("최대체력") && body.contains("인컴 피해") && body.contains("웨이브 피해"), "New ADV damage and max-health effects should be visible.")) {
            return;
        }
        if (!assertTrue(context, body.contains("회복량") && body.contains("주기 감소"), "New ADV allay effects should be visible.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerAdvLaneLeakLosesHalfReputationThroughRuntimeLane(GameTestHelper context) {
        UUID playerId = stableUuid("villager-adv-leak-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, VillagerAdvTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        SemionPlayer player = game.players().get(playerId);

        VillagerAdvStates.onWaveStarted(game, 1);
        if (!assertClose(context, 0.0, VillagerAdvStates.reputation(playerId), "Wave start should not grant reputation before the lane is defended.")) {
            return;
        }
        VillagerAdvStates.onWaveCleared(game, 1);
        if (!assertClose(context, 1.0, VillagerAdvStates.reputation(playerId), "A fully defended wave should grant reputation during payout.")) {
            return;
        }

        lane.resetForRound();
        VillagerAdvStates.onWaveStarted(game, 2);
        Monster monster = new Monster(
                "villager_adv_leak_probe",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                10.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        lane.enqueueSummonedMonster(monster);
        lane.tick(context.getLevel().getServer(), null, game.players());
        monster.syncLaneProgress(1.0);
        lane.tick(context.getLevel().getServer(), null, game.players());

        if (!assertTrue(context, player != null, "ADV owner should still exist.")) {
            return;
        }
        if (!assertClose(context, 0.5, VillagerAdvStates.reputation(playerId), "Any lane leak should subtract 0.5 reputation.")) {
            return;
        }
        VillagerAdvStates.onWaveCleared(game, 2);
        if (!assertClose(context, 0.5, VillagerAdvStates.reputation(playerId), "A leaked wave should not grant payout reputation.")) {
            return;
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        method.invoke(this, context);
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, ResourceLocation jobId) {
        VillagerAdvStates.clear(playerId);
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        if (!game.selectJob(playerId, jobId)) {
            throw new IllegalStateException("Failed to select job " + jobId);
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(playerId, "tester", TeamId.RED, 1)),
                Set.of(),
                1
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start Semion test game.");
        }
        return game;
    }

    private static PlayerLane redLane(SemionGame game, int laneId) {
        return game.teams().get(TeamId.RED).laneGroup().lane(laneId).orElseThrow();
    }

    private static BlockPos towerPlacementPos(PlayerLane lane) {
        return BlockPos.containing(lane.laneLayout().positionAt(0.35));
    }

    private static String timedEffectBody(SemionTowerEntity entity) {
        try {
            Method method = SemionDialogService.class.getDeclaredMethod("appendTowerTimedEffects", StringBuilder.class, SemionTowerEntity.class);
            method.setAccessible(true);
            StringBuilder body = new StringBuilder();
            method.invoke(null, body, entity);
            return body.toString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to render tower timed effects.", exception);
        }
    }

    private static String upgradeTooltipText(TowerUpgradeOption option, Tower tower) {
        try {
            Method method = SemionDialogService.class.getDeclaredMethod("upgradeTooltip", TowerUpgradeOption.class, boolean.class, boolean.class, Tower.class);
            method.setAccessible(true);
            return ((Component) method.invoke(null, option, true, false, tower)).getString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to render upgrade tooltip.", exception);
        }
    }

    private static void waitForAdvExperience(
            GameTestHelper context,
            SemionGame game,
            PlayerLane lane,
            GridPosition gridPosition,
            int waitedTicks,
            Runnable continuation
    ) {
        context.runAfterDelay(1, () -> {
            VillagerAdvStates.applyPending(game);
            Tower tower = lane.towerAt(gridPosition);
            if (tower != null && Math.abs(VillagerAdvStates.experience(tower) - 2.0) <= 0.01) {
                continuation.run();
                return;
            }
            if (waitedTicks >= 180) {
                double experience = tower == null ? 0.0 : VillagerAdvStates.experience(tower);
                context.fail(Component.literal("T1 tower should gain experiencePerTower + experiencePerTier asynchronously. Expected 2.0, got " + experience + "."));
                return;
            }
            waitForAdvExperience(context, game, lane, gridPosition, waitedTicks + 1, continuation);
        });
    }

    private static UUID stableUuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            context.fail(Component.literal(message + " Expected " + expected + ", got " + actual + "."));
            return false;
        }
        return true;
    }

    private static boolean assertClose(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.01) {
            context.fail(Component.literal(message + " Expected " + expected + ", got " + actual + "."));
            return false;
        }
        return true;
    }

    private static boolean assertAdvVanillaStats(GameTestHelper context, TowerBalanceConfig balance) {
        return assertStats(context, balance, VillagerTowers.ADV_T1_SPLASH_TOWER, 50, 40.0, 5.5, 5.0, 10, 0)
                && assertStats(context, balance, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, 110, 60.0, 7.0, 8.0, 10, 5)
                && assertStats(context, balance, VillagerTowers.ADV_T3_CLERIC_TOWER, 180, 80.0, 7.0, 10.0, 10, 10)
                && assertStats(context, balance, VillagerTowers.ADV_T1_GOLEM_TOWER, 50, 120.0, 2.0, 5.0, 20, 35)
                && assertStats(context, balance, VillagerTowers.ADV_T2_GOLEM_TOWER, 180, 200.0, 2.0, 8.0, 20, 50)
                && assertStats(context, balance, VillagerTowers.ADV_T3_GOLEM_TOWER, 350, 300.0, 3.0, 10.0, 20, 80)
                && assertStats(context, balance, VillagerTowers.ADV_T1_ALLAY_TOWER, 80, 40.0, 5.0, 2.0, 15, -5)
                && assertStats(context, balance, VillagerTowers.ADV_T2_ALLAY_TOWER, 200, 50.0, 5.0, 4.0, 15, -5)
                && assertStats(context, balance, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, 250, 50.0, 12.0, 5.0, 15, -5)
                && assertStats(context, balance, VillagerTowers.ADV_T3_ARMORER_TOWER, 300, 70.0, 7.0, 10.0, 15, -5)
                && assertStats(context, balance, VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER, 350, 60.0, 12.0, 7.0, 15, -5)
                && assertStats(context, balance, VillagerTowers.ADV_T1_CAT_TOWER, 60, 50.0, 10.0, 10.0, 15, 5)
                && assertStats(context, balance, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, 180, 50.0, 12.0, 20.0, 15, 5)
                && assertStats(context, balance, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, 200, 50.0, 10.0, 15.0, 15, 5)
                && assertStats(context, balance, VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER, 250, 50.0, 15.0, 25.0, 15, 5)
                && assertStats(context, balance, VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER, 275, 50.0, 10.0, 20.0, 10, 5)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_SPLASH_TOWER, "villager_splash_t2", 80)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, "villager_splash_t3", 150)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_GOLEM_TOWER, "t2_golem_tower", 100)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_GOLEM_TOWER, "t3_golem_tower", 200)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_allay_tower", 150)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_weapon_smith_tower", 180)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_ALLAY_TOWER, "t3_armorer_tower", 200)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", 200)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_CAT_TOWER, "t2_anti_tanker_cat_tower", 120)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T1_CAT_TOWER, "t2_lane_clear_cat_tower", 120)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", 210)
                && assertUpgradeCost(context, balance, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", 210)
                && assertEquals(context, 0.0015, balance.villagerAdv().buff(VillagerTowers.ADV_T1_SPLASH_TOWER.id(), "rangedDamagePerExperience"), "ADV ranged damage buff should be tower-scoped.")
                && assertEquals(context, 1.0, balance.villagerAdv().buffInterval(VillagerTowers.ADV_T1_SPLASH_TOWER.id(), "rangedDamagePerExperience"), "ADV ranged damage buff interval should be configurable per tower.")
                && assertEquals(context, 0.0, balance.villagerAdv().buff(VillagerTowers.ADV_T1_GOLEM_TOWER.id(), "rangedDamagePerExperience"), "ADV golem should not inherit ranged experience buffs.")
                && assertEquals(context, 0.0, balance.villagerAdv().buff(VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER.id(), "allayHealAmountPerExperience"), "ADV weapon smith should not inherit allay heal amount buffs.");
    }

    private static boolean assertStats(
            GameTestHelper context,
            TowerBalanceConfig balance,
            TowerType type,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority
    ) {
        TowerBalanceConfig.TowerStats stats = balance.towers().get(type.id());
        return assertTrue(context, stats != null, type.id() + " should have configured ADV stats.")
                && assertEquals(context, mineralCost, stats.mineralCost(), type.id() + " mineral cost")
                && assertEquals(context, maxHealth, stats.maxHealth(), type.id() + " max health")
                && assertEquals(context, range, stats.range(), type.id() + " range")
                && assertEquals(context, damage, stats.damage(), type.id() + " damage")
                && assertEquals(context, attackIntervalTicks, stats.attackIntervalTicks(), type.id() + " attack interval")
                && assertEquals(context, aggroPriority, stats.aggroPriority(), type.id() + " aggro priority");
    }

    private static boolean assertUpgradeCost(GameTestHelper context, TowerBalanceConfig balance, TowerType from, String upgradeId, long cost) {
        return assertEquals(context, cost, balance.upgradeCost(from.id(), upgradeId, -1), from.id() + "->" + upgradeId + " mineral cost");
    }
}
