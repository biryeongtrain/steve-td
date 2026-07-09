# 인컴 유닛 설계안

이 문서는 구현 전 검토용 초안이다. 목표는 세미온 타워디펜스 계열의 인컴 구조를 참고하되, Minecraft 바닐라 엔티티만으로 먼저 플레이 가능한 넓은 인컴 유닛 풀을 만드는 것이다.

원문 2 문서는 검색/미러에서 본문을 직접 확인하기 어려웠다. 그래서 확인 가능한 전작 인컴 문서의 포지션, TD2 라운드 문서에 보이는 유닛 성격, 현재 코드의 `SummonMonsterType` 구조를 기준으로 넓은 후보 풀을 잡았다. 실제 구현 전에는 이 목록에서 빼거나 이름/수치만 조정하면 된다.

## 설계 기준

- 인컴 유닛은 Emerald를 소모하고, 소환 성공 시 플레이어의 라운드 수입을 증가시킨다.
- 표시 이름은 창작 명칭보다 Minecraft 엔티티 이름을 우선한다.
- 약한 유닛과 서포트 유닛에는 동물/비적대 엔티티를 섞어 수를 늘린다.
- `entityTypeId`는 바닐라 엔티티를 사용한다.
- `blockbenchModelId`는 비워 두되, 추후에 `semion-td:summon/<id>` 형태로 연결할 수 있게 각 유닛 ID를 안정적으로 유지한다.
- 오라 중첩이 가능한 유닛은 5팀 구조에서 풀 중첩이 어렵기 때문에, 처음부터 최대 중첩값을 가진 단일 오라로 처리한다.
- 비오라 유닛은 기본적으로 수입 1당 Emerald 20에 가깝게 맞춘다.
- 오라/강한 교란/강한 전투력 유닛은 같은 수입이라도 비용 효율을 낮춘다.
- 공중처럼 보이는 엔티티도 1차 구현에서는 일반 라인 몬스터처럼 경로를 따라가게 둔다.

## 티어별 기준

| 티어 | 비용대 | 목적 |
|---|---:|---|
| T1 | 20-100 | 초반 인컴, 물량, 약한 견제 |
| T2 | 100-180 | 첫 오라/회복/고속 교란 |
| T3 | 180-320 | 중반 압박, 대형 유닛, 고수입 선택지 |
| T4 | 350-560 | 후반 원기옥, 강한 오라, 공성 |
| T5 | 600+ | 최종 원기옥, 보스급 탱커/공성 |

## 전체 인컴 유닛 후보

| 티어 | ID | 표시명 | EntityType | 비용 | 수입 | 현상금 | 역할 | 공격 | 피해 | 참고 포지션 | 특수/비고 |
|---|---|---|---|---:|---:|---:|---|---|---|---|---|
| T1 | `chicken` | Chicken | `minecraft:chicken` | 20 | 1 | 1 | SWARM | 근접 | 물리 | 최저가 인컴 | 가장 약한 물량 |
| T1 | `rabbit` | Rabbit | `minecraft:rabbit` | 40 | 2 | 2 | RUSH, SWARM | 근접 | 물리 | 빠른 초반 유닛 | 빠른 타겟 분산 |
| T1 | `silverfish` | Silverfish | `minecraft:silverfish` | 40 | 2 | 2 | SWARM | 근접 | 물리 | 저글링/물량 | 작은 충돌 크기 컨셉 |
| T1 | `zombie` | Zombie | `minecraft:zombie` | 40 | 2 | 2 | RUSH | 근접 | 물리 | 감염된 테란 | 기본 근접 |
| T1 | `husk` | Husk | `minecraft:husk` | 60 | 3 | 3 | TANK | 근접 | 물리 | 바퀴형 저티어 | 약한 탱커 |
| T1 | `skeleton` | Skeleton | `minecraft:skeleton` | 60 | 3 | 3 | RUSH | 원거리 | 물리 | 유령/해병형 | 초반 원거리 견제 |
| T1 | `wolf` | Wolf | `minecraft:wolf` | 60 | 2 | 3 | DISRUPTOR | 근접 | 물리 | 망치 경호대 | 짧은 공속 감소 |
| T1 | `spider` | Spider | `minecraft:spider` | 80 | 4 | 4 | RUSH | 근접 | 물리 | 초반 돌파 | 빠른 근접 압박 |
| T1 | `cave_spider` | Cave Spider | `minecraft:cave_spider` | 80 | 4 | 4 | DISRUPTOR | 근접 | 물리 | 독/교란 | 약한 지속 피해 후보 |
| T1 | `bee` | Bee | `minecraft:bee` | 100 | 5 | 4 | RUSH | 근접 | 물리 | 냉동 뮤탈형 | 공중 느낌의 고기동 |
| T2 | `turtle` | Turtle | `minecraft:turtle` | 100 | 4 | 5 | TANK, SUPPORT | 근접 | 물리 | 파수기 | 받는 타워 피해 감소 오라 |
| T2 | `sheep` | Sheep | `minecraft:sheep` | 100 | 5 | 4 | SWARM | 근접 | 물리 | 양 라운드/물량 | 약한 중가 물량 |
| T2 | `zombie_villager` | Zombie Villager | `minecraft:zombie_villager` | 120 | 6 | 6 | TANK | 근접 | 물리 | 계승자 | 효율형 근접 |
| T2 | `stray` | Stray | `minecraft:stray` | 120 | 5 | 6 | DISRUPTOR | 원거리 | 물리 | 냉동 원거리 | 약한 슬로우 후보 |
| T2 | `allay` | Allay | `minecraft:allay` | 140 | 6 | 6 | SUPPORT | 원거리 | 마법 | 암흑 불멸자 | 광역 회복 |
| T2 | `vex` | Vex | `minecraft:vex` | 140 | 7 | 6 | RUSH, DISRUPTOR | 근접 | 마법 | 망령 | 고속 진형 붕괴 |
| T2 | `fox` | Fox | `minecraft:fox` | 160 | 7 | 7 | SUPPORT | 근접 | 물리 | 그늘날개 | 공격력 오라 |
| T2 | `slime` | Slime | `minecraft:slime` | 160 | 8 | 7 | SWARM, TANK | 근접 | 물리 | 물량 탱커 | 분열은 1차 구현 제외 |
| T2 | `goat` | Goat | `minecraft:goat` | 180 | 9 | 8 | DISRUPTOR | 근접 | 물리 | 라이더/충돌 | 넉백 컨셉 디버프 |
| T2 | `bogged` | Bogged | `minecraft:bogged` | 180 | 8 | 8 | DISRUPTOR | 원거리 | 물리 | 독 원거리 | 독/감속 후보 |
| T3 | `pillager` | Pillager | `minecraft:pillager` | 180 | 9 | 8 | RUSH | 원거리 | 물리 | 금전사 대체 | 중반 원거리 |
| T3 | `piglin_brute` | Piglin Brute | `minecraft:piglin_brute` | 180 | 9 | 8 | RUSH | 근접 | 물리 | 금전사 | 강한 근접 |
| T3 | `ravager` | Ravager | `minecraft:ravager` | 200 | 10 | 10 | TANK | 근접 | 물리 | A.R.E.S. | 대형 딜탱 |
| T3 | `hoglin` | Hoglin | `minecraft:hoglin` | 200 | 10 | 9 | RUSH, TANK | 근접 | 물리 | 힝거루 | 빠른 대형 근접 |
| T3 | `horse` | Horse | `minecraft:horse` | 250 | 12 | 10 | RUSH, DISRUPTOR | 근접 | 물리 | 라이더 | 고속 슬로우/교란 |
| T3 | `llama` | Llama | `minecraft:llama` | 250 | 12 | 10 | SUPPORT, RUSH | 원거리 | 물리 | 동물 원거리 | 약한 원거리 지원 |
| T3 | `phantom` | Phantom | `minecraft:phantom` | 280 | 13 | 10 | RUSH | 근접 | 물리 | 공중 압박 | 공중 느낌, 일반 경로 |
| T3 | `enderman` | Enderman | `minecraft:enderman` | 300 | 30 | 12 | SIEGE | 근접 | 마법 | 암흑 추적자 | 고수입 선택지 |
| T3 | `breeze` | Breeze | `minecraft:breeze` | 300 | 14 | 12 | DISRUPTOR | 원거리 | 마법 | 과학자/교란 | 넉백/풍압 컨셉 |
| T3 | `guardian` | Guardian | `minecraft:guardian` | 320 | 15 | 12 | SIEGE | 원거리 | 마법 | 정찰기/광선 | 원거리 공성 |
| T4 | `polar_bear` | Polar Bear | `minecraft:polar_bear` | 350 | 17 | 14 | TANK | 근접 | 물리 | 양산형 토르 탱커화 | 후반 대형 탱커 |
| T4 | `magma_cube` | Magma Cube | `minecraft:magma_cube` | 380 | 18 | 14 | SWARM, TANK | 근접 | 마법 | 후반 물량 | 분열은 1차 구현 제외 |
| T4 | `ocelot` | Ocelot | `minecraft:ocelot` | 400 | 18 | 15 | SUPPORT, RUSH | 근접 | 물리 | 변신수 | 이동속도 오라 |
| T4 | `vindicator` | Vindicator | `minecraft:vindicator` | 420 | 19 | 15 | DISRUPTOR | 근접 | 물리 | 황실 기사단 | 강한 근접 교란 |
| T4 | `witch` | Witch | `minecraft:witch` | 450 | 19 | 16 | SUPPORT, DISRUPTOR | 원거리 | 마법 | 암흑 고위 기사 | 공속/이속 오라 |
| T4 | `iron_golem` | Iron Golem | `minecraft:iron_golem` | 460 | 20 | 16 | TANK | 근접 | 물리 | 불멸자/토르 | 중후반 고방어 탱커 |
| T4 | `blaze` | Blaze | `minecraft:blaze` | 500 | 22 | 18 | SIEGE | 원거리 | 마법 | 암흑 우주모함 | 일부 방어 무시 후보 |
| T4 | `shulker` | Shulker | `minecraft:shulker` | 520 | 23 | 18 | DISRUPTOR, TANK | 원거리 | 마법 | 공성 방해 | 투사체/이동 방해 컨셉 |
| T4 | `ghast` | Ghast | `minecraft:ghast` | 560 | 25 | 20 | SIEGE | 원거리 | 마법 | 왕복선/스플래시 | 광역 공성 후보 |
| T5 | `zoglin` | Zoglin | `minecraft:zoglin` | 600 | 30 | 22 | TANK, RUSH | 근접 | 물리 | 클레이머 전 단계 | 빠른 최종 근접 |
| T5 | `wither_skeleton` | Wither Skeleton | `minecraft:wither_skeleton` | 650 | 32 | 24 | DISRUPTOR, SIEGE | 근접 | 마법 | 암흑 기사/디버프 | 위더 디버프 후보 |
| T5 | `evoker` | Evoker | `minecraft:evoker` | 680 | 33 | 24 | SUPPORT, DISRUPTOR | 원거리 | 마법 | 고위 기사 | 강한 오라/디버프 |
| T5 | `elder_guardian` | Elder Guardian | `minecraft:elder_guardian` | 700 | 34 | 26 | TANK, DISRUPTOR | 원거리 | 마법 | 보스형 방해 | 사거리/공속 압박 |
| T5 | `warden` | Warden | `minecraft:warden` | 800 | 40 | 30 | TANK, SIEGE | 근접 | 물리 | 클레이머 | 최종 고체력 탱커 |

## 상세 스펙 초안

현재 `SummonMonsterType`이 직접 받는 값은 비용, 수입, 체력, 방어, 공격력, 공격 방식, 바닐라 엔티티, Blockbench 모델 ID, 크기, 피해 타입, 저항, 티어, 역할, 능력 발동 타입, 현상금이다. 이동속도는 아직 명시 필드가 없으므로 구현 메모로만 둔다.

| ID | 체력 | 방어 | 저항 | 공격력 | 크기 | 능력 발동 | 구현 클래스 | 이동/행동 메모 |
|---|---:|---:|---:|---:|---|---|---|---|
| `chicken` | 18 | 0 | 0 | 1 | 0.4 x 0.7 | PASSIVE | `BasicIncomeSummon` | 빠름, 최저 체력 물량 |
| `rabbit` | 26 | 0 | 0 | 2 | 0.4 x 0.5 | PASSIVE | `BasicIncomeSummon` | 매우 빠름, 타겟 분산용 |
| `silverfish` | 24 | 0 | 0 | 2 | 0.4 x 0.3 | PASSIVE | `BasicIncomeSummon` | 작고 빠름, 스플래시 없으면 귀찮은 물량 |
| `zombie` | 42 | 1 | 0 | 3 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | 기준 초반 근접 |
| `husk` | 58 | 3 | 0 | 4 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | 느리지만 약간 단단함 |
| `skeleton` | 36 | 0 | 0 | 4 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | 원거리라 접근 전부터 누적 피해 |
| `wolf` | 52 | 1 | 0 | 5 | 0.6 x 0.85 | CONDITIONAL | `WolfSummon` | 빠른 근접, 공격 시 짧은 타워 공속 감소 후보 |
| `spider` | 70 | 1 | 0 | 5 | 1.4 x 0.9 | PASSIVE | `BasicIncomeSummon` | 넓은 크기와 빠른 이동으로 초반 압박 |
| `cave_spider` | 60 | 1 | 0 | 4 | 0.7 x 0.5 | CONDITIONAL | `CaveSpiderSummon` | 공격 시 약한 지속 피해 또는 타워 디버프 후보 |
| `bee` | 82 | 0 | 2 | 6 | 0.7 x 0.6 | PASSIVE | `BasicIncomeSummon` | 공중처럼 보이지만 일반 경로 사용 |
| `turtle` | 150 | 8 | 0 | 3 | 1.2 x 0.4 | COOLDOWN | `TurtleSummon` | 매우 느림, 피해 감소 오라 중심 |
| `sheep` | 88 | 1 | 0 | 3 | 0.9 x 1.3 | PASSIVE | `BasicIncomeSummon` | 중가 물량, 전투력은 낮음 |
| `zombie_villager` | 125 | 3 | 2 | 6 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | T2 기준 효율 근접 |
| `stray` | 90 | 1 | 2 | 6 | 0.6 x 1.95 | CONDITIONAL | `StraySummon` | 원거리, 약한 둔화 디버프 후보 |
| `allay` | 78 | 0 | 6 | 2 | 0.6 x 0.95 | COOLDOWN | `AllaySummon` | 후방 지원, 광역 회복 |
| `vex` | 86 | 2 | 6 | 8 | 0.4 x 0.8 | PASSIVE | `VexSummon` | 매우 빠름, 낮은 체력의 교란 |
| `fox` | 98 | 1 | 0 | 6 | 0.6 x 0.7 | COOLDOWN | `FoxSummon` | 빠름, 공격력 오라 중심 |
| `slime` | 145 | 2 | 0 | 6 | 1.2 x 1.2 | PASSIVE | `BasicIncomeSummon` | 분열 없이 단일 탱커 물량 |
| `goat` | 135 | 3 | 0 | 9 | 0.9 x 1.3 | COOLDOWN | `GoatSummon` | 돌진 컨셉, 타워 템포 방해 |
| `bogged` | 115 | 2 | 3 | 7 | 0.6 x 1.95 | CONDITIONAL | `BoggedSummon` | 원거리 독/감속 후보 |
| `pillager` | 115 | 2 | 0 | 8 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | 중반 원거리 기준 유닛 |
| `piglin_brute` | 145 | 4 | 0 | 12 | 0.6 x 1.95 | PASSIVE | `BasicIncomeSummon` | 강한 단일 근접 |
| `ravager` | 260 | 10 | 2 | 15 | 1.95 x 1.35 | PASSIVE | `BasicIncomeSummon` | 큰 크기, 높은 체력의 딜탱 |
| `hoglin` | 210 | 6 | 0 | 16 | 1.4 x 1.4 | PASSIVE | `BasicIncomeSummon` | Ravager보다 빠르고 공격적 |
| `horse` | 175 | 4 | 0 | 10 | 1.4 x 1.6 | CONDITIONAL | `HorseSummon` | 매우 빠름, 공격 시 짧은 둔화 후보 |
| `llama` | 155 | 3 | 2 | 9 | 0.9 x 1.87 | PASSIVE | `BasicIncomeSummon` | 약한 원거리 지원 유닛 |
| `phantom` | 165 | 2 | 4 | 12 | 0.9 x 0.5 | PASSIVE | `BasicIncomeSummon` | 공중 시각, 일반 경로 |
| `enderman` | 260 | 4 | 10 | 22 | 0.6 x 2.9 | PASSIVE | `BasicIncomeSummon` | 고수입 선택지, 순간이동은 제외 |
| `breeze` | 190 | 3 | 12 | 13 | 0.6 x 1.77 | COOLDOWN | `BreezeSummon` | 원거리 풍압/넉백 컨셉 |
| `guardian` | 230 | 6 | 14 | 16 | 0.85 x 0.85 | CONDITIONAL | `GuardianSummon` | 원거리 공성, 타워 대상 추가 피해 후보 |
| `polar_bear` | 410 | 13 | 4 | 20 | 1.4 x 1.4 | PASSIVE | `BasicIncomeSummon` | 후반 대형 탱커 |
| `magma_cube` | 360 | 9 | 12 | 18 | 1.4 x 1.4 | PASSIVE | `BasicIncomeSummon` | 분열 없이 마법 저항 탱커 |
| `ocelot` | 205 | 3 | 3 | 10 | 0.6 x 0.7 | COOLDOWN | `OcelotSummon` | 매우 빠름, 이동속도 오라 |
| `vindicator` | 285 | 8 | 2 | 24 | 0.6 x 1.95 | CONDITIONAL | `VindicatorSummon` | 강한 근접 교란 |
| `witch` | 265 | 3 | 16 | 12 | 0.6 x 1.95 | COOLDOWN | `WitchSummon` | 원거리, 공속/이속 오라 |
| `iron_golem` | 520 | 18 | 5 | 30 | 1.4 x 2.2 | PASSIVE | `BasicIncomeSummon` | 느린 중후반 고방어 탱커 |
| `blaze` | 350 | 5 | 16 | 28 | 0.6 x 1.8 | CONDITIONAL | `BlazeSummon` | 원거리 공성, 일부 방어 무시 후보 |
| `shulker` | 430 | 16 | 14 | 18 | 1.0 x 1.0 | COOLDOWN | `ShulkerSummon` | 느림, 투사체/이동 방해 컨셉 |
| `ghast` | 460 | 4 | 18 | 34 | 2.0 x 2.0 | CONDITIONAL | `GhastSummon` | 큰 공중 시각, 광역 공성 후보 |
| `zoglin` | 650 | 16 | 6 | 38 | 1.4 x 1.4 | PASSIVE | `BasicIncomeSummon` | 빠른 최종 근접 탱커 |
| `wither_skeleton` | 620 | 14 | 18 | 34 | 0.7 x 2.4 | CONDITIONAL | `WitherSkeletonSummon` | 지속 피해/위더 디버프 후보 |
| `evoker` | 560 | 8 | 24 | 26 | 0.6 x 1.95 | COOLDOWN | `EvokerSummon` | 강한 복합 오라/디버프 |
| `elder_guardian` | 760 | 22 | 28 | 32 | 2.0 x 2.0 | COOLDOWN | `ElderGuardianSummon` | 느림, 강한 타워 사거리/공속 압박 |
| `warden` | 1050 | 28 | 22 | 46 | 1.55 x 2.6 | CONDITIONAL | `WardenSummon` | 매우 느린 최종 탱커/공성 |

## 밸런스 의도

- T1의 핵심은 체력보다 수와 속도다. `chicken`, `rabbit`, `silverfish`는 죽기 쉽지만 타겟을 분산한다.
- T2는 첫 시너지 구간이다. `turtle`, `allay`, `fox`가 원기옥의 기본 조합이 된다.
- T3는 전투 압박이 확실히 생기는 구간이다. `ravager`, `hoglin`, `enderman`은 서로 다른 방식의 중반 선택지다.
- T4는 오라와 공성의 가치가 커지는 구간이다. 단순 수입 효율보다 전투 효과를 보고 보내야 한다.
- T5는 많이 보내는 유닛이 아니라 판을 흔드는 유닛이다. 수입 효율은 높아 보이지만 현상금과 처치 시간이 크다.

## 세부 효과 수치 초안

| ID | 효과 수치 |
|---|---|
| `wolf` | 가장 가까운 타워 1개에 3초간 공격속도 10% 감소 |
| `cave_spider` | 공격 대상에게 4초 동안 초당 2 고정 피해 후보 |
| `turtle` | 반경 5.5 안 아군 인컴 유닛 받는 타워 피해 25% 감소, 4초 유지, 3초마다 갱신 |
| `stray` | 가장 가까운 타워 1개에 4초간 공격속도 12% 감소 |
| `allay` | 반경 6 안 아군 인컴 유닛 최대 6기에게 8 회복, 6초 쿨다운 |
| `vex` | 기본 이동 빠름. 필요 시 피격 후 2초간 이동속도 20% 증가 후보 |
| `fox` | 반경 6 안 아군 인컴 유닛 공격력 25% 증가, 4초 유지, 3초마다 갱신 |
| `goat` | 가장 가까운 타워 1개에 3초간 공격속도 15% 감소 |
| `bogged` | 원거리 공격 대상에게 5초 동안 초당 2 고정 피해 후보 |
| `horse` | 가장 가까운 타워 1개에 3초간 사거리 10% 감소 |
| `breeze` | 가장 가까운 타워 2개에 3초간 공격속도 15% 감소 |
| `guardian` | 진행도 70% 이상부터 타워/보스 대상 추가 고정 피해 15 |
| `ocelot` | 반경 7 안 아군 인컴 유닛 이동속도 30% 증가, 4초 유지, 3초마다 갱신 |
| `vindicator` | 체력 50% 이하에서 5초간 공격력 30% 증가 후보 |
| `witch` | 반경 7 안 아군 인컴 유닛 공격속도 25%, 이동속도 30% 증가 |
| `blaze` | 진행도 65% 이상부터 타워/보스 대상 추가 고정 피해 25 |
| `shulker` | 가장 가까운 타워 2개에 4초간 사거리 15% 감소 |
| `ghast` | 진행도 75% 이상부터 보스/타워 대상 광역 고정 피해 30 후보 |
| `wither_skeleton` | 공격 대상에게 5초 동안 초당 5 고정 피해 후보 |
| `evoker` | 반경 8 안 아군 인컴 유닛 공격력 25%, 받는 타워 피해 25% 감소 |
| `elder_guardian` | 반경 8 안 타워 최대 3개에 공격속도 30%, 사거리 20% 감소 |
| `warden` | 진행도 70% 이상부터 보스 대상 추가 고정 피해 50, 타워 피해 15% 감소 후보 |

## 오라/특수효과 후보

| ID | 효과 | 적용 대상 | 최대 중첩 가정 | 구현 난이도 |
|---|---|---|---|---|
| `turtle` | 받는 타워 피해 감소 | 같은 sender team 인컴 유닛 | 5% x 5 = 25% | 낮음 |
| `allay` | 주기적 광역 회복 | 같은 sender team 인컴 유닛 | 회복량/대상 수 최대치 | 낮음 |
| `fox` | 공격력 증가 | 같은 sender team 인컴 유닛 | 5% x 5 = 25% | 중간 |
| `ocelot` | 이동속도 증가 | 같은 sender team 인컴 유닛 | 10% x 3 = 30% | 낮음 |
| `witch` | 공격속도/이동속도 증가 | 같은 sender team 인컴 유닛 | 공속 25%, 이속 30% | 중간 |
| `evoker` | 강한 공격/방어 복합 오라 | 같은 sender team 인컴 유닛 | 공격 25%, 피해감소 25% | 높음 |

현재 `TimedEffectType`에는 몬스터 이동속도 증가와 받는 타워 피해 감소가 이미 있다. 공격력 증가, 공격속도 증가, 회복 증폭처럼 몬스터 측 신규 효과가 필요하면 `TimedEffectType` 확장이 필요하다.

## 특수 로직 분리 기준

단순 스탯 유닛은 공통 `BasicIncomeSummon`으로 만들 수 있다. 아래 유닛은 로직이 달라질 가능성이 높으므로 별도 클래스로 분리하는 편이 좋다.

| ID | 분리 이유 |
|---|---|
| `wolf` | 짧은 공격속도 감소 또는 스턴 컨셉 |
| `cave_spider` | 지속 피해/독 |
| `turtle` | 피해 감소 오라 |
| `stray` | 감속 원거리 |
| `allay` | 회복 |
| `vex` | 고속 교란/사거리 감소 가능성 |
| `fox` | 공격력 오라 |
| `goat` | 넉백/템포 방해 |
| `bogged` | 독 원거리 |
| `horse` | 고속 슬로우 |
| `breeze` | 넉백/풍압 |
| `guardian` | 원거리 공성 |
| `ocelot` | 이동속도 오라 |
| `witch` | 공속/이속 오라 |
| `blaze` | 방어 무시 공성 |
| `shulker` | 이동 방해 |
| `ghast` | 광역 공성 |
| `wither_skeleton` | 위더/지속 피해 |
| `evoker` | 복합 오라/디버프 |
| `elder_guardian` | 강한 타워 디버프 |
| `warden` | 최종 탱커/공성 보너스 |

## 구현 시 파일 배치 제안

```text
src/main/java/kim/biryeong/semiontd/summon/
  IncomeSummons.java
  BasicIncomeSummon.java
  WolfSummon.java
  CaveSpiderSummon.java
  TurtleSummon.java
  StraySummon.java
  AllaySummon.java
  VexSummon.java
  FoxSummon.java
  GoatSummon.java
  BoggedSummon.java
  HorseSummon.java
  BreezeSummon.java
  GuardianSummon.java
  OcelotSummon.java
  WitchSummon.java
  BlazeSummon.java
  ShulkerSummon.java
  GhastSummon.java
  WitherSkeletonSummon.java
  EvokerSummon.java
  ElderGuardianSummon.java
  WardenSummon.java
```

## 1차 구현 추천 범위

전체 후보를 한 번에 등록하되, 능력은 단계적으로 켜는 것이 안전하다.

1. 모든 유닛의 비용/수입/스탯/바닐라 엔티티 등록.
2. `turtle`, `allay`, `ocelot`처럼 기존 `TimedEffectType`으로 가능한 효과부터 구현.
3. `fox`, `witch`, `evoker`를 위해 몬스터 공격력/공격속도 계열 timed effect 확장.
4. `wolf`, `stray`, `goat`, `horse`, `breeze`, `shulker`의 교란 효과 구현.
5. `blaze`, `ghast`, `guardian`, `elder_guardian`, `warden`의 공성/보스 압박 효과 구현.

## 검토 필요 항목

- `enderman`은 300 비용 / 30 수입이라 매우 효율적이다. 전작의 고수입 선택지 느낌을 살릴지, 비용을 올릴지 결정이 필요하다.
- T5는 원기옥 메타를 만들 가능성이 높다. `zoglin`, `wither_skeleton`, `evoker`, `elder_guardian`, `warden` 중 일부만 최종 구현에 남겨도 된다.
- 동물 유닛이 너무 많으면 인컴이 가벼워 보일 수 있다. 다만 저티어/서포트 쪽에 배치하면 시각적 다양성과 역할 구분이 좋아진다.
- 공중형 엔티티(`bee`, `phantom`, `blaze`, `ghast`)는 경로 시스템과 충돌할 수 있다. 1차 구현에서는 시각만 공중이고 경로/전투는 일반 몬스터처럼 처리하는 것이 안전하다.
- `slime`, `magma_cube` 분열은 렉과 밸런스 이슈가 크므로 1차 구현에서는 분열 없이 단일 유닛으로 취급하는 편이 좋다.
