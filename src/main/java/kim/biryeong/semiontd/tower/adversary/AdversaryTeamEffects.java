package kim.biryeong.semiontd.tower.adversary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Refreshes the Adversary builder's strongest team-wide support channels. */
public final class AdversaryTeamEffects {
    private static final Map<UUID, TeamLaneGroup> REGISTERED_TEAMS = new ConcurrentHashMap<>();
    private static final Map<TeamLaneGroup, Long> LAST_REFRESH_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final ResourceLocation DAMAGE_SOURCE = source("tower_damage");
    private static final ResourceLocation ATTACK_SPEED_SOURCE = source("tower_attack_speed");
    private static final ResourceLocation MAX_HEALTH_SOURCE = source("tower_max_health");

    private AdversaryTeamEffects() {
    }

    /** Registers the owning player's match-local team without exposing it through shared combat APIs. */
    public static void registerTeam(UUID playerId, TeamLaneGroup laneGroup) {
        if (playerId == null) {
            return;
        }
        if (laneGroup == null) {
            REGISTERED_TEAMS.remove(playerId);
        } else {
            REGISTERED_TEAMS.put(playerId, laneGroup);
        }
    }

    /** Removes only this player's reference; other Adversary players on the team stay registered. */
    public static void unregisterPlayer(UUID playerId) {
        if (playerId != null) {
            REGISTERED_TEAMS.remove(playerId);
        }
    }

    static void tick(AdversaryFoxTower fox, SemionTowerEntity source) {
        if (fox == null || source == null || source.isRemoved() || !source.isAlive()
                || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        TeamLaneGroup laneGroup = REGISTERED_TEAMS.get(fox.ownerPlayer());
        if (laneGroup == null || laneGroup.teamId() != fox.teamId()) {
            return;
        }

        int scanInterval = Math.max(1, globalInt(
                "teamEffectScanIntervalTicks",
                AdversaryBalance.TEAM_EFFECT_SCAN_INTERVAL_TICKS
        ));
        long gameTime = level.getGameTime();
        // The offset spreads scans from different teams while remaining deterministic.
        if (Math.floorMod(gameTime + fox.teamId().ordinal(), scanInterval) != 0
                || !claimTeamRefresh(laneGroup, gameTime)) {
            return;
        }

        TeamTargets targets = collectTargets(laneGroup, fox.teamId());
        TeamProfile profile = strongestProfile(targets.forms());
        int duration = Math.max(1, globalInt(
                "teamEffectDurationTicks",
                AdversaryBalance.TEAM_EFFECT_DURATION_TICKS
        ));

        for (SemionTowerEntity tower : targets.towers()) {
            tower.refreshTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_BONUS,
                    DAMAGE_SOURCE,
                    profile.towerDamageBonus(),
                    duration
            );
            tower.refreshTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                    ATTACK_SPEED_SOURCE,
                    profile.towerAttackSpeedBonus(),
                    duration
            );
            tower.refreshTimedEffect(
                    TimedEffectType.TOWER_MAX_HEALTH_BONUS,
                    MAX_HEALTH_SOURCE,
                    profile.towerMaxHealthBonus(),
                    duration
            );
        }
        for (SemionMonsterEntity monster : targets.monsters()) {
            applyStrongest(
                    monster,
                    TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION,
                    profile.monsterDamageReduction(),
                    duration
            );
            applyStrongest(
                    monster,
                    TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION,
                    profile.monsterAttackSpeedReduction(),
                    duration
            );
            applyStrongest(
                    monster,
                    TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                    profile.monsterTowerDamageTakenBonus(),
                    duration
            );
        }
    }

    private static TeamTargets collectTargets(TeamLaneGroup laneGroup, TeamId teamId) {
        Set<SemionTowerEntity> towers = new LinkedHashSet<>();
        Set<SemionMonsterEntity> monsters = new LinkedHashSet<>();
        List<FoxForm> forms = new ArrayList<>();

        for (PlayerLane lane : List.copyOf(laneGroup.lanes())) {
            if (lane == null || lane.teamId() != teamId || lane.arenaWorld() == null) {
                continue;
            }
            ServerLevel level = lane.arenaWorld();
            for (Tower tower : List.copyOf(lane.towers())) {
                SemionTowerEntity entity = towerEntity(tower, level);
                if (entity == null || entity.teamId() != teamId) {
                    continue;
                }
                towers.add(entity);
                if (tower instanceof AdversaryFoxTower fox) {
                    forms.add(fox.form());
                }
            }
            for (Monster monster : List.copyOf(lane.activeMonsters())) {
                SemionMonsterEntity entity = monsterEntity(monster, level);
                if (entity != null
                        && monster.targetTeam() == teamId
                        && AdversaryRivalTower.kindOf(monster).isEmpty()) {
                    monsters.add(entity);
                }
            }
        }
        return new TeamTargets(List.copyOf(towers), List.copyOf(monsters), List.copyOf(forms));
    }

    private static SemionTowerEntity towerEntity(Tower tower, ServerLevel level) {
        if (!(tower instanceof EntityBackedTower entityBackedTower)
                || tower.health() <= 0.0
                || entityBackedTower.entityId().isEmpty()) {
            return null;
        }
        return level.getEntity(entityBackedTower.entityId().getAsInt()) instanceof SemionTowerEntity entity
                && entity.runtimeTower() == tower
                && entity.isAlive()
                && !entity.isRemoved()
                ? entity
                : null;
    }

    private static SemionMonsterEntity monsterEntity(Monster monster, ServerLevel level) {
        if (monster == null || !monster.hasMinecraftEntity()) {
            return null;
        }
        return level.getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity
                && entity.runtimeMonster() == monster
                && entity.isAlive()
                && !entity.isRemoved()
                ? entity
                : null;
    }

    private static void applyStrongest(
            SemionMonsterEntity monster,
            TimedEffectType type,
            double magnitude,
            int duration
    ) {
        if (magnitude > 0.0) {
            // The unsourced monster channel is strongest-only and refreshes equal magnitudes.
            monster.applyTimedEffect(type, magnitude, duration);
        }
    }

    private static boolean claimTeamRefresh(TeamLaneGroup laneGroup, long gameTime) {
        synchronized (LAST_REFRESH_TICKS) {
            Long previous = LAST_REFRESH_TICKS.put(laneGroup, gameTime);
            return previous == null || previous.longValue() != gameTime;
        }
    }

    static TeamProfile strongestProfile(Iterable<FoxForm> forms) {
        double towerDamage = 0.0;
        double towerAttackSpeed = 0.0;
        double towerMaxHealth = 0.0;
        double monsterDamageReduction = 0.0;
        double monsterAttackSpeedReduction = 0.0;
        double monsterVulnerability = 0.0;

        if (forms == null) {
            forms = List.of();
        }
        for (FoxForm form : forms) {
            if (form == null) {
                continue;
            }
            switch (form) {
                case BELL_KEEPER -> towerDamage = Math.max(
                        towerDamage,
                        global("bellTeamDamageBonus", AdversaryBalance.BELL_TEAM_DAMAGE_BONUS)
                );
                case BEACON_KEEPER -> {
                    towerDamage = Math.max(
                            towerDamage,
                            global("beaconTeamDamageBonus", AdversaryBalance.BEACON_TEAM_DAMAGE_BONUS)
                    );
                    towerAttackSpeed = Math.max(
                            towerAttackSpeed,
                            global("beaconTeamAttackSpeedBonus", AdversaryBalance.BEACON_TEAM_ATTACK_SPEED_BONUS)
                    );
                    towerMaxHealth = Math.max(
                            towerMaxHealth,
                            global("beaconTeamMaxHealthBonus", AdversaryBalance.BEACON_TEAM_MAX_HEALTH_BONUS)
                    );
                }
                case OMINOUS_HEXER -> {
                    monsterDamageReduction = Math.max(
                            monsterDamageReduction,
                            global(
                                    "ominousMonsterDamageReduction",
                                    AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION
                            )
                    );
                    monsterAttackSpeedReduction = Math.max(
                            monsterAttackSpeedReduction,
                            global(
                                    "ominousMonsterAttackSpeedReduction",
                                    AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION
                            )
                    );
                    monsterVulnerability = Math.max(
                            monsterVulnerability,
                            global(
                                    "ominousMonsterTowerDamageTakenBonus",
                                    AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS
                            )
                    );
                }
                default -> {
                }
            }
        }
        return new TeamProfile(
                towerDamage,
                towerAttackSpeed,
                towerMaxHealth,
                monsterDamageReduction,
                monsterAttackSpeedReduction,
                monsterVulnerability
        );
    }

    static void clearAllForTesting() {
        REGISTERED_TEAMS.clear();
        LAST_REFRESH_TICKS.clear();
    }

    private static double global(String key, double fallback) {
        return AdversaryBalance.globalValue(key, fallback);
    }

    private static int globalInt(String key, int fallback) {
        return AdversaryBalance.globalInt(key, fallback);
    }

    private static ResourceLocation source(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "adversary/team/" + path);
    }

    private record TeamTargets(
            List<SemionTowerEntity> towers,
            List<SemionMonsterEntity> monsters,
            List<FoxForm> forms
    ) {
    }

    record TeamProfile(
            double towerDamageBonus,
            double towerAttackSpeedBonus,
            double towerMaxHealthBonus,
            double monsterDamageReduction,
            double monsterAttackSpeedReduction,
            double monsterTowerDamageTakenBonus
    ) {
    }
}
