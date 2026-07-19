package kim.biryeong.semiontd.tower.villager;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.CatVisual;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate;
import kim.biryeong.semiontd.util.EntityTypeUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.CatVariants;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

public final class VillagerTowers {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat(
            "0.##%",
            DecimalFormatSymbols.getInstance(Locale.ROOT)
    );
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat(
            "0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    // 멍청이 타워
    public static final TowerType T1_SPLASH_TOWER = tower(
            "villager_splash_t1",
            "주민 원거리 기본 타워",
            75,
            40,
            5.5,
            5,
            10,
            0,
            VillagerVisual.builder().profession(VillagerProfession.NITWIT).build(),
            List.of("<gray>기본 주민 원거리 타워입니다.</gray>")
    );

    public static final TowerType T2_LIBRARIAN_TOWER = tower(
            "villager_splash_t2",
            "사서 타워",
            110,
            60,
            7,
            8,
            10,
            5,
            VillagerVisual.builder().profession(VillagerProfession.LIBRARIAN).build(),
            List.of(
                    "<gray>무난한 초반 타워입니다.<gray>",
                    "<green>+ 생존한 라운드마다 피해, 공격 속도가 5% 씩 증가합니다. 최대 30% </green>",
                    "<green>+ 스플래시가 존재합니다. 1.25블록, 75% 피해"
                    )
    );

    public static final TowerType T3_CLERIC_TOWER = tower(
            "villager_splash_t3",
            "성직자 타워",
            180,
            80,
            7,
            10,
            10,
            10,
            VillagerVisual.builder().profession(VillagerProfession.CLERIC).build(),
            List.of(
                    "<gray> 초중반용 스플래시 타워입니다. </green>",
                    "<green> + 생존한 라운드마다 피해, 공격 속도가 7.5% 씩 증가합니다. 최대 45%",
                    "<green> + 스플래시가 존재합니다. 1.75블록, 75% 피해 </green>",
                    "<green> + 3번 공격 시 1번 추가로 공격을 가합니다.</green>"
                    )
    );

    // 골렘 타워

    public static final TowerType T1_GOLEM_TOWER = tower(
            "t1_golem_tower",
            "눈 골렘 타워",
            100,
            120,
            2,
            5,
            20,
            35,
            EntityTypeUtil.byId(EntityType.SNOW_GOLEM),
            List.of("<gray>주민 타워의 탱킹을 담당하는 트리입니다.</gray>")
    );

    public static final TowerType T2_GOLEM_TOWER = tower(
            "t2_golem_tower",
            "라마 타워",
            180,
            200,
            2,
            8,
            20,
            50,
            EntityTypeUtil.byId(EntityType.TRADER_LLAMA),
            List.of("<gray>구리 골렘이 있는 줄 알고 만들다가 없는거 알고 급하게 바꾼 타워입니다.</gray>",
                    "<green> + 피격 시 1 범위 적에게 10 데미지를 입힙니다. (쿨타임 : 2초)</green>",
                    "<green> + 생존한 라운드 마다 체력이 10% 증가합니다. (최대 : 50%)"
                    )
    );

    public static final TowerType T3_GOLEM_TOWER = tower(
            "t3_golem_tower",
            "철 골렘 타워",
            350,
            300,
            3,
            10,
            20,
            80,
            EntityTypeUtil.byId(EntityType.IRON_GOLEM),
            List.of(
                    "<green> + 피격 시 2 범위 적에게 10 데미지를 입힙니다. ( 쿨타임 : 1.5초 ) </green>",
                    "<green> + 생존한 라운드 마다 체력이 20% 증가합니다. ( 최대 : 100 % ) "
            )
    );


    // 알레이 타워
    public static final TowerType T1_ALLAY_TOWER = tower(
            "t1_allay_tower",
            "알레이 타워",
            120,
            40,
            5,
            2,
            15,
            -5,
            EntityTypeUtil.byId(EntityType.ALLAY),
            List.of(
                    "<gray> 팀 지원형 타워입니다.</gray>",
                    "<green> + 5초마다 주위 2블록에 있는 타워의 체력을 20 회복시킵니다. (회복 받은 대상은 5초간 회복받지 않음)</green>"
            )
    );

    public static final TowerType T2_ALLAY_TOWER = tower(
            "t2_allay_tower",
            "알레이 타워(강함)",
            200,
            50,
            5,
            4,
            15,
            -5,
            EntityTypeUtil.byId(EntityType.ALLAY),
            List.of(
                    "<green> + 5초마다 주위 3블록에 있는 타워의 체력을 50 회복시킵니다. (회복 받은 대상은 5초간 회복받지 않음)"
            )
    );

    public static final TowerType T2_WEAPON_SMITH_TOWER = tower(
            "t2_weapon_smith_tower",
            "대장장이 타워",
            250,
            50,
            5,
            5,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.WEAPONSMITH).build(),
            List.of(
                    "<green> + 5초마다 주위 2블록에 있는 타워의 공격력을 3초간 10% 증가시킵니다. (버프 받은 대상은 5초간 같은 버프를 적용받지 않음) </green>"
            )
    );

    public static final TowerType T3_ARMORER_TOWER = tower(
            "t3_armorer_tower",
            "갑옷 제조인 타워",
            300,
            70,
            7,
            10,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.ARMORER).build(),
            List.of(
                    "<green> + 5초마다 주위 3블록에 있는 타워의 체력을 80 회복시킵니다. 또한 3초간 받는 피해를 10% 감소합니다. </green>",
                    "<red> 효과를 받은 대상은 같은 타워 종류의 효과를 5초간 받을 수 없습니다. </red>"
            )

    );

    public static final TowerType T3_WEAPON_SMITH_TOWER = tower(
            "t3_weapon_smith_tower",
            "강화 대장장이 타워",
            350,
            60,
            5,
            7,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.WEAPONSMITH).type(VillagerType.SAVANNA).build(),
            List.of(
                    "<green> + 5초마다 주위 3블록 이내에 있는 타워를 3초동안 공격력과 공격속도를 15% 증가시킵니다.</green>",
                    "<red> 이 효과를 받은 타워는 5초동안 같은 효과를 받을 수 없습니다.</red>"
            )
    );

    // 고양이 타워
    public static final TowerType T1_CAT_TOWER = tower(
            "t1_cat_tower",
            "오셸롯 타워",
            120,
            50,
            10,
            10,
            15,
            5,
            EntityTypeUtil.byId(EntityType.OCELOT),
            List.of(
                    "<gray> 빠른 사거리 공격을 담당하는 고양이 타워입니다. </gray>"
            )
    );

    public static final TowerType T2_ANTI_TANKER_CAT_TOWER = tower(
            "t2_anti_tanker_cat_tower",
            "저격 캣 타워",
            250,
            50,
            12,
            20,
            15,
            5,
            CatVisual.builder().variant(CatVariants.ALL_BLACK).tame(true).build(),
            List.of(
                    "<green> + 현재 체력이 가장 높은 적을 우선 공격합니다. </green>",
                    "<green> + 웨이브가 아닌 대상을 공격할 때 피해를 100% 더 입힙니다. </green>",
                    "<green> + 대상이 </green><red>탱커</red><green>태그를 가질 경우 대신 200% 추가 피해를 입힙니다.</green>",
                    "<green> + 주위 타워/웨이브/인컴 몹 사망 시마다 공격력이 영구적으로 0.02씩 오르며, 최대 </green><yellow>10</yellow><green>까지 상승합니다. </green>"
            )
    );

    public static final TowerType T2_LANE_CLEAR_CAT_TOWER = tower(
            "t2_lane_clear_cat_tower",
            "라클 캣 타워",
            200,
            50,
            10,
            15,
            15,
            5,
            CatVisual.builder().variant(CatVariants.WHITE).tame(true).build(),
            List.of(
                    "<green> + 웨이브를 공격 할 때 피해를 50% 더 입힙니다. </green>",
                    "<green> 적을 처치 시 적이 폭발하며 해당 적 근처 1블록 이내의 적에게 공격력과 같은 피해를 줍니다. </green>",
                    "<green> 주위 타워/웨이브/인컴 몹 사망 시 공격력이 0.025 증가하며, 최대 5까지 증가합니다. (최대 200스택) </green>",
                    "<red> 시체 폭발 피해로 처치한 적은 폭발하지 않습니다. </red>"
            )
    );

    public static final TowerType T3_ANTI_TANKER_CAT_TOWER = tower(
            "t3_anti_tanker_cat_tower",
            "강화 저격 캣 타워",
            450,
            50,
            15,
            25,
            18,
            5,
            CatVisual.builder().variant(CatVariants.BLACK).tame(true).build(),
            List.of(
                    "<green> + 현재 체력이 가장 높은 적을 우선 공격합니다. </green>",
                    "<green> + 웨이브가 아닌 대상을 공격할 때 피해를 200% 더 입힙니다. </green>",
                    "<green> + 대상이 </green><red>탱커</red><green>태그를 가질 경우 대신 400% 추가 피해를 입힙니다.</green>",
                    "<green> + 주위 타워/웨이브/인컴 몹 사망 시마다 공격력이 영구적으로 0.04씩 오르며, 최대 </green><yellow>20</yellow><green>까지 상승합니다. </green>"
            )
    );

    public static final TowerType T3_LANE_CLEAR_CAT_TOWER = tower(
            "t3_lane_clear_cat_tower",
            "강화 라클 캣 타워",
            375,
            50,
            10,
            17,
            18,
            5,
            CatVisual.builder().variant(CatVariants.WHITE).tame(true).build(),
            List.of(
                    "<green> + 웨이브를 공격 할 때 피해를 75% 더 입힙니다. </green>",
                    "<green> 적을 처치 시 적이 폭발하며 해당 적 근처 1.5블록 이내의 적에게 공격력과 같은 피해를 줍니다. </green>",
                    "<green> 주위 타워/웨이브/인컴 몹 사망 시 공격력이 0.05 증가하며, 최대 20까지 증가합니다. (최대 400스택) </green>",
                    "<red> 시체 폭발 피해로 처치한 적은 폭발하지 않습니다. </red>"
            )
    );

    public static final TowerType ADV_T1_SPLASH_TOWER = adv(T1_SPLASH_TOWER);
    public static final TowerType ADV_T2_LIBRARIAN_TOWER = adv(T2_LIBRARIAN_TOWER);
    public static final TowerType ADV_T3_CLERIC_TOWER = adv(T3_CLERIC_TOWER);
    public static final TowerType ADV_T1_GOLEM_TOWER = adv(T1_GOLEM_TOWER);
    public static final TowerType ADV_T2_GOLEM_TOWER = adv(T2_GOLEM_TOWER);
    public static final TowerType ADV_T3_GOLEM_TOWER = adv(T3_GOLEM_TOWER);
    public static final TowerType ADV_T1_ALLAY_TOWER = adv(T1_ALLAY_TOWER);
    public static final TowerType ADV_T2_ALLAY_TOWER = adv(T2_ALLAY_TOWER);
    public static final TowerType ADV_T2_WEAPON_SMITH_TOWER = adv(T2_WEAPON_SMITH_TOWER);
    public static final TowerType ADV_T3_ARMORER_TOWER = adv(T3_ARMORER_TOWER);
    public static final TowerType ADV_T3_WEAPON_SMITH_TOWER = adv(T3_WEAPON_SMITH_TOWER);
    public static final TowerType ADV_T1_CAT_TOWER = adv(T1_CAT_TOWER);
    public static final TowerType ADV_T2_ANTI_TANKER_CAT_TOWER = adv(T2_ANTI_TANKER_CAT_TOWER);
    public static final TowerType ADV_T2_LANE_CLEAR_CAT_TOWER = adv(T2_LANE_CLEAR_CAT_TOWER);
    public static final TowerType ADV_T3_ANTI_TANKER_CAT_TOWER = adv(T3_ANTI_TANKER_CAT_TOWER);
    public static final TowerType ADV_T3_LANE_CLEAR_CAT_TOWER = adv(T3_LANE_CLEAR_CAT_TOWER);

    private static final List<TowerType> BASE_TOWERS = List.of(
            T1_SPLASH_TOWER,
            T2_LIBRARIAN_TOWER,
            T3_CLERIC_TOWER,
            T1_GOLEM_TOWER,
            T2_GOLEM_TOWER,
            T3_GOLEM_TOWER,
            T1_ALLAY_TOWER,
            T2_ALLAY_TOWER,
            T2_WEAPON_SMITH_TOWER,
            T3_ARMORER_TOWER,
            T3_WEAPON_SMITH_TOWER,
            T1_CAT_TOWER,
            T2_ANTI_TANKER_CAT_TOWER,
            T2_LANE_CLEAR_CAT_TOWER,
            T3_ANTI_TANKER_CAT_TOWER,
            T3_LANE_CLEAR_CAT_TOWER
    );
    private static final List<TowerType> ADV_TOWERS = List.of(
            ADV_T1_SPLASH_TOWER,
            ADV_T2_LIBRARIAN_TOWER,
            ADV_T3_CLERIC_TOWER,
            ADV_T1_GOLEM_TOWER,
            ADV_T2_GOLEM_TOWER,
            ADV_T3_GOLEM_TOWER,
            ADV_T1_ALLAY_TOWER,
            ADV_T2_ALLAY_TOWER,
            ADV_T2_WEAPON_SMITH_TOWER,
            ADV_T3_ARMORER_TOWER,
            ADV_T3_WEAPON_SMITH_TOWER,
            ADV_T1_CAT_TOWER,
            ADV_T2_ANTI_TANKER_CAT_TOWER,
            ADV_T2_LANE_CLEAR_CAT_TOWER,
            ADV_T3_ANTI_TANKER_CAT_TOWER,
            ADV_T3_LANE_CLEAR_CAT_TOWER
    );

    static {
        registerAdvTemplate(ADV_T1_SPLASH_TOWER, T1_SPLASH_TOWER.description(), List.of(
                advLine("공격력", "rangedDamagePerExperience"),
                advLine("공격 속도", "rangedAttackSpeedPerExperience")
        ));
        registerAdvTemplate(ADV_T2_LIBRARIAN_TOWER, List.of(
                "<gray>무난한 초반 타워입니다.<gray>",
                "<green>+ 생존한 라운드마다 피해, 공격 속도가 {ability.bonusPerSurvivedRound:percent} 씩 증가합니다. 최대 {ability.bonusPerSurvivedRound*ability.maxSurvivalStacks:percent} </green>",
                "<green>+ 스플래시가 존재합니다. {ability.splashRadius:blocks}, {ability.splashDamageRatio:percent} 피해"
        ), List.of(
                advLine("공격력", "rangedDamagePerExperience"),
                advLine("공격 속도", "rangedAttackSpeedPerExperience")
        ));
        registerAdvTemplate(ADV_T3_CLERIC_TOWER, List.of(
                "<gray> 초중반용 스플래시 타워입니다. </green>",
                "<green> + 생존한 라운드마다 피해, 공격 속도가 {ability.bonusPerSurvivedRound:percent} 씩 증가합니다. 최대 {ability.bonusPerSurvivedRound*ability.maxSurvivalStacks:percent}",
                "<green> + 스플래시가 존재합니다. {ability.splashRadius:blocks}, {ability.splashDamageRatio:percent} 피해 </green>",
                "<green> + {ability.extraAttackEvery:integer}번 공격 시 1번 추가로 공격을 가합니다.</green>"
        ), List.of(
                advLine("공격력", "rangedDamagePerExperience"),
                advLine("공격 속도", "rangedAttackSpeedPerExperience")
        ));
        registerAdvTemplate(ADV_T1_GOLEM_TOWER, T1_GOLEM_TOWER.description(), List.of(
                advLine("최대 체력", "golemHealthPerExperience"),
                advLine("받는 피해 감소", "golemDamageReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T2_GOLEM_TOWER, List.of(
                "<gray>구리 골렘이 있는 줄 알고 만들다가 없는거 알고 급하게 바꾼 타워입니다.</gray>",
                "<green> + 피격 시 {ability.thornRadius:blocks} 범위 적에게 {ability.thornDamage:number} 데미지를 입힙니다. (쿨타임 : {ability.thornCooldownTicks:seconds})</green>",
                "<green> + 생존한 라운드 마다 체력이 {ability.healthBonusPerSurvivedRound:percent} 증가합니다. (최대 : {ability.healthBonusPerSurvivedRound*ability.maxSurvivalStacks:percent})"
        ), List.of(
                advLine("최대 체력", "golemHealthPerExperience"),
                advLine("받는 피해 감소", "golemDamageReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T3_GOLEM_TOWER, List.of(
                "<green> + 피격 시 {ability.thornRadius:blocks} 범위 적에게 {ability.thornDamage:number} 데미지를 입힙니다. ( 쿨타임 : {ability.thornCooldownTicks:seconds} ) </green>",
                "<green> + 생존한 라운드 마다 체력이 {ability.healthBonusPerSurvivedRound:percent} 증가합니다. ( 최대 : {ability.healthBonusPerSurvivedRound*ability.maxSurvivalStacks:percent} ) "
        ), List.of(
                advLine("최대 체력", "golemHealthPerExperience"),
                advLine("받는 피해 감소", "golemDamageReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T1_ALLAY_TOWER, List.of(
                "<gray> 팀 지원형 타워입니다.</gray>",
                "<green> + {ability.supportBlockTicks:seconds}마다 주위 {ability.radius:blocks}에 있는 타워의 체력을 {ability.healAmount:number} 회복시킵니다. (회복 받은 대상은 {ability.supportBlockTicks:seconds}간 회복받지 않음)</green>"
        ), List.of(
                advLine("회복량", "allayHealAmountPerExperience"),
                advLine("회복 주기 감소", "allayIntervalReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T2_ALLAY_TOWER, List.of(
                "<green> + {ability.supportBlockTicks:seconds}마다 주위 {ability.radius:blocks}에 있는 타워의 체력을 {ability.healAmount:number} 회복시킵니다. (회복 받은 대상은 {ability.supportBlockTicks:seconds}간 회복받지 않음)"
        ), List.of(
                advLine("회복량", "allayHealAmountPerExperience"),
                advLine("회복 주기 감소", "allayIntervalReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T2_WEAPON_SMITH_TOWER, List.of(
                "<green> + {ability.supportBlockTicks:seconds}마다 주위 {ability.radius:blocks}에 있는 타워의 공격력을 {ability.buffDurationTicks:seconds}간 {ability.weaponBuff:percent} 증가시킵니다. (버프 받은 대상은 {ability.supportBlockTicks:seconds}간 같은 버프를 적용받지 않음) </green>"
        ), List.of(
                advLine("지원 주기 감소", "allayIntervalReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T3_ARMORER_TOWER, List.of(
                "<green> + {ability.supportBlockTicks:seconds}마다 주위 {ability.radius:blocks}에 있는 타워의 체력을 {ability.healAmount:number} 회복시킵니다. 또한 {ability.buffDurationTicks:seconds}간 받는 피해를 {ability.damageReduction:percent} 감소합니다. </green>",
                "<red> 효과를 받은 대상은 같은 타워 종류의 효과를 {ability.supportBlockTicks:seconds}간 받을 수 없습니다. </red>"
        ), List.of(
                advLine("회복량", "allayHealAmountPerExperience"),
                advLine("회복 주기 감소", "allayIntervalReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T3_WEAPON_SMITH_TOWER, List.of(
                "<green> + {ability.supportBlockTicks:seconds}마다 주위 {ability.radius:blocks} 이내에 있는 타워를 {ability.buffDurationTicks:seconds}동안 공격력과 공격속도를 {ability.weaponBuff:percent} 증가시킵니다.</green>",
                "<red> 이 효과를 받은 타워는 {ability.supportBlockTicks:seconds}동안 같은 효과를 받을 수 없습니다.</red>"
        ), List.of(
                advLine("지원 주기 감소", "allayIntervalReductionPerExperience")
        ));
        registerAdvTemplate(ADV_T1_CAT_TOWER, T1_CAT_TOWER.description(), List.of(
                advLine("공격력", "catDamagePerExperience"),
                advLine("공격 속도", "catAttackSpeedPerExperience")
        ));
        registerAdvTemplate(ADV_T2_ANTI_TANKER_CAT_TOWER, List.of(
                "<green> + 현재 체력이 가장 높은 적을 우선 공격합니다. </green>",
                "<green> + 웨이브가 아닌 대상을 공격할 때 피해를 {ability.nonWaveBonus:percent} 더 입힙니다. </green>",
                "<green> + 대상이 </green><red>탱커</red><green>태그를 가질 경우 대신 {ability.tankBonus:percent} 추가 피해를 입힙니다.</green>",
                "<green> + 주위 타워/웨이브/인컴 몹 사망 시마다 공격력이 영구적으로 {ability.stackDamage:number}씩 오르며, 최대 </green><yellow>{ability.stackDamageCap:number}</yellow><green>까지 상승합니다. </green>"
        ), List.of(
                advLine("공격력", "catDamagePerExperience"),
                advLine("공격 속도", "catAttackSpeedPerExperience"),
                advLine("인컴 대상 피해", "catIncomeDamagePerExperience")
        ));
        registerAdvTemplate(ADV_T2_LANE_CLEAR_CAT_TOWER, List.of(
                "<green> + 웨이브를 공격 할 때 피해를 {ability.waveBonus:percent} 더 입힙니다. </green>",
                "<green> 적을 처치 시 적이 폭발하며 해당 적 근처 {ability.explosionRadius:blocks} 이내의 적에게 공격력과 같은 피해를 줍니다. </green>",
                "<green> 주위 타워/웨이브/인컴 몹 사망 시 공격력이 {ability.stackDamage:number} 증가하며, 최대 {ability.stackDamageCap:number}까지 증가합니다. (최대 {ability.stackDamageCap/ability.stackDamage:integer}스택) </green>",
                "<red> 시체 폭발 피해로 처치한 적은 폭발하지 않습니다. </red>"
        ), List.of(
                advLine("공격력", "catDamagePerExperience"),
                advLine("공격 속도", "catAttackSpeedPerExperience"),
                advLine("웨이브 대상 피해", "catWaveDamagePerExperience")
        ));
        registerAdvTemplate(ADV_T3_ANTI_TANKER_CAT_TOWER, List.of(
                "<green> + 현재 체력이 가장 높은 적을 우선 공격합니다. </green>",
                "<green> + 웨이브가 아닌 대상을 공격할 때 피해를 {ability.nonWaveBonus:percent} 더 입힙니다. </green>",
                "<green> + 대상이 </green><red>탱커</red><green>태그를 가질 경우 대신 {ability.tankBonus:percent} 추가 피해를 입힙니다.</green>",
                "<green> + 주위 타워/웨이브/인컴 몹 사망 시마다 공격력이 영구적으로 {ability.stackDamage:number}씩 오르며, 최대 </green><yellow>{ability.stackDamageCap:number}</yellow><green>까지 상승합니다. </green>"
        ), List.of(
                advLine("공격력", "catDamagePerExperience"),
                advLine("공격 속도", "catAttackSpeedPerExperience"),
                advLine("인컴 대상 피해", "catIncomeDamagePerExperience")
        ));
        registerAdvTemplate(ADV_T3_LANE_CLEAR_CAT_TOWER, List.of(
                "<green> + 웨이브를 공격 할 때 피해를 {ability.waveBonus:percent} 더 입힙니다. </green>",
                "<green> 적을 처치 시 적이 폭발하며 해당 적 근처 {ability.explosionRadius:blocks} 이내의 적에게 공격력과 같은 피해를 줍니다. </green>",
                "<green> 주위 타워/웨이브/인컴 몹 사망 시 공격력이 {ability.stackDamage:number} 증가하며, 최대 {ability.stackDamageCap:number}까지 증가합니다. (최대 {ability.stackDamageCap/ability.stackDamage:integer}스택) </green>",
                "<red> 시체 폭발 피해로 처치한 적은 폭발하지 않습니다. </red>"
        ), List.of(
                advLine("공격력", "catDamagePerExperience"),
                advLine("공격 속도", "catAttackSpeedPerExperience"),
                advLine("웨이브 대상 피해", "catWaveDamagePerExperience")
        ));
    }


    private VillagerTowers() {
    }

    public static boolean isBaseVillagerTower(TowerType type) {
        return type != null && BASE_TOWERS.stream().anyMatch(base -> type.id().equals(base.id()));
    }

    public static boolean isAdvVillagerTower(TowerType type) {
        return type != null && ADV_TOWERS.stream().anyMatch(adv -> type.id().equals(adv.id()));
    }

    public static boolean isVillagerTower(TowerType type) {
        return isBaseVillagerTower(type) || isAdvVillagerTower(type);
    }

    public static boolean matches(TowerType type, TowerType baseType) {
        return type != null && baseType != null
                && (type.id().equals(baseType.id()) || type.id().equals(advId(baseType)));
    }

    private static TowerType adv(TowerType base) {
        return new TowerType(
                advId(base),
                base.displayName(),
                base.category(),
                base.mineralCost(),
                base.maxHealth(),
                base.range(),
                base.damage(),
                base.attackIntervalTicks(),
                base.aggroPriority(),
                base.description(),
                base.visual(),
                base.upgradeOptions()
        );
    }

    private static String advId(TowerType base) {
        return "villager_adv_" + base.id();
    }

    private static void registerAdvTemplate(TowerType type, List<String> template, List<AdvLine> experienceLines) {
        TowerDescriptionRegistry.register(type, resolved -> {
            ArrayList<String> lines = new ArrayList<>(TowerDescriptionTemplate.render(template, resolved));
            for (AdvLine line : experienceLines) {
                lines.add(experienceLine(type, line.stat(), line.key()));
            }
            lines.addAll(reputationLines(type));
            return List.copyOf(lines);
        });
    }

    private static List<String> reputationLines(TowerType type) {
        return List.of(
                reputationLine(type, "공격력", "reputationDamagePerPoint"),
                reputationLine(type, "공격 속도", "reputationAttackSpeedPerPoint"),
                reputationLine(type, "최대 체력", "reputationHealthPerPoint"),
                reputationLine(type, "받는 피해 감소", "reputationDamageReductionPerPoint")
        );
    }

    private static AdvLine advLine(String stat, String key) {
        return new AdvLine(stat, key);
    }

    private static String experienceLine(TowerType type, String stat, String key) {
        return "<green>경험치 " + advInterval(type, key) + "마다 " + stat + "이 " + advPercent(type, key) + "만큼 증가합니다.</green>";
    }

    private static String reputationLine(TowerType type, String stat, String key) {
        return "<blue>평판 " + advInterval(type, key) + "마다 " + stat + "이 " + advPercent(type, key) + "만큼 증가합니다.</blue>";
    }

    private static String advPercent(TowerType type, String key) {
        return PERCENT_FORMAT.format(TowerBalanceRuntime.villagerAdv().buff(type.id(), key));
    }

    private static String advInterval(TowerType type, String key) {
        return NUMBER_FORMAT.format(TowerBalanceRuntime.villagerAdv().buffInterval(type.id(), key));
    }

    private record AdvLine(String stat, String key) {
    }
}
