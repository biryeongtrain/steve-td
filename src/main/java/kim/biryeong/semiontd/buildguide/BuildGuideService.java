package kim.biryeong.semiontd.buildguide;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import xyz.nucleoid.map_templates.BlockBounds;

public final class BuildGuideService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private BuildGuideStore store;
    private final Map<UUID, Recording> activeRecordings = new HashMap<>();
    private final Map<UUID, Recording> lastRecordings = new HashMap<>();
    private final Map<UUID, String> trackedCodes = new HashMap<>();

    public BuildGuideService(Path storePath) {
        this.store = new BuildGuideStore(storePath);
    }

    public void configure(Path storePath) {
        this.store = new BuildGuideStore(storePath);
        this.trackedCodes.clear();
        this.activeRecordings.clear();
        this.lastRecordings.clear();
    }

    public void startMatch(SemionGame game) {
        activeRecordings.clear();
        if (game == null) {
            return;
        }
        lastRecordings.clear();
        for (SemionPlayer player : game.players().values()) {
            String jobId = player.job().map(job -> job.id().toString()).orElse("");
            activeRecordings.put(player.uuid(), new Recording(player.uuid(), player.name(), jobId, new ArrayList<>(), false, game.currentRound()));
        }
    }

    public void finishMatch(SemionGame game, int finalRound) {
        if (activeRecordings.isEmpty()) {
            return;
        }
        lastRecordings.clear();
        for (Recording recording : activeRecordings.values()) {
            lastRecordings.put(recording.playerId(), recording.withEnded(Math.max(1, finalRound)));
        }
        activeRecordings.clear();
    }

    public Optional<BuildGuide> publishLastRecording(UUID playerId, String title) {
        Recording recording = lastRecordings.get(playerId);
        if (recording == null || !recording.ended() || recording.actions().isEmpty()) {
            return Optional.empty();
        }
        String code = nextCode();
        BuildGuide guide = new BuildGuide(
                code,
                title,
                recording.playerId(),
                recording.playerName(),
                recording.jobId(),
                recording.finalRound(),
                Instant.now().toEpochMilli(),
                BuildGuide.VISIBILITY_PRIVATE,
                recording.actions()
        );
        return Optional.of(store.put(guide));
    }

    public BuildGuide saveDebugGuide(UUID playerId, String playerName, String jobId, int finalRound, List<BuildAction> actions) {
        return saveDebugGuide(
                "DEBUG1",
                "디버그 빌드 시각 테스트",
                playerId,
                playerName,
                jobId,
                finalRound,
                actions
        );
    }

    public BuildGuide saveDebugGuide(
            String code,
            String title,
            UUID playerId,
            String playerName,
            String jobId,
            int finalRound,
            List<BuildAction> actions
    ) {
        BuildGuide guide = new BuildGuide(
                code,
                title,
                playerId,
                playerName,
                jobId,
                finalRound,
                Instant.now().toEpochMilli(),
                BuildGuide.VISIBILITY_PUBLIC,
                actions
        );
        return store.put(guide);
    }

    public Optional<BuildGuide> find(String code) {
        return store.find(code);
    }

    public Optional<BuildGuide> findViewable(String code, UUID playerId) {
        return find(code)
                .filter(guide -> !isDebugGuide(guide))
                .filter(guide -> guide.viewableBy(playerId));
    }

    public List<BuildGuide> publicGuides() {
        return store.publicGuides().stream()
                .filter(guide -> !isDebugGuide(guide))
                .filter(BuildGuide::isPublic)
                .toList();
    }

    public List<BuildGuide> debugPublicGuides() {
        return store.publicGuides();
    }

    public List<BuildGuide> myGuides(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return store.publicGuides().stream()
                .filter(guide -> !isDebugGuide(guide))
                .filter(guide -> guide.ownedBy(playerId))
                .toList();
    }

    public List<BuildGuide> recentGuides(UUID playerId, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .filter(guide -> !isDebugGuide(guide))
                .filter(guide -> guide.viewableBy(playerId))
                .toList();
    }

    public List<BuildGuide> debugRecentGuides(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public static boolean isDebugGuide(BuildGuide guide) {
        return guide != null && BuildGuideStore.normalizeCode(guide.code()).startsWith("DEBUG");
    }

    public boolean track(UUID playerId, String code) {
        Optional<BuildGuide> guide = find(code).filter(found -> found.viewableBy(playerId));
        if (playerId == null || guide.isEmpty()) {
            return false;
        }
        trackedCodes.put(playerId, guide.get().code());
        return true;
    }

    public void clearTracked(UUID playerId) {
        trackedCodes.remove(playerId);
    }

    public Optional<BuildGuide> trackedGuide(UUID playerId) {
        String code = trackedCodes.get(playerId);
        return code == null ? Optional.empty() : find(code).filter(guide -> guide.viewableBy(playerId));
    }

    public Optional<BuildGuide> setVisibility(UUID playerId, String code, String visibility) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<BuildGuide> guide = find(code).filter(found -> found.ownedBy(playerId));
        if (guide.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(store.put(guide.get().withVisibility(visibility)));
    }

    public boolean delete(UUID playerId, String code) {
        if (playerId == null) {
            return false;
        }
        Optional<BuildGuide> guide = find(code).filter(found -> found.ownedBy(playerId));
        if (guide.isEmpty()) {
            return false;
        }
        String normalizedCode = BuildGuideStore.normalizeCode(guide.get().code());
        boolean removed = store.remove(normalizedCode);
        if (removed) {
            trackedCodes.entrySet().removeIf(entry -> normalizedCode.equals(BuildGuideStore.normalizeCode(entry.getValue())));
        }
        return removed;
    }

    public List<BuildAction> trackedActionsForRound(UUID playerId, int round) {
        return trackedGuide(playerId)
                .map(guide -> actionsForRound(guide, round))
                .orElseGet(List::of);
    }

    public boolean isRecommendedTower(SemionGame game, UUID playerId, int round, GridPosition position, String towerId) {
        if (position == null || towerId == null) {
            return false;
        }
        return trackedActionsForRound(playerId, round).stream()
                .anyMatch(action -> action.type() == BuildActionType.TOWER_PLACE
                        && towerId.equals(action.subjectId())
                        && position.equals(resolveActionPosition(game, playerId, action).orElse(null)));
    }

    public boolean isRecommendedUpgrade(SemionGame game, UUID playerId, int round, GridPosition position, String upgradeId) {
        if (position == null || upgradeId == null) {
            return false;
        }
        return trackedActionsForRound(playerId, round).stream()
                .anyMatch(action -> action.type() == BuildActionType.TOWER_UPGRADE
                        && upgradeId.equals(action.subjectId())
                        && position.equals(resolveActionPosition(game, playerId, action).orElse(null)));
    }

    public void recordTowerPlacement(SemionGame game, UUID playerId, String towerId, GridPosition position, long cost) {
        record(playerId, towerPlacementAction(game, playerId, towerId, position, cost));
    }

    public void recordTowerUpgrade(SemionGame game, UUID playerId, String upgradeId, GridPosition position, long cost) {
        record(playerId, towerUpgradeAction(game, playerId, upgradeId, position, cost));
    }

    public void recordSummon(SemionGame game, UUID playerId, String summonId, long cost, long incomeGain, TeamId targetTeam, int targetLaneId, int scheduledRound) {
        record(playerId, BuildAction.summon(
                game.currentRound(),
                summonId,
                cost,
                incomeGain,
                scheduledRound,
                targetTeam == null ? "" : targetTeam.name(),
                targetLaneId
        ));
    }

    public void recordEmeraldProductionUpgrade(SemionGame game, UUID playerId, int upgradeCount, long cost, long incomeGain) {
        record(playerId, BuildAction.emeraldProductionUpgrade(game.currentRound(), upgradeCount, cost, incomeGain));
    }

    public void onPreparePhaseStarted(net.minecraft.server.MinecraftServer server, SemionGame game, int round) {
        if (server == null || game == null) {
            return;
        }
        for (SemionPlayer activePlayer : game.players().values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(activePlayer.uuid());
            if (player == null) {
                continue;
            }
            Optional<BuildGuide> trackedGuide = trackedGuide(activePlayer.uuid());
            if (trackedGuide.isEmpty()) {
                continue;
            }
            List<BuildAction> actions = actionsForRound(trackedGuide.get(), round);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MUSIC, 1.0f, 1.2f);
            player.sendSystemMessage(SemionText.prefixedMini(roundSummary(game, activePlayer.uuid(), round, actions)));
            showRoundIndicators(player, game, activePlayer.uuid(), actions);
        }
    }

    public static List<BuildAction> actionsForRound(BuildGuide guide, int round) {
        if (guide == null) {
            return List.of();
        }
        return guide.actions().stream()
                .filter(action -> action.round() == round)
                .sorted(Comparator.comparing(BuildAction::type).thenComparing(BuildAction::subjectId))
                .toList();
    }

    public static Component towerButtonLabel(String displayName, boolean affordable, boolean recommended) {
        return Component.literal(displayName)
                .withStyle(style -> style
                        .withColor(recommended ? ChatFormatting.BLUE : (affordable ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .withBold(true));
    }

    public static String subjectDisplayName(BuildAction action) {
        if (action == null) {
            return "";
        }
        return switch (action.type()) {
            case TOWER_PLACE -> ProductionTowerCatalog.find(action.subjectId())
                    .map(entry -> entry.type().displayName())
                    .orElse(action.subjectId());
            case TOWER_UPGRADE -> ProductionTowerCatalog.findUpgrade(action.subjectId())
                    .map(TowerUpgradeOption::displayName)
                    .orElse(action.subjectId());
            case SUMMON -> SummonRegistry.find(action.subjectId())
                    .map(SummonMonsterType::displayName)
                    .orElse(action.subjectId());
            case EMERALD_PRODUCTION_UPGRADE -> action.subjectId() + "단계";
        };
    }

    public Optional<GridPosition> resolveActionPosition(SemionGame game, UUID playerId, BuildAction action) {
        if (action == null || action.position() == null) {
            return Optional.empty();
        }
        if (!action.hasLaneRelativePosition()) {
            return Optional.of(action.position());
        }
        return game.playerLane(playerId).map(lane -> toAbsolute(lane, action.position()));
    }

    private void showRoundIndicators(ServerPlayer player, SemionGame game, UUID playerId, List<BuildAction> actions) {
        for (BuildAction action : actions) {
            Optional<GridPosition> position = resolveActionPosition(game, playerId, action);
            if (action.type() == BuildActionType.TOWER_PLACE) {
                position.ifPresent(value -> BuildGuideIndicatorService.showPlacement(player, value));
            } else if (action.type() == BuildActionType.TOWER_UPGRADE) {
                position.flatMap(value -> towerAt(game, player.getUUID(), value))
                        .ifPresent(tower -> BuildGuideIndicatorService.showUpgradeTarget(player, tower));
            }
        }
    }

    private Optional<Tower> towerAt(SemionGame game, UUID playerId, GridPosition position) {
        if (position == null) {
            return Optional.empty();
        }
        return game.playerLane(playerId).map(lane -> lane.towerAt(position));
    }

    private String roundSummary(SemionGame game, UUID playerId, int round, List<BuildAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return "<aqua>이번 라운드 작업</aqua> <gray>#" + round + "</gray>: <gray>이번 라운드는 빌드 작업 없음</gray>";
        }
        StringBuilder body = new StringBuilder("<aqua>이번 라운드 작업</aqua> <gray>#").append(round).append("</gray>\n");
        for (BuildAction action : actions) {
            body.append("<dark_gray>-</dark_gray> ")
                    .append(actionLabel(action, resolveActionPosition(game, playerId, action).orElse(null)))
                    .append('\n');
        }
        return body.toString();
    }

    private static String actionLabel(BuildAction action, GridPosition resolvedPosition) {
        return switch (action.type()) {
            case TOWER_PLACE -> "<blue>타워 설치</blue> <white>" + subjectDisplayName(action) + "</white> <gray>"
                    + positionLabel(resolvedPosition) + "</gray>";
            case TOWER_UPGRADE -> "<blue>타워 업그레이드</blue> <white>" + subjectDisplayName(action) + "</white> <gray>"
                    + positionLabel(resolvedPosition) + "</gray>";
            case SUMMON -> "<light_purple>견제 소환</light_purple> <white>" + subjectDisplayName(action) + "</white>"
                    + " <gray>예약 라운드 " + action.scheduledRound() + "</gray>";
            case EMERALD_PRODUCTION_UPGRADE -> "<green>에메랄드 생산 업그레이드</green> <gray>"
                    + subjectDisplayName(action) + "</gray>";
        };
    }

    private static String positionLabel(GridPosition position) {
        if (position == null) {
            return "";
        }
        return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
    }

    private static BuildAction towerPlacementAction(SemionGame game, UUID playerId, String towerId, GridPosition position, long cost) {
        return game.playerLane(playerId)
                .map(lane -> BuildAction.towerPlaceRelative(game.currentRound(), towerId, toLaneRelative(lane, position), cost))
                .orElseGet(() -> BuildAction.towerPlace(game.currentRound(), towerId, position, cost));
    }

    private static BuildAction towerUpgradeAction(SemionGame game, UUID playerId, String upgradeId, GridPosition position, long cost) {
        return game.playerLane(playerId)
                .map(lane -> BuildAction.towerUpgradeRelative(game.currentRound(), upgradeId, toLaneRelative(lane, position), cost))
                .orElseGet(() -> BuildAction.towerUpgrade(game.currentRound(), upgradeId, position, cost));
    }

    private static GridPosition toLaneRelative(PlayerLane lane, GridPosition position) {
        if (position == null) {
            return null;
        }
        BlockPos origin = laneOrigin(lane);
        return new GridPosition(position.x() - origin.getX(), position.y() - origin.getY(), position.z() - origin.getZ());
    }

    private static GridPosition toAbsolute(PlayerLane lane, GridPosition relativePosition) {
        BlockPos origin = laneOrigin(lane);
        return new GridPosition(
                origin.getX() + relativePosition.x(),
                origin.getY() + relativePosition.y(),
                origin.getZ() + relativePosition.z()
        );
    }

    private static BlockPos laneOrigin(PlayerLane lane) {
        BlockBounds bounds = lane.laneLayout().laneArea();
        return bounds.min();
    }

    private void record(UUID playerId, BuildAction action) {
        Recording recording = activeRecordings.get(playerId);
        if (recording != null) {
            recording.actions().add(action);
        }
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = randomCode();
            if (!store.contains(code)) {
                return code;
            }
        }
        return randomCode() + Long.toString(System.currentTimeMillis(), 36).toUpperCase(java.util.Locale.ROOT);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private record Recording(
            UUID playerId,
            String playerName,
            String jobId,
            List<BuildAction> actions,
            boolean ended,
            int finalRound
    ) {
        private Recording withEnded(int finalRound) {
            return new Recording(playerId, playerName, jobId, List.copyOf(actions), true, finalRound);
        }
    }
}
