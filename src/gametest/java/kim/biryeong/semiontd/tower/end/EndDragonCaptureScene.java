package kim.biryeong.semiontd.tower.end;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EndDragonCaptureScene implements AutoCloseable {
    private final ServerPlayer viewer;
    private final PlayerLane lane;
    private final EndTower core;
    private final SemionTowerEntity source;
    private final Runnable verifyReturnedState;
    private boolean closed;
    private boolean begun;
    private int tick;
    private EndDragonAssault.Phase lastPhase;

    public EndDragonCaptureScene(ServerPlayer viewer) {
        this(viewer, false);
    }

    public EndDragonCaptureScene(ServerPlayer viewer, boolean frontView) {
        if (!Boolean.getBoolean("semiontd.capture") || !"dragon".equals(System.getProperty("semiontd.capture.mode"))) {
            throw new IllegalStateException("The isolated dragon capture mode must be enabled");
        }
        if (!viewer.getGameProfile().name().equals("SemionCapture")) {
            throw new IllegalStateException("The isolated capture account is required");
        }
        if (!eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(viewer)) {
            throw new IllegalStateException("The capture resource pack must be active");
        }
        this.viewer = viewer;
        var level = viewer.level();
        for (int x = -6; x <= 56; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlock(new BlockPos(x, 239, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = 240; y < 244; y++) {
            level.setBlock(new BlockPos(0, y, 5), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            level.setBlock(new BlockPos(50, y, 5), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
        BlockBounds spawn = BlockBounds.of(new BlockPos(43, 240, -3), new BlockPos(49, 240, 3));
        BlockBounds path = BlockBounds.of(new BlockPos(0, 240, -3), new BlockPos(42, 240, 3));
        Vec3 rear = new Vec3(0, 241, .5);
        Vec3 front = new Vec3(46.5, 241, .5);
        var layout = new LaneRegionLayout(1, front, spawn, List.of(rear), rear, path, List.of());
        UUID owner = viewer.getUUID();
        lane = new PlayerLane(TeamId.RED, 1, owner, level, layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                new PlayerAugmentState.Selection(5, AugmentRarity.PRISMATIC, EndAugments.ASSAULT,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
        AreaEffectLaneIndex.register(lane);
        TowerType base = EndTowers.BASE_END_TOWER;
        TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                base.description(), base.visual(), base.upgradeOptions());
        core = new EndTower(giant, owner, TeamId.RED, 1, new GridPosition(25, 241, 0));
        lane.addTower(core);
        core.onWaveStarted(lane, 5);
        source = core.runtimeEntity(lane).orElseThrow();
        source.setNoAi(false);
        var logicalId = core.logicalId();
        var entityUuid = source.getUUID();
        var original = core.originalPosition();
        var stats = core.transferStats();
        var paid = core.paidMineralCost();
        var health = source.getHealth();
        verifyReturnedState = () -> {
            if (!core.logicalId().equals(logicalId) || !source.getUUID().equals(entityUuid)
                    || !core.ownerPlayer().equals(owner) || !core.originalPosition().equals(original)
                    || !core.transferStats().equals(stats) || core.paidMineralCost() != paid
                    || source.getHealth() != health || lane.towers().size() != 1
                    || core.runtimeEntity(lane).orElseThrow() != source
                    || lane.towerAt(original) != core || lane.towerAt(core.position()) != core
                    || source.isInvisible() || source.isNoAi()
                    || Math.abs(source.getX() - 21.5) > .01 || Math.abs(source.getZ() - .5) > .01) {
                throw new AssertionError("The GPU scene return must preserve entity, ownership, HP, growth, payment and management reservations");
            }
        };
        viewer.setGameMode(GameType.CREATIVE);
        viewer.setNoGravity(true);
        viewer.getAbilities().mayfly = true;
        viewer.getAbilities().flying = true;
        viewer.onUpdateAbilities();
        if (frontView) {
            viewer.teleportTo(level, 70, 255, .5, Set.of(), 90, 16, true);
        } else {
            viewer.teleportTo(level, 25, 255, -22, Set.of(), 0, 10, true);
        }
        viewer.setExperienceLevels(2030);
        System.out.println("SEMION_DRAGON_CAPTURE_READY entity=" + source.getId() + " uuid=" + source.getUUID()
                + " floor=240 direction=1,0,0 rear=0,241,.5 front=50,241,.5 camera=25,255,-22"
                + " evolution=" + core.state() + " health=" + core.maxHealth() + " clientType=" + source.getPolymerEntityType(null));
    }

    public void begin() {
        if (closed || begun) throw new IllegalStateException("The scene starts exactly once");
        begun = true;
    }

    public void tick() {
        if (closed || !begun) return;
        if (viewer.hasDisconnected()) {
            close();
            return;
        }
        core.tick(lane);
        tick++;
        var phase = core.assaultPhase();
        if (phase == EndDragonAssault.Phase.SPENT) {
            verifyReturnedState.run();
            if (phase != lastPhase) {
                System.out.println("SEMION_DRAGON_RETURN_STATE_VERIFIED entity=" + source.getId()
                        + " uuid=" + source.getUUID() + " logicalId=" + core.logicalId()
                        + " owner=" + core.ownerPlayer() + " health=" + source.getHealth()
                        + " stats=" + core.transferStats() + " paid=" + core.paidMineralCost()
                        + " original=" + core.originalPosition() + " position=" + core.position());
            }
        }
        if (phase != lastPhase) {
            viewer.setExperienceLevels(switch (phase) {
                case READY -> 2030;
                case CHARGING -> 2031;
                case RUSHING -> 2032;
                case EXITING -> 2033;
                case VANISHED -> 2034;
                case BREATHING -> 2035;
                case RETURNING -> 2036;
                case SPENT -> 2037;
            });
            viewer.connection.send(new ClientboundSetExperiencePacket(
                    viewer.experienceProgress, viewer.totalExperience, viewer.experienceLevel));
            lastPhase = phase;
        }
        System.out.println("SEMION_DRAGON_SERVER tick=" + tick + " entity=" + source.getId()
                + " phase=" + phase + " pos=" + source.position() + " yaw=" + source.getYRot()
                + " pitch=" + source.getXRot() + " noAi=" + source.isNoAi()
                + " invisible=" + source.isInvisible() + " clientType=" + source.getPolymerEntityType(null));
        if (tick > 360) begun = false;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        lane.clearTowers();
        AreaEffectLaneIndex.unregister(lane);
    }
}
