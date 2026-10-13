package kim.biryeong.semiontd.tower;

import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTower;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.villager.AntiTankerCatTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class TowerBuilderTargetPolicyTest extends GameTestParticipantFixture {
    @GameTest
    public void highHealthTargetPoliciesUseMaximumHealth(GameTestHelper context) {
        SemionMonsterEntity lowerMaxHealth = spawnRoleMonsterEntity(
                context,
                "targeting-lower-max-health",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity higherMaxHealth = spawnRoleMonsterEntity(
                context,
                "targeting-higher-max-health",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                200.0,
                List.of(SummonRole.TANK)
        );
        lowerMaxHealth.setHealth(80.0F);
        higherMaxHealth.setHealth(20.0F);
        GridPosition position = GridPosition.from(context.absolutePos(BlockPos.ZERO));

        for (Tower tower : List.of(
                new AntiTankerCatTower(
                        VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                        stableUuid("maximum-health-sniper-cat"),
                        TeamId.RED,
                        1,
                        position
                ),
                new AntiTankerCatTower(
                        VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER,
                        stableUuid("maximum-health-adv-sniper-cat"),
                        TeamId.RED,
                        1,
                        position
                ),
                new OceanTower(
                        OceanTowers.T1_COD,
                        stableUuid("maximum-health-cod"),
                        TeamId.RED,
                        1,
                        position
                ),
                ProductionTowerCatalog.find(IllagerTowers.T2_WITCH_HIGH.id()).orElseThrow().create(
                        stableUuid("maximum-health-witch"),
                        TeamId.RED,
                        1,
                        position
                )
        )) {
            if (!assertTrue(
                    context,
                    tower.selectAttackTarget(null, List.of(lowerMaxHealth, higherMaxHealth)).orElse(null) == higherMaxHealth,
                    tower.type().id() + " should prioritize the target with the highest maximum health."
            )) {
                return;
            }
        }
        context.succeed();
    }
}
