package kim.biryeong.semiontd.tower.developer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Covers the rules that decide whether an operation is allowed at all, and what it costs.
 *
 * <p>{@link DeveloperTowerTest} drives {@link DeveloperTowerData} directly, which means it never
 * exercises a single gate: budgets, the 롤백 실패 lockout, the 읽기 전용 lockout, pinning, or the
 * defect roll. Those live in {@link DeveloperPatchService} and are the part a player actually bumps
 * into every round.
 *
 * <p>The lane is passed as {@code null} throughout. Every path that needs one — the 테스트 빌드 aura
 * and the entity resync — is null-safe, so the gates can be tested without standing up a world.
 */
class DeveloperPatchServiceTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("developer-service-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetState() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        DeveloperStates.clearAll();
    }

    private static DeveloperTower tower(TowerType type) {
        return tower(type, OWNER, 0, 0);
    }

    private static DeveloperTower tower(TowerType type, UUID owner, int laneId, int x) {
        DeveloperTower tower = new DeveloperTower(type, owner, TeamId.RED, laneId, new GridPosition(x, 64, 0));
        tower.useRandom(RandomSource.create(1234L));
        return tower;
    }

    private static PlayerLane lane(DeveloperTower... towers) {
        LaneRegionLayout layout = new LaneRegionLayout(
                0,
                new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(0.5, 64.0, 4.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(12, 66, 12)),
                List.of(new GridPosition(0, 63, 10))
        );
        PlayerLane lane = new PlayerLane(TeamId.RED, 0, OWNER, null, layout);
        for (DeveloperTower tower : towers) {
            lane.addTower(tower);
        }
        return lane;
    }

    /** Seeds the budgets an ability tower line would have granted. */
    private static void grant(int patchSlots, int hotfixes, boolean maintenance, boolean debugger, boolean developer) {
        DeveloperStates.openRound(OWNER, 1, new DeveloperStates.Capacity(
                patchSlots,
                hotfixes,
                maintenance,
                debugger || developer,
                debugger || developer ? DeveloperBalance.debugRemovalsPerRound() : 0,
                developer,
                developer ? DeveloperBalance.versionPinSlots() : 0
        ));
    }

    // ------------------------------------------------------------------ 정식 패치

    @Test
    void aReviewedPatchSpendsASlotAndWaitsForTheNextWave() {
        grant(2, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);

        DeveloperPatchService.Result result =
                DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, false);

        assertTrue(result.success(), result.message());
        assertEquals(1, DeveloperStates.of(OWNER).patchesRemaining());
        assertTrue(DeveloperTowerData.pendingAmount(beta, DeveloperPatch.ATTACK) > 0.0);
        assertEquals(0.0, DeveloperTowerData.activeAmount(beta, DeveloperPatch.ATTACK), 1.0e-9);
    }

    @Test
    void withoutAWorkbenchThereIsNothingToSpend() {
        grant(0, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);

        DeveloperPatchService.Result result =
                DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, false);

        assertFalse(result.success());
        assertEquals(0.0, DeveloperTowerData.pendingAmount(beta, DeveloperPatch.ATTACK), 1.0e-9);
    }

    @Test
    void abilityTowersCannotBePatched() {
        grant(5, 2, true, true, true);
        DeveloperTower workbench = tower(DeveloperTowers.WORKBENCH);

        assertFalse(DeveloperPatchService.applyPatch(null, workbench, DeveloperPatch.ATTACK, false).success());
        assertEquals(5, DeveloperStates.of(OWNER).patchesRemaining(), "실패한 시도는 슬롯을 쓰면 안 됩니다.");
    }

    // ------------------------------------------------------------------ 핫픽스

    @Test
    void aHotfixLandsImmediatelyAndAlwaysLeavesADefect() {
        grant(0, 1, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);

        DeveloperPatchService.Result result =
                DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, true);

        assertTrue(result.success(), result.message());
        assertTrue(DeveloperTowerData.activeAmount(beta, DeveloperPatch.ATTACK) > 0.0,
                "핫픽스는 이번 라운드부터 적용되어야 합니다.");
        assertEquals(1, DeveloperTowerData.instability(beta));
        assertNotNull(result.spawnedBug(), "핫픽스는 티어와 무관하게 항상 버그를 남깁니다.");
    }

    @Test
    void anLtsSoaksHotfixesWithoutEverBecomingUnstable() {
        grant(0, 3, false, false, false);
        DeveloperTower lts = tower(DeveloperTowers.LTS);

        for (int index = 0; index < 3; index++) {
            assertTrue(DeveloperPatchService.applyPatch(null, lts, DeveloperPatch.ATTACK, true).success());
        }

        assertEquals(0, DeveloperTowerData.instability(lts), "LTS는 불안정에 면역이어야 합니다.");
        assertTrue(DeveloperTowerData.activeAmount(lts, DeveloperPatch.ATTACK) > 0.0);
    }

    @Test
    void releaseAbsorbsLessOfAHotfixThanLtsDoes() {
        grant(0, 1, false, false, false);
        DeveloperTower release = tower(DeveloperTowers.RELEASE);
        DeveloperPatchService.applyPatch(null, release, DeveloperPatch.ATTACK, true);
        double onRelease = DeveloperTowerData.activeAmount(release, DeveloperPatch.ATTACK);

        DeveloperStates.clearAll();
        grant(0, 1, false, false, false);
        DeveloperTower lts = tower(DeveloperTowers.LTS);
        DeveloperPatchService.applyPatch(null, lts, DeveloperPatch.ATTACK, true);
        double onLts = DeveloperTowerData.activeAmount(lts, DeveloperPatch.ATTACK);

        assertTrue(onLts > onRelease,
                "핫픽스는 LTS에 더 크게, 정식판에 더 작게 들어가야 합니다. release=" + onRelease + " lts=" + onLts);
    }

    @Test
    void releaseNeverPicksUpADefectFromAReviewedPatch() {
        grant(20, 0, false, false, false);
        DeveloperTower release = tower(DeveloperTowers.RELEASE);

        for (int index = 0; index < 20; index++) {
            DeveloperPatchService.Result result =
                    DeveloperPatchService.applyPatch(null, release, DeveloperPatch.ATTACK, false);
            assertTrue(result.success(), result.message());
            assertEquals(null, result.spawnedBug(), "무결성 타워는 정식 패치로 버그가 생기면 안 됩니다.");
        }
        assertTrue(DeveloperTowerData.bugs(release).isEmpty());
    }

    // ------------------------------------------------------------------ 잠금 규칙

    @Test
    void aPinnedTowerRefusesPatchesAndReproduction() {
        grant(3, 1, false, false, true);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.setPinned(beta, true);

        assertFalse(DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, false).success());
        assertFalse(DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, true).success());

        DeveloperTower source = tower(DeveloperTowers.ALPHA, OWNER, 0, 1);
        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);
        assertFalse(DeveloperPatchService.reproduceBug(lane(source, beta), source, beta, DeveloperBug.PRIMITIVE).success());
    }

    @Test
    void rollbackFailureLeavesOnlyTheHotfixPath() {
        grant(2, 1, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(beta, DeveloperBug.ROLLBACK_FAILURE);

        assertFalse(DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, false).success(),
                "롤백 실패는 정식 패치를 막아야 합니다.");
        assertTrue(DeveloperPatchService.applyPatch(null, beta, DeveloperPatch.ATTACK, true).success(),
                "핫픽스는 여전히 가능해야 합니다.");
    }

    // ------------------------------------------------------------------ 긴급 점검

    @Test
    void maintenanceNeedsAnOpsCenterAndRunsOncePerRound() {
        grant(0, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        assertFalse(DeveloperPatchService.applyMaintenance(null, beta, 3).success(),
                "운영 센터 없이는 점검할 수 없어야 합니다.");

        grant(0, 0, true, false, false);
        assertTrue(DeveloperPatchService.applyMaintenance(null, beta, 3).success());
        assertTrue(DeveloperTowerData.underMaintenance(beta, 3));
        assertFalse(DeveloperPatchService.applyMaintenance(null, beta, 3).success(),
                "라운드당 한 번만 가능해야 합니다.");
    }

    @Test
    void maintenanceWipesInstabilityAndTheLeakCounter() {
        grant(0, 0, true, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addInstability(beta, 4);
        DeveloperTowerData.advanceLeak(beta);
        DeveloperTowerData.advanceLeak(beta);

        assertTrue(DeveloperPatchService.applyMaintenance(null, beta, 5).success());

        assertEquals(0, DeveloperTowerData.instability(beta));
        assertEquals(0, DeveloperTowerData.leakRounds(beta));
    }

    // ------------------------------------------------------------------ 최적화

    @Test
    void optimizationIsLimitedPerMatchNotPerRound() {
        grant(0, 0, false, false, false);
        int budget = DeveloperBalance.optimizationsPerMatch();
        DeveloperTower beta = tower(DeveloperTowers.BETA);

        DeveloperOptimization[] options = DeveloperOptimization.values();
        for (int index = 0; index < budget; index++) {
            assertTrue(DeveloperPatchService.applyOptimization(null, beta, options[index]).success());
        }
        assertFalse(DeveloperPatchService.applyOptimization(null, beta, options[budget]).success(),
                "매치 예산을 넘겨서 최적화할 수 없어야 합니다.");

        // Opening a new round must not hand the budget back.
        grant(0, 0, false, false, false);
        assertFalse(DeveloperPatchService.applyOptimization(null, beta, options[budget]).success(),
                "최적화 예산은 라운드마다 회복되면 안 됩니다.");
    }

    @Test
    void readOnlyLocksOptimizationOutWithoutSpendingTheBudget() {
        grant(0, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(beta, DeveloperBug.READ_ONLY);

        assertFalse(DeveloperPatchService.applyOptimization(null, beta, DeveloperOptimization.RANGE).success());
        assertEquals(DeveloperBalance.optimizationsPerMatch(),
                DeveloperStates.of(OWNER).optimizationsRemaining(),
                "거부된 최적화는 예산을 쓰면 안 됩니다.");
    }

    @Test
    void conflictingOptimizationsAreFlaggedButStillAllowed() {
        grant(0, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        assertTrue(DeveloperPatchService.applyOptimization(null, beta, DeveloperOptimization.DURABILITY).success());

        assertTrue(DeveloperPatchService.wouldConflict(beta, DeveloperOptimization.ATTACK),
                "내구 포기 위에 공격 포기를 얹는 것은 경고 대상이어야 합니다.");
        assertTrue(DeveloperPatchService.applyOptimization(null, beta, DeveloperOptimization.ATTACK).success(),
                "경고일 뿐 차단은 아니어야 합니다.");
    }

    // ------------------------------------------------------------------ 디버그와 재현

    @Test
    void removingABugNeedsADebuggerAndSpendsTheRoundBudget() {
        grant(0, 0, false, false, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);
        DeveloperTowerData.addBug(beta, DeveloperBug.PRIMITIVE);

        assertFalse(DeveloperPatchService.removeBug(null, beta, DeveloperBug.PRIMITIVE).success(),
                "디버거 없이는 제거할 수 없어야 합니다.");

        grant(0, 0, false, true, false);
        assertTrue(DeveloperPatchService.removeBug(null, beta, DeveloperBug.PRIMITIVE).success());
        assertFalse(beta.hasBug(DeveloperBug.PRIMITIVE));
        assertEquals(0, DeveloperStates.of(OWNER).debugRemovalsRemaining());
    }

    @Test
    void reproductionRequiresTheSourceToActuallyCarryTheDefect() {
        grant(0, 0, false, false, true);
        DeveloperTower source = tower(DeveloperTowers.ALPHA);
        DeveloperTower target = tower(DeveloperTowers.RELEASE, OWNER, 0, 1);
        PlayerLane lane = lane(source, target);

        assertFalse(DeveloperPatchService.reproduceBug(lane, source, target, DeveloperBug.PRIMITIVE).success(),
                "원본에 없는 버그는 재현할 수 없어야 합니다.");

        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);
        assertTrue(DeveloperPatchService.reproduceBug(lane, source, target, DeveloperBug.PRIMITIVE).success());
        assertTrue(target.hasBug(DeveloperBug.PRIMITIVE),
                "무결성 타워도 의도적으로 심은 버그는 받아야 합니다.");
    }

    @Test
    void reproductionNeedsTheDeveloperTower() {
        grant(0, 0, false, true, false);
        DeveloperTower source = tower(DeveloperTowers.ALPHA);
        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);
        DeveloperTower target = tower(DeveloperTowers.BETA);

        assertFalse(DeveloperPatchService.reproduceBug(null, source, target, DeveloperBug.PRIMITIVE).success());
    }

    @Test
    void failedReproductionKeepsItsChargeAndRejectsOtherOwnersAndFullTargets() {
        grant(0, 0, false, false, true);
        DeveloperTower source = tower(DeveloperTowers.ALPHA);
        DeveloperTower otherOwner = tower(DeveloperTowers.BETA, UUID.randomUUID(), 0, 1);
        DeveloperTower full = tower(DeveloperTowers.RELEASE, OWNER, 0, 2);
        PlayerLane lane = lane(source, otherOwner, full);
        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);
        for (DeveloperBug bug : List.of(
                DeveloperBug.BOUNDARY,
                DeveloperBug.FLOATING_POINT,
                DeveloperBug.TIMEOUT,
                DeveloperBug.BUFFER_OVERRUN
        )) {
            DeveloperTowerData.addBug(full, bug);
        }

        assertFalse(DeveloperPatchService.reproduceBug(lane, source, otherOwner, DeveloperBug.PRIMITIVE).success());
        assertFalse(DeveloperPatchService.reproduceBug(lane, source, full, DeveloperBug.PRIMITIVE).success());
        assertEquals(1, DeveloperStates.of(OWNER).reproductionsRemaining(),
                "거부된 재현은 횟수를 소비하면 안 됩니다.");
    }

    @Test
    void openingANewRoundClearsAnArmedReproduction() {
        grant(0, 0, false, false, true);
        DeveloperTower source = tower(DeveloperTowers.ALPHA);
        PlayerLane lane = lane(source);
        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);

        assertTrue(DeveloperPatchService.armReproduction(lane, source, DeveloperBug.PRIMITIVE).success());
        assertTrue(DeveloperStates.of(OWNER).pendingReproduction().isPresent());

        grant(0, 0, false, false, true);
        assertTrue(DeveloperStates.of(OWNER).pendingReproduction().isEmpty());
    }

    @Test
    void pinningNeedsTheDeveloperTower() {
        grant(0, 0, false, true, false);
        DeveloperTower beta = tower(DeveloperTowers.BETA);

        assertFalse(DeveloperPatchService.setPinned(null, beta, true).success());

        grant(0, 0, false, false, true);
        assertTrue(DeveloperPatchService.setPinned(null, beta, true).success());
        assertTrue(DeveloperTowerData.isPinned(beta));
    }
}
