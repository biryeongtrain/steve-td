package kim.biryeong.semiontd.augment;

import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;

public final class AugmentBriefDescriptions {
    private AugmentBriefDescriptions() { }

    public static String describe(AugmentDefinition card, AugmentConfig config) {
        String id = AugmentService.shortId(card.id());
        String template = card.requiredJobId() != null ? dedicatedTemplate(id) : commonTemplate(id);
        if (!AugmentCatalog.fixedMode(card.id()).isEmpty()) template = null;
        String summary = template == null ? AugmentDescriptions.completeDescription(card, config)
                : AugmentDescriptions.renderTemplate(template, config.parametersFor(card.id()));
        if (card.towerAugment() && template != null) {
            TowerType tower = AugmentTowers.all().stream().filter(type -> card.id().equals(AugmentTowers.augmentId(type)))
                    .map(kim.biryeong.semiontd.config.TowerBalanceRuntime::resolve).findFirst().orElseThrow();
            return (AugmentTowers.isFreeCall(tower) ? "무료" : tower.mineralCost() + "다이아")
                    + "·" + AugmentTowers.slots(tower) + "칸·최대 " + AugmentTowers.placementLimit(tower) + "기. "
                    + summary + " R15/R25 자동 강화.";
        }
        return summary;
    }

    private static String commonTemplate(String id) {
        if (id.startsWith("reserve_production_")) return "기본 에메랄드 +{amount}/초. 업그레이드 상한 별개, R25 후에도 유지.";
        return switch (id) {
            case "tactical_designation_1" -> "지정 1기 모드 선택. 돌격: 최종 피해 +{damageBonus:percent}. 엄호: 받는 피해 -{damageReduction:percent}.";
            case "tactical_designation_2" -> "지정 1기 모드 선택. 돌격: 최종 피해 +{damageBonus:percent}. 엄호: 받는 피해 -{damageReduction:percent}.";
            case "tactical_designation_3" -> "지정 1기 모드 선택. 돌격: 최종 피해 +{damageBonus:percent}. 엄호: 받는 피해 -{damageReduction:percent}.";
            case "triangle_formation" -> "웨이브 시작에 반경 {radius} 내 다른 타워 {neighborCount}기 이상: 최종 피해 +{damageBonus:percent}·받는 피해 -{damageReduction:percent}.";
            case "emergency_loan" -> "정기 지급 {advanceMultiplier}배·최대 {advanceCap}다이아 선불. 원금×{debtMultiplier}를 이후 {repaymentCount}회 지급에서 상환.";
            case "additional_payload" -> "다음 유틸 인컴의 비용 ×{costMultiplier}·최대 체력 ×{healthMultiplier}·회복/보호막 ×{supportMultiplier}.";
            case "twin_squadron" -> "웨이브 시작에 같은 종류·티어가 정확히 2기: 최종 피해 +{damageBonus:percent}.";
            case "overheat_core" -> "이번 준비에 지정한 타워 최종 피해 +{damageBonus:percent}. 사용마다 피해 -{penaltyPerStack:percent} 영구 누적, 최대 {maxStacks}회.";
            case "frontline_specialization" -> "서로 다른 2기 지정. 선봉 피해 -{vanguardDamagePenalty:percent}·받는 피해 -{vanguardDamageReduction:percent}. 포대 피해 +{artilleryDamageBonus:percent}·피격 ×{artilleryIncomingMultiplier}. 둘 다 지정해야 적용.";
            case "forecast_offensive" -> "인컴 출현 1웨이브 지연, 능력치 {echoRatio:percent} 복제 1기 추가. 비용 즉시 지불, 인컴 증가는 출현 때.";
            case "support_performance" -> "유틸 인컴 1기가 다른 {targetCount}기 회복/보호막: 인컴 +{incomeBonus}. 준비마다 1회, 경기 합계 최대 +{matchIncomeCap}.";
            case "battlefield_mastery" -> "지정 타워가 웨이브 중 최대 체력 {damageThreshold:percent} 피해 후 생존: 최종 피해·최대 체력 +{bonusPerStack:percent}, 최대 {maxStacks}중첩. 변경·해제 시 초기화.";
            case "cash_settlement" -> "매 라운드 첫 인컴의 인컴 증가 포기, 그 {diamondMultiplier}배를 즉시 다이아로 획득.";
            case "forbidden_blueprint" -> "최대 {ticketValue}다이아 승급권 {ticketCount}장. 사용마다 영구 정기 지급 ×{payoutMultiplier}. 공격 타워당 1회, 이번 준비 종료 시 소멸.";
            case "low_pressure_high_yield" -> "매 라운드 첫 인컴 체력·공격 ×{bodyMultiplier}, 인컴 증가 +{bonusRatio:percent}. 추가 증가 최대 {roundBonusCap}.";
            case "finishing_fire_1" -> "체력 절반 이하 적에 주 대상 기본 공격 피해 +{damageBonus:percent}. 추가·범위 공격 제외.";
            case "finishing_fire_2" -> "체력 절반 이하 적에 주 대상 기본 공격 피해 +{damageBonus:percent}. 추가·범위 공격 제외.";
            case "finishing_fire_3" -> "체력 절반 이하 적에 주 대상 기본 공격 피해 +{damageBonus:percent}. 추가·범위 공격 제외.";
            case "winning_barrage" -> "주 대상 기본 공격 처치: 다음 기본 공격 {charges}회 피해 +{damageBonus:percent}. 재처치하면 잔량 {charges}회로 갱신.";
            case "domino_fire" -> "주 대상 기본 공격 초과 피해 {overkillRatio:percent}를 반경 {radius} 적 1기에 전달. 공격력 {damageCapRatio:percent} 한도, 연쇄 발동 없음.";
            case "one_man_show" -> "변경 불가 주역: 최종 피해 +{damageBonus:percent}·최대 체력 +{maxHealthBonus:percent}. 나머지 피해 -{otherDamagePenalty:percent}, 주역 해제 후에도 유지.";
            case "wartime_economy" -> "최종 피해 +{damageBonus:percent}·최대 체력 +{maxHealthBonus:percent}. 이후 정기 다이아 지급 영구 ×{payoutMultiplier}.";
            case "folding_barricade_blueprint" -> "동거리 우선 피격. 한 번에 받는 피해 최대 {damagePerHitCap}, 회복 불가·직접 공격 불가.";
            case "pulse_relay_blueprint" -> "공격 타워 2기 연결. 한쪽 기본 공격 {attacksPerCharge}적중마다 반대편 다음 기본 공격 추가 피해 {chargedDamageRatio:percent}.";
            case "barrier_core_call" -> "연결 최대 3기의 피해 {redirectRatio:percent} 대신 받음. 전투 중 회복·판매·재배치 불가.";
            case "giant_hunter_call" -> "기본 공격에 적 최대 체력 {maxHealthDamageRatio:percent}(자연 보스 {bossMaxHealthDamageRatio:percent}) 추가 피해. {minimumRange}블록 안 공격 불가.";
            case "emergency_bell_blueprint" -> "체력 {healthThreshold:percent} 이하: 최대 체력 {healRatio:percent}·상한 {healCap} 회복. 웨이브당 다른 {maxHeals}기에 각 1회.";
            case "capacitor_post_blueprint" -> "사거리 내 적 없으면 {chargeTicks:seconds}초마다 충전, 최대 {maxCharges}. 다음 기본 공격에 충전당 추가 피해 {chargeDamage}.";
            case "ambush_workshop_blueprint" -> "전방 지뢰 3개. 감지 {triggerRadius}·폭발 {damageRadius}블록, 최대 {mineTargets}기에 {mineDamage}피해. 다음 웨이브 재장전.";
            case "starlight_cocoon_call" -> "{hatchWaves}웨이브 생존 후 준비에 부화: 체력 {hatchedHealth}·사거리 {hatchedRange}·공격 {hatchedDamage}·간격 {hatchedIntervalTicks:seconds}초.";
            case "ordnance_factory_call" -> "인컴 구매 {emeraldPerShell}에메랄드당 다음 전투 포탄, 최대 {maxShells}발. 반경 {shellRadius}·최대 {shellTargets}기에 {shellDamage}피해.";
            default -> null;
        };
    }

    private static String dedicatedTemplate(String id) {
        return switch (id) {
            case "job_villager_adv_towers_g2" -> "생존 주민 {requiredKinds}계열 이상: {intervalTicks:seconds}초마다 계열별 최고 경험치 1기 추가 행동. 같은 스타터 승급은 같은 계열.";
            case "job_villager_adv_towers_p" -> "경험치 지급 후 최고 경험치 공격 타워: 이번 전투 사거리 레인 전체, 기본 공격에 {extraTargets}기 추가 {damageRatio:percent} 피해.";
            case "job_undead_towers_g1" -> "성장 완료 스켈레톤: 성장 범위 내 {deathsPerCharge}처치마다 충전. 기본 공격에 추가 {targetCount}기 {damageRatio:percent} 물리 피해. 라운드 종료 소멸.";
            case "job_undead_towers_g2" -> "사망 {reviveDelayTicks:seconds}초 후 체력 {reviveHealthRatio:percent}로 부활, 공격력 +{damageBonus:percent}. 타워당 라운드 1회, 전투 종료 시 취소.";
            case "job_undead_towers_p" -> "지정 원본 스켈레톤 공격·체력 +{statBonus:percent}. 성장 범위 {deathsPerSummon}처치마다 {copyRatio:percent} 복제 {copiesPerSummon}기, 최대 {maxCopies}. 슬롯·성장·소환·증강 없음. 종료·판매 시 제거.";
            case "job_animal_towers_g2" -> "동종 기본 공격 {everyAttacks}적중마다 다른 동종 최대 {maxAllies}기가 같은 적에 {damageRatio:percent} 추가 공격. 증강 재발동 없음.";
            case "job_warlock_towers_p" -> "핵심 2기 허용, 둘째 {secondCoreCost}다이아. 생존 핵심끼리 흡수 성장·회복 공유, 횟수·유언·폭발 제외. 서로 생존해도 각성 가능.";
            case "job_legion_towers_s" -> "원본당 라운드 1회: 복제본 소모로 치명 피해 무효, 피격 전 체력에서 최대 체력 {healRatio:percent} 회복. 최대 체력 상한.";
            case "job_legion_towers_g1" -> "동종·동티어 복제 합체: 체력·공격 합산, 재료당 크기 +{scalePerClone:percent}. 독립 행동, 공격 시 반경 {radius}에 {damageRatio:percent} 추가 피해.";
            case "job_legion_towers_p" -> "최다 투자 원본의 복제 첫 공격에 다음 세대 생성(합체 권리 유지). 최대 {maxGeneration}세대, 부모 능력치 {childRatio:percent}·동일 만료. 예약 포함 라운드 추가 {maxAdditional}기 한도.";
            case "job_resonance_towers_s" -> "웨이브 시작: 최소 공명 무블룸 1기와 미연결 타종 중 최대 공명 1기를 연결. 동률은 가까운 순, 공명 +1.";
            case "job_resonance_towers_g2" -> "현재 티어 최대 공명: 기본 공격 {everyAttacks}적중마다 반경 {radius}의 최대 {maxTargets}기에 공격력 {damageRatio:percent} 추가 마법 피해.";
            case "job_illager_towers_g2" -> "내 표식·흉조 적 처치: 반경 {radius}의 {targetCount}기에 표식 전이·공격력 {damageRatio:percent} 마법 피해. 지속시간 갱신, 중첩 없음.";
            case "job_illager_towers_p" -> "습격 중 추가 게이지 {gaugePerVolley}마다 공격 중인 우민이 현재 적에 {damageRatio:percent} 추가 물리 공격. 증강 재발동 없음.";
            case "job_nether_s" -> "자연 감소로 최대 체력 {healthRatio:percent} 손실마다 충전. 기본 공격에 손실량만큼 추가 마법 피해. 라운드 종료 시 충전 소멸.";
            case "job_nether_g2" -> "좀비 전환 후 {durationTicks:seconds}초간 체력 최소 1. 이때 기본 공격은 반경 {radius}에 공격력 {damageRatio:percent} 추가 물리 피해.";
            case "job_nether_p" -> "좀비 사망 {reviveDelayTicks:seconds}초 후 체력 {reviveHealthRatio:percent}로 재부활, 주는 피해 +{damageBonus:percent}. 타워당 라운드 1회, 전투 종료 취소.";
            case "job_end_towers_g1" -> "힘 전달 시간 ×{durationMultiplier}. 전투 중 {transfersPerCharge}기 전달마다 다음 기본 공격의 기존 대상에 {damageRatio:percent} 추가 마법 피해. 최대 1충전.";
            case "job_end_towers_g2" -> "{intervalTicks:seconds}초마다 {burstIntervalTicks}틱 간격 브레스 {shots}회. 길이 {length}·폭 {width}, 최대 {maxTargets}기에 {damageRatio:percent} 마법 피해. 유효 적 재선택.";
            case "job_end_towers_p" -> "라운드 1회·{chargeTicks:seconds}초 차지. 돌진 {rushDamageRatio:percent} 물리. 소환수 {stunTicks:seconds}초 기절·넉백 {knockbackDistance}(보스/최종방어 제외). Y+{flightHeight} 브레스 {burnDurationTicks:seconds}초, {burnIntervalTicks:seconds}초당 {burnDamageRatio:percent} 마법 화상.";
            case "job_ocean_s" -> "소프트캡 도달 타워의 물을 같은 공급원의 최소 물 타워로 전달. 감쇠 전 물량을 옮기며 중복 감쇠는 1회 적용.";
            case "job_ocean_g1" -> "물 공급·소프트캡 ×{supplyMultiplier}, 공급 중단 한도 유지. 웨이브 시작에 물 사용 타워의 물 최소 {openingWater} 보장.";
            case "job_ocean_g2" -> "물 {waterPerCharge} 소모마다 충전. 다음 기본 공격에 반경 {radius} 최대 {targets}기에 {damageRatio:percent} 마법 피해. 라운드 종료 시 충전 소멸.";
            case "job_ancient_city_s" -> "내 스컬크 위 적에 감지 표식 부여 시 반경 {radius}의 미표식 최대 {targets}기에도 전체 지속시간 표식. 효과 중첩 없음.";
            case "job_ancient_city_p" -> "표식 적 아래 스컬크 최대 {centers}곳에 충격파. 반경 {radius}·최대 {targets}기, 최강 워든 소닉 붐 {damageRatio:percent} 피해. 쿨 {cooldownTicks:seconds}초.";
            case "job_hero_party_g1" -> "무기 1·3·5강 효과 즉시 해금. 실제 5강부터 효과 세기·체력 비례 피해 상한 ×{effectMultiplier}. 범위·시간·조건·가격 유지.";
            case "job_hero_party_p" -> "무기 공격력 +{weaponDamageBonus:percent}·체력 +{healthBonus:percent}. 기본 공격 {everyAttacks}적중마다 길이 {length}·폭 {width}, 최대 {maxTargets}기에 {damageRatio:percent} 물리 피해.";
            case "job_adversary_towers_g2" -> "여우가 내 라이벌 처치: 체력 {healthRatio:percent}·공격력 {damageRatio:percent} 흡수. 시작 능력치의 {healthCapRatio:percent}·{damageCapRatio:percent} 한도, 라운드 종료 소멸.";
            case "job_adversary_towers_p" -> "최종 여우 공격 +{damageBonus:percent}·체력 +{healthBonus:percent}. 라이벌 처치 후 {durationTicks:seconds}초간 기본 공격 추가 {extraTargets}기에 {secondaryRatio:percent} 피해.";
            case "job_engineer_towers_g1" -> "신호당 {activeTicks:seconds}초 작동. 사격은 {shotSpacingTicks}틱 간격 3점사, 후속탄 각 {followupRatio:percent} 피해. 무효 표적은 사거리 내 재선택.";
            case "job_engineer_towers_p" -> "발판 연결 타워 {intervalTicks:seconds}초마다 자동 작동. 기술자 피해 +{damageBonus:percent}. 발판 횟수·성장 증가 없음, TNT 사용 한도 유지.";
            case "job_queen_towers_g2" -> "무료 조커 {tickets}기, 일반 슬롯 사용. 최적 족보·문양 능력 획득. 포함된 줄 공격 속도 +{attackSpeedBonus:percent}(1회). 환급·승급 불가.";
            case "job_atlantis_towers_p" -> "수압 폭발 전 압력 {transferRatio:percent}를 반경 {radius}의 다른 {targets}기에 전달. 고압 구역 최대 압력은 즉시 폭발, 연쇄 최대 {chainTargets}기.";
            case "job_plant_towers_g1" -> "내 잔디·회백토 식물 성장 +{growthRounds}라운드. 새 식물은 동계열 최대 성장의 {inheritRatio:percent} 내림 계승. 기존 성장 상한 유지.";
            case "job_plant_towers_p" -> "지형 1기를 세계수로 지정. 반경 {radius}에 토양 무관 식재·계열별 토양 효과. 범위 내 식물 공격·체력 +{statBonus:percent}.";
            case "job_army_g1" -> "최종 계급 지휘 범위의 동종 후임 {attacksRequired}회 공격마다 마지막 적에 지원 사격. 계급 페널티 전 공격력 {damageRatio:percent}, 원래 피해 유형.";
            case "job_army_g2" -> "전역 시 진급 가능한 전투병 무료권 획득, 즉시 1계급 진급. 라운드당 1회·최대 1장. 시설·증강 타워 제외.";
            case "job_army_p" -> "최근 전역 2기 재합류: 체력 {healthRatio:percent}·페널티 전 공격 {damageRatio:percent}. 공격 능력만 계승, 슬롯·진급·전역 보상 없음. 라운드 종료 제거.";
            case "job_thunder_s" -> "전력 충분·사거리 내 적 없음: {chargeTicks:seconds}초마다 1발, 최대 {maxShots}발 충전. 다음 기본 공격에 각 {damageRatio:percent} 피해로 추가 발사.";
            case "job_thunder_p" -> "번개가 아군 람쥐 최대 {maxRelays}기 경유. 각 중계 사거리 내 새 적 최대 {targetsPerRelay}기에 {damageRatio:percent} 피해. 적 중복 공격 없음.";
            case "job_demon_lord_towers_g1" -> "라운드 1회, 체력 {healthThreshold:percent} 이하: 최대 체력 {healRatio:percent} 회복·모든 스킬 쿨 초기화. {durationTicks:seconds}초간 피해 +{damageBonus:percent}.";
            case "job_demon_lord_towers_g2" -> "{windowTicks:seconds}초 내 공격 스킬 {distinctSkills}종 적중: 공격 스킬 쿨 -{cooldownReductionTicks:seconds}초, {durationTicks:seconds}초간 스킬 피해 +{damageBonus:percent}. 쿨 {cooldownTicks:seconds}초.";
            case "job_demon_lord_towers_p" -> "{windowTicks:seconds}초 내 공격 스킬 2종 적중: 피격 없는 분신이 각 스킬 {damageRatio:percent} 재현. 추가 비용·스킬 쿨·재발동 없음. 쿨 {cooldownTicks:seconds}초.";
            case "job_gamble_p" -> "홀짝·주사위 최대 {maxAttempts}회 재시도. 성공: 다음 공격 반경 {jackpotRadius}·{maxTargets}기 양쪽 공격력 {jackpotDamageRatio:percent} 폭발, {maxCharges}충전. 전부 실패 점수 -{allFailedScoreLoss}.";
            case "job_body_g2" -> "심장 체력 +{healthBonus:percent}. 라운드 1회, 체력 {healthThreshold:percent} 이하: {healRatio:percent} 회복, 다음 {fastBeats}박동 간격 ×{intervalRatio}.";
            case "job_body_p" -> "피부가 적에게 잃은 체력 {healthLossRatio:percent}마다 연결 심장 추가 박동. 심장당 {cooldownTicks:seconds}초에 1회, 대기 저장 없음. 웨이브마다 초기화.";
            case "job_pet_towers_g1" -> "대장 반려 지정. 기본 공격 {hitsRequired}적중마다 같은 마당 타종이 종당 1기·최대 {maxAllies}기 협공. 같은 적에 기본 피해 {damageRatio:percent}.";
            case "job_pet_towers_g2" -> "어린 개·고양이 성체 능력 해금. 새 공격은 자신과 같은 마당 다친 반려 {healTargets}기 회복. 실제 성장·승급 유대 조건 유지.";
            case "job_pet_towers_p" -> "마당에 성체 개·고양이·새: 다수 고양이 독립 효과, 고양이·새도 동티어 개 무리 강화. 새는 다친 반려 최대 {healTargets}기 회복.";
            case "job_developer_towers_s" -> "라운드 첫 조건부 피해 버그: 해당 버그·복사 없는 최근접 개발자 전투 타워에 강화 {bonusRatio:percent} 복사. 불이익 제외.";
            case "job_developer_towers_p" -> "라운드 첫 정식 패치 성공: 보유 개발자 전투 타워 전부에 예약. 횟수 1회만 소모, 버전 고정·롤백 실패 제한 유지.";
            case "job_frost_p" -> "내 냉매 적 최대 {maxSources}기: {intervalTicks:seconds}초마다 반경 {radius}의 다른 {maxTargets}기에 한기 {chill:percent}. 냉매화 시 {stunTicks:seconds}초 기절, 대상별 쿨 {stunCooldownTicks:seconds}초.";
            case "job_pirate_g1" -> "만기 보상 +{rewardBonus:percent}. 라운드 첫 만기 개봉: 남은 상자 만기 -{roundReduction}라운드. 환급·성장·뱃사공 유지, 연쇄도 건별 1회 정산.";
            case "job_pirate_g2" -> "제독·항해사 첫 {openingAttacks}회 공격: {shotSpacingTicks}틱 간격 포탄 {extraShots}발 추가. 반경 {radius}·{maxTargets}기에 {damageRatio:percent} 물리 피해. 사거리 내 재표적.";
            case "job_pirate_p" -> "즉시 함포 {initialStacks}중첩. 만기 {chestsPerStack}회마다 +{diamondReward}다이아·1중첩. 전투에 1중첩 소비, 최강 공격력 {damageRatio:percent} 고정. {intervalTicks:seconds}초마다 {shots}포격, 반경 {radius}·{maxTargets}기.";
            case "job_insect_towers_g1" -> "첫 {revivalCount}회 부활 대기 -{waitReduction:percent}. 이때 부활하면 반경 {radius}의 대기 중 다른 벌레 최대 {maxNeighbors}기 대기 -{neighborReductionTicks:seconds}초.";
            case "job_insect_towers_g2" -> "원본 사망: 체력·공격 {statRatio:percent} 유충 {spawnCount}기, 대기 포함 최대 {activeCap}. 사망·접촉 폭발 계승, 추가 생성·부활·거점 없음.";
            case "job_future_agency_towers_g2" -> "원본당 연결 생존자 최대 {survivorCap}기. 함께 생존하면 원본이 받는 피해 -{damageReduction:percent}.";
            case "job_future_agency_towers_p" -> "원본당 라운드 1회 치명 피해: 원본·연결 생존자를 시작 체력으로 복원, 사망 생존자도 복귀. 추가 생성·성장·시작 효과 없음.";
            case "job_magic_school_g1" -> "최종 방어 이동 때 아군→내 레인 1기, 나→전투 중 아군별 1기 지원. 최대·현재 체력순, 마법학교·마왕 제외. 수비 종료 후 합류.";
            case "job_magic_school_p" -> "즉시 {diamondReward}다이아·3대 저주 해금. 5단계 주문 마법사에 장착, 저주별 1기. 숙련도 500 이하 공격 속도 -90%.";
            case "job_developer_towers_g1" -> "경계 조건, 버퍼 오버런, 하드코딩, 은신, 지연 로딩의 추가 피해 조건 제거. 기존 불이익 유지.";
            default -> null;
        };
    }
}
