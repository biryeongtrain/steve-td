package kim.biryeong.semiontd.tower.illager;

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
import kim.biryeong.semiontd.augment.AugmentCombat;

public final class IllagerTowerAugmentSelectionTest extends AugmentControllerFixture {
    @GameTest
    public void illagerSoulBondAcceptsBothPhysicalClickRoles(GameTestHelper context) {
        for (var entry : ProductionTowerCatalog.all().stream()
                .filter(entry -> entry.type().id().startsWith("illager_")).toList()) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, "GGG");
            try {
                game.players().get(online.getUUID()).assignJob(new kim.biryeong.semiontd.job.IllagerTowerJob());
                var lane = game.playerLane(online.getUUID()).orElseThrow();
                Tower first = addTarget(game, online, entry.type());
                Tower second = entry.create(online.getUUID(), TeamId.RED, 1,
                        lane.laneLayout().finalDefenseTowerSlots().get(1));
                lane.addTower(second);
                force(game, online, "frontline_specialization reserve_income_gold reserve_production_gold");
                advance(game, online, 20);
                var state = game.players().get(online.getUUID()).augments();
                handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID());
                holdTargetTool(online);
                var manager = new kim.biryeong.semiontd.game.SemionGameManager();
                setField(manager, "activeGame", game);
                var firstEntity = ((EntityBackedTower) first).runtimeEntity(lane).orElseThrow();
                var secondEntity = ((EntityBackedTower) second).runtimeEntity(lane).orElseThrow();
                kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleTargetToolAttack(game, online, firstEntity);
                online.getCooldowns().tick();
                online.getCooldowns().tick();
                kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleUse(manager, online, online.level(),
                        net.minecraft.world.InteractionHand.MAIN_HAND, secondEntity, new net.minecraft.world.phys.EntityHitResult(secondEntity));
                var choice = state.snapshot().choice("frontline_specialization");
                require(first.logicalId().equals(choice.primaryTargetId()) && second.logicalId().equals(choice.secondaryTargetId()),
                        entry.type().id() + " must accept separate vanguard and artillery clicks.");
                lane.markWaveStarted(5);
                require(AugmentCombat.damageBonus(first, firstEntity) < 0 && AugmentCombat.damageBonus(second, secondEntity) > 0,
                        "Both assigned illager roles must affect combat.");
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {game.close();}
        }
        context.succeed();
    }
}
