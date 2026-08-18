package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.HeroPartyTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class HeroPartyTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("hero-party-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetState() {
        HeroPartyStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void registersTheHeroJobAndCompleteUpgradeGraph() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        HeroPartyTowerJob job = (HeroPartyTowerJob) JobRegistry.find(HeroPartyTowerJob.ID).orElseThrow();

        assertEquals("용사 빌더", job.displayName().getString());
        assertEquals(3, job.description().size());
        assertEquals(List.of("시작", "운영", "주의"), job.description().stream()
                .map(line -> line.getString().split(" ", 2)[0])
                .toList());
        assertEquals(25, HeroPartyTowers.all().size());
        assertEquals(25, ProductionTowerCatalog.all().stream()
                .filter(entry -> job.includesTowerInCatalog(entry.type()))
                .count());
        assertEquals("용사 타워", HeroPlayerVisuals.displayProfileName(HeroPartyTowers.HERO));
        assertEquals("견습 사제", HeroPartyTowers.companion(HeroCompanionRole.PRIEST, 1).displayName());
        assertEquals("중견 사제", HeroPartyTowers.companion(HeroCompanionRole.PRIEST, 2).displayName());
        assertEquals("베테랑 사제", HeroPartyTowers.companion(HeroCompanionRole.PRIEST, 3).displayName());
        assertEquals("대사제", HeroPartyTowers.companion(HeroCompanionRole.PRIEST, 4).displayName());
        assertEquals("견습 사제 타워", HeroPlayerVisuals.displayProfileName(
                HeroPartyTowers.companion(HeroCompanionRole.PRIEST, 1)
        ));

        Set<String> ids = HeroPartyTowers.all().stream().map(type -> type.id()).collect(Collectors.toSet());
        assertTrue(ids.contains(HeroPartyTowers.HERO_ID));
        assertEquals(6, java.util.Arrays.stream(HeroCompanionRole.values())
                .map(HeroPlayerVisuals::companionProfileId)
                .mapToInt(uuid -> Math.floorMod(uuid.hashCode(), 18))
                .distinct()
                .count());
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier <= 4; tier++) {
                String id = "hero_party_" + role.id() + "_" + tier;
                assertTrue(ids.contains(id));
                assertEquals(tier == 1, ProductionTowerCatalog.find(id).orElseThrow().starter());
                if (tier < 4) {
                    String nextId = "hero_party_" + role.id() + "_" + (tier + 1);
                    assertEquals(nextId, ProductionTowerCatalog.upgrade(
                            HeroPartyTowers.companion(role, tier),
                            nextId
                    ).orElseThrow().targetType().id());
                }
            }
        }
    }

    @Test
    void correctsFakePlayerCombatPitchDownward() {
        assertEquals(19.5F, HeroPlayerVisuals.correctedPitch(-18.0F, true));
        assertEquals(-18.0F, HeroPlayerVisuals.correctedPitch(-18.0F, false));
        assertEquals(90.0F, HeroPlayerVisuals.correctedPitch(80.0F, true));
    }

    @Test
    void weightedCapacityUsesThreeForHeroAndTwoThroughFiveForCompanions() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        TestContext context = testContext();
        Tower hero = hero(context, new GridPosition(1, 64, 1));
        Tower knight = companion(context, HeroCompanionRole.KNIGHT, 1, new GridPosition(2, 64, 1));

        assertEquals(3, TowerCapacity.slotCost(HeroPartyTowers.HERO));
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier <= 4; tier++) {
                assertEquals(tier + 1, TowerCapacity.slotCost(HeroPartyTowers.companion(role, tier)));
            }
        }
        assertEquals(1, TowerCapacity.slotCost(AnimalTowers.T1_PIG_TOWER));

        context.lane().addTower(hero);
        context.lane().addTower(knight);
        assertEquals(2, context.game().towerCount(OWNER));
        assertEquals(5, context.game().towerCapacityUsed(OWNER));
        assertFalse(context.game().canFitTower(OWNER, HeroPartyTowers.companion(HeroCompanionRole.ARCHER, 1)));
        assertFalse(context.game().canFitUpgrade(
                OWNER,
                HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1),
                HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2)
        ));

        context.player().economy().addEmerald(context.game().economyConfig().towerLimit().initialPurchaseEmeraldCost());
        assertTrue(context.player().economy().purchaseTowerLimit(context.game().economyConfig().towerLimit()));
        assertTrue(context.game().canFitUpgrade(
                OWNER,
                HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1),
                HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2)
        ));
        Tower normalTower = ProductionTowerCatalog.find(AnimalTowers.T1_PIG_TOWER.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(3, 64, 1));
        context.lane().addTower(normalTower);
        context.lane().addTower(ProductionTowerCatalog.find(AnimalTowers.T1_PIG_TOWER.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(4, 64, 1)));
        assertTrue(context.game().towerCapacityUsed(OWNER) > context.game().towerLimitForPlayer(OWNER));
        assertTrue(context.game().canFitUpgrade(OWNER, normalTower.type(), normalTower.type()));

        PlayerLane finalPartyLane = testLane();
        finalPartyLane.addTower(hero(context, new GridPosition(1, 64, 2)));
        int x = 2;
        for (HeroCompanionRole role : List.of(
                HeroCompanionRole.KNIGHT,
                HeroCompanionRole.ARCHER,
                HeroCompanionRole.MAGE,
                HeroCompanionRole.PRIEST
        )) {
            finalPartyLane.addTower(companion(context, role, 4, new GridPosition(x++, 64, 2)));
        }
        assertEquals(23, finalPartyLane.towers().stream().mapToInt(tower -> TowerCapacity.slotCost(tower.type())).sum());
    }

    @Test
    void heroGateAndCompanionCommitmentsSurviveSalesButResetForTheNextMatch() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        TestContext context = testContext();
        HeroPartyTowerJob job = new HeroPartyTowerJob();
        HeroPartyState state = HeroPartyStates.state(OWNER);

        assertEquals(Set.of(HeroPartyTowers.HERO_ID), availableIds(context.game()));
        Tower hero = hero(context, new GridPosition(1, 64, 1));
        context.lane().addTower(hero);
        assertEquals(6, availableIds(context.game()).size());

        ArrayList<Tower> companions = new ArrayList<>();
        for (HeroCompanionRole role : List.of(
                HeroCompanionRole.KNIGHT,
                HeroCompanionRole.ARCHER,
                HeroCompanionRole.MAGE,
                HeroCompanionRole.PRIEST
        )) {
            Tower companion = companion(context, role, 1, new GridPosition(companions.size() + 2, 64, 1));
            companions.add(companion);
            context.lane().addTower(companion);
        }
        assertEquals(4, state.committedCompanions().size());
        assertFalse(job.canUseTower(new JobContext(context.game(), context.player()), HeroPartyTowers.companion(HeroCompanionRole.ROGUE, 1)));
        assertFalse(job.canUseTower(new JobContext(context.game(), context.player()), HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1)));

        companions.forEach(context.lane()::removeTower);
        assertEquals(Set.of(
                "hero_party_knight_1",
                "hero_party_archer_1",
                "hero_party_mage_1",
                "hero_party_priest_1"
        ), availableIds(context.game()));

        context.lane().removeTower(hero);
        assertEquals(Set.of(HeroPartyTowers.HERO_ID), availableIds(context.game()));
        assertTrue(job.canUseTower(new JobContext(context.game(), context.player()), HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2)));

        context.lane().addTower(hero(context, new GridPosition(1, 64, 1)));
        assertEquals(4, state.committedCompanions().size());
        job.onMatchStarted(new JobContext(context.game(), context.player()));
        assertTrue(HeroPartyStates.state(OWNER).committedCompanions().isEmpty());
    }

    @Test
    void equipmentAndQuestNumbersMatchTheApprovedProgression() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        HeroPartyState state = HeroPartyStates.state(OWNER);
        assertTrue(state.owns(HeroWeapon.SWORD));
        assertTrue(state.addWeapon(HeroWeapon.LONGBOW));
        for (int level = 1; level <= 5; level++) {
            assertTrue(state.upgradeWeapon(HeroWeapon.LONGBOW));
            assertEquals(level, state.weaponLevel(HeroWeapon.LONGBOW));
        }
        assertFalse(state.upgradeWeapon(HeroWeapon.LONGBOW));
        assertTrue(state.equip(HeroWeapon.LONGBOW));
        for (int level = 1; level <= 5; level++) {
            assertTrue(state.upgradeArmor());
        }
        assertFalse(state.upgradeArmor());
        assertEquals(HeroWeapon.LONGBOW, state.equippedWeapon());
        assertTrue(state.owns(HeroWeapon.SWORD));
        assertTrue(state.owns(HeroWeapon.LONGBOW));
        HeroPartyTower hero = (HeroPartyTower) hero(testContext(), new GridPosition(1, 64, 1));
        assertEquals(5, HeroPlayerVisuals.displayedArmorLevel(hero));
        assertFalse(state.toggleArmorVisibility());
        assertEquals(0, HeroPlayerVisuals.displayedArmorLevel(hero));
        assertTrue(state.toggleArmorVisibility());
        assertEquals(5, HeroPlayerVisuals.displayedArmorLevel(hero));

        assertEquals(5, HeroPartyState.questReward(1));
        assertEquals(10, HeroPartyState.questReward(5));
        assertEquals(15, HeroPartyState.questReward(10));
        assertEquals(21, HeroPartyState.questReward(20));
        assertEquals(24, HeroPartyState.questReward(41));
        assertEquals(2.0, HeroPartyState.questTarget(HeroQuestKind.WEAPON_KILLS, 1, 8, 1000, 500));
        assertEquals(3.0, HeroPartyState.questTarget(HeroQuestKind.WEAPON_KILLS, 20, 10, 1000, 500));
        assertEquals(5.0, HeroPartyState.questTarget(HeroQuestKind.HERO_KILLS, 20, 20, 1000, 500));
        assertEquals(175.0, HeroPartyState.questTarget(HeroQuestKind.WEAPON_DAMAGE, 20, 10, 1000, 500));
        assertEquals(175.0, HeroPartyState.questTarget(HeroQuestKind.LONGBOW_MARK_DAMAGE, 20, 10, 1000, 500));
        assertEquals(245.0, HeroPartyState.questTarget(HeroQuestKind.TOME_HEALING, 20, 10, 1000, 500));
        assertEquals(20, HeroQuestKind.values().length);
        assertEquals(1.0, HeroPartyState.questTarget(HeroQuestKind.PARTY_SURVIVAL, 20, 10, 1000, 500));
        assertEquals(4.0, HeroPartyState.questTarget(HeroQuestKind.COMPANION_KILLS, 20, 10, 1000, 500));
        assertEquals(280.0, HeroPartyState.questTarget(HeroQuestKind.PARTY_DAMAGE, 20, 10, 1000, 500));
        assertEquals(245.0, HeroPartyState.questTarget(HeroQuestKind.PRIEST_HEALING, 20, 10, 1000, 500));
        assertEquals(10.0, HeroPartyState.questTarget(HeroQuestKind.BARD_AURA_SUPPORT, 1, 1, 100, 100));
        assertEquals(10.0, HeroPartyState.questTarget(HeroQuestKind.BARD_AURA_SUPPORT, 1, 100, 10000, 100));
        assertEquals(35.0, HeroPartyState.questTarget(HeroQuestKind.BARD_AURA_SUPPORT, 20, 1, 100, 100));
        assertEquals(35.0, HeroPartyState.questTarget(HeroQuestKind.BARD_AURA_SUPPORT, 20, 100, 10000, 100));
        assertEquals(1.30, HeroPartyBalance.partyDamageMultiplier(100), 0.0001);
        assertEquals(1.30, HeroPartyBalance.partyHealingMultiplier(100), 0.0001);
        assertEquals(1.45, HeroPartyBalance.partyHealthMultiplier(100), 0.0001);
    }

    @Test
    void weaponProfilesPreserveHealthRatioAndScaleArmor() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        HeroPartyState state = HeroPartyStates.state(OWNER);
        assertTrue(state.upgradeArmor());
        HeroTower hero = (HeroTower) hero(testContext(), new GridPosition(1, 64, 1));
        hero.refreshPartyStats(null);

        assertEquals(275.0, hero.currentMaxHealth(), 0.0001);
        assertEquals(60, hero.aggroPriority());
        hero.syncHealth(137.5);

        assertTrue(state.addWeapon(HeroWeapon.LONGBOW));
        assertTrue(state.equip(HeroWeapon.LONGBOW));
        hero.refreshPartyStats(null);

        assertEquals(187.0, hero.currentMaxHealth(), 0.0001);
        assertEquals(93.5, hero.health(), 0.0001);
        assertEquals(5, hero.aggroPriority());
        assertEquals(11, hero.adjustAttackInterval(hero.type().attackIntervalTicks()));
        for (int level = 1; level <= HeroPartyBalance.MAX_WEAPON_LEVEL; level++) {
            assertTrue(state.upgradeWeapon(HeroWeapon.LONGBOW));
            assertEquals(11 - level, hero.adjustAttackInterval(hero.type().attackIntervalTicks()));
        }

        hero.syncEffectMaxHealth(hero.effectBaseMaxHealth() * 1.50, 0.0, false);
        assertEquals(280.5, hero.currentMaxHealth(), 0.0001);
        hero.syncEffectMaxHealth(hero.effectBaseMaxHealth(), 0.0, false);
        assertEquals(187.0, hero.currentMaxHealth(), 0.0001);
    }

    @Test
    void weaponBalanceDefaultsMergeValidateAndMatchBundledConfig() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        defaults.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(defaults);

        Map<HeroWeapon, Double> healthMultipliers = Map.of(
                HeroWeapon.SWORD, 1.25,
                HeroWeapon.GREATSWORD, 1.15,
                HeroWeapon.LONGBOW, 0.85,
                HeroWeapon.STAFF, 0.90,
                HeroWeapon.TOME, 1.05
        );
        Map<HeroWeapon, Integer> aggroPriorities = Map.of(
                HeroWeapon.SWORD, 60,
                HeroWeapon.GREATSWORD, 40,
                HeroWeapon.LONGBOW, 5,
                HeroWeapon.STAFF, 0,
                HeroWeapon.TOME, -10
        );
        for (HeroWeapon weapon : HeroWeapon.values()) {
            assertEquals(healthMultipliers.get(weapon), HeroPartyBalance.weaponMaxHealthMultiplier(weapon), 0.0001);
            assertEquals(aggroPriorities.get(weapon), HeroPartyBalance.weaponAggroPriority(weapon));
            for (int level = 0; level <= HeroPartyBalance.MAX_WEAPON_LEVEL; level++) {
                assertEquals(Math.max(1, weapon.defaultAttackIntervalTicks() - level),
                        HeroPartyBalance.weaponAttackInterval(weapon, level));
            }
        }

        TowerBalanceConfig merged = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(
                        HeroPartyBalance.GLOBAL_CONFIG_ID,
                        Map.of("weaponAttackIntervalReductionPerLevel", 2.0),
                        HeroWeapon.SWORD.configId(),
                        Map.of("maxHealthMultiplier", 1.40, "aggroPriority", 55.0)
                )
        ).withMissingDefaults(defaults);
        merged.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(1.40, HeroPartyBalance.weaponMaxHealthMultiplier(HeroWeapon.SWORD), 0.0001);
        assertEquals(55, HeroPartyBalance.weaponAggroPriority(HeroWeapon.SWORD));
        assertEquals(10, HeroPartyBalance.weaponAttackInterval(HeroWeapon.SWORD, 1));
        assertEquals(0.35, merged.ability(HeroWeapon.SWORD.configId(), "incomeDamageBonus", -1.0), 0.0001);

        assertInvalidAbility(defaults, HeroWeapon.SWORD.configId(), "maxHealthMultiplier", 0.0);
        assertInvalidAbility(defaults, HeroWeapon.TOME.configId(), "aggroPriority", -101.0);
        assertInvalidAbility(defaults, HeroWeapon.TOME.configId(), "aggroPriority", 1.5);
        assertInvalidAbility(defaults, HeroPartyBalance.GLOBAL_CONFIG_ID,
                "weaponAttackIntervalReductionPerLevel", 0.0);
        assertInvalidAbility(defaults, HeroPartyBalance.GLOBAL_CONFIG_ID,
                "weaponAttackIntervalReductionPerLevel", 1.5);

        try (var input = HeroPartyTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var bundledAbilities = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("abilities");
            for (HeroWeapon weapon : HeroWeapon.values()) {
                var bundled = bundledAbilities.getAsJsonObject(weapon.configId());
                Map<String, Double> javaDefaults = defaults.abilities().get(weapon.configId());
                assertEquals(javaDefaults.keySet(), bundled.keySet(), weapon.configId());
                javaDefaults.forEach((key, value) -> assertEquals(
                        value, bundled.get(key).getAsDouble(), 0.0001, weapon.configId() + "." + key
                ));
            }
        }
    }

    @Test
    void focusFireDefenseUsesTheConfiguredPerAttackerReductionAndCap() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(0.08, defaults.ability(
                HeroPartyBalance.GLOBAL_CONFIG_ID, "focusFireDamageReductionPerExtraAttacker", -1), 0.0001);
        assertEquals(0.40, defaults.ability(
                HeroPartyBalance.GLOBAL_CONFIG_ID, "focusFireDamageReductionCap", -1), 0.0001);
        assertEquals(0.0, HeroPartyTower.focusFireDamageReduction(1), 0.0001);
        assertEquals(0.08, HeroPartyTower.focusFireDamageReduction(2), 0.0001);
        assertEquals(0.24, HeroPartyTower.focusFireDamageReduction(4), 0.0001);
        assertEquals(0.40, HeroPartyTower.focusFireDamageReduction(6), 0.0001);
        assertEquals(0.40, HeroPartyTower.focusFireDamageReduction(100), 0.0001);

        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(HeroPartyBalance.GLOBAL_CONFIG_ID, Map.of(
                        "focusFireDamageReductionPerExtraAttacker", 0.10
                ))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(0.10, merged.ability(
                HeroPartyBalance.GLOBAL_CONFIG_ID, "focusFireDamageReductionPerExtraAttacker", -1), 0.0001);
        assertEquals(0.40, merged.ability(
                HeroPartyBalance.GLOBAL_CONFIG_ID, "focusFireDamageReductionCap", -1), 0.0001);
        assertInvalidFocusConfig(defaults, "focusFireDamageReductionCap", 1.0);
        assertInvalidFocusConfig(defaults, "focusFireDamageReductionPerExtraAttacker", 0.50);

        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        try (var input = HeroPartyTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var bundled = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("abilities")
                    .getAsJsonObject(HeroPartyBalance.GLOBAL_CONFIG_ID);
            Map<String, Double> javaDefaults = defaults.abilities().get(HeroPartyBalance.GLOBAL_CONFIG_ID);
            assertEquals(javaDefaults.keySet(), bundled.keySet());
            javaDefaults.forEach((key, value) -> assertEquals(value, bundled.get(key).getAsDouble(), 0.0001, key));
        }
    }

    @Test
    void swordLongbowAndArchersReceiveConfigurableIncomeDamage() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        ProductionTowerCatalogs.reloadBuiltIns(defaults);

        assertEquals(0.35, HeroPartyBalance.weaponIncomeDamageBonus(HeroWeapon.SWORD), 0.0001);
        assertEquals(0.35, HeroPartyBalance.weaponIncomeDamageBonus(HeroWeapon.LONGBOW), 0.0001);
        assertEquals(0.0, HeroPartyBalance.weaponIncomeDamageBonus(HeroWeapon.STAFF), 0.0001);
        for (int tier = 1; tier <= 4; tier++) {
            assertEquals(0.35, defaults.ability(
                    HeroPartyTowers.companion(HeroCompanionRole.ARCHER, tier).id(),
                    "incomeDamageBonus",
                    -1.0
            ), 0.0001);
        }

        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(HeroWeapon.SWORD.configId(), Map.of("incomeDamageBonus", 0.20))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(0.20, merged.ability(HeroWeapon.SWORD.configId(), "incomeDamageBonus", -1.0), 0.0001);
        assertEquals(0.35, merged.ability(HeroWeapon.LONGBOW.configId(), "incomeDamageBonus", -1.0), 0.0001);

        LinkedHashMap<String, Map<String, Double>> invalidAbilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalidSword = new LinkedHashMap<>(invalidAbilities.get(HeroWeapon.SWORD.configId()));
        invalidSword.put("incomeDamageBonus", 1.01);
        invalidAbilities.put(HeroWeapon.SWORD.configId(), invalidSword);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), invalidAbilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    @Test
    void companionPricesDamageAndTierAbilitiesMatchTheApprovedProgression() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<HeroCompanionRole, long[]> expectedCosts = Map.of(
                HeroCompanionRole.KNIGHT, new long[]{84, 126, 196, 308},
                HeroCompanionRole.ARCHER, new long[]{77, 119, 182, 280},
                HeroCompanionRole.MAGE, new long[]{98, 147, 231, 364},
                HeroCompanionRole.PRIEST, new long[]{91, 140, 217, 336},
                HeroCompanionRole.ROGUE, new long[]{70, 105, 168, 259},
                HeroCompanionRole.BARD, new long[]{84, 126, 196, 308}
        );
        Map<HeroCompanionRole, double[]> expectedDamage = Map.of(
                HeroCompanionRole.KNIGHT, new double[]{7.2, 10.8, 15.6, 21.6},
                HeroCompanionRole.ARCHER, new double[]{14.4, 20.4, 28.8, 38.4},
                HeroCompanionRole.MAGE, new double[]{19.2, 26.4, 37.2, 50.4},
                HeroCompanionRole.PRIEST, new double[]{4.8, 7.2, 12.0, 16.8},
                HeroCompanionRole.ROGUE, new double[]{12.0, 16.8, 22.8, 31.2},
                HeroCompanionRole.BARD, new double[]{4.8, 8.4, 12.0, 16.8}
        );
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier <= 4; tier++) {
                var type = HeroPartyTowers.companion(role, tier);
                assertEquals(expectedCosts.get(role)[tier - 1], defaults.statsFor(type).mineralCost());
                assertEquals(expectedDamage.get(role)[tier - 1], defaults.statsFor(type).damage(), 0.0001);
                if (tier < 4) {
                    var next = HeroPartyTowers.companion(role, tier + 1);
                    assertEquals(expectedCosts.get(role)[tier], defaults.upgradeCost(type.id(), next.id(), -1));
                }
            }
        }
        assertEquals(112, defaults.statsFor(HeroPartyTowers.HERO).mineralCost());
        assertEquals(List.of(15, 13, 10, 7),
                java.util.stream.IntStream.rangeClosed(1, 4)
                        .mapToObj(tier -> defaults.statsFor(
                                HeroPartyTowers.companion(HeroCompanionRole.ARCHER, tier)
                        ).attackIntervalTicks())
                        .toList());
        assertEquals(List.of(126L, 210L, 322L, 476L, 672L),
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(HeroPartyBalance::armorUpgradeCost).toList());
        assertEquals(List.of(80L, 140L, 220L, 320L, 450L),
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(HeroPartyBalance::weaponUpgradeCost).toList());

        assertEquals(0.0, defaults.ability("hero_party_knight_1", "shieldBashEvery", -1.0), 0.0001);
        assertEquals(4.0, defaults.ability("hero_party_knight_2", "shieldBashEvery", -1.0), 0.0001);
        assertEquals(0.08, defaults.ability("hero_party_knight_3", "guardDamageReduction", -1.0), 0.0001);
        assertEquals(0.75, defaults.ability("hero_party_archer_4", "pierceDamageRatio", -1.0), 0.0001);
        assertEquals(1.50, defaults.ability("hero_party_mage_3", "empoweredSplashMultiplier", -1.0), 0.0001);
        assertEquals(0.50, defaults.ability("hero_party_priest_3", "secondTargetRatio", -1.0), 0.0001);
        assertEquals(90.0, defaults.ability("hero_party_priest_4", "healAmount", -1.0), 0.0001);
        assertEquals(0.20, defaults.ability("hero_party_rogue_3", "killAttackSpeedBonus", -1.0), 0.0001);
        assertEquals(4.0, defaults.ability("hero_party_bard_4", "encoreEveryPulses", -1.0), 0.0001);

        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of("hero_party_knight_2", Map.of("shieldBashSlow", 0.30))
        ).withMissingDefaults(defaults);
        assertEquals(0.30, partial.ability("hero_party_knight_2", "shieldBashSlow", -1.0), 0.0001);
        assertEquals(4.0, partial.ability("hero_party_knight_2", "shieldBashEvery", -1.0), 0.0001);

        assertInvalidAbility(defaults, "hero_party_archer_2", "pierceEvery", 3.5);
        assertInvalidAbility(defaults, "hero_party_archer_4", "pierceDamageRatio", 0.50);
        assertInvalidAbility(defaults, "hero_party_bard_4", "attackSpeedBonus", 0.12);
    }

    @Test
    void companionDescriptionsExposeUnlocksWithoutUnresolvedText() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        try (var input = HeroPartyTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var bundledAbilities = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("abilities");
            for (HeroCompanionRole role : HeroCompanionRole.values()) {
                for (int tier = 1; tier <= 4; tier++) {
                    var type = TowerBalanceRuntime.resolve(HeroPartyTowers.companion(role, tier));
                    List<String> description = TowerDescriptionRegistry.describe(type).orElseThrow();
                    assertEquals(2, description.size());
                    assertTrue(description.stream().noneMatch(line -> line.contains("{")), type.id());
                    if (tier == 1) {
                        assertTrue(description.get(1).contains(HeroPartyTowers.firstAbilityName(role)));
                        assertTrue(description.get(1).contains(HeroPartyTowers.secondAbilityName(role)));
                    } else if (tier < 4) {
                        String expected = tier == 2
                                ? HeroPartyTowers.firstAbilityName(role)
                                : HeroPartyTowers.secondAbilityName(role);
                        assertTrue(description.get(1).contains("새 능력: " + expected));
                    } else {
                        assertTrue(description.get(1).contains("강화"));
                    }

                    var bundled = bundledAbilities.getAsJsonObject(type.id());
                    Map<String, Double> javaDefaults = defaults.abilities().get(type.id());
                    assertEquals(javaDefaults.keySet(), bundled.keySet(), type.id());
                    javaDefaults.forEach((key, value) -> assertEquals(
                            value, bundled.get(key).getAsDouble(), 0.0001, type.id() + "." + key
                    ));
                }
            }
        }
    }

    @Test
    void questPoolOnlyAddsCompanionQuestsThatThePartyCanComplete() {
        TestContext context = testContext();
        HeroPartyState state = HeroPartyStates.state(OWNER);
        List<WaveMonsterEntry> normalWave = List.of(new WaveMonsterEntry(
                "normal",
                100.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                10
        ));
        List<HeroQuestKind> soloPool = state.eligibleQuestKinds(context.game(), normalWave);
        assertTrue(soloPool.containsAll(List.of(
                HeroQuestKind.HERO_KILLS,
                HeroQuestKind.PARTY_DAMAGE,
                HeroQuestKind.HERO_SURVIVAL,
                HeroQuestKind.PARTY_SURVIVAL
        )));
        assertFalse(soloPool.contains(HeroQuestKind.COMPANION_KILLS));
        assertFalse(soloPool.contains(HeroQuestKind.ARCHER_BOSS_DAMAGE));

        int x = 1;
        context.lane().addTower(companion(context, HeroCompanionRole.KNIGHT, 2, new GridPosition(x++, 64, 1)));
        for (HeroCompanionRole role : List.of(
                HeroCompanionRole.ARCHER,
                HeroCompanionRole.MAGE,
                HeroCompanionRole.PRIEST,
                HeroCompanionRole.ROGUE,
                HeroCompanionRole.BARD
        )) {
            context.lane().addTower(companion(context, role, 1, new GridPosition(x++, 64, 1)));
        }
        List<WaveMonsterEntry> bossWave = List.of(new WaveMonsterEntry(
                "test_boss",
                1000.0,
                0.0,
                10.0,
                AttackKind.MELEE,
                "minecraft:warden",
                null,
                1
        ));
        List<HeroQuestKind> partyPool = state.eligibleQuestKinds(context.game(), bossWave);
        assertTrue(partyPool.containsAll(List.of(
                HeroQuestKind.COMPANION_KILLS,
                HeroQuestKind.KNIGHT_GUARD,
                HeroQuestKind.ARCHER_BOSS_DAMAGE,
                HeroQuestKind.MAGE_SPLASH_HITS,
                HeroQuestKind.PRIEST_HEALING,
                HeroQuestKind.ROGUE_EXECUTE_HITS,
                HeroQuestKind.BARD_AURA_SUPPORT
        )));
    }

    @Test
    void heroShopShowsEffectiveDamageAndEverySkillUnlock() {
        assertEquals(12.0, HeroShopGui.effectiveWeaponDamage(HeroWeapon.SWORD, 0, 0));
        assertEquals(24.288, HeroShopGui.effectiveWeaponDamage(HeroWeapon.SWORD, 5, 4), 0.0001);
        assertEquals(HeroShopGui.WeaponStatus.EQUIPPED,
                HeroShopGui.weaponStatus(true, true, true, 0, 0));
        assertEquals(HeroShopGui.WeaponStatus.OWNED,
                HeroShopGui.weaponStatus(true, false, true, 0, 0));
        assertEquals(HeroShopGui.WeaponStatus.PURCHASABLE,
                HeroShopGui.weaponStatus(false, false, true, 100, 100));
        assertEquals(HeroShopGui.WeaponStatus.UNAFFORDABLE,
                HeroShopGui.weaponStatus(false, false, true, 99, 100));
        assertEquals(HeroShopGui.WeaponStatus.READ_ONLY,
                HeroShopGui.weaponStatus(false, false, false, 100, 100));
        for (HeroWeapon weapon : HeroWeapon.values()) {
            assertFalse(HeroShopGui.skillDescription(weapon, 1).isBlank());
            assertFalse(HeroShopGui.skillDescription(weapon, 3).isBlank());
            assertFalse(HeroShopGui.skillDescription(weapon, 5).isBlank());
            assertTrue(HeroShopGui.skillDescription(weapon, 2).isBlank());
        }
    }

    private static Set<String> availableIds(SemionGame game) {
        return ProductionTowerService.availableTowers(game, OWNER).stream()
                .map(entry -> entry.type().id())
                .collect(Collectors.toSet());
    }

    private static Tower hero(TestContext context, GridPosition position) {
        return ProductionTowerCatalog.find(HeroPartyTowers.HERO_ID).orElseThrow()
                .create(OWNER, TeamId.RED, 1, position);
    }

    private static Tower companion(TestContext context, HeroCompanionRole role, int tier, GridPosition position) {
        return ProductionTowerCatalog.find(HeroPartyTowers.companion(role, tier).id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, position);
    }

    private static TestContext testContext() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(OWNER, "hero", TeamId.RED, 1, new PlayerEconomy(economy));
        player.assignJob(new HeroPartyTowerJob());
        game.players().put(OWNER, player);
        game.teams().get(TeamId.RED).activate();
        PlayerLane lane = testLane();
        game.teams().get(TeamId.RED).laneGroup().addLane(lane);
        return new TestContext(game, player, lane);
    }

    private static void assertInvalidFocusConfig(TowerBalanceConfig defaults, String key, double value) {
        assertInvalidAbility(defaults, HeroPartyBalance.GLOBAL_CONFIG_ID, key, value);
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults,
            String configId,
            String key,
            double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> configured = new LinkedHashMap<>(abilities.get(configId));
        configured.put(key, value);
        abilities.put(configId, configured);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    private static PlayerLane testLane() {
        Vec3 spawn = new Vec3(0.5, 64.0, 0.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(new Vec3(0.5, 64.0, 4.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(8, 66, 10)),
                List.of(new GridPosition(0, 63, 10))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }

    private record TestContext(SemionGame game, SemionPlayer player, PlayerLane lane) {
    }
}
