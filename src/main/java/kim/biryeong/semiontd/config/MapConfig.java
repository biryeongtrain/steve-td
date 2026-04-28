package kim.biryeong.semiontd.config;

public record MapConfig(
        String templateId,
        int originX,
        int originY,
        int originZ,
        long timeOfDay,
        RegionMarkers regions
) {
    private static final String DEFAULT_TEMPLATE_ID = "semion-td:arena";

    public MapConfig {
        templateId = templateId == null || templateId.isBlank() ? DEFAULT_TEMPLATE_ID : templateId;
        regions = regions == null ? RegionMarkers.defaultMarkers() : regions;
    }

    public static MapConfig defaultConfig() {
        return new MapConfig(
                DEFAULT_TEMPLATE_ID,
                0,
                80,
                0,
                6000,
                RegionMarkers.defaultMarkers()
        );
    }

    public record RegionMarkers(
            String teamSpawn,
            String laneSpawn,
            String lanePath,
            String laneWaypoint,
            String bossSpawn,
            String finalDefenseTower
    ) {
        private static final String DEFAULT_TEAM_SPAWN = "semion:team_spawn";
        private static final String DEFAULT_LANE_SPAWN = "semion:lane_spawn";
        private static final String DEFAULT_LANE_PATH = "semion:lane_path";
        private static final String DEFAULT_LANE_WAYPOINT = "semion:lane_waypoint";
        private static final String DEFAULT_BOSS_SPAWN = "semion:boss_spawn";
        private static final String DEFAULT_FINAL_DEFENSE_TOWER = "semion:final_defense_tower";

        public RegionMarkers {
            teamSpawn = teamSpawn == null || teamSpawn.isBlank() ? DEFAULT_TEAM_SPAWN : teamSpawn;
            laneSpawn = laneSpawn == null || laneSpawn.isBlank() ? DEFAULT_LANE_SPAWN : laneSpawn;
            lanePath = lanePath == null || lanePath.isBlank() ? DEFAULT_LANE_PATH : lanePath;
            laneWaypoint = laneWaypoint == null || laneWaypoint.isBlank() ? DEFAULT_LANE_WAYPOINT : laneWaypoint;
            bossSpawn = bossSpawn == null || bossSpawn.isBlank() ? DEFAULT_BOSS_SPAWN : bossSpawn;
            finalDefenseTower = finalDefenseTower == null || finalDefenseTower.isBlank()
                    ? DEFAULT_FINAL_DEFENSE_TOWER
                    : finalDefenseTower;
        }

        public static RegionMarkers defaultMarkers() {
            return new RegionMarkers(
                    DEFAULT_TEAM_SPAWN,
                    DEFAULT_LANE_SPAWN,
                    DEFAULT_LANE_PATH,
                    DEFAULT_LANE_WAYPOINT,
                    DEFAULT_BOSS_SPAWN,
                    DEFAULT_FINAL_DEFENSE_TOWER
            );
        }
    }
}
