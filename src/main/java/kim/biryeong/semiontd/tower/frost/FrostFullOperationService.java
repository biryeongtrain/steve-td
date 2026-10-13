package kim.biryeong.semiontd.tower.frost;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.job.FrostTowerJob;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.ui.SemionText;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/** 혹한 빌더의 웨이브별 생산라인 충전과 9번 슬롯 완전 가동 능력. */
public final class FrostFullOperationService {
    private static final int ACTIVATION_SLOT = 8;
    private static final String ACTIVATION_ITEM_KEY = "semion_td_frost_full_operation";
    private static final float FULL_OPERATION_SOUND_PITCH = 0.75F;
    private static final Component ACTIVATION_NAME = Component.literal("냉동창고 완전 가동")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    private static final Identifier CHILL_PULSE_ID =
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_full_operation_chill");
    private static final Identifier DAMAGE_REDUCTION_ID =
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_full_operation_damage_reduction");
    private static final Identifier FULL_OPERATION_AMBIENT_SOUND_ID =
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop");
    private static final Map<UUID, FrostFullOperationState> STATES = new ConcurrentHashMap<>();

    private FrostFullOperationService() {
    }

    public static void register(SemionGameManager gameManager) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()
                    || hand != InteractionHand.MAIN_HAND
                    || !(player instanceof ServerPlayer serverPlayer)
                    || !isActivationItem(serverPlayer.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            SemionGame game = gameManager.playableGame(serverPlayer.getUUID()).orElse(null);
            if (game == null || !game.isActiveParticipant(serverPlayer.getUUID())) {
                return InteractionResult.PASS;
            }
            var semionPlayer = game.players().get(serverPlayer.getUUID());
            if (semionPlayer == null
                    || semionPlayer.job().filter(job -> FrostTowerJob.ID.equals(job.id())).isEmpty()) {
                return InteractionResult.PASS;
            }
            PlayerLane lane = game.playerLane(serverPlayer.getUUID()).orElse(null);
            if (lane == null || !activate(lane, serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static void beginWave(PlayerLane lane) {
        if (lane == null || lane.ownerPlayer() == null) {
            return;
        }
        FrostFullOperationState state = STATES.computeIfAbsent(lane.ownerPlayer(), ignored -> new FrostFullOperationState());
        state.beginWave();
        clearFullOperationEffects(lane);
        onlinePlayer(lane).ifPresent(player -> {
            clearActivationItem(player);
            stopFullOperationPresentation(player);
        });
    }

    public static void endWave(PlayerLane lane) {
        if (lane == null || lane.ownerPlayer() == null) {
            return;
        }
        FrostFullOperationState state = STATES.get(lane.ownerPlayer());
        if (state != null) {
            state.endWave();
        }
        clearFullOperationEffects(lane);
        onlinePlayer(lane).ifPresent(player -> {
            clearActivationItem(player);
            stopFullOperationPresentation(player);
        });
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static void cleanupPlayer(ServerPlayer player) {
        if (player != null) {
            clearActivationItem(player);
            stopFullOperationPresentation(player);
            clearPlayer(player.getUUID());
        }
    }

    public static void clearAll() {
        STATES.clear();
    }

    static void recordSpecialActivation(PlayerLane lane, TriggerFamily family) {
        if (lane == null || family == null || lane.arenaWorld() == null) {
            return;
        }
        FrostFullOperationState state = STATES.computeIfAbsent(lane.ownerPlayer(), ignored -> new FrostFullOperationState());
        if (!state.record(family, lane.arenaWorld().getGameTime())) {
            return;
        }
        updateReadiness(lane, state);
    }

    static void onEruptionChillChanged(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        FrostFullOperationState state = STATES.computeIfAbsent(lane.ownerPlayer(), ignored -> new FrostFullOperationState());
        updateReadiness(lane, state);
    }

    public static void tick(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        FrostFullOperationState state = STATES.get(lane.ownerPlayer());
        if (state == null || !state.active()) {
            return;
        }
        long gameTime = lane.arenaWorld().getGameTime();
        if (gameTime >= state.activeUntilTick()) {
            state.expire();
            clearFullOperationEffects(lane);
            onlinePlayer(lane).ifPresent(FrostFullOperationService::stopFullOperationPresentation);
            return;
        }
        refreshFullOperationEffects(lane, state, gameTime);
        onlinePlayer(lane).ifPresent(player -> player.setTicksFrozen(player.getTicksRequiredToFreeze()));
        if (gameTime >= state.nextChillPulseTick()) {
            applyChillPulse(lane);
            state.scheduleChillPulse(gameTime + Math.max(1, FrostBalance.fullOperationChillIntervalTicks()));
        }
    }

    public static double fixedOutgoingDamage(UUID ownerPlayer, long gameTime, double normalDamage) {
        return isActive(ownerPlayer, gameTime)
                ? Math.max(0.0, FrostBalance.fullOperationFixedAttackDamage())
                : normalDamage;
    }

    public static double fixedIncomingDamage(
            UUID ownerPlayer,
            long gameTime,
            double originalDamage,
            double normallyReducedDamage
    ) {
        if (!isActive(ownerPlayer, gameTime)) {
            return normallyReducedDamage;
        }
        return Math.max(0.0, originalDamage) * (1.0 - FrostBalance.fullOperationDamageReduction());
    }

    public static boolean isActivationItem(ItemStack stack) {
        if (stack == null || !stack.is(Items.ICE)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(ACTIVATION_ITEM_KEY, false);
    }

    static FrostFullOperationState stateForTest(UUID ownerPlayer) {
        return STATES.computeIfAbsent(ownerPlayer, ignored -> new FrostFullOperationState());
    }

    private static boolean activate(PlayerLane lane, ServerPlayer player) {
        FrostFullOperationState state = STATES.get(lane.ownerPlayer());
        if (state == null || !state.ready() || state.usedThisWave() || !hasBothDevices(lane)
                || eruptionChill(lane) + 1.0E-9 < FrostBalance.fullOperationEruptionChill()) {
            clearActivationItem(player);
            return false;
        }
        long gameTime = lane.arenaWorld().getGameTime();
        state.activate(gameTime);
        clearActivationItem(player);
        refreshFullOperationEffects(lane, state, gameTime);
        startFullOperationPresentation(player);
        showFullOperationVfx(lane);
        player.sendSystemMessage(SemionText.prefixedMini(
                "<aqua><bold>냉동창고 완전 가동!</bold></aqua> <gray>"
                        + format(FrostBalance.fullOperationDurationTicks(), "seconds")
                        + " 동안 방어 체계가 작동합니다.</gray>"
        ));
        return true;
    }

    private static void startFullOperationPresentation(ServerPlayer player) {
        player.setTicksFrozen(player.getTicksRequiredToFreeze());
        kim.biryeong.semiontd.util.SemionPlayerPackets.playSound(player,
                SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS,
                1.25F,
                1.05F
        );
        kim.biryeong.semiontd.util.SemionPlayerPackets.playSound(player,
                SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.AMBIENT,
                1.0F,
                FULL_OPERATION_SOUND_PITCH
        );
        kim.biryeong.semiontd.util.SemionPlayerPackets.playSound(player,
                SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(),
                SoundSource.AMBIENT,
                1.0F,
                FULL_OPERATION_SOUND_PITCH
        );
    }

    private static void stopFullOperationPresentation(ServerPlayer player) {
        player.setTicksFrozen(0);
        player.connection.send(fullOperationAmbientStopPacket());
    }

    static ClientboundStopSoundPacket fullOperationAmbientStopPacket() {
        return new ClientboundStopSoundPacket(FULL_OPERATION_AMBIENT_SOUND_ID, SoundSource.AMBIENT);
    }

    static float fullOperationSoundPitch() {
        return FULL_OPERATION_SOUND_PITCH;
    }

    private static void updateReadiness(PlayerLane lane, FrostFullOperationState state) {
        if (!state.waveActive() || state.ready() || state.usedThisWave()
                || state.totalActivations() < FrostBalance.fullOperationRequiredActivations()
                || !hasBothDevices(lane)
                || eruptionChill(lane) + 1.0E-9 < FrostBalance.fullOperationEruptionChill()) {
            return;
        }
        state.markReady();
        onlinePlayer(lane).ifPresent(player -> {
            player.getInventory().setItem(ACTIVATION_SLOT, activationItem());
            player.containerMenu.sendAllDataToRemote();
            player.sendSystemMessage(SemionText.prefixedMini(
                    "<aqua>완전 가동 준비 완료!</aqua> <gray>9번 슬롯의 얼음을 우클릭하세요.</gray>"
            ));
        });
    }

    private static boolean hasBothDevices(PlayerLane lane) {
        boolean emission = false;
        boolean eruption = false;
        for (Tower tower : lane.towers()) {
            if (!lane.ownerPlayer().equals(tower.ownerPlayer()) || tower.isDestroyed(lane)) {
                continue;
            }
            emission |= FrostTowers.isEmissionCoolingDevice(tower.type());
            eruption |= FrostTowers.isEruptionCoolingDevice(tower.type());
        }
        return emission && eruption;
    }

    private static double eruptionChill(PlayerLane lane) {
        return lane.towers().stream()
                .filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()))
                .filter(FrostEruptionCoolingTower.class::isInstance)
                .map(FrostEruptionCoolingTower.class::cast)
                .mapToDouble(FrostEruptionCoolingTower::operationChill)
                .max()
                .orElse(0.0);
    }

    private static void applyChillPulse(PlayerLane lane) {
        SemionTowerEntity source = operationSource(lane);
        if (source == null) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                CHILL_PULSE_ID,
                source,
                Math.max(0.01, FrostBalance.fullOperationAreaRadius()),
                AreaVfxSpec.none()
        );
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            var result = FrostMonsterStates.applyChill(source, target, FrostBalance.fullOperationChillPerPulse());
            return result.currentChill() > result.previousChill() || result.becameRefrigerated()
                    ? AreaEffectOutcome.APPLIED
                    : AreaEffectOutcome.UNCHANGED;
        });
    }

    public static boolean showFullOperationVfx(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return false;
        }
        SemionTowerEntity source = operationSource(lane);
        if (source == null || source.runtimeTower() == null) {
            return false;
        }
        List<Vec3> towerCenters = lane.towers().stream()
                .filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> !tower.isDestroyed(lane))
                .map(tower -> towerEntity(lane.arenaWorld(), tower).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(SemionTowerEntity::isAlive)
                .map(SemionTowerEntity::position)
                .toList();
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(source.runtimeTower(), "frost_full_operation"),
                AreaVfxStyles.BUFF,
                source.position(),
                Math.max(0.01, FrostBalance.fullOperationAreaRadius()),
                towerCenters,
                towerCenters.size(),
                towerCenters.size(),
                0
        );
        return true;
    }

    private static SemionTowerEntity operationSource(PlayerLane lane) {
        ServerLevel level = lane.arenaWorld();
        if (level == null) {
            return null;
        }
        return lane.towers().stream()
                .filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> FrostTowers.isEruptionCoolingDevice(tower.type())
                        || FrostTowers.isEmissionCoolingDevice(tower.type()))
                .map(tower -> towerEntity(level, tower).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(SemionTowerEntity::isAlive)
                .findFirst()
                .orElse(null);
    }

    private static java.util.Optional<SemionTowerEntity> towerEntity(ServerLevel level, Tower tower) {
        if (!(tower instanceof EntityBackedTower entityBacked) || entityBacked.entityId().isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(level.getEntity(entityBacked.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    public static boolean isActive(UUID ownerPlayer, long gameTime) {
        if (ownerPlayer == null) {
            return false;
        }
        FrostFullOperationState state = STATES.get(ownerPlayer);
        return state != null && state.active() && gameTime < state.activeUntilTick();
    }

    public static double displayedDamageReduction(UUID ownerPlayer, long gameTime, double normalReduction) {
        return isActive(ownerPlayer, gameTime)
                ? Math.max(0.0, FrostBalance.fullOperationDamageReduction())
                : normalReduction;
    }

    private static ItemStack activationItem() {
        ItemStack stack = new ItemStack(Items.ICE);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putBoolean(ACTIVATION_ITEM_KEY, true));
        stack.set(DataComponents.CUSTOM_NAME, ACTIVATION_NAME);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                SemionText.mini("<gray>[" + format(FrostBalance.fullOperationDurationTicks(), "seconds")
                        + " 동안 자신의 모든 타워의 받는 피해 감소가 "
                        + format(FrostBalance.fullOperationDamageReduction(), "percent")
                        + "로 고정되며 "
                        + format(FrostBalance.fullOperationChillIntervalTicks(), "seconds")
                        + "마다 모든 적에게 한기 "
                        + format(FrostBalance.fullOperationChillPerPulse(), "percent")
                        + "가 부여됨.</gray>"),
                SemionText.mini("<gray>지속 시간 동안 본인의 모든 타워가 입히는 공격력 피해가 "
                        + format(FrostBalance.fullOperationFixedAttackDamage(), "number")
                        + "로 고정됨. ]</gray>")
        )));
        return stack;
    }

    static ItemStack activationItemForTest() {
        return activationItem();
    }

    private static void refreshFullOperationEffects(PlayerLane lane, FrostFullOperationState state, long gameTime) {
        ServerLevel level = lane.arenaWorld();
        int remainingTicks = (int) Math.max(1L, state.activeUntilTick() - gameTime);
        double reduction = Math.max(0.0, FrostBalance.fullOperationDamageReduction());
        for (Tower tower : lane.towers()) {
            if (!lane.ownerPlayer().equals(tower.ownerPlayer()) || tower.isDestroyed(lane)) {
                continue;
            }
            towerEntity(level, tower).ifPresent(entity -> entity.refreshTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_REDUCTION,
                    DAMAGE_REDUCTION_ID,
                    reduction,
                    remainingTicks
            ));
        }
    }

    private static void clearFullOperationEffects(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (!lane.ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            towerEntity(lane.arenaWorld(), tower).ifPresent(entity -> entity.refreshTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_REDUCTION,
                    DAMAGE_REDUCTION_ID,
                    0.0,
                    1
            ));
        }
    }

    public static void clearActivationItem(ServerPlayer player) {
        if (clearActivationItems(player.getInventory())) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    static boolean clearActivationItems(Container inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isActivationItem(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }

    private static java.util.Optional<ServerPlayer> onlinePlayer(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || lane.arenaWorld().getServer() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                lane.arenaWorld().getServer().getPlayerList().getPlayer(lane.ownerPlayer()));
    }

    enum TriggerFamily {
        DONGTAE,
        FROZEN_FOOD,
        ICEBOX
    }

}
