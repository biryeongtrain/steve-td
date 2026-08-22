package kim.biryeong.semiontd.tower.futureagency;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;

public final class FutureAgencyTowers {
    public static final TowerType ESCAPEE = leader("future_escapee", "2054년으로부터의 도피자", 150, 250, 0, 0, 20,
            "minecraft:wandering_trader", "공격하지 않는 유일 지도자입니다. <gold>미래기관 재건</gold>을 무료로 선택해야 요원을 설치할 수 있습니다.");
    public static final TowerType REBUILDER = leader("future_rebuilder", "기관 재건자", 0, 550, 7, 20, 16,
            "minecraft:villager", "직접 전투하며 모든 <light_purple>요원</light_purple>의 피해·최대 체력을 <green>5%</green> 높입니다. 매 준비 단계에 <gold>정책 3개</gold>가 제시됩니다.");
    public static final TowerType COMMANDER = leader("future_commander", "기관 최고 지휘자", 0, 1000, 8, 32, 14,
            "minecraft:illusioner", "직접 전투하며 모든 <light_purple>요원</light_purple>의 피해·최대 체력을 <green>12%</green>, 공격속도를 <green>8%</green> 높입니다.");

    private static final Map<FutureAgencyRole, List<TowerType>> AGENTS = buildAgents();
    private static final List<TowerType> ALL = buildAll();

    static {ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));}

    private FutureAgencyTowers() {}

    public static List<TowerType> all() {return ALL;}
    public static List<TowerType> agents(FutureAgencyRole role) {return AGENTS.get(role);}
    public static TowerType agent(FutureAgencyRole role, int grade) {return AGENTS.get(role).get(5 - grade);}

    public static boolean isFutureAgencyTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isLeader(TowerType type) {
        return type != null && (type.id().equals(ESCAPEE.id()) || type.id().equals(REBUILDER.id())
                || type.id().equals(COMMANDER.id()));
    }

    public static FutureAgencyRole role(TowerType type) {
        if (type == null) return null;
        for (var entry : AGENTS.entrySet()) {
            if (entry.getValue().stream().anyMatch(candidate -> candidate.id().equals(type.id()))) return entry.getKey();
        }
        return null;
    }

    public static int grade(TowerType type) {
        FutureAgencyRole role = role(type);
        if (role == null) return 0;
        List<TowerType> values = AGENTS.get(role);
        for (int index = 0; index < values.size(); index++) if (values.get(index).id().equals(type.id())) return 5 - index;
        return 0;
    }

    private static Map<FutureAgencyRole, List<TowerType>> buildAgents() {
        EnumMap<FutureAgencyRole, List<TowerType>> result = new EnumMap<>(FutureAgencyRole.class);
        result.put(FutureAgencyRole.COMBAT, line(FutureAgencyRole.COMBAT,
                new double[]{80,125,190,285,390}, new double[]{7,7.5,8,8.5,9},
                new double[]{8,13,21,33,48}, new int[]{16,15,13,11,10}, new int[]{20,25,30,35,40}));
        result.put(FutureAgencyRole.SUPPRESSION, line(FutureAgencyRole.SUPPRESSION,
                new double[]{95,145,215,320,420}, new double[]{6,6.5,7,7.5,8},
                new double[]{6,10,16,25,38}, new int[]{20,18,16,14,13}, new int[]{10,15,20,25,30}));
        result.put(FutureAgencyRole.PROTECTION, line(FutureAgencyRole.PROTECTION,
                new double[]{190,300,450,640,780}, new double[]{2.5,2.7,3,3.2,3.5},
                new double[]{5,8,13,21,32}, new int[]{22,20,18,16,14}, new int[]{70,85,100,115,130}));
        return Map.copyOf(result);
    }

    private static List<TowerType> line(FutureAgencyRole role, double[] hp, double[] range, double[] damage,
                                        int[] interval, int[] aggro) {
        ArrayList<TowerType> result = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            int grade = 5 - index;
            double scale = 0.90 + index * 0.075;
            result.add(TowerType.builder("future_agent_" + role.name().toLowerCase() + "_g" + grade,
                            grade + "급 " + role.displayName() + " 요원")
                    .mineralCost(index == 0 ? 50 : 0).maxHealth(hp[index]).range(range[index])
                    .damage(damage[index]).attackIntervalTicks(interval[index]).aggroPriority(aggro[index])
                    .visual(EntityVisual.builder(role.entityTypeId()).scale(scale).build())
                    .description(agentDescription(role, grade)).build());
        }
        return List.copyOf(result);
    }

    private static TowerType leader(String id, String name, long cost, double health, double range, double damage,
                                    int interval, String entity, String description) {
        return TowerType.builder(id, name).mineralCost(cost).maxHealth(health).range(range).damage(damage)
                .attackIntervalTicks(interval).aggroPriority(-50).visual(EntityVisual.vanilla(entity))
                .description(List.of(description, "기관의 <gold>정책</gold>과 <red>세계 구원</red> 진행을 관리합니다."))
                .build();
    }

    private static List<String> agentDescription(FutureAgencyRole role, int grade) {
        String roleLine = switch (role) {
            case COMBAT -> "진행도가 가장 높은 적을 우선하는 <light_purple>장거리 단일 공격</light_purple> 요원입니다.";
            case SUPPRESSION -> "공격 대상 주변을 함께 타격하고 이동·공격속도를 낮추는 <light_purple>광역 제압</light_purple> 요원입니다.";
            case PROTECTION -> "높은 어그로와 등급별 피해 감소로 동료를 지키는 <light_purple>방호</light_purple> 요원입니다.";
        };
        return List.of(
                "<light_purple>" + role.displayName() + " 요원</light_purple> <white>" + grade + "급</white>. " + roleLine,
                "구원 전 생존하면 현재 위치·체력을 잇는 <aqua>연결 생존자</aqua>를 원본당 최대 1기 유지합니다.",
                "설치 원본은 매 웨이브 원래 위치와 최대 체력으로 다시 출전하며, <red>세계 구원</red> 후에는 생존자와 함께 중앙 방어에 참가합니다."
        );
    }

    private static List<TowerType> buildAll() {
        ArrayList<TowerType> result = new ArrayList<>(List.of(ESCAPEE, REBUILDER, COMMANDER));
        AGENTS.values().forEach(result::addAll);
        return List.copyOf(result);
    }
}
