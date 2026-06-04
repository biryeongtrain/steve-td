# 동물 빌더 여우 타워 구현 계획

> **For Hermes:** 구현 시 `karpathy-coding-guidelines`, `test-driven-development`, `minecraft-config-driven-gameplay-features`, `minecraft-testing`를 따른다.

**Goal:** 동물 빌더에 “낮은 체력 정리/처형” 역할의 여우 타워 계열을 추가한다. 기존 돼지/늑구/토끼와 겹치지 않게, 여우는 **체력이 낮은 몬스터를 우선 포착하고 처형 피해를 넣는 마무리 담당**으로 설계한다.

**Status:** 구현 전 세부 기획 / TDD 기준 문서.

---

## 1. 현재 동물 빌더 구조

현재 동물 빌더는 `AnimalStackTower`를 통해 같은 계열 타워 수에 따라 보너스를 받는다.

- 돼지: 체력/공격력 스택, 탱킹, 고티어 splash
- 늑구: 공격력/공격 주기 스택, splash DPS
- 토끼: 공격력 스택, 고티어 추가타

따라서 여우가 들어갈 좋은 빈 역할은 다음이다.

```text
여우 = 낮은 체력 대상 우선 공격 + 처형 피해 + 같은 여우 스택으로 처형 범위/피해 강화
```

이렇게 하면 여우는 단순 DPS인 늑구/토끼와 다르고, 방어형 돼지와도 역할이 겹치지 않는다.

---

## 2. 핵심 판정

### 2.1 처형 대상

여우는 자신의 공격 사거리 안에 있는 몬스터 중 아래 조건을 만족하는 대상을 찾는다.

```text
monsterHealthRatio <= effectiveExecuteHealthThreshold
```

`effectiveExecuteHealthThreshold`는 기본 threshold에 여우 스택 보너스를 더한 값이다.

```text
effectiveThreshold = min(
  maxExecuteHealthThreshold,
  executeHealthThreshold + currentStacks * executeThresholdPerStack
)
```

### 2.2 우선순위

처형 후보가 있으면 기존 몬스터 진행도/role 우선순위보다 여우의 처형 후보를 먼저 선택한다.

정렬:

```text
1. health ratio 낮은 대상 우선
2. 같은 ratio면 가까운 대상 우선
```

처형 후보가 없으면 기존 `TowerAttackMonsterGoal`의 기본 target priority를 그대로 사용한다.

이 fallback이 중요하다. 여우가 처형 대상만 찾다가 아무 것도 안 때리는 타워가 되면 안 된다.

### 2.3 out-of-range 제한

MVP에서는 “사거리 안에 있는” 낮은 체력 대상만 우선한다.

이유:

- 멀리 있는 딸피를 쫓아가느라 현재 라인 방어를 버리는 문제 방지
- 기존 tower movement/targeting 정책과 충돌 최소화
- GameTest가 deterministic해짐

---

## 3. 타워 라인업

### T1 사냥 여우 타워

```text
id: t1_fox_tower
name: 사냥 여우 타워
role: 초반 정리 / 막타 보조
```

초안 stat:

```text
cost: 75
health: 35
range: 7
damage: 6
attackIntervalTicks: 16
aggroPriority: 10
```

능력:

```text
executeHealthThreshold: 0.30
executeThresholdPerStack: 0.02
maxExecuteHealthThreshold: 0.40
executeDamageBonusRatio: 0.25
executeDamageBonusPerStack: 0.05
maxStacks: 4
```

설명:

- 체력 30% 이하의 사거리 내 몬스터를 우선 공격
- 처형 대상에게 추가 피해
- 같은 여우 타워마다 처형 기준과 추가 피해 증가

### T2 붉은 여우 타워

```text
id: t2_fox_tower
name: 붉은 여우 타워
role: 중반 정리 / 처형 강화
```

초안 stat:

```text
cost: 170
health: 45
range: 7
damage: 10
attackIntervalTicks: 15
aggroPriority: 10
```

능력:

```text
executeHealthThreshold: 0.35
executeThresholdPerStack: 0.025
maxExecuteHealthThreshold: 0.50
executeDamageBonusRatio: 0.50
executeDamageBonusPerStack: 0.075
maxStacks: 4
```

설명:

- 체력 35% 이하 우선 공격
- 처형 추가 피해가 T1보다 높음
- 같은 여우 타워를 깔수록 더 이른 체력 구간부터 마무리 가능

### T3 설원 여우 타워

```text
id: t3_fox_tower
name: 설원 여우 타워
role: 후반 정리 / 고위험 누수 방지
```

초안 stat:

```text
cost: 320
health: 60
range: 8
damage: 15
attackIntervalTicks: 14
aggroPriority: 10
```

능력:

```text
executeHealthThreshold: 0.40
executeThresholdPerStack: 0.03
maxExecuteHealthThreshold: 0.60
executeDamageBonusRatio: 0.75
executeDamageBonusPerStack: 0.10
maxStacks: 4
```

설명:

- 체력 40% 이하 우선 공격
- 처형 대상에게 큰 추가 피해
- 최대 스택에서는 체력 50~60% 구간의 몬스터도 마무리 대상으로 인식 가능

---

## 4. 구현 범위

### 4.1 `AnimalTowers`

추가:

- `T1_FOX_TOWER`
- `T2_FOX_TOWER`
- `T3_FOX_TOWER`

시각 요소:

- `FoxVisual.builder().variant(Fox.Variant.DEFAULT)`
- T3는 `Fox.Variant.SNOW` 사용 가능

설명 template도 `TowerDescriptionRegistry`에 등록한다.

### 4.2 `AnimalTowerCatalogs`

추가:

```java
registerTower(AnimalTowers.T1_FOX_TOWER, FoxTower::new, 1);
registerTower(AnimalTowers.T2_FOX_TOWER, FoxTower::new, 2);
registerTower(AnimalTowers.T3_FOX_TOWER, FoxTower::new, 3);

link(AnimalTowers.T1_FOX_TOWER, "t2_fox_tower", "붉은 여우 타워", AnimalTowers.T2_FOX_TOWER);
link(AnimalTowers.T2_FOX_TOWER, "t3_fox_tower", "설원 여우 타워", AnimalTowers.T3_FOX_TOWER);
```

### 4.3 `TowerBalanceConfig`

default config에 tower stats, upgrade costs, abilities 추가.

### 4.4 `FoxTargetingPolicy`

순수 정책 클래스로 target 선택 점수를 분리한다.

검증할 동작:

- threshold 이하 후보 중 가장 낮은 health ratio 선택
- 같은 health ratio면 가까운 후보 선택
- threshold 초과 후보는 선택하지 않음
- 사거리 밖 후보는 선택하지 않음
- stack threshold는 cap을 넘지 않음

### 4.5 targeting hook

기존 `TowerAttackMonsterGoal`은 기본 target priority만 사용한다. 여우가 낮은 체력 후보를 먼저 잡으려면 tower-level hook이 필요하다.

최소 변경:

```java
Tower#selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates)
SemionTowerEntity#selectAttackTarget(List<SemionMonsterEntity> candidates)
TowerAttackMonsterGoal#findTarget()에서 기본 priority 전에 hook 확인
```

기본 구현은 `Optional.empty()`라 기존 타워 동작은 변하지 않는다.

### 4.6 `FoxTower`

`AnimalStackTower` 상속.

주요 동작:

- `isStackFamily`: T1/T2/T3 여우 계열
- `maxStacks`: config ability
- `selectAttackTarget`: `FoxTargetingPolicy`로 낮은 체력 in-range 후보 선택
- `modifyAttackDamage`: 처형 threshold 이하 대상에게 추가 피해

---

## 5. 테스트 계획

### JVM 테스트

`FoxTargetingPolicyTest`

- 낮은 체력 후보를 선택한다
- healthy 후보는 선택하지 않는다
- 사거리 밖 low-health 후보는 무시한다
- 같은 low-health면 더 낮은 ratio 우선
- 같은 ratio면 가까운 대상 우선
- stack threshold cap 검증

`FoxTowerCatalogTest` 또는 기존 config test 보강

- default config에 T1/T2/T3 fox tower stats 존재
- upgrade cost 존재
- ability defaults 존재

### GameTest

`foxTowerPrioritizesLowHealthTargetInRuntimeCombat`

시나리오:

```text
1. 동물 타워 catalog 등록
2. RED lane에 여우 타워 배치
3. 같은 lane에 몬스터 2마리 spawn
   - A: 가까운 healthy target
   - B: 사거리 안의 low-health target
4. tower AI tick 대기
5. 여우 tower currentAttackTarget == B 확인
6. B health가 A보다 먼저 감소하는지 확인
```

`foxTowerFallsBackToDefaultTargetingWhenNoExecuteCandidate`

선택적으로 추가. MVP에서는 JVM 정책 테스트와 기존 tower target GameTest가 fallback 안정성을 뒷받침한다.

---

## 6. 리스크와 제한

### 서버 성능

여우 target hook은 현재 tower가 이미 수집한 candidates list 안에서만 작동한다. 별도 world scan을 추가하지 않는다.

### 밸런스

여우는 “처형 대상”이 없으면 일반 타워와 비슷하게 행동한다. 그래서 지나치게 낮은 threshold면 체감이 약하고, 지나치게 높은 threshold면 사실상 모든 대상을 우선하게 된다.

초기 추천:

```text
T1: 30~40%
T2: 35~50%
T3: 40~60%
```

### 기존 타워 영향

기본 hook은 empty fallback이므로 여우 외 기존 타워 targeting은 유지되어야 한다.

---

## 7. Acceptance Criteria

- 여우 타워 3단계가 동물 빌더 catalog에 등록된다.
- T1 -> T2 -> T3 upgrade link가 존재한다.
- tower balance default config에 stats/abilities/upgrade cost가 포함된다.
- 여우는 사거리 안의 low-health 몬스터를 기존 기본 priority보다 먼저 공격한다.
- low-health 후보가 없으면 기존 targeting fallback을 사용한다.
- 같은 여우 계열 스택으로 threshold/피해가 강화된다.
- JVM test와 Fabric GameTest가 통과한다.

---

## 8. 구현 결과와 계획 매핑

| 계획 항목 | 구현 결과 |
|---|---|
| `AnimalTowers`에 T1/T2/T3 여우 타워 추가 | `T1_FOX_TOWER`, `T2_FOX_TOWER`, `T3_FOX_TOWER` 추가 |
| 여우 visual | T1/T2 `Fox.Variant.DEFAULT`, T3 `Fox.Variant.SNOW` |
| `AnimalTowerCatalogs` 등록 | `FoxTower::new`로 1/2/3티어 등록, T1→T2→T3 upgrade link 추가 |
| balance config | `TowerBalanceConfig.defaultConfig()`에 stats/upgrade cost/ability defaults 추가 |
| 순수 targeting 정책 | `FoxTargetingPolicy` 추가 |
| tower-level targeting hook | `Tower#selectAttackTarget`, `SemionTowerEntity#selectAttackTarget`, `TowerAttackMonsterGoal` hook 추가. 기본 구현은 empty라 기존 타워 동작 유지 |
| 여우 runtime 구현 | `FoxTower extends AnimalStackTower` 추가. 낮은 체력 in-range target 우선 선택, 처형 대상 추가 피해 적용, 같은 여우 계열 스택 반영 |
| JVM 테스트 | `FoxTargetingPolicyTest`, `FoxTowerCatalogTest` 추가 |
| GameTest | `foxTowerPrioritizesLowHealthTargetInRuntimeCombat` 추가 |
| 검증 | `./gradlew test gametest --rerun-tasks --no-daemon` 통과 필요 |

### 계획 대비 조정

- “처형 성공 시 다음 공격 속도 증가”는 MVP에서 제외했다. 현재 코드의 attack interval/cooldown 구조에 상태를 추가해야 하므로, 첫 구현은 낮은 체력 target priority + 추가 피해에 집중했다.
- out-of-range 낮은 체력 target 추격은 제외했다. 사거리 안 후보만 우선 선택해 라인 방어 이탈을 방지한다.
- 기존 `animaladv.AnimalADVTowers`에 있던 실험적 fox tower 정의는 production catalog에 연결되어 있지 않아 이번 구현에서는 건드리지 않았다. 실제 플레이 경로는 `tower.animal.AnimalTowers` + `AnimalTowerCatalogs`다.
