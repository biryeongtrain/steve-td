package kim.biryeong.semiontd.ui;

import java.util.Comparator;
import java.util.List;
import kim.biryeong.dantashader.displayhud.DisplayHud;
import kim.biryeong.dantashader.displayhud.DisplayHud.HudAlignment;
import kim.biryeong.dantashader.displayhud.DisplayHudManager;
import kim.biryeong.dantashader.displayhud.TextDisplayHud;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SemionDisplayHudService {
    private static final String HUD_ID = "semion-td:status";
    private static final float HUD_X = 1520.0F;
    private static final float HUD_Y = 190.0F;
    private static final float HUD_SIZE = 34.0F;
    private static final int LINE_WIDTH = 170;
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private int updateTicker;

    public void tick(MinecraftServer server, SemionGame game) {
        updateTicker++;
        if (updateTicker % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (game.isActiveParticipant(player.getUUID()) || game.isMatchSpectator(player.getUUID())) {
                update(player, game);
            } else {
                remove(player);
            }
        }
    }

    public void clear(MinecraftServer server) {
        updateTicker = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            remove(player);
        }
    }

    public void remove(ServerPlayer player) {
        DisplayHud.removeHud(player, HUD_ID);
    }

    private void update(ServerPlayer player, SemionGame game) {
        TextDisplayHud hud = hud(player);
        hud.setText(Component.literal(textFor(player, game)));
    }

    private TextDisplayHud hud(ServerPlayer player) {
        DisplayHud existing = DisplayHud.getHud(player, HUD_ID);
        if (existing instanceof TextDisplayHud textHud) {
            return textHud;
        }
        if (existing != null) {
            existing.remove();
        }

        DisplayHudManager.configure(1.0F, 1.0F, 1920.0F, 1080.0F, 10_000);
        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(LINE_WIDTH);
        hud.setShadowToggle(true);
        hud.setScale(HUD_SIZE, HUD_SIZE, 1.0F);
        hud.setViewRange(1000.0F);
        hud.setAlignment(HudAlignment.UNALIGNED);
        hud.setLocation(HUD_X, HUD_Y, 0.0F);
        hud.spawn(player, HUD_ID);
        return hud;
    }

    private static String textFor(ServerPlayer viewer, SemionGame game) {
        StringBuilder text = new StringBuilder();
        text.append("Semion TD\n");
        text.append("Round ").append(game.currentRound()).append(" | ").append(phaseLabel(game.phase()));
        int remainingPrepareSeconds = game.remainingPrepareSeconds();
        if (remainingPrepareSeconds >= 0) {
            text.append(" | ").append(remainingPrepareSeconds).append("s");
        }
        text.append('\n');

        SemionPlayer player = game.players().get(viewer.getUUID());
        if (player != null) {
            PlayerEconomy economy = player.economy();
            text.append("Mineral ").append(economy.mineral())
                    .append("  Gas ").append(economy.gas())
                    .append("  Income ").append(economy.income())
                    .append('\n');
            text.append("Kills ").append(player.matchStats().monsterKills())
                    .append("  Summons ").append(player.matchStats().summonedMonsters())
                    .append('\n');
        } else {
            text.append("Spectating\n");
        }

        text.append("Teams\n");
        List<SemionTeam> activeTeams = game.teams().values().stream()
                .filter(SemionTeam::active)
                .sorted(Comparator.comparing(SemionTeam::id))
                .toList();
        for (SemionTeam team : activeTeams) {
            text.append(team.id().name())
                    .append(" ")
                    .append(team.eliminated() ? "OUT" : Math.round(team.laneGroup().boss().health()) + " HP")
                    .append('\n');
        }
        return text.toString();
    }

    private static String phaseLabel(RoundPhase phase) {
        return switch (phase) {
            case WAITING -> "Waiting";
            case PREPARE_AND_SUMMON -> "Prepare";
            case LANE_WAVE -> "Wave";
            case ROUND_PAYOUT -> "Payout";
            case ENDED -> "Ended";
        };
    }
}
