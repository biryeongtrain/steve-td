# 설정 파일

Semion TD는 서버 시작 또는 `/semiontd reload` 시 `config/semion-td/` 아래 설정 파일을 읽습니다. 파일이 없으면 `SemionConfigLoader`가 기본값으로 생성합니다. 이 문서는 운영자가 수정할 수 있는 필드와 주의사항만 다룹니다.

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

`wave.json`이 현재 파일명입니다. 예전 복수형 파일명은 `wave.json`이 없을 때만 읽는 레거시 경로입니다.

## 운영 데이터

다음 파일은 설정 파일처럼 직접 편집하지 않습니다.

| 파일 | 역할 |
|---|---|
| `profiles.json` | 플레이어 프로필 데이터입니다. |
| `build_guides.json` | 플레이어가 저장한 빌드 기록입니다. |
| `semiontd.db` | SQLite 영속 저장소입니다. `persistence.json`의 `sqlitePath` 기본값입니다. |
| `semiontd.db-shm`, `semiontd.db-wal` | SQLite 런타임 보조 파일입니다. |

운영 데이터는 서버를 끈 뒤 백업하고, 가능하면 명령어로 갱신합니다.

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
- `infinite`: 무한 웨이브 기준 몬스터입니다. 라운드가 오를수록 체력이 스케일됩니다.
- `lanes`: `default`, `lane_1` 같은 키로 라인별 몬스터 목록을 지정합니다.

몬스터 항목 필드:

- `id`: 웨이브 몬스터 ID입니다.
- `health`, `armor`, `attackDamage`: 전투 수치입니다.
- `attackKind`: `MELEE` 또는 `RANGED` 계열 공격 타입입니다.
- `entityType`: 바닐라 엔티티 ID입니다.
- `blockbenchModelId`: BIL 모델 ID입니다. 없으면 `null`을 둡니다.
- `dimensions`: 몬스터 충돌 크기입니다.
- `mineralReward`: 처치 시 다이아 보상입니다.
- `count`: 소환 수입니다.

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
