package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGame;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import kim.biryeong.semiontd.augment.AugmentControllerFixture;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentService;

public final class DemonLordTowerAugmentSelectionTest extends AugmentControllerFixture {
    @GameTest
    public void demonLordDesignationsAreAutomaticAndSoulBondIsExcluded(GameTestHelper context) {
        warmPlayerSpawn(context);
        context.runAfterDelay(10, () -> checkDemonLordDesignations(context));
    }

    private static void checkDemonLordDesignations(GameTestHelper context) {
        for (String id : List.of("tactical_designation_1_assault", "tactical_designation_1_cover",
                "overheat_core", "battlefield_mastery", "one_man_show")) {
            var card = AugmentCatalog.find(id).orElseThrow();
            String schedule = switch (card.rarity()) {case SILVER -> "SSS"; case GOLD -> "GGG"; case PRISMATIC -> "PPP";};
            String rarity = switch (card.rarity()) {case SILVER -> "silver"; case GOLD -> "gold"; case PRISMATIC -> "prismatic";};
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, schedule);
            try {
                var player = game.players().get(online.getUUID());
                player.assignJob(new kim.biryeong.semiontd.job.DemonLordTowerJob());
                var demon = kim.biryeong.semiontd.tower.demonlord.DemonLordStates.getOrCreate(player.uuid());
                var lane = game.playerLane(player.uuid()).orElseThrow();
                require(!game.augmentService().isEligible(game, player, "frontline_specialization"),
                        "Demon Lords cannot receive soul bond in any candidate path.");
                require(selfTargeted(player, id), "Common single designations must target the Demon Lord.");
                force(game, online, id + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var offer = player.augments().currentOffer().orElseThrow();
                String command = "draft " + offer.revision() + " 0 " + UUID.randomUUID();
                require(handle(game, online, command) == 1, "A self designation must be acquired in one click.");
                handle(game, online, command);
                require(player.augments().selections().size() == 1 && toolCount(online) == 0,
                        "No duplicate grant or unusable target tool is allowed.");
                advance(game, online, 5);
                game.augmentService().reopen(game, online);
                require(AugmentService.hudHint(game, player).contains("마왕 자신"), "HUD must not ask for a missing target.");
                require(game.augmentService().eligibleTargets(game, player, id).isEmpty(), "Altars cannot steal the self effect.");
                double before = demon.maxHealth();
                lane.markWaveStarted(5);
                require(demon.inCombat() && demon.maxHealth() == before, "Starting combat cannot lose or double the self bonus.");
                if (id.equals("one_man_show")) {
                    var unaugmented = new kim.biryeong.semiontd.tower.demonlord.DemonLordState(UUID.randomUUID());
                    require(Math.abs(before - unaugmented.maxHealth() * 1.2) < .0001,
                            "Self health must include 20% of the chosen bonus.");
                }
            } finally {
                game.close();
                kim.biryeong.semiontd.tower.demonlord.DemonLordStates.clearAllForTesting();
            }
        }
        context.succeed();
    }
}
