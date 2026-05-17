package kim.biryeong.semiontd.tower;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;

public final class ProductionTowerCatalog {
    private static final TowerFactory DEFAULT_TOWER_FACTORY = ProductionTower::new;

    private static final TowerType VILLAGER_TANKER_T3 = tower(
            "villager_armor_tower_villager_tanker_t3", "개쌉탱커", 395, 375.0, 7.5, 7.7, 12, 75,
            "minecraft:iron_golem",
            List.of("주민 탱커 최종 업그레이드입니다.", "높은 체력과 직업 능력을 통해 최대한의 탱킹을 자랑합니다.")
    );
    private static final TowerType VILLAGER_TANKER_T2 = tower(
            "villager_armor_tower_villager_tanker_t2", "주민 탱커 특화 타워", 185, 250, 6.5, 5.28, 14, 57,
            "minecraft:iron_golem",
            List.of("체력과 어그로를 강화한 2차 탱커 타워입니다.", "다음 업그레이드에서 전방 유지력이 크게 증가합니다."),
            List.of(upgrade("villager_tanker_t3", "개쌉탱커", VILLAGER_TANKER_T3, 210))
    );
    private static final TowerType VILLAGER_MELEE_DEALER_T3 = tower(
            "villager_armor_tower_villager_melee_dealer_t3", "개쌉 근딜러", 470, 232.5, 9.0, 7.26, 12, 53,
            "minecraft:villager",
            List.of("주민 근딜 최종 업그레이드입니다.", "짧은 공격 주기로 Emerald 중첩을 빠르게 쌓습니다.")
    );
    private static final TowerType VILLAGER_MELEE_DEALER_T2 = tower(
            "villager_armor_tower_villager_melee_dealer_t2", "주민 근딜 타워", 210, 180.0, 8.0, 5.28, 15, 42,
            "minecraft:villager",
            List.of("공격 주기를 줄인 주민 근딜 2차 타워입니다.", "후속 업그레이드에서 지속 화력이 크게 올라갑니다."),
            List.of(upgrade("villager_melee_dealer_t3", "개쌉 근딜러", VILLAGER_MELEE_DEALER_T3, 280))
    );

    public static final TowerType VILLAGER_CROSSBOW_POST = tower(
            "villager_armor_tower", "주민 탱커 타워", 75, 150, 7.0, 4.4, 15, 35,
            "minecraft:villager",
            List.of("높은 체력과 어그로로 라인을 붙잡는 전방형 타워입니다.", "공격할수록 Emerald 중첩으로 피해량이 점진적으로 오릅니다."),
            List.of(
                    upgrade("villager_tanker_t2", "주민 탱커 특화 타워", VILLAGER_TANKER_T2, 120),
                    upgrade("villager_melee_dealer_t2", "주민 근딜 타워", VILLAGER_MELEE_DEALER_T2, 130)
            )
    );

    private static final TowerType VILLAGER_LANE_CLEAR_T3 = tower(
            "villager_bell_mortar_villager_lane_clear_t3", "라클 타워", 550, 90, 11.75, 12.1, 13, 19,
            "minecraft:iron_golem",
            List.of("주민 스플래시 최종 업그레이드입니다.", "넓은 폭발 범위로 몰려오는 몬스터를 안정적으로 정리합니다.")
    );
    private static final TowerType VILLAGER_LANE_CLEAR_T2 = tower(
            "villager_bell_mortar_villager_lane_clear_t2", "주민 라인클리어 타워", 240, 75, 11.0, 9.63, 17, 10,
            "minecraft:iron_golem",
            List.of("스플래시 범위를 강화한 주민 2차 타워입니다.", "다음 업그레이드에서 광역 피해가 크게 증가합니다."),
            List.of(upgrade("villager_lane_clear_t3", "라클 타워", VILLAGER_LANE_CLEAR_T3, 320))
    );
    private static final TowerType VILLAGER_BALANCE_T3 = tower(
            "villager_bell_mortar_villager_balance_t3", "개쌉 밸런스 타워", 520, 100, 12.5, 11.0, 13, 23,
            "minecraft:villager",
            List.of("주민 밸런스 최종 업그레이드입니다.", "사거리, 공격 주기, 스플래시가 고르게 강화됩니다.")
    );
    private static final TowerType VILLAGER_BALANCE_T2 = tower(
            "villager_bell_mortar_villager_balance_t2", "주민 밸런스 타워", 230, 80, 11.5, 8.8, 15, 12,
            "minecraft:villager",
            List.of("기본 성능을 고르게 올린 주민 2차 타워입니다.", "후속 업그레이드에서 지속 광역 화력이 올라갑니다."),
            List.of(upgrade("villager_balance_t3", "개쌉 밸런스 타워", VILLAGER_BALANCE_T3, 315))
    );

    public static final TowerType VILLAGER_BELL_MORTAR = tower(
            "villager_bell_mortar", "주민 스플래쉬 타워", 85, 65, 10.5, 7.7, 17, 5,
            "minecraft:villager",
            List.of("넓은 스플래시로 몰려오는 몬스터를 정리하는 라인 클리어 타워입니다.", "중첩이 쌓이면 광역 피해가 안정적으로 누적됩니다."),
            List.of(
                    upgrade("villager_lane_clear_t2", "주민 라인클리어 타워", VILLAGER_LANE_CLEAR_T2, 150),
                    upgrade("villager_balance_t2", "주민 밸런스 타워", VILLAGER_BALANCE_T2, 140)
            )
    );

    private static final TowerType VILLAGER_RANGE_T3 = tower(
            "villager_emerald_lens_villager_range_t3", "개쌉 사거리 타워", 600, 75, 21.0, 28.6, 10, 16,
            "minecraft:villager",
            List.of("주민 사거리 최종 업그레이드입니다.", "후방 배치에서도 라인 깊숙한 곳까지 개입합니다.")
    );
    private static final TowerType VILLAGER_RANGE_T2 = tower(
            "villager_emerald_lens_villager_range_t2", "사거리 타워", 260, 70, 18.0, 19.31, 13, 0,
            "minecraft:villager",
            List.of("사거리를 크게 늘린 주민 저격 2차 타워입니다.", "다음 업그레이드에서 전장 장악력이 더 올라갑니다."),
            List.of(upgrade("villager_range_t3", "개쌉 사거리 타워", VILLAGER_RANGE_T3, 320))
    );
    private static final TowerType VILLAGER_SNIPER_T3 = tower(
            "villager_emerald_lens_villager_sniper_t3", "개쌉 저거리 타워", 585, 75, 19.0, 35.04, 11, 14,
            "minecraft:iron_golem",
            List.of("주민 저격 최종 업그레이드입니다.", "고체력 타겟을 처리하는 단일 피해가 크게 증가합니다.")
    );
    private static final TowerType VILLAGER_SNIPER_T2 = tower(
            "villager_emerald_lens_villager_sniper_t2", "저격 타워", 255, 65, 17.0, 22.17, 14, 1,
            "minecraft:iron_golem",
            List.of("단일 피해를 강화한 주민 저격 2차 타워입니다.", "직업 능력 중첩으로 후반 화력이 강화됩니다."),
            List.of(upgrade("villager_sniper_t3", "개쌉 저거리 타워", VILLAGER_SNIPER_T3, 310))
    );

    public static final TowerType VILLAGER_EMERALD_LENS = tower(
            "villager_emerald_lens", "주민 저격 타워", 90, 68.0, 15.0, 14.3, 15, -10,
            "minecraft:villager",
            List.of("긴 사거리와 높은 단일 피해로 후방에서 핵심 타겟을 압박합니다.", "처치 시 중첩을 얻어 오브젝트 처리 능력이 강화됩니다."),
            List.of(
                    upgrade("villager_range_t2", "사거리 타워", VILLAGER_RANGE_T2, 150),
                    upgrade("villager_sniper_t2", "저격 타워", VILLAGER_SNIPER_T2, 145)
            )
    );

    private static final TowerType UNDEAD_LANE_CLEAR_T3 = tower(
            "undead_bone_spitter_undead_lane_clear_t3", "개씹 스플타워", 420, 85, 10.75, 11.55, 9, 14,
            "minecraft:skeleton",
            List.of("언데드 스플래시 최종 업그레이드입니다.", "Decay 폭발과 넓은 스플래시로 물량을 지웁니다.")
    );
    private static final TowerType UNDEAD_LANE_CLEAR_T2 = tower(
            "undead_bone_spitter_undead_lane_clear_t2", "스플 타워", 195, 80, 10.0, 8.25, 11, 5,
            "minecraft:skeleton",
            List.of("스플래시를 강화한 언데드 2차 타워입니다.", "후속 업그레이드에서 라인 클리어 성능이 크게 증가합니다."),
            List.of(upgrade("undead_lane_clear_t3", "개씹 스플타워", UNDEAD_LANE_CLEAR_T3, 225))
    );
    private static final TowerType UNDEAD_BALANCE_T3 = tower(
            "undead_bone_spitter_undead_balance_t3", "개씹 밸런스 타워", 495, 100, 13.5, 16.17, 9, 24,
            "minecraft:skeleton",
            List.of("언데드 밸런스 최종 업그레이드입니다.", "단일 피해와 처치 폭발을 함께 강화합니다.")
    );
    private static final TowerType UNDEAD_BALANCE_T2 = tower(
            "undead_bone_spitter_undead_balance_t2", "밸런스 타워", 220, 90.0, 11.5, 10.23, 11, 11,
            "minecraft:skeleton",
            List.of("사거리와 단일 피해를 강화한 언데드 2차 타워입니다.", "처치 시 Decay 폭발로 추가 피해를 냅니다."),
            List.of(upgrade("undead_balance_t3", "개씹 밸런스 타워", UNDEAD_BALANCE_T3, 275))
    );

    public static final TowerType UNDEAD_BONE_SPITTER = tower(
            "undead_bone_spitter", "언데드 폭발 타워", 80, 65, 9.5, 6.6, 12, 0,
            "minecraft:skeleton",
            List.of("빠른 공격과 폭발 스플래시로 물량을 갉아먹는 타워입니다.", "Decay 특성으로 처치 주변에 추가 피해를 남깁니다."),
            List.of(
                    upgrade("undead_lane_clear_t2", "스플 타워", UNDEAD_LANE_CLEAR_T2, 95),
                    upgrade("undead_balance_t2", "밸런스 타워", UNDEAD_BALANCE_T2, 120)
            )
    );

    private static final TowerType UNDEAD_TANKER_T3 = tower(
            "undead_grave_bombard_undead_tanker_t3", "극탱 타워", 560, 320, 7.0, 7.7, 14, 78,
            "minecraft:zombie",
            List.of("언데드 탱커 최종 업그레이드입니다.", "매우 높은 체력과 어그로로 전선을 고정합니다.")
    );
    private static final TowerType UNDEAD_TANKER_T2 = tower(
            "undead_grave_bombard_undead_tanker_t2", "탱 타워", 240, 230, 6.0, 5.28, 16, 60,
            "minecraft:zombie",
            List.of("체력과 어그로를 강화한 언데드 2차 타워입니다.", "후속 업그레이드에서 방어 성능이 크게 올라갑니다."),
            List.of(upgrade("undead_tanker_t3", "극탱 타워", UNDEAD_TANKER_T3, 300))
    );
    private static final TowerType UNDEAD_MELEE_T3 = tower(
            "undead_grave_bombard_undead_melee_t3", "근딜 타워", 580, 280, 8.25, 8.14, 13, 58,
            "minecraft:zombie",
            List.of("언데드 근딜 최종 업그레이드입니다.", "처치 폭발을 크게 강화해 전방에서 연쇄 피해를 냅니다.")
    );
    private static final TowerType UNDEAD_MELEE_T2 = tower(
            "undead_grave_bombard_undead_melee_t2", "근딜 타워", 245, 200, 7.25, 5.72, 15, 46,
            "minecraft:zombie",
            List.of("전방 피해를 강화한 언데드 2차 타워입니다.", "처치 시 Decay 폭발이 더 자주 위협을 만듭니다."),
            List.of(upgrade("undead_melee_t3", "근딜 타워", UNDEAD_MELEE_T3, 320))
    );

    public static final TowerType UNDEAD_GRAVE_BOMBARD = tower(
            "undead_grave_bombard", "언데드 방어 타워", 80, 165.0, 6.5, 4.4, 17, 38,
            "minecraft:zombie",
            List.of("튼튼하고 어그로가 높아 최전방을 버티는 방어형 타워입니다.", "근접 라인에서 몬스터를 붙잡고 처치 폭발로 보조 피해를 냅니다."),
            List.of(
                    upgrade("undead_tanker_t2", "탱 타워", UNDEAD_TANKER_T2, 140),
                    upgrade("undead_melee_t2", "근딜 타워", UNDEAD_MELEE_T2, 145)
            )
    );

    private static final TowerType UNDEAD_SNIPER_T3 = tower(
            "undead_soul_reaper_undead_sniper_t3", "강화 저격 타워", 640, 65, 20, 24.2, 16, 19,
            "minecraft:wither_skeleton",
            List.of("언데드 저격 최종 업그레이드입니다.", "고체력 타겟 처치와 Decay 처치 폭발에 특화됩니다.")
    );
    private static final TowerType UNDEAD_SNIPER_T2 = tower(
            "undead_soul_reaper_undead_sniper_t2", "저격 타워", 280, 62.5, 15.5, 18.7, 18, 6,
            "minecraft:wither_skeleton",
            List.of("단일 피해를 강화한 언데드 저격 2차 타워입니다.", "처치 기반 Decay 중첩으로 후반 화력이 올라갑니다."),
            List.of(upgrade("undead_sniper_t3", "강화 저격 타워", UNDEAD_SNIPER_T3, 325))
    );
    private static final TowerType UNDEAD_RANGE_T3 = tower(
            "undead_soul_reaper_undead_range_t3", "강화 사거리 타워", 615, 65, 19.5, 20.9, 18, 21,
            "minecraft:wither_skeleton",
            List.of("언데드 사거리 최종 업그레이드입니다.", "긴 사거리와 처치 한 상대를 폭발시켜 후방에서 라인을 제어합니다.")
    );
    private static final TowerType UNDEAD_RANGE_T2 = tower(
            "undead_soul_reaper_undead_range_t2", "사거리 타워", 270, 65, 16.5, 16.5, 18, 5,
            "minecraft:wither_skeleton",
            List.of("사거리를 늘린 언데드 저격 2차 타워입니다.", "강력한 적을 먼저 공격 가능합니다."),
            List.of(upgrade("undead_range_t3", "강화 사거리 타워", UNDEAD_RANGE_T3, 320))
    );

    public static final TowerType UNDEAD_SOUL_REAPER = tower(
            "undead_soul_reaper", "언데드 저격 타워", 95, 55.0, 13.5, 14.3, 20, -5,
            "minecraft:wither_skeleton",
            List.of("높은 단일 피해를 통해 안티 탱커 역할을 하는 타워입니다.", "사거리 분기와 저격 분기 모두 후반 대응력이 높습니다."),
            List.of(
                    upgrade("undead_sniper_t2", "저격 타워", UNDEAD_SNIPER_T2, 165),
                    upgrade("undead_range_t2", "사거리 타워", UNDEAD_RANGE_T2, 155)
            )
    );

    private static final TowerType BEAST_BALANCE_T3 = tower(
            "beast_wolf_den_beast_balance_t3", "강화 공속 타워", 400, 114.7, 10.5, 9.08, 12, 23,
            "minecraft:wolf",
            List.of("동물 공속 최종 업그레이드입니다.", "직업 능력 중첩과 짧은 공격 주기로 지속 화력을 냅니다.")
    );
    private static final TowerType BEAST_BALANCE_T2 = tower(
            "beast_wolf_den_beast_balance_t2", "공속 타워", 185, 88.8, 9.5, 6.6, 15, 12,
            "minecraft:wolf",
            List.of("공격 주기를 크게 줄인 동물 2차 타워입니다.", "후속 업그레이드에서 직업 능력 중첩이 강화됩니다."),
            List.of(upgrade("beast_balance_t3", "강화 공속 타워", BEAST_BALANCE_T3, 215))
    );
    private static final TowerType BEAST_SPLASH_T3 = tower(
            "beast_wolf_den_white_fang_den", "강화 라클 타워", 440, 140.6, 9.75, 9.63, 15, 19,
            "minecraft:wolf",
            List.of("동물 라인 클리어 최종 업그레이드입니다.", "넓어진 스플래시와 직업 능력 중첩으로 물량을 정리합니다.")
    );
    private static final TowerType BEAST_SPLASH_T2 = tower(
            "beast_wolf_den_beast_splash_t2", "라클 타워", 200, 99.9, 9.0, 6.88, 13, 10,
            "minecraft:wolf",
            List.of("스플래시를 강화한 동물 2차 타워입니다.", "초중반 중심 유닛입니다."),
            List.of(upgrade("white_fang_den", "강화 라클 타워", BEAST_SPLASH_T3, 240))
    );

    public static final TowerType BEAST_WOLF_DEN = tower(
            "beast_wolf_den", "댕댕이 타워(원거리)", 75, 74.0, 8.5, 5.5, 10, 5,
            "minecraft:wolf",
            List.of("빠른 공격으로 직업 중첩을 쌓아 공속을 끌어올리는 원거리 타워입니다.", "지속 싸움에 강력합니다."),
            List.of(
                    upgrade("beast_balance_t2", "공속 타워", BEAST_BALANCE_T2, 90),
                    upgrade("beast_splash_t2", "라클 타워", BEAST_SPLASH_T2, 105)
            )
    );

    private static final TowerType BEAST_MELEE_DPS_T3 = tower(
            "beast_boar_crasher_beast_melee_dps_t3", "강화 근딜 타워", 300, 271.25, 8.5, 9.08, 12, 60,
            "minecraft:pig",
            List.of("돼지 근딜 최종 업그레이드입니다.", "짧은 공격 주기와 직업 중첩으로 전방 화력을 냅니다.")
    );
    private static final TowerType BEAST_MELEE_DPS_T2 = tower(
            "beast_boar_crasher_beast_melee_dps_t2", "근딜 타워", 200, 210.0, 7.5, 6.6, 16, 49,
            "minecraft:pig",
            List.of("전방 피해를 강화한 돼지 2차 타워입니다.", "후속 업그레이드에서 근접 지속 피해가 크게 올라갑니다."),
            List.of(upgrade("beast_melee_dps_t3", "강화 근딜 타워", BEAST_MELEE_DPS_T3, 300))
    );
    private static final TowerType BEAST_TANKER_T3 = tower(
            "beast_boar_crasher_beast_tanker_t3", "강화 탱 타워", 200, 420, 7.0, 9.63, 12, 82,
            "minecraft:pig",
            List.of("돼지 탱커 최종 업그레이드입니다.", "높은 체력과 어그로로 라인을 단단하게 고정합니다.")
    );
    private static final TowerType BEAST_TANKER_T2 = tower(
            "beast_boar_crasher_beast_tanker_t2", "탱 타워", 120, 288.75, 6.0, 6.6, 14, 64,
            "minecraft:pig",
            List.of("체력과 어그로를 강화한 돼지 2차 타워입니다.", "후속 업그레이드에서 전방 유지력이 크게 증가합니다."),
            List.of(upgrade("beast_tanker_t3", "강화 탱 타워", BEAST_TANKER_T3, 280))
    );

    public static final TowerType BEAST_BOAR_CRASHER = tower(
            "beast_boar_crasher", "돼지 타워", 85, 175.0, 6.5, 5.5, 15, 42,
            "minecraft:pig",
            List.of("높은 체력과 어그로로 전선을 세우는 동물 진영 방어 타워입니다.", "직업 중첩으로 피해와 공속이 함께 성장합니다."),
            List.of(
                    upgrade("beast_melee_dps_t2", "근딜 타워", BEAST_MELEE_DPS_T2, 140),
                    upgrade("beast_tanker_t2", "탱 타워", BEAST_TANKER_T2, 130)
            )
    );

    private static final TowerType BEAST_RANGE_T3 = tower(
            "beast_hawk_roost_beast_range_t3", "강화 사거리 타워", 320, 75, 22.0, 26.4, 13, 14,
            "minecraft:parrot",
            List.of("앵무새 사거리 최종 업그레이드입니다.", "긴 사거리로 라인 전체에 지속 압박을 겁니다.")
    );
    private static final TowerType BEAST_RANGE_T2 = tower(
            "beast_hawk_roost_beast_range_t2", "사거리 타워", 250, 65, 19.0, 17.82, 18, -2,
            "minecraft:parrot",
            List.of("사거리를 강화한 앵무새 2차 타워입니다.", "명중과 처치 모두 직업 중첩을 유지하는 데 도움을 줍니다."),
            List.of(upgrade("beast_range_t3", "강화 사거리 타워", BEAST_RANGE_T3, 300))
    );
    private static final TowerType BEAST_DIVE_HUNTER_T3 = tower(
            "animal_dps_tower_t2", "강화 DPS 타워", 320, 75, 18.0, 21.78, 13, 6,
            "minecraft:parrot",
            List.of("동물 직업 DPS 최종 업그레이드입니다.", "빠른 공격과 직업 중첩으로 우선 타겟을 압박합니다.")
    );
    private static final TowerType BEAST_DIVE_HUNTER_T2 = tower(
            "animal_dps_tower_t3", "DPS 타워", 240, 65, 17.0, 15.84, 18, -5,
            "minecraft:parrot",
            List.of("공격 주기를 줄인 동물 직업 2차 타워입니다.", "후속 업그레이드에서 빠른 단일 압박 능력이 강화됩니다."),
            List.of(upgrade("animal_dps_tower_t3", "강화 DPS 타워", BEAST_DIVE_HUNTER_T3, 280))
    );

    public static final TowerType BEAST_HAWK_ROOST = tower(
            "animal_snipe_tower_t1", "앵무새 저격 타워", 95, 60, 16.0, 13.2, 14, -12,
            "minecraft:parrot",
            List.of("매우 긴 사거리로 라인 전체를 견제하는 저격형 타워입니다.", "명중과 처치 모두 직업 중첩을 올려 후반 화력이 커집니다."),
            List.of(
                    upgrade("beast_range_t2", "사거리 타워", BEAST_RANGE_T2, 135),
                    upgrade("animal_dps_tower_t2", "DPS 타워", BEAST_DIVE_HUNTER_T2, 125)
            )
    );

    private static final ProductionTowerBehavior VILLAGER_CROSSBOW_POST_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.39, 0.25, 10, 0.035, 0.0, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_TANKER_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.71, 0.33, 11, 0.043, 0.006, true, false, 0.13, 0.08);
    private static final ProductionTowerBehavior VILLAGER_TANKER_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 1.1, 0.43, 13, 0.053, 0.012, true, false, 0.31, 0.18);
    private static final ProductionTowerBehavior VILLAGER_MELEE_DEALER_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.53, 0.33, 12, 0.045, 0.012, true, false, 0.1, 0.06);
    private static final ProductionTowerBehavior VILLAGER_MELEE_DEALER_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.78, 0.41, 14, 0.055, 0.02, true, false, 0.23, 0.14);

    private static final ProductionTowerBehavior VILLAGER_BELL_MORTAR_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 1.46, 0.58, 8, 0.035, 0.0, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_LANE_CLEAR_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 2.01, 0.7, 9, 0.045, 0.002, true, false, 0.18, 0.1);
    private static final ProductionTowerBehavior VILLAGER_LANE_CLEAR_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 3.0, 0.82, 11, 0.055, 0.006, true, false, 0.41, 0.22);
    private static final ProductionTowerBehavior VILLAGER_BALANCE_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 1.71, 0.66, 10, 0.045, 0.012, true, false, 0.1, 0.06);
    private static final ProductionTowerBehavior VILLAGER_BALANCE_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 2.23, 0.74, 12, 0.055, 0.02, true, false, 0.23, 0.14);

    private static final ProductionTowerBehavior VILLAGER_EMERALD_LENS_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.0, 0.0, 7, 0.095, 0.0, false, true, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_RANGE_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.0, 0.0, 8, 0.107, 0.004, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_RANGE_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.0, 0.0, 10, 0.121, 0.01, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_SNIPER_T2_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.0, 0.0, 9, 0.113, 0.002, false, true, 0.0, 0.0);
    private static final ProductionTowerBehavior VILLAGER_SNIPER_T3_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.0, 0.0, 11, 0.13, 0.006, false, true, 0.0, 0.0);

    private static final ProductionTowerBehavior UNDEAD_BONE_SPITTER_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.36, 0.55, 5, 0.035, 0.0, true, false, 0.77, 0.35);
    private static final ProductionTowerBehavior UNDEAD_LANE_CLEAR_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.88, 0.67, 6, 0.045, 0.002, true, false, 0.95, 0.45);
    private static final ProductionTowerBehavior UNDEAD_LANE_CLEAR_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 2.82, 0.79, 8, 0.055, 0.006, true, false, 1.18, 0.57);
    private static final ProductionTowerBehavior UNDEAD_BALANCE_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.28, 0.59, 7, 0.053, 0.002, true, true, 0.85, 0.39);
    private static final ProductionTowerBehavior UNDEAD_BALANCE_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.58, 0.65, 9, 0.07, 0.006, true, true, 0.95, 0.45);

    private static final ProductionTowerBehavior UNDEAD_GRAVE_BOMBARD_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.44, 0.28, 7, 0.025, 0.0, true, false, 0.51, 0.25);
    private static final ProductionTowerBehavior UNDEAD_TANKER_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.78, 0.36, 8, 0.033, 0.006, true, false, 0.64, 0.33);
    private static final ProductionTowerBehavior UNDEAD_TANKER_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.18, 0.46, 10, 0.043, 0.012, true, false, 0.82, 0.43);
    private static final ProductionTowerBehavior UNDEAD_MELEE_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.65, 0.38, 8, 0.037, 0.002, true, true, 1.03, 0.5);
    private static final ProductionTowerBehavior UNDEAD_MELEE_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 1.04, 0.5, 10, 0.049, 0.006, true, true, 1.54, 0.7);

    private static final ProductionTowerBehavior UNDEAD_SOUL_REAPER_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.0, 0.0, 6, 0.08, 0.0, false, true, 0.0, 0.0);
    private static final ProductionTowerBehavior UNDEAD_SNIPER_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.0, 0.0, 8, 0.098, 0.002, false, true, 0.0, 0.0);
    private static final ProductionTowerBehavior UNDEAD_SNIPER_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.0, 0.0, 10, 0.115, 0.006, false, true, 0.0, 0.0);
    private static final ProductionTowerBehavior UNDEAD_RANGE_T2_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.0, 0.0, 7, 0.092, 0.004, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior UNDEAD_RANGE_T3_BEHAVIOR =
            behavior(TowerFaction.UNDEAD, "Decay", 0.0, 0.0, 9, 0.106, 0.01, true, true, 0.0, 0.0);

    private static final ProductionTowerBehavior BEAST_WOLF_DEN_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 1.16, 0.5, 7, 0.0, 0.035, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_BALANCE_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 1.37, 0.58, 9, 0.01, 0.047, true, false, 0.1, 0.06);
    private static final ProductionTowerBehavior BEAST_BALANCE_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 1.82, 0.66, 11, 0.02, 0.055, true, false, 0.23, 0.14);
    private static final ProductionTowerBehavior BEAST_SPLASH_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 1.63, 0.62, 8, 0.01, 0.037, true, false, 0.18, 0.1);
    private static final ProductionTowerBehavior BEAST_SPLASH_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 2.46, 0.74, 10, 0.02, 0.041, true, false, 0.41, 0.22);

    private static final ProductionTowerBehavior BEAST_BOAR_CRASHER_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.46, 0.3, 8, 0.018, 0.02, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_MELEE_DPS_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.61, 0.38, 10, 0.028, 0.032, true, false, 0.1, 0.06);
    private static final ProductionTowerBehavior BEAST_MELEE_DPS_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.88, 0.46, 12, 0.038, 0.04, true, false, 0.23, 0.14);
    private static final ProductionTowerBehavior BEAST_TANKER_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.81, 0.38, 9, 0.026, 0.026, true, false, 0.13, 0.08);
    private static final ProductionTowerBehavior BEAST_TANKER_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 1.23, 0.48, 11, 0.036, 0.032, true, false, 0.31, 0.18);

    private static final ProductionTowerBehavior BEAST_HAWK_ROOST_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.0, 0.0, 7, 0.03, 0.035, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_RANGE_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.0, 0.0, 8, 0.042, 0.039, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_RANGE_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.0, 0.0, 10, 0.056, 0.045, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_DIVE_HUNTER_T2_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.0, 0.0, 9, 0.04, 0.047, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior BEAST_DIVE_HUNTER_T3_BEHAVIOR =
            behavior(TowerFaction.BEAST, "Rage", 0.0, 0.0, 11, 0.05, 0.055, true, true, 0.0, 0.0);

    private static final Map<String, CatalogEntry> ENTRIES = new LinkedHashMap<>();

    static {
        registerLine(
                VILLAGER_CROSSBOW_POST,
                VILLAGER_CROSSBOW_POST_BEHAVIOR,
                branch(VILLAGER_TANKER_T2, VILLAGER_TANKER_T2_BEHAVIOR, VILLAGER_TANKER_T3, VILLAGER_TANKER_T3_BEHAVIOR),
                branch(VILLAGER_MELEE_DEALER_T2, VILLAGER_MELEE_DEALER_T2_BEHAVIOR, VILLAGER_MELEE_DEALER_T3, VILLAGER_MELEE_DEALER_T3_BEHAVIOR)
        );
        registerLine(
                VILLAGER_BELL_MORTAR,
                VILLAGER_BELL_MORTAR_BEHAVIOR,
                branch(VILLAGER_LANE_CLEAR_T2, VILLAGER_LANE_CLEAR_T2_BEHAVIOR, VILLAGER_LANE_CLEAR_T3, VILLAGER_LANE_CLEAR_T3_BEHAVIOR),
                branch(VILLAGER_BALANCE_T2, VILLAGER_BALANCE_T2_BEHAVIOR, VILLAGER_BALANCE_T3, VILLAGER_BALANCE_T3_BEHAVIOR)
        );
        registerLine(
                VILLAGER_EMERALD_LENS,
                VILLAGER_EMERALD_LENS_BEHAVIOR,
                branch(VILLAGER_RANGE_T2, VILLAGER_RANGE_T2_BEHAVIOR, VILLAGER_RANGE_T3, VILLAGER_RANGE_T3_BEHAVIOR),
                branch(VILLAGER_SNIPER_T2, VILLAGER_SNIPER_T2_BEHAVIOR, VILLAGER_SNIPER_T3, VILLAGER_SNIPER_T3_BEHAVIOR)
        );
        registerLine(
                UNDEAD_BONE_SPITTER,
                UNDEAD_BONE_SPITTER_BEHAVIOR,
                branch(UNDEAD_LANE_CLEAR_T2, UNDEAD_LANE_CLEAR_T2_BEHAVIOR, UNDEAD_LANE_CLEAR_T3, UNDEAD_LANE_CLEAR_T3_BEHAVIOR),
                branch(UNDEAD_BALANCE_T2, UNDEAD_BALANCE_T2_BEHAVIOR, UNDEAD_BALANCE_T3, UNDEAD_BALANCE_T3_BEHAVIOR)
        );
        registerLine(
                UNDEAD_GRAVE_BOMBARD,
                UNDEAD_GRAVE_BOMBARD_BEHAVIOR,
                branch(UNDEAD_TANKER_T2, UNDEAD_TANKER_T2_BEHAVIOR, UNDEAD_TANKER_T3, UNDEAD_TANKER_T3_BEHAVIOR),
                branch(UNDEAD_MELEE_T2, UNDEAD_MELEE_T2_BEHAVIOR, UNDEAD_MELEE_T3, UNDEAD_MELEE_T3_BEHAVIOR)
        );
        registerLine(
                UNDEAD_SOUL_REAPER,
                UNDEAD_SOUL_REAPER_BEHAVIOR,
                branch(UNDEAD_SNIPER_T2, UNDEAD_SNIPER_T2_BEHAVIOR, UNDEAD_SNIPER_T3, UNDEAD_SNIPER_T3_BEHAVIOR),
                branch(UNDEAD_RANGE_T2, UNDEAD_RANGE_T2_BEHAVIOR, UNDEAD_RANGE_T3, UNDEAD_RANGE_T3_BEHAVIOR)
        );
        registerLine(
                BEAST_WOLF_DEN,
                BEAST_WOLF_DEN_BEHAVIOR,
                branch(BEAST_BALANCE_T2, BEAST_BALANCE_T2_BEHAVIOR, BEAST_BALANCE_T3, BEAST_BALANCE_T3_BEHAVIOR),
                branch(BEAST_SPLASH_T2, BEAST_SPLASH_T2_BEHAVIOR, BEAST_SPLASH_T3, BEAST_SPLASH_T3_BEHAVIOR)
        );
        registerLine(
                BEAST_BOAR_CRASHER,
                BEAST_BOAR_CRASHER_BEHAVIOR,
                branch(BEAST_MELEE_DPS_T2, BEAST_MELEE_DPS_T2_BEHAVIOR, BEAST_MELEE_DPS_T3, BEAST_MELEE_DPS_T3_BEHAVIOR),
                branch(BEAST_TANKER_T2, BEAST_TANKER_T2_BEHAVIOR, BEAST_TANKER_T3, BEAST_TANKER_T3_BEHAVIOR)
        );
        registerLine(
                BEAST_HAWK_ROOST,
                BEAST_HAWK_ROOST_BEHAVIOR,
                branch(BEAST_RANGE_T2, BEAST_RANGE_T2_BEHAVIOR, BEAST_RANGE_T3, BEAST_RANGE_T3_BEHAVIOR),
                branch(BEAST_DIVE_HUNTER_T2, BEAST_DIVE_HUNTER_T2_BEHAVIOR, BEAST_DIVE_HUNTER_T3, BEAST_DIVE_HUNTER_T3_BEHAVIOR)
        );
    }

    private ProductionTowerCatalog() {
    }

    public static Optional<CatalogEntry> find(String towerId) {
        return Optional.ofNullable(ENTRIES.get(towerId));
    }

    public static Collection<CatalogEntry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<CatalogEntry> forFaction(TowerFaction faction) {
        return ENTRIES.values().stream()
                .filter(entry -> entry.behavior().faction() == faction)
                .toList();
    }

    public static Optional<ProductionTowerBehavior> behavior(TowerType type) {
        return type == null ? Optional.empty() : find(type.id()).map(CatalogEntry::behavior);
    }

    public static Optional<CatalogEntry> entry(TowerType type) {
        return type == null ? Optional.empty() : find(type.id());
    }

    private static TowerType tower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            String entityTypeId,
            List<String> description
    ) {
        return tower(id, displayName, mineralCost, maxHealth, range, damage, attackIntervalTicks, aggroPriority, entityTypeId, description, List.of());
    }

    private static TowerType tower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            String entityTypeId,
            List<String> description,
            List<TowerUpgradeOption> upgrades
    ) {
        return new TowerType(
                id,
                displayName,
                TowerCategory.DIRECT,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                description,
                entityTypeId,
                null,
                upgrades
        );
    }

    private static ProductionTowerBehavior behavior(
            TowerFaction faction,
            String mechanicName,
            double splashRadius,
            double splashDamageMultiplier,
            int maxStacks,
            double damagePerStack,
            double attackSpeedPerStack,
            boolean stackOnHit,
            boolean stackOnKill,
            double killSplashRadius,
            double killSplashDamageMultiplier
    ) {
        return new ProductionTowerBehavior(
                faction,
                mechanicName,
                splashRadius,
                splashDamageMultiplier,
                maxStacks,
                damagePerStack,
                attackSpeedPerStack,
                stackOnHit,
                stackOnKill,
                killSplashRadius,
                killSplashDamageMultiplier
        );
    }

    private static TowerUpgradeOption upgrade(String id, String displayName, TowerType target, long mineralCost) {
        return new TowerUpgradeOption(id, displayName, target, mineralCost);
    }

    private static UpgradeBranch branch(
            TowerType tierTwo,
            ProductionTowerBehavior tierTwoBehavior,
            TowerType ultimate,
            ProductionTowerBehavior ultimateBehavior
    ) {
        return branch(tierTwo, tierTwoBehavior, DEFAULT_TOWER_FACTORY, ultimate, ultimateBehavior, DEFAULT_TOWER_FACTORY);
    }

    private static UpgradeBranch branch(
            TowerType tierTwo,
            ProductionTowerBehavior tierTwoBehavior,
            TowerFactory tierTwoFactory,
            TowerType ultimate,
            ProductionTowerBehavior ultimateBehavior,
            TowerFactory ultimateFactory
    ) {
        return new UpgradeBranch(tierTwo, tierTwoBehavior, tierTwoFactory, ultimate, ultimateBehavior, ultimateFactory);
    }

    private static void registerLine(
            TowerType starter,
            ProductionTowerBehavior starterBehavior,
            UpgradeBranch left,
            UpgradeBranch right
    ) {
        registerLine(starter, starterBehavior, DEFAULT_TOWER_FACTORY, left, right);
    }

    private static void registerLine(
            TowerType starter,
            ProductionTowerBehavior starterBehavior,
            TowerFactory starterFactory,
            UpgradeBranch left,
            UpgradeBranch right
    ) {
        register(starter, starterBehavior, starterFactory, 1);
        register(left.tierTwo(), left.tierTwoBehavior(), left.tierTwoFactory(), 2);
        register(left.ultimate(), left.ultimateBehavior(), left.ultimateFactory(), 3);
        register(right.tierTwo(), right.tierTwoBehavior(), right.tierTwoFactory(), 2);
        register(right.ultimate(), right.ultimateBehavior(), right.ultimateFactory(), 3);
    }

    private static void register(TowerType type, ProductionTowerBehavior behavior, int tier) {
        register(type, behavior, DEFAULT_TOWER_FACTORY, tier);
    }

    private static void register(TowerType type, ProductionTowerBehavior behavior, TowerFactory factory, int tier) {
        ENTRIES.put(type.id(), new CatalogEntry(type, behavior, factory, tier));
    }

    private record UpgradeBranch(
            TowerType tierTwo,
            ProductionTowerBehavior tierTwoBehavior,
            TowerFactory tierTwoFactory,
            TowerType ultimate,
            ProductionTowerBehavior ultimateBehavior,
            TowerFactory ultimateFactory
    ) {
    }

    @FunctionalInterface
    public interface TowerFactory {
        ProductionTower create(
                TowerType type,
                ProductionTowerBehavior behavior,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition originalPosition,
                GridPosition currentPosition
        );
    }

    public record CatalogEntry(TowerType type, ProductionTowerBehavior behavior, TowerFactory factory, int tier) {
        public CatalogEntry {
            factory = factory == null ? DEFAULT_TOWER_FACTORY : factory;
        }

        public boolean starter() {
            return tier == 1;
        }

        public ProductionTower create(UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
            return create(ownerPlayer, teamId, laneId, position, position);
        }

        public ProductionTower create(
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition originalPosition,
                GridPosition currentPosition
        ) {
            return factory.create(type, behavior, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        }
    }
}
