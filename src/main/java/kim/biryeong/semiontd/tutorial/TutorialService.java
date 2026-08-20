package kim.biryeong.semiontd.tutorial;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

public final class TutorialService {
    public static final long TRAINING_DIAMONDS = 200L;
    private static final int NARRATION_INTERVAL_TICKS = 50;

    private static final Map<Stage, String> OBJECTIVES = new EnumMap<>(Stage.class);

    static {
        OBJECTIVES.put(Stage.INTRO, "1/10 · 세미온 TD 이해");
        OBJECTIVES.put(Stage.PLACE_PIG, "2/10 · 앞줄에 돼지 타워 설치");
        OBJECTIVES.put(Stage.PLACE_WOLF, "3/10 · 돼지 뒤에 늑구 타워 설치");
        OBJECTIVES.put(Stage.DEFEND_FIRST_WAVE, "4/10 · 첫 웨이브 방어");
        OBJECTIVES.put(Stage.UPGRADE_TOWER, "5/10 · 설치한 타워 1회 업그레이드");
        OBJECTIVES.put(Stage.BUY_INCOME_MONSTER, "6/10 · 에메랄드로 인컴 몹 구매");
        OBJECTIVES.put(Stage.LEARN_INCOME, "7/10 · 오른 수입 확인");
        OBJECTIVES.put(Stage.UPGRADE_EMERALD_PRODUCTION, "8/10 · 다이아로 인컴 업그레이드");
        OBJECTIVES.put(Stage.DEFEND_INCOME_MONSTER, "9/10 · 인컴 몹이 포함된 웨이브 방어");
        OBJECTIVES.put(Stage.FINAL_DEFENSE, "10/10 · 최종 방어선과 보스 설명 확인");
        OBJECTIVES.put(Stage.COMPLETE, "튜토리얼 완료 · /튜토리얼 종료");
        OBJECTIVES.put(Stage.FAILED, "보스가 쓰러짐 · /튜토리얼 다시");
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    public void start(MinecraftServer server, UUID playerId, SemionGame game) {
        stop(playerId);
        Session session = new Session();
        sessions.put(playerId, session);
        game.players().get(playerId).economy().addDiamond(TRAINING_DIAMONDS);
        showStage(server, playerId, session, game);
    }

    public void tick(MinecraftServer server, UUID playerId, SemionGame game) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            updateBossBar(player, session);
        }
        boolean terminal = session.stage == Stage.COMPLETE || session.stage == Stage.FAILED;
        if (!terminal && game.phase() == RoundPhase.ENDED) {
            advance(server, playerId, session, game, Stage.FAILED);
            return;
        }
        if (tickNarration(player, session)) {
            if (!terminal) {
                game.keepTutorialPrepareOpen();
            }
            return;
        }
        if (terminal) {
            return;
        }
        if (session.stage == Stage.INTRO) {
            advance(server, playerId, session, game, Stage.PLACE_PIG);
            return;
        }
        if (session.stage == Stage.LEARN_INCOME) {
            advance(server, playerId, session, game, Stage.UPGRADE_EMERALD_PRODUCTION);
            return;
        }
        if (isWaveDefense(session.stage) && !session.waveCountdownStarted) {
            game.startTutorialWaveCountdown();
            session.waveCountdownStarted = true;
        }
        if (holdsPrepareOpen(session.stage)) {
            game.keepTutorialPrepareOpen();
        }

        List<BuildAction> actions = game.buildGuideService()
                .map(service -> service.recordedActions(playerId))
                .orElseGet(List::of);
        switch (session.stage) {
            case PLACE_PIG -> latestAction(actions, BuildActionType.TOWER_PLACE, AnimalTowers.T1_PIG_TOWER.id())
                    .flatMap(action -> resolvePosition(game, playerId, action))
                    .ifPresent(position -> {
                        session.pigPosition = position;
                        advance(server, playerId, session, game, Stage.PLACE_WOLF);
                    });
            case PLACE_WOLF -> latestAction(actions, BuildActionType.TOWER_PLACE, AnimalTowers.T1_WOLF_TOWER.id())
                    .flatMap(action -> resolvePosition(game, playerId, action))
                    .ifPresent(position -> {
                        PlayerLane lane = game.playerLane(playerId).orElse(null);
                        if (lane != null && isBehind(lane, session.pigPosition, position)) {
                            session.defenseRound = game.currentRound();
                            advance(server, playerId, session, game, Stage.DEFEND_FIRST_WAVE);
                        } else if (!position.equals(session.rejectedWolfPosition)) {
                            session.rejectedWolfPosition = position;
                            if (player != null) {
                                player.sendSystemMessage(SemionText.prefixedMini(
                                        "<red>늑구 타워가 돼지보다 앞에 있습니다.</red> 판매한 뒤 <yellow>팀 보스 쪽</yellow>에 다시 설치하세요."
                                ));
                            }
                        }
                    });
            case DEFEND_FIRST_WAVE -> {
                if (game.hasClearedRound(playerId, session.defenseRound)) {
                    advance(server, playerId, session, game, Stage.UPGRADE_TOWER);
                } else if (game.currentRound() > session.defenseRound
                        && game.restartTutorialRound(server, session.defenseRound)) {
                    showLines(server, playerId, session, firstDefenseRetryLines(session.defenseRound));
                    session.waveCountdownStarted = true;
                }
            }
            case UPGRADE_TOWER -> {
                if (hasAction(actions, BuildActionType.TOWER_UPGRADE)) {
                    advance(server, playerId, session, game, Stage.BUY_INCOME_MONSTER);
                }
            }
            case BUY_INCOME_MONSTER -> latestAction(actions, BuildActionType.SUMMON).ifPresent(action -> {
                session.incomeDefenseRound = action.scheduledRound();
                session.incomeGain = action.incomeGain();
                session.currentIncome = game.players().get(playerId).economy().income();
                advance(server, playerId, session, game, Stage.LEARN_INCOME);
            });
            case UPGRADE_EMERALD_PRODUCTION -> {
                if (hasAction(actions, BuildActionType.EMERALD_PRODUCTION_UPGRADE)) {
                    advance(server, playerId, session, game, Stage.DEFEND_INCOME_MONSTER);
                }
            }
            case DEFEND_INCOME_MONSTER -> {
                if (game.hasClearedRound(playerId, session.incomeDefenseRound)) {
                    advance(server, playerId, session, game, Stage.FINAL_DEFENSE);
                }
            }
            case INTRO, LEARN_INCOME, FINAL_DEFENSE, COMPLETE, FAILED -> {
            }
        }
    }

    public boolean showCurrent(MinecraftServer server, UUID playerId, SemionGame game) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        showStage(server, playerId, session, game);
        return true;
    }

    public boolean complete(MinecraftServer server, UUID playerId, SemionGame game) {
        Session session = sessions.get(playerId);
        if (session == null || session.stage != Stage.FINAL_DEFENSE || hasPendingNarration(session)) {
            return false;
        }
        advance(server, playerId, session, game, Stage.COMPLETE);
        return true;
    }

    public Optional<Stage> stage(UUID playerId) {
        Session session = sessions.get(playerId);
        return Optional.ofNullable(session == null ? null : session.stage);
    }

    public boolean isActive(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void stop(UUID playerId) {
        Session session = playerId == null ? null : sessions.remove(playerId);
        if (session != null && session.bossBar != null) {
            session.bossBar.removeAllPlayers();
        }
    }

    public enum Stage {
        INTRO,
        PLACE_PIG,
        PLACE_WOLF,
        DEFEND_FIRST_WAVE,
        UPGRADE_TOWER,
        BUY_INCOME_MONSTER,
        LEARN_INCOME,
        UPGRADE_EMERALD_PRODUCTION,
        DEFEND_INCOME_MONSTER,
        FINAL_DEFENSE,
        COMPLETE,
        FAILED
    }

    private static boolean holdsPrepareOpen(Stage stage) {
        return stage == Stage.PLACE_PIG
                || stage == Stage.PLACE_WOLF
                || stage == Stage.UPGRADE_TOWER
                || stage == Stage.BUY_INCOME_MONSTER
                || stage == Stage.UPGRADE_EMERALD_PRODUCTION;
    }

    private static boolean isWaveDefense(Stage stage) {
        return stage == Stage.DEFEND_FIRST_WAVE || stage == Stage.DEFEND_INCOME_MONSTER;
    }

    private static boolean hasAction(List<BuildAction> actions, BuildActionType type) {
        return actions.stream().anyMatch(action -> action.type() == type);
    }

    private static Optional<BuildAction> latestAction(List<BuildAction> actions, BuildActionType type) {
        return actions.stream().filter(action -> action.type() == type).reduce((first, second) -> second);
    }

    private static Optional<BuildAction> latestAction(List<BuildAction> actions, BuildActionType type, String subjectId) {
        return actions.stream()
                .filter(action -> action.type() == type && subjectId.equals(action.subjectId()))
                .reduce((first, second) -> second);
    }

    private static Optional<GridPosition> resolvePosition(SemionGame game, UUID playerId, BuildAction action) {
        return game.buildGuideService().flatMap(service -> service.resolveActionPosition(game, playerId, action));
    }

    private static boolean isBehind(PlayerLane lane, GridPosition pig, GridPosition wolf) {
        if (pig == null || wolf == null) {
            return false;
        }
        return lane.laneLayout().progressAt(center(wolf)) > lane.laneLayout().progressAt(center(pig));
    }

    private static Vec3 center(GridPosition position) {
        return new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    private void advance(MinecraftServer server, UUID playerId, Session session, SemionGame game, Stage next) {
        session.stage = next;
        showStage(server, playerId, session, game);
    }

    private void showStage(MinecraftServer server, UUID playerId, Session session, SemionGame game) {
        showLines(server, playerId, session, stageLines(session.stage, game, playerId, session));
    }

    private void showLines(MinecraftServer server, UUID playerId, Session session, List<String> lines) {
        session.narrationLines = lines;
        session.narrationIndex = 0;
        session.narrationTicks = 0;
        session.completionLinkSent = false;
        session.waveCountdownStarted = false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        updateBossBar(player, session);
        sendNextNarrationLine(player, session);
    }

    private static boolean tickNarration(ServerPlayer player, Session session) {
        if (session.narrationTicks > 0) {
            session.narrationTicks--;
            return true;
        }
        if (session.narrationIndex < session.narrationLines.size()) {
            if (player != null) {
                sendNextNarrationLine(player, session);
            }
            return true;
        }
        if (session.stage == Stage.FINAL_DEFENSE && !session.completionLinkSent && player != null) {
            player.sendSystemMessage(Component.literal("[설명을 확인하고 튜토리얼 완료]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand("/튜토리얼 완료"))));
            session.completionLinkSent = true;
        }
        return false;
    }

    private static void sendNextNarrationLine(ServerPlayer player, Session session) {
        if (session.narrationIndex == 0) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MUSIC, 1.0F, 1.2F);
        }
        player.sendSystemMessage(SemionText.prefixedMini(session.narrationLines.get(session.narrationIndex++)));
        session.narrationTicks = NARRATION_INTERVAL_TICKS;
    }

    private static boolean hasPendingNarration(Session session) {
        return session.narrationTicks > 0 || session.narrationIndex < session.narrationLines.size();
    }

    private void updateBossBar(ServerPlayer player, Session session) {
        Component title = Component.literal(OBJECTIVES.get(session.stage));
        if (session.bossBar == null) {
            session.bossBar = new ServerBossEvent(title, color(session.stage), BossEvent.BossBarOverlay.PROGRESS);
        }
        session.bossBar.setName(title);
        session.bossBar.setColor(color(session.stage));
        session.bossBar.setProgress(progress(session.stage));
        session.bossBar.addPlayer(player);
    }

    private static BossEvent.BossBarColor color(Stage stage) {
        return switch (stage) {
            case COMPLETE -> BossEvent.BossBarColor.GREEN;
            case FAILED -> BossEvent.BossBarColor.RED;
            default -> BossEvent.BossBarColor.YELLOW;
        };
    }

    private static float progress(Stage stage) {
        return switch (stage) {
            case INTRO -> 0.05F;
            case PLACE_PIG -> 1.0F / 10.0F;
            case PLACE_WOLF -> 2.0F / 10.0F;
            case DEFEND_FIRST_WAVE -> 3.0F / 10.0F;
            case UPGRADE_TOWER -> 4.0F / 10.0F;
            case BUY_INCOME_MONSTER -> 5.0F / 10.0F;
            case LEARN_INCOME -> 6.0F / 10.0F;
            case UPGRADE_EMERALD_PRODUCTION -> 7.0F / 10.0F;
            case DEFEND_INCOME_MONSTER -> 8.0F / 10.0F;
            case FINAL_DEFENSE -> 9.0F / 10.0F;
            case COMPLETE -> 1.0F;
            case FAILED -> 0.0F;
        };
    }

    private static List<String> stageLines(Stage stage, SemionGame game, UUID playerId, Session session) {
        return switch (stage) {
            case INTRO -> List.of(
                    "<gold><bold>튜토리얼 1/10 · 세미온 TD</bold></gold>",
                    "세미온 TD는 타워로 내 라인을 막고, 인컴 몹으로 상대 라인을 압박하는 타워 디펜스입니다.",
                    "웨이브마다 몹이 스폰 지점에서 팀 보스 쪽으로 이동합니다.",
                    "내 타워로 몹을 처치하고, 상대보다 오래 팀 보스를 지키면 승리합니다.",
                    "이제 타워 설치부터 직접 연습합니다."
            );
            case PLACE_PIG -> List.of(
                    "<gold><bold>튜토리얼 2/10 · 앞줄 탱커</bold></gold>",
                    "<aqua>다이아</aqua>는 타워를 설치하고 업그레이드할 때 씁니다.",
                    "돼지 타워는 체력과 공격 우선순위가 높아 앞줄에서 몹의 공격을 받습니다.",
                    "<white>과제:</white> 라인 앞쪽 칸에 서서 <aqua>나침반</aqua>을 우클릭하고 <yellow>돼지 타워</yellow>를 설치하세요."
            );
            case PLACE_WOLF -> List.of(
                    "<gold><bold>튜토리얼 3/10 · 뒤줄 딜러</bold></gold>",
                    "늑구 타워는 늑대 딜러입니다. 돼지가 버티는 동안 뒤에서 공격합니다.",
                    "몬스터는 스폰 지점에서 팀 보스 쪽으로 이동합니다.",
                    "<white>과제:</white> 돼지보다 <yellow>팀 보스 쪽</yellow> 칸에 서서 <aqua>나침반</aqua>으로 <yellow>늑구 타워</yellow>를 설치하세요."
            );
            case DEFEND_FIRST_WAVE -> List.of(
                    "<gold><bold>튜토리얼 4/10 · 첫 방어</bold></gold>",
                    "돼지가 공격을 받아 버티고, 뒤의 늑구가 긴 사거리로 공격합니다.",
                    "몹이 라인 끝을 통과하면 방어 실패로 기록되고 최종 방어선으로 이동합니다.",
                    "<white>과제:</white> <yellow>%d라운드</yellow>의 모든 몹을 막으세요.".formatted(game.currentRound())
            );
            case UPGRADE_TOWER -> List.of(
                    "<gold><bold>튜토리얼 5/10 · 타워 업그레이드</bold></gold>",
                    "업그레이드는 다이아를 쓰고 타워의 능력치나 기능을 강화합니다.",
                    "<white>과제:</white> 돼지나 늑구 타워 위에 서서 나침반을 우클릭한 뒤 업그레이드 하나를 고르세요."
            );
            case BUY_INCOME_MONSTER -> List.of(
                    "<gold><bold>튜토리얼 6/10 · 인컴 몹 구매</bold></gold>",
                    "<green>에메랄드</green>는 인컴 몹을 살 때 씁니다.",
                    "화면의 <dark_green>에메랄드/초</dark_green>만큼 매초 에메랄드를 받습니다.",
                    "실제 경기에서는 상대에게 보내지만, 여기서는 방어 연습을 위해 내 라인으로 보냅니다.",
                    "<white>과제:</white> <light_purple>메아리 조각</light_purple>을 우클릭하고 구매 가능한 인컴 몹 하나를 사세요."
            );
            case LEARN_INCOME -> List.of(
                    "<gold><bold>튜토리얼 7/10 · 수입</bold></gold>",
                    "방금 구매로 <yellow>수입이 %d</yellow> 올랐습니다.".formatted(session.incomeGain),
                    "현재 수입은 <yellow>%d</yellow>이며, 라운드가 끝날 때 그만큼 다이아를 받습니다.".formatted(session.currentIncome),
                    "인컴 몹을 더 사면 다음 라운드부터 받을 다이아가 계속 늘어납니다."
            );
            case UPGRADE_EMERALD_PRODUCTION -> List.of(
                    "<gold><bold>튜토리얼 8/10 · 인컴 업그레이드</bold></gold>",
                    "관리 창의 <green>인컴 업그레이드</green>는 다이아를 써서 에메랄드/초를 올립니다.",
                    "에메랄드가 빨리 차면 인컴 몹을 더 자주 사고, 수입도 더 빠르게 키울 수 있습니다.",
                    "<white>과제:</white> 나침반을 우클릭하고 <green>인컴 업그레이드</green>를 누르세요. 현재 비용은 <aqua>%d 다이아</aqua>입니다."
                            .formatted(nextProductionUpgradeCost(game, playerId))
            );
            case DEFEND_INCOME_MONSTER -> List.of(
                    "<gold><bold>튜토리얼 9/10 · 인컴 몹 방어</bold></gold>",
                    "방금 산 인컴 몹이 일반 웨이브 몹과 함께 내 라인으로 들어옵니다.",
                    "앞줄 돼지가 버티는 동안 뒤의 늑구가 안전하게 공격하도록 배치를 확인하세요.",
                    "<white>과제:</white> <yellow>%d라운드</yellow>의 모든 몹을 막으세요.".formatted(game.currentRound())
            );
            case FINAL_DEFENSE -> List.of(
                    "<gold><bold>튜토리얼 10/10 · 최종 방어선과 보스</bold></gold>",
                    "라인에서 막지 못한 몹은 최종 방어선으로 이동합니다.",
                    "남은 타워도 팀 보스 앞으로 모여 마지막 전투를 치릅니다.",
                    "몹이 팀 보스의 체력을 0으로 만들면 팀이 탈락합니다.",
                    "웨이브 보스는 일반 몹보다 훨씬 강합니다. 보스전에 쓸 다이아와 단일 대상 화력을 준비하세요.",
                    "화면의 팀 보스 체력을 확인한 뒤 아래 완료 버튼을 누르세요."
            );
            case COMPLETE -> List.of(
                    "<green><bold>튜토리얼 완료</bold></green>",
                    "돼지·늑구 배치부터 타워 강화와 인컴 운영까지 직접 수행했습니다.",
                    "현재 연습장을 계속 쓰거나 <yellow>/튜토리얼 종료</yellow>로 로비에 돌아가세요."
            );
            case FAILED -> List.of(
                    "<red><bold>튜토리얼 실패</bold></red>",
                    "팀 보스가 쓰러졌습니다. <yellow>/튜토리얼 다시</yellow>로 처음부터 시작하세요."
            );
        };
    }

    private static List<String> firstDefenseRetryLines(int round) {
        return List.of(
                "<red><bold>%d라운드 방어 실패</bold></red>".formatted(round),
                "설치한 타워와 자원은 유지한 채 %d라운드 준비 단계로 돌아왔습니다.".formatted(round),
                "남은 다이아로 돼지나 늑구 타워를 더 설치하세요.",
                "<white>과제:</white> 준비를 마친 뒤 <yellow>%d라운드</yellow>의 모든 몹을 막으세요.".formatted(round)
        );
    }

    private static long nextProductionUpgradeCost(SemionGame game, UUID playerId) {
        var player = game.players().get(playerId);
        if (player == null) {
            return 0L;
        }
        int count = player.economy().emeraldProductionUpgradeCount();
        return game.economyConfig().gasProduction().upgradeCost(count);
    }

    private static final class Session {
        private Stage stage = Stage.INTRO;
        private int defenseRound = 1;
        private int incomeDefenseRound = 1;
        private long incomeGain;
        private long currentIncome;
        private GridPosition pigPosition;
        private GridPosition rejectedWolfPosition;
        private List<String> narrationLines = List.of();
        private int narrationIndex;
        private int narrationTicks;
        private boolean completionLinkSent;
        private boolean waveCountdownStarted;
        private ServerBossEvent bossBar;
    }
}
