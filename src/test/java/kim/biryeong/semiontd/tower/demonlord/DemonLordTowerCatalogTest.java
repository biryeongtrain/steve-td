package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DemonLordTowerCatalogTest {
    private static final double EPSILON = 0.0001;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetCatalogs() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        DemonLordStates.clearAllForTesting();
    }

    @Test
    void jobExposesEveryDemonLordTowerAndNothingElse() {
        DemonLordTowerJob job = new DemonLordTowerJob();
        assertEquals("semion-td:demon_lord_towers", job.id().toString());
        for (TowerType type : DemonLordTowers.all()) {
            assertTrue(job.canUseTower(null, type), "Job should allow " + type.id());
        }
        assertFalse(job.canUseTower(null, ProductionTowerCatalog.all().stream()
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(type -> !DemonLordTowers.isDemonLordTower(type))
                .findFirst()
                .orElseThrow()));
    }

    @Test
    void catalogHasFiveStartersAndFourTiersEach() {
        assertEquals(DemonLordSkill.values().length * DemonLordSkill.MAX_TIER, DemonLordTowers.all().size());
        long starters = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> DemonLordTowers.isDemonLordTower(entry.type()))
                .count();
        assertEquals(DemonLordSkill.values().length, starters, "Only tier 1 altars belong in the shop.");

        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier < DemonLordSkill.MAX_TIER; tier++) {
                TowerType from = DemonLordTowers.tower(skill, tier);
                TowerType to = DemonLordTowers.tower(skill, tier + 1);
                assertTrue(ProductionTowerCatalog.upgrade(from, to.id()).isPresent(),
                        skill + " T" + tier + " should upgrade into T" + (tier + 1));
            }
        }
    }

    /** 사용자가 지정한 코스트: 파동 3 / 날개 2 / 하늘 부수기 4 / 폭격 4 / 배리어 3. */
    @Test
    void skillCostsMatchTheDesignedValues() {
        assertEquals(3, DemonLordSkill.WAVE_OF_MALICE.slotCost());
        assertEquals(2, DemonLordSkill.DEMON_WINGS.slotCost());
        assertEquals(4, DemonLordSkill.SKY_BREAKER.slotCost());
        assertEquals(4, DemonLordSkill.ARCANE_BOMBARDMENT.slotCost());
        assertEquals(3, DemonLordSkill.DEMON_BARRIER.slotCost());

        // 코스트는 티어가 올라도 그대로입니다. 업그레이드는 다이아만 먹습니다.
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                assertEquals(skill.slotCost(), TowerCapacity.slotCost(DemonLordTowers.tower(skill, tier)),
                        skill + " T" + tier + " slot cost");
            }
        }
    }

    /** 전부 열면 코스트가 크게 넘칩니다. 초반 타워 한도로는 못 여는 값이어야 선택이 생깁니다. */
    @Test
    void openingEverySkillCostsMoreThanAnEarlyTowerLimit() {
        int total = 0;
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            total += skill.slotCost();
            assertTrue(skill.slotCost() >= 2 && skill.slotCost() <= 4, skill + " 코스트는 2~4 범위여야 합니다");
        }
        assertTrue(total >= 24, "총 코스트 " + total + " 는 초반 한도로 감당할 수 없어야 합니다");
    }

    /** 업글마다 쿨타임 -1초. 4티어면 -3초입니다. */
    @Test
    void cooldownDropsOneSecondPerTier() {
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                int expectedSeconds = skill.baseCooldownSeconds() - (tier - 1);
                assertEquals(expectedSeconds, skill.cooldownSecondsForTier(tier), skill + " T" + tier);
                assertEquals(expectedSeconds * 20, DemonLordTowers.cooldownTicks(DemonLordTowers.tower(skill, tier)),
                        skill + " T" + tier + " cooldown ticks");
            }
        }
        assertEquals(8, DemonLordSkill.WAVE_OF_MALICE.cooldownSecondsForTier(1));
        assertEquals(5, DemonLordSkill.WAVE_OF_MALICE.cooldownSecondsForTier(4));
        assertEquals(20, DemonLordSkill.DEMON_BARRIER.cooldownSecondsForTier(1));
        assertEquals(17, DemonLordSkill.DEMON_BARRIER.cooldownSecondsForTier(4));
    }

    /** 빌더의 정체성: 타워는 공격도 방어도 어그로도 없습니다. */
    @Test
    void everyAltarIsInertAndUnkillable() {
        for (TowerType type : DemonLordTowers.all()) {
            assertEquals(0.0, type.damage(), EPSILON, type.id() + " must not deal damage");
            assertEquals(0.0, type.range(), EPSILON, type.id() + " must have no range");
            assertEquals(0, type.aggroPriority(), type.id() + " must not draw aggro");

            Tower tower = ProductionTowerCatalog.find(type.id())
                    .orElseThrow()
                    .create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 0, 0));
            DemonLordSkillTower altar = assertInstanceOf(DemonLordSkillTower.class, tower);
            assertFalse(altar.canChaseTargets(), type.id() + " must not chase");
            assertFalse(altar.drawsAggro(), type.id() + " must not draw aggro");
            assertTrue(altar.invulnerable(), type.id() + " must be invulnerable");
            assertNotNull(altar.skill());
        }
    }

    /**
     * 바인딩은 1·2·3·4·5(우클릭)·F·Q 일곱 개이고, 먼저 지은 순서대로 배정됩니다.
     * 핫바를 쓰는 것끼리는 겹치면 안 되고, 마검 자리는 침범하면 안 됩니다.
     */
    @Test
    void bindingsCoverSevenKeysAndNeverTakeTheBladeSlot() {
        Set<Integer> slots = new HashSet<>();
        for (DemonLordBinding binding : DemonLordBinding.values()) {
            if (!binding.isHotbarSlot()) {
                continue;
            }
            assertTrue(slots.add(binding.hotbarSlot()), "Duplicate hotbar slot for " + binding);
            assertNotEquals(DemonLordSkill.BLADE_SLOT, binding.hotbarSlot(),
                    binding + " must not take the blade slot");
        }
        // 일곱 바인딩 모두 핫바에 자리를 갖습니다. 1~4 는 눌러서 쓰고, 나머지 셋은
        // 쿨타임을 보여 주기만 하는 자리입니다(마검 우클릭 / F / Q).
        assertEquals(7, slots.size(), "일곱 바인딩 모두 쿨타임을 보여 줄 자리가 있어야 합니다");
        assertEquals(7, DemonLordBinding.values().length);
        assertEquals("1", DemonLordBinding.forIndex(0).label());
        assertEquals("4", DemonLordBinding.forIndex(3).label());
        assertEquals(DemonLordBinding.RIGHT_CLICK, DemonLordBinding.forIndex(4));
        assertEquals("F", DemonLordBinding.forIndex(5).label());
        assertEquals("Q", DemonLordBinding.forIndex(6).label());
        assertNull(DemonLordBinding.forIndex(7), "여덟 번째 스킬은 누를 키가 없습니다");
        assertEquals(8, DemonLordSkill.BLADE_SLOT);

        assertNull(DemonLordBinding.forHotbarSlot(DemonLordSkill.BLADE_SLOT));
        assertEquals(DemonLordBinding.SLOT_1, DemonLordBinding.forHotbarSlot(0));
    }

    /**
     * 숫자 키 넷만 고르는 즉시 나갑니다.
     *
     * <p>나머지 셋(마검 우클릭·F·Q)은 다른 입력으로 쓰므로 자리는 쿨타임 표시 전용이고,
     * 집어 들어도 발동하면 안 됩니다. 쿨다운은 아이템 위에만 그려져서 자리가 없으면
     * 남은 시간을 알 방법이 아예 없습니다.
     */
    @Test
    void onlyTheNumberKeysCastOnSelect() {
        Set<DemonLordBinding> displayOnly = Set.of(
                DemonLordBinding.RIGHT_CLICK, DemonLordBinding.OFFHAND, DemonLordBinding.DROP);
        for (DemonLordBinding binding : DemonLordBinding.values()) {
            assertTrue(binding.isHotbarSlot(), binding + " 는 쿨타임을 보여 줄 자리가 필요합니다");
            if (displayOnly.contains(binding)) {
                assertFalse(binding.castOnSelect(), binding + " 는 고르는 것만으로 나가면 안 됩니다");
            } else {
                assertTrue(binding.castOnSelect(), binding + " 는 고르는 즉시 나가야 합니다");
            }
        }
    }

    /**
     * 스킬이 바인딩 수보다 많아야 "무엇을 들고 갈지" 고르는 재미가 생깁니다.
     *
     * <p>동시에, 키보다 많이 지으면 누를 수 없는 타워가 생기므로 배치 단계에서 막아야 합니다.
     * 그 방어는 {@code ProductionTowerService} 가 하고, 여기서는 전제를 못 박아 둡니다.
     */
    @Test
    void thereAreMoreSkillsThanKeysToBindThem() {
        assertTrue(DemonLordSkill.values().length > DemonLordBinding.values().length,
                "스킬 " + DemonLordSkill.values().length + "종 / 키 " + DemonLordBinding.values().length + "개");
        // 키 개수를 넘어서는 순번에는 바인딩이 없어야 하고, 그래서 배치를 막아야 합니다.
        assertNull(DemonLordBinding.forIndex(DemonLordBinding.values().length));
    }

    /** 광역만 있으면 단단한 하나를 끊을 수단이 없습니다. 단일 대상 기술이 있어야 합니다. */
    @Test
    void aSingleTargetSkillExists() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        String id = DemonLordSkill.GRIP_OF_DOOM.towerId(1);
        assertTrue(defaults.ability(id, "killRefundTicks", -1) > 0.0,
                "처형 시 쿨타임 환급이 있어야 연쇄가 됩니다");
        // 단일 대상이므로 광역 기술보다 한 방이 커야 합니다.
        assertTrue(defaults.ability(id, "damage", 0)
                        > defaults.ability(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "damage", 0),
                "단일 대상 피해가 광역보다 커야 합니다");
    }

    /**
     * 처형은 조건부여야 합니다.
     *
     * <p>임계값이 1.0 이면 체력과 무관하게 무조건 즉사라, 상대가 비싸게 산 유닛을 12초마다 대응
     * 없이 지울 수 있습니다. 티어가 올라도 100% 에는 닿지 않아야 합니다.
     */
    @Test
    void executeStaysConditionalAtEveryTier() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        double previous = 0.0;
        for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
            String id = DemonLordSkill.GRIP_OF_DOOM.towerId(tier);
            double ratio = defaults.ability(id, "executeHealthRatio", -1);
            assertTrue(ratio > 0.0 && ratio < 1.0, "T" + tier + " 처형 임계값이 " + ratio + " 입니다");
            assertTrue(ratio > previous, "T" + tier + " 임계값은 아래 티어보다 높아야 합니다");
            previous = ratio;

            assertTrue(defaults.ability(id, "explosionRadius", -1) > 0.0, "T" + tier + " 폭발 반경 누락");
            assertTrue(defaults.ability(id, "explosionHealthRatio", -1) > 0.0, "T" + tier + " 폭발 체력 비율 누락");
            assertTrue(defaults.ability(id, "areaDamage", -1) > 0.0, "T" + tier + " 폭발 고정 피해 누락");
        }
    }

    /** 스킬 하나만 열어도 150 다이아 안에서 시작할 수 있어야 합니다. */
    @Test
    void openingSkillIsAffordableOnTheStartingBudget() {
        long cheapest = DemonLordTowers.all().stream()
                .filter(type -> DemonLordTowers.tierOf(type) == 1)
                .mapToLong(TowerType::mineralCost)
                .min()
                .orElseThrow();
        assertTrue(cheapest <= 150, "Cheapest opening altar costs " + cheapest);

        long wingsAndWave = DemonLordTowers.tower(DemonLordSkill.DEMON_WINGS, 1).mineralCost()
                + DemonLordTowers.tower(DemonLordSkill.WAVE_OF_MALICE, 1).mineralCost();
        assertTrue(wingsAndWave <= 150, "Wings + wave opening costs " + wingsAndWave);
    }

    @Test
    void levellingRaisesHealthAndDamageTogether() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        assertEquals(1, state.level());
        assertEquals(450.0, state.maxHealth(), EPSILON);
        assertEquals(1.0, state.damageMultiplier(), EPSILON);

        state.enterCombat();
        int gained = state.addExperience(1000.0);
        assertTrue(gained > 0, "A large experience dump should level the demon lord up");
        assertTrue(state.maxHealth() > 450.0);
        assertTrue(state.damageMultiplier() > 1.0);
        assertTrue(state.level() <= state.maxLevel());
    }

    /** 레벨업으로 늘어난 체력은 즉시 채워져야 전투 중 레벨업이 의미가 있습니다. */
    @Test
    void levelUpGrantsTheNewHeadroomImmediately() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        double before = state.health();
        state.addExperience(state.experienceForNextLevel());
        assertEquals(2, state.level());
        assertTrue(state.health() > before, "Gained max health should be granted, not left empty");
        assertEquals(state.maxHealth(), state.health(), EPSILON);
    }

    @Test
    void barrierAbsorbsBeforeHealthAndExpires() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        double full = state.health();

        state.grantShield(100.0, 100L);
        assertFalse(state.applyDamage(60.0));
        assertEquals(40.0, state.shield(), EPSILON);
        assertEquals(full, state.health(), EPSILON, "Shield should soak the whole hit");

        state.expireShieldIfNeeded(100L);
        assertEquals(0.0, state.shield(), EPSILON);
        assertFalse(state.applyDamage(50.0));
        assertEquals(full - 50.0, state.health(), EPSILON);
    }

    @Test
    void emptyingThePoolLeavesCombatAndBlocksSkills() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        assertTrue(state.inCombat());

        assertTrue(state.applyDamage(state.maxHealth() + 1.0), "Overkill should report the knockout");
        state.leaveCombat();
        assertFalse(state.inCombat());
        assertEquals(0.0, state.health(), EPSILON);
        // 전투 제외 상태에서는 추가 피해가 들어가지 않습니다.
        assertFalse(state.applyDamage(10.0));
    }

    @Test
    void cooldownsBlockRecastingUntilTheyExpire() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        assertTrue(state.isSkillReady(DemonLordSkill.SKY_BREAKER, 0L));

        state.startCooldown(DemonLordSkill.SKY_BREAKER, 0L, 200);
        assertFalse(state.isSkillReady(DemonLordSkill.SKY_BREAKER, 199L));
        assertEquals(1, state.remainingCooldownTicks(DemonLordSkill.SKY_BREAKER, 199L));
        assertTrue(state.isSkillReady(DemonLordSkill.SKY_BREAKER, 200L));
        // 다른 스킬은 영향을 받지 않습니다.
        assertTrue(state.isSkillReady(DemonLordSkill.DEMON_WINGS, 0L));
    }

    /** 라운드가 새로 시작되면 전투 제외 상태에서도 부활합니다. */
    @Test
    void enteringCombatRevivesAndClearsCooldowns() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        state.startCooldown(DemonLordSkill.WAVE_OF_MALICE, 0L, 500);
        state.applyDamage(state.maxHealth());
        state.leaveCombat();

        state.enterCombat();
        assertTrue(state.inCombat());
        assertEquals(state.maxHealth(), state.health(), EPSILON);
        assertTrue(state.isSkillReady(DemonLordSkill.WAVE_OF_MALICE, 0L));
        assertTrue(state.consumePendingSpawn(), "Round start should queue the teleport to lane centre");
        assertFalse(state.consumePendingSpawn(), "The teleport request is one-shot");
    }

    /**
     * 웨이브를 살아서 끝낸 마왕은 쓰러진 것이 아닙니다.
     *
     * <p>라운드 끝에 전투를 풀지 않으면 다음 준비 단계까지 스킬 핫바가 남아 상점을 못 엽니다.
     * 그렇다고 {@code leaveCombat} 으로 풀면 체력이 0이 되어 쓰러진 것처럼 보입니다.
     */
    @Test
    void standingDownEndsCombatWithoutEmptyingThePool() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        state.applyDamage(state.maxHealth() * 0.25);
        double survived = state.health();
        assertTrue(survived > 0.0);

        state.standDown();
        assertFalse(state.inCombat(), "라운드가 끝나면 전투가 풀려야 준비 단계에서 상점을 엽니다");
        assertEquals(survived, state.health(), EPSILON, "살아서 내려온 체력은 그대로 둡니다");
        assertTrue(state.loadoutDirty(), "핫바를 비전투 구성으로 다시 깔아야 합니다");

        // 다음 웨이브에는 다시 만피로 들어갑니다.
        state.enterCombat();
        assertTrue(state.inCombat());
        assertEquals(state.maxHealth(), state.health(), EPSILON);
    }

    @Test
    void balanceConfigCarriesEverySkillNumber() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                String id = skill.towerId(tier);
                assertEquals(skill.slotCost(), (int) defaults.ability(id, TowerCapacity.CONFIG_KEY, -1),
                        id + " must publish its cost");
                assertTrue(defaults.ability(id, "cooldownTicks", -1) > 0, id + " must publish its cooldown");
            }
        }
        assertEquals(60.0, defaults.ability(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "coneDegrees", -1), EPSILON);
        assertEquals(0.25, defaults.ability(DemonLordSkill.DEMON_BARRIER.towerId(1), "shieldRatio", -1), EPSILON);
        assertEquals(0.50, defaults.ability(DemonLordSkill.DEMON_BARRIER.towerId(4), "shieldRatio", -1), EPSILON);
        assertEquals(450.0, defaults.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "baseMaxHealth", -1), EPSILON);
        assertEquals(52.5, defaults.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "maxHealthPerLevel", -1), EPSILON);
        assertEquals(19.0, defaults.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "bladeDamage", -1), EPSILON);
        assertEquals(34.0, defaults.ability(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "damage", -1), EPSILON);
        assertEquals(98.0, defaults.ability(DemonLordSkill.GRIP_OF_DOOM.towerId(1), "damage", -1), EPSILON);
        assertEquals(30.0, defaults.ability(DemonLordSkill.GRIP_OF_DOOM.towerId(1), "areaDamage", -1), EPSILON);
        assertFalse(defaults.abilities().get(DemonLordTowers.GLOBAL_CONFIG_ID).containsKey("bladeReach"));
    }

    /**
     * 번들 리소스 {@code tower_balance.json} 은 코드 기본값과 <b>병합되지 않고 통째로 대체</b>합니다.
     * Java 에만 값을 넣으면 런타임에서 업그레이드 비용이 0 이 되고 ability 가 폴백으로 떨어지는데,
     * 컴파일로는 절대 잡히지 않습니다. 두 곳이 어긋나면 여기서 깨집니다.
     */
    @Test
    void bundledResourceCarriesEveryDemonLordEntryThatCodeDefines() {
        TowerBalanceConfig code = TowerBalanceConfig.codeDefaults();
        TowerBalanceConfig bundled = TowerBalanceConfig.defaultConfig();

        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                String id = skill.towerId(tier);
                assertNotNull(bundled.towers().get(id), id + " missing from the bundled resource");
                assertEquals(code.ability(id, TowerCapacity.CONFIG_KEY, -1),
                        bundled.ability(id, TowerCapacity.CONFIG_KEY, -2), EPSILON,
                        id + " cost drifted between code and the bundled resource");
                assertEquals(code.ability(id, "cooldownTicks", -1),
                        bundled.ability(id, "cooldownTicks", -2), EPSILON,
                        id + " cooldown drifted between code and the bundled resource");
                assertEquals(code.abilities().get(id), bundled.abilities().get(id),
                        id + " abilities drifted between code and the bundled resource");

                if (tier < DemonLordSkill.MAX_TIER) {
                    String next = skill.towerId(tier + 1);
                    assertTrue(bundled.upgradeCosts().getOrDefault(id + "->" + next, 0L) > 0L,
                            id + " upgrade cost missing from the bundled resource");
                }
            }
        }
        assertEquals(code.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "baseMaxHealth", -1),
                bundled.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "baseMaxHealth", -2), EPSILON);
        assertEquals(code.abilities().get(DemonLordTowers.GLOBAL_CONFIG_ID),
                bundled.abilities().get(DemonLordTowers.GLOBAL_CONFIG_ID));
    }

    @Test
    void partialDemonLordConfigKeepsNewDefaults() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig merged = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(DemonLordTowers.GLOBAL_CONFIG_ID, Map.of("baseMaxHealth", 500.0))
        ).withMissingDefaults(defaults);

        assertEquals(500.0, merged.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "baseMaxHealth", -1), EPSILON);
        assertEquals(52.5, merged.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "maxHealthPerLevel", -1), EPSILON);
        assertEquals(19.0, merged.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "bladeDamage", -1), EPSILON);
        assertEquals(34.0, merged.ability(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "damage", -1), EPSILON);
    }

    @Test
    void invalidDemonLordRatiosTicksAndRangesAreRejected() {
        assertInvalidAbility(DemonLordTowers.GLOBAL_CONFIG_ID, "bladeAttackIntervalTicks", 1.5);
        assertInvalidAbility(DemonLordTowers.GLOBAL_CONFIG_ID, "experienceGrowth", 0.99);
        assertInvalidAbility(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "range", 0.0);
        assertInvalidAbility(DemonLordSkill.WAVE_OF_MALICE.towerId(1), "coneDegrees", 361.0);
        assertInvalidAbility(DemonLordSkill.DEMON_WINGS.towerId(1), "healRatio", 1.01);
        assertInvalidAbility(DemonLordSkill.SKY_BREAKER.towerId(1), "stunTicks", 1.5);
        assertInvalidAbility(DemonLordSkill.GRIP_OF_DOOM.towerId(1), "executeHealthRatio", 1.0);
    }

    @Test
    void kitItemsAreMarkedAndClearedFromEveryContainerSlot() {
        SimpleContainer inventory = new SimpleContainer(6);
        inventory.setItem(0, DemonLordKitItems.mark(new ItemStack(Items.NETHERITE_SWORD)));
        inventory.setItem(5, DemonLordKitItems.mark(new ItemStack(Items.BLAZE_POWDER)));
        inventory.setItem(2, new ItemStack(Items.DIAMOND));

        assertTrue(DemonLordKitItems.isKitItem(inventory.getItem(0)));
        DemonLordKitItems.clear(inventory);

        assertTrue(inventory.getItem(0).isEmpty());
        assertTrue(inventory.getItem(5).isEmpty());
        assertFalse(inventory.getItem(2).isEmpty());
    }

    @Test
    void fallbackDamageStatisticsKeepPhysicalAndMagicSeparate() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.recordDamageDealt(12.5, DamageType.PHYSICAL);
        state.recordDamageDealt(8.0, DamageType.MAGIC);

        assertEquals(12.5, state.roundPhysicalDamageDealt(), EPSILON);
        assertEquals(8.0, state.roundMagicDamageDealt(), EPSILON);
        state.enterCombat();
        assertEquals(0.0, state.roundPhysicalDamageDealt(), EPSILON);
        assertEquals(0.0, state.roundMagicDamageDealt(), EPSILON);
    }

    @Test
    void soulDrainHealingUsesActualDamageAndRespectsTheCap() {
        assertEquals(10.0, DemonLordSkills.soulDrainHealing(40.0, 450.0, 0.25, 0.12), EPSILON);
        assertEquals(54.0, DemonLordSkills.soulDrainHealing(1_000.0, 450.0, 0.25, 0.12), EPSILON);
    }

    /**
     * 툴팁의 {@code {ability.키}} 는 해당 타워의 설정에서 값을 찾지 못하면 게임에 그대로 노출됩니다.
     * 키를 오타 내도 컴파일은 통과하므로 여기서 막습니다.
     */
    @Test
    void everyTooltipPlaceholderResolvesToARealAbilityValue() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Pattern placeholder = Pattern.compile("\\{ability\\.([a-zA-Z_][a-zA-Z0-9_]*)(?::[a-zA-Z_]+)?\\}");
        double missing = -987654.0;

        for (TowerType type : DemonLordTowers.all()) {
            for (String line : type.description()) {
                Matcher matcher = placeholder.matcher(line);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    assertNotEquals(missing, defaults.ability(type.id(), key, missing),
                            type.id() + " tooltip references unknown ability '" + key + "'");
                }
            }
        }
    }

    /** 툴팁이 피해량과 범위를 실제로 보여 줘야 합니다. 쿨타임만 있으면 값을 가늠할 수 없습니다. */
    @Test
    void tooltipsShowDamageAndReach() {
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                String joined = String.join("\n", DemonLordTowers.tower(skill, tier).description());
                assertTrue(joined.contains("{ability.cooldownTicks:"), skill + " T" + tier + " must show its cooldown");
                if (skill == DemonLordSkill.DEMON_BARRIER) {
                    assertTrue(joined.contains("{ability.shieldRatio:"), "배리어는 방어막 비율을 보여야 합니다");
                    continue;
                }
                assertTrue(joined.contains("{ability.damage:"), skill + " T" + tier + " must show its damage");
                assertTrue(joined.contains("{ability.range:")
                                || joined.contains("{ability.radius:")
                                || joined.contains("{ability.zoneRadius:")
                                || joined.contains("{ability.dashDistance:")
                                || joined.contains("{ability.blastRadius:"),
                        skill + " T" + tier + " must show how far it reaches");
            }
        }
        // 범위를 가진 스킬은 그 범위도 표기합니다.
        assertTrue(String.join("", DemonLordTowers.tower(DemonLordSkill.WAVE_OF_MALICE, 1).description())
                .contains("{ability.range:"));
        assertTrue(String.join("", DemonLordTowers.tower(DemonLordSkill.DEMON_WINGS, 1).description())
                .contains("{ability.radius:"));
        assertTrue(String.join("", DemonLordTowers.tower(DemonLordSkill.SKY_BREAKER, 1).description())
                .contains("{ability.dashDistance:"));
        assertTrue(String.join("", DemonLordTowers.tower(DemonLordSkill.ARCANE_BOMBARDMENT, 1).description())
                .contains("{ability.blastRadius:"));
    }

    /** 스킬 피해는 티어가 오를수록 반드시 강해져야 업그레이드가 의미를 가집니다. */
    @Test
    void skillDamageGrowsWithEveryTier() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        List<DemonLordSkill> damaging = List.of(
                DemonLordSkill.WAVE_OF_MALICE,
                DemonLordSkill.DEMON_WINGS,
                DemonLordSkill.SKY_BREAKER,
                DemonLordSkill.ARCANE_BOMBARDMENT
        );
        for (DemonLordSkill skill : damaging) {
            double previous = 0.0;
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                double damage = defaults.ability(skill.towerId(tier), "damage", -1);
                assertTrue(damage > previous, skill + " T" + tier + " should out-damage the tier below");
                previous = damage;
            }
        }
    }

    /**
     * 레벨은 한 경기 동안 상태가 없어졌다 다시 생겨도 유지돼야 합니다.
     *
     * <p>상태는 전투 제외, 직업 변경, 플레이어를 못 찾는 레인 틱 등으로 여러 번 버려지고 다시
     * 만들어집니다. 새로 만든 상태는 레벨 1 이므로, 진행도를 따로 붙들지 않으면 그동안 쌓은
     * 성장이 통째로 날아갑니다. 레벨은 마왕의 유일한 성장 축이라 그러면 판이 끝납니다.
     */
    @Test
    void levelSurvivesStateTeardownWithinAMatch() {
        UUID player = UUID.randomUUID();
        DemonLordState state = DemonLordStates.getOrCreate(player);
        state.enterCombat();
        state.addExperience(500.0);
        int grownLevel = state.level();
        assertTrue(grownLevel > 1, "테스트 전제: 경험치를 넣으면 레벨이 올라야 합니다");

        DemonLordStates.clear(player);
        assertEquals(grownLevel, DemonLordStates.getOrCreate(player).level(),
                "상태가 다시 만들어져도 레벨은 유지돼야 합니다");

        // 새 경기는 잊습니다.
        DemonLordStates.clear(player);
        DemonLordStates.resetProgression(player);
        assertEquals(1, DemonLordStates.getOrCreate(player).level(),
                "새 경기는 레벨 1 부터 시작해야 합니다");
    }

    /** 다섯 번째 스킬은 마검 우클릭으로 나갑니다. 슬롯을 들고 있을 필요가 없습니다. */
    @Test
    void theRightClickSkillIsBoundToTheBladeNotItsOwnSlot() {
        assertFalse(DemonLordBinding.RIGHT_CLICK.castOnSelect(),
                "고르는 것만으로 나가면 조준할 수 없습니다");
        assertTrue(DemonLordBinding.RIGHT_CLICK.isHotbarSlot(),
                "쿨타임을 보여 주려면 핫바에 자리가 있어야 합니다");
        assertTrue(DemonLordBinding.OFFHAND.isHotbarSlot() && DemonLordBinding.DROP.isHotbarSlot(),
                "F 와 Q 도 쿨타임을 볼 자리가 있어야 합니다");
        assertNotEquals(DemonLordSkill.BLADE_SLOT, DemonLordBinding.RIGHT_CLICK.hotbarSlot(),
                "표시 슬롯과 마검 자리는 달라야 합니다");
        assertTrue(DemonLordBinding.RIGHT_CLICK.label().contains("우클릭"),
                "라벨이 조작 방법을 알려야 합니다: " + DemonLordBinding.RIGHT_CLICK.label());
    }

    /** 레벨업마다 포인트를 받고, 찍은 만큼 실제 능력치가 움직여야 합니다. */
    @Test
    void levellingGrantsPointsAndSpendingThemChangesTheNumbers() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        assertEquals(0, state.unspentPoints(), "레벨 1 은 아직 받을 포인트가 없습니다");

        int gained = state.addExperience(500.0);
        assertTrue(gained > 0);
        int perLevel = (int) TowerBalanceRuntime.ability(
                DemonLordTowers.GLOBAL_CONFIG_ID, "statPointsPerLevel", 3.0);
        assertTrue(perLevel > 1, "레벨당 여러 포인트를 줘야 스탯 하나하나가 체감됩니다");
        assertEquals(gained * perLevel, state.unspentPoints(), "레벨업마다 정해진 수만큼 받아야 합니다");

        double beforeHealth = state.maxHealth();
        double beforeDamage = state.damageMultiplier();
        assertTrue(state.allocate(DemonLordStat.MAX_HEALTH));
        assertTrue(state.allocate(DemonLordStat.ATTACK));
        assertTrue(state.maxHealth() > beforeHealth, "체력 포인트가 최대 체력을 올려야 합니다");
        assertTrue(state.damageMultiplier() > beforeDamage, "공격력 포인트가 피해 배율을 올려야 합니다");
        assertEquals(gained * perLevel - 2, state.unspentPoints());
    }

    /** 포인트를 아무리 쌓아도 쿨타임 0 과 피해 감소 100% 에는 닿으면 안 됩니다. */
    @Test
    void cooldownAndDefenceApproachTheirFloorWithoutCrossingIt() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        state.addExperience(1.0E9);

        assertEquals(1.0, state.cooldownMultiplier(), EPSILON, "찍기 전에는 100% 그대로여야 합니다");
        // 기본값은 40 포인트마다 절반입니다. 다른 스탯보다 비싼 것은 의도된 것으로,
        // 쿨감은 모든 스킬에 한꺼번에 곱해져 같은 효율이면 다른 선택지가 없어집니다.
        for (int i = 0; i < 40; i++) {
            assertTrue(state.allocate(DemonLordStat.COOLDOWN));
        }
        assertEquals(0.5, state.cooldownMultiplier(), EPSILON, "40 포인트면 절반");
        for (int i = 0; i < 40; i++) {
            assertTrue(state.allocate(DemonLordStat.COOLDOWN));
        }
        assertEquals(0.25, state.cooldownMultiplier(), EPSILON, "80 포인트면 4분의 1");

        for (int i = 0; i < 200 && state.unspentPoints() > 0; i++) {
            state.allocate(DemonLordStat.COOLDOWN);
            state.allocate(DemonLordStat.DEFENSE);
        }
        assertTrue(state.cooldownMultiplier() > 0.0, "쿨타임이 0 이 되면 스킬을 무한히 씁니다");
        assertTrue(state.damageReduction() < 1.0, "피해 감소가 100% 가 되면 죽지 않습니다");
    }

    /** 몹을 못 잡아도 라운드를 넘기면 조금은 자라야 합니다. */
    @Test
    void roundsGrantPassiveExperience() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertTrue(defaults.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "passiveExperiencePerRound", -1) > 0.0,
                "라운드 경과 경험치가 설정에 있어야 합니다");
        assertTrue(defaults.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "statPointsPerLevel", -1) > 0.0,
                "레벨업 보상 포인트가 설정에 있어야 합니다");
    }

    private static void assertInvalidAbility(String configId, String key, double value) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> changed = new LinkedHashMap<>(abilities.get(configId));
        changed.put(key, value);
        abilities.put(configId, changed);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities);
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }
}
