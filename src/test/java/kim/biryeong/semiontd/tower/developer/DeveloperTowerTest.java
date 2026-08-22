package kim.biryeong.semiontd.tower.developer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the parts of the 개발자 builder that can go wrong silently: the patch arithmetic, the
 * rules that decide whether a defect may attach at all, and the assumption that tower state
 * survives an upgrade because {@code Tower.copyFrom} copies the typed data map.
 */
class DeveloperTowerTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("developer-test-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        DeveloperStates.clearAll();
    }

    private static DeveloperTower tower(TowerType type) {
        return new DeveloperTower(type, OWNER, TeamId.RED, 0, new GridPosition(0, 64, 0));
    }

    // ------------------------------------------------------------------ 카탈로그

    @Test
    void everyDeveloperTowerIsRegisteredAndOwnedByExactlyOneBuilder() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertEquals(12, DeveloperTowers.all().size());
        for (TowerType type : DeveloperTowers.all()) {
            assertTrue(
                    ProductionTowerCatalog.find(type.id()).isPresent(),
                    type.id() + " 이(가) 카탈로그에 등록되어야 합니다."
            );
        }

        Set<String> ids = new HashSet<>();
        for (TowerType type : DeveloperTowers.all()) {
            assertTrue(ids.add(type.id()), type.id() + " 이(가) 중복 등록되었습니다.");
        }
    }

    @Test
    void bothSecondTierTowersReachBothThirdTierTowersForTheSameTotal() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        long viaBeta = DeveloperTowers.ALPHA.mineralCost()
                + TowerBalanceRuntime.upgradeCost(DeveloperTowers.ALPHA, DeveloperTowers.BETA.id())
                + TowerBalanceRuntime.upgradeCost(DeveloperTowers.BETA, DeveloperTowers.RELEASE.id());
        long viaTestBuild = DeveloperTowers.ALPHA.mineralCost()
                + TowerBalanceRuntime.upgradeCost(DeveloperTowers.ALPHA, DeveloperTowers.TEST_BUILD.id())
                + TowerBalanceRuntime.upgradeCost(DeveloperTowers.TEST_BUILD, DeveloperTowers.RELEASE.id());

        assertEquals(viaBeta, viaTestBuild, "어느 T2를 거쳐도 정식판 누적 비용이 같아야 합니다.");
        assertEquals(290L, viaBeta);
    }

    // ------------------------------------------------------------------ 능력 타워

    @Test
    void abilityTowersCostNoSlotAndNeverFight() {
        DeveloperTower workbench = tower(DeveloperTowers.WORKBENCH);

        assertEquals(0, workbench.slotWeight(), "능력 타워는 타워 슬롯을 먹지 않아야 합니다.");
        assertEquals(0.0, workbench.adjustAttackRange(6.0), 1.0e-9);
        assertFalse(workbench.countsForLaneDefense(), "능력 타워만 남은 라인은 방어로 인정되면 안 됩니다.");
    }

    @Test
    void growthTowersTakeOneSlotAndTwoAfterTheSlotOptimization() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);
        assertEquals(1, alpha.slotWeight());

        DeveloperTowerData.addOptimization(alpha, DeveloperOptimization.SLOT);
        assertEquals(2, alpha.slotWeight());
    }

    // ------------------------------------------------------------------ 패치

    @Test
    void reviewedPatchesWaitForTheNextWaveAndHotfixesLandImmediately() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);

        DeveloperTowerData.addPendingPatch(alpha, DeveloperPatch.ATTACK, 0.1);
        assertEquals(0.0, DeveloperTowerData.activeAmount(alpha, DeveloperPatch.ATTACK), 1.0e-9);
        assertEquals(0.1, DeveloperTowerData.pendingAmount(alpha, DeveloperPatch.ATTACK), 1.0e-9);

        assertTrue(DeveloperTowerData.promotePendingPatches(alpha));
        assertEquals(0.1, DeveloperTowerData.activeAmount(alpha, DeveloperPatch.ATTACK), 1.0e-9);
        assertEquals(0.0, DeveloperTowerData.pendingAmount(alpha, DeveloperPatch.ATTACK), 1.0e-9);
    }

    @Test
    void repeatedPatchesOfTheSameKindDiminish() {
        double first = DeveloperPatch.ATTACK.stepAmount(0);
        double fifth = DeveloperPatch.ATTACK.stepAmount(4);

        assertTrue(fifth < first, "같은 종류의 패치는 반복할수록 약해져야 합니다.");
        assertEquals(
                DeveloperBalance.PATCH_ATTACK * Math.pow(DeveloperBalance.PATCH_DIMINISHING, 4),
                fifth,
                1.0e-9
        );
    }

    /**
     * 연사 must saturate, never invert.
     *
     * <p>The interval used to be computed as {@code base * (1 - accumulated)}. Because the
     * accumulated value converges on — and with the shipped rates exceeds — 1.0, that produced a
     * negative interval which the engine clamped to a single tick, i.e. a sixteenfold damage spike
     * for a player who simply kept picking the same patch. Dividing instead approaches a finite
     * ceiling.
     */
    @Test
    void stackingFireRateApproachesACeilingInsteadOfInverting() {
        DeveloperTower release = tower(DeveloperTowers.RELEASE);
        int baseInterval = DeveloperTowers.RELEASE.attackIntervalTicks();

        int previous = Integer.MAX_VALUE;
        for (int applied = 0; applied < 80; applied++) {
            DeveloperTowerData.addActivePatch(release, DeveloperPatch.FIRE_RATE,
                    DeveloperPatch.FIRE_RATE.stepAmount(applied)
                            * DeveloperBalance.patchScale(DeveloperTowers.RELEASE));
            int interval = release.adjustAttackInterval(baseInterval);
            assertTrue(interval >= 1, "간격이 1틱 미만으로 내려가면 안 됩니다.");
            assertTrue(interval <= previous, "연사 패치는 간격을 늘리면 안 됩니다.");
            previous = interval;
        }

        // 점근선은 1 + 0.12/(1-0.88) * 1.25 = 2.25배, 즉 16t -> 약 7t.
        assertTrue(previous >= baseInterval / 3,
                "연사만 쌓아도 간격이 기본의 1/3 아래로 떨어지면 안 됩니다. 실제=" + previous);
        assertTrue(previous < baseInterval,
                "연사 패치가 아무 효과도 없으면 안 됩니다. 실제=" + previous);
    }

    @Test
    void aggroPatchesAreFlatBecauseTheScaleIsBounded() {
        assertEquals(DeveloperPatch.AGGRO.stepAmount(0), DeveloperPatch.AGGRO.stepAmount(9), 1.0e-9);
        assertTrue(DeveloperPatch.AGGRO.isFlat());
    }

    @Test
    void tierScalesRunTheOtherWayFromTheBugChances() {
        assertTrue(
                DeveloperBalance.patchScale(DeveloperTowers.RELEASE)
                        > DeveloperBalance.patchScale(DeveloperTowers.ALPHA),
                "정식판이 알파보다 패치를 잘 받아야 승급 압력이 생깁니다."
        );
        assertTrue(
                DeveloperBalance.bugChance(DeveloperTowers.ALPHA)
                        > DeveloperBalance.bugChance(DeveloperTowers.RELEASE),
                "알파가 버그 채굴장 역할을 하려면 버그 확률이 가장 높아야 합니다."
        );
        assertEquals(0.0, DeveloperBalance.bugChance(DeveloperTowers.RELEASE), 1.0e-9);
    }

    @Test
    void releaseResistsHotfixesAndLtsWelcomesThem() {
        assertTrue(DeveloperBalance.hotfixScale(DeveloperTowers.RELEASE) < 1.0);
        assertTrue(DeveloperBalance.hotfixScale(DeveloperTowers.LTS) > 1.0);
    }

    // ------------------------------------------------------------------ 불안정

    @Test
    void ltsNeverAccumulatesInstability() {
        DeveloperTower lts = tower(DeveloperTowers.LTS);
        DeveloperTowerData.addInstability(lts, 3);
        assertEquals(0, DeveloperTowerData.instability(lts));

        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addInstability(beta, 3);
        assertEquals(3, DeveloperTowerData.instability(beta));
    }

    @Test
    void instabilityStopsAtTheConfiguredCeiling() {
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        for (int index = 0; index < 20; index++) {
            DeveloperTowerData.addInstability(beta, 1);
        }
        assertEquals(DeveloperBalance.maxInstability(), DeveloperTowerData.instability(beta));
    }

    @Test
    void maintenanceClearsInstabilityAndSchedulesThePayoff() {
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addInstability(beta, 3);

        DeveloperTowerData.scheduleMaintenance(beta, 7);

        assertEquals(0, DeveloperTowerData.instability(beta));
        assertTrue(DeveloperTowerData.underMaintenance(beta, 7));
        assertTrue(DeveloperTowerData.hasMaintenanceBonus(beta, 8));
        assertFalse(DeveloperTowerData.hasMaintenanceBonus(beta, 9));
    }

    // ------------------------------------------------------------------ 버그

    @Test
    void aTowerCarriesOnlySoManyBugs() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);
        int max = DeveloperBalance.maxBugsPerTower();

        int added = 0;
        for (DeveloperBug bug : DeveloperBug.values()) {
            if (DeveloperTowerData.addBug(alpha, bug)) {
                added++;
            }
        }

        assertEquals(max, added);
        assertEquals(max, DeveloperTowerData.bugs(alpha).size());
    }

    @Test
    void readOnlyLocksTheTowerOutOfOptimization() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);
        DeveloperTowerData.addBug(alpha, DeveloperBug.READ_ONLY);

        assertFalse(DeveloperTowerData.addOptimization(alpha, DeveloperOptimization.RANGE));
        assertTrue(DeveloperTowerData.optimizations(alpha).isEmpty());
    }

    @Test
    void everyBugIsDistinctAndTunable() {
        Set<String> keys = new HashSet<>();
        for (DeveloperBug bug : DeveloperBug.values()) {
            assertTrue(keys.add(bug.key()), bug.key() + " 키가 중복입니다.");
            assertFalse(bug.description().isEmpty(), bug.key() + " 설명이 비어 있습니다.");
        }
        assertEquals(25, DeveloperBug.values().length);
    }

    @Test
    void bothAggroBugsAreFlaggedAsRiskyToSpread() {
        assertTrue(DeveloperBug.STEALTH.dangerousToSpread());
        assertTrue(DeveloperBug.AGGRO_STORM.dangerousToSpread());
        assertFalse(DeveloperBug.PRIMITIVE.dangerousToSpread());
    }

    @Test
    void stealthStartsActiveDropsAfterDamageAndReturnsAfterSixtyTicks() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);
        DeveloperTowerData.addBug(alpha, DeveloperBug.STEALTH);
        double baseDamage = 100.0;
        double boosted = baseDamage * (1.0 + DeveloperBug.STEALTH.secondary());

        assertEquals(boosted, alpha.modifyAttackDamage(null, null, baseDamage), 1.0e-9,
                "아직 피해를 받지 않은 초기 상태도 은신 보너스가 활성화되어야 합니다.");

        alpha.onDamaged(null, null, 1.0, alpha.health(), alpha.health() - 1.0);
        assertEquals(baseDamage, alpha.modifyAttackDamage(null, null, baseDamage), 1.0e-9);
        for (int tick = 0; tick < 59; tick++) {
            alpha.tick(null);
        }
        assertEquals(baseDamage, alpha.modifyAttackDamage(null, null, baseDamage), 1.0e-9);
        alpha.tick(null);
        assertEquals(boosted, alpha.modifyAttackDamage(null, null, baseDamage), 1.0e-9);
    }

    // ------------------------------------------------------------------ 최적화

    @Test
    void durabilityAndAttackAreFlaggedAsCancellingOut() {
        assertTrue(DeveloperOptimization.DURABILITY.conflictsWith(DeveloperOptimization.ATTACK));
        assertTrue(DeveloperOptimization.ATTACK.conflictsWith(DeveloperOptimization.DURABILITY));
        assertFalse(DeveloperOptimization.RANGE.conflictsWith(DeveloperOptimization.ATTACK));
    }

    @Test
    void attackOptimizationTradesDamageForABiggerHealthPool() {
        DeveloperTower release = tower(DeveloperTowers.RELEASE);
        double baseHealth = release.effectBaseMaxHealth();

        DeveloperTowerData.addOptimization(release, DeveloperOptimization.ATTACK);

        assertTrue(release.effectBaseMaxHealth() > baseHealth, "공격 포기는 체력을 크게 올려야 합니다.");
        assertTrue(DeveloperOptimization.ATTACK.costMultiplier() < 1.0, "공격력은 감소해야 합니다.");
        assertTrue(release.aggroPriority() > DeveloperTowers.RELEASE.aggroPriority());
    }

    // ------------------------------------------------------------------ 승급

    @Test
    void patchesAndBugsSurviveAnUpgrade() {
        DeveloperTower alpha = tower(DeveloperTowers.ALPHA);
        DeveloperTowerData.addActivePatch(alpha, DeveloperPatch.ATTACK, 0.25);
        DeveloperTowerData.addBug(alpha, DeveloperBug.PRIMITIVE);
        DeveloperTowerData.addOptimization(alpha, DeveloperOptimization.RANGE);

        DeveloperTower beta = tower(DeveloperTowers.BETA);
        beta.copyFrom(alpha, 0L);

        assertEquals(0.25, DeveloperTowerData.activeAmount(beta, DeveloperPatch.ATTACK), 1.0e-9);
        assertTrue(beta.hasBug(DeveloperBug.PRIMITIVE));
        assertTrue(beta.hasOptimization(DeveloperOptimization.RANGE));
    }

    @Test
    void pinnedTowersStayPinnedThroughAnUpgrade() {
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.setPinned(beta, true);

        DeveloperTower release = tower(DeveloperTowers.RELEASE);
        release.copyFrom(beta, 0L);

        assertTrue(DeveloperTowerData.isPinned(release));
    }

    // ------------------------------------------------------------------ 가비지 컬렉션

    @Test
    void garbageCollectionTakesFromTheLargestPile() {
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.ATTACK, 0.10);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.ATTACK, 0.09);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.RANGE, 0.09);
        double rangeBefore = DeveloperTowerData.activeAmount(beta, DeveloperPatch.RANGE);

        assertTrue(DeveloperTowerData.dropOneActivePatch(beta));

        assertEquals(1, DeveloperTowerData.activeCount(beta, DeveloperPatch.ATTACK));
        assertEquals(rangeBefore, DeveloperTowerData.activeAmount(beta, DeveloperPatch.RANGE), 1.0e-9);
        assertNotEquals(0.19, DeveloperTowerData.activeAmount(beta, DeveloperPatch.ATTACK), 1.0e-9);
    }

    @Test
    void garbageCollectionNeedsAPatchAndOnlyRecoversOncePerWave() {
        DeveloperTower withoutPatch = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(withoutPatch, DeveloperBug.GARBAGE_COLLECTION);
        withoutPatch.onWaveStarted(null, 1);
        withoutPatch.syncHealth(30.0);
        assertEquals(10.0, withoutPatch.modifyIncomingDamage(null, null, 10.0), 1.0e-9,
                "활성 패치가 없으면 발동하면 안 됩니다.");

        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(beta, DeveloperBug.GARBAGE_COLLECTION);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.HEALTH, 0.20);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.HEALTH, 0.10);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.ATTACK, 0.10);
        beta.syncMaxHealth(beta.effectBaseMaxHealth(), false);
        beta.onWaveStarted(null, 1);
        beta.syncHealth(beta.currentMaxHealth() * 0.30);

        assertEquals(0.0, beta.modifyIncomingDamage(null, null, beta.currentMaxHealth() * 0.10), 1.0e-9);
        assertEquals(beta.currentMaxHealth(), beta.health(), 1.0e-9,
                "패치 제거 후 변경된 최대 체력까지 회복해야 합니다.");
        int patchesAfterRecovery = DeveloperTowerData.activeCount(beta, DeveloperPatch.HEALTH)
                + DeveloperTowerData.activeCount(beta, DeveloperPatch.ATTACK);

        beta.syncHealth(beta.currentMaxHealth() * 0.20);
        assertTrue(beta.modifyIncomingDamage(null, null, beta.currentMaxHealth()) > 0.0,
                "한 웨이브에 두 번 발동하면 안 됩니다.");
        assertEquals(patchesAfterRecovery,
                DeveloperTowerData.activeCount(beta, DeveloperPatch.HEALTH)
                        + DeveloperTowerData.activeCount(beta, DeveloperPatch.ATTACK));
    }

    @Test
    void garbageCollectionCanCatchLethalDamage() {
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(beta, DeveloperBug.GARBAGE_COLLECTION);
        DeveloperTowerData.addActivePatch(beta, DeveloperPatch.ATTACK, 0.10);
        beta.onWaveStarted(null, 1);

        assertEquals(0.0, beta.modifyIncomingDamage(null, null, beta.currentMaxHealth() * 10.0), 1.0e-9);
        assertEquals(beta.currentMaxHealth(), beta.health(), 1.0e-9);
        assertEquals(0, DeveloperTowerData.activeCount(beta, DeveloperPatch.ATTACK));
    }

    // ------------------------------------------------------------------ 밸런스 배선

    /**
     * The bundled resource is the real source of balance defaults.
     *
     * <p>{@code defaultConfig()} loads {@code balance-defaults/tower_balance.json} <em>verbatim</em>
     * and does not merge the code-side map into it, so a family added only in Java silently runs
     * with zero upgrade costs and missing abilities. This test fails the moment the two drift.
     */
    @Test
    void bundledResourceCarriesEverythingTheCodeDefaultsDeclare() {
        TowerBalanceConfig code = TowerBalanceConfig.codeDefaults();
        TowerBalanceConfig bundled = TowerBalanceConfig.defaultConfig();

        for (TowerType type : DeveloperTowers.all()) {
            assertTrue(
                    bundled.towers().containsKey(type.id()),
                    type.id() + " 이(가) 번들 tower_balance.json 에 없습니다."
            );
            assertEquals(
                    code.towers().get(type.id()),
                    bundled.towers().get(type.id()),
                    type.id() + " 스탯이 Java 기본값과 번들 JSON 사이에서 어긋났습니다."
            );
        }

        for (Map.Entry<String, Long> entry : code.upgradeCosts().entrySet()) {
            if (!entry.getKey().startsWith("developer_")) {
                continue;
            }
            assertEquals(
                    entry.getValue(),
                    bundled.upgradeCosts().get(entry.getKey()),
                    entry.getKey() + " 승급 비용이 번들 JSON 과 어긋났습니다."
            );
        }

        for (Map.Entry<String, Map<String, Double>> family : code.abilities().entrySet()) {
            if (!family.getKey().startsWith("developer_")) {
                continue;
            }
            Map<String, Double> bundledValues = bundled.abilities().get(family.getKey());
            assertTrue(bundledValues != null, family.getKey() + " 능력 값이 번들 JSON 에 없습니다.");
            for (Map.Entry<String, Double> value : family.getValue().entrySet()) {
                assertEquals(
                        value.getValue(),
                        bundledValues.get(value.getKey()),
                        1.0e-9,
                        family.getKey() + "." + value.getKey() + " 가 번들 JSON 과 어긋났습니다."
                );
            }
        }
    }

    @Test
    void defaultConfigCarriesEveryDeveloperTowerAndTunable() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(config);

        for (TowerType type : DeveloperTowers.all()) {
            assertNotEquals(
                    0L,
                    TowerBalanceRuntime.resolve(type).mineralCost(),
                    type.id() + " 이(가) 밸런스 설정에 없습니다."
            );
        }
        for (DeveloperBug bug : DeveloperBug.values()) {
            assertEquals(
                    bug.defaultPrimary(),
                    TowerBalanceRuntime.ability(DeveloperBalance.CONFIG_ID, bug.primaryKey(), Double.NaN),
                    1.0e-9,
                    bug.key() + " 기본값이 설정에 없습니다."
            );
        }
        for (DeveloperOptimization optimization : DeveloperOptimization.values()) {
            assertEquals(
                    optimization.defaultGain(),
                    TowerBalanceRuntime.ability(DeveloperBalance.CONFIG_ID, optimization.gainKey(), Double.NaN),
                    1.0e-9,
                    optimization.key() + " 기본값이 설정에 없습니다."
            );
        }
    }
}
