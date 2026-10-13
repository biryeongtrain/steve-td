# 서버 유지보수 인수인계

이 문서는 Minecraft 26.3 이식의 빌드·검증 및 명시적으로 승인된 배포의 인수인계 절차를 다룬다. 공유 소스는 `C:\steve-td`, 운영 설치는 `C:\SemionTD`다. 빌드 완료와 운영 배포·기동 승인은 별개이며 운영 월드를 개발 테스트에 사용하지 않는다.

## Builder performance verification

Resolve the actual Git source root and verify the operational installation and configuration separately. Folder names and legacy startup examples are not authorization to run an operating server. Read `reference_order.md` first when it is available; if it is absent, state that limitation and use the existing performance and builder maintenance documents. Record when a benchmark uses bundled defaults because an active server configuration is unavailable.

Use [the performance report](performance-optimization-report.ko.md) for the match-load measurement, its control server and limitations. `GameMatchPerformanceTest` runs only with `-Psemiontd.matchProfile=true`; the report lists its options and the Spark JAR property. Logs, profiles and generated worlds stay under ignored build paths. Running it does not authorize starting the operational installation.

The lane tick snapshot caches membership in a private array only. Never expose or mutate that array; replace it after invalidation. Invalidate it immediately after add, replace, successful removal and clear, before the callbacks that follow those mutations. Preserve the outer iteration snapshot and check identity membership before invoking each tower. Do not reuse combat calculations across intervening movement, deaths, healing, upgrades or callbacks.

Distinguish a local validation JAR containing owned private resources from a public release artifact. Check local private entries against their source bytes and preserve them; do not publish them or force-add them to source control. Comparison checkouts exclude private assets, so their setup/rendering costs do not represent the licensed models in a local installation.

Bounded selection must preserve encounter ties and duplicate slots, the original comparator and the complete selected list before damage callbacks. The common area API retains encounter order when candidates do not exceed its cap. Deterministic fixture inputs must include fixed world coordinates and explicit Queen cards; do not strip tooltip fields or weaken result comparisons to obtain a percentage. The opt-in performance GameTest returning success in a normal release gate does not constitute a performance measurement.

The GameTest-only `RuntimeNetworkConnectionMixin` hands background sends for embedded play connections to their GameTest server thread before Netty accesses its outbound buffers. Keep real socket/client channels outside this hook. Preserve every packet, its flush flag and completion listener; do not replace packet capture with a discard sink or disable HUD updates. `RuntimeNetworkThreadingTest` checks deferred delivery, packet order, listener completion and immediate server-thread delivery. The test mixin and its configuration must remain absent from the production JAR.

## 운영 기준

| 항목 | 현재 값 |
|---|---|
| 저장소 | `steve-td` |
| 기본 브랜치 | `master` |
| Minecraft | `26.3` |
| Fabric Loader | `0.19.5` |
| Java target | `25` |
| 현재 검증 JDK | `25.0.4.101` |
| 빌드 JAR | `build/libs/semion-td-1.0-SNAPSHOT+26.3.jar` |
| 서버 루트 | `C:\SemionTD` |
| 배포 JAR | `C:\SemionTD/mods/semion-td-1.0-SNAPSHOT+26.3.jar` |
| 운영 설정·데이터 | `C:\SemionTD/config/semion-td/` |

버전 판단에는 `gradle.properties`와 `src/main/resources/fabric.mod.json`을 사용한다. 명령어는 `SemionCommands`, 설정 기본값은 `SemionConfigLoader`와 각 config record를 기준으로 확인한다.

## 새 작업 환경 준비

1. Java 25를 설치한다.
2. 저장소를 clone하고 `master`를 checkout한다.
3. 소유자에게 비공개 리소스 원본을 받아 `src/main/resources/assets/semion-td/`에 배치한다.
4. `./gradlew compileJava`로 의존성과 toolchain을 확인한다.

### 비공개 리소스 규칙

`src/main/resources/assets/semion-td/`의 모델, 텍스처, 애니메이션은 별도 구매한 리소스다. `.gitignore`가 이 경로를 제외한다.

```bash
git check-ignore -v src/main/resources/assets/semion-td/textures/item/cosmetics/rabbit_body.png
git status --ignored --short src/main/resources/assets/semion-td
```

두 번째 명령은 디렉터리를 `!!`로 표시해야 한다. `git add -f`를 사용하지 않는다. 공개 GitHub Release나 공개 CI artifact에도 리소스가 포함된 JAR을 올리지 않는다. 새 담당자는 소유자가 관리하는 비공개 전달 경로로 원본을 받는다.

## 배포 전 검증

```bash
./gradlew test runGameTest remapJar
```

완료 기준:

- 단위 테스트가 통과한다.
- 등록된 모든 필수 Fabric GameTest가 통과한다. 실패가 남으면 배포 검증 완료로 처리하지 않는다.
- `build/libs/semion-td-1.0-SNAPSHOT+26.3.jar`가 생성된다.
- `git diff --check`가 오류를 출력하지 않는다.
- `git status --short`에 `src/main/resources/assets/semion-td/`가 나타나지 않는다.

테스트 수·결과·산출물 SHA-256은 해당 실행의 보고서에 보관한다. 26.3은 난독화 해제된 배포 구조를 사용하므로 `remapJar`는 배포 JAR을 생성하는 `jar` 태스크에 연결된 호환 진입점이다.

리소스팩은 원본 ZIP을 보존한 별도 복사본에서 필요한 자산을 복구하고, 현재 Polymer/danta 생성 결과와 병합한다. 현재 셰이더를 오래된 원본 셰이더로 덮어쓰지 않는다. 스카이박스는 알파 252 마커 픽셀만 조명·안개를 우회하며, 26.3의 `mix(overlay.rgb, color.rgb, overlay.a)`에서 원래 색을 유지하려면 중립 오버레이 알파가 1이어야 한다. 일반 아이템·OIT 분기는 유지한다. 실제 전달 팩의 SHA-1과 클라이언트 다운로드 캐시를 대조하고 GUI scale 2에서 하늘·HUD·폰트·반투명 표시를 확인한다.

운영 모드를 정리하기 전 직접·전이 의존성, 설정과 저장된 레지스트리 ID를 확인한다. Nightlights, PolyFactory, PolyDecorations처럼 저장 콘텐츠에 사용되는 모드는 호환 버전 또는 검증된 이식본 없이 제거하지 않는다. 선택 모드는 삭제 대신 해시와 복원 경로가 기록된 별도 디렉터리로 옮긴다.

## 운영 서버 배포

배포와 운영 서버 시작·재시작은 각각 사용자의 명시적 지시가 필요하다. 다음 절차는 자체적으로 실행 권한을 부여하지 않는다.

1. 승인된 유지보수 범위와 서버 종료 상태를 확인한다.
2. 기존 런처, 모드, 리소스팩, 설정, 월드, 데이터베이스와 WAL/SHM을 보존하고 파일별 SHA-256으로 백업을 검증한다.
3. 전체 검증을 통과한 정확한 JAR·의존성·리소스팩 목록을 적용하고 원본과 대상 해시를 비교한다.
4. 기동 지시가 없으면 파일 적용 결과와 복원 경로를 전달하고 서버를 시작하지 않는다.

런처만 새 버전으로 교체해도 이전 모드가 호환되는 것은 아니다. 필수 콘텐츠나 전체 검증이 미완료인 설치는 시작 가능한 배포로 취급하지 않는다.

## 기동 확인

격리된 새 월드에서 수행한다. 운영 환경에서는 별도 기동 승인 후에만 수행하며 기존 운영 월드를 변환하는 테스트로 대체하지 않는다.

로그에서 다음 항목을 확인한다.

- `Semion TD initialized.`
- Polymer resource pack 생성 성공
- mixin 적용 실패, registry sync 실패, config parse 실패가 없음
- 로비 로드 완료

콘솔 smoke:

```text
semiontd status
semiontd create
semiontd status
semiontd status teams
semiontd reset
semiontd status
```

마지막 상태는 `activeGame=false`, `arenaLoaded=false`여야 한다. 전체 경기 smoke는 [Carpet QA Runbook](carpet-qa-runbook.ko.md)을 따른다.

## 롤백

1. 승인된 작업 범위 안에서 종료 상태를 확인한다.
2. 적용한 JAR을 별도 보관한다.
3. 기록된 백업 경로에서 이전 런처와 모드 세트를 복원하고 해시를 확인한다.
4. 설정이나 데이터 복원은 변경 범위와 백업 시점을 검토하여 별도 승인 범위에서 수행한다.
5. 기동 승인이 있는 경우에만 서버를 시작하고 검증한다.

JAR을 교체한 뒤 실행 중인 Java 프로세스가 자동으로 새 클래스를 읽지는 않는다. 로그 스택트레이스의 줄 번호가 현재 소스와 다르면 서버 시작 시각, JAR 수정 시각, SHA-256을 먼저 비교한다.

## 설정과 운영 데이터

서버를 끈 상태에서 다음 파일을 함께 백업한다.

- `profiles.json`, `cosmetics.json`, `build_guides.json`
- `semiontd.db`, `semiontd.db-wal`, `semiontd.db-shm`
- `job-statistics.db`, `job-statistics.db-wal`, `job-statistics.db-shm`
- `skyboxes/`, `music/`
- 운영자가 수정한 `*.json` 설정

적용 명령:

| 변경 | 적용 방법 |
|---|---|
| 경제, 웨이브, 타워 밸런스 등 일반 설정 | `/semiontd reload` |
| `cosmetics.json` | `/semiontd cosmetic reload` |
| `skyboxes/`, `music/` | `/semiontd resourcepack reload` |
| 맵 설정 | reset 후 다음 create |
| mod 내장 모델·텍스처와 Java 코드 | 새 JAR 배포 후 서버 재시작 |

운영 데이터 형식과 각 설정 필드는 [설정 파일](config-reference.ko.md)을 본다.

## 밸런스 패치 알림

서버는 공개 저장소 `Kyarurung/semiontd-balance`의 `main` 최신 커밋을 5분마다 확인한다. 처음 확인할 때는 `balance_notification_state.json`에 현재 SHA만 저장하고 알림하지 않는다. 이후 새 커밋이 확인되면 그 시점에 접속 중인 플레이어에게 커밋 제목, 변경사항 최대 3개, GitHub 전체 내역 링크를 표시한다.

조회 실패나 잘못된 응답에서는 마지막 SHA를 바꾸지 않고 다음 주기에 다시 시도한다. 이 기능은 밸런스 파일을 내려받거나 `/semiontd reload`를 실행하지 않으므로, 실제 설정 반영은 기존 운영 절차대로 별도로 수행한다. Webhook 포트나 GitHub 인증 정보는 필요하지 않다.

## 현재 치장 시스템

- 서버는 Polymer 치장 아이템 123개를 등록한다. 머리 치장 121개와 왼손 `leiheng_blade`, `rabbit_body` 2개다.
- `cosmetics.json`은 `head`, `offhand` 슬롯을 저장한다. `slot`이 없는 기존 항목은 `head`로 읽는다.
- `/semiontd cosmetic add <id> <price> [head|offhand]`로 등록한다. 슬롯을 생략하면 `head`다.
- `/semiontd cosmetic update <id> <price> [head|offhand]`에서 슬롯을 생략하면 기존 슬롯을 유지한다.
- 플레이어는 머리와 왼손 치장을 함께 선택할 수 있다. 같은 슬롯에는 한 상품만 남는다.
- `profiles.json`의 현재 선택 필드는 `selectedCosmeticIds`다. 예전 `selectedCosmeticId`는 호환용으로 읽는다.
- 왼손 치장은 상점에서 해제한다. 손 교체, 드롭, 인벤토리 이동으로 제거하지 않는다.

2026-07-16 운영 catalog에는 167개 항목이 있었다. 이 숫자는 운영 중 바뀔 수 있으므로 `/semiontd cosmetic list` 또는 `cosmetics.json`의 `entries` 길이로 다시 확인한다.

## 이번 코드 상태의 게임 변경

- 염소 지원 타워는 일반 무리 타워에도 피해 증가와 받는 피해 감소를 함께 부여한다.
- 환영에는 `cloneDamageBonus`, `cloneDamageReduction` 값을 따로 적용한다.
- 염소 버프는 최대 3스택이며 `buffDurationTicks` 동안 유지되고 pulse마다 갱신된다.
- 오른쪽 클릭 타워 상세에 피해 증가와 받는 피해 감소가 표시된다.

수치는 `tower_balance.json`의 염소 ability 항목과 [타워 수치 설정](tower-balance-reference.ko.md)을 함께 확인한다.

## 장애 확인 순서

1. `semiontd status`, `status teams`, `status lanes`, `status players`를 저장한다.
2. `logs/latest.log`에서 첫 예외를 찾는다.
3. 배포 JAR과 빌드 JAR의 SHA-256을 비교한다.
4. config parse 오류면 실패한 파일을 백업본과 비교한다.
5. 새 JAR에서만 재현되면 이전 JAR로 롤백한다.
6. 서버 상태가 꼬였지만 프로세스는 정상이라면 `semiontd reset` 후 status를 확인한다.

화면, HUD, DialogUtils, 리소스팩, 치장 모델 문제는 서버 로그만으로 닫지 않는다. 실제 클라이언트에서 확인하고 [서비스 준비 체크리스트](service-readiness-checklist.ko.md)에 날짜와 결과를 남긴다.

## 커밋 전 확인

```bash
git status --short
git diff --check
./gradlew test runGameTest remapJar
git status --ignored --short src/main/resources/assets/semion-td
```

문서, 코드, 테스트만 stage한다. 운영 config, DB, 로그, 백업 JAR, 구매 리소스는 commit하지 않는다.

## 호환 모듈과 저장 경로

danta, Friends & Foes, Polymer 패치와 Nightlights는 메인 배포 JAR의 중첩 의존성이다. Friends & Foes와 그 Polymer 패치는 Modrinth 공식 배포본을 받고, danta shader와 Nightlights, Nightlights Polymer 패치는 `libs/`의 로컬 JAR을 받는다(준비 방법은 README의 서버 의존성). 타 모드의 소스는 이 저장소에 두지 않는다. ChatHeads, WorldEdit Hang Fix, HoloDisplays, Sylcurity는 각각 별도 JAR로 설치한다. 로컬 JAR을 다시 만들 때는 각 모듈의 원본 라이선스와 출처 고지를 유지하고, 메인 JAR만 교체하여 외부 모드 세트 검증을 생략하지 않는다.

Avatar 렌더러의 작업 스레드는 종료 이벤트에서 정리한다. 실제 서버가 저장을 끝낸 뒤 JVM까지 정상 종료되는지 확인한다. headless 검증 환경의 SessionService 부재는 ChatHeads 기본 아이콘으로 처리하며 실제 서버의 프로필 조회 동작과 구분한다.

26.3 저장소 이동으로 `playerdata`, `advancements`, `stats`, `player-mod-data`는 `players/data`, `players/advancements`, `players/stats`, `players/mod_data`로 옮겨질 수 있다. 파일 경로가 달라졌다는 이유만으로 삭제로 판단하지 말고 백업과 내용 해시를 대조한다. 월드 팩도 `world/resources.zip`에서 `world/resourcepacks/resources.zip`으로 이동하므로 현재 레이아웃을 확인한다. 실제 클라이언트 전달 파일은 `polymer/resource_pack.zip`이며 업로드 해시와 대조한다. 기존 설정의 자동 스키마 변환은 해당 config loader와 배포 전 소스를 비교한다.

외부 리소스팩 공급자는 생성된 팩의 SHA-1을 URL 파일명에 사용한다. R2 업로더를 별도 실행하는 구성에서는 팩을 새로 생성할 때마다 현재 해시의 파일을 업로드하고 공개 다운로드의 SHA-1·SHA-256을 확인해야 한다. 이전 버전 업로더 JAR이나 이전 팩 URL을 그대로 활성화하지 않는다. 자격 증명은 기존 비공개 운영 설정에서만 읽고 로그·소스에 넣지 않는다.

## 빌더 공통 경로와 책임 분리 검증

`GameLaneWaveOrder`는 자연 웨이브의 순서·힐러 배치·보상 분배를 계산하고, `PlayerLane`은 큐·엔티티·레인 수명주기를 연결한다. `UiPlayerAvatarService`는 비동기 스킨 조회·캐시·아바타 이미지를 맡으며 대화상자 서비스는 배치와 문구를 유지한다. 공백이 있는 샌드박스 이름 등 Minecraft 프로필 이름이 아닌 값은 원격 요청 없이 기본 아바타를 사용한다.

레인 틱은 기존 identity 멤버십 인덱스를 사용한다. 콜백 전에 스냅숏을 만들므로 앞선 콜백에서 삭제된 타워는 실행하지 않고, 추가·교체된 타워는 다음 틱부터 실행한다. 타워 수 n에 대해 멤버십 조회의 합계가 O(n²)에서 평균 O(n)으로 줄며 스냅숏 할당 O(n)은 유지된다. 몬스터 FIFO는 ArrayDeque로 보관하여 앞 원소 제거 시 배열 이동을 없앤다. 큐 삽입은 상각 O(1), 드물게 확장 시 O(n), 전체 배출은 O(n)이며 기존 소환 순서·간격·보상을 유지한다.

`TowerBuilderCatalogContractTest`는 모든 등록 빌더의 소유권·팩터리·업그레이드 간선과 원래/현재 좌표·지불 비용 보존을 검사한다. 설계도 빌더는 실제 동적 생성 경로로 등록한다. `SEMIONTD_TEST_TOWER_BALANCE`를 제공하면 해당 복사본을 읽으며, 미지정 시 현재 내장 기본값을 사용한다. 운영자가 오래된 `tower_balance.json`을 삭제한 경우 예전 백업을 현재 설정으로 되살리지 않는다. 기존 로더는 다음 기동/리로드에서 누락 설정을 기본값으로 생성하므로 운영 적용 전에 의도를 확인한다.

운영에만 설치하는 HoloDisplays·Sylcurity·ChatHeads·WorldEdit Hang Fix 전용 회귀 테스트는 SemionTD 기본 테스트 소스와 런타임 의존성에서 분리했다. SemionTD가 사용하는 AvatarRenderer·Nightlights·Friends & Foes·공용 저장/통신/UI 검증은 유지한다. 외부 모드의 배포 검증은 각 모듈 책임으로 별도 수행하며 기본 게이트 통과를 그 모드의 재검증으로 확대 해석하지 않는다.

패치 기준 수치 동기화의 출처와 회귀 계약은 [밸런스 문서](tower-balance-reference.ko.md#공개-패치와-기본-설정의-동기화)를 따른다. 테스트 경기장은 서버 시작 이후 생성되므로 headless 서버의 거리 설정을 새 월드에도 전달한다. 청크의 실제 `ENTITY_TICKING` 준비를 최대 400틱 기다리며, 이동 관측 700틱과 누수 판정은 유지한다.

빌더별 GameTest는 `src/gametest/java/kim/biryeong/semiontd/tower/<builder>/`에 두고 패키지·상위 책임·하위 책임에 맞춘 `…Test` 이름을 사용한다. `SemionParticipantGameTest`, `AugmentCombatGameTest`, `AugmentControllerGameTest`에는 참여자·증강 공통 동작을 남기고 특정 빌더의 전투·증강 선택 검사는 해당 빌더 패키지로 분리했다. `GameTestParticipantFixture`, `AugmentCombatFixture`, `AugmentControllerFixture`는 중복 준비 코드를 공유하며 테스트 진입점에는 등록하지 않는다. 여러 빌더의 타겟 정책을 비교하는 검사는 `tower/TowerBuilderTargetPolicyTest`가 담당한다. 클래스 이동 시 GameTest 소스셋·애너테이션·진입점과 모든 기존 단언을 함께 보존한다.

`SeasonThreeLoadGameTest`의 opt-in 격리 실행은 8개 논리 레인과 주민·동물의 고정 보드 48타워로 R16·19·20·25의 네 웨이브 변형을 관찰한다. 전체 구간과 직전 틱 시작 시 살아 있는 타워·몬스터가 함께 있던 구간의 MSPT 평균·p50·p95·p99·최댓값을 구분한다. 고정 보드가 패배한 뒤에도 남은 몬스터를 관찰하므로 전체 구간을 지속 전투 성능으로 해석하지 않는다. 무제한 속도의 headless 실행에서 틱 수/벽시계 시간은 처리율이며 20 TPS 유지나 실제 클라이언트 부하의 증거가 아니다. 입력 JSON의 기존 해시는 맵 직렬화 순서에 영향받으므로 서로 다른 JVM의 해시만으로 설정 차이를 단정하지 않는다.

GitHub 커밋 기반 밸런스 알림은 현재 공식 저장소 `Kyarurung/semiontd-balance`의 `main`을 조회한다. 첫 조회의 상태 초기화, 변경 시 한 번 알림, 실패 시 이전 상태 보존을 유지한다. 웹사이트의 적용 패치 목록과 GitHub 커밋 이력은 서로 다른 자료이므로 밸런스 수치 동기화는 위의 공식 패치 회귀 계약을 따른다.

## 네임스페이스의 조회·집계 책임

`AugmentCatalogLookup`은 불변 증강 ID·종류·stance 조회를 맡고 기존 `AugmentCatalog` 공개 API를 유지한다. 분리된 카드가 기본 카드보다 먼저 매칭되는 순서, 레거시 ID, 선택 목록 순서와 난수 호출은 변경하지 않는다. 평균 O(1) 조회를 위해 초기화 시 O(C) 인덱스 메모리를 사용하며 카탈로그가 가변으로 바뀌면 재구성 수명을 함께 설계해야 한다.

`GameRoundMetricsSnapshot`은 라운드 결과 구성, `GameRoundSupportMetrics`는 구매자별 지원 통계 집계를 맡는다. 참가자마다 전체 몬스터를 다시 순회하던 경로를 한 번 집계하되 레인과 몬스터의 덧셈 순서를 보존한다. 이는 라운드 종료 경로이며 매틱 비용 감소로 설명하지 않는다. `GameSpectatorPlacementOrder`는 전체 정렬 대신 UUID 자연순위보다 앞선 원소 수를 세고 매 호출 현재 관전자 집합을 읽는다.

HUD는 갱신 호출마다 `UiLobbyRosterSummary`를 한 번 생성하고, `UiHudDamageRanking`이 피해 집계와 TOP5 선택을 담당한다. 접속·준비 상태를 장기 캐시하지 않으며 플레이어별 본문과 스타일은 유지한다. `CommandGameStatus`는 명령 상태 출력만 담당하고 명령 등록·권한은 기존 진입점에 남긴다.

`ConfigJsonProperties`는 한 로드 호출 안에서 JSON 속성 존재 여부를 확인한다. 다른 리로드까지 파싱 트리를 보관하지 않는다. `PersistenceJobStatisticsSchema`는 연결마다 테이블 컬럼을 조회하므로 외부 DB 변경을 다음 연결에서 관측한다. `WebCatalogBuilderIndex`는 검증된 소유권의 역방향 목록만 구성하며 임의 소유권 술어의 전체 검증을 생략하지 않는다. `RatingLeaderboardSelection`은 큰 입력의 제한 순위를 선택하고 동점의 입력 순서를 보존한다. SQL 조회·역직렬화는 여전히 전체 프로필에 비례한다.

`EntityGoalTargetSelection`은 지원 능력의 제한된 대상 집합을 정렬 순서와 같은 결과로 선택한다. 모든 선택은 효과 적용 전에 끝내며 동점, 현재 체력·거리, 전체 후보 수 통계와 자신 대상 fallback을 보존한다. `VfxBuilderPaletteSelection`은 빌더 팔레트 분류만 분리한다. 렌더링·패킷·예산·타이밍은 기존 VFX 서비스의 책임이다.

`BalanceReceiptLedger`는 이력 순서와 예약 건수를 유지한다. 예약이 없는 일반 틱은 O(1) 확인으로 전체 이력 복사를 건너뛰지만 공지, runtime 상태와 쓰기 차단 확인은 계속 수행한다. 예약이 있으면 O(H) 이력 순회가 남는다. 모든 교체는 기존 서비스 잠금 안에서 ledger를 거쳐야 하며 저장 형식·서명된 패치·적용 경계는 바꾸지 않는다.

책임 분리만 한 변경은 성능 개선으로 계산하지 않는다. 작은 입력의 힙·인덱스 초기화 비용, 할당·GC, 캐시 무효화와 동점 순서까지 확인하고 원본/책임 분리/알고리즘 개선을 동일 입력으로 비교한다. 계산 경로의 미시 측정과 격리 GameTest 통과는 실제 클라이언트·운영 경기의 MSPT/TPS 보장이 아니다. 빌더별 구성은 [빌더 가이드](builders-and-towers.ko.md)와 현재 Controller/State/Snapshot/StatsView 코드에 따른다.

`MusicPlaybackSchedule`은 재생 시간표와 난수 선택 수명을 담당한다. 이미 생성된 시간 구간은 이진 탐색하고 한 틱의 온라인 대상 플레이어는 같은 결과를 공유한다. 과거 구간 조회를 위해 이력은 남기므로 저장 공간 O(S)와 새 구간 생성 비용은 줄지 않는다. 모든 대상이 오프라인인 경우 시간표를 미리 생성하거나 난수를 추가 소비하지 않는다.

`EngineerTrapSignalController`는 변경된 회로 상태를 호출마다 받아 목표에서 역방향 BFS를 한 번 수행한다. 반복기의 방향, 눌림 시각·거리·좌표 우선순위는 기존 발판별 탐색과 같아야 한다. `ResonanceTowerLinkController`는 기존 순차 링크·오라 갱신을 유지하며 친구 한 명의 선택만 정렬 대신 최솟값 탐색을 한다. 전체 공명 링크 그래프가 선형으로 바뀐 것은 아니다.

`TraitRoundTowerIndex`는 웨이브 시작 특성 적용 안에서 살아 있는 타워를 소유자·종류별로 한 번 집계한다. 특성을 받지 못하는 복제·엔티티 없는 타워도 기존 집계 규칙에 포함하고, 실제 효과를 받는 타워의 필터는 별도로 유지한다. 조회 때 현재 특성 설정과 원래 곱셈 순서를 사용하며 다음 웨이브에는 새 집계를 만든다. 일반적인 해시 조건에서 반복 전체 순회 O(T²)를 O(T+R)로 줄이고 고유 소유자·종류 조합 O(U) 임시 메모리를 쓴다.

`SemionJob`의 선택·경기 시작·라운드 시작/종료·탈락·경기 종료·소환·처치 진입점은 기본이 빈 메서드다. 고유 동작이 있는 빌더는 자신의 `*TowerJob`에서 필요한 훅만 override하며, 메타데이터·권한·경제 정책도 같은 클래스에 둔다. 별도의 Lifecycle 클래스나 ID별 등록 표는 두지 않는다.

`PlayerLane`의 `resetForRound`·`markWaveStarted`·`clearTowers`는 기존 준비 초기화·웨이브 시작·타워 제거 경계를 보존한다. 임시 복제 제거, 개별 타워 콜백, 특성 적용 사이의 호출을 합치거나 순서를 바꾸지 않는다. 경기 종료는 직업 종료 훅, 온라인/오프라인 플레이어 정리, 레인 제거 전 정리, 레인 제거, 제거 후 정리 순서다. 모든 참가자 UUID의 교차 계열 상태도 정리하며 Adversary 원장은 타워 제거 뒤에 비운다. 재접속은 기존 상태를 복원하고 시작 훅을 다시 실행하지 않는다.

`SemionGameManager.handlePlayerJoin`의 지연 작업은 현재 UUID에 등록된 플레이어 객체와 열린 연결을 확인한다. 이미 끊기거나 새 접속으로 대체된 객체에는 로비 이동을 적용하지 않는다. 서버 서비스 등록/종료, 지속 전투 틱, 타워별 제거·사망, 경제·입력 콜백은 각 책임을 유지한다.
