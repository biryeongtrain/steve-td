package kim.biryeong.semiontd.tower.magicschool;

import com.mojang.brigadier.CommandDispatcher;
import eu.pb4.sgui.api.SguiUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.MagicSchoolTowerJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.Upgrade;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import kim.biryeong.semiontd.ui.SemionDialogService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class MagicSchoolTowerIntegrationTest implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void protectionSurvivesReconnectPlacementAndDialogShowsCombinedPercentage(GameTestHelper context) throws Exception {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        var game = game(context, owner, UUID.randomUUID());
        var connection = player.connection;
        var sent = new ArrayList<Packet<?>>();
        try {
            player.connection = new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { sent.add(packet); }
            };
            var lane = game.playerLane(owner).orElseThrow();
            var economy = game.players().get(owner).economy();
            for (int tier = 2; tier <= 4; tier++) MagicSchoolCurriculum.unlockSpellTier(owner, tier, economy);
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            MagicSchoolWizardTower protectedWizard = null;
            for (int i = 0; i < 4; i++) {
                var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD,
                        new GridPosition(plot.x() + i, plot.y(), plot.z()));
                require(wizard.selectSpell(MagicSchoolSpell.PROTEGO_MAXIMA), "The unlocked protection must be selectable.");
                wizard.runtimeEntity(lane).orElseThrow().setNoAi(true);
                wizard.onWaveStarted(lane, 1);
                if (i == 0) protectedWizard = wizard;
            }
            var entity = protectedWizard.runtimeEntity(lane).orElseThrow();
            var manager = new SemionGameManager();
            var activeGame = SemionGameManager.class.getDeclaredField("activeGame");
            activeGame.setAccessible(true);
            activeGame.set(manager, game);
            manager.handlePlayerDisconnect(player);
            kim.biryeong.semiontd.ui.GambleRevealService.clear(player.getUUID());
            kim.biryeong.semiontd.tower.demonlord.DemonLordService.disconnectPlayer(player);
            kim.biryeong.semiontd.tower.frost.FrostFullOperationService.cleanupPlayer(player);
            require(game.restorePlayerPlacement(player.level().getServer(), player), "The join path must restore the existing participant.");
            requireClose(.3998375, MagicSchoolSpellCombat.protection(entity), "Reconnect placement preserves the existing protection sources.");
            manager.dialogService().showTowerDetails(player, game, protectedWizard);
            String packet = sent.stream().filter(ClientboundShowDialogPacket.class::isInstance).findFirst().orElseThrow().toString();
            require(packet.contains("39.98375%") && packet.contains("곱연산"), "The delivered dialog must show the actual combined protection.");
            double before = entity.getHealth();
            entity.hurt(entity.damageSources().generic(), 100);
            requireClose(60.01625, before - entity.getHealth(), "The restored participant retains the same actual damage factor.");
        } finally {
            player.connection = connection;
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void placementChargesCostsAndLimitsHogwartsPerPlayerUntilSold(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos first = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            var economy = game.players().get(owner).economy();
            long before = economy.diamond();
            require(place(game, owner, first, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "Hogwarts must be placeable.");
            require(economy.diamond() == before, "Hogwarts must be free.");
            require(place(game, owner, first.east(), MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.TOWER_NOT_ALLOWED,
                    "A second Hogwarts must be rejected without charging diamonds.");
            require(economy.diamond() == before, "Rejected placement must not charge diamonds.");
            require(place(game, owner, first.east(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "The first student must be placeable.");
            require(place(game, owner, first.west(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Multiple students must be allowed.");
            require(economy.diamond() == before - 200, "Each student must cost 100 diamonds while Hogwarts is free.");
            PlayerLane otherLane = game.playerLane(teammate).orElseThrow();
            require(place(game, teammate, BlockPos.containing(otherLane.laneLayout().positionAt(0.7)), MagicSchoolTowers.HOGWARTS)
                    == TowerPlacementResult.SUCCESS, "Each teammate must have an independent Hogwarts allowance.");
            var school = (HogwartsTower) lane.towers().stream().filter(tower -> tower instanceof HogwartsTower).findFirst().orElseThrow();
            school.syncHealth(0);
            require(place(game, owner, first.north(), MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.TOWER_NOT_ALLOWED,
                    "A defeated school must still consume its placement allowance.");
            require(ProductionTowerService.sellTower(game, owner, school.managementPosition()).result() == TowerSellResult.SUCCESS,
                    "Hogwarts must remain sellable.");
            require(place(game, owner, first, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS,
                    "Selling Hogwarts must free the allowance.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void rightClickOffersUnifiedCurriculumInLockedChest(GameTestHelper context) throws Exception {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, player.getUUID(), teammate);
        var connection = player.connection;
        List<Packet<?>> sent = new ArrayList<>();
        try {
            var manager = new SemionGameManager();
            var activeGame = SemionGameManager.class.getDeclaredField("activeGame");
            activeGame.setAccessible(true);
            activeGame.set(manager, game);
            player.connection = new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    sent.add(packet);
                }
            };
            PlayerLane lane = game.playerLane(player.getUUID()).orElseThrow();
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            require(place(game, player.getUUID(), plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "School placement failed.");
            HogwartsTower school = (HogwartsTower) lane.towers().getFirst();
            var entity = school.runtimeEntity(lane).orElseThrow();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            require(SemionTowerInteractionService.handleUse(manager, player, player.level(), InteractionHand.MAIN_HAND,
                    entity, new EntityHitResult(entity)) == InteractionResult.SUCCESS, "A physical right click must open the tower dialog.");
            String command = command(school.managementPosition());
            require(sent.stream().filter(packet -> packet instanceof ClientboundShowDialogPacket)
                    .anyMatch(packet -> packet.toString().contains("커리큘럼") && packet.toString().contains("/" + command)),
                    "The Hogwarts dialog must contain a Curriculum button targeting this plot.");
            require(sent.stream().filter(packet -> packet instanceof ClientboundShowDialogPacket)
                    .noneMatch(packet -> packet.toString().contains("/semiontd magic_school research")),
                    "The separate research action must be removed.");

            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            SemionCommands.register(dispatcher, manager, null, null, null, null, Path.of("run/config/semion-td"));
            String skinCommand = command.replace("curriculum", "skin");
            require(sent.stream().filter(ClientboundShowDialogPacket.class::isInstance)
                    .anyMatch(packet -> packet.toString().contains("마법사 스킨") && packet.toString().contains("/" + skinCommand)),
                    "Hogwarts must offer the wizard skin action beside Curriculum.");
            require(dispatcher.execute(skinCommand, player.createCommandSourceStack()) == 1
                    && SguiUtils.getCurrentGui(player) instanceof MagicSchoolSkinGui, "The skin command must open its own chest menu.");
            SguiUtils.getCurrentGui(player).close();
            require(dispatcher.execute(command, player.createCommandSourceStack()) == 1, "The dialog command must open the chest.");
            require(SguiUtils.getCurrentGui(player) instanceof CurriculumGui, "Curriculum must use the chest UI.");
            CurriculumGui gui = (CurriculumGui) SguiUtils.getCurrentGui(player);
            require(gui.getTitle().getString().equals("커리큘럼"), "The chest title must be exactly 커리큘럼.");
            require(player.containerMenu.getType() == MenuType.GENERIC_9x6, "Curriculum must be a six-row chest.");
            for (int slot = 0; slot < 54; slot++) {
                if (!Set.of(0, 1, 2, 9, 10, 11, 18, 19, 20, 21, 27, 28, 29, 36, 37, 45, 46, 47, 48, 49, 52, 53).contains(slot)) require(player.containerMenu.getSlot(slot).getItem().isEmpty(), "Unused curriculum slots must be empty.");
            }
            ItemStack hat = gui.getGuiElement(9).getItemStack();
            require(hat.is(Items.LEATHER_HELMET) && hat.getHoverName().getString().equals("기숙사 배정 모자"),
                    "Row two, column one must contain the sorting hat leather helmet.");
            var hatEconomy = game.players().get(player.getUUID()).economy();
            long beforeHat = hatEconomy.diamond();
            gui.click(9, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            gui.click(9, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(hatEconomy.diamond() == beforeHat - 80 && MagicSchoolCurriculum.hasSortingHat(player.getUUID()),
                    "Buying the hat must charge 80 diamonds only once.");
            require(!MagicSchoolCurriculum.hasSortingHat(teammate), "A teammate must not receive another player's curriculum.");
            player.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
            player.containerMenu.clicked(8, 0, ContainerInput.PICKUP, player);
            require(player.containerMenu.getSlot(8).getItem().isEmpty(), "Curriculum must not accept stored items.");
            require(player.containerMenu.getCarried().is(Items.DIAMOND), "An attempted deposit must preserve the carried item.");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            gui.close();

            require(place(game, player.getUUID(), plot.east(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Student placement failed.");
            var student = (FreshmanTower) lane.towers().stream().filter(tower -> tower instanceof FreshmanTower).findFirst().orElseThrow();
            require(dispatcher.execute(command(student.managementPosition()), player.createCommandSourceStack()) == 0,
                    "Students must not open the Hogwarts curriculum.");
            PlayerLane otherLane = game.playerLane(teammate).orElseThrow();
            require(place(game, teammate, plot.west(), MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "Teammate school placement failed.");
            var otherSchool = otherLane.towers().getFirst();
            sent.clear();
            manager.dialogService().showTowerDetails(player, game, otherSchool);
            require(sent.stream().filter(packet -> packet instanceof ClientboundShowDialogPacket)
                    .noneMatch(packet -> packet.toString().contains("/semiontd magic_school curriculum")),
                    "Another player's school must not expose its management button.");
            require(dispatcher.execute(command(otherSchool.managementPosition()), player.createCommandSourceStack()) == 0,
                    "A forged command must not open another player's curriculum.");
            require(dispatcher.execute(command(school.managementPosition()), player.createCommandSourceStack()) == 1,
                    "The owner may reopen research.");
            CurriculumGui stale = (CurriculumGui) SguiUtils.getCurrentGui(player);
            lane.removeTower(school);
            stale.onTick();
            require(SguiUtils.getCurrentGui(player) == null, "Replacing the school must close its old curriculum UI.");
            require(place(game, player.getUUID(), plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "A school can be replaced for free.");
            school = (HogwartsTower) lane.towers().stream().filter(HogwartsTower.class::isInstance).findFirst().orElseThrow();
            require(dispatcher.execute(command(school.managementPosition()), player.createCommandSourceStack()) == 1,
                    "The replacement school must expose its curriculum command.");
            stale = (CurriculumGui) SguiUtils.getCurrentGui(player);
            lane.removeTower(school);
            stale.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(SguiUtils.getCurrentGui(player) == null, "A stale click after sale must close research.");
            require(dispatcher.execute(command(school.managementPosition()), player.createCommandSourceStack()) == 0,
                    "Removed schools must not open research.");
            require(dispatcher.execute(command, player.createCommandSourceStack()) == 0, "A stale dialog must not open a removed school.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            if (SguiUtils.getCurrentGui(player) instanceof CurriculumGui gui) {
                gui.close();
            }
            player.connection = connection;
            game.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void hogwartsStaysStillAcrossCombatFinalDefenseAndRoundReset(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, position(context, 3, 1, 3));
            var entity = school.runtimeEntity(lane).orElseThrow();
            Vec3 original = entity.position();
            var target = monster(context, lane, position(context, 9, 1, 3));
            var goal = new TowerAttackMonsterGoal(entity);
            entity.moveTowardTarget(target.position(), entity.chaseSpeedModifier());
            entity.knockback(1.0, 1.0, 0.0, entity.damageSources().generic(), 1.0F);
            for (int tick = 0; tick < 40; tick++) {
                goal.tick();
                entity.tick();
            }
            require(entity.position().distanceToSqr(original) < 0.0001,
                    "Hogwarts must remain on its plot during combat: " + original + " -> " + entity.position());
            require(target.getHealth() == 100, "Hogwarts must not attack.");
            require(entity.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "Hogwarts must have zero movement speed.");
            school.moveToFinalDefense(lane, position(context, 12, 1, 12));
            require(!school.deployedAtFinalDefense() && school.position().equals(school.originalPosition()),
                    "Hogwarts must stay in its lane during final defense.");
            school.resetForRound(lane);
            require(entity.position().distanceToSqr(original) < 0.0001, "Round reset must keep the building in place.");
        } catch (Exception | AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
        } finally {
            lane.clearTowers();
            lane.activeMonsters().forEach(monster -> {
                var entity = context.getLevel().getEntity(monster.minecraftEntityId());
                if (entity != null) entity.discard();
            });
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void studentsUseSeparatePlayerVisualsAndDefaultRangedSpell(GameTestHelper context) throws Exception {
        PlayerLane lane = lane(context);
        SemionMonsterEntity target = null;
        try {
            FreshmanTower first = (FreshmanTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 3));
            FreshmanTower second = (FreshmanTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 5));
            ServerPlayer firstVisual = (ServerPlayer) FakePlayerTowerVisuals.visualEntity(first).orElseThrow();
            ServerPlayer secondVisual = (ServerPlayer) FakePlayerTowerVisuals.visualEntity(second).orElseThrow();
            require(!firstVisual.getUUID().equals(secondVisual.getUUID()), "Students must have independent player profiles.");
            var entity = first.runtimeEntity(lane).orElseThrow();
            require(first.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "Placement must automatically assign Expelliarmus.");
            first.markWaveStarted(1);
            require(FakePlayerTowerVisuals.resolveInteractionAnchor(context.getLevel(), firstVisual.getId()) == entity,
                    "Clicking the player visual must resolve its tower.");
            target = monster(context, lane, position(context, 9, 1, 3));
            var goal = new TowerAttackMonsterGoal(entity);
            goal.tick();
            require(target.getHealth() == 76, "Expelliarmus at six blocks must deal 80% of the 30 attack damage.");
            require(first.roundMagicDamageDealt() == 24 && first.roundPhysicalDamageDealt() == 0,
                    "The actual attack must be recorded as magic damage only.");
            require(entity.getMaxHealth() == 200 && entity.attackRange() == 8 && entity.attackIntervalTicks() == 22,
                    "The student's agreed base combat stats must reach its entity.");
            verifyRangedPaletteEvent(entity.getUUID());
            for (int tick = 0; tick < 21; tick++) goal.tick();
            require(target.getHealth() == 76, "A student must wait 22 ticks before its next attack.");
            target.setInvulnerableTime(0);
            goal.tick();
            require(target.getHealth() == 52, "The next spell cast must deal the same 80% damage exactly once.");
            first.tick(lane);
            require(firstVisual.position().distanceToSqr(entity.position()) < 0.0001, "The player visual must follow its anchor.");
            entity.discard();
            first.syncHealth(0);
            first.onDeath(lane);
            require(FakePlayerTowerVisuals.visualEntity(first).isEmpty(), "Death must remove the player visual.");
            first.resetForRound(lane);
            require(first.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "Respawn must preserve the assigned spell.");
            require(FakePlayerTowerVisuals.visualEntity(first).isPresent(), "Round reset must recreate the student visual.");
            require(first.runtimeEntity(lane).orElseThrow().getHealth() == 200, "Round reset must restore base health.");
            lane.removeTower(first);
            require(FakePlayerTowerVisuals.visualEntity(first).isEmpty(), "Removal must clean up the player visual.");
            require(FakePlayerTowerVisuals.visualEntity(second).isPresent(), "Removing one student must preserve the other visual.");
        } finally {
            if (target != null) target.discard();
            lane.clearTowers();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void wizardDialogOpensSixSpellRowsAndProtectsSelectionOwnership(GameTestHelper context) throws Exception {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, player.getUUID(), teammate);
        var connection = player.connection;
        List<Packet<?>> sent = new ArrayList<>();
        try {
            var manager = new SemionGameManager();
            var activeGame = SemionGameManager.class.getDeclaredField("activeGame");
            activeGame.setAccessible(true);
            activeGame.set(manager, game);
            player.connection = new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    sent.add(packet);
                }
            };
            PlayerLane lane = game.playerLane(player.getUUID()).orElseThrow();
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            require(place(game, player.getUUID(), plot, MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS,
                    "A student must be placeable before Hogwarts.");
            FreshmanTower student = (FreshmanTower) lane.towers().getFirst();
            require(student.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "A new student's spell must already be assigned.");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var visual = FakePlayerTowerVisuals.visualEntity(student).orElseThrow();
            var clickedEntity = FakePlayerTowerVisuals.resolveInteractionAnchor(context.getLevel(),
                    new ServerboundInteractPacket(visual.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false).entityId());
            require(clickedEntity == student.runtimeEntity(lane).orElseThrow(), "The player-model click packet must resolve the student anchor.");
            require(SemionTowerInteractionService.handleUse(manager, player, player.level(), InteractionHand.MAIN_HAND,
                    clickedEntity, new EntityHitResult(clickedEntity)) == InteractionResult.SUCCESS, "Right-clicking the player model must open the dialog.");
            GridPosition position = student.managementPosition();
            String command = spellCommand(position);
            require(sent.stream().filter(packet -> packet instanceof ClientboundShowDialogPacket)
                    .anyMatch(packet -> packet.toString().contains("주문") && packet.toString().contains("/" + command)),
                    "The student's dialog must offer its own Spell button.");
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            SemionCommands.register(dispatcher, manager, null, null, null, null, Path.of("run/config/semion-td"));
            require(dispatcher.execute(command, player.createCommandSourceStack()) == 1, "The Spell button command must open its UI.");
            SpellGui gui = (SpellGui) SguiUtils.getCurrentGui(player);
            require(gui.getTitle().getString().equals("주문"), "The spell UI must be titled 주문.");
            require(player.containerMenu.getType() == MenuType.GENERIC_9x6, "Spells must use a six-row chest.");
            List<String> labels = List.of("1단계 주문", "2단계 주문", "3단계 주문", "4단계 주문", "5단계 주문", "용서받지 못할 저주");
            for (int row = 0; row < 6; row++) {
                require(gui.getGuiElement(row * 9).getItemStack().getHoverName().getString().equals(labels.get(row)),
                        "The first slot of row " + row + " must label its spell tier.");
            }
            ItemStack spell = gui.getGuiElement(1).getItemStack();
            require(spell.getHoverName().getString().equals("엑스펠리아르무스"), "Expelliarmus must be in the first row.");
            var lore = spell.get(DataComponents.LORE).lines().stream().map(line -> line.getString()).toList();
            require(lore.contains("기본 공격이 공격력 80%의 마법 피해를 입힙니다."), "The spell must show the exact damage description.");
            require(lore.contains("현재 지정된 주문"), "The default spell must be visibly selected.");
            var rennervateLore = gui.getGuiElement(39).getItemStack().get(DataComponents.LORE).lines();
            var rennervateAttack = rennervateLore.stream()
                    .filter(line -> line.getString().startsWith("기본 공격이")).findFirst().orElseThrow();
            require(rennervateAttack.getString().equals("기본 공격이 공격력 110%의 마법 피해를 입힙니다."),
                    "Rennervate lore must not expose floating-point percentage artifacts.");
            var highlighted = new StringBuilder();
            rennervateAttack.visit((style, text) -> {
                if (style.getColor() != null && style.getColor().getValue() == 0xffb86c) highlighted.append(text);
                return java.util.Optional.<Void>empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            require(highlighted.toString().equals("공격력 110%"), "Only the attack coefficient must receive light orange emphasis.");
            int[] columns = new int[6];
            for (var registered : MagicSchoolSpell.values()) {
                int slot = (registered.tier() - 1) * 9 + 1 + columns[registered.tier() - 1]++;
                require(gui.getGuiElement(slot).getItemStack().getHoverName().getString().equals(registered.displayName()),
                        "Every spell must appear in its stable tier slot with its Korean name.");
            }
            gui.click(2, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(student.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "The Muggle augment spell must remain unselectable.");
            gui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(student.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "Clicking Expelliarmus must select it without toggling it off.");
            player.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
            player.containerMenu.clicked(8, 0, ContainerInput.PICKUP, player);
            require(player.containerMenu.getCarried().is(Items.DIAMOND), "The spell UI must not consume deposited items.");
            require(player.containerMenu.getSlot(8).getItem().isEmpty(), "The spell UI must not store items.");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            gui.close();

            require(place(game, player.getUUID(), plot.east(), MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "Hogwarts placement failed.");
            var school = lane.towers().stream().filter(tower -> tower instanceof HogwartsTower).findFirst().orElseThrow();
            require(dispatcher.execute(spellCommand(school.managementPosition()), player.createCommandSourceStack()) == 0,
                    "Hogwarts must not open the wizard-only spell UI.");
            PlayerLane otherLane = game.playerLane(teammate).orElseThrow();
            require(place(game, teammate, plot.west(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Teammate student placement failed.");
            var other = otherLane.towers().getFirst();
            sent.clear();
            manager.dialogService().showTowerDetails(player, game, other);
            require(sent.stream().filter(packet -> packet instanceof ClientboundShowDialogPacket)
                    .noneMatch(packet -> packet.toString().contains("/semiontd magic_school spells")), "Only the owner may see the spell management button.");
            require(dispatcher.execute(spellCommand(other.managementPosition()), player.createCommandSourceStack()) == 0,
                    "A forged command must not edit a teammate's spell.");
            require(dispatcher.execute(command, player.createCommandSourceStack()) == 1, "Reopening the student's spell UI must succeed.");
            SpellGui stale = (SpellGui) SguiUtils.getCurrentGui(player);
            lane.removeTower(student);
            stale.onTick();
            require(SguiUtils.getCurrentGui(player) == null, "Selling or upgrading the student must close its old spell UI.");
            require(dispatcher.execute(command, player.createCommandSourceStack()) == 0, "A stale button must not reopen a removed student.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            if (SguiUtils.getCurrentGui(player) instanceof SpellGui gui) gui.close();
            player.connection = connection;
            game.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void expelliarmusUsesMagicResistanceAndOnlyDamagesThePrimaryTarget(GameTestHelper context) {
        PlayerLane lane = lane(context);
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            FreshmanTower wizard = (FreshmanTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 3));
            wizard.markWaveStarted(1);

            var entity = wizard.runtimeEntity(lane).orElseThrow();
            var armored = monster(context, lane, position(context, 8, 1, 3), 900, 0);
            var neighbor = monster(context, lane, position(context, 8, 1, 4));
            targets.add(armored);
            targets.add(neighbor);
            entity.recordCurrentAttackTarget(armored);
            new TowerAttackMonsterGoal(entity).tick();
            require(armored.getHealth() == 76, "Magic damage must bypass physical armor and deal exactly 80% attack damage.");
            require(neighbor.getHealth() == 100, "Expelliarmus must not splash onto neighboring monsters.");
            armored.discard();
            var resistant = monster(context, lane, position(context, 8, 1, 3), 0, 100);
            targets.add(resistant);
            entity.recordCurrentAttackTarget(resistant);
            new TowerAttackMonsterGoal(entity).tick();
            require(resistant.getHealth() == 88, "100 magic resistance must reduce a 24-damage spell to 12.");
            entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, 0.5, 80);
            resistant.setInvulnerableTime(0);
            new TowerAttackMonsterGoal(entity).tick();
            require(resistant.getHealth() == 70, "Expelliarmus must use current attack damage, including a buff exactly once.");
            require(wizard.roundMagicDamageDealt() == 54 && wizard.roundPhysicalDamageDealt() == 0,
                    "All applied spell damage must be attributed to magic combat statistics.");
            require(neighbor.getHealth() == 100, "Repeated casts must only damage the primary target.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            targets.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void hogwartsIsFreeHasNoUpgradesAndNeverUsesCapacity(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var economy = game.players().get(owner).economy();
            economy.overrideStartingValues(0, 0, 0, 0);
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(.3));
            int limit = game.towerLimitForPlayer(owner);
            for (int i = 0; i < limit; i++) {
                var filler = ProductionTowerCatalog.find(kim.biryeong.semiontd.tower.villager.VillagerTowers.T1_GOLEM_TOWER.id()).orElseThrow()
                        .create(owner, TeamId.RED, lane.laneId(), new GridPosition(plot.getX() + i + 1, plot.getY(), plot.getZ()));
                lane.addTower(filler);
            }
            require(place(game, owner, plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS,
                    "A school must be placeable at zero funds and full capacity.");

            var placedPosition = kim.biryeong.semiontd.tower.TowerPlacementPositions.resolveGrid(lane, plot).orElseThrow();
            var school = (HogwartsTower) lane.towerAt(placedPosition);
            require(school != null, "The placed school must exist at the resolved placement position.");
            require(school.currentMaxHealth() == 1 && school.paidMineralCost() == 0 && economy.diamond() == 0,
                    "The single school tier must have one health and zero cost.");
            require(game.towerCapacityUsed(owner) == limit, "School must not consume capacity.");
            for (String removed : List.of("magic_school_hogwarts_t2", "magic_school_hogwarts_t3")) {
                require(ProductionTowerService.placeTower(game, owner, plot.north(), removed) == TowerPlacementResult.UNKNOWN_TOWER,
                        "Retired school tiers must not be placeable.");
                require(ProductionTowerService.upgradeTower(game, owner, school.managementPosition(), removed)
                        == TowerUpgradeResult.TOWER_NOT_UPGRADABLE, "Retired school upgrades must not be callable.");
            }
            require(place(game, owner, plot.north(), MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.TOWER_NOT_ALLOWED,
                    "Free schools must still be limited to one per player.");
            require(ProductionTowerService.sellTower(game, owner, school.managementPosition()).result() == TowerSellResult.SUCCESS,
                    "A free school must be sellable.");
            require(economy.diamond() == 0, "Selling a free school must not generate currency.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void schoolIgnoresAttacksAndCannotDefendAlone(GameTestHelper context) {
        PlayerLane lane = lane(context);
        SemionMonsterEntity enemy = null;
        try {
            enemy = monster(context, lane, position(context, 8, 1, 3));
            for (TowerType type : List.of(MagicSchoolTowers.HOGWARTS)) {
                var school = (HogwartsTower) add(lane, type, position(context, 3, 1, 3));
                var entity = school.runtimeEntity(lane).orElseThrow();
                var acquire = new AcquireLaneDefenseTargetGoal(enemy);
                require(!acquire.canUse(), "A monster must ignore a lone Hogwarts of every tier.");
                entity.hurtServer(context.getLevel(), context.getLevel().damageSources().mobAttack(enemy), 500);
                entity.hurtIgnoringReductions(context.getLevel().damageSources().magic(), 500);
                require(entity.getHealth() == 1, "Direct and reduction-ignoring damage must leave Hogwarts unharmed.");
                lane.removeTower(school);
            }
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, position(context, 3, 1, 3));
            var student = add(lane, MagicSchoolTowers.FRESHMAN, position(context, 4, 1, 3));
            var acquire = new AcquireLaneDefenseTargetGoal(enemy);
            acquire.start();
            require(enemy.getTarget() == student.runtimeEntity(lane).orElseThrow(), "The enemy must target the wizard rather than the school.");
            lane.tick(context.getLevel().getServer());
            require(!lane.laneDefenseBroken(), "The living wizard must still count as defense.");
            student.runtimeEntity(lane).orElseThrow().discard();
            student.syncHealth(0);
            lane.tick(context.getLevel().getServer());
            require(lane.laneDefenseBroken(), "Hogwarts alone must fail the lane defense check.");
            require(school.runtimeEntity(lane).orElseThrow().isAlive(), "Hogwarts remains intact when defense fails.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            if (enemy != null) enemy.discard();
            lane.clearTowers();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spellTiersSurviveSchoolReplacementAndClearWithMatch(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            var participant = game.players().get(owner);
            var economy = participant.economy();
            var job = participant.job().orElseThrow();
            var jobContext = new JobContext(game, participant);
            require(MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy) == MagicSchoolCurriculum.PurchaseResult.PURCHASED,
                    "A paid offer must unlock for its owner.");
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(teammate, 2), "Teammates must research independently.");
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            require(place(game, owner, plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "School placement failed.");
            var school = lane.towers().getFirst();
            require(ProductionTowerService.sellTower(game, owner, school.managementPosition()).result() == TowerSellResult.SUCCESS,
                    "School sale failed.");
            require(MagicSchoolCurriculum.isSpellTierUnlocked(owner, 2), "School sale must retain research for existing and future wizards.");
            job.onEliminated(jobContext);
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 2), "Elimination must clear research.");
            MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
            job.onMatchStarted(jobContext);
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 2), "A new match must clear stale research.");
            MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 2), "Actual game shutdown must clear research.");
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void proficiencyAppliesAtWaveStartAndSurvivesBuffExpiryReloadAndRespawn(GameTestHelper context) {
        PlayerLane lane = lane(context);
        SemionMonsterEntity target = null;
        try {
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 3));
            var second = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 5));
            var entity = student.runtimeEntity(lane).orElseThrow();
            lane.markWaveStarted(1);
            requireClose(11, student.proficiency(), "Round one must grant 10 + 1 proficiency.");
            requireClose(11, second.proficiency(), "Each student must earn its own proficiency.");
            requireClose(203.3, entity.getMaxHealth(), "Proficiency health must reach the entity before combat.");
            target = monster(context, lane, position(context, 8, 1, 3));
            new TowerAttackMonsterGoal(entity).tick();
            requireClose(75.604, target.getHealth(), "The first spell must include 1.65% proficiency damage exactly once.");
            student.onWaveStarted(lane, 1);
            requireClose(11, student.proficiency(), "A repeated wave hook must not award twice.");
            lane.markWaveStarted(2);
            requireClose(23, student.proficiency(), "Round two must grant another 12 proficiency.");
            student.gainProficiency(1000, lane);
            requireClose(100, student.proficiency(), "A freshman must cap at 100.");
            requireClose(230, entity.getMaxHealth(), "Full proficiency must give 15% maximum health.");
            requireClose(34.5, entity.attackDamageAmount(target), "Full proficiency must give 15% attack damage.");
            entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, 0.5, 2);
            requireClose(345, entity.getMaxHealth(), "Temporary health buffs must include existing growth.");
            entity.tick();
            entity.tick();
            requireClose(230, entity.getMaxHealth(), "Buff expiry must preserve proficiency health.");
            var override = new TowerBalanceConfig.TowerStats(null, 240.0, null, 36.0, null, null);
            var config = new TowerBalanceConfig(java.util.Map.of(MagicSchoolTowers.FRESHMAN.id(), override), java.util.Map.of(), java.util.Map.of())
                    .withMissingDefaults(TowerBalanceConfig.defaultConfig());
            student.refreshType(kim.biryeong.semiontd.config.TowerBalanceRuntime.resolve(MagicSchoolTowers.FRESHMAN, config), lane);
            requireClose(276, entity.getMaxHealth(), "Balance reload must rebase proficiency onto the new health.");
            requireClose(41.4, entity.attackDamageAmount(target), "Balance reload must update base damage without compounding growth.");
            entity.discard();
            student.syncHealth(0);
            student.onDeath(lane);
            student.resetForRound(lane);
            requireClose(100, student.proficiency(), "Death and reset must retain proficiency.");
            requireClose(276, student.runtimeEntity(lane).orElseThrow().getHealth(), "Respawn must restore the grown health.");
            require(student.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "Growth and respawn must preserve spell selection.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            if (target != null) target.discard();
            lane.clearTowers();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void sortingHatAndFullProficiencyGateEveryHouseUpgradeAndChargePerStudent(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            var economy = game.players().get(owner).economy();
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            require(place(game, owner, plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "School placement failed.");
            var school = (HogwartsTower) lane.towers().getFirst();
            require(place(game, owner, plot.east(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Student placement failed.");
            var first = (MagicSchoolWizardTower) lane.towers().stream().filter(tower -> tower instanceof MagicSchoolWizardTower).findFirst().orElseThrow();
            first.onWaveStarted(lane, 1);
            first.gainProficiency(1000, lane);
            long before = economy.diamond();
            require(ProductionTowerService.upgradeTower(game, owner, first.managementPosition(), MagicSchoolTowers.GRYFFINDOR.id())
                    == TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET, "Full proficiency alone must not bypass the sorting hat.");
            require(economy.diamond() == before, "Blocked graduation must not charge diamonds.");
            require(MagicSchoolCurriculum.purchaseSortingHat(game, teammate, school) == MagicSchoolCurriculum.PurchaseResult.INVALID_SCHOOL,
                    "A teammate cannot purchase through another player's school.");
            economy.overrideStartingValues(79, 50, 0, 0);
            require(purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT) == MagicSchoolCurriculum.PurchaseResult.NOT_ENOUGH_DIAMONDS,
                    "79 diamonds must not buy the hat.");
            require(economy.diamond() == 79 && !MagicSchoolCurriculum.hasSortingHat(owner), "Failed purchase must leave funds and unlock state unchanged.");
            economy.addDiamond(1);
            require(purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT) == MagicSchoolCurriculum.PurchaseResult.PURCHASED,
                    "80 diamonds must unlock house graduation.");
            require(economy.diamond() == 0 && economy.emerald() == 50, "Hat purchase must spend only diamonds.");
            economy.addDiamond(10000);
            require(purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT) == MagicSchoolCurriculum.PurchaseResult.ALREADY_PURCHASED,
                    "The curriculum unlock is a one-time purchase.");
            require(economy.diamond() == 10000, "Duplicate purchase must not charge again.");
            for (TowerType type : MagicSchoolTowers.houseWizards()) {
                MagicSchoolWizardTower student = first;
                if (type != MagicSchoolTowers.GRYFFINDOR) {
                    require(place(game, owner, plot.east(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Next student placement failed.");
                    student = (MagicSchoolWizardTower) lane.towers().stream().filter(tower -> tower instanceof MagicSchoolWizardTower).findFirst().orElseThrow();
                    student.onWaveStarted(lane, 1);
                    student.gainProficiency(88, lane);
                    require(ProductionTowerService.upgradeTower(game, owner, student.managementPosition(), type.id())
                            == TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET, "99 proficiency must not allow graduation.");
                    student.gainProficiency(1, lane);
                }
                var position = student.managementPosition();
                before = economy.diamond();
                require(ProductionTowerService.upgradeTower(game, owner, position, type.id()) == TowerUpgradeResult.SUCCESS,
                        "A mastered freshman with the sorting hat must be able to choose every house.");
                var graduate = (HouseWizardTower) lane.towerAt(position);
                require(economy.diamond() == before - 200, "Each student's graduation must cost 200 diamonds.");
                require(game.towerCapacityUsed(owner) == 2, "Graduation must retain two capacity slots.");
                requireClose(0, graduate.proficiency(), "Graduation must reset freshman proficiency.");
                requireClose(300, graduate.maxProficiency(), "T2 must allow up to 300 proficiency.");
                var entity = graduate.runtimeEntity(lane).orElseThrow();
                requireClose(type == MagicSchoolTowers.HUFFLEPUFF ? 374 : 340, entity.getMaxHealth(), "House health must exclude reset proficiency.");
                requireClose(type == MagicSchoolTowers.SLYTHERIN ? 55 : 50, entity.attackDamageAmount(null), "House damage must exclude reset proficiency.");
                require(entity.attackIntervalTicks() == (type == MagicSchoolTowers.GRYFFINDOR ? 17 : 18), "Only Gryffindor gets a one-tick reduction.");
                require(FakePlayerTowerVisuals.visualEntity(graduate).isPresent(), "Each house must retain a player model.");
                require(graduate.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS, "Graduation must retain the chosen spell.");
                graduate.onWaveStarted(lane, 1);
                requireClose(0, graduate.proficiency(), "Graduation must not reset the wave award guard.");
                graduate.onWaveStarted(lane, 2);
                requireClose(type == MagicSchoolTowers.RAVENCLAW ? 13.8 : 12, graduate.proficiency(), "Ravenclaw must gain exactly 15% extra, including fractions.");
                require(ProductionTowerService.sellTower(game, owner, position).result() == TowerSellResult.SUCCESS, "Graduate sale failed.");
            }
            require(ProductionTowerService.sellTower(game, owner, school.managementPosition()).result() == TowerSellResult.SUCCESS, "School sale failed.");
            require(MagicSchoolCurriculum.hasSortingHat(owner), "School sale must retain purchased curriculum.");
            require(!MagicSchoolCurriculum.hasSortingHat(teammate), "Curriculum must not leak to teammates.");
            var participant = game.players().get(owner);
            participant.job().orElseThrow().onEliminated(new JobContext(game, participant));
            require(!MagicSchoolCurriculum.hasSortingHat(owner), "Elimination must clear the curriculum unlock.");
            require(place(game, owner, plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "Replacement school placement failed.");
            school = (HogwartsTower) lane.towers().getFirst();
            purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT);
            participant.job().orElseThrow().onMatchStarted(new JobContext(game, participant));
            require(!MagicSchoolCurriculum.hasSortingHat(owner), "A new match must clear stale curriculum.");
            purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT);
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        require(!MagicSchoolCurriculum.hasSortingHat(owner), "Actual game shutdown must clear curriculum purchases.");
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void twoSlotStudentsRespectPlacementCapacityAndStaleCurriculumCannotCharge(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            BlockPos plot = BlockPos.containing(lane.laneLayout().positionAt(0.3));
            require(place(game, owner, plot, MagicSchoolTowers.HOGWARTS) == TowerPlacementResult.SUCCESS, "School placement failed.");
            var school = (HogwartsTower) lane.towers().getFirst();
            require(place(game, owner, plot.east(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "First student placement failed.");
            require(place(game, owner, plot.west(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.SUCCESS, "Second student placement failed.");
            require(game.towerCapacityUsed(owner) == 4, "Two students must consume four slots.");
            require(place(game, owner, plot.north(), MagicSchoolTowers.FRESHMAN) == TowerPlacementResult.TOWER_LIMIT_REACHED,
                    "A third two-slot student must not fit the initial five-slot limit.");
            var gui = new CurriculumGui(player, game, school);
            long before = game.players().get(owner).economy().diamond();
            lane.removeTower(school);
            gui.click(9, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(!MagicSchoolCurriculum.hasSortingHat(owner) && game.players().get(owner).economy().diamond() == before,
                    "A stale curriculum click must not spend diamonds or grant the hat.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void housesUseDistinctTargetsAndIgnoreBetterTargetsOutsideRange(GameTestHelper context) {
        PlayerLane lane = lane(context);
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            var near = monster(context, lane, position(context, 4, 1, 3), 0, 0, 200);
            var far = monster(context, lane, position(context, 10, 1, 3), 0, 0, 400);
            var tank = monster(context, lane, position(context, 6, 1, 4), 0, 0, 10000);
            var injured = monster(context, lane, position(context, 7, 1, 4), 0, 0, 600);
            var outside = monster(context, lane, position(context, 14, 1, 3), 0, 0, 50000);
            targets.addAll(List.of(near, far, tank, injured, outside));
            var wizardTypes = new ArrayList<>(MagicSchoolTowers.houseWizards());
            wizardTypes.addAll(MagicSchoolTowers.archWizards());
            for (TowerType type : wizardTypes) {
                setMonsterHealth(near, 160);
                setMonsterHealth(far, 320);
                setMonsterHealth(tank, 500);
                setMonsterHealth(injured, 120);
                setMonsterHealth(outside, 1);
                var wizard = (HouseWizardTower) add(lane, type, position(context, 3, 1, 3));
                var source = wizard.runtimeEntity(lane).orElseThrow();
                SemionMonsterEntity expected = MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.GRYFFINDOR) ? tank
                        : MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.HUFFLEPUFF) ? far
                        : MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.RAVENCLAW) ? near : injured;
                source.recordCurrentAttackTarget(outside);
                double before = expected.getHealth();
                new TowerAttackMonsterGoal(source).tick();
                require(source.currentAttackTarget() == expected, "Each house must override a cached target with its own in-range priority: " + type.id());
                requireClose(before - type.damage() * .8, expected.getHealth(), "The actual spell must damage the selected target.");
                requireClose(1, outside.getHealth(), "A more attractive out-of-range target must be ignored.");
                if (type == MagicSchoolTowers.CUNNING_ARCHWIZARD) {
                    setMonsterHealth(far, 1);
                    double previousTargetHealth = injured.getHealth();
                    new TowerAttackMonsterGoal(source).tick();
                    require(!far.isAlive() && injured.getHealth() == previousTargetHealth,
                            "Slytherin must re-evaluate absolute current health and kill the new weakest target without hitting the old one.");
                }
                lane.removeTower(wizard);
            }
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            targets.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void curriculumButtonsChargeAndRefreshExistingAndFutureStudents(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            GridPosition plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            HogwartsTower school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN,
                    new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            var allyLane = game.playerLane(teammate).orElseThrow();
            var ally = (MagicSchoolWizardTower) add(allyLane, MagicSchoolTowers.FRESHMAN,
                    GridPosition.from(BlockPos.containing(allyLane.laneLayout().positionAt(.3))));
            var economy = game.players().get(owner).economy();
            economy.addDiamond(20000);
            var gui = new CurriculumGui(player, game, school);
            var expectedItems = List.of(Items.BLAZE_ROD, Items.NETHERITE_CHESTPLATE, Items.BOOK,
                    Items.LEATHER_HELMET, Items.BREEZE_ROD, Items.ENCHANTED_BOOK, Items.SKELETON_SKULL, Items.DRIED_GHAST, Items.IRON_SWORD, Items.ENCHANTED_BOOK,
                    Items.ENDER_PEARL, Items.POTION, Items.FEATHER, Items.BARREL, Items.TNT);
            int[] expectedSlots = {0, 1, 2, 9, 10, 11, 21, 20, 19, 18, 28, 29, 27, 36, 37};
            for (int i = 0; i < Upgrade.values().length; i++) {
                Upgrade upgrade = Upgrade.values()[i];
                ItemStack item = gui.getGuiElement(expectedSlots[i]).getItemStack();
                require(item.is(expectedItems.get(i)) && item.getHoverName().getString().equals(upgrade.displayName()),
                        "Wrong curriculum item or name at slot " + upgrade.slot());
            }
            var entity = student.runtimeEntity(lane).orElseThrow();
            entity.setPos(entity.position().add(.25, 0, .25));
            Vec3 moved = entity.position();
            long before = economy.diamond();
            clickWithRoundReset(gui, owner, 0);
            clickWithRoundReset(gui, owner, 1);
            require(economy.diamond() == before, "Both first lessons must be free.");
            requireClose(31.2, entity.attackDamageAmount(null), "The live attack stat must refresh.");
            requireClose(206, entity.getMaxHealth(), "The live maximum health must refresh.");
            require(entity.position().distanceToSqr(moved) < .000001, "A curriculum purchase must not teleport a fighting student.");
            requireClose(200, ally.currentMaxHealth(), "Curriculum must not buff a teammate.");
            clickWithRoundReset(gui, owner, 0);
            clickWithRoundReset(gui, owner, 1);
            clickWithRoundReset(gui, owner, 2);
            require(economy.diamond() == before - 100 - 100 - 120, "Subsequent lesson prices must use completed counts.");
            student.gainProficiency(8, lane);
            requireClose(10, student.proficiency(), "History must increase ordinary proficiency gains.");
            require(entity.position().distanceToSqr(moved) < .000001, "Proficiency gain must preserve combat position.");
            clickWithRoundReset(gui, owner, 10);
            long afterWand = economy.diamond();
            clickWithRoundReset(gui, owner, 10);
            require(economy.diamond() == afterWand, "Custom wands may only be purchased once.");
            var future = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.HUFFLEPUFF,
                    new GridPosition(plot.x() - 1, plot.y(), plot.z()));
            requireClose(340 * 1.1 * 1.06 * 1.1, future.runtimeEntity(lane).orElseThrow().getMaxHealth(),
                    "Newly placed house wizards must inherit lessons and custom wands.");
            MagicSchoolTestSetup.resetCurriculumRoundLimit(owner);
            long beforeRejectedTier = economy.diamond();
            String prerequisiteLore = gui.getGuiElement(47).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                    .lines().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));
            require(prerequisiteLore.contains("선행 필요: 3단계 주문 해금") && prerequisiteLore.contains("500 다이아"),
                    "A missing prerequisite must be shown alongside the spell tier's price.");
            gui.click(47, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 4) && economy.diamond() == beforeRejectedTier
                            && MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()),
                    "An out-of-order GUI unlock must preserve both diamonds and the round allowance.");
            require(MagicSchoolCurriculum.unlockSpellTier(game, owner, school, 6)
                    == MagicSchoolCurriculum.PurchaseResult.AUGMENT_REQUIRED,
                    "Curses require the exclusive augment, not a curriculum purchase.");
            for (int tier = 2; tier <= 5; tier++) {
                int slot = 45 + tier - 2;
                require(gui.getGuiElement(slot).getItemStack().getHoverName().getString()
                        .equals(MagicSchoolCurriculum.spellTierName(tier) + " 해금"), "The last row must list tiers in order.");
                long old = economy.diamond();
                clickWithRoundReset(gui, owner, slot);
                clickWithRoundReset(gui, owner, slot);
                require(economy.diamond() == old - MagicSchoolCurriculum.spellTierCost(tier), "A spell tier must charge exactly once.");
                require(MagicSchoolCurriculum.isSpellTierUnlocked(owner, tier), "The clicked tier must unlock.");
                require(!MagicSchoolCurriculum.isSpellTierUnlocked(teammate, tier), "A spell tier must be owner scoped.");
            }
            lane.killTower(student);
            clickWithRoundReset(gui, owner, 1);
            require(student.health() == 0, "A health lesson must not revive a defeated student.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void mentorsRequireAnOwnedLivingHigherTierWithinOneBlockAndRewardOnce(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            var economy = new kim.biryeong.semiontd.game.PlayerEconomy(EconomyConfig.defaultConfig());
            economy.addDiamond(1000);
            MagicSchoolCurriculum.purchase(lane.ownerPlayer(), Upgrade.MENTOR, economy);
            MagicSchoolCurriculum.purchase(lane.ownerPlayer(), Upgrade.MAGIC_HISTORY, economy);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 3));
            var mentor = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.GRYFFINDOR, position(context, 4, 1, 3));
            var secondMentor = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.HUFFLEPUFF, position(context, 3, 1, 2));
            var diagonal = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 3, 1, 4));
            add(lane, MagicSchoolTowers.HOGWARTS, position(context, 3, 1, 5));
            var isolated = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, position(context, 7, 1, 3));
            var deadMentor = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.RAVENCLAW, position(context, 7, 1, 4));
            lane.killTower(deadMentor);
            var foreign = ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id()).orElseThrow()
                    .create(UUID.randomUUID(), TeamId.RED, 1, position(context, 8, 1, 3));
            lane.addTower(foreign);
            lane.markWaveStarted(1);
            requireClose((11 + 8) * 1.25, student.proficiency(), "Multiple mentors must award one history-adjusted bonus.");
            requireClose(11 * 1.25, diagonal.proficiency(), "Diagonal distance, equal tiers, and Hogwarts must not qualify.");
            requireClose(11 * 1.25, isolated.proficiency(), "Dead and foreign mentors must not qualify.");
            requireClose(11 * 1.25, mentor.proficiency(), "T2 students need a higher tier, not another T2.");
            lane.markWaveStarted(1);
            requireClose(19 * 1.25, student.proficiency(), "A repeated wave hook must not pay mentor XP twice.");
            lane.killTower(mentor);
            lane.killTower(secondMentor);
            lane.markWaveStarted(2);
            requireClose((19 + 12) * 1.25, student.proficiency(), "Mentor eligibility must be evaluated again each wave.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            lane.clearTowers();
            MagicSchoolCurriculum.clear(lane.ownerPlayer());
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void deathEaterToggleSpawnsNextWaveAndRewardsOnlyLivingOwnerWizardsOnce(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            PlayerLane allyLane = game.playerLane(teammate).orElseThrow();
            GridPosition plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            HogwartsTower school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            var ravenclaw = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.RAVENCLAW, new GridPosition(plot.x() - 1, plot.y(), plot.z()));
            var defeated = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, new GridPosition(plot.x(), plot.y(), plot.z() + 1));
            var ally = (MagicSchoolWizardTower) add(allyLane, MagicSchoolTowers.FRESHMAN,
                    GridPosition.from(BlockPos.containing(allyLane.laneLayout().positionAt(.3))));
            var economy = game.players().get(owner).economy();
            purchaseWithRoundReset(game, owner, school, Upgrade.MAGIC_HISTORY);
            long before = economy.diamond();
            MagicSchoolTestSetup.resetCurriculumRoundLimit(owner);
            MagicSchoolCurriculum.toggleDeathEater(game, owner, school);
            require(economy.diamond() == before - 250 && MagicSchoolCurriculum.deathEaterEnabled(owner), "Unlock must cost 250 and enable the challenge.");
            require(lane.queuedSummonCount() == 0, "Buying the challenge must not spawn it during preparation.");
            lane.markWaveStarted(7);
            lane.markWaveStarted(7);
            require(lane.queuedSummonCount() == 1, "Exactly one Death Eater must be queued per enabled wave.");
            lane.tick(context.getLevel().getServer());
            Monster monster = lane.activeMonsters().stream().filter(m -> MagicSchoolDeathEaters.ID.equals(m.id())).findFirst().orElseThrow();
            var magmaCube = kim.biryeong.semiontd.summon.SummonRegistry.find("magma_cube").orElseThrow();
            Monster lateRound = MagicSchoolDeathEaters.create(lane, 20);
            requireClose(magmaCube.maxHealth() * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonHealthMultiplier(20),
                    lateRound.maxHealth(), "Late-round health scaling must follow the same income formula.");
            requireClose(magmaCube.attackDamage() * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonAttackDamageMultiplier(20),
                    lateRound.attackDamage(), "Late-round damage scaling must follow the same income formula.");
            requireClose(magmaCube.maxHealth() * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonHealthMultiplier(7), monster.maxHealth(), "Health must use the income unit's round scaling.");
            requireClose(magmaCube.attackDamage() * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonAttackDamageMultiplier(7), monster.attackDamage(), "Damage must use the income unit's round scaling.");
            requireClose(magmaCube.armor(), monster.armor(), "Death Eater armor must follow Magma Cube.");
            requireClose(magmaCube.resistance(), monster.resistance(), "Death Eater magic resistance must follow Magma Cube.");
            require(monster.damageType() == DamageType.MAGIC && monster.attackKind() == magmaCube.attackKind(),
                    "Death Eater must use Magma Cube's melee magic attack.");
            var target = (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
            require(monster.entityTypeId().equals("minecraft:vex") && target.getCustomName().getString().equals("죽음을 먹는 자"), "Spawned challenge must have its Vex visual and Korean name.");
            MagicSchoolCurriculum.toggleDeathEater(game, owner, school);
            require(economy.diamond() == before - 250, "Switching off must be free.");
            require(lane.activeMonsters().contains(monster), "Turning off during combat must not remove the current challenge.");
            lane.killTower(defeated);
            double deadXp = defeated.proficiency();
            double studentXp = student.proficiency();
            double ravenXp = ravenclaw.proficiency();
            double allyXp = ally.proficiency();
            MagicSchoolDeathEaters.onMonsterDeath(lane, lateRound);
            requireClose(studentXp, student.proficiency(), "Living challenges must not reward proficiency.");
            lateRound.syncHealth(0);
            MagicSchoolDeathEaters.onMonsterDeath(lane, lateRound);
            requireClose(studentXp, student.proficiency(), "Unattributed deaths must not reward proficiency.");
            lateRound.recordLastHit(owner, kim.biryeong.semiontd.entity.monster.KillSourceKind.TOWER);
            lateRound.markRemoved();
            MagicSchoolDeathEaters.onMonsterDeath(lane, lateRound);
            requireClose(studentXp, student.proficiency(), "Despawned challenges must not reward proficiency.");
            long allyDiamonds = game.players().get(teammate).economy().diamond();
            ally.damageTargetResult(ally.runtimeEntity(allyLane).orElseThrow(), target, 100000, DamageType.MAGIC);
            require(monster.health() <= 0, "The shared damage pipeline must kill the challenge.");
            lane.tick(context.getLevel().getServer());
            requireClose(studentXp + 14 * 1.25, student.proficiency(), "A teammate kill must reward the challenge owner's living student.");
            requireClose(ravenXp + 14 * 1.25 * 1.15, ravenclaw.proficiency(), "History and Ravenclaw must multiply the kill award once.");
            requireClose(deadXp, defeated.proficiency(), "Defeated wizards must not receive kill proficiency.");
            requireClose(allyXp, ally.proficiency(), "The assisting teammate must not receive the owner's proficiency.");
            MagicSchoolDeathEaters.onMonsterDeath(lane, monster);
            lane.tick(context.getLevel().getServer());
            requireClose(studentXp + 17.5, student.proficiency(), "The same death must never reward twice.");
            require(game.players().get(teammate).economy().diamond() == allyDiamonds, "The challenge must not invent an extra diamond reward.");
            lane.markWaveStarted(8);
            require(lane.queuedSummonCount() == 0, "An off wave must not summon a Death Eater.");
            MagicSchoolCurriculum.toggleDeathEater(game, owner, school);
            require(lane.queuedSummonCount() == 0, "Enabling during a wave must wait for the next wave.");
            lane.markWaveStarted(8);
            require(lane.queuedSummonCount() == 0, "Repeated hooks must not apply a mid-wave toggle early.");
            lane.markWaveStarted(9);
            require(lane.queuedSummonCount() == 1, "Re-enabling must summon in the next wave.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void wizardDetailsShowBuffedMagicDamageAndHouseBaseStatsBeforeUpgrading(GameTestHelper context) throws Exception {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        var connection = player.connection;
        var sent = new ArrayList<Packet<?>>();
        try {
            player.connection = new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { sent.add(packet); }
            };
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN,
                    new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            purchaseWithRoundReset(game, owner, school, Upgrade.SPELL_POWER);
            purchaseWithRoundReset(game, owner, school, Upgrade.SORTING_HAT);
            student.gainProficiency(100, lane);
            var entity = student.runtimeEntity(lane).orElseThrow();
            requireClose(28.704, SemionDialogService.currentTowerPrimaryDamage(student, entity),
                    "The live magic damage display must include proficiency and the power lesson.");
            var manager = new SemionGameManager();
            manager.dialogService().showTowerDetails(player, game, student);
            String packet = sent.stream().filter(ClientboundShowDialogPacket.class::isInstance).findFirst().orElseThrow().toString();
            require(packet.contains("🔥") && packet.contains("28.7") && packet.contains("숙련도 강화") && packet.contains("+15%"),
                    "The delivered details dialog must display magic damage and its growth percentage.");
            require(packet.toLowerCase(java.util.Locale.ROOT).contains("ffb86c"),
                    "The delivered spell description must highlight its attack coefficient in light orange.");

            var tooltip = SemionDialogService.class.getDeclaredMethod("upgradeTooltip",
                    kim.biryeong.semiontd.tower.TowerUpgradeOption.class, boolean.class, boolean.class,
                    kim.biryeong.semiontd.tower.Tower.class);
            tooltip.setAccessible(true);
            for (TowerType house : MagicSchoolTowers.houseWizards()) {
                var option = ProductionTowerCatalog.upgrade(student.type(), house.id()).orElseThrow();
                String text = ((net.minecraft.network.chat.Component) tooltip.invoke(null, option, true, false, student)).getString();
                require(text.contains(house == MagicSchoolTowers.GRYFFINDOR ? "17틱" : "18틱"),
                        "Graduation tooltip must display the actual base interval for " + house.id());
                require(text.contains(house == MagicSchoolTowers.HUFFLEPUFF ? "374" : "340"),
                        "Graduation tooltip must display the actual base health for " + house.id());
                require(text.contains("🔥 피해: " + (house == MagicSchoolTowers.SLYTHERIN ? "55" : "50")),
                        "Graduation tooltip must display house base magic damage for " + house.id());
            }
            entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .5, 100);
            requireClose(43.056, SemionDialogService.currentTowerPrimaryDamage(student, entity),
                    "Timed damage buffs must be included exactly once in the display.");
            var target = monster(context, lane, new GridPosition(plot.x() + 3, plot.y(), plot.z()), 900, 0, 1000);
            student.markWaveStarted(1);
            entity.recordCurrentAttackTarget(target);
            new TowerAttackMonsterGoal(entity).tick();
            requireClose(1000 - 43.056, target.getHealth(), "The displayed damage must match an actual magic attack through high armor.");
            requireClose(43.056, student.roundMagicDamageDealt(), "The attack must remain classified as magic.");
            requireClose(0, student.roundPhysicalDamageDealt(), "The display correction must not introduce physical damage.");

            purchaseWithRoundReset(game, owner, school, Upgrade.CUSTOM_WANDS);
            var position = student.managementPosition();
            require(ProductionTowerService.upgradeTower(game, owner, position, MagicSchoolTowers.SLYTHERIN.id()) == TowerUpgradeResult.SUCCESS,
                    "Slytherin graduation must succeed.");
            var graduate = (MagicSchoolWizardTower) lane.towerAt(position);
            requireClose(55, graduate.type().damage(), "Slytherin damage must now be a catalog base stat.");
            requireClose(55 * 1.04 * 1.1 * .8,
                    SemionDialogService.currentTowerPrimaryDamage(graduate, graduate.runtimeEntity(lane).orElseThrow()),
                    "After graduation, proficiency must reset while lesson and wand bonuses remain.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            player.connection = connection;
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void archwizardGraduationCharges450ResetsProficiencyAndKeepsHouseWandsAndSpell(GameTestHelper context) throws Exception {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var economy = game.players().get(owner).economy();
            purchaseWithRoundReset(game, owner, school, Upgrade.CUSTOM_WANDS);
            MagicSchoolTestSetup.resetCurriculumRoundLimit(owner);
            MagicSchoolCurriculum.unlockSpellTier(game, owner, school, 2);
            var tooltip = SemionDialogService.class.getDeclaredMethod("upgradeTooltip",
                    kim.biryeong.semiontd.tower.TowerUpgradeOption.class, boolean.class, boolean.class,
                    kim.biryeong.semiontd.tower.Tower.class);
            tooltip.setAccessible(true);
            for (TowerType type : MagicSchoolTowers.houseWizards()) {
                var position = new GridPosition(plot.x() + 1, plot.y(), plot.z());
                var wizard = (MagicSchoolWizardTower) add(lane, type, position);
                wizard.recordPlacementEconomy(300, 1);
                wizard.onWaveStarted(lane, 1);
                double gainMultiplier = type == MagicSchoolTowers.RAVENCLAW ? 1.15 : 1;
                wizard.gainProficiency((299 - wizard.proficiency()) / gainMultiplier, lane);
                require(wizard.selectSpell(MagicSchoolSpell.STUPEFY), "An unlocked spell must be selectable before graduation.");
                var target = MagicSchoolTowers.archWizardFor(type);
                var option = ProductionTowerCatalog.upgrade(type, target.id()).orElseThrow();
                require(wizard.showsUnavailableUpgrade(lane, option), "T3 must be visible before mastery.");
                String text = ((net.minecraft.network.chat.Component) tooltip.invoke(null, option, true, false, wizard)).getString();
                require(text.contains(type == MagicSchoolTowers.GRYFFINDOR ? "11틱" : "12틱")
                        && text.contains(type == MagicSchoolTowers.HUFFLEPUFF ? "550" : "500")
                        && text.contains("🔥 피해: " + (type == MagicSchoolTowers.SLYTHERIN ? "88" : "80"))
                        && text.contains("299 / 300") && text.contains("초기화"), "T3 preview must show house base stats, requirement and reset.");
                long before = economy.diamond();
                require(ProductionTowerService.upgradeTower(game, owner, position, target.id()) == TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                        "299 proficiency must not allow T3 graduation.");
                require(economy.diamond() == before && lane.towerAt(position) == wizard, "Failed mastery check must not spend or replace.");
                wizard.gainProficiency(1 / gainMultiplier, lane);
                economy.overrideStartingValues(449, 50, 0, 0);
                require(ProductionTowerService.upgradeTower(game, owner, position, target.id()) == TowerUpgradeResult.NOT_ENOUGH_MINERAL,
                        "449 diamonds must not buy T3.");
                requireClose(300, wizard.proficiency(), "Failed payment must not reset proficiency.");
                require(economy.diamond() == 449, "Failed payment must not charge.");
                economy.addDiamond(1);
                require(ProductionTowerService.upgradeTower(game, owner, position, target.id()) == TowerUpgradeResult.SUCCESS,
                        "300 proficiency and 450 diamonds must allow T3.");
                var arch = (MagicSchoolWizardTower) lane.towerAt(position);
                var entity = arch.runtimeEntity(lane).orElseThrow();
                require(economy.diamond() == 0 && economy.emerald() == 50, "Graduation must cost exactly 450 diamonds.");
                require(arch.paidMineralCost() == 750 && arch.originalPosition().equals(position)
                        && arch.position().equals(position) && game.towerCapacityUsed(owner) == 2,
                        "Graduation must preserve position, paid costs and two capacity slots.");
                requireClose(0, arch.proficiency(), "Graduation must reset proficiency.");
                requireClose(1000, arch.maxProficiency(), "T3 cap must be 1000.");
                require(arch.selectedSpell() == MagicSchoolSpell.STUPEFY && arch.primaryDamageType() == DamageType.MAGIC,
                        "Selected spell and magic damage must survive graduation.");
                require(FakePlayerTowerVisuals.visualEntity(arch).isPresent(), "T3 must use a player model.");
                double health = type == MagicSchoolTowers.HUFFLEPUFF ? 605 : type == MagicSchoolTowers.RAVENCLAW ? 525 : 500;
                double damage = type == MagicSchoolTowers.SLYTHERIN ? 96.8 : type == MagicSchoolTowers.RAVENCLAW ? 84 : 80;
                requireClose(health, entity.getMaxHealth(), "House and wand health bonuses must apply once without old proficiency.");
                requireClose(damage, entity.attackDamageAmount(null), "House and wand damage bonuses must apply once.");
                require(entity.attackIntervalTicks() == (type == MagicSchoolTowers.GRYFFINDOR ? 10 : 12), "T3 must retain the wand speed bonus.");
                require(arch.spellChangeCost(123) == (type == MagicSchoolTowers.RAVENCLAW ? 0 : 123), "Wise archwizard must retain free changes.");
                arch.onWaveStarted(lane, 1);
                requireClose(0, arch.proficiency(), "Graduation must not award the same wave twice.");
                arch.onWaveStarted(lane, 2);
                requireClose(12 * gainMultiplier, arch.proficiency(), "T3 must retain the Ravenclaw gain bonus.");
                lane.removeTower(arch);
            }
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void submissionCurriculumLeavesRemovedSlotsEmptyAndClicksCannotGrantProgress(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN,
                    new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            wizard.gainProficiency(12, lane);
            var gui = new CurriculumGui(player, game, school);
            gui.click(0, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            var economy = game.players().get(owner).economy();
            long diamonds = economy.diamond();
            double proficiency = wizard.proficiency();
            double maxHealth = wizard.currentMaxHealth();
            for (int slot : List.of(49, 52, 53)) {
                require(gui.getGuiElement(slot) == null || gui.getGuiElement(slot).getItemStack().isEmpty(), "Removed slot must be empty: " + slot);
                gui.click(slot, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            }
            gui.onTick();
            gui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            requireClose(proficiency, wizard.proficiency(), "Removed buttons must not grant proficiency.");
            requireClose(maxHealth, wizard.currentMaxHealth(), "Removed buttons must not change stats.");
            require(economy.diamond() == diamonds && !MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()),
                    "Empty slots cannot charge currency or reset the round allowance.");
            require(MagicSchoolCurriculum.level(owner, Upgrade.DARK_ARTS_DEFENSE) == 0, "An empty-slot click cannot bypass the round limit.");
            lane.removeTower(school);
            for (int slot : List.of(49, 52, 53)) gui.click(slot, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            requireClose(proficiency, wizard.proficiency(), "Stale empty-slot clicks cannot grant progress either.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void archwizardsMentorT2StudentsOncePerWave(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            var economy = new kim.biryeong.semiontd.game.PlayerEconomy(EconomyConfig.defaultConfig());
            economy.addDiamond(1000);
            MagicSchoolCurriculum.purchase(lane.ownerPlayer(), Upgrade.MENTOR, economy);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.RAVENCLAW, position(context, 3, 1, 3));
            var mentor = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD, position(context, 4, 1, 3));
            add(lane, MagicSchoolTowers.KIND_ARCHWIZARD, position(context, 3, 1, 2));
            lane.markWaveStarted(1);
            lane.markWaveStarted(1);
            requireClose((11 + 15) * 1.15, student.proficiency(), "T2 must receive its 15-point mentor bonus once, including house gain.");
            requireClose(11, mentor.proficiency(), "T3 must not receive a bonus from an equal-tier wizard.");
        } catch (AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal(failure.getMessage()));
            return;
        } finally {
            lane.clearTowers();
            MagicSchoolCurriculum.clear(lane.ownerPlayer());
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void curriculumKeepsOnePurchasePerRoundWithoutResetButtons(GameTestHelper context) throws Exception {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var otherLane = game.playerLane(teammate).orElseThrow();
            var otherSchool = (HogwartsTower) add(otherLane, MagicSchoolTowers.HOGWARTS,
                    GridPosition.from(BlockPos.containing(otherLane.laneLayout().positionAt(.3))));
            var economy = game.players().get(owner).economy();
            economy.overrideStartingValues(0, 50, 0, 0);
            var gui = new CurriculumGui(player, game, school);
            require(MagicSchoolCurriculum.purchase(game, owner, school, Upgrade.MAGIC_HISTORY)
                    == MagicSchoolCurriculum.PurchaseResult.NOT_ENOUGH_DIAMONDS, "Failed payment must be rejected.");
            require(MagicSchoolCurriculum.canUpgradeThisRound(owner, 1), "Failed payment must not consume the round.");
            gui.click(0, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            gui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.level(owner, Upgrade.SPELL_POWER) == 1
                    && MagicSchoolCurriculum.level(owner, Upgrade.DARK_ARTS_DEFENSE) == 0, "Free lessons must share the one-purchase limit.");
            String priceLore = gui.getGuiElement(0).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                    .lines().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));
            require(priceLore.contains("100 다이아") && !priceLore.contains("다음 라운드"), "Blocked purchases must still show their price.");
            String spellPrice = gui.getGuiElement(45).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                    .lines().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));
            require(spellPrice.contains("175 다이아") && !spellPrice.contains("다음 라운드"), "Locked spell tiers must keep their price too.");
            require(MagicSchoolCurriculum.purchase(game, teammate, otherSchool, Upgrade.SPELL_POWER)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "A teammate has an independent allowance.");
            economy.addDiamond(1000);
            gui.click(52, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            gui.click(45, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 2) && economy.diamond() == 1000,
                    "Removed reset slot cannot permit a second purchase.");
            lane.removeTower(school);
            var replacement = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            require(!MagicSchoolCurriculum.canUpgradeThisRound(owner, 1), "Replacing school must not reset the limit.");
            var nextRound = SemionGame.class.getDeclaredMethod("moveToRound", net.minecraft.server.MinecraftServer.class, int.class);
            nextRound.setAccessible(true);
            nextRound.invoke(game, context.getLevel().getServer(), 2);
            require(MagicSchoolCurriculum.canUpgradeThisRound(owner, 2), "The next round restores the allowance.");
            require(MagicSchoolCurriculum.unlockSpellTier(game, owner, replacement, 2)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "Spell unlock uses the next round's allowance.");
            require(MagicSchoolCurriculum.purchase(game, owner, replacement, Upgrade.DARK_ARTS_DEFENSE)
                    == MagicSchoolCurriculum.PurchaseResult.ROUND_LIMIT_REACHED, "Spell unlock must block further lessons.");
            require(MagicSchoolCurriculum.unlockSpellTier(game, owner, replacement, 3)
                    == MagicSchoolCurriculum.PurchaseResult.ROUND_LIMIT_REACHED, "Spell unlock must block further spell unlocks.");
            nextRound.invoke(game, context.getLevel().getServer(), 3);
            require(MagicSchoolCurriculum.unlockSpellTier(game, owner, replacement, 2)
                    == MagicSchoolCurriculum.PurchaseResult.ALREADY_PURCHASED, "Duplicate unlock must not spend the new allowance.");
            require(MagicSchoolCurriculum.purchase(game, owner, replacement, Upgrade.DEATH_EATER)
                    == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "A new round allows one upgrade.");
            require(MagicSchoolCurriculum.toggleDeathEater(game, owner, replacement)
                    == MagicSchoolCurriculum.PurchaseResult.TOGGLED, "Purchased toggles remain usable after buying an upgrade.");
            require(!MagicSchoolCurriculum.canUpgradeThisRound(owner, 3), "Toggling must not reset the allowance.");
            nextRound.invoke(game, context.getLevel().getServer(), 4);
            MagicSchoolCurriculum.toggleDeathEater(game, owner, replacement);
            require(MagicSchoolCurriculum.canUpgradeThisRound(owner, 4), "Toggling must not consume an unused allowance.");
            var liveGui = new CurriculumGui(player, game, replacement);
            liveGui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.level(owner, Upgrade.DARK_ARTS_DEFENSE) == 1, "A later round permits another free lesson.");
            require(economy.diamond() == 575 && economy.emerald() == 50, "Only successful paid upgrades charge currency.");
            MagicSchoolCurriculum.clear(owner);
            require(MagicSchoolCurriculum.canUpgradeThisRound(owner, 4), "Match cleanup clears the limit.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void advancedSpellLessonChargesOnceAndSharesRoundLimit(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        UUID teammate = UUID.randomUUID();
        SemionGame game = game(context, owner, teammate);
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN,
                    new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            var economy = game.players().get(owner).economy();
            economy.overrideStartingValues(349, 0, 0, 0);
            var gui = new CurriculumGui(player, game, school);
            var item = gui.getGuiElement(11).getItemStack();
            require(item.is(Items.ENCHANTED_BOOK) && item.getHoverName().getString().equals("고등 주문 수업"),
                    "The advanced lesson must occupy row two, column three.");
            require(item.get(DataComponents.LORE).lines().stream().anyMatch(line -> line.getString().contains("350 다이아")),
                    "The lesson must show its 350-diamond price.");
            require(student.maxSpellTier() == 2, "Freshmen start with a tier-two limit.");
            gui.click(11, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(economy.diamond() == 349 && student.maxSpellTier() == 2
                    && MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()),
                    "Insufficient funds preserve currency, spell eligibility and the round allowance.");
            economy.addDiamond(1);
            gui.click(11, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(economy.diamond() == 0 && student.maxSpellTier() == 3,
                    "Exact funds buy the lesson and immediately raise an existing student's limit.");
            require(!MagicSchoolCurriculum.isSpellTierUnlocked(owner, 3), "Caster eligibility must not unlock spells for free.");
            require(!MagicSchoolCurriculum.purchased(teammate, Upgrade.ADVANCED_SPELLS), "The upgrade belongs only to its buyer.");
            gui.click(0, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.level(owner, Upgrade.SPELL_POWER) == 0,
                    "The advanced lesson consumes the same round allowance as free lessons.");
            economy.addDiamond(350);
            gui.click(11, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(economy.diamond() == 350 && student.maxSpellTier() == 3,
                    "A repeated click cannot charge again or increase the spell limit twice.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spellChestEnforcesCasterTierAndRavenclawCurseException(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        SemionGame game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var economy = game.players().get(owner).economy();
            economy.addDiamond(20000);
            for (int tier = 2; tier <= 5; tier++) MagicSchoolCurriculum.unlockSpellTier(owner, tier, economy);
            lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES));
            economy.addEmerald(10000);
            for (boolean advanced : List.of(false, true)) {
                if (advanced) {
                    require(MagicSchoolCurriculum.purchase(owner, Upgrade.ADVANCED_SPELLS, economy)
                            == MagicSchoolCurriculum.PurchaseResult.PURCHASED, "Advanced class must purchase once.");
                }
                for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
                    var wizard = (MagicSchoolWizardTower) add(lane, type, plot);
                    var gui = new SpellGui(player, game, wizard);
                    int limit = MagicSchoolTowers.belongsToHouse(type, MagicSchoolTowers.RAVENCLAW) ? 5
                            : (MagicSchoolTowers.isFreshman(type) ? 2 : MagicSchoolTowers.isArchWizard(type) ? 4 : 3)
                                    + (advanced ? 1 : 0);
                    int[] slots = {19, 28, 37, 46};
                    var spells = List.of(MagicSchoolSpell.EXPULSO, MagicSchoolSpell.SECTUMSEMPRA,
                            MagicSchoolSpell.EXPECTO_PATRONUM, MagicSchoolSpell.AVADA_KEDAVRA);
                    for (int i = 0; i < slots.length; i++) {
                        var before = wizard.selectedSpell();
                        gui.click(slots[i], eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                        require(wizard.selectedSpell() == (spells.get(i).requiredSpellTier() <= limit ? spells.get(i) : before),
                                "The real spell menu must enforce caster tier and advanced-class eligibility: " + type.id());
                    }
                    gui.click(2, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(wizard.selectedSpell() != MagicSchoolSpell.MUGGLE_WAND, "Tier eligibility must not unlock the reserved augment spell.");
                    lane.removeTower(wizard);
                }
            }
        } finally {
            game.close();
        }
        context.succeed();
    }

    private static MagicSchoolCurriculum.PurchaseResult purchaseWithRoundReset(
            SemionGame game, UUID owner, HogwartsTower school, Upgrade upgrade) {
        MagicSchoolTestSetup.resetCurriculumRoundLimit(owner);
        return MagicSchoolCurriculum.purchase(game, owner, school, upgrade);
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void duelingPracticeGuiChargesOnceAndKeepsFreeTogglesOwnerScoped(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        UUID friend = UUID.randomUUID();
        var game = game(context, owner, friend);
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var gui = new CurriculumGui(player, game, school);
            var economy = game.players().get(owner).economy();
            long before = economy.diamond();
            var item = gui.getGuiElement(19).getItemStack();
            require(item.is(Items.IRON_SWORD) && item.getHoverName().getString().equals("결투 실습"), "Dueling belongs in row three, column two.");
            gui.click(19, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(economy.diamond() == before - 150 && MagicSchoolCurriculum.enabled(owner, Upgrade.DUELING_PRACTICE),
                    "Purchase charges 150 diamonds and starts ON.");
            require(!MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()), "Unlocking consumes this round's upgrade allowance.");
            gui.click(19, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(!MagicSchoolCurriculum.enabled(owner, Upgrade.DUELING_PRACTICE), "A toggle must work after the upgrade allowance is used.");
            gui.click(19, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.enabled(owner, Upgrade.DUELING_PRACTICE) && economy.diamond() == before - 150, "Further toggles are free.");
            gui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.level(owner, Upgrade.DARK_ARTS_DEFENSE) == 0, "Toggles must not reset the purchase limit.");
            require(MagicSchoolCurriculum.toggle(game, friend, school, Upgrade.DUELING_PRACTICE)
                    == MagicSchoolCurriculum.PurchaseResult.INVALID_SCHOOL, "A teammate cannot toggle the owner's school.");
            lane.removeTower(school);
            gui.click(19, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolCurriculum.enabled(owner, Upgrade.DUELING_PRACTICE), "A stale school menu cannot change the toggle.");
        } finally { game.close(); }
        require(!MagicSchoolCurriculum.enabled(owner, Upgrade.DUELING_PRACTICE), "Closing the match clears the toggle.");
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spellMenuChargesEachDestinationEveryTimeAndKeepsRavenclawChangesFree(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var owner = player.getUUID();
        var game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var economy = game.players().get(owner).economy();
            economy.addDiamond(20000);
            MagicSchoolCurriculum.purchase(owner, Upgrade.ADVANCED_SPELLS, economy);
            for (int tier = 2; tier <= 5; tier++) MagicSchoolCurriculum.unlockSpellTier(owner, tier, economy);
            lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES));
            economy.overrideStartingValues(123, 1000, 0, 0);
            var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD, plot);
            var gui = new SpellGui(player, game, wizard);
            int[] slots = {10, 19, 28, 37, 46};
            long[] prices = {20, 35, 50, 65, 80};
            MagicSchoolSpell[] spells = {MagicSchoolSpell.STUPEFY, MagicSchoolSpell.EXPULSO, MagicSchoolSpell.SECTUMSEMPRA,
                    MagicSchoolSpell.EXPECTO_PATRONUM, MagicSchoolSpell.AVADA_KEDAVRA};
            for (int i = 0; i < slots.length; i++) {
                require(gui.getGuiElement(slots[i]).getItemStack().get(DataComponents.LORE).lines().stream()
                        .anyMatch(line -> line.getString().contains("에메랄드")), "The spell menu must show emerald prices.");
                for (int attempt = 0; attempt < 2; attempt++) {
                    long before = economy.emerald();
                    gui.click(slots[i], eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(wizard.selectedSpell() == spells[i] && economy.emerald() == before - prices[i],
                            "Every switch to this destination tier must charge, including previously used spells.");
                    gui.click(slots[i], eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(economy.emerald() == before - prices[i], "Clicking the already selected spell is not a change and cannot charge twice.");
                    gui.click(1, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(wizard.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS && economy.emerald() == before - prices[i],
                            "Returning to tier one must remain free.");
                }
            }
            require(economy.diamond() == 123, "Spell selection must never consume diamonds.");
            economy.overrideStartingValues(0, 0, 0, 0);
            for (var type : List.of(MagicSchoolTowers.RAVENCLAW, MagicSchoolTowers.WISE_ARCHWIZARD)) {
                var raven = (MagicSchoolWizardTower) add(lane, type, new GridPosition(plot.x() + 1, plot.y(), plot.z()));
                var ravenGui = new SpellGui(player, game, raven);
                for (int i = 0; i < slots.length; i++) {
                    require(ravenGui.getGuiElement(slots[i]).getItemStack().get(DataComponents.LORE).lines().stream()
                            .anyMatch(line -> line.getString().equals("변경 비용: 무료")), "Ravenclaw's actual free price must appear in the menu.");
                    ravenGui.click(slots[i], eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(raven.selectedSpell() == spells[i] && economy.emerald() == 0,
                            "Both Ravenclaw ranks must change freely even with no emeralds.");
                }
                lane.removeTower(raven);
            }
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void rejectedSpellChangesNeverSpendOrModifySelection(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var owner = player.getUUID();
        var friend = UUID.randomUUID();
        var game = game(context, owner, friend);
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD, plot);
            var economy = game.players().get(owner).economy();
            economy.addDiamond(20000);
            require(wizard.changeSpell(game, owner, MagicSchoolSpell.STUPEFY)
                    == MagicSchoolWizardTower.SpellChangeResult.UNAVAILABLE, "Locked tiers cannot be bought through spell selection.");
            MagicSchoolCurriculum.purchase(owner, Upgrade.ADVANCED_SPELLS, economy);
            for (int tier = 2; tier <= 5; tier++) MagicSchoolCurriculum.unlockSpellTier(owner, tier, economy);
            lane.assignAugmentSnapshot(MagicSchoolAugmentCombatTest.snapshot(MagicSchoolAugments.UNFORGIVABLE_CURSES));
            economy.overrideStartingValues(0, 79, 0, 0);
            require(wizard.changeSpell(game, owner, MagicSchoolSpell.AVADA_KEDAVRA)
                    == MagicSchoolWizardTower.SpellChangeResult.NOT_ENOUGH_EMERALDS, "One emerald short must reject without changing anything.");
            require(wizard.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS && economy.emerald() == 79, "Failed payment must be atomic.");
            require(wizard.changeSpell(game, owner, MagicSchoolSpell.MUGGLE_WAND)
                    == MagicSchoolWizardTower.SpellChangeResult.UNAVAILABLE, "The unreleased augment cannot be equipped.");
            require(wizard.changeSpell(game, friend, MagicSchoolSpell.AVADA_KEDAVRA)
                    == MagicSchoolWizardTower.SpellChangeResult.INVALID_TOWER, "Forged ownership must be rejected before payment.");
            require(economy.emerald() == 79, "All rejected requests must preserve the wallet.");
            economy.addEmerald(1);
            require(wizard.changeSpell(game, owner, MagicSchoolSpell.AVADA_KEDAVRA)
                    == MagicSchoolWizardTower.SpellChangeResult.CHANGED && economy.emerald() == 0, "Exact funds must succeed.");
            economy.addEmerald(80);
            var second = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD, new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            require(second.changeSpell(game, owner, MagicSchoolSpell.AVADA_KEDAVRA)
                    == MagicSchoolWizardTower.SpellChangeResult.UNAVAILABLE, "A reserved curse cannot charge another student.");
            var freshman = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, new GridPosition(plot.x() + 2, plot.y(), plot.z()));
            require(freshman.changeSpell(game, owner, MagicSchoolSpell.CRUCIO)
                    == MagicSchoolWizardTower.SpellChangeResult.UNAVAILABLE, "Tier-ineligible students cannot pay to bypass restrictions.");
            var stale = new SpellGui(player, game, second);
            lane.removeTower(second);
            stale.click(47, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(second.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS && economy.emerald() == 80,
                    "Removed or upgraded students cannot spend through stale menus.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void battleStartBlocksOpenSpellMenusAndFreeRavenclawChanges(GameTestHelper context) throws Exception {
        var player = context.makeMockServerPlayerInLevel();
        var owner = player.getUUID();
        var game = game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var wizard = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, plot);
            var raven = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.RAVENCLAW, new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            var economy = game.players().get(owner).economy();
            MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
            economy.overrideStartingValues(100, 100, 0, 0);
            var gui = new SpellGui(player, game, wizard);
            var ravenGui = new SpellGui(player, game, raven);

            var begin = SemionGame.class.getDeclaredMethod("startWavePhase", net.minecraft.server.MinecraftServer.class);
            begin.setAccessible(true);
            begin.invoke(game, context.getLevel().getServer());
            require(game.phase() == kim.biryeong.semiontd.game.RoundPhase.LANE_WAVE, "The fixture must enter real combat.");
            gui.click(10, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            ravenGui.click(10, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(wizard.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS && raven.selectedSpell() == MagicSchoolSpell.EXPELLIARMUS,
                    "Battle locks paid and free changes even before the GUI receives another tick.");
            require(economy.emerald() == 100, "Combat rejection must never charge emeralds.");
            gui.onTick();
            var lore = gui.getGuiElement(10).getItemStack().get(DataComponents.LORE).lines().stream().map(line -> line.getString()).toList();
            require(lore.contains("준비 시간에만 주문 변경 가능") && lore.contains("변경 비용: 20 에메랄드"),
                    "Combat must show the lock while preserving the price.");

            lane.disableMonsters();
            require(lane.clearedThisRound(), "The player's lane must be cleared for the phase check.");
            require(wizard.changeSpell(game, owner, MagicSchoolSpell.STUPEFY)
                    == MagicSchoolWizardTower.SpellChangeResult.INVALID_PHASE, "The global combat phase is authoritative.");
            var prepare = SemionGame.class.getDeclaredMethod("startPreparePhase", net.minecraft.server.MinecraftServer.class);
            prepare.setAccessible(true);
            prepare.invoke(game, context.getLevel().getServer());
            gui.onTick();
            require(gui.getGuiElement(10).getItemStack().get(DataComponents.LORE).lines().stream()
                    .noneMatch(line -> line.getString().equals("준비 시간에만 주문 변경 가능")), "The menu must unlock in the next preparation phase.");
            long before = economy.emerald();
            gui.click(10, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(wizard.selectedSpell() == MagicSchoolSpell.STUPEFY && economy.emerald() == before - 20,
                    "Preparation restores normal paid selection.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void spellPracticeCurriculumChargesOnceAndRewardsActualConsecutiveWaveSpells(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var owner = player.getUUID();
        var friend = UUID.randomUUID();
        var game = game(context, owner, friend);
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var otherLane = game.playerLane(friend).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var studentPos = new GridPosition(plot.x() + 1, plot.y(), plot.z());
            var student = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN, studentPos);
            var ally = (MagicSchoolWizardTower) add(otherLane, MagicSchoolTowers.FRESHMAN,
                    GridPosition.from(BlockPos.containing(otherLane.laneLayout().positionAt(.3))));
            var gui = new CurriculumGui(player, game, school);
            var item = gui.getGuiElement(18).getItemStack();
            require(item.is(Items.ENCHANTED_BOOK) && item.getHoverName().getString().equals("주문 연마 수업"),
                    "Spell practice must occupy row three, column one.");
            require(item.get(DataComponents.LORE).lines().stream().anyMatch(line -> line.getString().contains("125 다이아")),
                    "The lesson must show its 125-diamond price.");
            var economy = game.players().get(owner).economy();
            long before = economy.diamond();
            gui.click(18, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            gui.click(18, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(economy.diamond() == before - 125 && !MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()),
                    "One purchase costs 125 diamonds and uses this round's upgrade allowance.");
            requireClose(0, student.proficiency(), "The lesson must not grant proficiency on purchase.");
            lane.markWaveStarted(1);
            otherLane.markWaveStarted(1);
            requireClose(13, student.proficiency(), "First-round tier-one practice gives 11 + 2.");
            requireClose(11, ally.proficiency(), "A teammate must not share the purchased lesson.");
            requireClose(203.9, student.runtimeEntity(lane).orElseThrow().getMaxHealth(), "Practice must update real entity stats.");
            lane.resetForRound();
            lane.markWaveStarted(2);
            requireClose(29, student.proficiency(), "Consecutive use adds 12 + 2 + 2.");
            MagicSchoolCurriculum.unlockSpellTier(owner, 2, economy);
            lane.resetForRound();
            student.selectSpell(MagicSchoolSpell.STUPEFY);
            lane.markWaveStarted(3);
            requireClose(46, student.proficiency(), "Changing to tier two adds 13 + 4 without a repetition bonus.");
            var promoted = (MagicSchoolWizardTower) ProductionTowerCatalog.find(MagicSchoolTowers.GRYFFINDOR.id()).orElseThrow()
                    .create(owner, lane.teamId(), lane.laneId(), studentPos);
            promoted.copyFrom(student, 200);
            lane.replaceTower(student, promoted);
            promoted.onWaveStarted(lane, 3);
            requireClose(0, promoted.proficiency(), "Promotion cannot duplicate the wave's reward.");
            var newcomer = (MagicSchoolWizardTower) add(lane, MagicSchoolTowers.FRESHMAN,
                    new GridPosition(plot.x() + 2, plot.y(), plot.z()));
            newcomer.selectSpell(MagicSchoolSpell.STUPEFY);
            lane.resetForRound();
            lane.markWaveStarted(4);
            requireClose(20, promoted.proficiency(), "Promotion keeps previous spell history: 14 + 4 + 2.");
            requireClose(18, newcomer.proficiency(), "A new student has no previous-wave bonus: 14 + 4.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void wizardSkinMenuSavesFiveKindsRefreshesHouseRanksAndRejectsStaleSearches(GameTestHelper context) throws Exception {
        var player = context.makeMockServerPlayerInLevel();
        var owner = player.getUUID();
        var friend = UUID.randomUUID();
        var game = game(context, owner, friend);
        var manager = new SemionGameManager();
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var students = new ArrayList<MagicSchoolWizardTower>();
            int offset = 1;
            for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
                students.add((MagicSchoolWizardTower) add(lane, type, new GridPosition(plot.x() + offset++, plot.y(), plot.z())));
            }
            var gryffindor = students.stream().filter(tower -> tower.type().id().equals(MagicSchoolTowers.GRYFFINDOR.id())).findFirst().orElseThrow();
            var anchor = gryffindor.runtimeEntity(lane).orElseThrow();
            var previousVisual = (ServerPlayer) FakePlayerTowerVisuals.visualEntity(gryffindor).orElseThrow();
            var friendLane = game.playerLane(friend).orElseThrow();
            var other = add(friendLane, MagicSchoolTowers.GRYFFINDOR,
                    GridPosition.from(BlockPos.containing(friendLane.laneLayout().positionAt(.3))));
            var otherVisual = FakePlayerTowerVisuals.visualEntity(other).orElseThrow();
            anchor.setPos(anchor.position().add(.25, 0, .25));
            var position = anchor.position();
            double health = gryffindor.health();
            var gui = new MagicSchoolSkinGui(player, manager, game, school);
            gui.open();
            require(gui.getTitle().getString().equals("마법사 스킨"), "The wizard skin menu must use its requested name.");
            int index = 0;
            for (var kind : MagicSchoolSkins.Kind.values()) {
                require(gui.getGuiElement(11 + index).getItemStack().is(Items.PLAYER_HEAD)
                        && gui.getGuiElement(11 + index).getItemStack().getHoverName().getString().contains(kind.displayName()),
                        "Each of the five skin kinds must have its own player-head button.");
                index++;
            }
            gui.click(12, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            var input = (kim.biryeong.semiontd.ui.PlayerSkinInputGui) SguiUtils.getCurrentGui(player);
            require(input.getTitle().getString().equals("그리핀도르 마법사 스킨 검색"), "The menu must open the shared name-search UI.");
            var searched = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "WizardSkin",
                    new com.mojang.authlib.properties.PropertyMap(com.google.common.collect.ImmutableMultimap.of(
                            "textures", new com.mojang.authlib.properties.Property("textures", "wizard-gryffindor", "signed"))));
            setSkinPreview(input, searched);
            input.click(2, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.GRYFFINDOR).orElseThrow().sourceName().equals("WizardSkin"),
                    "Accepting the preview must save the selected skin.");
            for (var kind : MagicSchoolSkins.Kind.values()) {
                if (kind == MagicSchoolSkins.Kind.GRYFFINDOR) continue;
                var skin = new kim.biryeong.semiontd.progression.HeroCompanionSkinPreference("WizardSkin", UUID.randomUUID().toString(),
                        "wizard-" + kind.id(), "signed");
                require(manager.saveMagicSchoolSkin(owner, player.getGameProfile().name(), kind, skin), "Saving each independent kind must succeed.");
            }
            var visualIds = new java.util.HashSet<UUID>();
            for (var student : students) {
                var visual = (ServerPlayer) FakePlayerTowerVisuals.visualEntity(student).orElseThrow();
                var kind = MagicSchoolSkins.Kind.forTower(student.type());
                require(visual.getGameProfile().properties().get("textures").iterator().next().value().equals("wizard-" + kind.id()),
                        "Both house ranks must render their house's stored skin.");
                require(visualIds.add(visual.getUUID()), "Every student must retain an independent player profile.");
                require(FakePlayerTowerVisuals.resolveInteractionAnchor(context.getLevel(), visual.getId()) == student.runtimeEntity(lane).orElseThrow(),
                        "Skin replacement must preserve interaction routing.");
            }
            var nextVisual = (ServerPlayer) FakePlayerTowerVisuals.visualEntity(gryffindor).orElseThrow();
            require(!previousVisual.getUUID().equals(nextVisual.getUUID()), "Changing textures must replace the client profile to avoid stale skin caches.");
            require(FakePlayerTowerVisuals.visualEntity(other).orElseThrow() == otherVisual, "A skin update must not replace another owner's visual.");
            require(anchor.position().equals(position) && gryffindor.health() == health, "Changing skins must preserve combat state and position.");
            MagicSchoolSkins.load(owner, java.util.Map.of());
            manager.profile(player.level().getServer(), owner, player.getGameProfile().name());
            require(MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.SLYTHERIN).isPresent(), "Loading the account must restore stored choices.");
            gui = new MagicSchoolSkinGui(player, manager, game, school);
            gui.open();
            gui.click(21, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.GRYFFINDOR).isEmpty(), "The house reset button must clear only that preference.");
            require(MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.SLYTHERIN).isPresent(), "Resetting one house must preserve other choices.");
            gui.click(11, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            input = (kim.biryeong.semiontd.ui.PlayerSkinInputGui) SguiUtils.getCurrentGui(player);
            setSkinPreview(input, searched);
            var savedFreshman = MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.FRESHMAN);
            lane.removeTower(school);
            input.click(2, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            require(savedFreshman.equals(MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.FRESHMAN)),
                    "A stale preview after selling Hogwarts must not save a skin.");
        } finally {
            if (SguiUtils.getCurrentGui(player) != null) SguiUtils.getCurrentGui(player).close();
            game.close();
            MagicSchoolSkins.load(owner, java.util.Map.of());
        }
        context.succeed();
    }

    private static void setSkinPreview(kim.biryeong.semiontd.ui.PlayerSkinInputGui gui, com.mojang.authlib.GameProfile profile) throws Exception {
        var preview = kim.biryeong.semiontd.ui.PlayerSkinInputGui.class.getDeclaredField("preview");
        preview.setAccessible(true);
        preview.set(gui, profile);
        var refresh = kim.biryeong.semiontd.ui.PlayerSkinInputGui.class.getDeclaredMethod("refreshAction");
        refresh.setAccessible(true);
        refresh.invoke(gui);
    }

    private static void clickWithRoundReset(CurriculumGui gui, UUID owner, int slot) {
        MagicSchoolTestSetup.resetCurriculumRoundLimit(owner);
        gui.click(slot, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
    }

    static void setMonsterHealth(SemionMonsterEntity entity, double health) {
        entity.runtimeMonster().syncHealth(health);
        entity.setHealth((float) health);
        entity.setInvulnerableTime(0);
    }

    static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 0.001, message + " Expected " + expected + ", actual " + actual);
    }

    private static String spellCommand(GridPosition plot) {
        return "semiontd magic_school spells " + plot.x() + " " + plot.y() + " " + plot.z();
    }

    static SemionGame game(GameTestHelper context, UUID owner, UUID teammate) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        require(game.selectJob(owner, MagicSchoolTowerJob.ID), "Magic School must be selectable.");
        require(game.selectJob(teammate, MagicSchoolTowerJob.ID), "A teammate must also be able to select Magic School.");
        require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                new AssignedParticipant(owner, "student-owner", TeamId.RED, 1),
                new AssignedParticipant(teammate, "teammate", TeamId.RED, 2)), Set.of(), 2)), "The game must start.");
        game.players().values().forEach(player -> player.economy().addMineral(2000));
        return game;
    }

    private static TowerPlacementResult place(SemionGame game, UUID owner, BlockPos plot, TowerType type) {
        return ProductionTowerService.placeTower(game, owner, plot, type.id());
    }

    private static String command(GridPosition plot) {
        return "semiontd magic_school curriculum " + plot.x() + " " + plot.y() + " " + plot.z();
    }

    static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) context.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12)));
        var layout = new LaneRegionLayout(1, spawn, List.of(spawn, boss), boss,
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 5, 14))),
                List.of(position(context, 12, 1, 12)));
        return new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
    }

    static EntityBackedTower add(PlayerLane lane, TowerType type, GridPosition plot) {
        var tower = (EntityBackedTower) ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(lane.ownerPlayer(), lane.teamId(), lane.laneId(), plot);
        lane.addTower(tower);
        return tower;
    }

    static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, GridPosition plot) {
        return monster(context, lane, plot, 0, 0);
    }

    static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, GridPosition plot,
            double armor, double resistance) {
        return monster(context, lane, plot, armor, resistance, 100);
    }

    static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, GridPosition plot,
            double armor, double resistance, double maxHealth) {
        Monster monster = new Monster("magic-school-target", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                maxHealth, armor, 0, AttackKind.MELEE, "minecraft:zombie", null, DamageType.PHYSICAL, resistance,
                MonsterDimensions.DEFAULT, null, List.of(), 0);
        var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(plot.x() + 0.5, plot.y() + 1.0, plot.z() + 0.5);
        require(context.getLevel().addFreshEntity(entity), "The combat target must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static void verifyRangedPaletteEvent(UUID sourceId) throws Exception {
        var field = TowerVfxService.class.getDeclaredField("EVENTS");
        field.setAccessible(true);
        for (Object event : (Collection<?>) field.get(null)) {
            if (!event.getClass().getSimpleName().equals("MagicSchoolEvent")) continue;
            var contextField = event.getClass().getSuperclass().getDeclaredField("context");
            contextField.setAccessible(true);
            Object eventContext = contextField.get(event);
            var source = eventContext.getClass().getDeclaredMethod("sourceTowerId");
            source.setAccessible(true);
            if (!source.invoke(eventContext).equals(sourceId)) continue;
            var palette = eventContext.getClass().getDeclaredMethod("palette");
            palette.setAccessible(true);
            require(palette.invoke(eventContext) == BuilderPalette.MAGIC_SCHOOL, "The actual attack must use the Magic School palette.");
            var visualField = event.getClass().getDeclaredField("visual");
            visualField.setAccessible(true);
            var visual = (kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx.Visual) visualField.get(event);
            if (visual.kind() != kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx.Kind.ATTACK) continue;
            require(visual.spell() == MagicSchoolSpell.EXPELLIARMUS && visual.source().distanceTo(visual.center()) > 3,
                    "The actual ranged attack must capture the selected spell and its target.");
            return;
        }
        throw new AssertionError("A student attack must enqueue shared attack VFX.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
