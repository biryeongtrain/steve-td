package kim.biryeong.semiontd.ui;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonShop;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class SemionDialogService {
    private static final int BODY_WIDTH = 360;
    private static final int BUTTON_WIDTH = 180;
    private static final int COMPACT_BUTTON_WIDTH = 118;
    private static final int SUMMON_BUTTON_WIDTH = 82;
    private static final int SUMMON_PAGE_SIZE = 25;

    public void showGameStatus(ServerPlayer player, SemionGame game) {
        StringBuilder body = new StringBuilder();
        body.append("라운드: ").append(game.currentRound()).append('\n');
        body.append("단계: ").append(phaseLabel(game.phase())).append('\n');
        body.append("플레이어: ").append(game.players().size()).append('\n');
        body.append("관전자: ").append(game.spectatorCount()).append('\n');
        body.append('\n');
        body.append("팀 상태\n");
        for (SemionTeam team : game.teams().values()) {
            if (!team.active()) {
                continue;
            }
            body.append(" - ")
                    .append(team.id().name())
                    .append(": ")
                    .append(team.eliminated() ? "탈락" : "생존")
                    .append(", 인원=")
                    .append(team.memberIds().size())
                    .append(", 보스HP=")
                    .append(Math.round(team.laneGroup().boss().health()))
                    .append('\n');
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer != null) {
            PlayerEconomy economy = semionPlayer.economy();
            body.append('\n');
            body.append("내 정보\n");
            body.append("팀: ").append(semionPlayer.teamId().name()).append('\n');
            body.append("라인: ").append(semionPlayer.laneId()).append('\n');
            body.append("다이아: ").append(economy.diamond()).append('\n');
            body.append("에메랄드: ").append(economy.emerald()).append('\n');
            body.append("수입: ").append(economy.income()).append('\n');
            body.append("에메랄드/초: ").append(economy.emeraldPerSec()).append('\n');
        }

        show(player, "세미온 TD 상태", body.toString());
    }

    public void showMatchResult(
            ServerPlayer player,
            MatchResult matchResult,
            Map<UUID, MatchProgressionReward> rewards
    ) {
        MutableComponent body = mutableMiniMessage("<gradient:#facc15:#22d3ee><bold>경기 결과</bold></gradient>\n")
                .append(miniMessage("<gray>최종 라운드</gray> <white>" + matchResult.finalRound() + "</white>\n"))
                .append(miniMessage("<gray>승리 팀</gray> <gold>" + teamList(matchResult.winningTeams()) + "</gold>\n\n"));

        List<MatchParticipantResult> orderedParticipants = matchResult.participants().stream()
                .sorted(participantComparator())
                .toList();

        body = body.append(miniMessage("<yellow><bold>참가자 기록</bold></yellow>\n"));
        int avatarOffset = 0;
        for (MatchParticipantResult participant : orderedParticipants) {
            var stats = participant.stats();
            body = body.append(avatarComponent(participant, avatarOffset++));
            body = body.append(miniMessage(" <white>" + participant.playerName() + "</white>"
                    + " <dark_gray>[</dark_gray>"
                    + (participant.winner() ? "<gold>승리</gold>" : "<gray>패배</gray>")
                    + " <aqua>" + participant.teamId().name() + "</aqua>"
                    + "<dark_gray>]</dark_gray>"
                    + " <gray>처치</gray> <red>" + stats.monsterKills() + "</red>"
                    + " <gray>수입</gray> <green>" + stats.finalIncome() + "</green>"
                    + " <gray>소환</gray> <light_purple>" + stats.summonedMonsters() + "</light_purple>"
                    + " <gray>처치다이아</gray> <aqua>" + stats.killMinerals() + "</aqua>"));
            MatchProgressionReward reward = rewards.get(participant.playerId());
            if (reward != null) {
                body = body.append(miniMessage(" <gray>꾸미기재화</gray> <gold>+" + reward.currencyAwarded() + "</gold>"));
            }
            body = body.append(Component.literal("\n"));
        }

        List<MatchParticipantResult> losers = matchResult.participants().stream()
                .filter(participant -> !participant.winner())
                .sorted(participantComparator())
                .toList();
        body = body.append(miniMessage("\n<red><bold>탈락 플레이어</bold></red>\n"));
        if (losers.isEmpty()) {
            body = body.append(miniMessage("<gray>- 없음</gray>\n"));
        } else {
            for (MatchParticipantResult participant : losers) {
                body = body.append(miniMessage("<gray>- </gray><white>" + participant.playerName() + "</white>"
                        + " <dark_gray>[</dark_gray><aqua>" + participant.teamId().name() + "</aqua><dark_gray>]</dark_gray>\n"));
            }
        }

        show(player, "세미온 TD 결과", body);
    }

    public void showLastResult(ServerPlayer player, MatchResult matchResult) {
        showMatchResult(player, matchResult, Map.of());
    }

    public void showJobSelection(ServerPlayer player, SemionGame game) {
        SemionJob currentJob = game.selectedJobOrDefault(player.getUUID());
        String body = "<gradient:#67e8f9:#a78bfa><bold>직업 선택</bold></gradient>\n"
                + "<gray>현재 선택</gray> <yellow>" + currentJob.displayName().getString() + "</yellow>\n"
                + "<gray>버튼에 마우스를 올려 직업 특성을 확인하세요.</gray>";
        ArrayList<ActionButton> actions = new ArrayList<>();
        for (SemionJob job : JobRegistry.all()) {
            actions.add(jobButton(job, currentJob.id().equals(job.id())));
        }

        showActions(player, "세미온 TD 직업", body, actions, 2);
    }

    public void showTowerControl(ServerPlayer player, SemionGame game) {
        var semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null) {
            show(player, "세미온 TD 타워", "<red>현재 게임 참가자가 아닙니다.</red>");
            return;
        }

        PlayerEconomy economy = semionPlayer.economy();
        long nextGasUpgradeCost = nextGasUpgradeCost(game, economy);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append("<gray>라인</gray> <white>").append(semionPlayer.teamId()).append(" #").append(semionPlayer.laneId()).append("</white>\n");
        body.append("<gray>다이아</gray> <aqua>").append(economy.diamond()).append("</aqua> ");
        body.append("<gray>에메랄드</gray> <green>").append(economy.emerald()).append("</green>\n");
        body.append("<gray>에메랄드/초</gray> <green>").append(economy.emeraldPerSec()).append("</green>");
        body.append(" <dark_gray>|</dark_gray> <gray>다음 인컴 업글</gray> <gold>")
                .append(nextGasUpgradeCost >= 0 ? nextGasUpgradeCost : "최대")
                .append("</gold>\n\n");

        Tower selectedTower = game.playerLane(player.getUUID())
                .flatMap(lane -> TowerPlacementPositions.resolveGrid(lane, player.blockPosition())
                        .map(lane::towerAt))
                .orElse(null);
        if (selectedTower != null) {
            body.append("<yellow>현재 위치 타워</yellow> <white>").append(selectedTower.type().displayName()).append("</white>\n");
            body.append("<gray>레벨</gray> <white>").append(selectedTower.level()).append("</white>");
            body.append(" <gray>체력</gray> <red>").append(Math.round(selectedTower.health())).append("</red>");
            body.append("<dark_gray>/</dark_gray><red>").append(Math.round(selectedTower.currentMaxHealth())).append("</red>");
            body.append(" <gray>판매 환불</gray> <gold>").append(selectedTower.sellRefundAmount()).append("</gold>\n\n");
        }

        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerService.availableTowers(game, player.getUUID());
        List<TowerUpgradeOption> upgrades = selectedTower == null
                ? List.of()
                : ProductionTowerService.availableUpgrades(game, player.getUUID(), player.blockPosition());
        if (!upgrades.isEmpty()) {
            body.append("<gray>아래 버튼에서 업그레이드를 선택하세요.</gray>\n");
        } else if (selectedTower != null) {
            body.append("<gray>현재 위치 타워는 더 이상 업그레이드할 수 없습니다.</gray>\n");
        } else if (entries.isEmpty()) {
            body.append("<red>사용할 수 있는 타워가 없습니다.</red>\n");
        } else {
            body.append("<gray>건설 후보</gray> <yellow>").append(entries.size()).append("</yellow>");
            body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (selectedTower == null) {
            for (ProductionTowerCatalog.CatalogEntry entry : entries) {
                long mineralCost = Math.max(0, entry.type().mineralCost());
                actions.add(towerButton(entry, mineralCost, economy.diamond() >= mineralCost));
            }
        } else {
            for (TowerUpgradeOption option : upgrades) {
                actions.add(actionButton(
                        option.displayName(),
                        "/semiontd tower upgrade " + option.id(),
                        upgradeTooltip(option),
                        COMPACT_BUTTON_WIDTH
                ));
            }
        }
        actions.add(actionButton("인컴 업그레이드", "/semiontd emeraldup", "에메랄드 생산량을 올립니다."));
        actions.add(actionButton("상태 보기", "/semiontd ui", "현재 경기 상태를 엽니다."));
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showTowerDetails(ServerPlayer player, SemionGame game, Tower tower) {
        if (tower == null) {
            show(player, "세미온 TD 타워", "<red>타워 정보를 찾을 수 없습니다.</red>");
            return;
        }

        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        boolean ownedByPlayer = tower.ownerPlayer().equals(player.getUUID());
        boolean sameLane = semionPlayer != null
                && semionPlayer.teamId() == tower.teamId()
                && semionPlayer.laneId() == tower.laneId();
        Optional<SemionTowerEntity> towerEntity = towerEntity(game, tower);
        double currentDamage = towerEntity
                .map(entity -> entity.attackDamageAmount(null))
                .orElseGet(() -> tower.modifyAttackDamage(null, null, tower.type().damage()));
        double currentRange = towerEntity
                .map(SemionTowerEntity::attackRange)
                .orElse(tower.type().range());
        int currentAttackIntervalTicks = towerEntity
                .map(SemionTowerEntity::attackIntervalTicks)
                .orElseGet(() -> tower.adjustAttackInterval(tower.type().attackIntervalTicks()));
        double currentMaxHealth = tower.currentMaxHealth();

        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 상세 정보</bold></gradient>\n");
        body.append("<white><bold>").append(tower.type().displayName()).append("</bold></white>\n");
        body.append("<gray>🏷 팀</gray> <white>").append(tower.teamId()).append("</white>");
        body.append(" <dark_gray>|</dark_gray> <gray>라인</gray> <yellow>#").append(tower.laneId()).append("</yellow>\n");
        body.append("<gray>📍 위치</gray> <white>")
                .append(tower.position().x()).append(", ")
                .append(tower.position().y()).append(", ")
                .append(tower.position().z()).append("</white>");
        body.append(" <dark_gray>|</dark_gray> <gray>⭐ 레벨</gray> <gold>").append(tower.level()).append("</gold>\n");
        body.append("<red>❤ 체력 ").append(Math.round(tower.health())).append("</red>");
        body.append("<dark_gray>/</dark_gray><red>").append(Math.round(currentMaxHealth)).append("</red>")
                .append(statDeltaSuffix(tower.type().maxHealth(), currentMaxHealth, true));
        body.append(" <yellow>🧲 어그로 ").append(tower.type().aggroPriority()).append("</yellow>\n");
        body.append("<dark_red>⚔ 피해 ").append(oneDecimal(currentDamage)).append("</dark_red>")
                .append(statDeltaSuffix(tower.type().damage(), currentDamage, true));
        body.append(" <light_purple>🎯 사거리 ").append(oneDecimal(currentRange)).append("</light_purple>")
                .append(statDeltaSuffix(tower.type().range(), currentRange, true))
                .append('\n');
        double baseAttacksPerSecond = 20.0 / Math.max(1, tower.type().attackIntervalTicks());
        double currentAttacksPerSecond = 20.0 / Math.max(1, currentAttackIntervalTicks);
        body.append("<green>⚡ 공속 ").append(oneDecimal(currentAttacksPerSecond)).append("/초</green>")
                .append(statDeltaSuffix(baseAttacksPerSecond, currentAttacksPerSecond, true));
        body.append(" <dark_gray>(</dark_gray><gray>").append(currentAttackIntervalTicks).append("틱</gray><dark_gray>)</dark_gray>\n");
        towerEntity.ifPresent(entity -> appendTowerTimedEffects(body, entity));
        body.append("<aqua>💎 판매 환불 ").append(tower.sellRefundAmount()).append(" 다이아</aqua>\n");
        appendTowerDescription(body, tower.type().description());
        if (!ownedByPlayer) {
            body.append("\n<red>자신이 설치한 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        } else if (!sameLane) {
            body.append("\n<red>현재 담당 라인의 타워만 업그레이드하거나 판매할 수 있습니다.</red>\n");
        }

        ArrayList<ActionButton> actions = new ArrayList<>();
        if (ownedByPlayer && sameLane) {
            List<TowerUpgradeOption> upgrades = ProductionTowerService.availableUpgrades(game, player.getUUID(), tower.position());
            for (TowerUpgradeOption option : upgrades) {
                actions.add(actionButton(
                        option.displayName(),
                        "/semiontd tower upgrade "
                                + option.id() + " "
                                + tower.position().x() + " "
                                + tower.position().y() + " "
                                + tower.position().z(),
                        upgradeTooltip(option),
                        COMPACT_BUTTON_WIDTH
                ));
            }
            padActionRow(actions, 2);
            actions.add(actionButton(
                    "판매",
                    "/semiontd tower sell "
                            + tower.position().x() + " "
                            + tower.position().y() + " "
                            + tower.position().z(),
                    Component.literal("이 타워를 판매하고 환불을 받습니다."),
                    BUTTON_WIDTH
            ));
        }
        showActions(player, "세미온 TD 타워 상세", body.toString(), actions, 2);
    }

    public void showDebugTowerControl(ServerPlayer player) {
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#facc15:#22d3ee><bold>타워 관리</bold></gradient>\n");
        body.append("<gray>건설 후보</gray> <yellow>")
                .append(ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count())
                .append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n");

        List<ActionButton> actions = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(entry -> towerButton(entry, entry.type().mineralCost(), true))
                .toList();
        showActions(player, "세미온 TD 타워", body.toString(), actions, 3);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game) {
        showSummonShop(player, game, 1);
    }

    public void showSummonShop(ServerPlayer player, SemionGame game, int page) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long emerald = semionPlayer == null ? 0 : semionPlayer.economy().emerald();
        List<SummonMonsterType> summons = sortedSummons(game.summonShop().all());
        int pageCount = pageCount(summons.size());
        int safePage = clampPage(page, pageCount);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>페이지</gray> <yellow>").append(safePage).append("</yellow><gray>/</gray><yellow>").append(pageCount).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n\n");
        body.append(summonTierTable(game.summonShop().all()));

        ArrayList<ActionButton> actions = summons.stream()
                .skip((long) (safePage - 1) * SUMMON_PAGE_SIZE)
                .limit(SUMMON_PAGE_SIZE)
                .map(type -> actionButton(
                        summonButtonLabel(type, emerald >= type.gasCost()),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type, emerald >= type.gasCost()),
                        SUMMON_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        addSummonPageActions(actions, "/semiontd summonui ", safePage, pageCount);
        showActions(player, "세미온 TD 소환", body.toString(), actions, 5);
    }

    public void showDebugSummonShop(ServerPlayer player) {
        showDebugSummonShop(player, 1);
    }

    public void showDebugSummonShop(ServerPlayer player, int page) {
        SummonShop summonShop = new SummonShop();
        List<SummonMonsterType> summons = sortedSummons(summonShop.all());
        int pageCount = pageCount(summons.size());
        int safePage = clampPage(page, pageCount);
        StringBuilder body = new StringBuilder();
        body.append("<gradient:#f472b6:#a78bfa><bold>견제 몹 소환</bold></gradient>\n");
        body.append("<gray>페이지</gray> <yellow>").append(safePage).append("</yellow><gray>/</gray><yellow>").append(pageCount).append("</yellow>");
        body.append(" <dark_gray>|</dark_gray> <gray>상세 스탯은 버튼에 마우스를 올려 확인하세요.</gray>\n\n");
        body.append(summonTierTable(summonShop.all()));

        ArrayList<ActionButton> actions = summons.stream()
                .skip((long) (safePage - 1) * SUMMON_PAGE_SIZE)
                .limit(SUMMON_PAGE_SIZE)
                .map(type -> actionButton(
                        summonButtonLabel(type, true),
                        "/semiontd summon " + type.id(),
                        summonTooltip(type, true),
                        SUMMON_BUTTON_WIDTH
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        addSummonPageActions(actions, "/semiontd-debug summonui ", safePage, pageCount);
        showActions(player, "세미온 TD 소환", body.toString(), actions, 5);
    }

    private static List<SummonMonsterType> sortedSummons(java.util.Collection<SummonMonsterType> summons) {
        return summons.stream()
                .sorted(Comparator.comparing(SummonMonsterType::tier)
                        .thenComparing(type -> primaryRole(type).ordinal())
                        .thenComparingLong(SummonMonsterType::gasCost)
                        .thenComparing(SummonMonsterType::displayName))
                .toList();
    }

    private static int pageCount(int size) {
        return Math.max(1, (int) Math.ceil((double) Math.max(0, size) / SUMMON_PAGE_SIZE));
    }

    private static int clampPage(int page, int pageCount) {
        return Math.max(1, Math.min(Math.max(1, pageCount), page));
    }

    private static void addSummonPageActions(ArrayList<ActionButton> actions, String commandPrefix, int page, int pageCount) {
        if (pageCount <= 1) {
            return;
        }
        actions.add(actionButton("이전", page > 1 ? commandPrefix + (page - 1) : "", Component.literal("이전 페이지를 엽니다."), SUMMON_BUTTON_WIDTH));
        actions.add(actionButton("다음", page < pageCount ? commandPrefix + (page + 1) : "", Component.literal("다음 페이지를 엽니다."), SUMMON_BUTTON_WIDTH));
    }

    private static Optional<SemionTowerEntity> towerEntity(SemionGame game, Tower tower) {
        if (game == null || !(tower instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        SemionTeam team = game.teams().get(tower.teamId());
        if (team == null) {
            return Optional.empty();
        }
        return team.laneGroup()
                .lane(tower.laneId())
                .map(lane -> lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static void appendTowerTimedEffects(StringBuilder body, SemionTowerEntity entity) {
        double incomingDamageReduction = entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION);
        if (incomingDamageReduction > 0.0) {
            body.append("<blue>🛡 받는 피해 ")
                    .append(oneDecimal((1.0 - incomingDamageReduction) * 100.0))
                    .append("%</blue>")
                    .append(" <dark_gray>(</dark_gray><green>-")
                    .append(percent(incomingDamageReduction))
                    .append("</green><dark_gray>)</dark_gray>\n");
        }

        StringBuilder effects = new StringBuilder();
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_BONUS, "<green>⚔ 피해 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_BONUS, "<green>⚡ 공속 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_BONUS, "<green>🎯 사거리 증가 +", "</green>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_DAMAGE_REDUCTION, "<blue>🛡 받피 감소 +", "</blue>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION, "<red>⚡ 공속 감소 -", "</red>");
        appendTimedEffect(effects, entity, TimedEffectType.TOWER_RANGE_REDUCTION, "<red>🎯 사거리 감소 -", "</red>");
        if (effects.length() > 0) {
            body.append("<yellow>✨ 활성 효과</yellow>\n").append(effects);
        }
    }

    private static void appendTimedEffect(
            StringBuilder body,
            SemionTowerEntity entity,
            TimedEffectType type,
            String prefix,
            String suffix
    ) {
        double magnitude = entity.activeTimedEffectMagnitude(type);
        int ticks = entity.activeTimedEffectTicks(type);
        if (magnitude <= 0.0 || ticks <= 0) {
            return;
        }
        body.append("<dark_gray>-</dark_gray> ")
                .append(prefix)
                .append(percent(magnitude))
                .append(suffix)
                .append(" <gray>")
                .append(oneDecimal(ticks / 20.0))
                .append("초</gray>\n");
    }

    private void show(ServerPlayer player, String title, String body) {
        show(player, title, miniMessage(body));
    }

    private void show(ServerPlayer player, String title, Component body) {
        Dialog dialog = new NoticeDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(body, BODY_WIDTH)),
                        List.of()
                ),
                NoticeDialog.DEFAULT_ACTION
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions) {
        showActions(player, title, body, actions, 2);
    }

    private void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions, int columns) {
        if (actions.isEmpty()) {
            show(player, title, body);
            return;
        }
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        actionDialogBodies(body),
                        List.of()
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    private static List<DialogBody> actionDialogBodies(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        int split = body.indexOf('\n');
        if (split < 0) {
            return List.of(new HeaderMessage(miniMessage(body), BODY_WIDTH));
        }

        ArrayList<DialogBody> bodies = new ArrayList<>();
        String header = body.substring(0, split);
        String content = body.substring(split + 1);
        if (!header.isBlank()) {
            bodies.add(new HeaderMessage(miniMessage(header), BODY_WIDTH));
        }
        if (!content.isBlank()) {
            bodies.add(new PlainMessage(miniMessage(content), BODY_WIDTH));
        }
        return bodies;
    }

    private static ActionButton actionButton(String label, String command, String tooltip) {
        return actionButton(label, command, Component.literal(tooltip), BUTTON_WIDTH);
    }

    private static ActionButton actionButton(String label, String command, Component tooltip, int width) {
        return actionButton(Component.literal(label), command, tooltip, width);
    }

    private static ActionButton actionButton(Component label, String command, Component tooltip, int width) {
        Optional<net.minecraft.server.dialog.action.Action> action = command == null || command.isBlank()
                ? Optional.empty()
                : Optional.of(new StaticAction(new ClickEvent.RunCommand(command)));
        return new ActionButton(
                new CommonButtonData(label, Optional.of(tooltip), width),
                action
        );
    }

    private static void padActionRow(List<ActionButton> actions, int columns) {
        if (actions.isEmpty() || columns < 2) {
            return;
        }
        int remainder = actions.size() % columns;
        if (remainder == 0) {
            return;
        }
        for (int i = remainder; i < columns; i++) {
            actions.add(actionSpacer(COMPACT_BUTTON_WIDTH));
        }
    }

    private static ActionButton actionSpacer(int width) {
        return new ActionButton(
                new CommonButtonData(Component.literal(" "), Optional.empty(), width),
                Optional.empty()
        );
    }

    private static ActionButton towerButton(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable) {
        return actionButton(
                towerButtonLabel(entry, affordable),
                "/semiontd tower build " + entry.type().id(),
                towerTooltip(entry, mineralCost, affordable),
                COMPACT_BUTTON_WIDTH
        );
    }

    private static ActionButton jobButton(SemionJob job, boolean selected) {
        return actionButton(
                jobButtonLabel(job, selected),
                jobSelectionCommand(job),
                jobTooltip(job, selected),
                BUTTON_WIDTH
        );
    }

    public static String jobSelectionCommand(SemionJob job) {
        return "/semiontd job select " + job.id().getPath();
    }

    public static Component jobButtonLabel(SemionJob job, boolean selected) {
        String prefix = selected ? "✓ " : "";
        return Component.literal(prefix + job.displayName().getString())
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private static Component jobTooltip(SemionJob job, boolean selected) {
        MutableComponent tooltip = job.displayName().copy()
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA);
        if (selected) {
            tooltip.append(Component.literal("\n현재 선택된 직업입니다.").withStyle(ChatFormatting.GREEN));
        }
        for (Component line : job.description()) {
            tooltip.append(Component.literal("\n").append(line.copy().withStyle(ChatFormatting.GRAY)));
        }
        return tooltip;
    }

    public static Component towerButtonLabel(ProductionTowerCatalog.CatalogEntry entry, boolean affordable) {
        return Component.literal(entry.type().displayName())
                .withStyle(style -> style
                        .withColor(affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withBold(true));
    }

    private static Component towerTooltip(ProductionTowerCatalog.CatalogEntry entry, long mineralCost, boolean affordable) {
        var type = entry.type();
        MutableComponent tooltip = mutableMiniMessage(
                "<white><bold>" + type.displayName() + "</bold></white>\n"
                        + "<aqua>💎 " + mineralCost + " 다이아</aqua>" + (affordable ? "" : " <red>(부족)</red>") + "\n"
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<yellow>🧲 어그로 " + type.aggroPriority() + "</yellow>\n"
                        + "<dark_red>⚔ 피해 " + oneDecimal(type.damage()) + "</dark_red> "
                        + "<light_purple>🎯 사거리 " + oneDecimal(type.range()) + "</light_purple>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(type.attackIntervalTicks()) + "/초</green>"
        );
        appendTowerDescription(tooltip, type.description());
        return tooltip;
    }

    private static Component upgradeTooltip(TowerUpgradeOption option) {
        Optional<ProductionTowerCatalog.CatalogEntry> target = ProductionTowerCatalog.entry(option.targetType());
        if (target.isEmpty()) {
            return Component.literal("대상 타워를 찾을 수 없습니다.\n비용 " + option.mineralCost() + " 다이아");
        }
        var entry = target.get();
        var type = entry.type();
        MutableComponent tooltip = mutableMiniMessage(
                "<yellow><bold>" + option.displayName() + "</bold></yellow>\n"
                        + "<gray>대상</gray> <white>" + type.displayName() + "</white>\n"
                        + "<aqua>💎 " + option.mineralCost() + " 다이아</aqua>\n"
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<yellow>🧲 어그로 " + type.aggroPriority() + "</yellow>\n"
                        + "<dark_red>⚔ 피해 " + oneDecimal(type.damage()) + "</dark_red> "
                        + "<light_purple>🎯 사거리 " + oneDecimal(type.range()) + "</light_purple>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(type.attackIntervalTicks()) + "/초</green>"
        );
        appendTowerDescription(tooltip, type.description());
        return tooltip;
    }

    private static String summonTierTable(java.util.Collection<SummonMonsterType> summons) {
        Map<SummonTier, Map<SummonRole, List<SummonMonsterType>>> grouped = new EnumMap<>(SummonTier.class);
        for (SummonTier tier : SummonTier.values()) {
            grouped.put(tier, new EnumMap<>(SummonRole.class));
        }
        for (SummonMonsterType type : summons) {
            grouped.get(type.tier())
                    .computeIfAbsent(primaryRole(type), ignored -> new ArrayList<>())
                    .add(type);
        }

        SummonRole[] columns = {SummonRole.SWARM, SummonRole.RUSH, SummonRole.TANK, SummonRole.SIEGE, SummonRole.SUPPORT, SummonRole.DISRUPTOR};
        StringBuilder table = new StringBuilder();
        table.append("<dark_gray>|</dark_gray> <gray>티어</gray> ");
        for (SummonRole role : columns) {
            table.append("<dark_gray>|</dark_gray> <aqua>").append(roleLabel(role)).append("</aqua> ");
        }
        table.append("<dark_gray>|</dark_gray>\n");
        for (SummonTier tier : SummonTier.values()) {
            Map<SummonRole, List<SummonMonsterType>> row = grouped.getOrDefault(tier, Map.of());
            boolean hasAny = row.values().stream().anyMatch(list -> !list.isEmpty());
            if (!hasAny) {
                continue;
            }
            table.append("<dark_gray>|</dark_gray> <gold>").append(tier.name()).append("</gold> ");
            for (SummonRole role : columns) {
                List<SummonMonsterType> values = row.getOrDefault(role, List.of());
                table.append("<dark_gray>|</dark_gray> ");
                if (values.isEmpty()) {
                    table.append("<gray>-</gray> ");
                } else {
                    table.append("<white>").append(values.size()).append("</white>");
                    table.append("<gray>x</gray> ");
                }
            }
            table.append("<dark_gray>|</dark_gray>\n");
        }
        return table.toString();
    }

    private static Component summonButtonLabel(SummonMonsterType type, boolean affordable) {
        return Component.literal(type.tier().name() + " " + type.displayName().split("\\s+", 2)[0])
                .withStyle(style -> style
                        .withColor(affordable ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withBold(true));
    }

    private static Component summonTooltip(SummonMonsterType type, boolean affordable) {
        MutableComponent tooltip = mutableMiniMessage(
                "<white><bold>" + type.displayName() + "</bold></white>\n"
                        + "<gray>🏷 역할</gray> <yellow>" + roleList(type) + "</yellow> "
                        + "<dark_gray>|</dark_gray> <gray>티어</gray> <gold>" + type.tier().name() + "</gold>\n"
                        + "<green>◆ 비용 " + type.gasCost() + " 에메랄드</green>"
                        + (affordable ? "" : " <red>(부족)</red>")
                        + " <dark_gray>|</dark_gray> <yellow>📈 인컴 +" + type.incomeGain() + "</yellow>\n"
                        + "<aqua>💎 처치 " + type.mineralReward() + " 다이아</aqua> "
                        + "<light_purple>⚖ 효율 " + oneDecimal(type.incomeRatio() * 100.0) + "%</light_purple>\n"
                        + "<red>❤ 체력 " + Math.round(type.maxHealth()) + "</red> "
                        + "<blue>🛡 방어 " + oneDecimal(type.armor()) + "</blue> "
                        + "<dark_purple>✦ 저항 " + oneDecimal(type.resistance()) + "</dark_purple>\n"
                        + "<dark_red>⚔ 공격 " + oneDecimal(type.attackDamage()) + "</dark_red> "
                        + "<gray>" + attackKindIcon(type.attackKind()) + " 방식 " + attackKindLabel(type.attackKind()) + "</gray> "
                        + "<gold>🔥 피해 " + damageTypeLabel(type.damageType()) + "</gold>\n"
                        + "<green>⚡ 공속 " + attacksPerSecond(13) + "/초</green> "
                        + "<white>📏 크기 " + oneDecimal(type.dimensions().width()) + "x" + oneDecimal(type.dimensions().height()) + "</white>\n"
                        + "<light_purple>🎯 타겟 " + oneDecimal(type.targetRolePriority()) + "</light_purple> "
                        + "<yellow>✨ 능력 " + abilityActivationList(type) + "</yellow>"
        );
        appendSummonDescription(tooltip, type.description());
        return tooltip;
    }

    private static void appendTowerDescription(StringBuilder body, List<String> description) {
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                body.append("<dark_gray>-</dark_gray> <gray>").append(line).append("</gray>\n");
            }
        }
    }

    private static void appendTowerDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n설명"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- ").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.append(miniMessage("<gray>" + line + "</gray>"));
            }
        }
    }

    private static void appendDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n설명"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- " + line));
            }
        }
    }

    private static void appendSummonDescription(MutableComponent tooltip, List<String> description) {
        if (description.isEmpty()) {
            return;
        }
        tooltip.append(Component.literal("\n\n"));
        tooltip.append(miniMessage("<gray>설명</gray>"));
        for (String line : description) {
            if (line != null && !line.isBlank()) {
                tooltip.append(Component.literal("\n- ").withStyle(ChatFormatting.DARK_GRAY));
                tooltip.append(miniMessage("<gray>" + line + "</gray>"));
            }
        }
    }

    private static SummonRole primaryRole(SummonMonsterType type) {
        return type.roles().stream()
                .max(Comparator.comparingInt(SummonRole::targetPriority))
                .orElse(SummonRole.RUSH);
    }

    private static String roleList(SummonMonsterType type) {
        return type.roles().stream().map(SemionDialogService::roleLabel).collect(Collectors.joining(", "));
    }

    private static String abilityActivationList(SummonMonsterType type) {
        return type.abilityActivations().stream()
                .map(SemionDialogService::abilityActivationLabel)
                .collect(Collectors.joining(", "));
    }

    private static String abilityActivationLabel(SummonAbilityActivation activation) {
        return switch (activation) {
            case PASSIVE -> "지속";
            case CONDITIONAL -> "조건부";
            case COOLDOWN -> "쿨다운";
        };
    }

    private static String roleLabel(SummonRole role) {
        return switch (role) {
            case SWARM -> "물량";
            case RUSH -> "러시";
            case SIEGE -> "공성";
            case SUPPORT -> "지원";
            case TANK -> "탱커";
            case DISRUPTOR -> "교란";
        };
    }

    private static String attackKindLabel(AttackKind attackKind) {
        return switch (attackKind) {
            case MELEE -> "근접";
            case RANGED -> "원거리";
        };
    }

    private static String attackKindIcon(AttackKind attackKind) {
        return switch (attackKind) {
            case MELEE -> "🗡";
            case RANGED -> "🏹";
        };
    }

    private static String damageTypeLabel(DamageType damageType) {
        return switch (damageType) {
            case PHYSICAL -> "물리";
            case MAGIC -> "마법";
            case TRUE -> "고정";
        };
    }

    private static String attacksPerSecond(int attackIntervalTicks) {
        return oneDecimal(20.0 / Math.max(1, attackIntervalTicks));
    }

    private static String statDeltaSuffix(double baseValue, double currentValue, boolean higherBetter) {
        if (Math.abs(baseValue - currentValue) < 0.005) {
            return "";
        }
        String color = (currentValue > baseValue) == higherBetter ? "green" : "red";
        String sign = currentValue > baseValue ? "+" : "";
        String delta = Math.abs(baseValue) < 0.0001
                ? sign + oneDecimal(currentValue - baseValue)
                : sign + percent((currentValue - baseValue) / baseValue);
        return " <dark_gray>(기본 " + oneDecimal(baseValue)
                + ", </dark_gray><" + color + ">" + delta + "</" + color + "><dark_gray>)</dark_gray>";
    }

    private static String percent(double value) {
        return oneDecimal(value * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component miniMessage(String text) {
        try {
            return SemionText.mini(text);
        } catch (RuntimeException exception) {
            return Component.literal(text);
        }
    }

    private static MutableComponent mutableMiniMessage(String text) {
        try {
            return SemionText.mutableMini(text);
        } catch (RuntimeException exception) {
            return Component.empty().append(Component.literal(text));
        }
    }

    private static Component avatarComponent(MatchParticipantResult participant, int offset) {
        Component avatar = AvatarRendererMod.computeNow(participant.playerName(), offset, false);
        return avatar == null ? Component.empty() : avatar;
    }

    private static long nextGasUpgradeCost(SemionGame game, PlayerEconomy economy) {
        var config = game.economyConfig().gasProduction();
        if (economy.emeraldProductionUpgradeCount() >= config.maxUpgradeCount()) {
            return -1;
        }
        return config.upgradeCost(economy.emeraldProductionUpgradeCount());
    }

    private static String teamList(java.util.Set<TeamId> teams) {
        if (teams.isEmpty()) {
            return "없음";
        }
        return teams.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    private static Comparator<MatchParticipantResult> participantComparator() {
        return Comparator.comparing(MatchParticipantResult::teamId)
                .thenComparing(MatchParticipantResult::playerName);
    }

    private static String phaseLabel(RoundPhase phase) {
        return switch (phase) {
            case WAITING -> "대기";
            case PREPARE_AND_SUMMON -> "준비/소환";
            case LANE_WAVE -> "웨이브";
            case ROUND_PAYOUT -> "정산";
            case ENDED -> "종료";
        };
    }
}
