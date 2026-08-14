# 아틀란티스 빌더

심해 수압을 자원으로 쓰는 4계열 빌더입니다. 이 문서는 현재 구현과 번들 기본값을 설명합니다.

- 직업 ID: `semion-td:atlantis`
- 표시명: 아틀란티스 빌더
- 타워 ID prefix: `atlantis_`
- 패밀리 전역 설정 ID: `atlantis_global`
- 패키지: `kim.biryeong.semiontd.tower.atlantis`

## 1. 컨셉

몬스터에게 **압력 스택**을 쌓고, 스택이 빠지는 순간 **수압 폭발**로 터뜨립니다. 거북이는 자기 주변이 아니라 **전방 경로 위에 고압 구역을 전개**하고, 그 구역이 스택 축적 속도를 결정합니다.

```
🐢 거북이가 전방 경로에 고압 구역 전개 ─┐
                                       ├─▶ 구역 안 몬스터: 스택 2배 축적, 스택당 둔화
🐬 돌고래가 공격마다 스택 부여 ─────────┘
                                        │ 구역 이탈 / 지속시간 만료
                                        ▼
                                   💥 수압 폭발
```

### 다른 빌더와 겹치지 않는 근거

라이브 config의 ability 키 1024개(고유 333개)를 전수조사한 결과입니다.

| 요소 | 기존 게임 내 사용 현황 |
|---|---|
| **타워 위치와 분리된 지속 필드** | **0건.** 반경 기반 지속 효과 키가 25개 있으나 전부 타워 중심 오라(`frostAuraRange`, `supplyRadius`, `leaderAuraRadius` 등) |
| 둔화를 피해로 환산 | **0건.** `slow` 계열 키 42건은 전부 순수 CC |
| 우파루파 / 거북이 엔티티 | **0건.** 154개 타워 중 사용 없음 |
| 전달체 블록 | **0건.** `block_display`는 sculk 계열·dragon_egg·light만 사용 |
| 돌고래 엔티티 | 1건 — `ocean_dolphin_t3`, **공격력 0의 지원 타워**. 본 빌더의 주력 딜러와 역할이 정반대 |

신규성의 핵심은 "지속되는 광역 효과"가 아니라 **효과의 중심이 타워에서 분리된다**는 점입니다. 기존 25개 반경 효과는 전부 타워를 중심으로 하므로, 타워를 옮기지 않고는 효과 위치를 바꿀 수 없습니다.

고대도시의 스컬크 영토는 "내 땅 위에 있으면 버프"라는 정적 소유 개념이고, 본 빌더의 고압 구역은 몬스터 상태를 바꾸는 동적 필드라 성격이 다릅니다.

고압 구역은 10틱마다 기존 광역 효과 경로를 재사용해 판정합니다. 별도 지속 효과 서비스는 추가하지 않습니다.

## 2. 계열 구성

4계열 × 3티어 = 12타워. 고대도시·네더·무블룸과 같은 규모입니다.

| 계열 | 엔티티 | 역할 | 담당 |
|---|---|---|---|
| 거북이 | `minecraft:turtle` | 탱커 | 전방 경로에 고압 구역 전개, 어그로로 몬스터 고정 |
| 돌고래 | `minecraft:dolphin` | 딜러 | 압력 스택 부여, 수압 폭발 |
| 우파루파 | `minecraft:axolotl` | 유틸 | 티어별 변종 고정, 티어마다 지원 능력 누적 |
| 전달체 | `block_display` + `minecraft:conduit` | 버프 | 스택 상한·수압 배율 증폭 |

생물 3 + 구조물 1 구성입니다. 고대도시가 블록 3(촉매·센서·쉬리커) + 생물 1(워든)로 같은 혼합 구성을 씁니다.

## 3. 타워 수치

비교 기준은 같은 4계열 규모 빌더의 라이브 실측값입니다.

### 거북이 — 탱커 (고대도시 촉매 계보)

| ID | 티어 | 원 | 최대체력 | 공격력 | 사거리 | 간격 | 어그로 | 구역 반경 |
|---|---|---|---|---|---|---|---|---|
| `atlantis_turtle_t1` | 1 | 55 | 190 | 5 | 2.6 | 20 | 55 | 3.0 |
| `atlantis_turtle_t2` | 2 | 115 | 320 | 9 | 2.8 | 18 | 85 | 3.5 |
| `atlantis_turtle_t3` | 3 | 240 | 620 | 14 | 3.0 | 16 | 115 | 4.0 |

대조군 `ancient_city_catalyst_t1/t2/t3`: 50/110/230원, 체력 110/220/450, 공격 3/5/8, 어그로 50/80/110.

체력/100원은 236 / 178 / 192로, 기존 탱커(어그로 40 이상) 표본의 3사분위(T1 300 / T2 176 / T3 196) 안에 들어옵니다. T3 기준으로 `ocean_elder_guardian_t3`(252), `illager_ravager_t3`(200), `ancient_city_catalyst_t3`(196)와 같은 급입니다.

### 돌고래 — 딜러 (네더 블레이즈 계보, 기본 공격력을 낮추고 배율로 보완)

| ID | 티어 | 원 | 최대체력 | 공격력 | 사거리 | 간격 | 어그로 |
|---|---|---|---|---|---|---|---|
| `atlantis_dolphin_t1` | 1 | 55 | 80 | 13 | 6.5 | 16 | 0 |
| `atlantis_dolphin_t2` | 2 | 120 | 130 | 24 | 7.5 | 14 | 0 |
| `atlantis_dolphin_t3` | 3 | 250 | 190 | 40 | 8.5 | 12 | 0 |

**기준선 산출.** 라이브 159개 공격 타워에서 순수 딜러(비용 150 이상, 어그로 20 이하)만 추려 기본 DPS로 정렬한 값입니다.

| 타워 | 원 | 공격력 | 간격 | DPS | /100원 |
|---|---|---|---|---|---|
| `illager_evoker_single_t3` | 200 | 45 | 12 | 75.0 | 37.5 |
| `ocean_cod_t3` | 210 | 40 | 12 | 66.7 | 31.7 |
| **`atlantis_dolphin_t3`** | **250** | **40** | **12** | **66.7** | **26.7** |
| `t3_ranged_skeleton_tower` | 200 | 35 | 12 | 58.3 | 29.2 |
| `t3_resonance_wave_moobloom` | 300 | 35 | 12 | 58.3 | 19.4 |

T3의 기본 DPS는 66.7입니다. 수압 폭발을 포함한 1/3/5대상 실효 DPS는 지원 없음·구역 밖 106.7/186.7/266.7, 지원 없음·구역 안 146.7/306.7/466.7, T3 지원 완성·구역 안 163.6/345.5/527.3으로 제한합니다.

### 우파루파 — 유틸 (고대도시 센서 계보)

| ID | 티어 | 변종 | 원 | 최대체력 | 공격력 | 사거리 | 간격 | 어그로 |
|---|---|---|---|---|---|---|---|---|
| `atlantis_axolotl_t1` | 1 | 루시(핑크) | 45 | 70 | 6 | 7.0 | 20 | -10 |
| `atlantis_axolotl_t2` | 2 | 골드 | 95 | 110 | 10 | 8.0 | 18 | -10 |
| `atlantis_axolotl_t3` | 3 | 블루 | 200 | 150 | 16 | 9.0 | 16 | -10 |

대조군 `ancient_city_sensor_t1/t2/t3`: 45/90/190원, 체력 50/85/110, 공격 2/4/5, 어그로 -10.

변종은 티어에 고정되며 런타임에 바뀌지 않습니다. 따라서 `TowerType.visual()`에 정적으로 지정하고 `Tower.visual()` 오버라이드나 `onStateChanged()` 호출이 필요 없습니다.

### 전달체 — 버프 (순수 지원, 공격력 0)

| ID | 티어 | 원 | 최대체력 | 공격력 | 사거리 | 간격 | 어그로 |
|---|---|---|---|---|---|---|---|
| `atlantis_conduit_t1` | 1 | 50 | 90 | 0 | 5.0 | 20 | -5 |
| `atlantis_conduit_t2` | 2 | 105 | 130 | 0 | 5.5 | 18 | -5 |
| `atlantis_conduit_t3` | 3 | 215 | 200 | 0 | 6.0 | 16 | -5 |

공격력 0인 지원 타워는 게임 내 12건의 선례가 있습니다(`ocean_water_*`, `ocean_tropical_fish_*`, `t1_goat_tower` 등).

체력/100원은 124 / 76 / 65로, 기존 지원 타워 표본의 3사분위(137 / 77 / 68) 바로 아래입니다. 초기 제안(80/120/160)은 T2에서 114로 3사분위의 1.5배였고, 공격하지 않는 타워가 그만큼 단단할 근거가 없어 하향했습니다.

### 업그레이드 비용

고대도시의 패턴(업그레이드 비용 = 목표 티어의 배치 비용)을 따릅니다.

| 업그레이드 edge | 비용 |
|---|---|
| `atlantis_turtle_t1->atlantis_turtle_t2` | 115 |
| `atlantis_turtle_t2->atlantis_turtle_t3` | 240 |
| `atlantis_dolphin_t1->atlantis_dolphin_t2` | 120 |
| `atlantis_dolphin_t2->atlantis_dolphin_t3` | 250 |
| `atlantis_axolotl_t1->atlantis_axolotl_t2` | 95 |
| `atlantis_axolotl_t2->atlantis_axolotl_t3` | 200 |
| `atlantis_conduit_t1->atlantis_conduit_t2` | 105 |
| `atlantis_conduit_t2->atlantis_conduit_t3` | 215 |

T1→T2 구간 95~120은 기존 4계열 빌더의 60~160 범위 안, T2→T3 구간 200~250은 기존 180~300 범위 안입니다.

T1 배치 비용 합계는 205로, 시작 다이아 150(`economy.json`)으로 T1 두 기를 못 채웁니다. 고대도시 합계 260보다 낮아 초반 진입은 더 쉽습니다.

12타워 배치 비용 총합은 1545원으로 고대도시 1670, 네더 1775, 무블룸 1995보다 낮습니다.

## 4. 압력 시스템

### 기본값은 두 곳에 넣어야 합니다

`TowerBalanceConfig.defaultConfig()`는 마지막에 다음을 실행합니다.

```java
return BundledBalanceDefaults.load("tower_balance.json", TowerBalanceConfig.class, fallback);
```

`BundledBalanceDefaults.load`는 번들 리소스를 파싱해 **그대로 반환**하며 코드로 만든 `fallback`과 병합하지 않습니다. 따라서 실제 기본값의 출처는 다음 리소스입니다.

```
src/main/resources/semiontd/balance-defaults/tower_balance.json
```

Java의 `putAtlantisTowers` / `putAtlantisUpgrades` / `putAtlantisAbilities`는 그 리소스가 없을 때만 쓰이는 폴백입니다. **새 계열을 추가할 때 Java만 고치면 런타임에서 업그레이드 비용이 0이 되고 ability가 전부 폴백값으로 떨어집니다.** 컴파일로는 잡히지 않고, 카탈로그 테스트와 웹 익스포트 테스트에서만 드러납니다.

두 곳의 값이 어긋나지 않도록 변경 시 항상 함께 수정합니다.

### 전역 설정 `atlantis_global`

```json
{
  "maxPressureStacks": 10.0,
  "stackDurationTicks": 100.0,
  "slowPerStack": 0.05,
  "maxSlow": 0.45,
  "maxZoneAllyDamageReduction": 0.35,
  "waterPressureDamageRatio": 0.16,
  "waterPressureDamageCap": 2.5,
  "waterPressureRadius": 3.0,
  "zoneStackMultiplier": 2.0,
  "maxZoneCount": 6.0,
  "zoneSpacingBlocks": 4.0,
  "zoneScanIntervalTicks": 10.0,
  "zoneVfxIntervalTicks": 40.0,
  "maxChainDepth": 3.0
}
```

### 수압 피해 계산

```
수압 피해 = 부여 타워 공격력 × min(스택 수 × waterPressureDamageRatio, waterPressureDamageCap)
```

발동 조건은 셋 중 먼저 오는 것입니다.

1. 몬스터가 고압 구역을 벗어남
2. `stackDurationTicks` 경과로 스택 지속시간 만료
3. **몬스터 사망** — 스택을 보유한 채 죽으면 수압이 발동해 주변으로 이어집니다

3번이 처치 연쇄를 만듭니다. 수압 폭발이 주변 몬스터를 죽이면 그 몬스터의 스택으로 다시 수압이 발동합니다. 무한 연쇄를 막기 위해 `maxChainDepth`로 깊이를 제한하고, 한 번 수압한 몬스터는 같은 연쇄에서 재발동하지 않습니다.

연쇄 피해는 `TowerAreaDamage`를 통해 원래 부여 타워에 귀속되므로 처치 보상·통계·킬 훅이 정상 동작합니다.

| 상황 | 계산 | 실효 배율 |
|---|---|---|
| 돌고래 T3, 10스택 | 40 × (10 × 0.24) | ×2.4 (**+140%**) |
| T3 지원 완성, 상한 도달 | 40 × 2.5 | ×2.5 (**+150%**) |

상한 `waterPressureDamageCap: 2.5`는 모든 지원을 완성해도 폭발 한 번이 공격력의 2.5배를 넘지 않게 합니다.

### 스택 둔화

스택당 이동속도 -5%, 최대 -45%(`maxSlow`)입니다. 딜과 CC가 같은 자원을 공유하므로 "더 묶어둘 것인가, 지금 터뜨릴 것인가"가 매 순간의 판단이 됩니다.

### 고압 구역 생성 규칙

구역은 거북이 반경이 아니라 **거북이 전방 경로 위**에 전개됩니다.

**정원(capacity)** — 보유한 거북이 타워가 결정합니다.

```
기여도   거북이 T1 → 1     T2 → 2     T3 → 3
정원     Σ(기여도), 상한 6

  거북이 T1 하나       정원 1
  거북이 T3 하나       정원 3
  거북이 T2 + T3       정원 5
  거북이 T3 둘         정원 6  (상한)
```

**전개 시점** — 거북이를 설치하거나 업그레이드하는 즉시 정원만큼 전개합니다. 거북이가 파괴되거나 판매되면 그 기여분만큼 구역이 사라집니다.

**전개 위치 — 한 줄.** 거북이마다 따로 깔지 않고, **가장 뒤쪽(보스에 가까운) 거북이를 앵커로 잡아 접근로를 향해 정원만큼 일렬로** 깝니다.

거북이별로 독립 배치하면 거북이들이 가까이 붙어 있을 때 구역이 같은 자리에 겹쳐 생성되어, 거북이를 추가해도 커버리지가 늘지 않는 것처럼 보입니다. 한 줄 배치는 로스터가 눈에 보이게 만듭니다 — 거북이가 늘거나 티어가 오르면 벽이 길어집니다.

**방향.** 몬스터는 진행도 0(스폰)에서 1(보스)로 이동하므로, 아직 밟지 않은 땅은 **진행도가 낮은 쪽**입니다. 구역을 1 방향으로 깔면 웨이브 뒤편에 놓여 쓰이지 않습니다. 앵커에서 진행도가 낮아지는 방향으로 물립니다.

**간격 — 블록 단위.** `zoneSpacingBlocks`(4.0)를 경로 길이로 나눠 진행도 증분으로 환산합니다. 초기에는 경로 길이의 비율(`zoneSpacingRatio` 0.12)이었는데, 실제 아레나에서 구역이 수십 블록씩 벌어져 돌고래 사거리(8.5) 밖으로 흩어졌고 경로가 꺾이는 지점에서는 다른 레인 구간까지 넘어갔습니다. 블록 단위는 맵 크기와 무관하게 벽을 계열의 작동 범위 안에 유지합니다.

라인 크기는 코드 상수가 아니라 맵 데이터입니다(`LaneRegionLayout.laneArea`는 맵 템플릿의 `lane_path` 영역, 경로는 `waypoints`). 따라서 **간격을 블록 단위로 하드코딩하지 않고** `LaneRegionLayout.pathPoints()`가 반환하는 실제 경로(spawn → waypoints → boss) 위에서 **남은 경로 길이의 비율로 분배**합니다. 맵이 바뀌어도 배치가 깨지지 않습니다.

거북이 앞쪽 경로가 부족해 남은 구역을 전개할 수 없으면 **그 구역은 전개하지 않습니다**. 위치를 강제로 밀어넣거나 뒤쪽에 배치하지 않습니다.

**최종 방어선.** `PlayerLane.moveTowersToFinalDefense()`가 모든 타워를 보스 근처 슬롯으로 옮기면 거북이 위치는 몬스터가 어디 있을지를 더 이상 알려주지 못합니다. 몬스터는 `Monster.FINAL_DEFENSE_PROGRESS`(0.90)에 붙잡혀 전투하므로, 이때는 **정원과 같은 개수를 최종 방어선 전방(진행도가 낮은 쪽)으로 `zoneSpacingBlocks` 간격만큼 물려 배치**합니다. 선두 구역이 방어선 위에 놓이고 나머지가 접근로를 덮습니다.

`Tower.resetForRound`가 `deployedAtFinalDefense`를 해제하고 타워를 원위치로 돌리므로, 라운드 리셋 시 구역도 평시 전방 배치로 되돌아갑니다. 두 전환 모두 `AtlantisTower`에서 `moveToFinalDefense` / `resetForRound`를 오버라이드해 재전개를 요청합니다.

| 키 | 값 |
|---|---|
| `zoneCapacityPerTier` | T1 1 / T2 2 / T3 3 |
| `maxZoneCount` | 6 |
| `zoneRadius` | 3.0 ~ 4.0 (거북이 티어) |
| `zoneSpacingBlocks` | 4.0 (경로 위 블록 거리) |
| `zoneStackMultiplier` | 2.0 |
| `zoneScanIntervalTicks` | 10 |
| `zoneVfxIntervalTicks` | 40 |

### 구역 중첩과 지속 구현

**구역 안 아군 보호.** 구역은 몬스터만 다루지 않습니다. 같은 플레이어의 타워가 구역 안에 서 있으면 `zoneAllyDamageReduction`만큼 받는 피해가 줄어듭니다(`TOWER_DAMAGE_REDUCTION`). 구역이 경로 위에 깔리므로 전방에 세운 타워가 자연히 그 안에 들어가고, 거북이가 탱커 역할을 자기 몸이 아니라 **자기가 만든 땅**으로 수행하게 됩니다. 몬스터 쪽 둔화와 같은 스캔 주기(`zoneScanIntervalTicks`)로 갱신되며, 지속시간이 스캔 간격보다 길어 갱신 사이에 끊기지 않습니다.

**중첩 규칙 — 소스드, 상한 있음.** 구역 효과는 몬스터 둔화와 아군 피해감소 양쪽 모두 구역마다 고유 `sourceId`(`tower/<id>/pressure_zone/<index>`)를 붙여 적용합니다. `TimedEffectSet`의 소스드 `apply`는 source별로 값을 보관하고 `magnitude()`에서 **합산**하므로, 구역이 겹치는 자리에서는 효과가 실제로 더 강해집니다.

합산을 그대로 두면 깨집니다. 이동속도는 `1.0 - reduction`으로 소비되므로(`SemionMonsterEntity`), 구역 두 개만 겹쳐도 감소량이 1.0을 넘어 몬스터가 그 자리에 정지합니다. 그래서 각 구역이 내놓는 몫에 상한을 겁니다.

```java
int overlap = AtlantisStates.overlapCount(owner, position);
double share = Math.min(value, cap / overlap);   // 총합 = min(value x overlap, cap)
```

겹칠수록 총량이 커지되 `cap`에서 멈춥니다. 둔화는 `maxSlow`(0.45), 아군 피해감소는 `maxZoneAllyDamageReduction`(0.35)이 상한입니다.

| 겹침 | 구역당 둔화(3스택 기준 0.15) | 총 둔화 |
|---|---|---|
| 1개 | 0.15 | 0.15 |
| 2개 | 0.15 | 0.30 |
| 3개 | 0.15 | 0.45 |

소스드를 쓰는 두 번째 이유는 추적입니다. 언소스드는 최댓값 하나만 남기므로 강한 구역이 사라져도 값이 남고, 약한 구역은 자기 몫을 갱신하지 못합니다. 소스드는 구역이 사라질 때 정확히 자기 기여분만 회수됩니다.

**지속 구현 — 주기적 재발동.** `AreaEffectService`에 duration 개념을 추가하지 않습니다. 공용 API 변경은 13개 빌더 전체에 영향을 주는 반면, 이 게임은 이미 지속 효과를 주기적 재발동으로 처리합니다(`supplyIntervalTicks`, `effectRefreshTicks`, `supportIntervalTicks`, `healIntervalTicks`, `scanIntervalTicks`).

`zoneScanIntervalTicks`(10틱) 간격으로 각 구역이 `MonsterAreaEffectRequest`를 발동해 범위 안 몬스터에 효과를 갱신합니다. 매 틱이 아니므로 부하가 낮고, 효과 지속시간을 스캔 간격보다 길게 잡아(`stackDurationTicks` 100 > 10) 갱신 사이에 효과가 끊기지 않습니다.

### 계열별 ability

| 설정 ID | 키 | t1 | t2 | t3 |
|---|---|---|---|---|
| `atlantis_turtle_*` | `zoneCapacity` | 1 | 2 | 3 |
| | `zoneRadius` | 3.0 | 3.5 | 4.0 |
| | `zoneAllyDamageReduction` | 0.10 | 0.18 | 0.25 |
| `atlantis_dolphin_*` | `stackPerHit` | 1 | 2 | 3 |
| | `waterPressureRatioBonus` | 0.03 | 0.05 | 0.08 |
| `atlantis_axolotl_*` | `regenAmount` | 6 | 16 | 32 |
| | `attackSpeedBonus` | — | 0.08 | 0.15 |
| | `stackBonus` | — | — | 1 |
| | `waterPressureRatioBonus` | — | — | 0.04 |
| `atlantis_conduit_*` | `amplifyRadius` | 6.0 | 7.0 | 8.0 |
| | `maxStackBonus` | 2 | 3 | 4 |
| | `waterPressureRatioBonus` | 0.02 | 0.04 | 0.06 |

### 우파루파 변종

변종은 **타워 티어에 고정**됩니다. 런타임 전환이 없으므로 상태 동기화 비용과 깜빡임 문제가 발생하지 않습니다.

| 티어 | 변종 | 누적 능력 |
|---|---|---|
| T1 | 🩷 루시(핑크) | 아군 재생 |
| T2 | 💛 골드 | 아군 재생 + 아군 공격속도 증가 |
| T3 | 💙 블루 | 아군 재생 + 공격속도 증가 + **스택 부여량 증가 + 수압 배율 증가** |

능력은 티어가 오를 때 **교체되지 않고 누적**됩니다. 업그레이드 비용(95 / 200)을 지불하고 기존 능력을 잃지 않도록 하기 위함입니다.

T3의 스택 부여량 증가와 수압 배율 증가는 원래 별도 변종(시안)으로 분리했던 능력을 블루에 합산한 것입니다.

변종 지정은 **기존 `AxolotlVisual` 헬퍼를 그대로 씁니다.** 이미 구현되어 있어 추가 작업이 없습니다.

```java
AxolotlVisual.builder().variant(Axolotl.Variant.LUCY).build()   // T1 핑크
AxolotlVisual.builder().variant(Axolotl.Variant.GOLD).build()   // T2 골드
AxolotlVisual.builder().variant(Axolotl.Variant.BLUE).build()   // T3 블루
```

내부적으로 `EntityVisualProperties.AXOLOTL_VARIANT`(`"axolotl_variant"`)를 통해 적용됩니다. 거북이와 돌고래는 바닐라 변종이 없으므로 `EntityVisual.vanilla(...)`로 충분합니다.

## 5. andlist 조건 대조

| 조건 | 충족 여부 |
|---|---|
| 탱·딜·버프가 조화롭게 존재 | 거북이(탱)가 구역을 안 만들면 스택이 안 쌓이고, 전달체(버프)가 없으면 상한에 못 닿음. 역할 분담이 아니라 **기계적 전제조건** |
| 기본 공격 외 능력 + 최대 100% 이상 딜증 | 수압 폭발 상한 **+150%** |
| 기본 데미지 50 이하 | 최고값이 돌고래 T3의 **40** |

## 6. 구현 범위

`feat: 대적자 빌더 추가`(a967c4a, 35파일)의 구조를 따릅니다.

```
job/AtlantisTowerJob.java                        신규
job/JobRegistry.java                             registerBuiltIns()에 1줄
tower/atlantis/AtlantisTowers.java               타워 상수 + all() + isAtlantisTower()
tower/atlantis/AtlantisTowerCatalogs.java        등록 + 업그레이드 링크
tower/atlantis/AtlantisTower.java                4계열 공용 런타임 (역할별 분기)
tower/atlantis/AtlantisRole.java                 역할 구분
tower/atlantis/AtlantisBalance.java              atlantis_global 타입 접근
tower/atlantis/AtlantisPressure.java             몬스터별 압력 스택 + 수압 공식 + 연쇄 가드
tower/atlantis/PressureZone.java                 구역 위치·반경·소유 거북이
tower/atlantis/AtlantisStates.java               플레이어별 구역 목록 (키드 서비스)
tower/atlantis/AtlantisVfx.java                  구역/수압 전용 area VFX 스타일
SemionTd.java                                    AtlantisVfx.register() 1줄
tower/ProductionTowerCatalogs.java               패밀리 등록 2줄
config/TowerBalanceConfig.java                   기본값 + 머지 + 검증
```

계열별 타워 클래스를 나누지 않고 `AtlantisTower` 하나에서 `role()`로 분기합니다. 네 계열이 같은 압력 상태를 공유하고 보조 능력을 서로 조회하므로, 클래스를 나누면 그 조회가 클래스 간 캐스팅으로 흩어집니다.

### VFX

전용 area VFX 스타일 두 개를 `AreaVfxStyleRegistry`에 등록합니다. 공용 `DEBUFF`/`SPLASH`로도 동작은 하지만, 이 빌더는 **효과의 위치가 타워와 분리되어 있다는 점이 핵심 규칙**이라 구역이 어디 깔렸는지 보이지 않으면 플레이어가 배치를 판단할 수 없습니다.

| 스타일 | 렌더 정책 | 내용 |
|---|---|---|
| `atlantis_pressure_zone` | **`ON_TRIGGER`** | 바닥 테두리 링 + 상단 링 + 가장자리 물기둥 8개. 구역 안 몬스터는 바닥으로 눌리는 선 |
| `atlantis_water_pressure` | `ON_TRIGGER` | 코어 구체 + 확산 링 + 바깥으로 뻗는 궤적 10줄 + 피격 대상 연결선 |

구역 스타일이 `ON_TRIGGER`인 것이 핵심입니다. `ON_CHANGE`는 효과가 적용된 대상이 있을 때만 렌더링되므로(`AreaEffectService.shouldRender`), 빈 구역이 보이지 않습니다. 판정은 10틱마다 유지하되 경계 VFX는 `zoneVfxIntervalTicks`(40틱)마다 그려 과밀을 막습니다.

### 검증

| 대상 | 테스트 |
|---|---|
| 카탈로그 등록·업그레이드 그래프·비용 | `AtlantisTowerCatalogTest` (JUnit) |
| 수압 피해 공식·상한·소유자 격리 | `AtlantisPressureTest` (JUnit) |
| 빌더 소유권 단일성 | `WebCatalogExporterTest` 갱신 |
| 내장 빌더 수·starter 수 | `SemionParticipantGameTest` 갱신 |
| 구역 전개·스택 축적·운반 대상 포함 폭발·사망 연쇄 | `AtlantisIntegrationGameTest` (GameTest) |
| 거북이 설치/업그레이드/판매/파괴·라운드 복원 시 구역 정원 증감 | `AtlantisZoneCapacityTest` (JUnit) + GameTest |
| 티어별 변종이 카탈로그 재로드 후에도 유지 | `AtlantisTowerCatalogTest` |
| 구역 안 아군만 피해감소를 받음 | `AtlantisIntegrationGameTest` (GameTest) |
| VFX 스타일 등록·지오메트리·운영 렌더러 디버그 명령 | `AtlantisVfxTest` (JUnit) + GameTest |

```text
./gradlew test runGameTest remapJar --console=plain --no-daemon
git diff --check
```

## 7. 미해결 사항

- `maxChainDepth` 3이 실제 웨이브 밀집도에서 과한지 부족한지는 플레이 검증이 필요합니다.
- 저티어 돌고래에 고티어 지원을 붙이는 조합은 공격력 13과 40의 차이 및 티어별 배율 보너스로 억제했지만, 비용 대비 효율은 실플레이에서 계속 확인합니다.
- `zoneSpacingBlocks` 4.0은 구역 반경 3~4와 맞물려 벽이 살짝 겹치도록 잡은 값입니다. 겹침이 과하면 간격을, 벽이 성기면 반경을 조정합니다.

### 해결된 사항

- ~~우파루파 변종 지정 방법~~ → `AxolotlVisual` 헬퍼가 이미 존재하며 `Axolotl.Variant`를 그대로 받습니다.
- ~~구역 상한이 라인 길이에 맞는지~~ → 간격을 블록 단위로 고정하지 않고 `pathPoints()` 기반 비율 배치로 바꿔 맵 크기에 종속되지 않습니다.
- ~~전방 경로 부족 시 폴백~~ → 전개하지 않습니다.
- ~~최종 방어선 이동 시 구역 처리~~ → 정원과 같은 개수를 최종 방어선 전방으로 물려 재배치하고, 라운드 리셋 시 평시 배치로 복귀합니다.
- ~~전용 VFX~~ → `AtlantisVfx`가 구역·수압 스타일 두 개를 등록합니다. 구역은 `ON_TRIGGER`라 비어 있어도 경계가 보입니다.
- ~~`zoneAllyDamageReduction`이 툴팁에만 있고 적용되지 않던 문제~~ → 구역 스캔에서 구역 안 아군 타워에 `TOWER_DAMAGE_REDUCTION`을 적용합니다.
