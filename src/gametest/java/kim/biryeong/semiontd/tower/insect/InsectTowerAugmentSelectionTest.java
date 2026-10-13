package kim.biryeong.semiontd.tower.insect;

import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import kim.biryeong.semiontd.augment.AugmentControllerFixture;

public final class InsectTowerAugmentSelectionTest extends AugmentControllerFixture {
    @GameTest
    public void insectCoverDesignatesRealBodiesAndReducesActualDamage(GameTestHelper context) {
        for (var type : kim.biryeong.semiontd.tower.insect.InsectTowers.all()) {
            if (type == kim.biryeong.semiontd.tower.insect.InsectTowers.SPAWNER) {continue;}
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online);
            try {
                var lane = game.playerLane(online.getUUID()).orElseThrow();
                Tower tower = ProductionTowerCatalog.entry(type).orElseThrow().create(online.getUUID(), TeamId.RED, 1,
                        lane.laneLayout().finalDefenseTowerSlots().getFirst());
                lane.addTower(tower);
                var entity = ((EntityBackedTower) tower).runtimeEntity(lane).orElseThrow();
                double health = tower.health();
                entity.hurt(entity.damageSources().generic(), 10);
                double baseline = health - tower.health();
                require(baseline > 0, "The fixture must receive damage before designation.");
                entity.damageCooldownTime = 0;
                force(game, online, "tactical_designation_1_cover reserve_income_silver reserve_production_silver");
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID());
                holdTargetTool(online);
                var manager = new kim.biryeong.semiontd.game.SemionGameManager();
                setField(manager, "activeGame", game);
                kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleUse(manager, online, online.level(),
                        net.minecraft.world.InteractionHand.MAIN_HAND, entity, new net.minecraft.world.phys.EntityHitResult(entity));
                require(tower.logicalId().equals(state.snapshot().choice("tactical_designation_1").primaryTargetId()),
                        type.id() + " must accept cover designation through a physical right click.");
                health = tower.health();
                entity.hurt(entity.damageSources().generic(), 10);
                double reduction = game.augmentConfig().parameter("tactical_designation_1", "damageReduction", .2);
                require(Math.abs((health - tower.health()) - baseline * (1 - reduction)) < .0001,
                        type.id() + " must reduce actual HP damage, not only record the target.");
                Tower copy = ProductionTowerCatalog.entry(type).orElseThrow().create(online.getUUID(), TeamId.RED, 1,
                        tower.originalPosition()).markTemporaryCopy(tower.logicalId());
                lane.addTower(copy);
                require(!game.augmentService().eligibleTargets(game, player, "tactical_designation_1_cover").contains(copy),
                        "Temporary larvae/copies must remain excluded.");
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {game.close();}
        }
        context.succeed();
    }
}
