package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerRoundMetricsTracker;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Drives everything the demon lord player does: the boss bar, the combat lock, the hotbar and the
 * melee swing.
 *
 * <p>Health never touches the vanilla player. Incoming damage is intercepted and routed into
 * {@link DemonLordState}, so the player cannot actually die, respawn or drop anything - emptying the
 * pool simply flips them to 전투 제외 until the next round.
 */
public final class DemonLordService {
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new ConcurrentHashMap<>();

    /** 전투 진입 직전의 핫바. 전투가 끝나면 우리가 치운 자리만 이걸로 되돌립니다. */
    private static final Map<UUID, List<ItemStack>> PRE_COMBAT_HOTBAR = new ConcurrentHashMap<>();

    private static final int HOTBAR_SLOTS = 9;

    /** Falling this far below the lane floor means something threw the player out of the map. */
    private static final double FALL_RESCUE_DEPTH = 4.0;

    /** 이동기 착지 지점을 되짚어 볼 때의 간격. 한 블록보다 촘촘해야 좁은 틈을 놓치지 않습니다. */
    private static final double LANDING_STEP = 0.5;

    /** 착지 지점에서 발밑 땅을 찾아 내려가는 깊이. 이보다 깊으면 허공으로 봅니다. */
    private static final double GROUND_PROBE_DEPTH = 6.0;

    /** 몸 검사 시 줄여 두는 여유. 벽에 스치듯 붙는 착지까지 막지는 않습니다. */
    private static final double BODY_MARGIN = 0.05;

    /** How often a knocked-out demon lord shakes off lingering monster targets. */
    private static final int AGGRO_RELEASE_INTERVAL = 5;

    /** 스스로 전투에서 물러나는 자리. 스킬 슬롯과 마검 사이의 마지막 빈칸입니다. */
    private static final int RETREAT_SLOT = 7;

    /** 준비 단계에만 놓이는 스탯 분배 도구 자리. 기존 매치 도구(0~2) 바로 뒤입니다. */
    private static final int STAT_TOOL_SLOT = 3;

    private static final Component STAT_TOOL_NAME =
            Component.literal("스탯 포인트 분배").withStyle(ChatFormatting.LIGHT_PURPLE);

    private static final ResourceLocation MOVE_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "demon_lord_move_speed");

    private static final Component RETREAT_NAME =
            Component.literal("전투 이탈").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

    private static final Component BLADE_NAME =
            Component.literal("마검").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);

    private DemonLordService() {
    }

    /**
     * Hooks damage and melee. Must run after {@code SemionPlayerProtectionService}, which already
     * stops protecting a demon lord while they are in combat.
     */
    public static void register(SemionGameManager gameManager) {
        // 다섯 번째 스킬은 마검을 우클릭해 씁니다.
        //
        // 손은 시전이 끝날 때마다 마검으로 돌아오므로, 마검에 걸어 두면 슬롯을 옮기는 동작 없이
        // 바로 조준해 쏠 수 있습니다. 스킬 슬롯을 들고 우클릭하게 하면 매번 슬롯을 바꿨다가
        // 되돌아오는 왕복이 생겨 불편합니다.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            // 준비 단계의 스탯 분배 도구.
            if (isStatTool(serverPlayer.getMainHandItem())
                    && DemonLordStates.get(serverPlayer.getUUID()) != null) {
                SemionPlayer semionPlayer = gameManager.playableGame(serverPlayer.getUUID())
                        .map(game -> game.players().get(serverPlayer.getUUID()))
                        .orElse(null);
                if (semionPlayer != null) {
                    new DemonLordStatGui(serverPlayer, semionPlayer.economy()).open();
                    return InteractionResult.SUCCESS;
                }
            }
            if (serverPlayer.getInventory().getSelectedSlot() != DemonLordSkill.BLADE_SLOT) {
                // 스킬 카드는 들고 우클릭해도 아무 일도 일어나면 안 됩니다. 시전은 슬롯을 잡는
                // 동작이고, 카드 자체는 표시용입니다.
                //
                // 그냥 PASS 로 흘려보내면 바닐라가 그 아이템의 사용 동작을 그대로 실행합니다.
                // 스킬 아이콘 중에는 염소 뿔·방패·화염구·위더 해골처럼 진짜 사용 동작이 붙은
                // 것들이 있어서, 아레나 한복판에서 불을 놓거나 방패를 들거나 하다가 튕깁니다.
                return DemonLordKitItems.isKitItem(serverPlayer.getMainHandItem())
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS;
            }
            return handleKeyBinding(gameManager, serverPlayer, DemonLordBinding.RIGHT_CLICK)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
        // 블록을 보고 우클릭하는 경로는 위 콜백을 타지 않습니다. 화염구와 위더 해골은 이쪽으로
        // 설치되므로 같이 막습니다.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!DemonLordKitItems.isKitItem(serverPlayer.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND
                    && serverPlayer.getInventory().getSelectedSlot() == DemonLordSkill.BLADE_SLOT) {
                return handleKeyBinding(gameManager, serverPlayer, DemonLordBinding.RIGHT_CLICK)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });
        registerCombatHooks(gameManager);
    }

    private static void registerCombatHooks(SemionGameManager gameManager) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }
            DemonLordState state = DemonLordStates.get(player.getUUID());
            if (state == null || !state.inCombat()) {
                return true;
            }
            // 체력은 보스바 풀에서만 관리합니다. 바닐라 체력은 건드리지 않습니다.
            state.expireShieldIfNeeded(player.level().getGameTime());
            boolean knockedOut = state.applyDamage(amount);
            // 바닐라 피해를 막으면 연출도 같이 사라지므로 피격 패킷을 직접 보냅니다.
            sendHitFeedback(player, source, amount);
            if (knockedOut) {
                knockOutOfCombat(player, state);
            }
            return false;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer attacker)) {
                return InteractionResult.PASS;
            }
            DemonLordState state = DemonLordStates.get(attacker.getUUID());
            if (state == null || !state.inCombat() || !(target instanceof SemionMonsterEntity monsterEntity)) {
                return InteractionResult.PASS;
            }
            // 마검 평타. 바닐라 피해 대신 런타임 피해로 넣어야 몹의 방어/저항이 정상 적용됩니다.
            //
            // 바닐라 공격 쿨다운은 바닐라 피해 경로에만 걸리므로, 여기서 직접 걸지 않으면 연타가
            // 그대로 최대 피해가 됩니다. 차지 비율을 바닐라와 같은 곡선으로 곱해 줍니다.
            int interval = (int) TowerBalanceRuntime.ability(
                    DemonLordTowers.GLOBAL_CONFIG_ID, "bladeAttackIntervalTicks", 12.0);
            long now = attacker.level().getGameTime();
            double charge = state.bladeChargeScale(now, interval);
            state.recordBladeAttack(now);
            PlayerLane lane = gameManager.playableGame(attacker.getUUID())
                    .flatMap(game -> game.playerLane(attacker.getUUID()))
                    .orElse(null);
            List<DemonLordSkillTower> altars = lane == null
                    ? List.of()
                    : orderedAltars(lane, attacker.getUUID());
            dealDamage(attacker, lane, altars.isEmpty() ? null : altars.getFirst(), monsterEntity,
                    state.bladeDamage() * (0.2 + charge * charge * 0.8), DamageType.PHYSICAL);
            playSwing(attacker, charge);
            return InteractionResult.SUCCESS;
        });
    }

    /** Called once per lane tick from {@code PlayerLane}. */
    public static void tick(PlayerLane lane, Map<UUID, SemionPlayer> players) {
        if (lane == null || lane.arenaWorld() == null || players == null) {
            return;
        }
        UUID owner = lane.ownerPlayer();
        SemionPlayer semionPlayer = owner == null ? null : players.get(owner);
        if (semionPlayer == null || !isDemonLord(semionPlayer)) {
            if (owner != null && DemonLordStates.get(owner) != null) {
                ServerPlayer player = lane.arenaWorld().getServer().getPlayerList().getPlayer(owner);
                if (player == null) {
                    clearPlayerState(owner);
                } else {
                    cleanupPlayer(player);
                }
            }
            return;
        }
        ServerPlayer player = lane.arenaWorld().getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        state.setLaneId(lane.laneId());
        long gameTime = lane.arenaWorld().getGameTime();

        // 초당 한 번 강제로 다시 깔아, 인벤토리에서 스킬이나 마검을 옮겨도 제자리로 돌아옵니다.
        if (gameTime % 20 == 0) {
            state.markLoadoutDirty();
        }
        if (state.loadoutDirty()) {
            syncHotbar(player, lane, state);
            state.clearLoadoutDirty();
        }
        syncBossBar(player, state);
        syncMoveSpeed(player, state);

        if (!state.inCombat()) {
            restoreFlight(player);
            releaseAggro(player, gameTime);
            return;
        }

        state.tickRoundMetrics();

        if (state.consumePendingSpawn()) {
            moveToLaneCentre(player, lane);
        }
        enforceCombatArea(player, lane, state);
        state.expireShieldIfNeeded(gameTime);
        lockFlight(player);
        rescueFromVoid(player, lane);
        DemonLordSkills.tickPending(player, lane, state, gameTime);
        detectSkillCast(player, lane, state, gameTime);
    }

    /** Round start: pull the demon lord to the middle of their own lane. */
    private static void moveToLaneCentre(ServerPlayer player, PlayerLane lane) {
        LaneRegionLayout layout = lane.laneLayout();
        if (layout == null) {
            return;
        }
        Vec3 centre = laneCentre(layout);
        player.teleportTo(centre.x, centre.y, centre.z);
        setHeldSlot(player, DemonLordSkill.BLADE_SLOT);
    }

    /**
     * Middle of the player's own lane.
     *
     * <p>Not {@code positionAt(0.5)}: {@link LaneRegionLayout#pathPoints()} runs lane spawn ->
     * lane waypoints -> the shared final waypoints -> the central boss spawn, so half way along it
     * lands outside the lane entirely. Only the stretch that is actually inside {@code lane_path}
     * counts, which keeps the drop on walkable path rather than on the lane area's bounding box
     * centre (an L-shaped lane would put that inside a wall).
     */
    private static Vec3 laneCentre(LaneRegionLayout layout) {
        List<Vec3> inside = layout.pathPoints().stream()
                .filter(point -> containsHorizontally(layout.laneArea(), point))
                .toList();
        if (inside.size() == 1) {
            return inside.getFirst();
        }
        if (inside.size() >= 2) {
            return midpointAlong(inside);
        }
        BlockBounds area = layout.laneArea();
        return new Vec3(
                (area.min().getX() + area.max().getX() + 1.0) / 2.0,
                layout.spawn().y,
                (area.min().getZ() + area.max().getZ() + 1.0) / 2.0
        );
    }

    private static Vec3 midpointAlong(List<Vec3> points) {
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        if (total <= 0.0) {
            return points.getFirst();
        }
        double target = total / 2.0;
        double walked = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            double segment = from.distanceTo(to);
            if (segment <= 0.0) {
                continue;
            }
            if (walked + segment >= target) {
                return from.lerp(to, (target - walked) / segment);
            }
            walked += segment;
        }
        return points.getLast();
    }

    private static boolean containsHorizontally(BlockBounds area, Vec3 point) {
        return point.x >= area.min().getX()
                && point.x < area.max().getX() + 1.0
                && point.z >= area.min().getZ()
                && point.z < area.max().getZ() + 1.0;
    }

    public static void clearBossBar(UUID playerId) {
        ServerBossEvent bar = BOSS_BARS.remove(playerId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    public static void cleanupPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean hadState = DemonLordStates.get(player.getUUID()) != null;
        clearCombatKit(player);
        if (hadState) {
            restoreFlight(player);
        }
        clearPlayerState(player.getUUID());
    }

    /**
     * 웨이브가 시작될 때 마왕을 전투 상태로 넣습니다.
     *
     * <p>준비 단계가 아니라 여기인 것이 중요합니다. 준비 단계에 전투로 들어가면 핫바가 스킬로
     * 덮여 타워를 살 수 없고, 8번으로 스스로 물러난 뒤에는 웨이브가 시작돼도 복귀할 방법이
     * 없어 레인이 그대로 뚫립니다. 웨이브마다 다시 들어오므로 물러남의 대가는 그 웨이브 하나로
     * 끝납니다.
     */
    public static void beginWave(UUID playerId) {
        DemonLordState state = DemonLordStates.get(playerId);
        if (state != null) {
            state.enterCombat();
        }
    }

    public static TowerRoundMetricsTracker roundMetricsTracker(UUID playerId) {
        DemonLordState state = DemonLordStates.get(playerId);
        return state == null ? null : state.roundMetricsTracker();
    }

    /**
     * 라운드가 끝나면 전투를 해제합니다.
     *
     * <p>{@link #beginWave}의 짝입니다. 이게 없으면 전투 플래그가 웨이브를 넘어 살아남아 다음
     * 준비 단계까지 스킬 핫바가 유지되고, 상점을 열 수 없습니다. 체력이 남아 있었다면 그대로
     * 두고 [대기]로만 표시합니다.
     */
    public static void endRound(UUID playerId) {
        DemonLordState state = DemonLordStates.get(playerId);
        if (state != null) {
            state.standDown();
        }
    }

    public static void clearPlayerState(UUID playerId) {
        DemonLordState state = DemonLordStates.get(playerId);
        if (state != null) {
            state.removeRoundMetrics();
        }
        clearBossBar(playerId);
        PRE_COMBAT_HOTBAR.remove(playerId);
        DemonLordStates.clear(playerId);
    }

    // ------------------------------------------------------------- internals

    private static boolean isDemonLord(SemionPlayer player) {
        return player.job().map(job -> DemonLordTowerJob.ID.equals(job.id())).orElse(false);
    }

    private static void knockOutOfCombat(ServerPlayer player, DemonLordState state) {
        knockOutOfCombat(player, state, false);
    }

    private static void knockOutOfCombat(ServerPlayer player, DemonLordState state, boolean voluntary) {
        state.leaveCombat();
        releaseAggro(player);
        restoreFlight(player);
        setHeldSlot(player, DemonLordSkill.BLADE_SLOT);
        player.displayClientMessage(
                Component.literal(voluntary
                                ? "스스로 전투에서 물러났습니다. 다음 라운드에 복귀합니다."
                                : "전투에서 제외되었습니다. 다음 라운드에 부활합니다.")
                        .withStyle(voluntary ? ChatFormatting.GOLD : ChatFormatting.DARK_RED),
                false
        );
    }

    /**
     * Restores the swing animation and hit sound.
     *
     * <p>Returning {@code SUCCESS} from the attack callback cancels vanilla's whole attack path, and
     * the arm swing and the strong/weak hit sound go with it. Charge decides which sound plays, so
     * the player can hear whether the swing was fully wound up.
     */
    private static void playSwing(ServerPlayer attacker, double charge) {
        attacker.swing(InteractionHand.MAIN_HAND, true);
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(
                null,
                attacker.getX(),
                attacker.getY(),
                attacker.getZ(),
                charge >= 1.0 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );
    }

    /**
     * Rebuilds the hit feedback that blocking vanilla damage throws away.
     *
     * <p>{@link ClientboundHurtAnimationPacket} is what makes the screen flash red and tilts the
     * camera away from the attacker - the same packet vanilla sends on a normal hit. Without it the
     * demon lord takes damage with no on-screen sign at all beyond the boss bar sliding.
     */
    private static void sendHitFeedback(ServerPlayer player, DamageSource source, double amount) {
        float hurtDirection = 0.0f;
        Entity attacker = source == null ? null : source.getEntity();
        if (attacker != null) {
            hurtDirection = (float) (Mth.atan2(attacker.getZ() - player.getZ(), attacker.getX() - player.getX())
                    * (180.0 / Math.PI) - player.getYRot());
        }
        player.connection.send(new ClientboundHurtAnimationPacket(player.getId(), hurtDirection));

        // 큰 피해일수록 강하게: 최대 체력의 5% 를 넘는 타격에만 낮은 신음을 겹칩니다.
        DemonLordState state = DemonLordStates.get(player.getUUID());
        boolean heavy = state != null && amount >= state.maxHealth() * 0.05;
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(heavy ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.PLAYER_HURT),
                SoundSource.PLAYERS,
                player.getX(),
                player.getY(),
                player.getZ(),
                heavy ? 1.0f : 0.6f,
                heavy ? 0.7f : 1.0f,
                player.level().getRandom().nextLong()
        ));
    }

    private static void syncBossBar(ServerPlayer player, DemonLordState state) {
        ServerBossEvent bar = BOSS_BARS.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(
                    Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            created.addPlayer(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        if (state.inCombat()) {
            bar.setName(Component.literal(
                            "마왕 Lv." + state.level() + "  " + Math.round(state.health()) + " / " + Math.round(state.maxHealth())
                                    + (state.shield() > 0.0 ? "  (+" + Math.round(state.shield()) + ")" : "")
                                    + experienceSuffix(state))
                    .withStyle(ChatFormatting.RED));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress((float) state.healthRatio());
        } else {
            // 체력이 남아 있으면 웨이브를 버티고 내려온 것이고, 0이면 실제로 쓰러진 것입니다.
            boolean knockedOut = state.health() <= 0.0;
            bar.setName(Component.literal(
                            "마왕 Lv." + state.level() + (knockedOut ? "  [전투 제외]" : "  [대기]"))
                    .withStyle(knockedOut ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
            bar.setColor(BossEvent.BossBarColor.WHITE);
            bar.setProgress(knockedOut ? 0.0f : (float) state.healthRatio());
        }
    }

    /**
     * 다음 레벨까지 남은 경험치. 만렙이면 그 사실을 대신 보여 줍니다.
     *
     * <p>레벨이 이 빌더의 성장 전부인데 얼마나 남았는지 볼 곳이 없었습니다. 보스바는 전투 중
     * 항상 떠 있는 유일한 표시라 여기에 붙입니다.
     */
    private static String experienceSuffix(DemonLordState state) {
        if (state.level() >= state.maxLevel()) {
            return "  [만렙]";
        }
        double remaining = Math.max(0.0, state.experienceForNextLevel() - state.experience());
        return "  EXP " + (long) Math.ceil(remaining) + " 남음";
    }

    /**
     * Drops monsters that are still chewing on a knocked-out demon lord.
     *
     * <p>Blocking target <i>acquisition</i> is not enough: a monster that locked on while the player
     * was still fighting keeps that target until something clears it, so [전투 제외] would not
     * actually take the player out of the fight.
     */
    private static void releaseAggro(ServerPlayer player, long gameTime) {
        if (gameTime % AGGRO_RELEASE_INTERVAL != 0) {
            return;
        }
        releaseAggro(player);
    }

    static void releaseAggro(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (Entity candidate : level.getAllEntities()) {
            if (candidate instanceof SemionMonsterEntity entity && entity.getTarget() == player) {
                entity.setTarget(null);
            }
        }
    }

    private static void enforceCombatArea(ServerPlayer player, PlayerLane lane, DemonLordState state) {
        LaneRegionLayout layout = lane.laneLayout();
        if (layout == null) {
            return;
        }
        if (!lane.clearedThisRound()) {
            if (!containsHorizontally(layout.laneArea(), player.position())) {
                teleport(player, laneCentre(layout));
            }
            return;
        }

        if (!state.centralDefense()) {
            state.enterCentralDefense();
            AABB area = layout.finalDefenseTowerAreaBox();
            teleport(player, new Vec3(
                    (area.minX + area.maxX) / 2.0,
                    area.maxY,
                    (area.minZ + area.maxZ) / 2.0));
        } else if (!layout.isInsideFinalDefenseTowerArea(player.position())) {
            teleport(player, layout.clampToFinalDefenseTowerArea(player.position()));
        }
    }

    private static void teleport(ServerPlayer player, Vec3 position) {
        player.teleportTo(position.x, position.y, position.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /**
     * 이동 속도 스탯을 바닐라 속성으로 반영합니다.
     *
     * <p>속도 물약 효과가 아니라 속성 수정자를 쓰는 것은 포인트당 3% 같은 잔단위를 표현하려면
     * 등급 단위(20%)로는 불가능하기 때문입니다. 일시(transient) 수정자라 저장되지 않고, 값이
     * 달라질 때만 갱신해 매 틱 속성을 흔들지 않습니다.
     */
    private static void syncMoveSpeed(ServerPlayer player, DemonLordState state) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        double bonus = state.inCombat() ? state.moveSpeedBonus() : 0.0;
        AttributeModifier existing = attribute.getModifier(MOVE_SPEED_MODIFIER_ID);
        if (bonus <= 0.0) {
            if (existing != null) {
                attribute.removeModifier(MOVE_SPEED_MODIFIER_ID);
            }
            return;
        }
        if (existing != null && Math.abs(existing.amount() - bonus) < 1.0E-6) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                MOVE_SPEED_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void lockFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.getAbilities().mayfly || player.getAbilities().flying) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void restoreFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    /**
     * 맵 아래로 떨어진 마왕을 자기 레인 중앙으로 되돌립니다.
     *
     * <p>레인 밖으로 나가는 것 자체는 막지 않습니다. 다만 허공으로 빠지는 것만은 되돌려야
     * 합니다. 전투 중에는 비행이 잠겨 있어 한 번 떨어지면 스스로 올라올 방법이 없고, 그대로
     * 두면 낙사 외에는 결말이 없습니다.
     */
    private static void rescueFromVoid(ServerPlayer player, PlayerLane lane) {
        if (lane.laneLayout() == null) {
            return;
        }
        double floor = lane.laneLayout().laneArea().min().getY();
        if (player.getY() >= floor - FALL_RESCUE_DEPTH) {
            return;
        }
        Vec3 centre = laneCentre(lane.laneLayout());
        player.teleportTo(centre.x, centre.y, centre.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /**
     * 이동기가 실제로 설 수 있는 지점을 고릅니다.
     *
     * <p>순간이동은 충돌을 무시합니다. 목표를 그대로 쓰면 벽 속에 박히거나 허공에 놓여, 비행이
     * 잠긴 채로 맵 밖으로 떨어집니다. 그래서 목표에서 시작점 쪽으로 되짚어 오며 몸이 들어갈
     * 틈이 있고 발밑에 땅이 있는 첫 지점을 씁니다. 어디에도 설 수 없으면 제자리입니다.
     *
     * <p>전투 영역은 호출자가 먼저 자릅니다. 여기서는 벽과 허공만 검사합니다.
     */
    static Vec3 safeLanding(ServerPlayer player, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 1.0E-4) {
            Vec3 spot = standingSpot(player, to);
            return spot == null ? from : spot;
        }
        int steps = (int) Math.ceil(distance / LANDING_STEP);
        for (int i = steps; i >= 1; i--) {
            Vec3 spot = standingSpot(player, from.add(delta.scale((double) i / steps)));
            if (spot != null) {
                return spot;
            }
        }
        return from;
    }

    static Vec3 safeLanding(
            ServerPlayer player,
            PlayerLane lane,
            DemonLordState state,
            Vec3 from,
            Vec3 to
    ) {
        LaneRegionLayout layout = lane == null ? null : lane.laneLayout();
        if (layout != null) {
            to = state != null && state.centralDefense()
                    ? layout.clampToFinalDefenseTowerArea(to)
                    : containsHorizontally(layout.laneArea(), to) ? to : from;
        }
        return safeLanding(player, from, to);
    }

    /**
     * 후보 지점에 설 수 있으면 발이 닿는 좌표, 아니면 {@code null}.
     *
     * <p>탐침을 후보보다 위에서 시작하지 않는 것이 중요합니다. 위에서 쏘면 벽 속을 노렸을 때
     * 그 벽의 윗면을 짚어 아레나 배리어 위에 올려놓게 됩니다. 후보가 블록 안이면 탐침이 즉시
     * 막히고 몸 검사에서 걸러져, 한 걸음 뒤로 물러난 지점을 보게 됩니다.
     */
    private static Vec3 standingSpot(ServerPlayer player, Vec3 candidate) {
        HitResult ground = player.level().clip(new ClipContext(
                candidate.add(0.0, 0.05, 0.0),
                candidate.subtract(0.0, GROUND_PROBE_DEPTH, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        if (ground.getType() == HitResult.Type.MISS) {
            return null;
        }
        Vec3 foot = ground.getLocation();
        AABB body = player.getBoundingBox().move(foot.subtract(player.position()));
        return player.level().noCollision(player, body.deflate(BODY_MARGIN)) ? foot : null;
    }

    /**
     * Casting is "move your hand to the slot", so we poll the selected slot instead of listening for
     * a use packet. A cast always bounces the hand back to the 마검 slot, which also means holding a
     * skill slot cannot re-trigger it every tick.
     */
    private static void detectSkillCast(ServerPlayer player, PlayerLane lane, DemonLordState state, long gameTime) {
        int selected = player.getInventory().getSelectedSlot();
        if (selected == state.lastSelectedSlot()) {
            return;
        }
        state.setLastSelectedSlot(selected);

        // 8번 슬롯은 스스로 전투에서 빠지는 자리입니다. 다음 라운드까지 스킬을 못 쓰지만
        // 어그로에서도 벗어나므로, 이길 수 없는 웨이브를 버티다 죽는 대신 물러설 수 있습니다.
        if (selected == RETREAT_SLOT) {
            setHeldSlot(player, DemonLordSkill.BLADE_SLOT);
            state.setLastSelectedSlot(DemonLordSkill.BLADE_SLOT);
            knockOutOfCombat(player, state, true);
            return;
        }

        DemonLordBinding binding = DemonLordBinding.forHotbarSlot(selected);
        if (binding == null) {
            return;
        }
        // 우클릭 스킬은 마검에 걸려 있습니다. 그 슬롯은 쿨타임을 보여 주는 자리일 뿐이라,
        // 집어 들면 아무것도 못 하는 아이템을 든 채로 남지 않게 마검으로 되돌립니다.
        if (!binding.castOnSelect()) {
            setHeldSlot(player, DemonLordSkill.BLADE_SLOT);
            state.setLastSelectedSlot(DemonLordSkill.BLADE_SLOT);
            return;
        }
        tryCast(player, lane, state, binding, gameTime);
        setHeldSlot(player, DemonLordSkill.BLADE_SLOT);
        state.setLastSelectedSlot(DemonLordSkill.BLADE_SLOT);
    }

    /**
     * Fires the skill on {@code binding} if the player owns it and it is off cooldown.
     *
     * @return {@code true} when the input belonged to the demon lord and should be swallowed
     */
    public static boolean tryCast(
            ServerPlayer player,
            PlayerLane lane,
            DemonLordState state,
            DemonLordBinding binding,
            long gameTime
    ) {
        DemonLordSkillTower altar = altarFor(lane, player.getUUID(), binding);
        if (altar == null) {
            return false;
        }
        DemonLordSkill skill = altar.skill();
        if (skill == null) {
            return false;
        }
        if (!state.isSkillReady(skill, gameTime)) {
            return true;
        }
        int refund = DemonLordSkills.cast(player, lane, state, skill, altar, gameTime);
        // 쿨감 스탯은 곱연산이라 0 에 닿지 않고, 환급은 그 뒤에 뺍니다.
        int base = (int) Math.round(altar.cooldownTicks() * state.cooldownMultiplier());
        int cooldown = Math.max(1, base - Math.max(0, refund));
        state.startCooldown(skill, gameTime, cooldown);
        player.getCooldowns().addCooldown(new ItemStack(skill.item()), cooldown);
        return true;
    }

    /**
     * Entry point for the key-driven bindings (F and Q).
     *
     * @return {@code true} when the key was consumed as a skill and its vanilla action must not run
     */
    public static boolean handleKeyBinding(SemionGameManager gameManager, ServerPlayer player, DemonLordBinding binding) {
        DemonLordState state = DemonLordStates.get(player.getUUID());
        if (state == null || !state.inCombat()) {
            return false;
        }
        PlayerLane lane = gameManager.playableGame(player.getUUID())
                .flatMap(game -> game.playerLane(player.getUUID()))
                .orElse(null);
        if (lane == null || lane.arenaWorld() == null) {
            return true;
        }
        tryCast(player, lane, state, binding, lane.arenaWorld().getGameTime());
        return true;
    }

    /**
     * Moves the hand and tells the client about it.
     *
     * <p>{@code Inventory#setSelectedSlot} only updates the server copy; without the packet the
     * client keeps its own selection and echoes it straight back, so the hand appears never to move.
     */
    private static void setHeldSlot(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    /**
     * The player's altars in build order.
     *
     * <p>Build order is what decides the key binding, so the first altar raised answers to
     * {@code 1}. {@code lane.towers()} keeps insertion order, and upgrading replaces a tower in
     * place, so a tier-up never shuffles the bar under the player's fingers.
     */
    public static List<DemonLordSkillTower> orderedAltars(PlayerLane lane, UUID owner) {
        List<DemonLordSkillTower> altars = new ArrayList<>();
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof DemonLordSkillTower altar && owner.equals(altar.ownerPlayer())) {
                altars.add(altar);
            }
        }
        return altars;
    }

    private static DemonLordSkillTower altarFor(PlayerLane lane, UUID owner, DemonLordBinding binding) {
        List<DemonLordSkillTower> altars = orderedAltars(lane, owner);
        int index = binding.ordinal();
        return index < altars.size() ? altars.get(index) : null;
    }

    static DemonLordSkillTower altarFor(PlayerLane lane, UUID owner, TowerType type) {
        return orderedAltars(lane, owner).stream()
                .filter(altar -> altar.type().id().equals(type.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Rebuilds the bar.
     *
     * <p>In combat the hotbar belongs entirely to the demon lord kit: the first four altars sit on
     * keys 1-4 and the 마검 waits in slot 9. Out of combat the kit is stripped and whatever the bar
     * held before combat comes back, because that is when the player is actually shopping for towers.
     */
    private static void syncHotbar(ServerPlayer player, PlayerLane lane, DemonLordState state) {
        if (!state.inCombat()) {
            if (state.combatKitGranted()) {
                clearCombatKit(player);
                restoreHotbar(player);
                state.setCombatKitGranted(false);
            }
            ensureStatTool(player);
            return;
        }

        if (!state.combatKitGranted()) {
            rememberHotbar(player);
        }
        SemionHotbarService.clearMatchTools(player);
        clearCombatKit(player);
        List<DemonLordSkillTower> altars = orderedAltars(lane, player.getUUID());
        // 타워 정보창이 자기 키를 보여줄 수 있게 배정 결과를 되돌려 씁니다.
        for (int i = 0; i < altars.size(); i++) {
            altars.get(i).setBinding(DemonLordBinding.forIndex(i));
        }
        for (DemonLordBinding binding : DemonLordBinding.values()) {
            if (!binding.isHotbarSlot()) {
                continue;
            }
            int index = binding.ordinal();
            if (index >= altars.size()) {
                player.getInventory().setItem(binding.hotbarSlot(), ItemStack.EMPTY);
                continue;
            }
            player.getInventory().setItem(binding.hotbarSlot(), skillStack(altars.get(index), binding));
        }
        ItemStack retreat = new ItemStack(Items.TOTEM_OF_UNDYING);
        retreat.set(DataComponents.CUSTOM_NAME, RETREAT_NAME);
        player.getInventory().setItem(RETREAT_SLOT, DemonLordKitItems.mark(retreat));

        ItemStack blade = new ItemStack(Items.NETHERITE_SWORD);
        blade.set(DataComponents.CUSTOM_NAME, BLADE_NAME);
        player.getInventory().setItem(DemonLordSkill.BLADE_SLOT, DemonLordKitItems.mark(blade));
        state.setCombatKitGranted(true);
    }

    /** Item name carries the key, so F/Q skills are still discoverable even off the bar. */
    private static ItemStack skillStack(DemonLordSkillTower altar, DemonLordBinding binding) {
        ItemStack stack = new ItemStack(altar.skill().item());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                        "[" + binding.label() + "] " + altar.skill().displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return DemonLordKitItems.mark(stack);
    }

    private static void clearCombatKit(ServerPlayer player) {
        DemonLordKitItems.clear(player.getInventory());
    }

    /**
     * 준비 단계에만 놓이는 스탯 분배 도구입니다.
     *
     * <p>전투 중에는 핫바가 스킬로 꽉 차므로 자리가 없고, 어차피 분배는 다음 웨이브를 준비하며
     * 하는 일입니다. 이미 올바른 아이템이 있으면 건드리지 않아 매 틱 인벤토리를 흔들지 않습니다.
     */
    private static void ensureStatTool(ServerPlayer player) {
        if (isStatTool(player.getInventory().getItem(STAT_TOOL_SLOT))) {
            return;
        }
        ItemStack tool = new ItemStack(Items.EXPERIENCE_BOTTLE);
        tool.set(DataComponents.CUSTOM_NAME, STAT_TOOL_NAME);
        player.getInventory().setItem(STAT_TOOL_SLOT, tool);
    }

    /**
     * 전투에 들어가기 직전의 핫바를 그대로 찍어 둡니다.
     *
     * <p>전에는 전투가 끝날 때 {@code grantMatchTools} 로 되돌렸는데, 그건 일반 매치의 타워·소환
     * 도구만 아는 함수입니다. 샌드박스의 라운드 이동 도구나 팀장 도구처럼 다른 경로로 받은
     * 것들은 전투 진입에 지워진 뒤 영영 돌아오지 않았습니다. 무엇을 들고 있었는지 기억해 두면
     * 이 서비스가 도구 종류를 하나도 몰라도 됩니다.
     */
    static void rememberHotbar(ServerPlayer player) {
        List<ItemStack> saved = new ArrayList<>(HOTBAR_SLOTS);
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            saved.add(player.getInventory().getItem(slot).copy());
        }
        PRE_COMBAT_HOTBAR.put(player.getUUID(), List.copyOf(saved));
    }

    static void restoreHotbar(ServerPlayer player) {
        List<ItemStack> saved = PRE_COMBAT_HOTBAR.remove(player.getUUID());
        if (saved == null) {
            return;
        }
        for (int slot = 0; slot < saved.size(); slot++) {
            // 이미 뭔가 들어 있는 칸은 건드리지 않습니다. 라운드 시작에 게임이 새로 쥐여 준
            // 도구가 우선이고, 우리는 우리가 비워 둔 자리만 되돌립니다.
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, saved.get(slot).copy());
            }
        }
    }

    private static boolean isStatTool(ItemStack stack) {
        if (stack == null || !stack.is(Items.EXPERIENCE_BOTTLE)) {
            return false;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && name.getString().equals(STAT_TOOL_NAME.getString());
    }

    /** Shared damage entry point for the blade and every skill. */
    static Tower.DamageResult dealDamage(
            ServerPlayer attacker,
            PlayerLane lane,
            DemonLordSkillTower altar,
            SemionMonsterEntity monsterEntity,
            double amount,
            DamageType type
    ) {
        if (amount <= 0.0 || monsterEntity == null || monsterEntity.isRemoved()) {
            return Tower.DamageResult.NONE;
        }
        Monster monster = monsterEntity.runtimeMonster();
        if (monster == null || !monster.isAlive()) {
            return Tower.DamageResult.NONE;
        }
        if (attacker != null) {
            DemonLordState state = DemonLordStates.get(attacker.getUUID());
            if (state == null || !state.inCombat() || !state.canFight(monster)) {
                return Tower.DamageResult.NONE;
            }
        }
        SemionTowerEntity source = altar == null ? null : altar.entity(lane);
        if (source != null) {
            Tower.DamageResult result = altar.damageTargetResult(source, monsterEntity, amount, type);
            if (result.dealtDamage() > 0.0) {
                DemonLordState state = attacker == null ? null : DemonLordStates.get(attacker.getUUID());
                if (state != null) {
                    state.recordDamageDealt(result.dealtDamage(), type);
                    if (result.killed()) {
                        state.recordKill();
                    }
                }
                // 제단은 쏘지 않습니다. showAttack 을 쓰면 건축 구역의 제단에서 몹까지 직선이
                // 뻗어 나가, 아무것도 하지 않는 기둥이 공격한 것처럼 보입니다. 타격 표시와
                // 처치 연출만 남기고 궤적은 뺍니다.
                TowerVfxService.showRemoteHit(source, monsterEntity, result.killed());
            }
            return result;
        }
        double before = monster.health();
        boolean killed = monsterEntity.applyRuntimeDamage(
                attacker.damageSources().playerAttack(attacker), amount, type);
        double dealtDamage = Math.max(0.0, before - monster.health());
        if (dealtDamage > 0.0) {
            monster.recordLastHit(attacker.getUUID(), KillSourceKind.TOWER);
            DemonLordState state = DemonLordStates.get(attacker.getUUID());
            if (state != null) {
                state.recordDamageDealt(dealtDamage, type);
                if (killed) {
                    state.recordKill();
                }
            }
        }
        return new Tower.DamageResult(killed, dealtDamage, amount);
    }
}
