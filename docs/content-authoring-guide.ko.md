# Semion TD 콘텐츠 작성 가이드

이 문서는 직업, 타워, 웨이브, 소환 유닛을 직접 추가할 때 보는 작업 기준이다.

## 직업 추가

직업은 `kim.biryeong.semionTd.job.SemionJob`을 상속해서 Java 코드로 만든다.

기본 등록 위치는 새 직업 클래스를 만든 뒤 `JobRegistry.register(...)`를 호출하는 방식이다. 예시는 다음 형태다.

```java
public final class RefinerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "refiner");

    public RefinerJob() {
        super(ID, Component.literal("Refiner"), List.of(Component.literal("Better gas scaling.")));
    }

    @Override
    public long modifyStartingGasPerSec(JobContext context, long baseGasPerSec) {
        return baseGasPerSec + 1;
    }
}
```

현재 연결된 직업 훅:

- `onSelected`
- `onMatchStarted`
- `onRoundStarted`
- `onRoundEnded`
- `onEliminated`
- `modifyStartingMineral`
- `modifyStartingGas`
- `modifyStartingIncome`
- `modifyStartingGasPerSec`
- `canUseTower`
- `modifyTowerMineralCost`
- `onTowerPlaced`
- `canUseSummon`
- `modifySummonGasCost`
- `modifySummonIncomeGain`
- `onSummonedMonster`
- `modifyKillMineralReward`
- `onMonsterKilled`

플레이어 명령:

```text
/semiontd job list
/semiontd job current
/semiontd job select <id>
```

`<id>`는 `recruit`처럼 쓰면 `semion-td:recruit`로 해석된다. 다른 namespace를 쓰려면 `namespace:path` 형식으로 쓴다.

주의사항:

- 직업 선택은 경기 시작 전까지만 가능하다.
- 직업을 선택하지 않은 플레이어는 `semion-td:recruit` 기본 직업을 받는다.
- 직업 효과는 현재 경기 단위 상태다. 영속 프로필에는 아직 저장하지 않는다.

## 타워 추가

타워는 Java 코드로 하드코딩한다.

기본 흐름:

1. `TowerType`을 정의한다.
2. `Tower` 또는 `DirectTower`, `ProducerTower`, `SummonerTower`, `SupportTower`를 상속한 클래스를 만든다.
3. 배치 서비스 또는 카탈로그에서 해당 타워 타입을 찾아 생성하게 한다.
4. 직업 제한이 필요하면 직업의 `canUseTower(...)`에서 `TowerType.id()`를 기준으로 제한한다.
5. 직업별 비용 보정은 `modifyTowerMineralCost(...)`에서 처리한다.

현재 테스트 타워는 `kim.biryeong.semionTd.test` 패키지에만 둔다. 실제 게임 타워는 `kim.biryeong.semionTd.tower` 또는 하위 패키지에 추가하는 편이 좋다.

## 소환 유닛 설정

소환 유닛은 `config/semion-td/summons.json`에서 설정한다.

필드:

```json
{
  "id": "grunt",
  "displayName": "Grunt",
  "gasCost": 20,
  "incomeGain": 2,
  "maxHealth": 45,
  "armor": 0,
  "attackDamage": 4,
  "attackKind": "MELEE",
  "entityTypeId": "minecraft:zombie",
  "mineralReward": 3
}
```

직업별 소환 제한과 보정:

- `canUseSummon(...)`
- `modifySummonGasCost(...)`
- `modifySummonIncomeGain(...)`
- `onSummonedMonster(...)`

## 웨이브 설정

웨이브는 `config/semion-td/waves.json`에서 설정한다.

각 몬스터 필드:

```json
{
  "id": "basic_melee_1",
  "health": 35,
  "armor": 0,
  "attackDamage": 4,
  "attackKind": "MELEE",
  "entityType": "minecraft:zombie",
  "bliModelId": null,
  "count": 12,
  "mineralReward": 2
}
```

라인 키:

- `default`: 모든 lane에 적용
- `lane_1`부터 `lane_5`: 특정 lane 전용 설정

현재 구현은 라운드 설정에서 lane 키에 해당하는 엔트리를 읽어 lane에 큐잉한다. 특정 lane 정책을 바꾸려면 `RoundWaveConfig.entriesForLane(...)`를 확인한다.

## 경제 설정

경제는 `config/semion-td/economy.json`에서 설정한다.

기본값:

```json
{
  "startingMineral": 200,
  "startingGas": 50,
  "startingIncome": 0,
  "gasCap": {
    "base": 500,
    "roundOffsetMultiplier": 2,
    "roundOffsetStep": 20,
    "flatBonus": 10
  },
  "gasProduction": {
    "initialGasPerSec": 1,
    "maxUpgradeCount": 20,
    "initialUpgradeCost": 50,
    "upgradeCostIncrease": 25,
    "gasPerSecIncrease": 1,
    "upgradeCurrency": "MINERAL"
  }
}
```

직업의 시작 자원 보정은 이 config 값을 기준으로 한 번만 적용된다.

## 추천 작업 순서

1. 직업 1개를 만든다.
2. `JobRegistry.register(new YourJob())`로 등록한다.
3. `/semiontd job list`에서 보이는지 확인한다.
4. `/semiontd job select your_job_id`로 선택한다.
5. `/semiontd start` 후 HUD와 `/semiontd economy`로 효과를 확인한다.
6. 그 다음 타워나 소환 유닛 제한/보정을 붙인다.
