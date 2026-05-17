# 프로덕션 타워 카탈로그

## 설계 수렴 요약

두 agent를 생성자/비평자 역할로 두고 20회 이상 반복 검토한 결론은 다음과 같다.

- 경쟁형 income TD에서는 몹팩 대응이 필수이므로 대부분의 타워가 스플래시를 가져야 한다.
- 전역 자원, 전역 영혼, 이동형 타워, 타워별 소환수는 1차 구현 범위에서 제외한다. 기존 lane/tower reset/final defense 흐름과 충돌하거나 snowball 위험이 크다.
- 팩션 특색은 tower-local capped stack으로 둔다. GameTest로 검증 가능하고, HUD/명령 출력에서 설명하기 쉽다.
- 1차 설치 타워는 세 팩션, 각 3개로 유지한다. 대신 각 1차 타워가 2개의 2차 분기를 가지고, 각 분기는 3차 특화 궁극 타워로 이어진다.
- 프로덕션 카탈로그 총량은 45개다. 직접 설치 가능한 타워 9개, 업그레이드 전용 2차 타워 18개, 3차 궁극 타워 18개로 구성한다.

## 직업

### 주민 기술자

ID: `semion-td:villager_engineer`

주민 타워만 사용할 수 있다. 주민 타워는 `Emerald` 스택으로 전투가 길어질수록 피해량이 오른다.

### 언데드 강령술사

ID: `semion-td:undead_necromancer`

언데드 타워만 사용할 수 있다. 언데드 타워는 `Decay` 스택과 죽음 폭발로 몹팩을 연쇄 정리한다.

### 동물 조련사

ID: `semion-td:beast_tamer`

동물 타워만 사용할 수 있다. 동물 타워는 `Rage` 스택으로 교전 중 공격 속도와 압박 대응력이 오른다.

## 명령

```text
/semiontd job list
/semiontd job select villager_engineer
/semiontd tower list
/semiontd tower build villager_crossbow_post
/semiontd tower upgrades
/semiontd tower upgrade militia_net
```

`/semiontd tower build <id>`는 현재 위치가 자신의 lane area 안이고, 현재 직업이 해당 팩션 타워를 허용하며, 다이아가 충분할 때 타워를 설치한다.
2차/3차 타워는 업그레이드 전용이므로 `tower build`로 직접 설치할 수 없다.
`/semiontd tower upgrades`와 `/semiontd tower upgrade <id>`는 현재 위치의 내 프로덕션 타워를 기준으로 동작한다.

## 타워 생성 및 등록

프로덕션 타워 정의는 `src/main/java/kim/biryeong/semiontd/tower/ProductionTowerCatalog.java`에 모은다.
새 타워를 추가할 때는 `TowerType`, `ProductionTowerBehavior`, `registerLine` 등록 순서로 작업한다.

### 1. 타워 스탯 정의

`tower(...)`로 가격, 체력, 사거리, 공격력, 공속, 어그로, 외형, 설명, 업그레이드 선택지를 정의한다.
입력한 공격력은 그대로 최종 공격력으로 쓰인다. 전역 `1.1배` 같은 숨은 보정은 두지 않는다.

```java
private static final TowerType SPECIAL_TOWER_T2 = tower(
        "special_tower_t2", "특수 타워", 200, 90, 12.0, 8.5, 14, 10,
        "minecraft:villager",
        List.of("특수 능력을 사용하는 2차 타워입니다."),
        List.of(upgrade("special_tower_t3", "강화 특수 타워", SPECIAL_TOWER_T3, 260))
);
```

### 2. 타워 행동 정의

`behavior(...)`로 팩션 능력, 스플래시, 스택 증가 규칙, 처치 폭발을 정의한다.
스플래시 거리도 최종 칸 수를 직접 적는다. 최대 스플래시를 3칸으로 쓰고 싶으면 `3.0`을 그대로 넣는다.

```java
private static final ProductionTowerBehavior SPECIAL_TOWER_T2_BEHAVIOR =
        behavior(TowerFaction.VILLAGER, "Emerald", 1.5, 0.6, 8, 0.04, 0.0, true, false, 0.0, 0.0);
```

### 3. 기본 타워 등록

일반 타워는 기존처럼 `registerLine(...)`과 `branch(...)`에 타입과 행동만 연결한다.

```java
registerLine(
        SPECIAL_STARTER,
        SPECIAL_STARTER_BEHAVIOR,
        branch(SPECIAL_LEFT_T2, SPECIAL_LEFT_T2_BEHAVIOR, SPECIAL_LEFT_T3, SPECIAL_LEFT_T3_BEHAVIOR),
        branch(SPECIAL_RIGHT_T2, SPECIAL_RIGHT_T2_BEHAVIOR, SPECIAL_RIGHT_T3, SPECIAL_RIGHT_T3_BEHAVIOR)
);
```

### 4. 특수 능력 타워 등록

타워마다 별도 로직이 필요하면 `ProductionTower`를 상속한 클래스를 만들고, 등록할 때 팩토리를 넘긴다.
팩토리를 넘긴 타워는 배치와 업그레이드 양쪽에서 해당 클래스 인스턴스로 생성된다.

```java
public final class SpecialTower extends ProductionTower {
    public SpecialTower(
            TowerType type,
            ProductionTowerBehavior behavior,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, behavior, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }
}
```

```java
registerLine(
        SPECIAL_STARTER,
        SPECIAL_STARTER_BEHAVIOR,
        SpecialTower::new,
        branch(SPECIAL_LEFT_T2, SPECIAL_LEFT_T2_BEHAVIOR, SPECIAL_LEFT_T3, SPECIAL_LEFT_T3_BEHAVIOR),
        branch(SPECIAL_RIGHT_T2, SPECIAL_RIGHT_T2_BEHAVIOR, SpecialTower::new, SPECIAL_RIGHT_T3, SPECIAL_RIGHT_T3_BEHAVIOR, SpecialTower::new)
);
```

특수 로직을 넣을 때는 `ProductionTower` 또는 상위 `TestTower`의 훅을 먼저 확인하고 오버라이드한다.
예를 들어 공격 성공, 처치, 틱 처리, 받는 피해 같은 기존 흐름에 맞춰 붙이면 배치/판매/업그레이드/직업 제한 로직을 새로 만들 필요가 없다.

## 타워

| 팩션 | ID | 이름 | 역할 |
| --- | --- | --- | --- |
| 주민 | `villager_crossbow_post` | 주민 쇠뇌 초소 | 저렴한 시작 타워, 작은 스플래시, 킬 기반 Emerald 성장 |
| 주민 | `villager_bell_mortar` | 주민 종 포대 | 주력 스플래시, 공격 기반 Emerald 성장 |
| 주민 | `villager_emerald_lens` | 주민 에메랄드 렌즈 | 장거리 보스/누수 대응, 작은 스플래시 |
| 언데드 | `undead_bone_spitter` | 언데드 뼈 발사기 | 저렴한 시작 타워, 뼈 파편 스플래시 |
| 언데드 | `undead_grave_bombard` | 언데드 묘지 폭격기 | 주력 광역 타워, Decay 죽음 폭발 강화 |
| 언데드 | `undead_soul_reaper` | 언데드 영혼 수확자 | 보스/고체력 대응, Decay 누적 피해 |
| 동물 | `beast_wolf_den` | 동물 늑대 소굴 | 빠른 시작 타워, Rage로 공격 속도 증가 |
| 동물 | `beast_boar_crasher` | 동물 멧돼지 돌격대 | 짧은 사거리 광역 bruiser |
| 동물 | `beast_hawk_roost` | 동물 매 둥지 | 장거리 anti-leak, 작은 스플래시 |

## 업그레이드 트리

표의 왼쪽은 직접 설치 가능한 1차 타워이며, 각 타워는 두 개의 2차 분기와 각 분기의 3차 궁극 타워를 가진다.

| 1차 타워 | 2차 분기 | 3차 궁극 타워 |
| --- | --- | --- |
| `villager_crossbow_post` | `militia_net` 민병 감시망 | `emerald_sentry` 에메랄드 파수대 |
| `villager_crossbow_post` | `bolt_gallery` 쇠뇌 연사대 | `royal_bolt_bastion` 왕실 쇠뇌 성채 |
| `villager_bell_mortar` | `foundry_chorus` 종 주조 합창대 | `grand_bell_foundry` 대종 주조소 |
| `villager_bell_mortar` | `guild_auditor` 길드 감사관 | `emerald_treasury` 에메랄드 회계청 |
| `villager_emerald_lens` | `prism_observatory` 프리즘 관측소 | `emerald_astrarium` 에메랄드 천문대 |
| `villager_emerald_lens` | `market_ward` 시장 수호 렌즈 | `city_guardian_lens` 도시 수호 렌즈 |
| `undead_bone_spitter` | `shard_swarm` 뼈 파편 무리 | `skeleton_storm` 해골 폭풍 무리 |
| `undead_bone_spitter` | `marrow_sniper` 골수 저격수 | `reaper_marrow_cannon` 사신 골수포 |
| `undead_grave_bombard` | `plague_burial` 역병 매장지 | `plague_catacomb` 역병 카타콤 |
| `undead_grave_bombard` | `ossuary_mortar` 납골 박격포 | `colossal_ossuary` 거대 납골포 |
| `undead_soul_reaper` | `void_scythe` 공허 낫꾼 | `void_reaper` 공허 수확자 |
| `undead_soul_reaper` | `soul_lantern` 영혼 등불 | `wraith_beacon` 망령 등대 |
| `beast_wolf_den` | `alpha_pack` 우두머리 무리 | `frenzy_alpha_den` 광란 우두머리 굴 |
| `beast_wolf_den` | `frost_fang` 서리 송곳니 | `white_fang_den` 백색 송곳니 굴 |
| `beast_boar_crasher` | `stampede_pack` 돌진 무리 | `ravager_stampede` 파괴 돌진대 |
| `beast_boar_crasher` | `iron_tusk_guard` 철엄니 방벽 | `iron_tusk_bastion` 철엄니 보루 |
| `beast_hawk_roost` | `storm_hawk` 폭풍 매 둥지 | `thunder_hawk_sanctum` 천둥 매 성소 |
| `beast_hawk_roost` | `dive_hunter` 급강하 사냥대 | `hawk_command_roost` 매 사령탑 |

분기 역할은 다음과 같다.

- 주민: `Emerald` 스택 기반 장기 교전 성장, 광역 정리 또는 보스/누수 저격으로 분화한다.
- 언데드: `Decay`와 죽음 폭발을 강화해 몹팩 연쇄 정리 또는 고체력 대상 절단으로 분화한다.
- 동물: `Rage` 기반 공격 속도와 교전 템포를 강화하며, 돌진형 광역 또는 장거리 anti-leak으로 분화한다.

## 검증

- 프로덕션 직업 3개와 45개 타워 등록 GameTest.
- 9개 1차 타워가 각각 2개 2차 분기와 1개 3차 궁극 후속 업그레이드를 가지는지 검증하는 GameTest.
- 직업별 타워 허용/거부 GameTest.
- 업그레이드 전용 타워를 직접 설치할 수 없고, 프로덕션 타워 업그레이드가 런타임 타워와 판매 환불 비용을 보존하는 GameTest.
- 스플래시 타워가 packed monsters를 동시에 피해 입히는 GameTest.
- 주민 타워가 교전 후 Emerald 스택을 얻고 피해 배율이 증가하는 GameTest.
