# 프로덕션 타워 카탈로그

Minecraft 26.3 / Java 25의 현재 공통 API를 사용하는 제작 가이드입니다. 빌더 목록과 테스트 배치는 [빌더와 타워](builders-and-towers.ko.md), 설정·공식 패치의 값은 [밸런스 문서](tower-balance-reference.ko.md)를 기준으로 합니다.

## 책임과 코드 위치

| 책임 | 현재 코드 | 제작 시 재사용하는 부분 |
|---|---|---|
| 빌더 정의·수명 | `job/SemionJob`, `job/JobRegistry`, 각 `job/*TowerJob` | 직업 정의와 경기 이벤트 훅, 단계별 정리 |
| 타워 정의 | `tower/TowerType`, `tower/catalog/ProductionTowerDefinitions` | 안정적인 ID·기본 스탯·외형·설명 |
| 계열 등록 | `tower/<family>/*TowerCatalogs`, `ProductionTowerCatalogs.reloadBuiltIns` | 팩토리·티어·스타터·업그레이드 그래프 |
| 배치·거래 | `tower/ProductionTowerService` | 단계·권한·좌표·점유·한도·지출 검사와 행동 기록 |
| 런타임 | `Tower`, `EntityBackedTower`, `ProductionTower`, `SupportTower` | 상태·복사·엔티티 수명·기본 공격·지원 실행 |
| 전투 | `entity/tower/SemionTowerEntity`, `goal/TowerAttackMonsterGoal` | 대상 탐색·기본 공격·스탯 동기화 |
| 밸런스와 설명 | `TowerBalanceRuntime`, `TowerDescriptionRegistry` | 로드된 수치·가격·능력과 설명 치환 |
| 범위 효과·연출 | `SemionTdApi.areaEffects()`, `TowerAreaDamage`, `TowerVfxService` | 대상 필터·피해 출처·통계·처치·팔레트·전송 예산 |

경로는 `src/main/java/kim/biryeong/semiontd` 아래입니다. 주민 ADV는 `VillagerTowerCatalogs`에서 함께 등록합니다. 설계도는 `BlueprintStates`가 동적으로 생성하며 `ProductionTowerCatalogs.reloadBuiltIns`에서 다시 설치합니다. 따라서 고정 계열 목록이나 스타터 총수를 새 제작 코드에 복사하지 않습니다.

새 클래스 이름은 패키지·상위 책임·하위 책임 순서로 정합니다. 특정 계열의 동작은 `tower/<family>`에 두고, 다른 수명이나 호출자가 있는 상태·등록·표시 책임만 분리합니다. 한 계열을 위해 별도 배치·피해·통계 체계를 만들 필요는 없습니다.

## 빌더별 구현 방식 선택

변경하려는 동작과 상태 소유권·수명·트리거가 가까운 계열을 찾고, 상태가 적은 계열도 함께 비교합니다. 아래 표는 [JobRegistry.registerBuiltIns](../src/main/java/kim/biryeong/semiontd/job/JobRegistry.java)의 33개 빌더와 별도 기본 등록인 `DefaultJob`을 모두 다룹니다. 표시명은 각 직업 클래스의 현재 값을 사용합니다. 특정 빌더를 모든 책임의 모범으로 지정하거나 controller/state/view의 개수를 맞추지 않습니다.

표의 장점은 코드에서 확인한 책임·상태 경계입니다. 링크한 테스트는 해당 계약의 검증 위치이며 성능 측정 결과가 아닙니다. 책임 분리, 비동기 계산, 큐 또는 스냅샷의 존재만으로 MSPT/TPS 개선을 주장하지 않습니다. 반복 탐색·할당·갱신 비용과 측정 조건은 [성능 분석 보고서](performance-optimization-report.ko.md)에서 구분합니다.

| 등록 항목: 표시명·직업 클래스 | 확인된 구현과 검증 근거 | 재사용에 적합한 경우 | 보존할 조건·개선 필요 |
|---|---|---|---|
| 무직 ([DefaultJob](../src/main/java/kim/biryeong/semiontd/job/DefaultJob.java)) | [SemionJob.canUseTower](../src/main/java/kim/biryeong/semiontd/job/SemionJob.java)의 기본 불허 계약과 빈 기본 훅을 사용한다. 회귀 근거: [JobLifecycleTest](../src/test/java/kim/biryeong/semiontd/job/JobLifecycleTest.java). | 이벤트와 전용 타워가 없는 직업의 최소 구성. | 전용 상태·전투 구현이 없어 전투 구조의 모범으로 인용할 근거는 없다. |
| 주민 빌더 ([VillagerTowerJob](../src/main/java/kim/biryeong/semiontd/job/VillagerTowerJob.java)) | [AllayTower.execute / supportRequest / canApply](../src/main/java/kim/biryeong/semiontd/tower/villager/AllayTower.java)가 지원 주기·공통 영역 API·대상별 재적용 차단을 연결한다. 회귀 근거: [VillagerTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerTowerRuntimeTest.java). | 주기적 치유와 같은 종류의 지원 효과. | 효과 source는 시전자별이 아닌 지원 종류별이다. 골렘 업그레이드의 생존 스택 이전·최대 체력 회복은 계열 고유 계약이다. |
| 주민 ADV 빌더 ([VillagerAdvTowerJob](../src/main/java/kim/biryeong/semiontd/job/VillagerAdvTowerJob.java)) | [VillagerAdvStates.calculateExperienceGains / applyPending](../src/main/java/kim/biryeong/semiontd/tower/villager/VillagerAdvStates.java)가 스냅샷 계산과 현재 직업·lane·타워 소속을 재검증한 반영을 구분한다. 회귀 근거: [VillagerAdvancedTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/villager/VillagerAdvancedTowerRuntimeTest.java). | 타워별 경험치와 플레이어 공통 평판을 함께 관리하는 성장. | 종료 전 대기열 제거와 늦은 결과 검증을 함께 보존한다. 비동기 계산이라는 이유만으로 성능 우위를 주장하지 않는다. |
| 언데드 빌더 ([UndeadTowerJob](../src/main/java/kim/biryeong/semiontd/job/UndeadTowerJob.java)) | [UndeadDrownedTower.applyLastStand](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadDrownedTower.java)가 일반 피해·방어 무시 피해의 생존 조건을 공유한다. [UndeadRangedSkeletonTower.onNearbyMonsterDeath / copyRuntimeStateFrom](../src/main/java/kim/biryeong/semiontd/tower/undead/UndeadRangedSkeletonTower.java)는 사망 알림 성장과 상한 있는 복사를 연결한다. 회귀 근거: [UndeadTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/undead/UndeadTowerRuntimeTest.java). | 여러 피해 경로의 공통 생존 판정과 주변 사망 성장. | 개선 필요: 좀비·원거리 해골 흡혈은 `onAttack`의 시도 피해 기준이며, 원거리 해골 `pickExtraTargets`는 직접 엔티티 검색이다. 실제 피해 흡혈·공통 탐색의 모범으로 쓰지 않는다. |
| 동물 빌더 ([AnimalTowerJob](../src/main/java/kim/biryeong/semiontd/job/AnimalTowerJob.java)) | [FoxTargetingPolicy.select](../src/main/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicy.java)는 표적 순위를 엔티티에서 분리한다. [AnimalStackTower.meetsUpgradeRequirements / refreshAnimalStacks](../src/main/java/kim/biryeong/semiontd/tower/animal/AnimalStackTower.java)는 실제 무리 수와 증강 가상 스택을 구분한다. 회귀 근거: [FoxTargetingPolicyTest](../src/test/java/kim/biryeong/semiontd/tower/animal/FoxTargetingPolicyTest.java). | 조건부 표적 선택, 실제 배치 수를 요구하는 승급. | 각 타워 `tick`의 lane 전체 갱신은 성능 개선 후보다. 효율적 캐시 사례로 설명하지 않으며 소유자·범위·생존·오라 체력 조건을 보존한다. |
| 흑마법사 ([WarlockTowerJob](../src/main/java/kim/biryeong/semiontd/job/WarlockTowerJob.java)) | [WarlockSacrifice.snapshot / calculate / commit](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrifice.java)와 [WarlockSacrificeController.absorbNearest](../src/main/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeController.java)가 입력 계산과 실제 제거 성공 뒤 성장 확정을 구분한다. 회귀 근거: [WarlockSacrificeTest](../src/test/java/kim/biryeong/semiontd/tower/warlock/WarlockSacrificeTest.java). | 자원 소모 성공 시에만 영구·라운드 성장을 확정하는 동작. | 엔드의 부분 적용·롤백과 다른 계약이다. 희생·각성 규칙이나 클래스 수를 일반화하지 않고 하이퍼 캐리 계약을 유지한다. |
| 무리 빌더 ([LegionTowerJob](../src/main/java/kim/biryeong/semiontd/job/LegionTowerJob.java)) | [IllusionCloneSpawnQueue.tick / PendingCloneSpawn.isValid](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionCloneSpawnQueue.java)는 예약 FIFO·tick당 생성 상한·실행 직전 유효성 검사를, [IllusionSummonerTower.createCloneTower / cleanupClones](../src/main/java/kim/biryeong/semiontd/tower/legion/IllusionSummonerTower.java)는 원본 factory 재사용과 분신 정리를 맡는다. 회귀 근거: [LegionTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/legion/LegionTowerRuntimeTest.java). | 지연 소환과 원본 타워 행동을 보존하는 분신. | `cancel(owner)`의 일반 분신과 `cancelAugmentChildren`의 증강 자식은 취소 경로가 다르다. 생성 상한만으로 MSPT 개선을 단정하지 않는다. |
| 무블룸 빌더 ([ResonanceTowerJob](../src/main/java/kim/biryeong/semiontd/job/ResonanceTowerJob.java)) | [ResonanceTowerLinkController.refresh / selectFriend](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkController.java)는 전체 기본 링크→친구→오라 순서를, [ResonanceService.captureWaveStart](../src/main/java/kim/biryeong/semiontd/tower/resonance/ResonanceService.java)는 웨이브 스냅샷을 담당한다. 회귀 근거: [ResonanceTowerLinkControllerTest](../src/test/java/kim/biryeong/semiontd/tower/resonance/ResonanceTowerLinkControllerTest.java). | 전투 중 유지되는 배치 관계와 결정적 친구 선택. | 실시간 주변 캐시가 아니다. 일반 링크 후보 순서·상한과 친구의 링크 수→거리→논리 ID 순서를 구분하며 전투 중 사망·승급 후 스냅샷 유지도 보존한다. |
| 우민 빌더 ([IllagerTowerJob](../src/main/java/kim/biryeong/semiontd/job/IllagerTowerJob.java)) | [IllagerRaidState.consumePendingActivationEffects / consumePendingVolleys](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidState.java)는 일회 소비 상태를, [IllagerRaidStates.onRoundStarted / onWaveStarted](../src/main/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStates.java)는 UUID별 상태와 호출 시점을 구분한다. 회귀 근거: [IllagerRaidStateTest](../src/test/java/kim/biryeong/semiontd/tower/illager/IllagerRaidStateTest.java). | 공유 게이지와 한 번 실행해야 하는 활성 효과. | 타워 수는 준비 단계 라운드 시작에 캡처하고 증강은 실제 웨이브 시작에 초기화한다. 강제 표식→역할별 대상 선택 및 직업 종료 정리를 보존한다. |
| 네더 빌더 ([NetherTowerJob](../src/main/java/kim/biryeong/semiontd/job/NetherTowerJob.java)) | [NetherBloodChargeController.recordNaturalLoss / consume / snapshot / restore](../src/main/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeController.java)는 생성 당시 충전 값의 FIFO와 잔여 손실을 보존하며 독립 복사를 제공한다. 회귀 근거: [NetherBloodChargeControllerTest](../src/test/java/kim/biryeong/semiontd/tower/nether/NetherBloodChargeControllerTest.java). | 충전마다 가치가 달라질 수 있는 소모 자원. | 자연 체력 손실만 입력한다. 임계값 변경 뒤 기존 충전을 새 값으로 재계산하지 않으며 부활 상태와 라운드 초기화는 별도로 보존한다. |
| 엔드 빌더 ([EndTowerJob](../src/main/java/kim/biryeong/semiontd/job/EndTowerJob.java)) | [EndTransferController.resolveCompletions / rollbackIncomplete / copyCommittedFrom](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferController.java)와 [EndTransferState.committedSnapshot](../src/main/java/kim/biryeong/semiontd/tower/end/EndTransferState.java)가 미완료 기여 롤백과 원본 불변의 확정 상태 복사를 제공한다. 회귀 근거: [EndTransferLifecycleTest](../src/test/java/kim/biryeong/semiontd/tower/end/EndTransferLifecycleTest.java). | 여러 tick에 걸쳐 부분 반영되며 중단 가능한 전이. | 진화·현재 체력·설정 리로드를 롤백과 함께 검증한다. 흑마법사의 즉시 확정과 합치지 않고 하이퍼 캐리 계약을 유지한다. |
| 바다 빌더 ([OceanTowerJob](../src/main/java/kim/biryeong/semiontd/job/OceanTowerJob.java)) | [OceanCurrentController.recordSpent / snapshot / restore](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanCurrentController.java)는 소비 잔여·파도 시계·충전을, [OceanWaterTower.captureSupplyTargets / restoreWater](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanWaterTower.java)는 공급 대상과 소유 블록 복원을 관리한다. 회귀 근거: [OceanTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/ocean/OceanTowerRuntimeTest.java). | 저장 자원 공급과 임시 월드 표현을 함께 갖는 타워. | 저장 물과 라운드 충전의 수명, 공급 UUID 스냅샷·분배 순서·이동 후 공급 유지를 보존한다. 컨트롤러 주기·임계값의 유효 입력 조건도 확인한다. |
| 고대 도시 빌더 ([AncientCityTowerJob](../src/main/java/kim/biryeong/semiontd/job/AncientCityTowerJob.java)) | [AncientCityStates.onWaveStarted / recordAttributedDeath](../src/main/java/kim/biryeong/semiontd/tower/ancientcity/AncientCityStates.java)가 소유자별 영역·웨이브 성장·성공한 사망 성장 한도를 분리한다. 회귀 근거: [JobLifecycleRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/job/JobLifecycleRuntimeTest.java). | 소유자 단위 지형과 라운드 예산. | 본진과 최종방어 영역은 별도다. 보상 귀속 경로와 `closeBeforeLanes` 정리 순서를 보존한다. |
| 히어로 빌더 ([AdversaryTowerJob](../src/main/java/kim/biryeong/semiontd/job/AdversaryTowerJob.java)) | [AdversaryProgressStates.recordFoxKill](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryProgressStates.java)와 [AdversaryProgressState.reconcileRivals](../src/main/java/kim/biryeong/semiontd/tower/adversary/AdversaryProgressState.java)가 정확한 처치 관계·중복 지급 검사와 설치 기여 원장·진화·강등을 분리한다. 회귀 근거: [AdversaryRivalLedgerTest](../src/test/java/kim/biryeong/semiontd/tower/adversary/AdversaryRivalLedgerTest.java). | 제거 가능한 기여원으로 성장하는 타워. | 라이벌 제거 콜백이 원장을 재조정하므로 `closeAfterLanes` 정리를 앞당기지 않는다. 모든 성장 모델에 강등 규칙을 적용하지 않는다. |
| 마도사 빌더 ([MageTowerJob](../src/main/java/kim/biryeong/semiontd/job/MageTowerJob.java)) | [MageTowerLifecycle.finishRound](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerLifecycle.java)는 소유자·생존·코어 조건으로 자원을 정산하고, [MageTowerRuntime.restoreTemporaryTowers](../src/main/java/kim/biryeong/semiontd/tower/mage/MageTowerRuntime.java)는 catalog·두 위치·공통 복사로 임시형을 복구한다. 회귀 근거: [MageTowerCatalogTest](../src/test/java/kim/biryeong/semiontd/tower/mage/MageTowerCatalogTest.java). | 생존 조건부 정산과 선택형 임시 타워 복구. | 영속 시전 횟수와 초기화할 주문 실행 상태의 수명을 구분하며 라운드 종료 순서를 유지한다. |
| 기술자 ([EngineerTowerJob](../src/main/java/kim/biryeong/semiontd/job/EngineerTowerJob.java)) | [EngineerTrapSignalController.select / distances](../src/main/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalController.java)는 방향성 회로 스냅샷에서 최근 입력→거리→좌표 선택을 계산한다. 회귀 근거: [EngineerTrapSignalControllerTest](../src/test/java/kim/biryeong/semiontd/tower/engineer/EngineerTrapSignalControllerTest.java). | 월드 실행과 분리해 시험할 회로·연결 선택. | `EngineerTrapTower.plateActivation`의 소유자 필터·실제 전원·최종방어 조건을 함께 유지한다. 스냅샷 구성 비용을 제외한 성능 우위는 주장하지 않는다. |
| 벌레 빌더 ([InsectTowerJob](../src/main/java/kim/biryeong/semiontd/job/InsectTowerJob.java)) | [InsectUnitTower.isDestroyed / livingLinkedSpawners](../src/main/java/kim/biryeong/semiontd/tower/insect/InsectUnitTower.java)가 부활 대기·영구사망을 먼저 확정한 뒤 사망 AoE를 실행하고 원래 소환기 위치의 연결을 유지한다. 회귀 근거: [InsectGameTest](../src/gametest/java/kim/biryeong/semiontd/tower/insect/InsectGameTest.java). | 재진입 위험이 있는 사망 효과와 이동 후 부활. | `isDestroyed`는 상태 전이·피해 실행을 포함한다. 소환기 소실 시 취소, 이동·복사·라운드 초기화를 함께 검토한다. |
| 미래기관 빌더 ([FutureAgencyTowerJob](../src/main/java/kim/biryeong/semiontd/job/FutureAgencyTowerJob.java)) | [FutureAgencyAgentTower.linkedSurvivors / onUpgradeCompleted / captureWaveStart](../src/main/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyAgentTower.java)가 소유자·lane·원래 위치로 생존자를 연결하고 승급의 등급·현재 위치·체력비를 유지한다. 회귀 근거: [FutureAgencyGameTest](../src/gametest/java/kim/biryeong/semiontd/tower/futureagency/FutureAgencyGameTest.java). | 원본에 연결된 지속 복사체와 후행 웨이브 스냅샷. | 타워 callback·특성 이후 캡처 순서, `worldSaved` 최종방어 분기, 웨이브당 한 번 치명상 복구를 일반 복사 규칙에 합치지 않는다. |
| 붉은 여왕 빌더 ([QueenTowerJob](../src/main/java/kim/biryeong/semiontd/job/QueenTowerJob.java)) | 직업의 `canUseTower / includesTowerInCatalog`는 배치 권한과 소유권을 구분한다. [QueenPoker.snapshot](../src/main/java/kim/biryeong/semiontd/tower/queen/QueenPoker.java)은 웨이브 시작 배치 조합을 확정한다. 회귀 근거: [QueenGameTest](../src/gametest/java/kim/biryeong/semiontd/tower/queen/QueenGameTest.java). | 문맥 의존 해금과 배치 조합 스냅샷. | 전투 중 카드 변경과 다음 웨이브 반영을 구분한다. 조커 최적 족보·행 순서·중첩 창은 계열 전용 규칙이다. |
| 용사 빌더 ([HeroPartyTowerJob](../src/main/java/kim/biryeong/semiontd/job/HeroPartyTowerJob.java)) | [HeroCompanionSupportController.tick / copyFrom / resetRound](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportController.java)는 지원 실행·진행 복사·초기화를, [HeroCompanionStatsView.abilities](../src/main/java/kim/biryeong/semiontd/tower/hero/HeroCompanionStatsView.java)는 표시를 맡는다. 회귀 근거: [HeroCompanionSupportControllerTest](../src/test/java/kim/biryeong/semiontd/tower/hero/HeroCompanionSupportControllerTest.java). | 지원 cooldown과 라운드 pulse의 수명이 다른 동료. | 승급에는 둘 다 복사하되 라운드에는 pulse만 초기화한다. 영웅의 지정 증강 효율 예외는 하이퍼 캐리 분류와 별도로 유지한다. |
| 아틀란티스 빌더 ([AtlantisTowerJob](../src/main/java/kim/biryeong/semiontd/job/AtlantisTowerJob.java)) | [AtlantisPressure.addStacks / consumeForBurst / Chain](../src/main/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressure.java)는 소유자·몬스터별 압력과 원래 격자 source를 보존하고 연쇄의 중복 대상·깊이·고유 대상 수를 제한한다. 회귀 근거: [AtlantisPressureTest](../src/test/java/kim/biryeong/semiontd/tower/atlantis/AtlantisPressureTest.java). | 타워 교체 뒤 유지되는 대상별 누적과 재귀 처치 AoE. | `monstersFrom`은 전체 압력 맵을 순회한다. 소유자별 독립 상태를 몬스터 단일 상태로 합치지 않으며 `finally`의 활성 연쇄 해제를 유지한다. |
| 식물 빌더 ([PlantTowerJob](../src/main/java/kim/biryeong/semiontd/job/PlantTowerJob.java)) | [PlantSoilStates.releaseFrom / restore](../src/main/java/kim/biryeong/semiontd/tower/plant/PlantSoilStates.java)는 원래 블록·생성원 위치를 보존해 자기 토양만 복구한다. [PlantTowerJob.onRoundEnded](../src/main/java/kim/biryeong/semiontd/job/PlantTowerJob.java)는 생존 타워 수입을 별도 정산한다. 회귀 근거: [PlantTowerIntegrationTest](../src/gametest/java/kim/biryeong/semiontd/tower/plant/PlantTowerIntegrationTest.java). | 가역적인 지형 점유와 환경·정산 수명 분리. | `clear(owner)`의 맵 제거는 블록 복원이 아니다. 타워 제거→복구→새 타워 배치 순서를 유지한다. |
| 군대 빌더 ([ArmyTowerJob](../src/main/java/kim/biryeong/semiontd/job/ArmyTowerJob.java)) | [ArmyTower.onWaveStarted / completeServiceWave / completeDischarge](../src/main/java/kim/biryeong/semiontd/tower/army/ArmyTower.java)는 웨이브 참여분을 종료 시 정산하고 판매·자동 전역을 한 번만 지급하는 경로로 모은다. 회귀 근거: [ArmyTowerRuntimeTest](../src/gametest/java/kim/biryeong/semiontd/tower/army/ArmyTowerRuntimeTest.java). | 사망 뒤에도 참여를 정산하는 성장과 여러 처분 경로. | 근속은 타워, 훈장·퇴역 기록은 플레이어 상태다. 정산 전 참여 스냅샷 삭제나 lane 제거 실패 시 지급을 허용하지 않는다. |
| 람쥐썬더 빌더 ([ThunderTowerJob](../src/main/java/kim/biryeong/semiontd/job/ThunderTowerJob.java)) | [ThunderStates.rollStorm](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderStates.java)의 라운드 공유 난수와 [ThunderPower.snapshot](../src/main/java/kim/biryeong/semiontd/tower/thunder/ThunderPower.java)의 공급·소비 계산을 분리한다. 회귀 근거: [ThunderPowerTest](../src/test/java/kim/biryeong/semiontd/tower/thunder/ThunderPowerTest.java). | 여러 타워가 같은 라운드 난수를 공유하는 자원망. | `ThunderTower.refreshGrid`는 결과를 10틱 주기로 보관하고 웨이브 시작에 강제 갱신한다. 항상 즉시 최신이거나 공유 캐시 최적화가 끝났다고 설명하지 않는다. |
| 마왕 빌더 ([DemonLordTowerJob](../src/main/java/kim/biryeong/semiontd/job/DemonLordTowerJob.java)) | [DemonLordStates.clear / getOrCreate / resetProgression](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordStates.java)는 전투 상태 정리와 경기 성장 복원을 구분한다. [DemonLordService.dealDamage](../src/main/java/kim/biryeong/semiontd/tower/demonlord/DemonLordService.java)는 대상별 피해 계산 순서를 모은다. 회귀 근거: [DemonLordGameTest](../src/gametest/java/kim/biryeong/semiontd/tower/demonlord/DemonLordGameTest.java). | 플레이어 직접 조작 전투체, 재접속 성장 복원. | 공통 `DamageLifeSteal` 이행은 미완료다. 레벨 배율 0.5/0.5와 본체 피해 포인트 100/100·100/50을 구분하고 TRUE·별도 소환수 예외, 재시전 원시 피해, 기존 회복 상한을 유지한다. |
| 겜블 빌더 ([GambleTowerJob](../src/main/java/kim/biryeong/semiontd/job/GambleTowerJob.java)) | [GambleState.recordReward](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleState.java)는 방어 복사한 불변 상태를 반환하고, [GambleTowerStatsView.runtimeDetailLines / upgradeTooltipLines](../src/main/java/kim/biryeong/semiontd/tower/gamble/GambleTowerStatsView.java)는 RNG를 소비하지 않고 표시한다. 회귀 근거: [GambleTowerStatsViewTest](../src/test/java/kim/biryeong/semiontd/tower/gamble/GambleTowerStatsViewTest.java). | 확률 결과 저장과 UI 조회의 부작용 방지. | 상태 상한은 현재 설정을 읽는다. 재시도 정산의 RNG 순서, 구매 횟수와 시도 횟수를 보존한다. |
| 서큐버스 빌더 ([SuccubusTowerJob](../src/main/java/kim/biryeong/semiontd/job/SuccubusTowerJob.java)) | [SuccubusDreamState.add / tickCounters / clearStacksForWake](../src/main/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreamState.java)가 타이머·수면 전이를 분리하고 깨울 때 귀속·수면 이력을 남긴다. 회귀 근거: [SuccubusDreamStateTest](../src/test/java/kim/biryeong/semiontd/tower/succubus/SuccubusDreamStateTest.java). | 여러 타이머가 있는 상태이상과 누적 이력·일시 상태 분리. | 타워·엔티티·lane 참조를 갖는 가변 상태이며 불변 스냅샷이 아니다. 몬스터 UUID당 최초 소유자 귀속, persistent 효과 제거와 재귀 깨우기 방지를 유지한다. |
| 신체 빌더 ([BodyTowerJob](../src/main/java/kim/biryeong/semiontd/job/BodyTowerJob.java)) | [BodyTowerTargetGeometry.eyeDirection / insideEyeRay](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometry.java)는 기하를 분리하고, [BodyTower.eyeAction](../src/main/java/kim/biryeong/semiontd/tower/body/BodyTower.java)은 공통 영역·피해 API로 연결한다. 회귀 근거: [BodyTowerTargetGeometryTest](../src/test/java/kim/biryeong/semiontd/tower/body/BodyTowerTargetGeometryTest.java). | 고정 방향 통로·직선 공격의 기하 검증. | 수평 판정에는 높이 검사가 없고 최종방어에서는 마지막 유효 경로 구간을 쓴다. 심장 pulse 실행 전체를 범용 타게팅 체계로 복사하지 않는다. |
| 반려동물 빌더 ([PetTowerJob](../src/main/java/kim/biryeong/semiontd/job/PetTowerJob.java)) | [PetBondService.refresh / bindLoyalty / resolvePacks](../src/main/java/kim/biryeong/semiontd/tower/pet/PetBondService.java)는 관계를 격자 위치로 묶어 승급 교체를 견디며 컬렉션 입력으로 관계를 시험한다. 회귀 근거: [PetBondGrowthTest](../src/test/java/kim/biryeong/semiontd/tower/pet/PetBondGrowthTest.java). | 배치 인접 관계와 업그레이드 후 유대 유지. | 최종방어 이동 중에는 기존 관계를 유지한다. 겹친 위치로 다시 계산하지 않으며 BFS 내부 후보 순회 비용을 누락하지 않는다. |
| 개발자 빌더 ([DeveloperTowerJob](../src/main/java/kim/biryeong/semiontd/job/DeveloperTowerJob.java)) | [DeveloperPatchService.refreshCapacity / applyPatch / applyCommittedPatch](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperPatchService.java)가 파생 한도 갱신과 예산 소비·예약/즉시 적용을 구분한다. [DeveloperTowerPatchEfficiency.resolve](../src/main/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerPatchEfficiency.java)는 효율 규칙을 분리한다. 회귀 근거: [DeveloperTowerPatchEfficiencyTest](../src/test/java/kim/biryeong/semiontd/tower/developer/DeveloperTowerPatchEfficiencyTest.java). | 라운드 사용권과 예약·즉시 적용 행동. | `refreshCapacity`를 `openRound`로 바꾸면 예산이 다시 생긴다. 효율 helper도 lane 명단을 조회하므로 순수 수학 함수나 캐시 최적화로 과장하지 않는다. |
| 혹한 빌더 ([FrostTowerJob](../src/main/java/kim/biryeong/semiontd/job/FrostTowerJob.java)) | [FrostFullOperationState.record / activate / expire / beginWave](../src/main/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationState.java)는 계열별 동일 tick 중복·활성·사용 완료·만료를 구분하고 서비스가 장치·자원을 재검사한다. 회귀 근거: [FrostFullOperationStateTest](../src/test/java/kim/biryeong/semiontd/tower/frost/FrostFullOperationStateTest.java). | 여러 생산 계열의 공동 충전과 웨이브 1회 활성 능력. | `expire`는 `usedThisWave`를 해제하지 않는다. 상태 객체의 `activate` 직접 호출로 서비스 검증을 우회하지 않는다. |
| 해적 빌더 ([PirateTowerJob](../src/main/java/kim/biryeong/semiontd/job/PirateTowerJob.java)) | [PirateStates.recordDiamondSpend / grantFerrymanSaleIncome / close](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateStates.java)가 경기 자원을 소유하고, [PirateTower.cashOutChest / advanceChestTimer](../src/main/java/kim/biryeong/semiontd/tower/pirate/PirateTower.java)는 제거 성공 후 정산 표식을 먼저 설정한다. 회귀 근거: [PirateEconomyTest](../src/test/java/kim/biryeong/semiontd/tower/pirate/PirateEconomyTest.java). | 지출 연동 성장과 연쇄 만기·판매의 중복 지급 방지. | 만기·직접 판매·환불·뱃사공 수입은 세금·원가·증강 적용 대상이 다르다. 여러 책임이 남은 `PirateTower` 전체를 일괄 모범으로 삼지 않는다. |
| 빌더 빌더 ([BlueprintTowerJob](../src/main/java/kim/biryeong/semiontd/job/BlueprintTowerJob.java)) | [BlueprintStates.create / reinstall / clear](../src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintStates.java)는 동적 타입을 공통 catalog에 등록하고, [BlueprintTowerSummonController.summon / expire / dismiss](../src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerSummonController.java)는 소환 생성·만료·철수를 관리한다. 회귀 근거: [BlueprintTowerModuleTest](../src/gametest/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTowerModuleTest.java). | 사용자 정의 카탈로그와 생성원보다 오래 유지되는 소환체. | 계정 라이브러리와 경기 상태의 수명을 구분한다. 소환수는 clone 형태이므로 대상 mode와 생성자 사망 후 만료·정리를 함께 확인한다. |
| 마법학교 빌더 ([MagicSchoolTowerJob](../src/main/java/kim/biryeong/semiontd/job/MagicSchoolTowerJob.java)) | [MagicSchoolWizardTower.onWaveStarted / copyRuntimeStateFrom / recordDamageDealt](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolWizardTower.java)가 숙련도 지급 guard와 선택적 승급 초기화를 보존한다. `MagicSchoolWizardTower.recordDamageDealt`가 cooldown을 먼저 설정한 뒤 [MagicSchoolSpellCombat.transfer](../src/main/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolSpellCombat.java)로 공통 AoE 피해를 실행한다. 회귀 근거: [MagicSchoolProficiencyTest](../src/test/java/kim/biryeong/semiontd/tower/magicschool/MagicSchoolProficiencyTest.java). | 승급별 성장 초기화와 여러 실제 피해 경로의 후속 효과. | 같은 타입 복사와 졸업을 구분한다. 주문 변경·승급으로 보상이나 주기를 재지급하지 않으며 방어 오라의 논리 ID source와 곱연산 규칙을 유지한다. |

계열 내부 도우미는 다른 빌더가 직접 호출하는 공용 API가 아닙니다. 공통 배치·피해·영역 효과·상태 복사 경로를 먼저 사용하고, 독립적인 호출자나 수명이 있는 책임만 분리합니다. 여러 계산이 공유하는 설정은 타입으로 표현하되 리로드 의미를 보존합니다. 단순 훅은 타워에 남기며 이벤트가 없는 직업은 `JobLifecycle.NONE`을 사용합니다.

표시 도우미는 전투 상태나 RNG를 바꾸지 않고, 상위 설명을 받으면 유지합니다. 월드 엔티티를 다루는 컨트롤러와 참조를 보유한 가변 상태를 순수 계산·불변 스냅샷으로 취급하지 않습니다. 복사·초기화는 필드 수가 아니라 영구·라운드·웨이브·타워 수명에 맞추며, 계산·상태의 JUnit과 실제 호출자의 GameTest를 함께 유지합니다.

하이퍼 캐리형의 특성·증강 제한, 흡혈의 표시·실제 회복 일치, 선형 구간 이후 성장 점감은 [빌더와 타워](builders-and-towers.ko.md)의 공통 계약을 따릅니다. 흑마법사 희생의 성공 후 확정과 엔드 전이의 부분 롤백은 별개이며, 마왕의 공통 흡혈 미이행을 구조 분리만으로 완료 처리하지 않습니다.

## 직업 이벤트와 레인 생명주기

직업 클래스는 ID·이름·설명·타워 권한·경제 보정과 경기 이벤트 훅을 함께 가집니다. 이벤트는 `SemionJob`의 기존 8개 훅을 각 `*TowerJob`이 직접 override해 처리합니다. 현재 `JobRegistry`의 기본 직업 포함 34개 중 23개가 훅을 override하고, 11개는 `SemionJob`의 빈 기본 메서드를 그대로 씁니다.

새 직업은 `JobRegistry`에 등록하고, 필요한 이벤트 훅만 자신의 `*TowerJob`에서 override합니다. 직업은 공유 객체이므로 플레이어별 가변 상태를 필드에 두지 않습니다. 실제 상태와 전투 규칙은 기존 계열 서비스·타워에 남기고, 직업의 훅은 호출 순서를 연결합니다.

| 훅 | 이전 실행 위치 | 현재 실행 경로 | 보존할 고유 동작·조건 |
|---|---|---|---|
| `onSelected` | `SemionJob`의 빈 기본 훅 | override한 직업 없음 | 시작 자원 적용 후, `team.addPlayer` 이전. 현재 별도 선택 동작 없음 |
| `onMatchStarted` | 각 직업의 override | 공통 dispatch → 각 `Job<Builder>Lifecycle` | 초기화와 팀 등록, 설계도 설치, 해적 경기 상태 개설, 마왕 경기 성장 초기화 |
| `onRoundStarted` | 각 직업의 override | 동일한 공통 경로 | 번개 공유 추첨, 개발자 예산, 용사 퀘스트, 고대 도시 라운드 처리. 활성·비탈락 팀만 호출 |
| `onRoundEnded` | 각 직업의 override | 동일한 공통 경로 | 군인 전역·환급, 식물 수입, 마법사 라운드 종료, 해적 타워 처리, 퀘스트 완료·다음 선택 한도 |
| `onEliminated` | 각 직업의 override | 동일한 공통 경로 | 온라인 참가자의 선행 정리·관전 전환 후, 모든 팀원의 소유자 상태와 팀 효과 정리 |
| `onMatchClosed` | 일부 직업 override와 경기 종료 정리 | 직업의 override 후 경기 전체의 단계별 정리 | 반복 호출 가능. 우민 습격·번개 폭풍도 종료 시 해제 |
| `onSummonedMonster` | `SemionJob`의 빈 기본 훅 | override한 직업 없음 | 기존 소환 인자 전달, 현재 별도 동작 없음 |
| `onMonsterKilled` | 고대 도시·흑마법사·마왕 override | 각 `*TowerJob`의 override | 영토 확장·각성 진척·전투 중 경험치 처리, 한 처치의 중복 반영 방지 |

선택 해제 훅은 현재 없습니다. 재접속도 선택·경기 시작 훅을 다시 실행하지 않습니다. 레인이 필요한 초기화는 실제 연결 이후 훅에서 처리합니다. 연결 종료 시에는 `Events`의 연결 종료 처리가 도박 표시·마왕·냉기 플레이어 정리의 기존 순서를 담당합니다.

팀 탈락 시 `SemionGame`은 온라인 플레이어마다 기존 마왕 정리(`DemonLordService.cleanupPlayer`)를 1회 실행합니다. 관전 팀 배정·이동 전에 호출하며, 이후 모든 팀원의 직업 `onEliminated`를 호출하는 순서를 유지합니다.

`PlayerLane`의 `resetForRound`·`markWaveStarted`·`clearTowers`는 경계별 작업을 순서대로 실행합니다. 플레이어 라운드 훅과 타워 웨이브 훅을 하나로 합치지 않습니다.

| 공통 진입점 | 유지해야 할 경계 |
|---|---|
| `beforeRoundReset` | 임시 복제 제거 전 계열·증강 정리 |
| `afterTemporaryCopiesRemoved` | 임시 복제 제거 후 냉기 가동·마왕 전투·초월·몽환 상태 정리, 개별 타워 reset 전 |
| `prepareWave` | 증강·냉기 웨이브 준비와 초월 정리, 라운드 추적 정보 초기화 전 |
| `beforeTowerWaveStarted` | 타워별 `onWaveStarted` 호출 전 군단·주민 ADV·우민·냉기 처리 |
| `afterTowerWaveStarted` | 타워 훅과 라운드 특성 적용 후 공명·체력·대적자·미래 기관 스냅샷, 해적·예비군·마왕 전투 진입 |
| `beforeTowersCleared` | 타워 제거 콜백 전에 계열·증강 정리 |

마왕의 전투 진입은 실제 웨이브 시작 이후에 유지합니다. 준비 단계의 `onRoundStarted`로 옮기면 상점·핫바 동작이 달라집니다. 경기 전체의 주민 ADV 웨이브 시작·완료도 `JobBuilderLifecycle.onWaveStarted`·`onWaveCleared`를 통해 기존 위치에서 호출합니다.

경기 종료 순서는 참가자별 `onMatchClosed` → 마왕 온라인/오프라인 정리 → 레인 제거 전 정리 → 팀·레인·타워 종료 → 레인 제거 후 정리입니다. 두 정리 단계는 `SemionGame.closeRuntimeState()`에 있으며 선택 직업과 무관한 모든 참가자 UUID를 대상으로 합니다. 전행 단계는 주민 ADV·고대 도시·기술자, 후행 단계는 모든 참가자의 아틀란티스 상태·압력을 먼저 정리한 뒤 대적자·마법사·미래 기관·여왕·용사·군인 상태를 정리합니다. 대적자 설치 점수는 타워 제거 도중 조정되므로 후행 정리를 앞당기지 않습니다.

`JobLifecycleTest`로 소유자 범위·반복 종료·처치 1회 반영을 검사합니다. `JobLifecycleRuntimeTest`는 고대 도시 처치 상한·라운드 초기화·종료 정리와 대적자 타워 제거 후 정리 순서를 실제 서버에서 검사하며, 계열 GameTest도 유지합니다. 우민·번개의 종료 정리 보완을 제외한 본문 이동은 게임 규칙 변경의 근거가 되지 않습니다.

## 등록과 업그레이드

현재 구현 예시는 [OceanTowerCatalogs](../src/main/java/kim/biryeong/semiontd/tower/ocean/OceanTowerCatalogs.java)입니다. 타입 정의, 런타임 클래스, 등록 클래스를 함께 읽습니다.

1. `JobRegistry.registerBuiltIns()`에 빌더를 연결합니다. 직업은 공유 객체이므로 플레이어별 가변 상태는 키가 있는 상태 서비스에 둡니다.
2. `TowerBalanceRuntime.resolve(type)`로 실제 설정이 적용된 타입을 등록합니다. 직접 설치할 1티어는 `registerStarter`, 상위 티어는 `register(type, factory, tier)`를 사용합니다.
3. 모든 끝점을 등록한 후 업그레이드를 연결합니다. 업그레이드 ID를 유지하고 비용은 `TowerBalanceRuntime.upgradeCost(from, upgradeId)`로 읽습니다.
4. 계열의 등록 함수를 `ProductionTowerCatalogs.reloadBuiltIns(...)`에 연결합니다. `canUseTower`로 실제 배치를 허용하고 `includesTowerInCatalog`로 공개 소유권을 정확히 하나 지정합니다.
5. 패키지 기본값, 부분 설정 병합, 설명과 테스트를 함께 작성합니다. 기존 운영 설정의 값을 덮어쓰거나 삭제된 운영 파일을 되살리는 절차는 아닙니다.

아래는 이미 정의된 `from`, `to`, `factory`를 사용하는 등록 구문입니다. 실제 계열에서는 모든 타입을 먼저 등록한 뒤 링크를 만듭니다.

```java
TowerType resolvedFrom = TowerBalanceRuntime.resolve(from);
TowerType resolvedTo = TowerBalanceRuntime.resolve(to);
ProductionTowerCatalog.registerStarter(resolvedFrom, factory);
ProductionTowerCatalog.register(resolvedTo, factory, 2);
ProductionTowerCatalog.linkUpgrade(
        resolvedFrom,
        to.id(),
        to.displayName(),
        resolvedTo,
        TowerBalanceRuntime.upgradeCost(from, to.id())
);
```

`TowerFactory`는 타입·소유자·팀·레인·원래 좌표·현재 좌표를 받아 `Tower`를 만듭니다. 두 좌표를 보존해야 이동과 최종 방어, 업그레이드가 같은 의미를 유지합니다. 기본 팩토리는 `ProductionTower::new`이며, 고유 동작이 있을 때만 계열 팩토리를 지정합니다.

배치와 업그레이드는 `ProductionTowerService`를 통과시킵니다. 업그레이드 시 `Tower.copyFrom(previous, upgradeCost)`가 투자금과 공통 상태를 전달합니다. `TowerDataKey`의 단순 값은 공통 복사를 사용하고, 별도 필드나 독립 복사가 필요한 가변 값만 `copyRuntimeStateFrom`에서 처리합니다. 목적지의 설치 가격은 업그레이드 가격이 아닙니다.

## 런타임과 공통 훅

일반 기본 공격 타워는 `ProductionTower`를 우선합니다. 엔티티 수명만 필요하고 기본 동작과 충돌할 때 `EntityBackedTower`, 주기적 지원 행동에는 `SupportTower.execute(PlayerLane)`를 검토합니다. 프로덕션 타워는 테스트용 `TestTower`를 상속하지 않습니다.

| 필요한 동작 | 사용할 훅 또는 API |
|---|---|
| 실제 피해·처치 결과에 따른 효과 | `onAttackResolved` |
| 귀속된 타워 피해 | `Tower.damageTargetResult(...)` |
| 광역 피해·지원 | `SemionTdApi.areaEffects()`, `TowerAreaDamage` |
| 웨이브 시작의 명단/연결 캡처 | `onWaveStarted` |
| 동적 설명 | `runtimeDetailLines()`와 공용 timed-effect 표시 |
| 변경된 엔티티 외형 동기화 | `visual()`과 `onStateChanged()` |
| 경기 종료·취소 시 상태 정리 | 각 `*TowerJob`의 `onMatchClosed`와 `SemionGame.closeRuntimeState()`의 레인 제거 전후 정리 |

직접 엔티티 피해를 주면 출처·물리/마법 통계·처치 전달을 빠뜨릴 수 있으므로 공통 경로를 사용합니다. `aggroPriority`는 몬스터가 타워를 고르는 우선순위입니다. 타워의 적 선택 규칙은 `selectAttackTarget`과 공용 goal에서 다룹니다.

레인 변경은 공용 등록·제거·교체 경로로 수행합니다. 틱 순회는 순서가 있는 스냅샷과 현재 멤버십을 함께 검사하므로 콜백 중 제거와 다음 틱에 참여할 추가를 구분합니다. 공유 가능한 집계만 재사용하고, 순차 자원 배분·난수·동률 선택·반올림 순서는 보존합니다. 새 캐시를 두면 이동·업그레이드·사망·리로드·경기 종료 등 해당 값의 변경 지점을 모두 처리합니다.

## 설정·외형·표시

`TowerBalanceConfig.defaultConfig()`는 `BundledBalanceDefaults`를 통해 `src/main/resources/semiontd/balance-defaults/tower_balance.json`을 읽고 코드 기본값을 fallback으로 사용합니다. 타워 공통 스탯은 `towers`, 업그레이드 간선 가격은 `upgradeCosts`, 고유 동작의 값은 `abilities`에 둡니다. `withMissingDefaults`는 기존 값을 유지하면서 새 키를 채워야 합니다.

숫자 설명은 `TowerDescriptionRegistry.registerTemplate`에 등록하고 `TowerBalanceRuntime.resolve(...)`로 해석합니다. 동적인 스택·모드·자원은 `runtimeDetailLines()`로 보여 줍니다. 설명 문자열에 기본값을 다시 적어 런타임과 다른 규칙을 만들지 않습니다.

외형은 현재 [entity/visual](../src/main/java/kim/biryeong/semiontd/entity/visual)의 typed builder를 사용합니다. `VillagerVisual`, `WolfVisual`, `PandaVisual`, `BlockDisplayVisual` 등 가장 가까운 타입의 실제 26.3 import와 API를 확인합니다. 임의의 tracked-data 문자열이나 예전 Minecraft 패키지를 복사하지 않습니다. Blockbench 모델은 해당 모델 적용 스킬과 리소스팩 생성 경로를 따릅니다.

각 새 빌더는 전용 `BuilderPalette`를 갖고 `TowerVfxService.paletteFor(...)`에서 모든 타워가 그 팔레트로 연결되어야 합니다. 실제 공격·범위·특수 연출이 이 경로를 사용하는지도 검증합니다. enum 추가만으로 시각 구현이 완료되지는 않습니다.

## 구현과 함께 검증하기

JUnit은 `src/test/java`, Fabric GameTest는 `src/gametest/java`의 대응 책임 패키지에 둡니다. 새 테스트명은 `Test`로 끝내되 GameTest 어노테이션·호출 인터페이스·소스 세트는 유지합니다.

- `TowerBuilderCatalogContractTest`: 빌더별 카탈로그 생성·소유권·좌표·업그레이드·투자금 복사. 동적 설계도도 fixture로 생성합니다.
- `tower/<family>`의 JUnit: 고유 계산·상태·설정 병합·경계값. GameTest: 실제 배치·전투·업그레이드·라운드/경기 정리. 예시는 `OceanTowerRuntimeTest`, `PlantTowerIntegrationTest`입니다.
- `GameTestParticipantFixture`, `AugmentCombatFixture`, `AugmentControllerFixture`: 필요한 준비 책임을 공유합니다. 구체 테스트만 `src/gametest/resources/fabric.mod.json`에 등록하며 공통 빌더 간 계약은 공통 패키지에 남깁니다.
- `ConfigPublishedPatchContractTest`, `WebCatalogExporterTest`, `TowerVfxGameTest`: 패키지 기본값과 실제 로딩, 공개 소유권·설명, VFX 연결을 검증합니다. 기존 사용자 오버라이드가 유지되어야 합니다.

구현 변경의 최종 검증 명령은 다음과 같습니다.

```text
./gradlew test runGameTest remapJar --console=plain --no-daemon
git diff --check
```

Windows에서는 `.\gradlew.bat`를 사용합니다. 저장소의 `runGameTest`는 격리 디렉터리에 새 월드를 생성하며 필요한 패치 의존성을 갖춰야 합니다. `remapJar`는 현재 26.3 빌드에서 배포 산출물을 확인하는 호환 태스크이므로 예전 remapping 설정을 가져오지 않습니다.

리팩토링의 책임 분리와 알고리즘 개선은 별도로 검증합니다. 입력 규모·호출 빈도·색인 갱신·할당 비용을 포함한 동일 조건 측정 없이 성능 향상을 단정하지 않습니다. 서버 GameTest는 실제 클라이언트의 폰트·VFX·모델 렌더링이나 다중 접속 성능 검증을 대신하지 않습니다. 운영 시작·배포·푸시는 별도 승인 범위입니다.


엔티티를 생성하는 테스트는 최종 방어 대상과 숨김 스킬 운반체까지 포함하는 구조 크기를 선언해야 합니다. Body·Demon Lord의 동기 전투 검사는 `combat_arena`와 `RuntimeArenaFixture`를 사용합니다. 청크의 `ENTITY_TICKING` 표시만으로 준비됐다고 판단하지 않고 `areEntitiesActuallyLoadedAndTicking`으로 엔티티 저장소 준비를 확인하며, 기존 테스트 제한 시간은 유지합니다. 지연 검증의 실패는 원인을 보존한 `GameTestAssertException`으로 전달하여 해당 테스트가 실패하게 합니다. 모의 접속의 로그인 단계에서 빠진 Fabric 서버·레지스트리·프로필 정보는 테스트 전용 `RuntimeEnvironmentFixture`가 준비하며 `RuntimePacketContextTest`가 블록 엔티티 청크 패킷 생성 경로를 검증합니다.
