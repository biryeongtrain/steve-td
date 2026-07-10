package kim.biryeong.semiontd.entity.tower.vfx;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import kim.biryeong.gcbserver.packet.s2c.GCBParticleS2CPacket;
import kim.biryeong.gcbserver.player.GCBPlayer;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxContext;
import kim.biryeong.semiontd.api.area.AreaVfxOutput;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.config.VfxConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.area.AreaVfxStyleRegistryImpl;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mcbrincie.apel.lib.renderers.BaseApelRenderer;
import net.mcbrincie.apel.lib.util.math.bezier.QuadraticBezierCurve;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class TowerVfxService {
    private static final double RANGED_ATTACK_RANGE_THRESHOLD = 3.0;
    private static final double SPECTATOR_RADIUS_SQR = 64.0 * 64.0;
    private static final int MAX_QUEUE_DEPTH_PER_LANE = 512;
    private static final long MAX_EVENT_AGE_TICKS = 2L;
    private static final long LANE_STATE_TTL_TICKS = 200L;
    private static final int MIN_RAY_POINTS = 12;
    private static final int MAX_RAY_POINTS = 64;
    private static final double RAY_POINTS_PER_BLOCK = 5.0;
    private static final float HORIZONTAL_ROTATION_X = (float) (Math.PI / 2.0);

    private static final DustParticleOptions MARK_PARTICLE = new DustParticleOptions(0xB388FF, 0.9F);
    private static final DustParticleOptions LIFE_STEAL_PARTICLE = new DustParticleOptions(0xE53935, 0.85F);
    private static final DustParticleOptions NETHER_TRANSITION_PARTICLE = new DustParticleOptions(0xFF6D00, 1.2F);
    private static final DustParticleOptions ZOMBIE_TRANSITION_PARTICLE = new DustParticleOptions(0x6D8B3D, 1.0F);

    private static final Set<String> UNDEAD_TOWER_IDS = Set.of(
            UndeadTowers.T1_ZOMBIE_TOWER.id(), UndeadTowers.T2_ZOMBIE_TOWER.id(), UndeadTowers.T3_ZOMBIE_TOWER.id(),
            UndeadTowers.T1_SKELETON_TOWER.id(), UndeadTowers.T2_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T2_MELEE_TOWER.id(), UndeadTowers.T3_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T3_MELEE_TOWER.id(), UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id(),
            UndeadTowers.T2_UNDEAD_ANIMAL_TOWER.id()
    );
    private static final Set<String> ANIMAL_TOWER_IDS = Set.of(
            AnimalTowers.T1_PIG_TOWER.id(), AnimalTowers.T2_PIG_TOWER.id(), AnimalTowers.T3_PIG_TOWER.id(),
            AnimalTowers.T1_WOLF_TOWER.id(), AnimalTowers.T2_WOLF_DPS_TOWER.id(), AnimalTowers.T3_WOLF_DPS_TOWER.id(),
            AnimalTowers.T1_RABBIT_TOWER.id(), AnimalTowers.T2_RABBIT_TOWER.id(), AnimalTowers.T3_RABBIT_TOWER.id(),
            AnimalTowers.T1_FOX_TOWER.id(), AnimalTowers.T2_FOX_TOWER.id(), AnimalTowers.T3_FOX_TOWER.id()
    );

    private static final ConcurrentLinkedQueue<PendingEvent> EVENTS = new ConcurrentLinkedQueue<>();
    private static final Map<VfxLaneKey, Integer> QUEUED_BY_LANE = new HashMap<>();
    private static final Map<VfxLaneKey, TowerVfxBudget> VANILLA_BUDGETS = new ConcurrentHashMap<>();
    private static final Map<VfxLaneKey, LaneStats> STATS = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Object EXECUTOR_LOCK = new Object();

    private static volatile VfxConfig config = VfxConfig.defaultConfig();
    private static volatile SemionGameManager gameManager;
    private static volatile AreaVfxStyleRegistryImpl areaVfxStyles;
    private static volatile ExecutorService executor;
    private static volatile Consumer<AreaEffectVfxEvent> areaEffectTestObserver;
    private static final Set<net.minecraft.resources.ResourceLocation> MISSING_STYLE_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final Map<net.minecraft.resources.ResourceLocation, Long> STYLE_ERROR_LOG_TICKS = new ConcurrentHashMap<>();

    private TowerVfxService() {
    }

    public static void initialize(
            VfxConfig initialConfig,
            SemionGameManager manager,
            AreaVfxStyleRegistryImpl styles
    ) {
        config = safeConfig(initialConfig);
        gameManager = manager;
        areaVfxStyles = Objects.requireNonNull(styles, "styles");
        ensureExecutor();
    }

    public static void configure(VfxConfig newConfig) {
        config = safeConfig(newConfig);
        ensureExecutor();
    }

    public static void showAttack(
            SemionTowerEntity tower,
            SemionMonsterEntity target,
            boolean killedPrimaryTarget,
            boolean healedTower
    ) {
        if (!config.enabled() || tower == null || target == null) {
            return;
        }
        EventContext context = context(tower, targetCenter(target));
        if (context == null) {
            return;
        }
        Vec3 source = towerCenter(tower);
        Vec3 impact = targetCenter(target);
        AttackVisualKind kind = visualKind(tower.attackRange());
        enqueue(new AttackEvent(context, source, impact, kind, false));
        if (!killedPrimaryTarget
                && target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0.0) {
            enqueue(new MarkEvent(context, impact, Math.max(0.4, target.getBbWidth() * 0.75), Math.max(0.5, target.getBbHeight() * 0.45)));
        }
        if (healedTower) {
            enqueue(new LifeStealEvent(context, impact, source));
        }
        if (killedPrimaryTarget) {
            enqueue(new KillEvent(context, impact));
        }
    }

    public static void showSecondaryAttack(SemionTowerEntity tower, SemionMonsterEntity target) {
        if (!config.enabled() || tower == null || target == null) {
            return;
        }
        EventContext context = context(tower, targetCenter(target));
        if (context != null) {
            enqueue(new AttackEvent(context, towerCenter(tower), targetCenter(target), visualKind(tower.attackRange()), true));
        }
    }

    public static void showNetherTransition(SemionTowerEntity tower) {
        if (!config.enabled() || tower == null) {
            return;
        }
        EventContext context = context(tower, towerCenter(tower));
        if (context != null) {
            enqueue(new TransitionEvent(context, towerCenter(tower)));
        }
    }

    public static void endServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        List<PendingEvent> batch = drainEvents(gameTime);
        if (batch.isEmpty()) {
            pruneLaneState(gameTime);
            return;
        }
        VfxConfig batchConfig = config;
        if (!batchConfig.enabled()) {
            batch.forEach(event -> stats(event.context().lane()).dropped.increment());
            return;
        }
        Runnable work = () -> processBatch(batch, gameTime, batchConfig);
        if (batchConfig.asyncPlanning()) {
            ensureExecutor().execute(work);
        } else {
            work.run();
        }
    }

    public static void shutdown() {
        EVENTS.clear();
        synchronized (QUEUED_BY_LANE) {
            QUEUED_BY_LANE.clear();
        }
        VANILLA_BUDGETS.clear();
        gameManager = null;
        synchronized (EXECUTOR_LOCK) {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    executor.shutdownNow();
                }
                executor = null;
            }
        }
    }

    public static String statsSummary() {
        if (STATS.isEmpty()) {
            return "VFX 통계가 없습니다.";
        }
        long queued = STATS.values().stream().mapToLong(stats -> stats.queued.sum()).sum();
        long planned = STATS.values().stream().mapToLong(stats -> stats.planned.sum()).sum();
        long merged = STATS.values().stream().mapToLong(stats -> stats.merged.sum()).sum();
        long dropped = STATS.values().stream().mapToLong(stats -> stats.dropped.sum()).sum();
        long vanillaPoints = STATS.values().stream().mapToLong(stats -> stats.vanillaSentPoints.sum()).sum();
        long vanillaPackets = STATS.values().stream().mapToLong(stats -> stats.vanillaPackets.sum()).sum();
        long gcbShapes = STATS.values().stream().mapToLong(stats -> stats.gcbShapes.sum()).sum();
        long gcbBytes = STATS.values().stream().mapToLong(stats -> stats.gcbBytes.sum()).sum();
        return "VFX lanes=" + STATS.size()
                + " queued=" + queued
                + " planned=" + planned
                + " merged=" + merged
                + " dropped=" + dropped
                + " vanillaPoints=" + vanillaPoints
                + " vanillaPackets=" + vanillaPackets
                + " gcbShapes=" + gcbShapes
                + " gcbBytes=" + gcbBytes;
    }

    public static void resetStats() {
        STATS.clear();
    }

    static AttackVisualKind visualKind(double attackRange) {
        return attackRange > RANGED_ATTACK_RANGE_THRESHOLD ? AttackVisualKind.RANGED : AttackVisualKind.MELEE;
    }

    public static BuilderPalette paletteFor(TowerType type) {
        if (VillagerTowers.isAdvVillagerTower(type)) {
            return BuilderPalette.VILLAGER_ADV;
        }
        if (VillagerTowers.isBaseVillagerTower(type)) {
            return BuilderPalette.VILLAGER;
        }
        if (type != null && UNDEAD_TOWER_IDS.contains(type.id())) {
            return BuilderPalette.UNDEAD;
        }
        if (type != null && ANIMAL_TOWER_IDS.contains(type.id())) {
            return BuilderPalette.ANIMAL;
        }
        if (WarlockTowers.isWarlockTower(type)) {
            return BuilderPalette.WARLOCK;
        }
        if (LegionTowers.isLegionTower(type)) {
            return BuilderPalette.LEGION;
        }
        if (ResonanceTowers.isResonanceTower(type)) {
            return BuilderPalette.RESONANCE;
        }
        if (IllagerTowers.isIllagerTower(type)) {
            return BuilderPalette.ILLAGER;
        }
        return BuilderPalette.DEFAULT;
    }

    static int preferredRayPointCount(double distance) {
        int preferred = (int) Math.ceil(Math.max(0.0, distance) * RAY_POINTS_PER_BLOCK);
        return Math.max(MIN_RAY_POINTS, Math.min(MAX_RAY_POINTS, preferred));
    }

    static int adaptiveRayPointCount(double distance, int remainingBudget) {
        int preferred = preferredRayPointCount(distance);
        return remainingBudget >= preferred
                ? preferred
                : Math.max(MIN_RAY_POINTS, Math.min(preferred, Math.max(0, remainingBudget)));
    }

    static List<Vec3> collectLinePoints(Vec3 start, Vec3 end, int points) {
        CollectingApelRenderer collector = new CollectingApelRenderer();
        collector.drawLine(
                ParticleTypes.END_ROD,
                0,
                vector(start),
                new Vector3f(),
                vector(end.subtract(start)),
                new Vector3f(),
                points
        );
        return collector.points.stream().map(ParticlePoint::position).toList();
    }

    static boolean shouldIncludeSpectator(boolean matchSpectator, boolean sameLevel, double distanceSqr) {
        return matchSpectator && sameLevel && distanceSqr <= SPECTATOR_RADIUS_SQR;
    }

    public static Vec3 targetCenter(SemionMonsterEntity target) {
        return target == null
                ? Vec3.ZERO
                : new Vec3(target.getX(), target.getY() + Math.max(0.2, target.getBbHeight() * 0.55), target.getZ());
    }

    public static void showAreaEffect(
            SemionTowerEntity tower,
            net.minecraft.resources.ResourceLocation effectId,
            net.minecraft.resources.ResourceLocation styleId,
            Vec3 center,
            double radius,
            List<Vec3> appliedPositions,
            int candidateCount,
            int appliedCount,
            int killedCount
    ) {
        if (!config.enabled() || !config.areaDamageEnabled() || tower == null || effectId == null || styleId == null
                || center == null || radius <= 0.0 || tower.runtimeTower() == null) {
            return;
        }
        Vec3 visualCenter = center.add(0.0, 0.08, 0.0);
        EventContext context = context(tower, visualCenter);
        if (context == null) {
            return;
        }
        List<Vec3> samples = appliedPositions == null
                ? List.of()
                : appliedPositions.stream().limit(config.maxSampledHitRays()).toList();
        String rawTowerTypeId = tower.runtimeTower().type().id();
        net.minecraft.resources.ResourceLocation towerTypeId = net.minecraft.resources.ResourceLocation.tryParse(rawTowerTypeId);
        if (towerTypeId == null || rawTowerTypeId.indexOf(':') < 0) {
            towerTypeId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, rawTowerTypeId);
        }
        AreaVfxContext visual = new AreaVfxContext(
                effectId,
                styleId,
                tower.getUUID(),
                towerTypeId,
                context.palette().areaPalette(),
                towerCenter(tower),
                visualCenter,
                radius,
                samples,
                candidateCount,
                appliedCount,
                killedCount,
                context.gameTime()
        );
        AreaEffectVfxEvent event = new AreaEffectVfxEvent(context.lane(), visual);
        Consumer<AreaEffectVfxEvent> observer = areaEffectTestObserver;
        if (observer != null) {
            observer.accept(event);
        }
        enqueue(new AreaEvent(context, event));
    }

    static void setAreaEffectTestObserver(Consumer<AreaEffectVfxEvent> observer) {
        areaEffectTestObserver = observer;
    }

    private static Vec3 towerCenter(SemionTowerEntity tower) {
        return tower == null
                ? Vec3.ZERO
                : new Vec3(tower.getX(), tower.getY() + Math.max(0.35, tower.getBbHeight() * 0.65), tower.getZ());
    }

    private static EventContext context(SemionTowerEntity tower, Vec3 audienceCenter) {
        if (!(tower.level() instanceof ServerLevel level)
                || tower.teamId() == null
                || tower.ownerPlayer() == null
                || tower.runtimeTower() == null) {
            return null;
        }
        VfxLaneKey lane = new VfxLaneKey(level.dimension(), tower.teamId(), tower.laneId());
        List<Recipient> recipients = recipients(level, tower.ownerPlayer(), audienceCenter);
        return new EventContext(
                lane,
                tower.getUUID(),
                paletteFor(tower.runtimeTower().type()),
                level.getGameTime(),
                recipients
        );
    }

    private static List<Recipient> recipients(ServerLevel level, UUID ownerId, Vec3 center) {
        Map<UUID, Recipient> recipients = new HashMap<>();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() == level) {
            recipients.put(ownerId, Recipient.snapshot(owner));
        }

        SemionGameManager manager = gameManager;
        SemionGame game = manager == null ? null : manager.playableGame(ownerId).orElse(null);
        SemionGame activeGame = manager == null ? null : manager.activeGame().orElse(null);
        if (game == null || game != activeGame) {
            return List.copyOf(recipients.values());
        }
        for (UUID spectatorId : game.matchSpectatorIds()) {
            ServerPlayer spectator = level.getServer().getPlayerList().getPlayer(spectatorId);
            if (spectator != null && shouldIncludeSpectator(
                    true,
                    spectator.level() == level,
                    spectator.distanceToSqr(center.x, center.y, center.z)
            )) {
                recipients.putIfAbsent(spectatorId, Recipient.snapshot(spectator));
            }
        }
        return List.copyOf(recipients.values());
    }

    private static void enqueue(PendingEvent event) {
        VfxLaneKey lane = event.context().lane();
        int depth;
        synchronized (QUEUED_BY_LANE) {
            depth = QUEUED_BY_LANE.getOrDefault(lane, 0);
            if (depth >= MAX_QUEUE_DEPTH_PER_LANE) {
                stats(lane).dropped.increment();
                return;
            }
            QUEUED_BY_LANE.put(lane, depth + 1);
        }
        event.assignSequence(SEQUENCE.incrementAndGet());
        EVENTS.add(event);
        LaneStats stats = stats(lane);
        stats.queued.increment();
        stats.maxQueueDepth.accumulateAndGet(depth + 1, Math::max);
        stats.lastTouchedTick.set(event.context().gameTime());
    }

    private static List<PendingEvent> drainEvents(long gameTime) {
        List<PendingEvent> batch = new ArrayList<>();
        PendingEvent event;
        while ((event = EVENTS.poll()) != null) {
            synchronized (QUEUED_BY_LANE) {
                QUEUED_BY_LANE.computeIfPresent(event.context().lane(), (lane, count) -> count <= 1 ? null : count - 1);
            }
            if (gameTime - event.context().gameTime() > MAX_EVENT_AGE_TICKS) {
                stats(event.context().lane()).dropped.increment();
                continue;
            }
            batch.add(event);
        }
        batch.sort(Comparator
                .comparingLong((PendingEvent pending) -> pending.context().gameTime())
                .thenComparingInt(pending -> pending.phase().order)
                .thenComparingLong(PendingEvent::sequence));
        return batch;
    }

    private static void processBatch(List<PendingEvent> batch, long gameTime, VfxConfig batchConfig) {
        long started = System.nanoTime();
        Set<ExplosionKey> explosions = new HashSet<>();
        for (PendingEvent event : batch) {
            if (event instanceof AreaEvent area && area.event.visual().styleId().equals(AreaVfxStyles.CORPSE_EXPLOSION)) {
                explosions.add(ExplosionKey.from(area.event));
            }
        }

        Map<UUID, Integer> vanillaPacketsByRecipient = new HashMap<>();
        Map<VfxLaneKey, Integer> gcbShapesByLane = new HashMap<>();
        for (PendingEvent event : batch) {
            LaneStats stats = stats(event.context().lane());
            if (event instanceof KillEvent kill && explosions.contains(ExplosionKey.from(kill))) {
                stats.merged.increment();
                continue;
            }
            stats.planned.increment();
            if (event instanceof AttackEvent attack) {
                renderAttack(attack, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            } else if (event instanceof AreaEvent area) {
                renderArea(area, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            } else if (event instanceof MarkEvent mark) {
                renderMark(mark, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            } else if (event instanceof LifeStealEvent lifeSteal) {
                renderLifeSteal(lifeSteal, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            } else if (event instanceof KillEvent kill) {
                renderKill(kill, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            } else if (event instanceof TransitionEvent transition) {
                renderTransition(transition, gameTime, batchConfig, vanillaPacketsByRecipient, gcbShapesByLane);
            }
        }
        long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started);
        Set<VfxLaneKey> lanes = new HashSet<>();
        for (PendingEvent event : batch) {
            lanes.add(event.context().lane());
        }
        for (VfxLaneKey lane : lanes) {
            stats(lane).planningMicros.add(elapsedMicros);
        }
        pruneLaneState(gameTime);
    }

    private static void renderAttack(
            AttackEvent event,
            long gameTime,
            VfxConfig config,
            Map<UUID, Integer> packetCounts,
            Map<VfxLaneKey, Integer> shapeCounts
    ) {
        int preferred = preferredRayPointCount(event.source.distanceTo(event.impact));
        int points = claimVanillaPoints(event.context().lane(), gameTime, config, preferred, MIN_RAY_POINTS, true);
        sendLine(event.context(), event.context().palette().rayParticle(), event.context().palette().gcbRayParticle(), event.source, event.impact,
                points, true, config, packetCounts, shapeCounts);
        int accents = claimVanillaPoints(event.context().lane(), gameTime, config, Math.max(5, points * 2 / 3), 0, false);
        if (accents > 0) {
            sendLine(event.context(), event.context().palette().accentParticle(), event.context().palette().gcbAccentParticle(), event.source, event.impact,
                    accents, false, config, packetCounts, shapeCounts);
        }
        int impactPoints = claimVanillaPoints(event.context().lane(), gameTime, config, event.secondary ? 12 : 18, 0, false);
        if (impactPoints > 0) {
            sendSphere(event.context(), event.context().palette().accentParticle(), event.context().palette().gcbAccentParticle(),
                    event.impact, event.secondary ? 0.28 : 0.42, impactPoints, false, config, packetCounts, shapeCounts);
            sendParticle(event.context(), event.kind.particle, event.kind.gcbParticle, event.impact, false, config, packetCounts, shapeCounts);
        }
    }

    private static void renderArea(
            AreaEvent wrapper,
            long gameTime,
            VfxConfig config,
            Map<UUID, Integer> packetCounts,
            Map<VfxLaneKey, Integer> shapeCounts
    ) {
        AreaEffectVfxEvent event = wrapper.event;
        AreaVfxStyleRegistryImpl registry = areaVfxStyles;
        var planner = registry == null ? null : registry.find(event.visual().styleId()).orElse(null);
        if (planner == null) {
            if (MISSING_STYLE_WARNINGS.add(event.visual().styleId())) {
                SemionTd.LOGGER.warn("Unknown Semion TD area VFX style: {}", event.visual().styleId());
            }
            stats(event.lane()).dropped.increment();
            return;
        }
        AreaVfxOutput output = new PlannerOutput(wrapper.context(), event.lane(), gameTime, config, packetCounts, shapeCounts);
        try {
            planner.plan(event.visual(), output);
        } catch (RuntimeException exception) {
            stats(event.lane()).dropped.increment();
            long previous = STYLE_ERROR_LOG_TICKS.getOrDefault(event.visual().styleId(), Long.MIN_VALUE);
            if (gameTime - previous >= LANE_STATE_TTL_TICKS) {
                STYLE_ERROR_LOG_TICKS.put(event.visual().styleId(), gameTime);
                SemionTd.LOGGER.warn("Area VFX planner {} failed; the visual was dropped.", event.visual().styleId(), exception);
            }
        }
    }

    private static void renderMark(MarkEvent event, long gameTime, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts) {
        int points = claimVanillaPoints(event.context().lane(), gameTime, config, 24, 0, false);
        if (points > 0) {
            sendCircle(event.context(), MARK_PARTICLE, "minecraft:witch", event.center, event.radius, points / 2,
                    false, config, packetCounts, shapeCounts);
            sendSphere(event.context(), MARK_PARTICLE, "minecraft:witch", event.center, event.stretch, points / 2,
                    false, config, packetCounts, shapeCounts);
        }
    }

    private static void renderLifeSteal(LifeStealEvent event, long gameTime, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts) {
        int points = claimVanillaPoints(event.context().lane(), gameTime, config, 10, 0, false);
        if (points > 0) {
            sendBezier(event.context(), LIFE_STEAL_PARTICLE, "minecraft:damage_indicator", event.impact, event.source, points,
                    false, config, packetCounts, shapeCounts);
        }
    }

    private static void renderKill(KillEvent event, long gameTime, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts) {
        int points = claimVanillaPoints(event.context().lane(), gameTime, config, 18, 0, false);
        if (points > 0) {
            sendSphere(event.context(), event.context().palette().accentParticle(), event.context().palette().gcbAccentParticle(), event.center, 0.62,
                    points, false, config, packetCounts, shapeCounts);
        }
    }

    private static void renderTransition(TransitionEvent event, long gameTime, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts) {
        int points = claimVanillaPoints(event.context().lane(), gameTime, config, 44, 10, true);
        sendSphere(event.context(), NETHER_TRANSITION_PARTICLE, "minecraft:flame", event.center, 0.9, points / 2,
                true, config, packetCounts, shapeCounts);
        sendCircle(event.context(), ZOMBIE_TRANSITION_PARTICLE, "minecraft:smoke", event.center.add(0.0, -0.65, 0.0), 0.7,
                Math.max(8, points / 2), true, config, packetCounts, shapeCounts);
        sendParticle(event.context(), ParticleTypes.LARGE_SMOKE, "minecraft:large_smoke", event.center, false, config, packetCounts, shapeCounts);
    }

    private static void sendLine(
            EventContext context, ParticleOptions particle, String gcbParticle, Vec3 start, Vec3 end, int points,
            boolean essential, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts
    ) {
        if (points <= 0) {
            return;
        }
        sendVanilla(context, collector -> collector.drawLine(
                particle, 0, vector(start), new Vector3f(), vector(end.subtract(start)), new Vector3f(), points
        ), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Line(
                Math.max(0.01, start.distanceTo(end) / Math.max(1, points)),
                GCBParticleS2CPacket.ShapeOptions.DEFAULT,
                gcbVec(start),
                gcbVec(end)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendCircle(
            EventContext context, ParticleOptions particle, String gcbParticle, Vec3 center, double radius, int points,
            boolean essential, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts
    ) {
        if (points <= 0 || radius <= 0.0) {
            return;
        }
        sendVanilla(context, collector -> collector.drawEllipse(
                particle, 0, vector(center), (float) radius, (float) radius,
                new Vector3f(HORIZONTAL_ROTATION_X, 0.0F, 0.0F), points
        ), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Circle(
                GCBParticleS2CPacket.Vec.UNIT_Y,
                GCBParticleS2CPacket.Vec.ZERO,
                points,
                360.0,
                shapeOptions(radius, 0.12),
                gcbVec(center)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendSphere(
            EventContext context, ParticleOptions particle, String gcbParticle, Vec3 center, double radius, int points,
            boolean essential, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts
    ) {
        if (points <= 0 || radius <= 0.0) {
            return;
        }
        sendVanilla(context, collector -> collector.drawEllipsoid(
                particle, 0, vector(center), (float) radius, (float) radius, (float) radius, new Vector3f(), points
        ), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Sphere(
                GCBParticleS2CPacket.Vec.UNIT_Y, points, shapeOptions(radius, 0.12), gcbVec(center)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendBezier(
            EventContext context, ParticleOptions particle, String gcbParticle, Vec3 start, Vec3 end, int points,
            boolean essential, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts
    ) {
        Vec3 control = start.lerp(end, 0.5).add(0.0, 0.9, 0.0);
        sendVanilla(context, collector -> collector.drawBezier(
                particle, 0, new Vector3f(),
                new QuadraticBezierCurve(vector(start), vector(end), vector(control)), new Vector3f(), points
        ), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Trail(
                0.0, GCBParticleS2CPacket.ShapeOptions.DEFAULT, gcbVec(start), gcbVec(control), gcbVec(end)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendTrail(
            EventContext context,
            ParticleOptions particle,
            String gcbParticle,
            Vec3 start,
            Vec3 control,
            Vec3 end,
            int points,
            boolean essential,
            VfxConfig config,
            Map<UUID, Integer> packetCounts,
            Map<VfxLaneKey, Integer> shapeCounts
    ) {
        sendVanilla(context, collector -> collector.drawBezier(
                particle, 0, new Vector3f(),
                new QuadraticBezierCurve(vector(start), vector(end), vector(control)), new Vector3f(), points
        ), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Trail(
                0.0, GCBParticleS2CPacket.ShapeOptions.DEFAULT, gcbVec(start), gcbVec(control), gcbVec(end)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendParticle(
            EventContext context, ParticleOptions particle, String gcbParticle, Vec3 position,
            boolean essential, VfxConfig config, Map<UUID, Integer> packetCounts, Map<VfxLaneKey, Integer> shapeCounts
    ) {
        sendVanilla(context, collector -> collector.drawParticle(particle, 0, vector(position)), config, packetCounts);
        GCBParticleS2CPacket.ShapeData shape = new GCBParticleS2CPacket.Sphere(
                GCBParticleS2CPacket.Vec.UNIT_Y, 1, shapeOptions(0.05, 0.0), gcbVec(position)
        );
        sendGcb(context, gcbParticle, shape, essential, config, shapeCounts);
    }

    private static void sendVanilla(
            EventContext context,
            Consumer<CollectingApelRenderer> draw,
            VfxConfig config,
            Map<UUID, Integer> packetCounts
    ) {
        List<Recipient> recipients = context.recipients().stream().filter(recipient -> !recipient.gcb()).toList();
        if (recipients.isEmpty()) {
            return;
        }
        CollectingApelRenderer collector = new CollectingApelRenderer();
        draw.accept(collector);
        LaneStats stats = stats(context.lane());
        stats.vanillaRequestedPoints.add(collector.points.size());
        for (Recipient recipient : recipients) {
            int sent = packetCounts.getOrDefault(recipient.id(), 0);
            int remaining = config.vanilla().maxPacketsPerTickPerRecipient() - sent;
            if (remaining <= 0) {
                stats.dropped.add(collector.points.size());
                continue;
            }
            int limit = Math.min(remaining, collector.points.size());
            for (int index = 0; index < limit; index++) {
                ParticlePoint point = collector.points.get(index);
                recipient.send(new ClientboundLevelParticlesPacket(
                        point.particle(), false, false,
                        point.position().x, point.position().y, point.position().z,
                        0.0F, 0.0F, 0.0F, 0.0F, 1
                ));
            }
            packetCounts.put(recipient.id(), sent + limit);
            stats.vanillaPackets.add(limit);
            stats.vanillaRecipientDeliveries.add(limit);
            if (limit < collector.points.size()) {
                stats.dropped.add(collector.points.size() - limit);
            }
        }
        stats.vanillaSentPoints.add(collector.points.size());
    }

    private static void sendGcb(
            EventContext context,
            String particle,
            GCBParticleS2CPacket.ShapeData shape,
            boolean essential,
            VfxConfig config,
            Map<VfxLaneKey, Integer> shapeCounts
    ) {
        List<Recipient> recipients = context.recipients().stream().filter(Recipient::gcb).toList();
        if (recipients.isEmpty()) {
            return;
        }
        int used = shapeCounts.getOrDefault(context.lane(), 0);
        if (used >= config.gcb().maxShapeInstructionsPerTick() && !essential) {
            stats(context.lane()).dropped.increment();
            return;
        }
        GCBParticleS2CPacket payload = new GCBParticleS2CPacket(
                particle, GCBParticleS2CPacket.Vec.ZERO, 1, 0.0, false, "", shape
        );
        Packet<ClientCommonPacketListener> packet = ServerPlayNetworking.createS2CPacket(payload);
        for (Recipient recipient : recipients) {
            recipient.send(packet);
        }
        shapeCounts.put(context.lane(), used + 1);
        LaneStats stats = stats(context.lane());
        stats.gcbShapes.increment();
        stats.gcbBytes.add(payload.encode().length());
    }

    private static int claimVanillaPoints(
            VfxLaneKey lane,
            long gameTime,
            VfxConfig config,
            int preferred,
            int minimum,
            boolean essential
    ) {
        TowerVfxBudget bucket = VANILLA_BUDGETS.computeIfAbsent(
                lane,
                ignored -> new TowerVfxBudget(config.vanilla().burstCapacityPoints(), gameTime)
        );
        int claimed = bucket.claim(
                preferred,
                minimum,
                essential,
                gameTime,
                config.vanilla().refillPointsPerTick(),
                config.vanilla().burstCapacityPoints()
        );
        if (claimed < preferred) {
            stats(lane).dropped.add(preferred - claimed);
        }
        return claimed;
    }

    private static GCBParticleS2CPacket.ShapeOptions shapeOptions(double expand, double time) {
        return new GCBParticleS2CPacket.ShapeOptions(
                expand, time,
                GCBParticleS2CPacket.Vec.UNIT_X,
                GCBParticleS2CPacket.Vec.UNIT_Y,
                GCBParticleS2CPacket.Vec.UNIT_Z,
                null
        );
    }

    private static GCBParticleS2CPacket.Vec gcbVec(Vec3 position) {
        return new GCBParticleS2CPacket.Vec(position.x, position.y, position.z);
    }

    private static Vector3f vector(Vec3 position) {
        return new Vector3f((float) position.x, (float) position.y, (float) position.z);
    }

    private static LaneStats stats(VfxLaneKey lane) {
        return STATS.computeIfAbsent(lane, ignored -> new LaneStats());
    }

    private static void pruneLaneState(long gameTime) {
        Set<VfxLaneKey> expired = new HashSet<>();
        STATS.forEach((lane, stats) -> {
            if (gameTime - stats.lastTouchedTick.get() > LANE_STATE_TTL_TICKS) {
                expired.add(lane);
            }
        });
        for (VfxLaneKey lane : expired) {
            STATS.remove(lane);
            VANILLA_BUDGETS.remove(lane);
        }
    }

    private static VfxConfig safeConfig(VfxConfig config) {
        return (config == null ? VfxConfig.defaultConfig() : config).normalized();
    }

    private static ExecutorService ensureExecutor() {
        synchronized (EXECUTOR_LOCK) {
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "semion-td-vfx");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((ignored, exception) ->
                            SemionTd.LOGGER.error("Uncaught exception in VFX planner.", exception));
                    return thread;
                });
            }
            return executor;
        }
    }

    enum AttackVisualKind {
        MELEE(ParticleTypes.SWEEP_ATTACK, "minecraft:sweep_attack"),
        RANGED(ParticleTypes.CRIT, "minecraft:crit");

        private final ParticleOptions particle;
        private final String gcbParticle;

        AttackVisualKind(ParticleOptions particle, String gcbParticle) {
            this.particle = particle;
            this.gcbParticle = gcbParticle;
        }
    }

    private enum Phase {
        PRIMARY_ATTACK(0),
        SECONDARY_ATTACK(1),
        AREA_DAMAGE(2),
        MARK_AND_LIFESTEAL(3),
        KILL_EFFECT(4);

        private final int order;

        Phase(int order) {
            this.order = order;
        }
    }

    private record EventContext(
            VfxLaneKey lane,
            UUID sourceTowerId,
            BuilderPalette palette,
            long gameTime,
            List<Recipient> recipients
    ) {
    }

    private abstract static class PendingEvent {
        private final EventContext context;
        private final Phase phase;
        private long sequence;

        private PendingEvent(EventContext context, Phase phase) {
            this.context = context;
            this.phase = phase;
        }

        EventContext context() {
            return context;
        }

        Phase phase() {
            return phase;
        }

        long sequence() {
            return sequence;
        }

        void assignSequence(long sequence) {
            this.sequence = sequence;
        }
    }

    private static final class AttackEvent extends PendingEvent {
        private final Vec3 source;
        private final Vec3 impact;
        private final AttackVisualKind kind;
        private final boolean secondary;

        private AttackEvent(EventContext context, Vec3 source, Vec3 impact, AttackVisualKind kind, boolean secondary) {
            super(context, secondary ? Phase.SECONDARY_ATTACK : Phase.PRIMARY_ATTACK);
            this.source = source;
            this.impact = impact;
            this.kind = kind;
            this.secondary = secondary;
        }
    }

    private static final class AreaEvent extends PendingEvent {
        private final AreaEffectVfxEvent event;

        private AreaEvent(EventContext context, AreaEffectVfxEvent event) {
            super(context, Phase.AREA_DAMAGE);
            this.event = event;
        }
    }

    private static final class MarkEvent extends PendingEvent {
        private final Vec3 center;
        private final double radius;
        private final double stretch;

        private MarkEvent(EventContext context, Vec3 center, double radius, double stretch) {
            super(context, Phase.MARK_AND_LIFESTEAL);
            this.center = center;
            this.radius = radius;
            this.stretch = stretch;
        }
    }

    private static final class LifeStealEvent extends PendingEvent {
        private final Vec3 impact;
        private final Vec3 source;

        private LifeStealEvent(EventContext context, Vec3 impact, Vec3 source) {
            super(context, Phase.MARK_AND_LIFESTEAL);
            this.impact = impact;
            this.source = source;
        }
    }

    private static final class KillEvent extends PendingEvent {
        private final Vec3 center;

        private KillEvent(EventContext context, Vec3 center) {
            super(context, Phase.KILL_EFFECT);
            this.center = center;
        }
    }

    private static final class TransitionEvent extends PendingEvent {
        private final Vec3 center;

        private TransitionEvent(EventContext context, Vec3 center) {
            super(context, Phase.AREA_DAMAGE);
            this.center = center;
        }
    }

    private record Recipient(UUID id, ServerGamePacketListenerImpl connection, boolean gcb) {
        static Recipient snapshot(ServerPlayer player) {
            return new Recipient(player.getUUID(), player.connection, player instanceof GCBPlayer gcbPlayer && gcbPlayer.gcb$hasMod());
        }

        void send(Packet<?> packet) {
            connection.send(packet);
        }
    }

    private record ParticlePoint(ParticleOptions particle, Vec3 position) {
    }

    private static final class PlannerOutput implements AreaVfxOutput {
        private static final int MAX_REQUESTED_POINTS = 8192;

        private final EventContext context;
        private final VfxLaneKey lane;
        private final long gameTime;
        private final VfxConfig config;
        private final Map<UUID, Integer> packetCounts;
        private final Map<VfxLaneKey, Integer> shapeCounts;

        private PlannerOutput(
                EventContext context,
                VfxLaneKey lane,
                long gameTime,
                VfxConfig config,
                Map<UUID, Integer> packetCounts,
                Map<VfxLaneKey, Integer> shapeCounts
        ) {
            this.context = context;
            this.lane = lane;
            this.gameTime = gameTime;
            this.config = config;
            this.packetCounts = packetCounts;
            this.shapeCounts = shapeCounts;
        }

        @Override
        public void line(AreaVfxParticle particle, Vec3 start, Vec3 end, int points, boolean essential) {
            int claimed = claim(particle, points, essential, start, end);
            if (claimed > 0) {
                sendLine(context, particle.vanilla(), particle.gcbParticleId().toString(), start, end, claimed,
                        essential, config, packetCounts, shapeCounts);
            }
        }

        @Override
        public void circle(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            int claimed = claimRadius(particle, center, radius, points, essential);
            if (claimed > 0) {
                sendCircle(context, particle.vanilla(), particle.gcbParticleId().toString(), center, radius, claimed,
                        essential, config, packetCounts, shapeCounts);
            }
        }

        @Override
        public void sphere(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            int claimed = claimRadius(particle, center, radius, points, essential);
            if (claimed > 0) {
                sendSphere(context, particle.vanilla(), particle.gcbParticleId().toString(), center, radius, claimed,
                        essential, config, packetCounts, shapeCounts);
            }
        }

        @Override
        public void trail(
                AreaVfxParticle particle,
                Vec3 start,
                Vec3 control,
                Vec3 end,
                int points,
                boolean essential
        ) {
            int claimed = claim(particle, points, essential, start, control, end);
            if (claimed > 0) {
                sendTrail(context, particle.vanilla(), particle.gcbParticleId().toString(), start, control, end, claimed,
                        essential, config, packetCounts, shapeCounts);
            }
        }

        private int claimRadius(AreaVfxParticle particle, Vec3 center, double radius, int points, boolean essential) {
            if (!Double.isFinite(radius) || radius <= 0.0) {
                stats(lane).dropped.add(Math.max(1, points));
                return 0;
            }
            return claim(particle, points, essential, center);
        }

        private int claim(AreaVfxParticle particle, int points, boolean essential, Vec3... positions) {
            if (particle == null || points <= 0 || positions == null) {
                stats(lane).dropped.add(Math.max(1, points));
                return 0;
            }
            for (Vec3 position : positions) {
                if (position == null || !finite(position)) {
                    stats(lane).dropped.add(Math.max(1, points));
                    return 0;
                }
            }
            int preferred = Math.min(MAX_REQUESTED_POINTS, points);
            int minimum = essential ? Math.min(MIN_RAY_POINTS, preferred) : 0;
            return claimVanillaPoints(lane, gameTime, config, preferred, minimum, essential);
        }

        private static boolean finite(Vec3 position) {
            return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
        }
    }

    private static final class CollectingApelRenderer extends BaseApelRenderer {
        private final List<ParticlePoint> points = new ArrayList<>();

        @Override
        public void drawParticle(ParticleOptions particle, int step, Vector3f position) {
            points.add(new ParticlePoint(particle, new Vec3(position.x, position.y, position.z)));
        }
    }

    private static final class LaneStats {
        private final LongAdder queued = new LongAdder();
        private final LongAdder planned = new LongAdder();
        private final LongAdder merged = new LongAdder();
        private final LongAdder dropped = new LongAdder();
        private final LongAdder vanillaRequestedPoints = new LongAdder();
        private final LongAdder vanillaSentPoints = new LongAdder();
        private final LongAdder vanillaPackets = new LongAdder();
        private final LongAdder vanillaRecipientDeliveries = new LongAdder();
        private final LongAdder gcbShapes = new LongAdder();
        private final LongAdder gcbBytes = new LongAdder();
        private final LongAdder planningMicros = new LongAdder();
        private final AtomicLong maxQueueDepth = new AtomicLong();
        private final AtomicLong lastTouchedTick = new AtomicLong();
    }

    private record ExplosionKey(VfxLaneKey lane, UUID towerId, long x, long z) {
        static ExplosionKey from(AreaEffectVfxEvent event) {
            return from(event.lane(), event.visual().sourceTowerId(), event.visual().center());
        }

        static ExplosionKey from(KillEvent event) {
            return from(event.context().lane(), event.context().sourceTowerId(), event.center);
        }

        private static ExplosionKey from(VfxLaneKey lane, UUID towerId, Vec3 center) {
            return new ExplosionKey(
                    lane,
                    towerId,
                    Math.round(center.x * 4.0),
                    Math.round(center.z * 4.0)
            );
        }
    }
}
