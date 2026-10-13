package kim.biryeong.semiontd.tower.demonlord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * 파멸의 손아귀를 슬롯에 넣은 마왕에게만, 지금 처형할 수 있는(체력이 처형 임계값 이하인) 적을 빨간 발광으로 보여 줍니다.
 *
 * <p>발광은 그 마왕 한 사람에게만 보내는 패킷이라 다른 플레이어 화면은 그대로입니다. Blockbench 모델 몹은 모델을 이루는
 * 디스플레이에 발광 색을 직접 넣고, 바닐라 모습 몹은 발광 색이 팀 색이라 그 플레이어에게만 있는 빨간 가짜 팀에 넣습니다.
 * 은신한 몹은 지정할 수 없으므로 표시하지 않습니다.
 */
public final class DemonLordExecuteMarks {
    /** 몇 틱마다 다시 훑는지. */
    static final int INTERVAL_TICKS = 5;
    static final int RED = 0xFF2A2A;
    private static final String TEAM_NAME = "semiontd_execute";

    private static final Map<UUID, Marks> MARKS = new ConcurrentHashMap<>();

    private static final class Marks {
        final Map<Integer, SemionMonsterEntity> marked = new HashMap<>();
        final Set<String> teamNames = new HashSet<>();
        boolean teamCreated;
    }

    private DemonLordExecuteMarks() {
    }

    /** 서비스 틱. 손아귀가 없거나 전투 중이 아니면 표시를 모두 지웁니다. */
    static void tick(ServerPlayer player, PlayerLane lane, DemonLordState state, long gameTime) {
        if (gameTime % INTERVAL_TICKS != 0) {
            return;
        }
        DemonLordSkillTower altar = state.inCombat() ? gripAltar(lane, player, state) : null;
        if (altar == null || lane.arenaWorld() == null) {
            clear(player);
            return;
        }
        double ratio = TowerBalanceRuntime.ability(altar.type().id(), "executeHealthRatio", 0.50);
        Map<Integer, SemionMonsterEntity> now = new HashMap<>();
        List<PlayerLane> lanes = List.of(lane);
        for (PlayerLane each : lanes) {
            if (each.arenaWorld() == null) {
                continue;
            }
            for (Monster monster : List.copyOf(each.activeMonsters())) {
                if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()
                        || monster.health() > monster.maxHealth() * ratio || !state.canFight(monster)) {
                    continue;
                }
                if (each.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity
                        && entity.isAlive() && !entity.isStealthed()) {
                    now.put(entity.getId(), entity);
                }
            }
        }
        update(player, now);
    }

    private static DemonLordSkillTower gripAltar(PlayerLane lane, ServerPlayer player, DemonLordState state) {
        return state.loadout().bindingOf(DemonLordSkill.GRIP_OF_DOOM)
                .map(binding -> DemonLordService.carrierFor(lane, player.getUUID(), binding))
                .orElse(null);
    }

    private static void update(ServerPlayer player, Map<Integer, SemionMonsterEntity> now) {
        Marks marks = MARKS.computeIfAbsent(player.getUUID(), ignored -> new Marks());
        PlayerTeam team = team();
        for (var iterator = marks.marked.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (now.containsKey(entry.getKey())) {
                continue;
            }
            unmark(player, marks, team, entry.getValue());
            iterator.remove();
        }
        for (var entry : now.entrySet()) {
            if (marks.marked.containsKey(entry.getKey())) {
                continue;
            }
            SemionMonsterEntity entity = entry.getValue();
            if (!entity.usesModelVisual()) {
                if (!marks.teamCreated) {
                    player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
                    marks.teamCreated = true;
                }
                String name = entity.getScoreboardName();
                if (marks.teamNames.add(name)) {
                    player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                            team, name, ClientboundSetPlayerTeamPacket.Action.ADD));
                }
            }
            entity.markGlowPackets(true, RED).forEach(player.connection::send);
            marks.marked.put(entry.getKey(), entity);
        }
    }

    private static void unmark(ServerPlayer player, Marks marks, PlayerTeam team, SemionMonsterEntity entity) {
        if (!entity.isRemoved()) {
            entity.markGlowPackets(false, RED).forEach(player.connection::send);
        }
        String name = entity.getScoreboardName();
        if (marks.teamNames.remove(name)) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
    }

    /** 표시를 모두 지웁니다(전투가 끝나거나 손아귀를 뺐을 때). */
    public static void clear(ServerPlayer player) {
        Marks marks = MARKS.get(player.getUUID());
        if (marks == null || (marks.marked.isEmpty() && marks.teamNames.isEmpty())) {
            return;
        }
        PlayerTeam team = team();
        for (SemionMonsterEntity entity : marks.marked.values()) {
            unmark(player, marks, team, entity);
        }
        marks.marked.clear();
    }

    /** 플레이어가 떠나면 기록만 버립니다. 다시 들어오면 가짜 팀부터 새로 만듭니다. */
    public static void forget(UUID playerId) {
        MARKS.remove(playerId);
    }

    /** 지금 빨갛게 표시 중인 몹의 엔티티 id. 테스트용입니다. */
    public static Set<Integer> markedFor(UUID playerId) {
        Marks marks = MARKS.get(playerId);
        return marks == null ? Set.of() : Set.copyOf(marks.marked.keySet());
    }

    private static PlayerTeam team() {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), TEAM_NAME);
        team.setColor(java.util.Optional.of(net.minecraft.world.scores.TeamColor.RED));
        return team;
    }
}
