package kim.biryeong.semiontd.augment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.mage.MageStates;
import kim.biryeong.semiontd.tower.pet.PetTowers;
import kim.biryeong.semiontd.tower.pirate.PirateAugments;
import kim.biryeong.semiontd.tower.plant.PlantCombatTower;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.tower.queen.QueenStates;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Job-specific acquisition side effects and designation rules through the real controller. */
public final class JobAugmentSelectionGameTest {
    @GameTest
    public void acquisitionGrantsApplyExactlyOnceAcrossDuplicateClicksAndConfirms(GameTestHelper context) {
        for (String id : List.of("job_queen_towers_g2", "job_mage_towers_g1", "job_plant_towers_g1", "job_pirate_p")) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, id);
            try {
                UUID owner = online.getUUID();
                IntSupplier amount;
                int expected;
                switch (id) {
                    case "job_queen_towers_g2" -> {
                        amount = () -> QueenStates.state(owner).jokerTickets();
                        expected = 3;
                    }
                    case "job_mage_towers_g1" -> {
                        MageStates.state(owner).clearMana();
                        amount = () -> MageStates.state(owner).mana();
                        expected = 300;
                    }
                    case "job_plant_towers_g1" -> {
                        addTower(context, game, owner, PlantTowers.T1_OAK_SEED_TOWER, 3, 3);
                        PlantCombatTower meadow = (PlantCombatTower) addTower(context, game, owner,
                                PlantTowers.T1_MEADOW_TOWER, 4, 3);
                        amount = meadow::growthRounds;
                        expected = 5;
                    }
                    case "job_pirate_p" -> {
                        amount = () -> PirateAugments.cannonStacks(owner);
                        expected = 1;
                    }
                    default -> throw new AssertionError("Unexpected fixture: " + id);
                }
                int before = amount.getAsInt();
                var state = game.players().get(owner).augments();
                var offer = forceAndReveal(game, online, id);
                String click = "draft " + offer.revision() + " 0 " + UUID.randomUUID();
                require(handle(game, online, click) == 1, id + " must commit through the actual selection controller.");
                require(amount.getAsInt() == before + expected, id + " must grant its exact acquisition reward.");
                handle(game, online, click);
                handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID());
                handle(game, online, "confirm " + offer.revision() + " " + offer.draftRevision() + " " + UUID.randomUUID());
                require(amount.getAsInt() == before + expected, id + " must not grant again for duplicate input.");
                require(state.selections().size() == 1 && state.snapshot().has(id),
                        id + " must have exactly one committed selection.");
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void allFourManualJobCardsRejectWrongClassForeignOwnerAndTemporaryCopies(GameTestHelper context) {
        for (TargetCase test : List.of(
                new TargetCase("job_villager_towers_p", VillagerTowers.T3_GOLEM_TOWER, VillagerTowers.T1_GOLEM_TOWER),
                new TargetCase("job_undead_towers_p", UndeadTowers.T1_SKELETON_TOWER, UndeadTowers.T1_ZOMBIE_TOWER),
                new TargetCase("job_plant_towers_p", PlantTowers.T1_OAK_SEED_TOWER, PlantTowers.T1_MEADOW_TOWER),
                new TargetCase("job_pet_towers_g1", PetTowers.DOG_T1, PetTowers.BUTLER_T1))) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, test.cardId());
            try {
                UUID owner = online.getUUID();
                var lane = game.playerLane(owner).orElseThrow();
                Tower valid = addTower(context, game, owner, test.validType(), 3, 3);
                Tower wrongClass = addTower(context, game, owner, test.invalidType(), 4, 3);
                Tower foreign = createTower(context, UUID.randomUUID(), test.validType(), 5, 3);
                lane.addTower(foreign);
                Tower copy = createTower(context, owner, test.validType(), 3, 4).markTemporaryCopy(valid.logicalId());
                lane.addTower(copy);
                require(game.augmentService().eligibleTargets(game, game.players().get(owner), test.cardId()).equals(List.of(valid)),
                        test.cardId() + " must list only the owned permanent tower of the approved class.");
                var offer = forceAndReveal(game, online, test.cardId());
                require(handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID()) == 1,
                        "The manual job card must be acquired before its target is designated.");
                var state = game.players().get(owner).augments();
                require(state.snapshot().choice(test.cardId()).primaryTargetId() == null,
                        "Acquisition must not automatically choose a manual target.");
                holdTargetTool(online);
                game.augmentService().useTargetTool(game, online, wrongClass, false, false);
                require(state.snapshot().choice(test.cardId()).primaryTargetId() == null,
                        "Wrong-class designation must leave the unassigned choice intact.");
                var manager = new kim.biryeong.semiontd.game.SemionGameManager();
                setField(manager, "activeGame", game);
                var entity = ((kim.biryeong.semiontd.tower.EntityBackedTower) valid).runtimeEntity(lane).orElseThrow();
                require(kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleUse(manager, online, online.level(),
                                net.minecraft.world.InteractionHand.MAIN_HAND, entity, new net.minecraft.world.phys.EntityHitResult(entity))
                                == net.minecraft.world.InteractionResult.SUCCESS,
                        "The physical right click must resolve and designate the job tower.");
                require(valid.logicalId().equals(state.snapshot().choice(test.cardId()).primaryTargetId()),
                        "A valid tower must be selected by the real tool path.");
                for (Tower rejected : List.of(wrongClass, foreign, copy)) {
                    game.augmentService().useTargetTool(game, online, rejected, false, false);
                    require(valid.logicalId().equals(state.snapshot().choice(test.cardId()).primaryTargetId()),
                            "Invalid designation must preserve the existing target: " + test.cardId());
                }
                require(valid.logicalId().equals(valid.augmentSnapshot().choice(test.cardId()).primaryTargetId()),
                        "The designated tower must receive the committed target snapshot.");
                require(!copy.augmentSnapshot().has(test.cardId()), "Temporary copies must remain outside job augment snapshots.");
            } catch (AssertionError error) {
                context.fail(Component.literal(test.cardId() + ": " + error.getMessage()));
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void targetToolSurvivesNextRoundAndRepeatedParticipantPlacement(GameTestHelper context) {
        String cardId = "job_pet_towers_g1";
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, cardId);
        try {
            var server = context.getLevel().getServer();
            require(server.getPlayerList().getPlayer(online.getUUID()) == online,
                    "The participant must be online so the real next-round placement path runs.");
            require(game.restorePlayerPlacement(server, online) && targetToolCount(online) == 0,
                    "Placement must not grant a target tool before a targeted augment is acquired.");
            Tower target = addTower(context, game, online.getUUID(), PetTowers.DOG_T1, 3, 3);
            var offer = forceAndReveal(game, online, cardId);
            require(handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID()) == 1,
                    "The R5 targeted augment must be acquired through the real controller.");
            holdTargetTool(online);
            game.augmentService().useTargetTool(game, online, target, false, false);
            var state = game.players().get(online.getUUID()).augments();
            require(target.logicalId().equals(state.snapshot().choice(cardId).primaryTargetId()),
                    "The target must be selected before changing rounds.");
            var selections = state.selections();
            var toolSelection = state.targetToolSelection().orElseThrow();
            online.getInventory().setItem(7, new ItemStack(Items.DIAMOND));
            online.getInventory().setItem(8, online.getMainHandItem().copy());
            require(targetToolCount(online) == 2, "The fixture must contain a stale duplicate before placement.");

            setField(game, "phase", RoundPhase.ROUND_PAYOUT);
            game.tick(server);
            require(game.currentRound() == 6 && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "The actual R5 payout must enter R6 preparation.");
            require(targetToolCount(online) == 1, "R6 placement must restore exactly one target tool from augment state.");
            require(online.getInventory().getItem(7).isEmpty() && online.getInventory().getItem(8).isEmpty(),
                    "Placement must clear arbitrary old items and stale duplicate tools.");
            require(online.getInventory().getItem(0).is(Items.COMPASS)
                            && online.getInventory().getItem(1).is(Items.ECHO_SHARD),
                    "Restoring the target tool must preserve the newly granted match tools.");
            require(state.selections().equals(selections) && state.targetToolSelection().orElseThrow().equals(toolSelection),
                    "Next-round placement must preserve the selected augment and exact designated tower.");

            AugmentTargetTool.clear(online);
            for (int restore = 0; restore < 2; restore++) {
                require(game.restorePlayerPlacement(server, online), "The active participant must restore through the rejoin path.");
                require(targetToolCount(online) == 1, "Repeated rejoin placement must retain exactly one target tool.");
                require(state.selections().equals(selections) && state.targetToolSelection().orElseThrow().equals(toolSelection),
                        "Rejoin placement must preserve the committed augment and target selection.");
            }
            context.succeed();
        } finally {
            game.close();
        }
    }

    private static int targetToolCount(ServerPlayer online) {
        int count = 0;
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            if (AugmentTargetTool.isTool(online.getInventory().getItem(index))) {count++;}
        }
        return count;
    }

    private static SemionGame prepare(GameTestHelper context, ServerPlayer online, String cardId) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            context.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            context.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
        }
        var card = AugmentCatalog.find(cardId).orElseThrow();
        String schedule = switch (card.rarity()) {
            case SILVER -> "SSS";
            case GOLD -> "GGG";
            case PRISMATIC -> "PPP";
        };
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals(schedule) ? 100 : 0));
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        game.configureAugments(new AugmentConfig(true, false, weights, Map.of(), Set.of()));
        require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                new AssignedParticipant(online.getUUID(), "job-selection-red", TeamId.RED, 1),
                new AssignedParticipant(UUID.randomUUID(), "job-selection-blue", TeamId.BLUE, 1)), Set.of(), 2)),
                "The synthetic NORMAL game must start.");
        for (var team : game.teams().values()) team.laneGroup().disableMonsters();
        setField(game, "currentRound", 4);
        setField(game, "phase", RoundPhase.ROUND_PAYOUT);
        game.tick(context.getLevel().getServer());
        require(game.currentRound() == 5 && game.phase() == RoundPhase.PREPARE_AND_SUMMON, "The fixture must enter R5 preparation.");
        game.players().get(online.getUUID()).assignJob(JobRegistry.find(ResourceLocation.parse(card.requiredJobId())).orElseThrow());
        return game;
    }

    private static PlayerAugmentState.Offer forceAndReveal(SemionGame game, ServerPlayer online, String cardId) {
        String rarity = AugmentCatalog.find(cardId).orElseThrow().rarity().name().toLowerCase(Locale.ROOT);
        require(game.augmentService().handle(game, online,
                "force 5 " + cardId + " reserve_income_" + rarity + " reserve_production_" + rarity, true) == 1,
                "The authorized force-offer fixture must succeed.");
        for (int tick = 0; tick < 20; tick++) game.tick(online.getServer());
        return game.players().get(online.getUUID()).augments().currentOffer().orElseThrow();
    }

    private static Tower addTower(GameTestHelper context, SemionGame game, UUID owner, TowerType type, int x, int z) {
        Tower tower = createTower(context, owner, type, x, z);
        game.playerLane(owner).orElseThrow().addTower(tower);
        return tower;
    }

    private static Tower createTower(GameTestHelper context, UUID owner, TowerType type, int x, int z) {
        return ProductionTowerCatalog.entry(type).orElseThrow().create(owner, TeamId.RED, 1,
                GridPosition.from(context.absolutePos(new BlockPos(x, 1, z))));
    }

    private static void holdTargetTool(ServerPlayer online) {
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            ItemStack stack = online.getInventory().getItem(index);
            if (AugmentTargetTool.isTool(stack)) {
                online.getInventory().setItem(index, ItemStack.EMPTY);
                online.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return;
            }
        }
        throw new AssertionError("The manual job card must grant a target tool.");
    }

    private static int handle(SemionGame game, ServerPlayer online, String input) {
        try {
            var token = AugmentService.class.getDeclaredField("sessionToken");
            token.setAccessible(true);
            return game.augmentService().handle(game, online, "session " + token.get(game.augmentService()) + " " + input, false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot obtain the actual native-button match token.", exception);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare controller fixture: " + name, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record TargetCase(String cardId, TowerType validType, TowerType invalidType) {}
}
