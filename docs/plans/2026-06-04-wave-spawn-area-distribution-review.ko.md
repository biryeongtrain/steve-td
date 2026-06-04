# 웨이브 몬스터 생성 구역 분산 배치 구현 계획

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 현재 lane별 웨이브 몬스터가 단일 `laneLayout.spawn()` 좌표에서만 생성되는 문제를 개선해, 넓은 생성 구역 안에 deterministic하고 고르게 분포되도록 한다.

**Status:** 구현 완료. `lane_spawn` bounds 기반 `spawnArea` + wave-only center-out round-robin 분산 + JVM/GameTest 검증까지 반영했다.

**Architecture:** `LaneRegionLayout`에 기존 단일 spawn point와 별도의 spawn area를 표현하고, `PlayerLane.spawnMinecraftEntity(...)`가 spawn sequence/policy를 통해 다음 spawn 위치를 선택하게 한다. 기존 맵이 별도 spawn area를 제공하지 않으면 기존 단일 spawn 좌표로 fallback해서 호환성을 유지한다.

**Tech Stack:** Java 21, Fabric, Nucleoid map template `TemplateRegion`/`BlockBounds`, Minecraft `Vec3`/`BlockPos`, JUnit, Fabric GameTest.

---

## 현재 코드 사실 확인

검토 기준 브랜치/상태:

```text
branch: master
status: dirty — 이전 인컴 lane routing 작업 변경이 아직 uncommitted 상태
```

이번 문서는 코드 구현을 하지 않고, 현재 코드 구조를 기준으로 타당성 및 구현 계획만 작성한다.

확인한 주요 파일:

- `src/main/java/kim/biryeong/semiontd/game/PlayerLane.java`
- `src/main/java/kim/biryeong/semiontd/map/LaneRegionLayout.java`
- `src/main/java/kim/biryeong/semiontd/map/ArenaLayout.java`
- `src/main/java/kim/biryeong/semiontd/entity/monster/SemionMonsterEntity.java`
- `src/gametest/java/kim/biryeong/semiontd/gametest/SyntheticArenaFactory.java`
- `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java`
- `src/gametest/java/kim/biryeong/semiontd/gametest/SemionLifecycleGameTest.java`

현재 웨이브 생성 흐름:

```text
SemionGame.startWave / enqueue path
-> PlayerLane.enqueueWaveMonster(WaveMonsterEntry)
-> waveMonsterSpawnQueue에 Monster N개 추가
-> PlayerLane.tick(...)
-> spawnQueuedMonster(waveMonsterSpawnQueue, players)
-> spawnMinecraftEntity(monster)
-> entity.configureFrom(monster, laneLayout)
-> var spawn = laneLayout.spawn()
-> entity.setPos(spawn.x, spawn.y, spawn.z)
-> monster.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z)
```

핵심 코드 위치:

```java
private void spawnMinecraftEntity(Monster monster) {
    if (monster.hasMinecraftEntity()) {
        return;
    }

    SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, arenaWorld);
    entity.configureFrom(monster, laneLayout);

    var spawn = laneLayout.spawn();
    entity.setPos(spawn.x, spawn.y, spawn.z);

    if (arenaWorld.addFreshEntity(entity)) {
        monster.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z);
    }
}
```

`LaneRegionLayout`는 현재 아래 정보를 갖고 있다.

```java
public record LaneRegionLayout(
        int laneId,
        Vec3 spawn,
        List<Vec3> waypoints,
        Vec3 bossPosition,
        BlockBounds laneArea,
        List<GridPosition> finalDefenseTowerSlots
)
```

`ArenaLayout.fromTemplate(...)`는 현재 `markers.laneSpawn()` region의 중심점을 단일 spawn으로 읽는다.

```java
Map<Integer, Vec3> laneSpawns = readLanePoints(template, markers.laneSpawn());
...
Vec3 laneSpawn = requiredLanePoint(laneSpawns, laneId, markers.laneSpawn());
...
lanes.put(laneId, new LaneRegionLayout(laneId, laneSpawn, waypoints, bossSpawn, laneArea, slots));
```

`SemionMonsterEntity.pathPoints()`는 현재 `laneLayout.spawn()`을 path point로 쓰지 않고, `laneLayout.waypoints() + bossPosition`만 반환한다.

```java
List<Vec3> points = new ArrayList<>(laneLayout.waypoints().size() + 1);
points.addAll(laneLayout.waypoints());
points.add(laneLayout.bossPosition());
return points;
```

따라서 spawn 위치가 넓은 구역 안에서 조금 달라져도, 첫 이동 목표는 기존 첫 waypoint 쪽으로 유지된다.

## 구현 결과와 계획 매핑

이번 구현은 문서의 추천 MVP와 같은 방향으로 완료했다.

| 계획 항목 | 구현 상태 | 근거 |
|---|---|---|
| `LaneRegionLayout`에 `spawnArea` 추가 | 완료 | `LaneRegionLayout(int laneId, Vec3 spawn, BlockBounds spawnArea, ...)` canonical record field 추가 |
| 기존 single-point spawn 호환 | 완료 | 기존 6-arg constructor 유지, `spawnArea`는 `BlockPos.containing(spawn)` 단일 cell fallback |
| `ArenaLayout`이 `lane_spawn` bounds를 읽음 | 완료 | `readLaneBounds(template, markers.laneSpawn())`로 `laneSpawnAreas`를 읽고 layout에 전달 |
| deterministic 분산 정책 | 완료 | `WaveSpawnPositionPolicy`가 spawn area floor cells를 center-out distance sort 후 round-robin 순회 |
| wave monster에만 적용 | 완료 | `PlayerLane.tick(...)`에서 `waveMonsterSpawnQueue`는 distributed spawn, `summonedMonsterSpawnQueue`는 기존 `laneLayout.spawn()` 사용 |
| `Monster.markMinecraftEntitySpawned(...)`에 실제 좌표 기록 | 완료 | 분산 선택된 `spawn` 좌표로 `entity.setPos(...)` 후 동일 좌표를 `markMinecraftEntitySpawned(...)`에 기록 |
| Synthetic GameTest arena 넓은 spawn area | 완료 | `SyntheticArenaFactory`가 lane별 3-cell `spawnArea`를 전달 |
| JVM policy/model test | 완료 | `LaneRegionLayoutTest`, `WaveSpawnPositionPolicyTest` 추가 |
| Fabric GameTest runtime 검증 | 완료 | `waveMonstersSpawnAcrossLaneSpawnArea`, `incomeSummonsKeepSingleSpawnPointWhenWaveSpawnAreaIsDistributed` 추가 |

검증 명령:

```bash
./gradlew test --tests '*LaneRegionLayoutTest' --tests '*WaveSpawnPositionPolicyTest' compileGameTestJava --no-daemon
./gradlew gametest --rerun-tasks --no-daemon
```

두 명령 모두 `BUILD SUCCESSFUL`을 확인했다.

## 문제 정의

현재는 lane별 웨이브 몬스터가 매 tick 하나씩 생성되더라도 모든 entity가 같은 `Vec3 spawn`에서 시작한다.

넓은 spawn 구역이 있는 맵에서는 아래 문제가 생길 수 있다.

1. 시각적으로 몬스터가 한 점에서 겹쳐 나와 부자연스럽다.
2. 큰 모델/히트박스가 여러 마리 겹칠 때 충돌, pathfinding, 타겟팅이 한 위치에 집중된다.
3. 넓은 길목/입구를 만들어 둔 map design 의도가 반영되지 않는다.
4. 초반 타워 사거리/공격 목표가 한 점에 과도하게 집중되어 실제 방어 부담이 왜곡될 수 있다.

## 변경 타당성 검토

### 결론

타당하다. 단, 무작위 scatter가 아니라 **lane path의 시작 구간에 정렬된 spawn area 안에서 deterministic sequence로 분산**해야 한다.

### 왜 타당한가

- 현재 `LaneRegionLayout.laneArea`는 이미 넓은 lane 영역을 표현하고 있다.
- `ArenaLayout`도 `TemplateRegion`/`BlockBounds`를 읽을 수 있으므로 spawn area를 추가로 읽는 구조가 자연스럽다.
- `SemionMonsterEntity`의 path follow는 spawn 이후 첫 waypoint를 향해 이동하므로, spawn 위치가 같은 lane의 spawn area 안에서 조금 달라지는 것은 path 구조와 충돌하지 않는다.
- GameTest에서 synthetic arena를 넓은 spawn area로 만들면 런타임 검증 가능하다.

### 위험한 접근

아래 방식은 피한다.

1. **완전 random 좌표**
   - 테스트가 불안정해지고, 특정 seed/짧은 구간에서 다시 한쪽으로 몰릴 수 있다.
2. **laneArea 전체를 spawn 후보로 사용**
   - laneArea에는 tower placement/방어 구역/경로 중후반까지 포함될 수 있어 몬스터가 중간에서 튀어나올 수 있다.
3. **path progress를 임의로 앞당긴 상태로 spawn**
   - 웨이브 시간/거리/킬 타이밍/실력 지표가 달라질 수 있다.
4. **기존 `spawn` field 의미를 바로 제거**
   - 기존 맵, 테스트, `StartPlacement`, layout assertion이 깨질 수 있다.

## 추천 설계

### MVP 방향

기존 `laneSpawn` marker를 계속 필수로 유지하되, 그 region의 `BlockBounds`도 보존해서 spawn area로 사용한다.

즉, 맵 제작자는 기존과 같이 lane spawn region을 넓게 만들면 된다.

```text
기존:
lane_spawn region center -> LaneRegionLayout.spawn

변경:
lane_spawn region center -> LaneRegionLayout.spawn fallback/대표점
lane_spawn region bounds  -> LaneRegionLayout.spawnArea
```

별도 marker를 새로 만들 수도 있지만, MVP에서는 기존 `lane_spawn` region을 넓게 쓰는 쪽이 더 단순하다.

### 데이터 모델

`LaneRegionLayout`에 필드를 추가한다.

```java
public record LaneRegionLayout(
        int laneId,
        Vec3 spawn,
        BlockBounds spawnArea,
        List<Vec3> waypoints,
        Vec3 bossPosition,
        BlockBounds laneArea,
        List<GridPosition> finalDefenseTowerSlots
)
```

호환 생성자를 하나 둔다.

```java
public LaneRegionLayout(
        int laneId,
        Vec3 spawn,
        List<Vec3> waypoints,
        Vec3 bossPosition,
        BlockBounds laneArea,
        List<GridPosition> finalDefenseTowerSlots
) {
    this(laneId, spawn, oneBlockBoundsAround(spawn), waypoints, bossPosition, laneArea, finalDefenseTowerSlots);
}
```

단, `oneBlockBoundsAround(spawn)`은 실제 코드에서 `BlockPos.containing(spawn)` 기반으로 안전하게 만든다.

### spawn position policy

새 class 후보:

```text
src/main/java/kim/biryeong/semiontd/game/WaveSpawnPositionPolicy.java
```

책임:

- `LaneRegionLayout.spawnArea()`에서 후보 spawn positions 생성
- 후보가 없으면 `laneLayout.spawn()` fallback
- 매 spawn마다 deterministic sequence로 다음 후보 선택
- 후보는 가능한 바닥 block center 기준 `Vec3(x + 0.5, y, z + 0.5)` 형태

MVP 후보 선택 방식:

```text
1. spawnArea의 floor plane만 사용한다.
2. minY 또는 spawn.y에 가까운 Y plane을 기준으로 한다.
3. X/Z grid cell 중심을 후보로 만든다.
4. 후보를 lane의 첫 waypoint 방향 기준으로 좌우가 번갈리도록 정렬한다.
5. 선택은 round-robin cursor로 한다.
```

간단한 정렬 MVP:

```text
candidates.sort(
  comparing distance from spawn center,
  then x,
  then z
)
```

이 방식은 매우 단순하고 deterministic하다. 다만 “고르게 분포” 품질은 완전한 blue-noise보다 약하다.

조금 더 좋은 MVP 후보:

```text
center, left1, right1, left2, right2 ... 형태의 interleaved ordering
```

하지만 map lane 방향을 계산해야 하므로, 첫 구현은 `center-out round-robin`이 충분하다.

### PlayerLane 연결

`PlayerLane`에 spawn cursor를 둔다.

```java
private final WaveSpawnPositionPolicy waveSpawnPositionPolicy;
```

또는 단순하게:

```java
private int waveSpawnCursor;
```

그리고 `spawnMinecraftEntity`에서 아래처럼 바꾼다.

```java
Vec3 spawn = nextSpawnPosition(monster);
entity.setPos(spawn.x, spawn.y, spawn.z);
```

MVP에서는 wave monster와 income unit 모두 같은 spawn area를 써도 된다. 다만 사용자 요청은 “웨이브 생성 메커니즘”이므로 더 보수적으로는 wave queue에만 적용한다.

추천은 다음과 같다.

```text
MVP: waveMonsterSpawnQueue에만 area distribution 적용
income summon queue는 기존 laneLayout.spawn() 유지
```

이유:

- 인컴 유닛은 최근 target lane fair routing과 별도 문제다.
- 인컴 유닛까지 동시에 바꾸면 테스트/밸런스 영향 범위가 커진다.
- 사용자가 명시한 대상은 wave spawn이다.

따라서 `spawnQueuedMonster`가 queue type을 구분하게 한다.

```java
spawnQueuedMonster(waveMonsterSpawnQueue, players, SpawnSource.WAVE);
spawnQueuedMonster(summonedMonsterSpawnQueue, players, SpawnSource.INCOME);
```

또는 작은 변경으로 별도 method를 둔다.

```java
spawnQueuedWaveMonster(...)
spawnQueuedSummonedMonster(...)
```

## Config 필요 여부

이번 기능은 config-driven보다 map-driven이 더 적절하다.

이유:

- spawn 분산 여부는 맵의 spawn region 크기로 자연스럽게 표현된다.
- region이 1 block이면 기존과 사실상 동일하게 동작한다.
- global config로 켜고 끄는 것보다 map marker의 의미가 명확하다.

다만 운영 rollback을 위해 config를 둘 수도 있다.

추천 MVP:

```text
config 추가 없음
spawnArea가 1 cell이면 기존과 동일
```

추천 확장:

```json
{
  "waveSpawnDistribution": {
    "enabled": true,
    "mode": "CENTER_OUT_ROUND_ROBIN"
  }
}
```

하지만 이번 계획에서는 YAGNI 원칙상 config는 2차로 미룬다. 사용자가 enable/disable을 명시적으로 요구하지 않았고, region 크기 1칸이 자연스러운 disable 역할을 한다.

## 구현 계획

### Task 1: `LaneRegionLayout`에 spawn area 추가

**Objective:** 기존 단일 spawn point와 별도로 spawn candidate bounds를 보존한다.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/map/LaneRegionLayout.java`
- Test: `src/test/java/kim/biryeong/semiontd/map/LaneRegionLayoutTest.java` 또는 기존 map 테스트 파일

**Step 1: Write failing test**

테스트 내용:

- 기존 6-arg constructor가 계속 동작해야 한다.
- 새 `spawnArea()`가 없던 생성자는 `spawn` 주변 1 block bounds를 반환해야 한다.
- 새 7-arg constructor는 전달한 `BlockBounds`를 보존해야 한다.

**Step 2: Run targeted test**

```bash
./gradlew test --tests '*LaneRegionLayoutTest' --no-daemon
```

Expected: FAIL — `spawnArea()`가 아직 없음.

**Step 3: Implement minimal model change**

- record field에 `BlockBounds spawnArea` 추가
- canonical constructor에서 null이면 one-block fallback
- 기존 constructor overload 추가

**Step 4: Verify**

```bash
./gradlew test --tests '*LaneRegionLayoutTest' --no-daemon
```

Expected: PASS.

### Task 2: `ArenaLayout`이 lane_spawn bounds를 spawn area로 읽게 변경

**Objective:** map template의 기존 `lane_spawn` region이 넓으면 그 bounds를 spawn area로 쓰게 한다.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/map/ArenaLayout.java`
- Test: 기존/신규 `ArenaLayout` loader 테스트가 있으면 추가. 없으면 pure test를 위해 helper를 만든다.
- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SyntheticArenaFactory.java`

**Step 1: Write failing test**

검증:

- `lane_spawn` region의 center는 기존 `spawn`으로 유지된다.
- 같은 region의 `BlockBounds`는 `spawnArea`로 저장된다.

**Step 2: Implement**

현재:

```java
Map<Integer, Vec3> laneSpawns = readLanePoints(template, markers.laneSpawn());
```

추가:

```java
Map<Integer, BlockBounds> laneSpawnAreas = readLaneBounds(template, markers.laneSpawn());
```

생성:

```java
BlockBounds laneSpawnArea = requiredLaneBounds(laneSpawnAreas, laneId, markers.laneSpawn());
lanes.put(laneId, new LaneRegionLayout(laneId, laneSpawn, laneSpawnArea, waypoints, bossSpawn, laneArea, slots));
```

**Step 3: Verify**

```bash
./gradlew test --tests '*ArenaLayout*' --no-daemon
./gradlew compileGameTestJava --no-daemon
```

Expected: PASS.

### Task 3: Spawn candidate calculator 추가

**Objective:** spawnArea bounds에서 deterministic candidate positions를 만든다.

**Files:**

- Create: `src/main/java/kim/biryeong/semiontd/game/WaveSpawnPositionPolicy.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/WaveSpawnPositionPolicyTest.java`

**Step 1: Write failing tests**

필수 테스트:

1. 1x1 spawn area는 항상 `laneLayout.spawn()`과 사실상 같은 좌표를 반환한다.
2. 3x3 spawn area에서 연속 9회 선택하면 중복 없이 9개 cell 중심을 순회한다.
3. 10번째 선택은 1번째 후보로 돌아온다.
4. 후보는 `spawnArea` bounds 안에 있어야 한다.
5. 후보 순서는 deterministic해야 한다.

**Step 2: Implement minimal policy**

권장 API:

```java
final class WaveSpawnPositionPolicy {
    private final List<Vec3> candidates;
    private int cursor;

    WaveSpawnPositionPolicy(LaneRegionLayout layout) { ... }

    Vec3 next() { ... }
}
```

후보 생성:

```java
for (BlockPos pos : spawnArea) {
    if (pos.getY() != floorY) continue;
    candidates.add(new Vec3(pos.getX() + 0.5, layout.spawn().y, pos.getZ() + 0.5));
}
```

정렬:

```java
candidates.sort(Comparator
    .comparingDouble((Vec3 p) -> p.distanceToSqr(layout.spawn()))
    .thenComparingDouble(p -> p.x)
    .thenComparingDouble(p -> p.z));
```

**Step 3: Verify**

```bash
./gradlew test --tests '*WaveSpawnPositionPolicyTest' --no-daemon
```

Expected: PASS.

### Task 4: `PlayerLane` wave spawn만 분산 적용

**Objective:** wave queue에서 spawn되는 몬스터만 area distribution을 쓰고, income summon은 기존 위치를 유지한다.

**Files:**

- Modify: `src/main/java/kim/biryeong/semiontd/game/PlayerLane.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/PlayerLaneSpawnTest.java` 또는 기존 game test 파일

**Step 1: Write failing test**

pure JVM에서 `PlayerLane.spawnMinecraftEntity`는 Minecraft `ServerLevel`이 필요하므로 직접 테스트하기 어렵다. 대신 정책 단위 테스트를 유지하고, runtime은 GameTest에서 검증한다.

이 task에서는 컴파일 가능한 최소 변경만 한다.

**Step 2: Implement**

현재:

```java
spawnQueuedMonster(waveMonsterSpawnQueue, players);
spawnQueuedMonster(summonedMonsterSpawnQueue, players);
```

변경 후보:

```java
spawnQueuedMonster(waveMonsterSpawnQueue, players, true);
spawnQueuedMonster(summonedMonsterSpawnQueue, players, false);
```

그리고:

```java
private void spawnMinecraftEntity(Monster monster, boolean distributeWaveSpawn) {
    ...
    Vec3 spawn = distributeWaveSpawn ? waveSpawnPositionPolicy.next() : laneLayout.spawn();
    entity.setPos(spawn.x, spawn.y, spawn.z);
    ...
}
```

**Step 3: Verify compile**

```bash
./gradlew compileJava --no-daemon
```

Expected: PASS.

### Task 5: Fabric GameTest로 런타임 spawn 분포 검증

**Objective:** 실제 entity spawn 위치가 넓은 spawn area 안에서 여러 좌표로 분산되는지 검증한다.

**Files:**

- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SyntheticArenaFactory.java`
- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java` 또는 별도 `SemionWaveSpawnGameTest.java`

**Step 1: SyntheticArenaFactory에 넓은 spawn area 제공**

현재 synthetic lane은 spawn point만 있고 `LaneRegionLayout` 생성자는 laneArea만 받는다.

변경 후:

```java
BlockBounds spawnArea = BlockBounds.of(
    new BlockPos(baseX + laneOffset, baseY + 1, baseZ + 3),
    new BlockPos(baseX + laneOffset + 2, baseY + 1, baseZ + 3)
);
lanes.put(laneId, new LaneRegionLayout(laneId, spawn, spawnArea, waypoints, bossSpawn, laneArea, finalDefenseSlots));
```

**Step 2: GameTest 작성**

테스트 이름 후보:

```java
waveMonstersSpawnAcrossLaneSpawnArea
```

검증 흐름:

```text
1. startedTwoPlayerGame 생성
2. RED lane 1에 wave monster count 3 enqueue
3. lane.tick(server)를 3회 실행해서 3마리 spawn
4. active monster entity positions를 읽는다
5. x/z 좌표 set size가 2 이상인지 확인
6. 모든 position이 lane.laneLayout().spawnArea() 안인지 확인
7. income summon은 기존 spawn 위치를 유지하는지 별도 assertion은 선택 사항
```

**Step 3: Compile GameTest**

```bash
./gradlew compileGameTestJava --no-daemon
```

Expected: PASS.

**Step 4: Run GameTest**

```bash
./gradlew gametest --no-daemon
```

Expected: BUILD SUCCESSFUL.

### Task 6: 회귀 테스트 및 문서 보강

**Objective:** 기존 movement/kill/reward behavior가 spawn 분산 때문에 깨지지 않는지 확인한다.

**Files:**

- Modify: this plan if implementation differs
- Optional: user-facing map authoring docs if present

**Verification:**

```bash
./gradlew test compileGameTestJava gametest --no-daemon
```

또는 최종 강제 실행:

```bash
./gradlew test gametest --rerun-tasks --no-daemon
```

Expected: BUILD SUCCESSFUL.

## Acceptance Criteria

구현 완료 조건:

1. 기존 single-point spawn map은 기존과 동일하게 동작한다.
2. 넓은 `lane_spawn` region이 있으면 wave monsters가 여러 cell center에 deterministic하게 분포된다.
3. 같은 lane에서 연속 spawn되는 wave monsters가 전부 한 좌표에 겹치지 않는다.
4. spawn 후보는 lane spawn area bounds 안에 있다.
5. income summon spawn 위치는 MVP에서 바뀌지 않는다.
6. movement path는 기존 waypoints/bossPosition을 그대로 사용한다.
7. `Monster.markMinecraftEntitySpawned(...)`에는 실제 분산 spawn 좌표가 기록된다.
8. JVM policy test와 Fabric GameTest가 모두 있다.
9. `./gradlew test gametest --rerun-tasks --no-daemon`가 성공한다.

## 타당성 자체 검토

### 설계가 요구를 만족하는가?

만족한다. 사용자의 요구는 “한 blockpos에서만 생성되는 것을 넓은 생성 구역에 고르게 분포”시키는 것이다. 이 계획은 spawn 구역을 `BlockBounds`로 보존하고, 생성마다 round-robin 후보를 사용하므로 한 좌표 집중 문제를 직접 해결한다.

### 너무 큰 변경인가?

MVP 기준으로는 크지 않다.

- map model에 `spawnArea` 필드 1개 추가
- loader에서 기존 `lane_spawn` bounds 재사용
- spawn position policy 1개 추가
- `PlayerLane` spawn 위치 선택부만 변경

기존 movement/path/rating/economy 로직은 건드리지 않는다.

### 기존 맵 호환성은 괜찮은가?

괜찮다. 기존 `lane_spawn` region이 1 block 또는 좁은 region이면 사실상 기존 single point와 동일하다. 또한 기존 생성자를 유지하면 테스트/기존 synthetic layout도 단계적으로 전환할 수 있다.

### gameplay fairness 관점에서 안전한가?

대체로 안전하다. spawn progress를 앞당기지 않고, 첫 waypoint와 boss path는 유지하므로 웨이브 도착 시간/거리의 큰 변화는 피한다.

다만 spawn area를 너무 넓게 만들면 일부 몬스터가 타워 사거리 안/밖에서 미세하게 다르게 시작할 수 있다. 그래서 map authoring rule이 필요하다.

권장 rule:

```text
lane_spawn region은 lane 시작선 근처의 횡방향 폭만 넓히고,
진행 방향으로 너무 깊게 만들지 않는다.
```

### deterministic이 random보다 나은가?

이 기능에서는 deterministic이 더 낫다.

- 테스트 가능하다.
- 짧은 wave에서도 고르게 퍼진다.
- random streak로 다시 한쪽에 몰릴 수 있는 문제를 피한다.
- replay/debug가 쉽다.

### `laneArea` 전체를 쓰지 않는 결정은 타당한가?

타당하다. `laneArea`는 tower placement와 방어 구역을 포함하는 넓은 영역이다. 이 전체를 spawn 후보로 쓰면 몬스터가 경로 중간이나 방어 구역 안에서 생성될 수 있다. spawn 전용 bounds를 따로 두는 편이 안전하다.

### income summon도 같이 바꿔야 하는가?

MVP에서는 아니다. 사용자 요청은 웨이브 생성 메커니즘이고, income summon은 최근 별도의 lane fairness 라우팅 정책과 연결되어 있다. 동시에 바꾸면 원인 분석이 어려워진다. 필요하면 2차에서 config로 `applyToIncomeSummons`를 별도 옵션으로 추가한다.

## 리스크와 완화

| 리스크 | 영향 | 완화 |
|---|---:|---|
| spawn area가 길 방향으로 너무 깊음 | 일부 몬스터가 더 앞에서 시작 | map authoring rule 문서화, GameTest에서 bounds 검증 |
| 후보가 공중/벽 내부 | entity stuck 가능 | MVP는 기존 spawn region floor convention 따름, 2차에서 safe floor validation 추가 |
| 큰 entity끼리 여전히 충돌 | pathfinding 밀림 | 후보 spacing이 1 block이면 대형 몬스터에 부족할 수 있음, 2차에서 dimensions-aware spacing |
| 기존 tests가 단일 spawn 좌표를 가정 | 회귀 실패 | wave spawn 관련 assertion을 “bounds 안 + 분산”으로 업데이트 |
| random 도입 시 flake | 테스트 불안정 | random 대신 deterministic round-robin |

## 2차 개선 후보

MVP 이후 필요할 때만 고려한다.

1. `WaveSpawnDistributionConfig`
   - enabled
   - mode: `SINGLE_POINT`, `CENTER_OUT_ROUND_ROBIN`, `RANDOM_SEEDED`
   - applyToIncomeSummons
2. dimensions-aware spacing
   - ravager/warden 같은 큰 entity는 후보 간격 2 block 이상
3. safe floor validator
   - 발밑 block solid, 머리 공간 air 여부 확인
4. lane direction aware ordering
   - 진행 방향과 수직인 축으로 좌우 균등 분포
5. map authoring validation command
   - 모든 lane spawn area가 laneArea 안에 포함되는지 검사

## 추천 최종 방향

구현은 진행할 가치가 있다.

가장 안전한 MVP는 다음이다.

```text
lane_spawn region bounds를 spawnArea로 저장
wave monster만 spawnArea 후보에 center-out round-robin 분산
single-cell area는 기존 동작과 동일 fallback
JVM policy test + Fabric GameTest로 검증
```

이렇게 하면 맵의 넓은 생성 구역을 실제 gameplay에 반영하면서도, pathing/rating/economy 변화는 최소화할 수 있다.
