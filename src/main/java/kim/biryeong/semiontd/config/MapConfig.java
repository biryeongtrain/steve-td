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
            String finalWaypoint,
            String bossSpawn,
            String finalDefenseTower
    ) {
        private static final String DEFAULT_TEAM_SPAWN = "team_spawn";
        private static final String DEFAULT_LANE_SPAWN = "lane_spawn";
        private static final String DEFAULT_LANE_PATH = "lane_path";
        private static final String DEFAULT_LANE_WAYPOINT = "lane_waypoint";
        private static final String DEFAULT_FINAL_WAYPOINT = "final_waypoint";
        private static final String DEFAULT_BOSS_SPAWN = "boss_spawn";
        private static final String DEFAULT_FINAL_DEFENSE_TOWER = "final_defense_lane";

        public RegionMarkers {
            teamSpawn = teamSpawn == null || teamSpawn.isBlank() ? DEFAULT_TEAM_SPAWN : teamSpawn;
            laneSpawn = laneSpawn == null || laneSpawn.isBlank() ? DEFAULT_LANE_SPAWN : laneSpawn;
            lanePath = lanePath == null || lanePath.isBlank() ? DEFAULT_LANE_PATH : lanePath;
            laneWaypoint = laneWaypoint == null || laneWaypoint.isBlank() ? DEFAULT_LANE_WAYPOINT : laneWaypoint;
            finalWaypoint = finalWaypoint == null || finalWaypoint.isBlank()
                    ? DEFAULT_FINAL_WAYPOINT
                    : finalWaypoint;
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
                    DEFAULT_FINAL_WAYPOINT,
                    DEFAULT_BOSS_SPAWN,
                    DEFAULT_FINAL_DEFENSE_TOWER
            );
        }
    }
}
