# 설정 파일

Semion TD는 서버 시작 또는 `/semiontd reload` 시 `config/semion-td/` 아래 설정 파일을 읽습니다. 파일이 없으면 `SemionConfigLoader`가 기본값으로 생성합니다. 이 문서는 운영자가 수정할 수 있는 필드와 주의사항만 다룹니다.

스카이박스와 음악처럼 생성 리소스팩에 들어가는 파일은 서버 시작 또는 `/semiontd resourcepack reload` 실행 시 읽습니다. 일반 `/semiontd reload`에는 포함되지 않습니다.

## 자동 생성 설정 파일

| 파일 | 역할 |
|---|---|
| `economy.json` | 시작 재화, 에메랄드 생산, 타워 제한, 처치 보상, 팀 재화 요청을 설정합니다. |
| `wave.json` | 라운드별 웨이브 몬스터와 무한 웨이브를 설정합니다. |
| `map.json` | 아레나 template ID, 원점, 시간, region marker 이름을 설정합니다. |
| `progression.json` | 경기 참가, 승리, 패배 보상을 설정합니다. |
| `rating.json` | ELO 계산, 팀 ELO 매칭, 리더보드, 기여도 가중치를 설정합니다. |
| `persistence.json` | SQLite 또는 MongoDB 저장소를 설정합니다. |
| `tower_balance.json` | 타워 수치, 업그레이드 비용, 능력값, 주민 ADV 값을 설정합니다. |
| `summons.json` | 인컴 유닛의 비용, 수입, 체력, 역할, 능력값을 설정합니다. |
| `leader_targeting.json` | 선두 팀 타겟팅 제한과 유지 라운드를 설정합니다. |
| `income_lane_routing.json` | 인컴 유닛을 어느 라인으로 보낼지 정합니다. |
| `monster_scaling.json` | 오래 살아남은 몬스터의 체력과 공격력 증가를 설정합니다. |
| `vfx.json` | 타워 공격과 AOE VFX, 비동기 계획, 전송 예산을 설정합니다. |
| `tips.json` | 접속 및 경기 중 표시할 팁, 표시 주기, 전체 활성화 여부를 설정합니다. |
| `traits.json` | 특성 선택 단계의 활성화 여부와 제한 시간을 설정합니다. |
| `trait_balance.json` | 개별 특성의 효과 수치와 지속 시간을 설정합니다. |

`wave.json`이 현재 파일명입니다. 예전 복수형 파일명은 `wave.json`이 없을 때만 읽는 레거시 경로입니다.

## 운영 데이터

다음 파일은 설정 파일처럼 직접 편집하지 않습니다.

| 파일 | 역할 |
|---|---|
| `profiles.json` | 플레이어 프로필 데이터입니다. |
| `cosmetics.json` | 치장 상품 ID, 가격, 전체 `ItemStack` 데이터를 등록 순서대로 저장합니다. |
| `build_guides.json` | 플레이어가 저장한 빌드 기록입니다. |
| `semiontd.db` | SQLite 영속 저장소입니다. `persistence.json`의 `sqlitePath` 기본값입니다. |
| `semiontd.db-shm`, `semiontd.db-wal` | SQLite 런타임 보조 파일입니다. |
| `job-statistics.db` | 일반 경기의 직업별 파생 통계입니다. 웹이나 외부 도구에서는 읽기 전용으로 조회합니다. |
| `job-statistics.db-shm`, `job-statistics.db-wal` | 직업 통계 SQLite의 런타임 보조 파일입니다. |
| `balance_notification_state.json` | 마지막으로 확인한 밸런스 저장소 커밋 SHA입니다. 서버가 원자적으로 갱신하므로 직접 편집하지 않습니다. |

운영 데이터는 서버를 끈 뒤 백업하고, 가능하면 명령어로 갱신합니다.

`profiles.json`에는 플레이어가 구매한 치장 상품 ID와 슬롯별 착용 선택, 선택한 스카이박스 ID, 팁 수신 여부도 저장됩니다. `selectedCosmeticIds`가 현재 필드이며, 예전 `selectedCosmeticId`만 있는 프로필은 읽을 때 자동으로 이전합니다.

## `cosmetics.json`: 치장 상품 목록

`/semiontd cosmetic add <id> <price> [head|offhand]`를 처음 실행하면 `cosmetics.json`이 생성됩니다. 서버는 주 손 아이템을 1개로 복사하고 이름, 로어, 아이템 모델, 인챈트 등 모든 컴포넌트를 함께 저장합니다. 슬롯을 생략하면 `head`를 사용합니다. 파일을 저장할 때 임시 파일을 만든 뒤 교체하므로, 운영자는 이 파일을 직접 수정하지 않는 편이 안전합니다. 파일을 직접 수정했다면 `/semiontd cosmetic reload`로 다시 불러옵니다.

```json
{
  "entries": [
    {
      "id": "gold_crown",
      "price": 100,
      "slot": "head",
      "item": {
        "id": "minecraft:golden_helmet",
        "count": 1
      }
    }
  ]
}
```

위 `item` 내용은 실제 아이템 컴포넌트에 따라 달라집니다. `slot`은 `head` 또는 `offhand`이며, 필드가 없는 기존 항목은 `head`로 읽습니다. 상품 순서는 GUI 표시 순서이며 한 페이지에 45개를 표시합니다. `update`는 같은 ID의 상품과 가격을 바꾸고 온라인 착용본도 갱신합니다. `remove`는 판매와 착용 선택만 해제합니다. 플레이어의 구매 기록은 남으므로, 같은 ID를 다시 등록하면 이전 구매자도 계속 보유한 것으로 처리합니다. 완전히 다른 상품은 새 ID를 사용합니다.

구매와 착용 선택은 `profiles.json`에 저장되며 재접속, 서버 재시작, 경기 전환 뒤에도 유지됩니다. 머리와 왼손은 함께 선택할 수 있고 같은 슬롯에는 한 상품만 남습니다. 결제 저장에 실패하면 포인트 차감과 구매 기록을 모두 되돌립니다.

## `skyboxes/`: 개인 스카이박스

`config/semion-td/skyboxes/`에 PNG 파일을 넣으면 서버 시작 시 생성 리소스팩에 자동으로 추가됩니다. 음악 파일을 `music/`에 추가하는 방식과 같습니다. 실행 중에는 `/semiontd resourcepack reload`로 두 디렉터리를 다시 읽습니다.

새 이미지를 만들 때는 [스카이박스 템플릿](skybox-template/README.ko.md)의 얼굴 배치도와 편집용 SVG를 사용합니다.

- 텍스처는 업로드한 템플릿과 같은 `2:1` 큐브맵 아틀라스여야 합니다. 예: `1024x512`, `2048x1024`.
- 파일명이 스카이박스 ID가 됩니다. `Red Nebula.png`는 `red_nebula`로 등록됩니다.
- 파일명은 영문 소문자, 숫자, `_`, `-` 조합을 권장합니다.
- 불투명 픽셀의 alpha는 생성팩에 넣을 때 자동으로 `252`로 바뀝니다. 이 값으로 스카이박스에만 fog 우회를 적용합니다.
- 첫 번째 파일이 기본 스카이박스입니다. 플레이어가 별도로 선택하지 않았다면 기본값을 봅니다.
- `/semiontd skybox <id>`로 개인 스카이박스를 선택하고 `/semiontd skybox off`로 끕니다.

스카이박스는 경기장 월드에서만 표시됩니다. 플레이어마다 별도의 가상 `item_display`를 받으므로 같은 팀과 같은 위치에 있어도 서로 다른 스카이박스를 선택할 수 있습니다. 파일 추가, 삭제, 교체는 `/semiontd resourcepack reload`로 반영합니다. 명령 실행 후 접속자는 갱신된 생성팩을 다시 받습니다.

## `tips.json`

플레이어 접속 시 팁을 한 번 표시합니다. 이후 실제 경기 참가자와 샌드박스 플레이어에게 플레이 시간 기준으로 팁을 반복 표시합니다. 관전 중에는 주기 시간이 흐르지 않습니다.

```json
{
  "enabled": true,
  "joinEnabled": true,
  "joinMessage": "<gold><bold>TIP</bold></gold> <gray>나침반을 우클릭하면 타워 설치 창을 열 수 있습니다.</gray>",
  "intervalSeconds": 120,
  "messages": [
    "<gold><bold>TIP</bold></gold> <gray>업그레이드 가격은 대상 타워의 설치 가격과 별도로 설정됩니다.</gray>"
  ]
}
```

- `enabled`: 서버 전체 팁 표시 여부입니다. `false`이면 개인 설정과 관계없이 표시하지 않습니다.
- `joinEnabled`: 접속 팁만 켜거나 끕니다. 경기 중 주기 팁에는 영향을 주지 않습니다.
- `joinMessage`: 접속할 때 한 번 표시할 MiniMessage 문자열입니다.
- `intervalSeconds`: 경기 중 팁 간격입니다. 기본값은 `120`초이며 `1..86400` 범위로 보정됩니다.
- `messages`: 경기 중 순서대로 반복할 MiniMessage 문자열 목록입니다. 빈 문자열은 무시합니다.

플레이어는 `/semiontd tip off`로 팁을 끄고 `/semiontd tip on`으로 다시 켤 수 있습니다. 이 선택은 `profiles.json`에 저장됩니다. 설정 파일 변경은 `/semiontd reload`로 반영합니다.

## `traits.json`

특성 선택 단계만 제어합니다. 개별 특성의 효과 수치는 `trait_balance.json`에서 설정합니다.

```json
{
  "enabled": true,
  "selectionDurationSeconds": 45
}
```

- `enabled`: `false`이면 특성 선택 단계를 건너뛰고 모든 참가자를 `(none, none)`으로 기록합니다.
- `selectionDurationSeconds`: 참가자 확정 후 최종 선택을 기다리는 시간입니다. 0 이하이면 기본값 `45`초를 사용합니다.

등록된 실전 특성이 하나도 없고 `none`만 있으면 `enabled`가 `true`여도 선택 단계를 건너뜁니다. 따라서 특성 기획 전에는 기존 게임 시작 시간이 늘어나지 않으며, 첫 실전 특성을 등록하면 별도 코드 플래그 없이 선택 단계가 열립니다. 변경 사항은 `/semiontd reload`로 다음 경기부터 반영합니다.

## `trait_balance.json`

개별 특성의 주특성 기준 수치를 설정합니다. 비율은 `0.15 = 15%` 형식이며 부특성에는 자동으로 50%가 적용됩니다. 파일이 없거나 새 특성·필드가 빠져 있으면 기본값을 자동으로 추가합니다.

```json
{
  "traits": {
    "mobilization_grant": {
      "startingDiamond": 150.0
    },
    "clean_lane_bonus": {
      "incomeRatio": 0.15
    },
    "rapid_deployment": {
      "refundRateAfterWave": 0.9
    },
    "berserk_summons": {
      "attackDamageBonus": 0.2,
      "attackSpeedBonus": 0.1
    },
    "interception_doctrine": {
      "damageBonus": 0.15
    },
    "opening_salvo": {
      "attackSpeedBonus": 0.15,
      "durationSeconds": 15.0
    },
    "wavebreaker_doctrine": {
      "damageBonus": 0.15
    },
    "fortitude": {
      "maxHealthBonus": 0.2,
      "warlockCoreMaxHealthBonus": 0.1
    },
    "double_edged_sword": {
      "outgoingDamageBonus": 0.25,
      "incomingDamageBonus": 0.25
    },
    "strength_in_numbers": {
      "damageBonusPerTower": 0.02
    },
    "diversity": {
      "damageBonusPerType": 0.02
    },
    "supply_depot": {
      "towerLimitBonus": 4.0
    },
    "transcendence": {
      "activationDelaySeconds": 20.0,
      "damageBonus": 0.3
    }
  }
}
```

`/semiontd reload`로 다시 읽으면 이후 계산과 특성 선택 UI에 같은 값이 반영됩니다. 이미 발동한 스피드러너 시간제 효과는 유지되고, 다음 웨이브 발동부터 새 수치와 지속 시간을 사용합니다.

`supply_depot.towerLimitBonus`는 모든 라운드의 타워 제한에 더해집니다. 예를 들어 기본 초기 제한 5와 상한 23은 주특성에서 각각 9와 27, 부특성에서 각각 7과 25가 됩니다.

## `economy.json`

주요 필드:

- `startingDiamond`: 시작 다이아입니다. 예전 이름 `startingMineral`도 읽습니다.
- `startingEmerald`: 시작 에메랄드입니다. 예전 이름 `startingGas`도 읽습니다.
- `startingIncome`: 시작 인컴입니다.
- `emeraldCap`: 라운드별 에메랄드 보유 상한입니다. `base`, `roundOffsetMultiplier`, `roundOffsetStep`, `flatBonus`를 씁니다.
- `emeraldProduction`: 기본 에메랄드 생산량과 `gasup`/`emeraldup` 업그레이드 비용입니다.
- `towerLimit`: 라운드별 타워 제한과 `/semiontd tower limitup` 구매 비용입니다.
- `killReward`: 다른 라인과 최종 방어 구간 처치 보상 보정을 설정합니다.
- `teamTransfer`: `/semiontd money request`와 `/semiontd money accept`의 허용 여부, 쿨다운, 라운드별 최대 요청량입니다.
- `emeraldIncomeBoost`: 지정 라운드 이후 에메랄드 인컴 배율을 올립니다.

음수 값은 대부분 거절됩니다. 비용과 제한을 낮출 때는 `towerLimit`과 `emeraldProduction`을 같이 확인합니다.

## `wave.json`

주요 필드:

- `rounds`: 고정 라운드 목록입니다. 각 항목은 `round`와 `lanes`를 가집니다.
- `infiniteFromRound`: 무한 웨이브가 시작되는 라운드입니다.
- `infinite`: 예전 형식의 단일 무한 웨이브입니다. `infiniteTemplates`가 없을 때 사용합니다.
- `infiniteTemplates`: 무한 웨이브 후보 목록입니다. 매 라운드 하나를 같은 확률로 뽑으며 모든 팀에 같은 구성을 적용합니다.
- `lanes`: `default`, `lane_1` 같은 키로 라인별 몬스터 목록을 지정합니다.
- `spawnMode`: 몬스터 종류가 여러 개일 때 소환 순서를 정합니다. `SEQUENTIAL`은 종류별로 전부 소환하고, `ROUND_ROBIN`은 종류마다 한 마리씩 번갈아 소환합니다. 기본값은 `SEQUENTIAL`입니다.
- `spawnIntervalTicks`: 같은 라인에 웨이브 몬스터를 소환하는 간격입니다. `1`이면 매 tick 한 마리를 소환합니다. 인컴 소환에는 적용되지 않습니다.

몬스터 항목 필드:

- `id`: 웨이브 몬스터 ID입니다.
- `health`, `armor`, `attackDamage`: 전투 수치입니다.
- `attackKind`: `MELEE` 또는 `RANGED` 계열 공격 타입입니다.
- `entityType`: 화면에 표시할 바닐라 엔티티 ID입니다. 크리퍼 폭발이나 블레이즈 화염구 같은 바닐라 능력을 부여하지는 않습니다.
- `blockbenchModelId`: BIL 모델 ID입니다. 없으면 `null`을 둡니다.
- `dimensions`: 몬스터 충돌 크기입니다.
- `mineralReward`: 처치 시 다이아 보상입니다.
- `count`: 소환 수입니다.
- `targetPriority`: 일반 타워가 공격 대상을 고를 때 더하는 우선순위입니다. 기본값은 `0`이며 값이 높을수록 먼저 공격받습니다.
- `movementSpeedMultiplier`: 기본 이동 속도 배율입니다. 기본값은 `1.0`입니다.
- `attackRange`: 공격 사거리입니다. 생략하면 근접은 `2.5`, 원거리는 `6.0`블록입니다.
- `attackIntervalTicks`: 공격 간격입니다. 기본값은 `13`tick이며 낮을수록 자주 공격합니다.

탱커와 원거리를 섞는 예시:

```json
{
  "round": 8,
  "spawnMode": "ROUND_ROBIN",
  "spawnIntervalTicks": 1,
  "lanes": {
    "default": [
      {
        "id": "tank_8",
        "health": 25.0,
        "armor": 4.0,
        "attackDamage": 1.0,
        "attackKind": "MELEE",
        "entityType": "minecraft:husk",
        "mineralReward": 5,
        "count": 20,
        "targetPriority": 45,
        "movementSpeedMultiplier": 0.95,
        "attackRange": 2.5,
        "attackIntervalTicks": 18
      },
      {
        "id": "ranged_8",
        "health": 12.0,
        "armor": 0.0,
        "attackDamage": 1.5,
        "attackKind": "RANGED",
        "entityType": "minecraft:skeleton",
        "mineralReward": 5,
        "count": 20,
        "targetPriority": 0,
        "movementSpeedMultiplier": 0.8,
        "attackRange": 7.0,
        "attackIntervalTicks": 18
      }
    ]
  }
}
```

타워가 몬스터를 고르는 점수는 `라인 진행도 * 100 + targetPriority + 역할 보너스`입니다. 처형이나 특정 역할 우선 공격처럼 고유 타게팅이 있는 타워는 자체 규칙을 먼저 사용합니다. 타워 설정의 `aggroPriority`는 반대 방향인 몬스터가 방어 대상을 고를 때 쓰므로 `targetPriority`와 용도가 다릅니다.

`infiniteTemplates`가 있으면 `infinite`보다 우선합니다. 선택된 템플릿은 `infiniteFromRound`부터 사용하며, 이후 라운드마다 체력이 `40%`씩 증가합니다. 같은 템플릿이 연속으로 선택될 수 있습니다. 웨이브와 인컴 몬스터가 라인 끝에 도달하거나, 라인 타워를 모두 파괴하거나, 시간 초과로 최종 방어선에 강제 이동하면 공격 사거리가 `2`블록으로 제한됩니다. 최종 방어선 타워는 7블록 안의 몬스터만 공격 대상으로 인식합니다. 일반 라인에서는 설정된 `attackRange`를 그대로 사용합니다.

기본 무한 웨이브는 동물, 오버월드 몬스터, 네더 몬스터 템플릿을 하나씩 제공합니다. 세 템플릿은 같은 수량·총 체력·총 보상을 사용하며 몹 구성과 역할 조합만 다릅니다.

기본 웨이브는 1~5라운드에 동물, 6~15라운드에 오버월드 몬스터, 16라운드 이후에 네더 몬스터를 사용합니다. `entityType`을 바꿀 때는 가스트나 파괴수처럼 화면을 크게 가리는 엔티티를 피하는 편이 좋습니다.

`rounds`는 라운드 번호 기준으로 정렬됩니다. 특정 라인만 다른 웨이브를 넣을 때도 `default` 구성을 남겨두면 누락 라인 처리 실수를 줄일 수 있습니다.

## `map.json`

주요 필드:

- `templateId`: 아레나 template ID입니다. 기본값은 `semion-td:arena`입니다.
- `originX`, `originY`, `originZ`: template 배치 원점입니다.
- `timeOfDay`: 기존 설정 파일 호환을 위해 유지합니다. SemionTD 로비와 경기장은 생성·로드 시 정오(6000틱)로 고정하며, 독립 시계를 일시 정지하고 일주기를 끕니다. 야간 값이 저장되어 있어도 정오를 적용합니다. 다른 서버 월드의 시간은 바꾸지 않습니다.
- `regions`: template 내부 marker 이름입니다. `teamSpawn`, `laneSpawn`, `lanePath`, `laneWaypoint`, `finalWaypoint`, `bossSpawn`, `finalDefenseTower`를 씁니다.

맵 설정은 다음 게임 생성부터 반영됩니다. 진행 중인 게임의 맵을 바꾸려면 게임을 리셋한 뒤 다시 생성합니다.

## `progression.json`

주요 필드:

- `playReward`: 경기 참가 기본 보상입니다.
- `winBonusReward`: 승리 추가 보상입니다.
- `lossReward`: 패배 보상입니다.

승리 보상은 `playReward + winBonusReward`, 패배 보상은 `playReward + lossReward`로 계산합니다.

## `rating.json`

주요 필드:

- `enabled`: ELO 기록을 켜거나 끕니다.
- `teamEloMatchmakingEnabled`: 팀 ELO 기반 매칭을 켜거나 끕니다.
- `eloKFactor`: 표시 ELO 조정 폭입니다.
- `initialDisplayElo`, `initialMu`, `initialSigma`: 신규 플레이어 초기값입니다.
- `leaderboardLimit`: 순위표 출력 수입니다.
- `minimumParticipants`: rating 반영 최소 참가자 수입니다.
- `excludeSpectators`: 관전자를 rating 계산에서 제외합니다.
- `contributionWeightingEnabled`: 기여도 가중치를 켭니다.
- `contributionMultiplierMin`, `contributionMultiplierMax`: 기여도 보정 배율 범위입니다.
- `defenseContributionWeight`, `pressureContributionWeight`, `economyContributionWeight`, `assistContributionWeight`: 기여도 항목별 비중입니다. 합계는 `1.0`이어야 합니다.

운영 중 전체 ELO를 초기화할 때는 파일을 직접 지우지 말고 `/semiontd rating softreset`을 사용합니다. 이 명령어는 백업을 만들고 같은 관리자의 2회 확인을 요구합니다.

## `persistence.json`

저장소 생성·SQLite 경로 해석·파일 폴백 이관은 `PersistenceRepositoryFactory`가 담당합니다. 평점 초기화의 백업·체크포인트·저장소 정리는 `PersistenceRatingBackup`이 담당하고, `SemionGameManager`는 명령 흐름과 서비스 재연결을 유지합니다. 파일명, SQLite 테이블, 저장 데이터 형식은 이 책임 분리로 바뀌지 않습니다. 백업 검증에서는 파일 존재뿐 아니라 저장소를 다시 열어 프로필·이벤트·적용 마커가 보존되는지 확인합니다.

주요 필드:

- `backend`: `SQLITE` 또는 MongoDB 계열 저장소 타입입니다.
- `sqlitePath`: SQLite 파일 경로입니다. 기본값은 `semiontd.db`입니다.
- `mongodbUri`: MongoDB 접속 문자열입니다.
- `mongodbDatabase`: MongoDB database 이름입니다.
- `externalDbRequired`: 외부 DB 연결 실패 시 서버 구동을 실패로 볼지 정합니다.

SQLite를 쓸 때는 `semiontd.db`, `semiontd.db-shm`, `semiontd.db-wal`을 같은 시점에 백업합니다.

## `tower_balance.json`

파일이 크기 때문에 전체 기본값을 문서에 복사하지 않습니다. 수치 조정 방법은 [타워 수치 설정](tower-balance-reference.ko.md)을 봅니다.

- `towers`: 타워 ID별 기본 수치입니다.
- `upgradeCosts`: `fromTowerId->upgradeId` 형태의 업그레이드 비용입니다. 업그레이드 가격은 `towers`의 `mineralCost`로 정해지지 않습니다.
- `abilities`: 타워별 특수 능력 수치입니다.
- `illusionCloneQueue`: 환영 복제 큐 설정입니다.
- `villagerAdv`: 주민 ADV 경험치와 평판 설정입니다.

타워 ID는 Java의 `TowerType.id()`와 맞아야 합니다. 새 타워를 코드에 추가했다면 `ProductionTowerCatalogs.reloadBuiltIns(...)` 흐름과 `tower_balance.json` 기본값을 함께 확인합니다.

## `summons.json`

파일이 크기 때문에 인컴 유닛별 전체 기본값은 생성 파일에서 확인합니다.

인컴 유닛 필드:

- `id`, `displayName`, `enabled`: 내부 ID, 표시명, 사용 여부입니다.
- `emeraldCost`: 소환 비용입니다.
- `incomeGain`: 소환 시 증가하는 인컴입니다.
- `maxHealth`, `armor`, `resistance`, `attackDamage`: 전투 수치입니다.
- `attackKind`, `damageType`: 공격 방식과 피해 타입입니다.
- `entityTypeId`: 바닐라 엔티티 ID입니다.
- `blockbenchModelId`: BIL 모델 ID입니다. 없으면 `null`로 처리됩니다.
- `dimensions`: 몬스터 크기입니다.
- `diamondReward`: 처치한 상대가 받는 다이아 보상입니다.
- `tier`, `roles`: UI 분류와 역할 태그입니다.
- `abilityActivations`, `abilityValues`: 특수 능력 발동 방식과 수치입니다.
- `description`: UI 설명입니다.

`summons.json`을 수정하면 `/semiontd reload` 후 소환 UI와 `/semiontd summons` 출력으로 확인합니다.

마왕 인컴 파견의 암흑 신관·오우거 광역 공격은 주 대상 100%, 추가 대상 각각 50% 피해입니다. 두 유닛의 `abilityValues.maxTargets` 기본값은 주 대상을 포함한 5이며, 가까운 대상부터 선택합니다. 일반 침공의 광역 공격도 대상 수 제한은 적용하되 피해 배율은 기존 값을 유지합니다. 파견 드워프는 단일 공격이며 일반 침공 드워프의 기존 관통 공격은 변경하지 않습니다.

암흑 신관 회복은 `healAmount × 성장 배율 + 대상 최대 체력 × healMaxHealthRatio`입니다. 기본값은 각각 `16`, `0.02`이며 성장 배율은 신관 최대 체력 / 기본 최대 체력(최소 1)입니다. 누락된 키만 보충하고 기존 운영자 설정은 보존합니다. 이미 `healMaxHealthRatio`를 지정한 운영 설정에는 기본값 변경이 덮어써지지 않으므로 배포 시 해당 값을 확인해야 합니다.

## `leader_targeting.json`

- `maxTargetingTeamsPerTarget`: 한 대상 팀을 동시에 타겟팅할 수 있는 팀 수입니다.
- `activeTargetRounds`: 타겟 지정이 유지되는 라운드 수입니다.

## `income_lane_routing.json`

- `enabled`: 자동 라우팅 사용 여부입니다.
- `mode`: `RANDOM` 또는 `LEAST_THREAT_PRESSURE`입니다.
- `queuedThreatWeight`: 현재 큐에 쌓인 위협도 반영 비중입니다.
- `nextRoundQueuedThreatWeight`: 다음 라운드 큐 위협도 반영 비중입니다.
- `tieBreakMode`: 동률 처리 방식입니다. `RANDOM` 또는 `ROUND_ROBIN`입니다.

## `monster_scaling.json`

- `enabled`: 생존 시간 기반 스케일링 사용 여부입니다.
- `survivalDelayTicks`: 몬스터가 살아남은 뒤 스케일링을 시작하기까지의 지연 시간입니다.
- `laneBreachDelayTicks`: 라인 돌파 상태에서 스케일링을 시작하기까지의 지연 시간입니다.
- `intervalTicks`: 스케일링 적용 간격입니다.
- `healthGrowthPercentPerInterval`: interval마다 체력 증가율입니다.
- `attackDamageGrowthPercentPerInterval`: interval마다 공격력 증가율입니다.
- `scaleWaveMonsters`: 웨이브 몬스터에 적용할지 정합니다.
- `scaleIncomeMonsters`: 인컴 유닛에 적용할지 정합니다.

tick 값은 Minecraft 기준 `20 tick = 1초`로 계산합니다.

## `vfx.json`

- `enabled`: 타워 공격 VFX 전체를 켜거나 끕니다.
- `areaDamageEnabled`: 스플래시, pulse, 시체 폭발, 버프·디버프처럼 범위 효과 API에서 만드는 VFX를 켜거나 끕니다. `false`여도 직접 공격 ray는 유지됩니다.
- `asyncPlanning`: APEL 도형 계산과 packet 생성을 전용 작업 스레드에서 처리합니다. 문제를 추적할 때 `false`로 바꾸면 같은 계산을 서버 tick에서 실행합니다.
- `maxSampledHitRays`: AOE 중심에서 실제 피격 대상으로 표시할 ray 수입니다. `0..8` 범위로 보정됩니다.
- `vanilla.refillPointsPerTick`: 일반 클라이언트 라인별 particle point 충전량입니다. 기본값은 `1024`입니다.
- `vanilla.burstCapacityPoints`: 라인별로 모아둘 수 있는 point 상한입니다. 기본값은 `4096`입니다.
- `vanilla.maxPacketsPerTickPerRecipient`: 일반 클라이언트 한 명에게 tick당 보낼 particle packet 상한입니다. 기본값은 `2048`입니다.
- `gcb.maxShapeInstructionsPerTick`: GCB 클라이언트에 라인별로 보낼 도형 명령 상한입니다. 기본값은 `128`입니다.

예산은 월드 전체가 아니라 팀과 라인별로 계산합니다. 타워 소유자는 거리에 상관없이 자신의 타워 VFX를 받고, 같은 경기의 관전자는 공격 중심과 같은 월드에서 64블록 안에 있을 때 받습니다. 다른 라인 참가자에게는 보내지 않습니다.

`/semiontd reload`를 실행하면 다음 VFX 이벤트부터 새 설정이 적용됩니다. `/semiontd-debug vfx stats`로 전송량과 생략량을 확인할 수 있습니다.

내장 스타일은 `semion-td:splash`, `semion-td:pulse`, `semion-td:corpse_explosion`, `semion-td:buff`, `semion-td:debuff`입니다. 애드온 스타일도 같은 라인별 큐와 전송 예산을 사용합니다. 등록 방법은 [범위 효과 API](area-effect-api.ko.md)를 봅니다.

## `augment_balance.json`

증강 리롤은 경기 공용 5회를 사용하며 남은 횟수는 다음 증강 라운드로 이월됩니다. 현재 보이는 3장은 다음 리롤에서 제외하지만, 이전 리롤에서 지나간 카드는 다시 나올 수 있습니다. 보유 증강·직업·등급·잠금 조건과 새 3장 안의 중복 제한은 유지됩니다. 적격한 새 3장을 만들 수 없으면 횟수와 현재 제안은 그대로 유지합니다. 기존 제시 이력은 보존하며 후보 제외 조건으로 사용하지 않습니다.

증강 카드·리롤·화면 이동 버튼에는 툴팁을 표시하지 않습니다. 카드 본문의 설명은 유지하며 리롤 잔여 횟수는 버튼 이미지가 아닌 기본 UI 글꼴로 표시합니다.

엔드 빌더 `semiontd:job_end_towers_p`는 라운드당 1회 돌진·브레스 효과입니다. 기본값은 `chargeTicks=60`, `rushDamageRatio=1.0`, `stunTicks=200`, `knockbackDistance=20`, `flightHeight=10`, `burnDurationTicks=200`, `burnIntervalTicks=20`, `burnDamageRatio=0.25`입니다. 돌진·브레스는 실제 통로와 소환 영역 전체 폭을 사용하므로 기존 `width` 키는 호환을 위해 읽지만 폭을 제한하지 않습니다. 돌진과 브레스의 각 이동 판정은 60틱이며 전방 이탈·후방 재등장 오프셋은 각각 5블록입니다. `flightHeight`는 출현 마커의 높이가 아닌 실제 바닥 높이를 기준으로 합니다. 이전 복제 효과의 `statRatio`는 기존 설정 로딩 때 무시하며, 생략된 새 키는 기본값을 사용합니다. 운영 설정 파일을 자동으로 덮어쓰지 않습니다.

`enabled`와 `publicPoolEnabled`의 기본값은 모두 `true`입니다. 일반 게임의 생존 참가자에게 5·15·25라운드 준비 단계에서 증강 세 장 중 하나를 고르게 합니다. 샌드박스·튜토리얼·일반 게임이 아닌 모드에는 적용하지 않습니다. `enabled=false`는 증강 시스템 전체를, `publicPoolEnabled=false`는 정규 라운드 카드 제시를 끕니다. 기존 운영 설정의 명시적 값은 보존됩니다. 설정 변경은 실행 중 경기의 스냅샷을 바꾸지 않고 설정 재로드 후 새 경기에 반영됩니다.

ELO 기록은 `rating.json`의 `enabled=true`로 켭니다. `teamEloMatchmakingEnabled`는 별도 팀 배정 옵션이며 ELO 기록 활성화만으로 바뀌지 않습니다.

플레이어 간 피해는 공통 서버 피해 이벤트에서 차단합니다. 실제 플레이어가 가해자인 근접·소유 투사체·플레이어 출처가 기록된 간접 피해에 적용하며, 로비·경기·재접속에 관계없이 유지됩니다. 몬스터와 타워의 전투 및 플레이어형 외형만 가진 NPC에는 PvP 규칙을 적용하지 않습니다. 기존 `server.properties`의 `pvp=false`는 유지하며 별도 운영 설정 변경이나 자동 재시작은 필요하지 않습니다.

일반 매치 팀장은 참가자의 표시 ELO가 가장 높은 사람으로 정하며, 최고값 동점자는 레인 번호가 가장 낮은 사람으로 선택합니다. 일반 매치 배치·재접속·라운드 준비 시 팀장에게만 3번째 슬롯의 `팀장 타깃` 블레이즈 막대를 지급합니다.

엔드는 기존 30스택당 1단계·최대 10단계를 유지하고, 생명력 흡수의 기본 회복률은 현재 단계 ÷ 10으로 계산한다. 효율은 `clamp(30 / 기준 피해, 0.01, 1)`로, 피해 30 이하에서는 100%, 300에서는 10%, 600에서는 5%, 3,000 이상에서는 1%다. 회복량은 실제 피해 × (현재 단계 ÷ 10) × 효율이다. 기본 공격과 기존 범위 공격에 적용하며 화상·돌진에 새 흡수 효과를 추가하지 않는다.

흑마법사는 원거리 누적 흡수 10회당 0.5%·최대 7%, 근거리 현재 라운드 흡수 1회당 1%·최대 12%의 기본 흡수율을 사용한다. 효율은 `clamp(40 / 기준 피해, 0.01, 1)`로, 피해 40 이하 100%, 400에서 10%, 4,000 이상 1%다. 회복량은 대상별 실제 피해 × 기본 흡수율 × 10 × 효율이며, 원거리·근거리 모두 같은 수식을 사용하고, 기본 흡수율을 최대 단계로 나누지 않는다. 최대 스택의 실제 회복률은 피해 40 이하에서 원거리 70%·근거리 120%, 피해 400에서 7%·12%, 피해 4,000 이상에서 0.7%·1.2%다. 실제 회복률에 별도 100% 제한을 두지 않으며, 회복 후 체력은 기존 최대 체력을 넘지 않는다. 주 대상의 효율 기준은 실제 피해, 범위 대상의 기준은 범위 감쇠 전 해당 공격의 피해다. 기존 범위 피해는 원거리 50%·근거리 75%를 유지하고 별도 회복 감쇠를 중복 적용하지 않는다. 피해 400·최대 단계에서 주 대상 회복은 원거리 28·근거리 48, 방어 없는 범위 대상당 회복은 각각 14·36이다. 완전 면역으로 공격이 취소되면 범위 피해·회복도 발생하지 않는다. 근거리 흑마법사는 다른 아군 타워가 살아 있어도 생명력 흡수가 발동하며, 각성의 최후 생존 조건은 유지한다. 기존 운영 설정의 `melee_warlock_tower.lifeCap` 값은 새 기본값 0.12로 자동 덮어쓰지 않는다.

묶음 기본 설정의 `abilities.end_global.damageScale`은 50, `abilities.ranged_warlock_tower.damageScale`은 40이다. 근거리의 피해 성장 계수는 20을 유지한다. 엔드 증강 돌진은 현재 물리 피해의 100%, 브레스 화상은 현재 공격력의 25%다. 기존 운영 설정의 명시된 값은 덮어쓰지 않는다.

엔드의 `생명력 흡수` 표시값은 기존 단계 진행률 × 현재 피해 기준 효율을 소수점 첫째 자리까지 백분율로 나타내며 불필요한 `.0`은 생략한다. 피해 30 이하에서 0단계는 0%, 1단계는 10%, 10단계는 100%이며 피해 250·6단계는 7.2%이며 실제로 18을 회복한다. 알 상태에서는 부화 후 예상 피해, 부화 후에는 타워 상세창과 같은 대상 없는 현재 기본 공격 피해를 기준으로 미리 표시한다. 표시와 실제 회복률은 같은 계산식을 사용한다. 적의 방어·남은 체력 때문에 실제 피해가 달라지면 실제 회복률도 달라질 수 있다. 기존 스택 임계값과 다음 스택 안내는 유지한다. 흑마법사도 현재 피해에 따른 단계 반영 회복률을 같은 형식으로 표시하며, 원래 흡수 횟수의 다음 단계·MAX 안내는 유지한다. 피해 400·최대 단계에서 원거리는 7%, 근거리는 12%로 표시한다.

엔드 스플래시 회복에는 66% 감쇠 전 해당 공격의 방어 적용 전 피해를 효율 기준으로 공유하고, 각 스플래시 대상의 실제 피해에 이 회복률을 곱한다. 별도 66% 회복 계수를 다시 곱하지 않는다. 피해 250·6단계에서는 주 대상 18, 스플래시 피해 165인 적마다 11.88을 회복한다. 주 대상의 과잉 피해·부분 방어로 실제 피해가 줄어도 스플래시의 효율 기준은 줄지 않는다. 완전 피해 감소로 공유 피해 API가 공격을 취소하는 경우에는 기존과 같이 스플래시 피해와 회복도 발생하지 않는다. 주 대상은 자신의 실제 피해로 효율을 계산하므로 방어가 서로 다르면 주 대상 회복량의 정확히 66%가 아닐 수 있다.

생명력 흡수의 피해 효율·최종 회복률·회복량 수치 계산은 `DamageLifeSteal`에서 공유한다. 엔드는 기준 피해 30과 현재 단계 진행률, 흑마법사는 기준 피해 40과 기존 스택 흡수율의 10배를 전달한다. 스택 누적·공격 실행·주 대상과 범위 대상의 기준 피해 선택·실제 체력 회복은 각 타워가 맡는다. 표시와 실제 회복량은 같은 공통 계산을 사용한다.

원거리 흑마법사의 `ranged_warlock_tower.healthThreshold`는 2,000이다. 흡수로 얻은 원시 체력 증가량은 2,000까지 그대로 적용하며, 초과하면 `2000 + 500 × ln(1 + (원시 증가량 − 2000) / 500)`으로 계속 증가한다. 최종 최대 체력을 2,000으로 제한하는 설정이 아니다.


### 마왕의 개별 피해 포인트 점감

`abilities.demon_lord_global`의 `physicalDamageThreshold=100`, `physicalDamageScale=100`, `magicDamageThreshold=100`, `magicDamageScale=50`은 모두 피해 포인트 단위의 양수다. 기존 설정에 없는 키만 기본값으로 보완하며 운영자 지정값은 보존한다. 레벨 추가 배율용 `damageBonusThreshold/Scale=0.5/0.5`와 다른 설정이다.

대상 하나의 피해 발생마다 마왕의 배율·조건을 합성한 x에 `x <= T ? x : T + S * ln(1 + (x - T) / S)`를 한 번 적용한다. 그 뒤 받는 피해 보정·방어·남은 체력을 반영한다. 직격·파동·지속 피해·추가 공격·재시전은 같은 본체 경로를 사용하며 광역 총합이나 시전 전체 합계를 점감하지 않는다. 처형 TRUE와 별도 소환수는 제외된다. 재시전은 감쇠 전 피해를 보관하며 흡혈은 실제 준 피해와 기존 회당 상한을 유지한다.

예: x=200이면 물리 169.3147, 마법 154.9306, x=500이면 물리 260.9438, 마법 209.8612다. 100 이하 피해는 변하지 않는다. 이는 초반 보존·큰 마법 피해 억제를 위한 초기 튜닝안이며, 타격별 피해 분포와 스킬 기여가 없어 흑마와 같은 누적 성능을 보장하지 않는다. 과거 통계의 물리 열에는 TRUE 처형도 포함된다. 운영 설정 수정·배포는 별도 작업이다.
