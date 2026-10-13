package kim.biryeong.semiontd.augment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class AugmentCaptureServer implements DedicatedServerModInitializer {
    private SemionGame game;
    private long initialOfferRevision;
    private String expectedSelectedCard;
    private double unselectedMaxHealth;
    private int unselectedAttackInterval;
    private double expectedSelectedBonus;
    private List<String> checkedCards;
    private List<Integer> checkedRerolls;
    private long checkedRevision;
    private PlayerAugmentState.Offer checkedOffer;
    private eu.pb4.polymer.virtualentity.api.ElementHolder sky;
    private kim.biryeong.semiontd.tower.end.EndDragonCaptureScene dragon;

    @Override
    public void onInitializeServer() {
        if (!Boolean.getBoolean("semiontd.capture")) return;
        kim.biryeong.semiontd.ui.CardDialogPoc.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var command = Commands.literal("semioncapture");
            if ("demon".equals(System.getProperty("semiontd.capture.mode"))) {
                command.then(Commands.literal("demon").then(Commands.argument("skill", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            show(player, "silver");
                            var participant = game.players().get(player.getUUID());
                            participant.assignJob(new kim.biryeong.semiontd.job.DemonLordTowerJob());
                            var state = kim.biryeong.semiontd.tower.demonlord.DemonLordStates.getOrCreate(player.getUUID());
                            state.addExperience(1e12);
                            var skill = kim.biryeong.semiontd.tower.demonlord.DemonLordSkill.valueOf(
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "skill"));
                            var tower = new kim.biryeong.semiontd.tower.demonlord.DemonLordSkillTower(
                                    kim.biryeong.semiontd.config.TowerBalanceRuntime.resolve(kim.biryeong.semiontd.tower.demonlord.DemonLordTowers.tower(skill, 4)), player.getUUID(),
                                    participant.teamId(), participant.laneId(), new kim.biryeong.semiontd.game.GridPosition(0, 80, 0));
                            new kim.biryeong.semiontd.ui.SemionDialogService().showTowerDetails(player, game, tower);
                            player.setExperienceLevels(2300 + skill.ordinal());
                            System.out.println("SEMION_DEMON_DETAILS=" + skill + " " + tower.runtimeDetailLines());
                            return 1;
                        })));
            }
            for (String rarity : List.of("silver", "gold", "prismatic")) {
                command.then(Commands.literal(rarity).executes(context -> {
                    try {
                        show(context.getSource().getPlayerOrException(), rarity);
                        return 1;
                    } catch (RuntimeException exception) {
                        org.slf4j.LoggerFactory.getLogger(AugmentCaptureServer.class).error("Capture offer setup failed", exception);
                        throw exception;
                    }
                }));
            }
            command.then(Commands.literal("iconpreview")
                    .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                    .executes(context -> {
                        kim.biryeong.semiontd.ui.AugmentIconPreview.show(context.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "page"));
                        return 1;
                    })));
            command.then(Commands.literal("checkreroll").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var state = captureState(player);
                if (state.rerollsRemaining(0) != 4 || !state.selections().isEmpty()
                        || state.currentOffer().orElseThrow().revision() == initialOfferRevision) {
                    throw new IllegalStateException("Actual mouse reroll did not replace the offer and spend exactly one reroll");
                }
                player.setExperienceLevels(2041);
                System.out.println("SEMION_HUD_REROLL_CONFIRMED remaining=4 selections=0 revision=" + state.currentOffer().orElseThrow().revision());
                return 1;
            }));
            command.then(Commands.literal("checkreroll")
                    .then(Commands.argument("remaining", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        int remaining = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "remaining");
                        var state = captureState(player);
                        var offer = state.currentOffer().orElseThrow();
                        if (state.rerollsRemaining() != remaining || checkedRerolls.getFirst() != remaining + 1
                                || !state.selections().isEmpty() || offer.revision() != checkedRevision + 1
                                || !java.util.Collections.disjoint(offer.cardIds(), checkedCards)
                                || new java.util.HashSet<>(offer.cardIds()).size() != 3
                                || offer.deadlineTickExclusive() != checkedOffer.deadlineTickExclusive()) {
                            throw new IllegalStateException("Shared reroll must replace all three cards and spend one charge atomically");
                        }
                        checkedCards = List.copyOf(offer.cardIds());
                        checkedRerolls = state.rerollsRemainingBySlot();
                        checkedRevision = offer.revision();
                        checkedOffer = offer;
                        player.setExperienceLevels(2100 + remaining);
                        System.out.println("SEMION_NATIVE_SHARED_REROLL remaining=" + remaining
                                + " revision=" + checkedRevision + " allThreeChanged=true deadlinePreserved=true");
                        return 1;
                    })));
            command.then(Commands.literal("lastroll").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                show(player, "silver");
                var state = captureState(player);
                setField(state, "rerollsUsedBySlot", new int[]{0, 4, 2});
                checkedRerolls = state.rerollsRemainingBySlot();
                if (state.rerollsRemaining() != 1) throw new IllegalStateException("Legacy minimum balance must migrate to one");
                game.augmentService().reopen(game, player);
                player.setExperienceLevels(2144);
                System.out.println("SEMION_NATIVE_LEGACY_BUDGET_MIGRATED remaining=1");
                return 1;
            }));
            command.then(Commands.literal("longtext").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                show(player, "longtext");
                player.setExperienceLevels(2145);
                return 1;
            }));
            command.then(Commands.literal("checkdisabled").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var state = captureState(player);
                if (state.rerollsRemaining(0) != 0 || state.currentOffer().orElseThrow().revision() != checkedRevision
                        || !state.rerollsRemainingBySlot().equals(checkedRerolls)) {
                    throw new IllegalStateException("Disabled native reroll unexpectedly mutated the offer");
                }
                player.setExperienceLevels(2140);
                System.out.println("SEMION_NATIVE_DISABLED_REROLL ignored=true remaining=" + checkedRerolls);
                return 1;
            }));
            command.then(Commands.literal("checkpreserved").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var state = captureState(player);
                if (!state.currentOffer().orElseThrow().equals(checkedOffer)
                        || !state.rerollsRemainingBySlot().equals(checkedRerolls) || !state.selections().isEmpty()) {
                    throw new IllegalStateException("Closing or reopening changed the live offer, deadline or reroll budgets");
                }
                player.setExperienceLevels(2141);
                System.out.println("SEMION_NATIVE_CLOSE_REOPEN_PRESERVED revision=" + checkedRevision + " remaining=" + checkedRerolls);
                return 1;
            }));
            command.then(Commands.literal("dedicated").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                show(player, "dedicated");
                player.setExperienceLevels(2142);
                return 1;
            }));
            command.then(Commands.literal("checkslotselection")
                    .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                            .executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                var state = captureState(player);
                                int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot");
                                String expected = checkedCards.get(slot);
                                if (state.selections().size() != 1 || state.currentOffer().isPresent()
                                        || !state.selections().getFirst().augmentId().equals(expected)
                                        || !state.rerollsRemainingBySlot().equals(checkedRerolls)) {
                                    throw new IllegalStateException("Actual mouse selection must preserve the displayed slot identity");
                                }
                                player.setExperienceLevels(2200 + slot);
                                System.out.println("SEMION_NATIVE_SLOT_ID_CONFIRMED slot=" + slot + " card=" + expected);
                                return 1;
                            })));
            command.then(Commands.literal("checkselection").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                verifySelectedState(player);
                player.setExperienceLevels(2042);
                System.out.println("SEMION_HUD_SELECTION_CONFIRMED card=" + expectedSelectedCard + " selections=1 rerolls=5");
                return 1;
            }));
            command.then(Commands.literal("checkselectedreopen").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                verifySelectedState(player);
                player.setExperienceLevels(2143);
                System.out.println("SEMION_SELECTED_REOPEN_PRESERVED card=" + expectedSelectedCard + " selections=1 offerClosed=true");
                return 1;
            }));
            if ("sky".equals(System.getProperty("semiontd.capture.mode"))) {
                command.then(Commands.literal("sky").executes(context -> {
                    showSky(context.getSource().getPlayerOrException());
                    return 1;
                }));
                command.then(Commands.literal("skyoff").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!player.getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
                    if (sky != null) sky.destroy();
                    sky = null;
                    player.setExperienceLevels(2025);
                    return 1;
                }));
            }
            if ("dragon".equals(System.getProperty("semiontd.capture.mode"))) {
                command.then(Commands.literal("dragon").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!player.getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
                    if (dragon != null) dragon.close();
                    dragon = new kim.biryeong.semiontd.tower.end.EndDragonCaptureScene(player);
                    return 1;
                }));
                command.then(Commands.literal("dragonfront").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!player.getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
                    if (dragon != null) dragon.close();
                    dragon = new kim.biryeong.semiontd.tower.end.EndDragonCaptureScene(player, true);
                    return 1;
                }));
                command.then(Commands.literal("dragonstart").executes(context -> {
                    if (!context.getSource().getPlayerOrException().getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
                    dragon.begin();
                    return 1;
                }));
                command.then(Commands.literal("dragonoff").executes(context -> {
                    if (!context.getSource().getPlayerOrException().getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
                    if (dragon != null) dragon.close();
                    dragon = null;
                    return 1;
                }));
            }
            dispatcher.register(command);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (sky != null) sky.tick();
            if (dragon != null) dragon.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (sky != null) sky.destroy();
            if (dragon != null) dragon.close();
            if (game != null) {
                var manager = captureManager();
                if (manager.activeGame().orElse(null) == game) setField(manager, "activeGame", null);
                game.close();
            }
        });
    }

    private void showSky(ServerPlayer player) {
        if (!player.getGameProfile().name().equals("SemionCapture")) throw new IllegalStateException("Capture account required");
        if (!eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(player)) throw new IllegalStateException("Capture main pack is not active");
        if (sky != null) sky.destroy();
        var library = kim.biryeong.semiontd.skybox.SemionSkyboxLibrary.load(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("semion-td/skyboxes"),
                org.slf4j.LoggerFactory.getLogger(AugmentCaptureServer.class));
        var selected = library.defaultSkybox().orElseThrow();
        player.level().getServer().getPlayerList().setViewDistance(16);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setNoGravity(true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), 0.5, 240, 0.5, Set.of(), 0, 0, true);
        var level = player.level();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) level.setBlock(new BlockPos(x, 238, z), net.minecraft.world.level.block.Blocks.CONCRETE.white().defaultBlockState(), 3);
        }
        for (int y = 240; y <= 243; y++) {
            level.setBlock(new BlockPos(-2, y, 6), net.minecraft.world.level.block.Blocks.CONCRETE.red().defaultBlockState(), 3);
            level.setBlock(new BlockPos(2, y, 6), net.minecraft.world.level.block.Blocks.STAINED_GLASS.cyan().defaultBlockState(), 3);
        }
        var stack = net.minecraft.world.item.Items.STICK.getDefaultInstance();
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, selected.itemModelId());
        var display = new eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement(stack);
        display.setScale(new org.joml.Vector3f(1000));
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setViewRange(1000);
        sky = new eu.pb4.polymer.virtualentity.api.ElementHolder();
        sky.addElement(display);
        new eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment(sky, level, player::getEyePosition);
        sky.startWatching(player);
        sky.tick();
        player.setExperienceLevels(2026);
        System.out.println("SEMION_SKY_CAPTURE_ACTIVE=" + selected.id() + " scale=1000 pack=true textures=" + library.skyboxes().size()
                + " serverViewDistance=" + player.level().getServer().getPlayerList().getViewDistance());
    }

    private void show(ServerPlayer player, String rarity) {
        boolean longText = rarity.equals("longtext");
        if (longText) rarity = "prismatic";
        boolean dedicated = rarity.equals("dedicated");
        if (dedicated) rarity = "silver";
        if (!player.getGameProfile().name().equals("SemionCapture")) {
            throw new IllegalStateException("The capture command is reserved for the isolated test account");
        }
        var manager = captureManager();
        if (manager.activeGame().isPresent() && manager.activeGame().orElseThrow() != game) {
            throw new IllegalStateException("Cannot replace a game not owned by the isolated capture fixture");
        }
        if (game != null) {
            setField(manager, "activeGame", null);
            game.close();
        }
        var server = player.level().getServer();
        game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(server.overworld(), new BlockPos(0, 80, 0)));
        String schedule = rarity.equals("silver") ? "SSS" : rarity.equals("gold") ? "GGG" : "PPP";
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals(schedule) ? 100 : 0));
        game.configureAugments(new AugmentConfig(true, false, weights, Map.of(), Set.of()));
        if (!game.start(server, new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                new AssignedParticipant(player.getUUID(), "capture-red", TeamId.RED, 1),
                new AssignedParticipant(UUID.randomUUID(), "capture-blue", TeamId.BLUE, 1)), Set.of(), 2))) {
            throw new IllegalStateException("The capture match could not start");
        }
        for (var team : game.teams().values()) team.laneGroup().disableMonsters();
        var lane = game.playerLane(player.getUUID()).orElseThrow();
        var position = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        var tower = kim.biryeong.semiontd.tower.ProductionTowerCatalog
                .entry(kim.biryeong.semiontd.tower.undead.UndeadTowers.T1_ZOMBIE_TOWER).orElseThrow()
                .create(player.getUUID(), TeamId.RED, 1, position);
        lane.addTower(tower);
        setField(game, "currentRound", 4);
        setField(game, "phase", RoundPhase.ROUND_PAYOUT);
        game.tick(server);
        int tier = rarity.equals("silver") ? 1 : rarity.equals("gold") ? 2 : 3;
        if (dedicated) game.players().get(player.getUUID()).assignJob(new kim.biryeong.semiontd.job.FutureAgencyTowerJob());
        if (longText) game.players().get(player.getUUID()).assignJob(new kim.biryeong.semiontd.job.EndTowerJob());
        String cards = longText ? "job_end_towers_p tactical_designation_3_cover reserve_production_prismatic" : dedicated ? "job_future_agency_towers_s finishing_fire_1 beneficial_effect_1"
                : "finishing_fire_" + tier + " tactical_designation_" + tier + "_cover beneficial_effect_" + tier;
        var definitions = java.util.Arrays.stream(cards.split(" ")).map(id -> AugmentCatalog.find(id).orElseThrow()).toList();
        if (definitions.stream().anyMatch(card -> card.rarity().ordinal() != tier - 1)
                || !dedicated && !definitions.stream().map(AugmentDisplayRole::of).toList()
                        .equals(List.of(AugmentDisplayRole.ATTACK, AugmentDisplayRole.DEFENSE, AugmentDisplayRole.OTHER))) {
            throw new IllegalStateException("Capture cards must cover the requested rarity and all three display roles");
        }
        if (game.augmentService().handle(game, player, "force 5 " + cards, true) != 1) {
            throw new IllegalStateException("The deterministic capture offer could not be opened");
        }
        for (int tick = 0; tick < 20; tick++) game.tick(server);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        game.augmentService().reopen(game, player);
        checkedOffer = captureState(player).currentOffer().orElseThrow();
        initialOfferRevision = checkedOffer.revision();
        checkedRevision = initialOfferRevision;
        checkedCards = List.copyOf(captureState(player).currentOffer().orElseThrow().cardIds());
        checkedRerolls = captureState(player).rerollsRemainingBySlot();
        expectedSelectedCard = AugmentCatalog.normalizeId("beneficial_effect_" + tier);
        var entity = ((kim.biryeong.semiontd.tower.EntityBackedTower) tower).runtimeEntity(lane).orElseThrow();
        unselectedMaxHealth = entity.getMaxHealth();
        unselectedAttackInterval = entity.attackIntervalTicks();
        expectedSelectedBonus = tier / 20.0;
        setField(manager, "activeGame", game);
        if (manager.playableGame(player.getUUID()).orElse(null) != game) {
            throw new IllegalStateException("The production augment command cannot resolve the isolated capture game");
        }
        player.setExperienceLevels(2040);
    }

    private void verifySelectedState(ServerPlayer player) {
        var state = captureState(player);
        if (state.selections().size() != 1 || state.currentOffer().isPresent()
                || !state.rerollsRemainingBySlot().equals(List.of(5, 5, 5))
                || !state.selections().getFirst().augmentId().equals(expectedSelectedCard)) {
            throw new IllegalStateException("Actual card input must grant exactly one selected augment and close its offer");
        }
        var lane = game.playerLane(player.getUUID()).orElseThrow();
        var tower = lane.towers().iterator().next();
        var entity = ((kim.biryeong.semiontd.tower.EntityBackedTower) tower).runtimeEntity(lane).orElseThrow();
        double health = unselectedMaxHealth * (1 + expectedSelectedBonus);
        int interval = (int) Math.ceil(unselectedAttackInterval / (1 + expectedSelectedBonus));
        double damage = AugmentCombat.damageBonus(tower, entity);
        if (!tower.augmentSnapshot().has(expectedSelectedCard)
                || Math.abs(entity.getMaxHealth() - health) > .001
                || entity.attackIntervalTicks() != interval
                || Math.abs(damage - expectedSelectedBonus) > 1.0e-9) {
            throw new IllegalStateException("Actual selected card effect mismatch: health=" + entity.getMaxHealth()
                    + "/" + health + " interval=" + entity.attackIntervalTicks() + "/" + interval + " damage=" + damage);
        }
        System.out.println("SEMION_CLICK_EFFECT_APPLIED card=" + expectedSelectedCard + " maxHealth=" + entity.getMaxHealth()
                + " attackInterval=" + entity.attackIntervalTicks() + " damageBonus=" + damage + " offerClosed=true");
    }

    private PlayerAugmentState captureState(ServerPlayer player) {
        if (!player.getGameProfile().name().equals("SemionCapture") || game == null) {
            throw new IllegalStateException("An active isolated capture account is required");
        }
        return game.players().get(player.getUUID()).augments();
    }

    private static kim.biryeong.semiontd.game.SemionGameManager captureManager() {
        var entrypoint = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("main", net.fabricmc.api.ModInitializer.class).stream()
                .filter(kim.biryeong.semiontd.SemionTd.class::isInstance).findFirst().orElseThrow();
        try {
            var field = kim.biryeong.semiontd.SemionTd.class.getDeclaredField("gameManager");
            field.setAccessible(true);
            return (kim.biryeong.semiontd.game.SemionGameManager) field.get(entrypoint);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the initialized capture game manager", exception);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot prepare the isolated capture offer", exception);
        }
    }
}
