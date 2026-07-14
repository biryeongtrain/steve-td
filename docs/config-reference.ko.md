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

`wave.json`이 현재 파일명입니다. 예전 복수형 파일명은 `wave.json`이 없을 때만 읽는 레거시 경로입니다.

## 운영 데이터

다음 파일은 설정 파일처럼 직접 편집하지 않습니다.

| 파일 | 역할 |
|---|---|
| `profiles.json` | 플레이어 프로필 데이터입니다. |
| `build_guides.json` | 플레이어가 저장한 빌드 기록입니다. |
| `semiontd.db` | SQLite 영속 저장소입니다. `persistence.json`의 `sqlitePath` 기본값입니다. |
| `semiontd.db-shm`, `semiontd.db-wal` | SQLite 런타임 보조 파일입니다. |
| `job-statistics.db` | 일반 경기의 직업별 파생 통계입니다. 웹이나 외부 도구에서는 읽기 전용으로 조회합니다. |
| `job-statistics.db-shm`, `job-statistics.db-wal` | 직업 통계 SQLite의 런타임 보조 파일입니다. |

운영 데이터는 서버를 끈 뒤 백업하고, 가능하면 명령어로 갱신합니다.

`profiles.json`에는 플레이어가 선택한 스카이박스 ID와 팁 수신 여부도 저장됩니다.

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
- `timeOfDay`: 아레나 시간입니다.
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
