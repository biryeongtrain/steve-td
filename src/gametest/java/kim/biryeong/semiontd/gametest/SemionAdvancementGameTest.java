package kim.biryeong.semiontd.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.advancement.SemionAdvancementService;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;

public final class SemionAdvancementGameTest {
    @GameTest
    public void onlySemionAdvancementsAreLoaded(GameTestHelper context) {
        List<Identifier> foreignAdvancements = context.getLevel().getServer().getAdvancements()
                .getAllAdvancements()
                .stream()
                .map(AdvancementHolder::id)
                .filter(id -> !id.getNamespace().equals(SemionTd.MOD_ID))
                .toList();
        if (!foreignAdvancements.isEmpty()) {
            context.fail(Component.literal("Only Semion TD advancements should remain: " + foreignAdvancements));
            return;
        }
        Map<Identifier, AdvancementHolder> loadedAdvancements = context.getLevel().getServer().getAdvancements()
                .getAllAdvancements()
                .stream()
                .collect(java.util.stream.Collectors.toMap(AdvancementHolder::id, advancement -> advancement));
        if (!loadedAdvancements.keySet().equals(SemionAdvancementService.IDS)) {
            context.fail(Component.literal("All Semion TD advancements and their root should load: " + loadedAdvancements.keySet()));
            return;
        }
        List<Identifier> missingParents = loadedAdvancements.values().stream()
                .filter(advancement -> advancement.value().parent()
                        .filter(parent -> !loadedAdvancements.containsKey(parent))
                        .isPresent())
                .map(AdvancementHolder::id)
                .toList();
        if (!missingParents.isEmpty()) {
            context.fail(Component.literal("Advancements with removed parents should not remain: " + missingParents));
            return;
        }
        context.succeed();
    }

    @GameTest
    public void allSemionAdvancementsAreVisibleToNewPlayer(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var manager = context.getLevel().getServer().getAdvancements();
        AdvancementHolder root = manager.get(SemionAdvancementService.ROOT);
        context.assertTrue(root != null, Component.literal("The Semion TD advancement root should load."));
        player.getAdvancements().revoke(root, "visible");

        context.succeedWhen(() -> {
            context.assertTrue(
                    player.getAdvancements().getOrStartProgress(root).isDone(),
                    Component.literal("The Semion TD advancement root should unlock automatically.")
            );
            Set<Identifier> visible = new HashSet<>();
            AdvancementVisibilityEvaluator.evaluateVisibility(
                    manager.tree().get(root),
                    node -> player.getAdvancements().getOrStartProgress(node.holder()).isDone(),
                    (node, isVisible) -> {
                        if (isVisible) {
                            visible.add(node.holder().id());
                        }
                    }
            );
            context.assertTrue(
                    visible.containsAll(SemionAdvancementService.IDS),
                    Component.literal("All Semion TD advancements should be visible: " + visible)
            );
        });
    }

    @GameTest
    public void matchAwardIsGrantedToPlayer(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        MatchResult result = new MatchResult(
                List.of(new MatchParticipantResult(player.getUUID(), player.getScoreboardName(), TeamId.RED, true)),
                Set.of(),
                Set.of(TeamId.RED),
                20
        );

        new SemionAdvancementService().awardMatch(
                context.getLevel().getServer(),
                result,
                Map.of(player.getUUID(), 100)
        );

        AdvancementHolder advancement = context.getLevel().getServer().getAdvancements()
                .get(SemionAdvancementService.VETERAN_100);
        if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            context.fail(Component.literal("The verified match award should complete the veteran advancement."));
            return;
        }
        context.succeed();
    }
}
