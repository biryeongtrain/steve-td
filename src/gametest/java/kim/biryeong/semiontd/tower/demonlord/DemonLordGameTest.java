package kim.biryeong.semiontd.tower.demonlord;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterState;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class DemonLordGameTest implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void combatTickScaleAppliesOnceAndResetsBetweenRounds(GameTestHelper context) {
        var server = context.getLevel().getServer();
        float originalRate = server.tickRateManager().tickrate();
        try (var fixture = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5))), GameType.ADVENTURE,
                UUID.randomUUID(), "demon-speed-test")) {
            var player = fixture.player();
            var lane = testLane(context, player.getUUID());
            var players = Map.of(player.getUUID(), demonLordPlayer(player));
            var state = DemonLordStates.getOrCreate(player.getUUID());
            try {
                state.enterCombat();
                state.consumePendingSpawn();
                server.tickRateManager().setTickRate(20.0F);
                DemonLordService.tick(lane, players);
                double movement = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
                float flight = player.getAbilities().getFlyingSpeed();
                for (float rate : new float[] {40.0F, 40.0F, 100.0F, 20.0F}) {
                    server.tickRateManager().setTickRate(rate);
                    DemonLordService.tick(lane, players);
                    requireClose(movement * rate / 20.0,
                            player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                            "Combat movement must follow the current tick rate without stacking.");
                    requireClose(attack * rate / 20.0,
                            player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),
                            "Combat attack speed must follow the current tick rate without stacking.");
                    requireClose(flight * rate / 20.0, player.getAbilities().getFlyingSpeed(),
                            "Combat flight must follow the current tick rate without stacking.");
                }
                server.tickRateManager().setTickRate(40.0F);
                DemonLordService.tick(lane, players);
                DemonLordService.endRound(player.getUUID());
                DemonLordService.tick(lane, players);
                requireClose(movement, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                        "Preparation must remove movement acceleration even while the server is accelerated.");
                requireClose(attack, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),
                        "Preparation must remove attack acceleration.");
                requireClose(flight, player.getAbilities().getFlyingSpeed(), "Preparation must remove flight acceleration.");
                DemonLordService.beginWave(player.getUUID());
                DemonLordService.tick(lane, players);
                requireClose(movement * 2.0, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                        "The next wave must restore acceleration exactly once.");
                context.succeed();
            } finally {
                DemonLordService.cleanupPlayer(player);
            }
        } finally {
            server.tickRateManager().setTickRate(originalRate);
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void disconnectRemovesCombatMovementAcceleration(GameTestHelper context) {
        var server = context.getLevel().getServer();
        float originalRate = server.tickRateManager().tickrate();
        try (var fixture = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5))), GameType.ADVENTURE,
                UUID.randomUUID(), "demon-exit-test")) {
            var player = fixture.player();
            var movement = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double originalMovement = movement.getValue();
            var state = DemonLordStates.getOrCreate(player.getUUID());
            try {
                state.enterCombat();
                state.consumePendingSpawn();
                server.tickRateManager().setTickRate(40.0F);
                DemonLordService.tick(testLane(context, player.getUUID()), Map.of(player.getUUID(), demonLordPlayer(player)));
                requireClose(originalMovement * 2.0, movement.getValue(), "The cleanup regression must start accelerated.");
                kim.biryeong.semiontd.ui.GambleRevealService.clear(player.getUUID());
                kim.biryeong.semiontd.tower.demonlord.DemonLordService.disconnectPlayer(player);
                kim.biryeong.semiontd.tower.frost.FrostFullOperationService.cleanupPlayer(player);
                requireClose(originalMovement, movement.getValue(), "Disconnect must remove the demon lord movement modifier.");
                context.succeed();
            } finally {
                DemonLordService.cleanupPlayer(player);
            }
        } finally {
            server.tickRateManager().setTickRate(originalRate);
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void skillEffectsSnapToTheGroundSurfaceUnderTheirOrigin(GameTestHelper context) {
        var level = context.getLevel();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos floor = context.absolutePos(new BlockPos(2, 1, 2));
        BlockPos slab = context.absolutePos(new BlockPos(4, 1, 2));
        for (BlockPos pos : List.of(floor, slab)) {
            level.setBlockAndUpdate(pos.above(), air);
            level.setBlockAndUpdate(pos.above(2), air);
        }
        level.setBlockAndUpdate(floor, stone);
        level.setBlockAndUpdate(slab, net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState());

        double top = floor.getY() + 1.0;
        double x = floor.getX() + 0.5, z = floor.getZ() + 0.5;
        requireClose(top, DemonLordVfx.onGround(level, new Vec3(x, top - 0.1, z)).y, "A sunken origin rises to the surface.");
        requireClose(top, DemonLordVfx.onGround(level, new Vec3(x, top + 0.2, z)).y, "A barely floating origin drops to the surface.");
        requireClose(top + 2.0, DemonLordVfx.onGround(level, new Vec3(x, top + 2.0, z)).y, "Effects in the air stay where they are.");
        double slabTop = slab.getY() + 0.5;
        requireClose(slabTop, DemonLordVfx.onGround(level, new Vec3(slab.getX() + 0.5, slabTop + 0.1, slab.getZ() + 0.5)).y,
                "A partial block snaps to its own collision top.");
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void selfDesignationsBoostBladeAndAltarDamageExactlyOnce(GameTestHelper context) {
        net.minecraft.world.level.ChunkPos.rangeClosed(net.minecraft.world.level.ChunkPos.containing(context.getLevel().getRespawnData().pos()), 2)
                .forEach(pos -> context.getLevel().getChunk(pos.x(), pos.z()));
        context.runAfterDelay(10, () -> checkSelfDesignationDamage(context));
    }

    private static void checkSelfDesignationDamage(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = augmentLane(context, player.getUUID());
        DemonLordSkillTower altar = altar(context, player.getUUID(), DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        SpawnedTarget target = null;
        try {
            lane.addTower(altar);
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            double baseline = state.maxHealth();
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                    new PlayerAugmentState.Selection(5, AugmentRarity.PRISMATIC, "one_man_show",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()),
                    new PlayerAugmentState.Selection(15, AugmentRarity.PRISMATIC, "tactical_designation_3_assault",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            lane.markWaveStarted(15);
            requireClose(baseline * 1.2, state.maxHealth(), "The reduced health bonus belongs to the Demon Lord, not the altar.");
            target = spawnTarget(context, lane, new BlockPos(5, 2, 5), 10000, 0);
            for (DemonLordSkillTower source : java.util.Arrays.asList(null, altar)) {
                double health = target.runtime().health();
                target.entity().damageCooldownTime = 0;
                var result = DemonLordService.dealDamage(player, lane, source, target.entity(), 20, DamageType.MAGIC);
                requireClose(32, result.dealtDamage(), "Blade and skill damage must each receive the reduced 1.6x modifier once.");
                requireClose(health - 32, target.runtime().health(), "Actual enemy HP must match the reported damage.");
            }
        } finally {
            if (target != null) target.entity().discard();
            lane.clearTowers();
            DemonLordStates.clearAllForTesting();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void augmentThronesReplayDamageOnlyWithoutCombatTowersOrCooldowns(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        Vec3 playerPosition = Vec3.atCenterOf(context.absolutePos(new BlockPos(3, 2, 3)));
        player.teleportTo(playerPosition.x, playerPosition.y, playerPosition.z);
        PlayerLane lane = augmentLane(context, player.getUUID());
        DemonLordSkillTower first = altar(context, player.getUUID(), DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        DemonLordSkillTower second = altar(context, player.getUUID(), DemonLordSkill.SOUL_DRAIN, 1, 4, 3);
        SpawnedTarget target = null;
        prepareFloor(context, 7);
        try {
            lane.addTower(first);
            lane.addTower(second);
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                    new PlayerAugmentState.Selection(5, AugmentRarity.PRISMATIC, "job_demon_lord_towers_p",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()),
                    new PlayerAugmentState.Selection(15, AugmentRarity.SILVER, "job_demon_lord_towers_s",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            target = spawnTarget(context, lane, new BlockPos(5, 2, 5), 10000, 0);
            state.startCooldown(first.skill(), 0, 500);
            state.startCooldown(second.skill(), 0, 500);
            state.augments().beginSpell(first);
            DemonLordService.dealDamage(player, lane, first, target.entity(), 200, DamageType.MAGIC);
            state.augments().finishSpell(player, lane, state, 0);
            state.augments().beginSpell(second);
            DemonLordService.dealDamage(player, lane, second, target.entity(), 300, DamageType.MAGIC);
            state.augments().finishSpell(player, lane, state, 1);
            double expected = 10000 - (100 + 50 * Math.log(3)) - 2 * (100 + 50 * Math.log(5))
                    - (100 + 50 * Math.log(8));
            requireClose(expected, target.runtime().health(), "Replays must scale the original raw amount times 150%, never scale an already reduced hit.");
            require(lane.towers().size() == 2, "Echoes must not register targetable combat towers or use tower slots.");
            require(state.remainingCooldownTicks(first.skill(), 1) == 499
                            && state.remainingCooldownTicks(second.skill(), 1) == 499,
                    "Echoes must not restart skill cooldowns.");
            requireClose(2, state.augments().consumeFinisher(lane.augmentSnapshot(), 2),
                    "Only the original successful skill grants one finisher.");
            requireClose(0, state.augments().consumeFinisher(lane.augmentSnapshot(), 2),
                    "Replays must not grant extra finisher charges.");
            state.augments().beginSpell(first);
            DemonLordService.dealDamage(player, lane, first, target.entity(), 200, DamageType.MAGIC);
            state.augments().finishSpell(player, lane, state, 2);
            requireClose(expected - (100 + 50 * Math.log(3)), target.runtime().health(), "Throne cooldown must suppress another pair of replays.");
            var visuals = context.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    player.getBoundingBox().inflate(3), entity -> entity.entityTags().contains(SemionEntityTypes.RUNTIME_NO_SAVE_TAG));
            require(visuals.size() == 2 && visuals.stream().allMatch(net.minecraft.world.entity.decoration.ArmorStand::isMarker),
                    "Exactly two untargetable marker visuals must represent the echoes.");
            state.standDown();
            require(visuals.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
                    "Ending combat must remove both echo visuals.");
            context.succeed();
        } finally {
            if (target != null) {target.entity().discard();}
            lane.clearTowers();
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void cleanupOnlyRestoresFlightForAnExistingDemonLordState(GameTestHelper context) {
        // The vanilla mock overrides gameMode() to CREATIVE even after setGameMode().
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "demon-flight-test"), false);
        ServerPlayer player = new ServerPlayer(context.getLevel().getServer(), context.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        context.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            player.setGameMode(GameType.ADVENTURE);
            require(!player.isCreative() && !player.isSpectator(),
                    "Flight restoration must be tested in the gameplay mode.");
            player.getAbilities().mayfly = false;
            DemonLordStates.clear(player.getUUID());
            DemonLordService.cleanupPlayer(player);
            require(!player.getAbilities().mayfly, "Cleanup must not grant flight to an unrelated player.");

            DemonLordStates.getOrCreate(player.getUUID());
            player.getInventory().setItem(0, DemonLordKitItems.mark(new ItemStack(Items.NETHERITE_SWORD)));
            DemonLordService.cleanupPlayer(player);
            require(player.getAbilities().mayfly, "Demon lord cleanup must restore flight.");
            require(DemonLordStates.get(player.getUUID()) == null, "Demon lord state must be cleared.");
            require(player.getInventory().getItem(0).isEmpty(), "Marked combat kit items must be removed.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord cleanup GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordStates.clear(player.getUUID());
            context.getLevel().getServer().getPlayerList().remove(player);
            player.discard();
            channel.finishAndReleaseAll();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void altarDamageUsesSharedDefenseStatisticsAndKillAttribution(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DemonLordSkillTower altar = altar(context, owner, DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            lane.addTower(altar);
            DemonLordState state = DemonLordStates.getOrCreate(owner);
            state.setLaneId(1);
            state.enterCombat();
            SpawnedTarget armored = spawnTarget(context, lane, new BlockPos(5, 2, 5), 100.0, 100.0);
            SpawnedTarget magic = spawnTarget(context, lane, new BlockPos(6, 2, 5), 100.0, 100.0);
            targets.add(armored);
            targets.add(magic);

            Tower.DamageResult physical = DemonLordService.dealDamage(
                    player, lane, altar, armored.entity(), 50.0, DamageType.PHYSICAL);
            Tower.DamageResult magical = DemonLordService.dealDamage(
                    player, lane, altar, magic.entity(), 50.0, DamageType.MAGIC);

            requireClose(25.0, physical.dealtDamage(), "Physical damage must respect armor.");
            requireClose(50.0, magical.dealtDamage(), "Magic damage must ignore armor when resistance is zero.");
            requireClose(25.0, state.roundPhysicalDamageDealt(), "Physical damage must be recorded on the demon lord once.");
            requireClose(50.0, state.roundMagicDamageDealt(), "Magic damage must be recorded on the demon lord once.");
            requireClose(0.0, altar.roundPhysicalDamageDealt(), "The altar must not duplicate the demon lord's physical statistics.");
            requireClose(0.0, altar.roundMagicDamageDealt(), "The altar must not duplicate the demon lord's magic statistics.");

            DemonLordService.dealDamage(player, lane, altar, magic.entity(), 1_000.0, DamageType.TRUE);
            require(!magic.runtime().isAlive(), "True damage must finish the target.");
            require(state.roundMetrics().killCount() == 1, "The demon lord must record the skill kill once.");
            require(owner.equals(magic.runtime().lastHitPlayerId().orElse(null))
                            && magic.runtime().lastHitSourceKind() == KillSourceKind.TOWER,
                    "Demon lord skill kills must stay attributed to the altar owner.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord damage GameTest failed: " + failure.getMessage()));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            DemonLordStates.clear(owner);
            player.discard();
        }
    }

    /**
     * 스킬은 레인에 짓지 않습니다. 배정한 슬롯마다 보이지 않는 운반체가 떠서 공용 연출·범위 경로의
     * 출처가 되고, 레인의 타워 목록·칸·타워 수에는 끼지 않습니다.
     */
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void slotCarriersFollowTheLoadoutWithoutJoiningTheLane(GameTestHelper context) {
        UUID owner = stableUuid("demon-lord-carrier-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        try {
            DemonLordState state = DemonLordStates.getOrCreate(owner);
            DemonLordSkill[] skills = DemonLordSkill.values();
            DemonLordBinding[] bindings = DemonLordBinding.values();
            for (int i = 0; i < bindings.length; i++) {
                require(state.loadout().assign(bindings[i], skills[i], 0), "Every key slot must accept a skill.");
            }
            DemonLordService.syncCarriers(lane, state);

            List<DemonLordSkillTower> carriers = DemonLordService.orderedAltars(lane, owner);
            require(carriers.size() == bindings.length, "One carrier per filled slot.");
            require(lane.towers().isEmpty(), "Carriers must not join the lane's tower list.");
            for (int i = 0; i < bindings.length; i++) {
                DemonLordSkillTower carrier = carriers.get(i);
                require(carrier.skill() == skills[i] && carrier.binding() == bindings[i],
                        "Carriers must follow slot order, not purchase order.");
                var entity = carrier.entity(lane);
                require(entity != null, carrier.skill() + " must have a hidden entity to act as the effect source.");
                require(entity.isNoGravity() && !entity.isCustomNameVisible(),
                        "The carrier entity must hover without a nameplate.");
            }

            DemonLordSkillTower before = carriers.get(1);
            require(state.loadout().upgrade(DemonLordBinding.SLOT_2, 0), "The second slot must upgrade.");
            DemonLordService.syncCarriers(lane, state);
            DemonLordSkillTower upgraded = DemonLordService.orderedAltars(lane, owner).get(1);
            require(upgraded != before && upgraded.tier() == 2 && upgraded.binding() == DemonLordBinding.SLOT_2,
                    "Upgrading must swap in the next tier on the same key.");
            require(before.entity(lane) == null, "The replaced carrier's entity must be discarded.");

            state.loadout().remove(DemonLordBinding.SLOT_1);
            DemonLordService.syncCarriers(lane, state);
            require(DemonLordService.orderedAltars(lane, owner).size() == bindings.length - 1,
                    "Removing a skill must drop its carrier.");

            // 모든 스킬이 디스플레이 엔티티 연출을 띄워야 합니다. 슬롯은 일곱 개라 하나씩 돌려 봅니다.
            TowerVfxService.resetStats();
            for (DemonLordBinding binding : bindings) {
                state.loadout().remove(binding);
            }
            int activeBefore = kim.biryeong.semiontd.vfx.DisplayEffect.activeCount();
            for (DemonLordSkill skill : skills) {
                state.loadout().remove(DemonLordBinding.SLOT_1);
                state.loadout().assign(DemonLordBinding.SLOT_1, skill, 0);
                DemonLordService.syncCarriers(lane, state);
                DemonLordSkillTower carrier = DemonLordService.orderedAltars(lane, owner).getFirst();
                require(DemonLordVfx.showDebug(carrier, lane, carrier.entity(lane).position()),
                        skill + " must spawn its display-entity effect.");
            }
            require(kim.biryeong.semiontd.vfx.DisplayEffect.activeCount() - activeBefore == skills.length,
                    "Every skill must be a live display effect: "
                            + (kim.biryeong.semiontd.vfx.DisplayEffect.activeCount() - activeBefore));
            require(!TowerVfxService.statsSummary().contains("queued="),
                    "Demon lord skills must no longer spray the shared area particles: "
                            + TowerVfxService.statsSummary());
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord carrier GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordService.clearPlayerState(owner);
            TowerVfxService.resetStats();
        }
    }

    /** 키 슬롯에 산 스킬이 그 키로 시전됩니다. 레인에 제단이 하나도 없어도 됩니다. */
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void slotBoundSkillsCastThroughTheirKey(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        try {
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            require(state.loadout().assign(DemonLordBinding.DROP, DemonLordSkill.DEMON_BARRIER, 0),
                    "The barrier must fit into the Q slot.");
            DemonLordService.syncCarriers(lane, state);
            state.enterCombat();
            long now = context.getLevel().getGameTime();

            require(!DemonLordService.tryCast(player, lane, state, DemonLordBinding.SLOT_1, now),
                    "An empty key must not swallow the input.");
            require(DemonLordService.tryCast(player, lane, state, DemonLordBinding.DROP, now),
                    "The Q key must cast the skill bought into it.");
            require(state.shield() > 0.0, "Demon barrier must grant its shield through the carrier.");
            require(!state.isSkillReady(DemonLordSkill.DEMON_BARRIER, now), "Casting must start the cooldown.");
            require(lane.towers().isEmpty(), "Casting must not need a tower in the lane.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord slot cast GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordService.clearPlayerState(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void demonLordStaysInOwnLaneThenMovesToFinalDefense(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        try {
            SemionPlayer semionPlayer = demonLordPlayer(player);
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.enterCombat();
            state.consumePendingSpawn();

            BlockBounds laneArea = lane.laneLayout().laneArea();
            player.teleportTo(laneArea.max().getX() + 4.0, player.getY(), laneArea.max().getZ() + 4.0);
            DemonLordService.tick(lane, Map.of(player.getUUID(), semionPlayer));
            require(player.getX() >= laneArea.min().getX() && player.getX() < laneArea.max().getX() + 1.0,
                    "Before clearing, the demon lord must be returned to their own lane.");

            lane.disableMonsters();
            DemonLordService.tick(lane, Map.of(player.getUUID(), semionPlayer));
            require(state.centralDefense(), "Clearing the lane must switch the demon lord to final defense.");
            require(lane.laneLayout().isInsideFinalDefenseTowerArea(player.position()),
                    "After clearing, the demon lord must move into the final-defense area.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord combat-area GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void demonLordBlocksOtherLanesUntilFinalDefenseAndReleasesAggro(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        DemonLordSkillTower altar = altar(context, player.getUUID(), DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            lane.addTower(altar);
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            SpawnedTarget ownLane = spawnTarget(context, lane, new BlockPos(5, 2, 5), 1, 100.0, 0.0);
            SpawnedTarget otherLane = spawnTarget(context, lane, new BlockPos(6, 2, 5), 2, 100.0, 0.0);
            targets.add(ownLane);
            targets.add(otherLane);

            require(DemonLordService.dealDamage(
                            player, lane, altar, ownLane.entity(), 10.0, DamageType.TRUE).dealtDamage() > 0.0,
                    "Before clearing, the demon lord must damage their own lane.");
            require(DemonLordService.dealDamage(
                            player, lane, altar, otherLane.entity(), 10.0, DamageType.TRUE).dealtDamage() == 0.0,
                    "Before clearing, the demon lord must not damage another lane.");

            state.enterCentralDefense();
            otherLane.runtime().enterFinalDefenseCombat();
            require(DemonLordService.dealDamage(
                            player, lane, altar, otherLane.entity(), 10.0, DamageType.TRUE).dealtDamage() > 0.0,
                    "At final defense, the demon lord must damage final-defense monsters.");
            otherLane.runtime().syncLaneProgress(1.0);
            requireClose(10.0, DemonLordService.dealDamage(
                            player, lane, altar, otherLane.entity(), 10.0, DamageType.TRUE).dealtDamage(),
                    "Altar-backed skills must also damage reached-boss monsters during final defense.");

            otherLane.entity().setTarget(player);
            state.leaveCombat();
            new MonsterAttackTargetGoal(otherLane.entity(), 1.1).tick();
            require(otherLane.entity().getTarget() == null,
                    "Monsters must drop a demon lord target after they leave combat.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord targeting GameTest failed: " + failure.getMessage()));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void groundMeleeDemonLordAggroReachesSixteenBlocksInFinalDefense(GameTestHelper context) {
        var fixture = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(20, 2, 3))), GameType.ADVENTURE,
                UUID.randomUUID(), "demon-aggro-test");
        ServerPlayer player = fixture.player();
        PlayerLane lane = failedLane(context, player.getUUID());
        prepareFloor(context);
        SpawnedTarget target = spawnTarget(context, lane, new BlockPos(3, 2, 3), 2, 100.0, 0.0);
        try {
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            state.enterCentralDefense();
            target.runtime().syncLaneProgress(1.0);
            require(target.runtime().state() == MonsterState.REACHED_BOSS,
                    "A monster that broke through must enter the reached-boss state.");
            Vec3 center = Vec3.atCenterOf(context.absolutePos(new BlockPos(20, 2, 3)));
            player.teleportTo(center.x, center.y, center.z);

            target.entity().setOnGround(true);
            AcquireLaneDefenseTargetGoal goal = new AcquireLaneDefenseTargetGoal(target.entity());
            require(!goal.canUse(), "A distant final-defense monster must keep following its lane path.");

            Vec3 nearby = Vec3.atCenterOf(context.absolutePos(new BlockPos(19, 2, 3)));
            player.teleportTo(nearby.x, nearby.y, nearby.z);
            require(!target.entity().defenseSearchBox().contains(player.position()),
                    "The other lane's defense box must not hide the cross-lane regression.");
            require(goal.canUse(), "A monster inside defense range must be able to target the demon lord.");
            goal.start();
            require(target.entity().getTarget() == player,
                    "The nearby final-defense monster must acquire the demon lord. actual=" + target.entity().getTarget()
                            + "; player=" + player.getUUID() + "; indexed=" + context.getLevel().getEntity(player.getUUID())
                            + "; playerRemoved=" + player.isRemoved() + "; targetPosition=" + target.entity().position()
                            + "; playerPosition=" + player.position());
            requireClose(10.0, DemonLordService.dealDamage(
                            player, lane, null, target.entity(), 10.0, DamageType.TRUE).dealtDamage(),
                    "The demon lord must be able to damage a failed monster from another lane.");
            requireClose(90.0, target.runtime().health(), "Reached-boss damage must reduce runtime health.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord aggro-range GameTest failed: " + failure.getMessage()));
        } finally {
            target.entity().discard();
            DemonLordStates.clear(player.getUUID());
            fixture.close();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void gripOfDoomTargetsReachedBossOnlyDuringFinalDefense(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        DemonLordSkillTower altar = altar(context, player.getUUID(), DemonLordSkill.GRIP_OF_DOOM, 1, 3, 3);
        SpawnedTarget target = null;
        try {
            lane.addTower(altar);
            target = spawnTarget(context, lane, new BlockPos(5, 2, 6), 2, 500.0, 0.0);
            target.runtime().syncLaneProgress(1.0);
            require(target.runtime().state() == MonsterState.REACHED_BOSS,
                    "The skill regression must use a reached-boss monster.");
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            Vec3 start = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 3)));
            player.teleportTo(start.x, start.y, start.z);
            player.setYRot(0.0F);
            player.setXRot(0.0F);

            DemonLordSkills.cast(player, lane, state, DemonLordSkill.GRIP_OF_DOOM, altar, context.getLevel().getGameTime());
            requireClose(500.0, target.runtime().health(),
                    "The single-target skill must not hit another lane before final defense.");
            state.enterCentralDefense();
            DemonLordSkills.cast(player, lane, state, DemonLordSkill.GRIP_OF_DOOM, altar, context.getLevel().getGameTime());
            require(target.runtime().health() < 500.0,
                    "The single-target skill must acquire a reached-boss monster during final defense.");
            requireClose(500.0 - target.runtime().health(), state.roundMagicDamageDealt(),
                    "The reached-boss skill damage must enter the player statistics once.");
            context.succeed();
        } finally {
            if (target != null) target.entity().discard();
            lane.clearTowers();
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void skyBreakerAppliesSharedStunWithoutChangingLiftOrDuration(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        DemonLordSkillTower altar = altar(context, player.getUUID(), DemonLordSkill.SKY_BREAKER, 1, 3, 3);
        SpawnedTarget target = null;
        AreaEffectLaneIndex.register(lane);
        try {
            lane.addTower(altar);
            target = spawnTarget(context, lane, new BlockPos(5, 2, 6), 500.0, 0.0);
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            Vec3 start = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 3)));
            player.teleportTo(start.x, start.y, start.z);
            player.setYRot(0.0F);
            player.setXRot(0.0F);

            DemonLordSkills.cast(player, lane, state, DemonLordSkill.SKY_BREAKER, altar, context.getLevel().getGameTime());

            require(target.entity().isStunned(), "Sky Breaker must apply the same stun as electric shock.");
            require(target.entity().activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) == 40,
                    "The first-tier stun must retain its configured 40-tick duration.");
            requireClose(0.8, target.entity().getDeltaMovement().y, "Sky Breaker's forced lift must remain unchanged.");
            requireClose(0.0, target.entity().activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Sky Breaker must not leave a simulated movement debuff.");
            requireClose(0.0, target.entity().activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Sky Breaker must not multiply the next attack cooldown.");
            requireClose(0.0, target.entity().activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Sky Breaker must not implement stun as zero attack damage.");
            context.succeed();
        } finally {
            if (target != null) target.entity().discard();
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    /** 손아귀를 슬롯에 넣으면 처형 임계값 이하의 적만 그 마왕에게 빨갛게 표시되고, 회복하거나 손아귀를 빼면 꺼집니다. */
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void gripOfDoomMarksOnlyExecutableMonsters(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        try {
            SemionPlayer semionPlayer = demonLordPlayer(player);
            DemonLordState state = DemonLordStates.getOrCreate(player.getUUID());
            state.enterCombat();
            state.consumePendingSpawn();
            require(state.loadout().assign(DemonLordBinding.SLOT_1, DemonLordSkill.GRIP_OF_DOOM, 0), "Grip must be slotted.");
            SpawnedTarget weak = spawnTarget(context, lane, new BlockPos(4, 2, 4), 100.0, 0.0);
            SpawnedTarget healthy = spawnTarget(context, lane, new BlockPos(6, 2, 6), 100.0, 0.0);
            weak.runtime().damage(70.0, kim.biryeong.semiontd.entity.monster.DamageType.TRUE);
            DemonLordService.tick(lane, Map.of(player.getUUID(), semionPlayer));

            DemonLordExecuteMarks.tick(player, lane, state, DemonLordExecuteMarks.INTERVAL_TICKS);
            var marked = DemonLordExecuteMarks.markedFor(player.getUUID());
            require(marked.contains(weak.entity().getId()), "A monster at 30% health must be marked for execution.");
            require(!marked.contains(healthy.entity().getId()), "A healthy monster must not be marked.");

            weak.runtime().heal(60.0);
            DemonLordExecuteMarks.tick(player, lane, state, DemonLordExecuteMarks.INTERVAL_TICKS * 2L);
            require(DemonLordExecuteMarks.markedFor(player.getUUID()).isEmpty(), "Healing past the threshold must clear the mark.");

            weak.runtime().damage(70.0, kim.biryeong.semiontd.entity.monster.DamageType.TRUE);
            DemonLordExecuteMarks.tick(player, lane, state, DemonLordExecuteMarks.INTERVAL_TICKS * 3L);
            require(!DemonLordExecuteMarks.markedFor(player.getUUID()).isEmpty(), "The mark must come back.");
            state.loadout().remove(DemonLordBinding.SLOT_1);
            DemonLordService.tick(lane, Map.of(player.getUUID(), semionPlayer));
            DemonLordExecuteMarks.tick(player, lane, state, DemonLordExecuteMarks.INTERVAL_TICKS * 4L);
            require(DemonLordExecuteMarks.markedFor(player.getUUID()).isEmpty(), "Without the grip nothing is marked.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Execute mark GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void reconnectRestoresCombatAccelerationAndRemainingCooldown(GameTestHelper context) {
        var server = context.getLevel().getServer();
        float originalRate = server.tickRateManager().tickrate();
        UUID owner = UUID.randomUUID();
        var position = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5)));
        var lane = testLane(context, owner);
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        try {
            server.tickRateManager().setTickRate(40.0F);
            state.enterCombat();
            state.consumePendingSpawn();
            state.startCooldown(DemonLordSkill.DEMON_BARRIER, context.getLevel().getGameTime(), 200);
            try (var first = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                    position, GameType.ADVENTURE, owner, "demon-rejoin")) {
                DemonLordService.tick(lane, Map.of(owner, demonLordPlayer(first.player())));
                kim.biryeong.semiontd.ui.GambleRevealService.clear(first.player().getUUID());
                kim.biryeong.semiontd.tower.demonlord.DemonLordService.disconnectPlayer(first.player());
                kim.biryeong.semiontd.tower.frost.FrostFullOperationService.cleanupPlayer(first.player());
                require(DemonLordStates.get(owner) == state && state.inCombat(),
                        "A temporary disconnect must preserve the active combat state until the match ends.");
            }
            try (var second = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                    position, GameType.ADVENTURE, owner, "demon-rejoin")) {
                var player = second.player();
                List<net.minecraft.network.protocol.Packet<?>> packets = new ArrayList<>();
                var originalConnection = player.connection;
                player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                        new Connection(PacketFlow.SERVERBOUND), player,
                        CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
                };
                try {
                    DemonLordService.tick(lane, Map.of(owner, demonLordPlayer(player)));
                    requireClose(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() * 2,
                            player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                            "Rejoining during combat must reapply the current movement acceleration.");
                    var group = player.getCooldowns().getCooldownGroup(new ItemStack(DemonLordSkill.DEMON_BARRIER.item()));
                    require(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundCooldownPacket cooldown
                                    && cooldown.cooldownGroup().equals(group) && cooldown.duration() == 100),
                            "Rejoining at 40 TPS must resend the remaining 200 server ticks as 100 client ticks.");
                    packets.clear();
                    server.tickRateManager().setTickRate(20.0F);
                    DemonLordService.tick(lane, Map.of(owner, demonLordPlayer(player)));
                    require(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundCooldownPacket cooldown
                                    && cooldown.cooldownGroup().equals(group) && cooldown.duration() == 200),
                            "Returning to 20 TPS must restore the remaining cooldown display without changing readiness.");
                    require(!state.isSkillReady(DemonLordSkill.DEMON_BARRIER, context.getLevel().getGameTime() + 199)
                                    && state.isSkillReady(DemonLordSkill.DEMON_BARRIER, context.getLevel().getGameTime() + 200),
                            "Client display conversion must not accelerate the server cooldown a second time.");
                    DemonLordService.cleanupPlayer(player);
                    require(DemonLordStates.get(owner) == null, "Final cleanup must still remove match state.");
                    context.succeed();
                } finally {
                    player.connection = originalConnection;
                    DemonLordService.cleanupPlayer(player);
                }
            }
        } finally {
            DemonLordService.clearPlayerState(owner);
            server.tickRateManager().setTickRate(originalRate);
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void demonLordAggroHasSeparateAcquisitionAndRetentionRanges(GameTestHelper context) {
        prepareFloor(context);
        var origin = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 3)));
        try (var first = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                origin.add(15.9, 0, 0), GameType.ADVENTURE, UUID.randomUUID(), "demon-near");
             var second = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                origin.add(16, 0, 0), GameType.ADVENTURE, UUID.randomUUID(), "demon-far")) {
            var lane = testLane(context, first.player().getUUID());
            var target = spawnTarget(context, lane, new BlockPos(2, 2, 3), 100, 0);
            try {
                for (var player : List.of(first.player(), second.player())) {
                    var state = DemonLordStates.getOrCreate(player.getUUID());
                    state.setLaneId(1);
                    state.enterCombat();
                }
                target.entity().setOnGround(true);
                var acquire = new AcquireLaneDefenseTargetGoal(target.entity());
                require(acquire.canUse(), "A ground melee monster must acquire an eligible demon lord at 15.9 blocks.");
                acquire.start();
                require(target.entity().getTarget() == first.player(), "The nearer eligible owner must win.");
                target.entity().setOnGround(true);
                first.player().setPos(origin.add(16, 0, 0));
                require(target.entity().canTargetDefense(first.player()), "An existing ground target is retained at 16 blocks.");
                first.player().setPos(origin.add(16.1, 0, 0));
                new MonsterAttackTargetGoal(target.entity(), 1.0).tick();
                require(target.entity().getTarget() == null, "A ground target beyond 16 blocks must be released.");
                second.player().setPos(origin.add(16.1, 0, 0));
                require(!acquire.canUse(), "Neither a just-outside nor distant target can be newly acquired.");
                second.player().setPos(origin.add(16, 0, 0));
                require(acquire.canUse(), "The sixteen-block ground melee acquisition boundary is inclusive.");
                DemonLordStates.get(second.player().getUUID()).setLaneId(2);
                require(!acquire.canUse(), "A nearby demon lord in another lane cannot attract this monster.");
                DemonLordStates.get(second.player().getUUID()).setLaneId(1);
                DemonLordStates.get(second.player().getUUID()).standDown();
                require(!acquire.canUse(), "A resting demon lord cannot attract this monster.");
                target.entity().setTarget(first.player());
                target.entity().setOnGround(false);
                require(target.entity().canTargetDefense(first.player()), "An airborne monster retains its existing target beyond the ground leash.");
                first.player().setHealth(0.0F);
                require(!new MonsterAttackTargetGoal(target.entity(), 1.0).canUse(), "The attack goal must stop for a dead player.");
                context.succeed();
            } finally {
                target.entity().discard();
                DemonLordStates.clear(first.player().getUUID());
                DemonLordStates.clear(second.player().getUUID());
            }
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void nearbyAggroMovesTowardThePlayerButCannotPassThroughAWall(GameTestHelper context) {
        prepareFloor(context);
        var origin = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 3)));
        try (var fixture = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                origin.add(15.9, 0, 0), GameType.ADVENTURE, UUID.randomUUID(), "demon-path-test")) {
            var player = fixture.player();
            var lane = testLane(context, player.getUUID());
            var target = spawnTarget(context, lane, new BlockPos(3, 2, 3), 1000, 0);
            try {
                var state = DemonLordStates.getOrCreate(player.getUUID());
                state.setLaneId(1);
                state.enterCombat();
                target.entity().setPos(origin);
                double before = target.entity().distanceToSqr(player);
                for (int tick = 0; tick < 30; tick++) target.entity().tick();
                require(target.entity().getTarget() == player, "Normal ground melee entity AI must acquire the player at 15.9 blocks.");
                require(target.entity().distanceToSqr(player) < before - 1,
                        "Acquisition must result in actual movement toward the player.");
                target.entity().setPos(origin);
                target.entity().setDeltaMovement(Vec3.ZERO);
                target.entity().getNavigation().stop();
                for (int y = 2; y <= 6; y++) {
                    for (int z = 0; z <= 16; z++) {
                        context.getLevel().setBlockAndUpdate(context.absolutePos(new BlockPos(6, y, z)), Blocks.STONE.defaultBlockState());
                    }
                }
                for (int tick = 0; tick < 80; tick++) target.entity().tick();
                require(target.entity().getX() < context.absolutePos(new BlockPos(6, 2, 3)).getX(),
                        "An impassable wall must block pursuit even when aggro remains active.");
                player.discard();
                require(!new MonsterAttackTargetGoal(target.entity(), 1.0).canUse(), "Pursuit must stop when the player is removed.");
                context.succeed();
            } finally {
                target.entity().discard();
                DemonLordStates.clear(player.getUUID());
            }
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void rangedAndAirborneAcquisitionAndDefenseTowerPriorityStayUnchanged(GameTestHelper context) {
        prepareFloor(context);
        var origin = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 3)));
        try (var fixture = kim.biryeong.semiontd.gametest.RuntimePlayerFixture.connect(context, context.getLevel(),
                origin.add(16, 0, 0), GameType.ADVENTURE, UUID.randomUUID(), "demon-range-kind")) {
            var player = fixture.player();
            var lane = testLane(context, player.getUUID());
            var melee = spawnTarget(context, lane, new BlockPos(2, 2, 3), 1, 1000, 0, AttackKind.MELEE);
            var ranged = spawnTarget(context, lane, new BlockPos(2, 2, 3), 1, 1000, 0, AttackKind.RANGED);
            try {
                var state = DemonLordStates.getOrCreate(player.getUUID());
                state.setLaneId(1);
                state.enterCombat();
                melee.entity().setOnGround(true);
                ranged.entity().setOnGround(true);
                var meleeGoal = new AcquireLaneDefenseTargetGoal(melee.entity());
                var rangedGoal = new AcquireLaneDefenseTargetGoal(ranged.entity());
                require(meleeGoal.canUse(), "Ground melee must acquire at sixteen blocks.");
                meleeGoal.start();
                for (int tick = 0; tick < 5; tick++) new MonsterAttackTargetGoal(melee.entity(), 1.0).tick();
                require(melee.entity().getTarget() == player, "The inclusive acquisition boundary must also retain the target.");
                melee.entity().setTarget(null);
                require(!rangedGoal.canUse(), "Ranged acquisition must not grow to sixteen blocks.");
                requireClose(8, melee.entity().defenseTargetSearchRange(), "Ordinary defense search must remain unchanged.");
                melee.entity().setOnGround(false);
                require(!meleeGoal.canUse(), "Airborne acquisition must not grow to sixteen blocks.");
                player.setPos(origin.add(8, 0, 0));
                require(meleeGoal.canUse() && rangedGoal.canUse(), "Existing eight-block acquisition remains inclusive.");
                player.setPos(origin.add(8.01, 0, 0));
                require(!meleeGoal.canUse() && !rangedGoal.canUse(), "Airborne and ranged acquisition keep their old boundary.");
                melee.entity().setOnGround(true);
                melee.runtime().applyCombatProfile(1, 20, 20);
                ranged.runtime().applyCombatProfile(1, 20, 20);
                player.setPos(origin.add(20, 0, 0));
                require(meleeGoal.canUse() && rangedGoal.canUse(), "A pre-existing attack range greater than sixteen must not shrink.");
                meleeGoal.start();
                new MonsterAttackTargetGoal(melee.entity(), 1.0).tick();
                require(melee.entity().getTarget() == player, "A longer attack range must also retain its existing target.");
                melee.entity().setTarget(null);
                melee.runtime().applyCombatProfile(1, 1.5, 20);
                player.setPos(origin.add(16.1, 0, 0));
                require(!meleeGoal.canUse(), "A ground melee player just outside sixteen must not be acquired.");
                player.setPos(origin.add(27, 0, 0));
                require(!meleeGoal.canUse(), "A far player must not be acquired.");
                player.setPos(origin.add(15.9, 0, 0));
                var tower = new kim.biryeong.semiontd.tower.ProductionTower(
                        kim.biryeong.semiontd.tower.insect.InsectTowers.SPAWNER, player.getUUID(), TeamId.RED, 1,
                        grid(context, new BlockPos(4, 2, 3)));
                lane.addTower(tower);
                var defender = context.getLevel().getEntity(tower.entityId().orElseThrow());
                require(defender != null, "The ordinary defense tower must exist in the test arena.");
                meleeGoal.start();
                require(melee.entity().getTarget() == defender, "An ordinary defense target must still take priority over the demon lord.");
                context.succeed();
            } finally {
                melee.entity().discard();
                ranged.entity().discard();
                lane.clearTowers();
                DemonLordStates.clear(player.getUUID());
            }
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void pointScalingFollowsAugmentsBeforeDefenseAndPreservesTrueDamage(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        var altar = altar(context, player.getUUID(), DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        var targets = new ArrayList<SpawnedTarget>();
        try {
            lane.addTower(altar);
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                    new PlayerAugmentState.Selection(5, AugmentRarity.PRISMATIC, "one_man_show",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()),
                    new PlayerAugmentState.Selection(15, AugmentRarity.PRISMATIC, "tactical_designation_3_assault",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            var state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            double expectedPhysical = (100 + 100 * Math.log(3.2)) / 2;
            double expectedMagic = 100 + 50 * Math.log(5.4);
            for (DemonLordSkillTower carrier : java.util.Arrays.asList(null, altar)) {
                var target = spawnTarget(context, lane, new BlockPos(5, 2, 5), 10000, 100);
                targets.add(target);
                var physical = DemonLordService.dealDamage(player, lane, carrier, target.entity(), 200, DamageType.PHYSICAL);
                var magic = DemonLordService.dealDamage(player, lane, carrier, target.entity(), 200, DamageType.MAGIC);
                requireClose(expectedPhysical, physical.dealtDamage(), "Augments precede scaling, and armor follows it.");
                requireClose(expectedMagic, magic.dealtDamage(), "Both carrier paths must scale magic once.");
                requireClose(10000 - expectedPhysical - expectedMagic, target.runtime().health(), "Actual HP must match scaled damage.");
            }
            requireClose(expectedPhysical * 2, state.roundPhysicalDamageDealt(), "Physical statistics count each hit once.");
            requireClose(expectedMagic * 2, state.roundMagicDamageDealt(), "Magic statistics count each hit once.");
            var resistant = spawnTarget(context, lane, new BlockPos(6, 2, 6), 1, 10000, 0, AttackKind.MELEE, 100);
            targets.add(resistant);
            requireClose(expectedMagic / 2, DemonLordService.dealDamage(player, lane, null, resistant.entity(), 200, DamageType.MAGIC).dealtDamage(),
                    "Magic resistance follows scaling even without a carrier.");
            resistant.entity().applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, .5, 100);
            requireClose(expectedMagic * 1.5 / 2, DemonLordService.dealDamage(player, lane, altar, resistant.entity(), 200, DamageType.MAGIC).dealtDamage(),
                    "Incoming vulnerability and resistance must follow the curve.");
            var immune = spawnTarget(context, lane, new BlockPos(6, 2, 5), 1000, 0);
            targets.add(immune);
            immune.entity().applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 1, 100);
            requireClose(0, DemonLordService.dealDamage(player, lane, altar, immune.entity(), 500, DamageType.MAGIC).dealtDamage(),
                    "Complete incoming damage reduction must still prevent damage.");
            lane.assignAugmentSnapshot(AugmentSnapshot.none());
            var executable = spawnTarget(context, lane, new BlockPos(7, 2, 5), 1000, 10000);
            targets.add(executable);
            var result = DemonLordService.dealDamage(player, lane, altar, executable.entity(), 1000, DamageType.TRUE);
            require(result.killed(), "TRUE damage must bypass the new curve and armor.");
            requireClose(1000, result.dealtDamage(), "Execution damage must not be logarithmically reduced.");
            context.succeed();
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void repeatedZonesScaleEachTargetOnceAndLifestealUsesActualDamage(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        AreaEffectLaneIndex.register(lane);
        var targets = new ArrayList<SpawnedTarget>();
        try {
            var state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            state.loadout().assign(DemonLordBinding.values()[0], DemonLordSkill.HELLFIRE_BRAND, 0);
            DemonLordService.syncCarriers(lane, state);
            var altar = DemonLordService.orderedAltars(lane, player.getUUID()).getFirst();
            var first = spawnTarget(context, lane, new BlockPos(5, 2, 5), 10000, 0);
            var second = spawnTarget(context, lane, new BlockPos(6, 2, 5), 10000, 0);
            targets.add(first);
            targets.add(second);
            state.placeZone(new DemonLordState.HellfireZone(altar.type(), first.entity().position(), 3, 200, 0, 20, 100, 20));
            DemonLordSkills.tickPending(player, lane, state, 20);
            DemonLordSkills.tickPending(player, lane, state, 40);
            double pulse = 154.93061443340548;
            requireClose(10000 - 2 * pulse, first.runtime().health(), "First target must receive two independently scaled pulses.");
            requireClose(10000 - 2 * pulse, second.runtime().health(), "Area targets must not share a damage budget.");
            requireClose(200, state.zone().damage(), "Zone state must retain unscaled damage for the next pulse.");
            state.applyDamage(200);
            double before = state.health();
            double actual = DemonLordService.dealDamage(player, lane, altar, first.entity(), 50, DamageType.PHYSICAL).dealtDamage();
            second.entity().discard();
            DemonLordPassives.bloodCleave(player, lane, state, altar, first.entity(), 50, actual);
            requireClose(before + 7.5, state.health(), "Uncapped lifesteal must use actual damage.");
            before = state.health();
            actual = DemonLordService.dealDamage(player, lane, altar, first.entity(), 500, DamageType.PHYSICAL).dealtDamage();
            DemonLordPassives.bloodCleave(player, lane, state, altar, first.entity(), 500, actual);
            requireClose(before + state.maxHealth() * .04, state.health(), "The per-attack lifesteal cap remains unchanged.");
            requireClose(pulse * .25, DemonLordSkills.soulDrainHealing(pulse, state.maxHealth(), .25, .12),
                    "Soul drain uses post-scaling actual damage.");
            context.succeed();
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            DemonLordService.cleanupPlayer(player);
            player.discard();
        }
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void soulDrainHealsFromScaledResistedDamageAndRetainsPerCastCap(GameTestHelper context) {
        var defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(),
                Map.of(DemonLordSkill.SOUL_DRAIN.towerId(1), Map.of("damage", 200.0))).withMissingDefaults(defaults));
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        PlayerLane lane = testLane(context, player.getUUID());
        prepareFloor(context);
        AreaEffectLaneIndex.register(lane);
        var altar = altar(context, player.getUUID(), DemonLordSkill.SOUL_DRAIN, 1, 3, 3);
        var targets = new ArrayList<SpawnedTarget>();
        try {
            lane.addTower(altar);
            var state = DemonLordStates.getOrCreate(player.getUUID());
            state.setLaneId(1);
            state.enterCombat();
            state.applyDamage(200);
            Vec3 start = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 3)));
            player.teleportTo(start.x, start.y, start.z);
            player.setYRot(0);
            player.setXRot(0);
            var target = spawnTarget(context, lane, new BlockPos(5, 2, 6), 1, 10000, 0, AttackKind.MELEE, 100);
            targets.add(target);
            double before = state.health();
            DemonLordSkills.cast(player, lane, state, DemonLordSkill.SOUL_DRAIN, altar, 0);
            double actual = 154.93061443340548 / 2;
            requireClose(10000 - actual, target.runtime().health(), "Soul drain must scale before resistance.");
            requireClose(before + actual * .25, state.health(), "Soul drain healing must use actual post-defense damage.");
            targets.add(spawnTarget(context, lane, new BlockPos(5, 2, 7), 10000, 0));
            targets.add(spawnTarget(context, lane, new BlockPos(5, 2, 8), 10000, 0));
            before = state.health();
            DemonLordSkills.cast(player, lane, state, DemonLordSkill.SOUL_DRAIN, altar, 1);
            requireClose(before + state.maxHealth() * .12, state.health(), "Multiple targets still share the existing healing cap per cast.");
            context.succeed();
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            DemonLordStates.clear(player.getUUID());
            player.discard();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void summonFreePositionsMatchNestedScanAfterTowerChanges(GameTestHelper context) {
        UUID owner = stableUuid("demon-lord-column-owner");
        PlayerLane lane = testLane(context, owner);
        PlayerLane otherLane = testLane(context, stableUuid("demon-lord-column-other-lane"));
        prepareFloor(context);
        try {
            List<GridPosition> empty = assertFreePositionsMatch(lane);
            require(!empty.isEmpty(), "The fixture must offer summon positions.");
            DemonLordSkillTower low = altar(context, owner, DemonLordSkill.WAVE_OF_MALICE, 1, 0, 0);
            DemonLordSkillTower high = altar(context, owner, DemonLordSkill.WAVE_OF_MALICE, 1, 16, 16);
            DemonLordSkillTower foreign = altar(context, stableUuid("demon-lord-column-foreign"),
                    DemonLordSkill.WAVE_OF_MALICE, 1, 4, 4);
            DemonLordSkillTower duplicate = altar(context, owner, DemonLordSkill.WAVE_OF_MALICE, 1, 4, 4);
            lane.addTower(low);
            lane.addTower(high);
            lane.addTower(foreign);
            lane.addTower(duplicate);
            GridPosition foreignAt = foreign.position();
            duplicate.syncPosition(new GridPosition(foreignAt.x(), foreignAt.y() + 9, foreignAt.z()));
            foreign.syncHealth(0.0);
            List<GridPosition> occupied = assertFreePositionsMatch(lane);
            require(occupied.size() == empty.size() - 3,
                    "Both boundary columns and the shared foreign/dead column must be excluded once.");
            require(assertFreePositionsMatch(otherLane).equals(empty), "Occupancy must remain local to each lane.");
            lane.removeTower(duplicate);
            require(assertFreePositionsMatch(lane).equals(occupied),
                    "Removing a duplicate must not free a column still occupied by a dead foreign tower.");
            GridPosition moved = grid(context, new BlockPos(7, 5, 8));
            foreign.syncPosition(moved);
            List<GridPosition> afterMove = assertFreePositionsMatch(lane);
            require(afterMove.stream().anyMatch(position -> position.x() == foreignAt.x() && position.z() == foreignAt.z()),
                    "The previous column must be available immediately after movement.");
            require(afterMove.stream().noneMatch(position -> position.x() == moved.x() && position.z() == moved.z()),
                    "The moved column must be occupied regardless of height or health.");
            lane.removeTower(foreign);
            require(assertFreePositionsMatch(lane).size() == empty.size() - 2,
                    "Removing the moved tower must free its column on the next call.");
            lane.removeTower(low);
            lane.removeTower(high);
            require(assertFreePositionsMatch(lane).equals(empty),
                    "Removing all towers must restore the original ordered positions.");
            context.succeed();
        } finally {
            lane.clearTowers();
            otherLane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            AreaEffectLaneIndex.unregister(otherLane);
        }
    }

    private static List<GridPosition> assertFreePositionsMatch(PlayerLane lane) {
        List<GridPosition> expected = legacyFreePositions(lane);
        List<GridPosition> actual = DemonLordPassives.freePositions(lane);
        require(actual.equals(expected), "Summon positions must equal the nested scan in the same order.");
        java.util.Random expectedRandom = new java.util.Random(7159);
        java.util.Random actualRandom = new java.util.Random(7159);
        List<GridPosition> expectedShuffled = new ArrayList<>(expected);
        List<GridPosition> actualShuffled = new ArrayList<>(actual);
        java.util.Collections.shuffle(expectedShuffled, expectedRandom);
        java.util.Collections.shuffle(actualShuffled, actualRandom);
        require(actualShuffled.equals(expectedShuffled) && actualRandom.nextLong() == expectedRandom.nextLong(),
                "The same seed must preserve placement choices and following random draws.");
        return actual;
    }

    private static List<GridPosition> legacyFreePositions(PlayerLane lane) {
        BlockBounds bounds = lane.laneLayout().laneArea();
        List<GridPosition> free = new ArrayList<>();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                Optional<BlockPos> floor = kim.biryeong.semiontd.tower.TowerPlacementPositions.resolve(
                        lane, new BlockPos(x, bounds.max().getY(), z));
                if (floor.isEmpty()) {
                    continue;
                }
                GridPosition position = GridPosition.from(floor.get());
                if (lane.towers().stream().noneMatch(tower -> tower.position().x() == position.x()
                        && tower.position().z() == position.z())) {
                    free.add(position);
                }
            }
        }
        return free;
    }

    private static SemionPlayer demonLordPlayer(ServerPlayer player) {
        SemionPlayer semionPlayer = new SemionPlayer(
                player.getUUID(), player.getGameProfile().name(), TeamId.RED, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        semionPlayer.assignJob(new DemonLordTowerJob());
        return semionPlayer;
    }

    private static DemonLordSkillTower altar(
            GameTestHelper context,
            UUID owner,
            DemonLordSkill skill,
            int tier,
            int x,
            int z
    ) {
        GridPosition position = grid(context, new BlockPos(x, 2, z));
        return new DemonLordSkillTower(
                DemonLordTowers.tower(skill, tier), owner, TeamId.RED, 1, position, position);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            BlockPos relative,
            double health,
            double armor
    ) {
        return spawnTarget(context, lane, relative, 1, health, armor);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            BlockPos relative,
            int targetLaneId,
            double health,
            double armor
    ) {
        return spawnTarget(context, lane, relative, targetLaneId, health, armor, AttackKind.MELEE);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context, PlayerLane lane, BlockPos relative, int targetLaneId,
            double health, double armor, AttackKind attackKind
    ) {
        return spawnTarget(context, lane, relative, targetLaneId, health, armor, attackKind, 0.0);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context, PlayerLane lane, BlockPos relative, int targetLaneId,
            double health, double armor, AttackKind attackKind, double resistance
    ) {
        Monster runtime = new Monster(
                "demon-lord-target-" + relative.toShortString(),
                TeamId.RED,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                health,
                armor,
                1.0,
                attackKind,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                resistance,
                null,
                List.of(),
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        Vec3 position = Vec3.atCenterOf(context.absolutePos(relative));
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Demon lord test target must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static PlayerLane augmentLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 6, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1, Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                BlockBounds.of(min, min),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 3)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 6))),
                BlockBounds.of(min, max), List.of(grid(context, new BlockPos(5, 2, 6))), 1);
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(16, 6, 16));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                BlockBounds.of(min, min),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 13))),
                BlockBounds.of(min, max),
                List.of(grid(context, new BlockPos(10, 2, 10))),
                1
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static PlayerLane failedLane(GameTestHelper context, UUID owner) {
        BlockPos corner = context.absolutePos(new BlockPos(0, 1, 0));
        LaneRegionLayout layout = new LaneRegionLayout(
                2,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                BlockBounds.of(corner, corner),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                BlockBounds.of(corner, corner),
                List.of(grid(context, new BlockPos(1, 2, 1))),
                1
        );
        return new PlayerLane(TeamId.RED, 2, owner, context.getLevel(), layout);
    }

    private static GridPosition grid(GameTestHelper context, BlockPos relative) {
        return GridPosition.from(context.absolutePos(relative));
    }

    private static void prepareFloor(GameTestHelper context) {
        prepareFloor(context, 16);
    }

    private static void prepareFloor(GameTestHelper context, int max) {
        context.assertTrue(context.getBounds().contains(Vec3.atCenterOf(context.absolutePos(new BlockPos(max, 2, max)))),
                "The declared Demon Lord structure must contain the complete carrier and movement arena.");
        for (int x = 0; x <= max; x++) {
            for (int z = 0; z <= max; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.001) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private record SpawnedTarget(Monster runtime, SemionMonsterEntity entity) {
    }
}
