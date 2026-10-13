package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates;
import kim.biryeong.semiontd.tower.adversary.AdversaryRivalTower;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTower;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class JobLifecycleRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void ancientCityDispatchPreservesDeathCapRoundResetAndCleanup(GameTestHelper context) {
        UUID owner = stableUuid("job-lifecycle-ancient-city");
        SemionGame game = startedSinglePlayerGame(context, owner, TeamId.RED, AncientCityTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        SemionMonsterEntity target = null;
        try {
            AncientCityTower tower = new AncientCityTower(
                    TowerBalanceRuntime.resolve(AncientCityTowers.CATALYST_T1), owner, TeamId.RED, 1,
                    GridPosition.from(towerPlacementPos(lane)));
            lane.addTower(tower);
            JobContext jobContext = new JobContext(game, game.players().get(owner));
            SemionJob job = jobContext.player().job().orElseThrow();
            int initial = AncientCityStates.territoryCount(owner);
            context.assertTrue(initial > 0, "The placed tower must seed territory.");
            target = spawnRoleMonsterEntity(context, "lifecycle-death", Optional.empty(), TeamId.RED, 1,
                    lane.laneLayout().positionAt(.75), 100, List.of(SummonRole.RUSH));
            target.setNoAi(true);
            target.runtimeMonster().markMinecraftEntitySpawned(target.getId(), target.getX(), target.getY(), target.getZ());
            int cap = TowerBalanceRuntime.abilityInt(AncientCityStates.CONFIG_ID, "deathSpreadCapPerRound");
            for (int i = 0; i <= cap; i++) {
                job.onMonsterKilled(jobContext, target.runtimeMonster(), 0);
            }
            context.assertTrue(AncientCityStates.territoryCount(owner) == initial + cap,
                    "Common kill dispatch must preserve the successful death spread cap.");
            int nextRound = game.currentRound() + 1;
            setField(game, "currentRound", nextRound);
            job.onRoundStarted(jobContext, nextRound);
            job.onMonsterKilled(jobContext, target.runtimeMonster(), 0);
            context.assertTrue(AncientCityStates.territoryCount(owner) == initial + cap + 1,
                    "The next round must reopen the death spread budget exactly once.");
            job.onEliminated(jobContext);
            context.assertTrue(AncientCityStates.territoryCount(owner) == 0, "Elimination must clear territory state.");
            AncientCityStates.ensureSeeded(tower, lane);
            job.onMatchStarted(jobContext);
            context.assertTrue(AncientCityStates.territoryCount(owner) == 0, "A new match must clear territory state.");
            AncientCityStates.ensureSeeded(tower, lane);
            game.close();
            context.assertTrue(AncientCityStates.territoryCount(owner) == 0, "Real match close must clear territory state.");
            game.close();
            context.assertTrue(AncientCityStates.territoryCount(owner) == 0, "Repeated close must remain empty.");
        } finally {
            if (target != null) {
                target.discard();
            }
            game.close();
            AncientCityStates.clear(owner);
        }
        context.succeed();
    }

    @GameTest
    public void closeClearsCrossFamilyRivalStateAfterTowerRemoval(GameTestHelper context) {
        UUID owner = stableUuid("job-lifecycle-mixed-rival");
        SemionGame game = startedSinglePlayerGame(context, owner, TeamId.RED, EndTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        try {
            AdversaryRivalTower rival = new AdversaryRivalTower(
                    TowerBalanceRuntime.resolve(AdversaryTowers.BREEZE_RIVAL), owner, TeamId.RED, 1,
                    GridPosition.from(towerPlacementPos(lane)));
            lane.addTower(rival);
            context.assertTrue(AdversaryProgressStates.find(owner).isPresent(),
                    "Placing a cross-family rival must create its installed-score ledger.");
            game.close();
            context.assertTrue(lane.towers().isEmpty(), "Close must detach the rival tower.");
            context.assertTrue(AdversaryProgressStates.find(owner).isEmpty(),
                    "Final cleanup must remove the ledger after rival removal reconciles it.");
            game.close();
            context.assertTrue(AdversaryProgressStates.find(owner).isEmpty(),
                    "Repeated close must not recreate cross-family state.");
        } finally {
            game.close();
            AdversaryProgressStates.clear(owner);
        }
        context.succeed();
    }
}
