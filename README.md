# Semion TD

Semion TD는 Minecraft `26.3` Fabric 서버에서 실행하는 서버 전용 타워 디펜스 미니게임입니다. 플레이어는 빌더를 고르고, 라인에 타워를 배치하며, 웨이브와 인컴 유닛을 버팁니다.

## 요구 환경

버전의 기준은 [gradle.properties](gradle.properties), [build.gradle](build.gradle), [fabric.mod.json](src/main/resources/fabric.mod.json)입니다.

| 항목 | 현재 빌드 기준 |
|---|---|
| Minecraft | `26.3` |
| Java | `25` (컴파일 대상과 서버 최소 요구 버전) |
| Fabric Loader | `0.19.5` (배포 메타데이터 최소 `0.19.5`) |
| Fabric API | `0.161.0+26.3` |
| Polymer | `0.18.2+26.3` |
| Gradle / Loom | Wrapper `9.6.0` / `net.fabricmc.fabric-loom 1.17.21` |

Fabric Loader와 Fabric API는 서로 다른 의존성입니다. Minecraft 26.3의 공식 이름을 사용하는 non-remapping 빌드이며, 이전 버전의 Yarn/mappings 설정을 복사하지 않습니다.

### 서버 의존성

필수 모드 ID와 최소 버전은 `fabric.mod.json`, 배포 JAR에 포함되는 라이브러리는 `build.gradle`의 `include(...)`로 구분합니다.

- 별도로 준비할 런타임 의존성은 Fabric API, Polymer 모듈, FactoryTools, Flowery Mooblooms와 이들의 전이 의존성입니다. 현재 빌드는 Resourceful Lib과 YACL도 사용합니다. 실제 서버에서는 각 모드의 메타데이터도 함께 검사합니다.
- SGUI, BIL, Fabric Permissions API, Placeholder API, Sidebar API 등 `include(...)` 의존성은 빌드된 JAR에 중첩됩니다. 최종 JAR의 메타데이터와 중첩 파일을 확인하고 같은 모드를 다른 버전으로 중복 설치하지 않습니다.
- Friends & Foes와 Polymer patch는 Modrinth 공식 배포본을 의존성으로 받아 함께 포함합니다(`gradle.properties`의 `friends_and_foes_version`, `friends_and_foes_polymer_version`). Flowery Mooblooms는 현재 선언된 배포본과 Friends Polymer patch의 조합을 사용합니다. 파일명에 다른 Minecraft 버전이 남아 있다는 이유만으로 임의 교체하지 않습니다.
- danta shader, Nightlights, Nightlights Polymer patch는 Minecraft 26.3용 배포본이 없어 로컬 JAR로 받아 함께 포함합니다. 빌드 전에 `libs/`에 `danta-shader-1.0.2+26.3-local.jar`, `nightlights-fabric-1.3.0+26.3-local.jar`, `night-lights-patch-1.3.0+26.3-local.jar`를 둡니다. 버전은 `gradle.properties`의 `danta_shader_version`, `nightlights_version`, `nightlights_patch_version`과 같아야 합니다. `libs/`는 저장소에 올리지 않으며, 세 JAR이 없으면 빌드는 의존성을 찾지 못해 실패합니다.
- 타 모드의 소스는 이 저장소에 두지 않습니다. ChatHeads, WorldEdit Hang Fix, HoloDisplays, Sylcurity는 Semion TD 빌드와 기본 GameTest에 무관한 운영 기능이며, 메인 JAR의 중첩 모드로 취급하지 않고 각각 별도 JAR로 설치합니다.

의존성 다운로드에는 해당 Maven/배포 서비스에 대한 네트워크 접근이 필요합니다. 비공개 모델·텍스처의 준비 조건은 아래 [비공개 리소스](#비공개-리소스)를 확인합니다.

## 빌드와 격리 검증

저장소의 `AGENTS.md`와 관련 빌더/모델 제작 스킬을 먼저 읽습니다. JDK 25를 선택하고 저장소의 Gradle Wrapper로 전체 검증을 실행합니다.

```bash
./gradlew test runGameTest remapJar
```

이 명령은 JUnit·서버 GameTest·배포 JAR을 검증합니다. 변경 중에는 해당 책임의 테스트를 함께 갱신합니다.

현재 배포 산출물은 `build/libs/semion-td-1.0-SNAPSHOT+26.3.jar`입니다. `remapJar`는 문서화된 명령을 유지하기 위한 호환 태스크로, 26.3에서는 중첩 의존성을 포함하는 일반 `jar` 태스크에 의존합니다. 예전 난독화 매핑을 다시 적용하지 않습니다.

`runGameTest`는 실행마다 `build/run/gameTest-26.3-<UUID>`에 새 테스트 월드를 만들며 기존 월드 재사용을 거부합니다. 실제 경로는 `build/26.3-gametest-run-dir.txt`에 기록됩니다. 테스트 의존성은 현재 Gradle 모듈 그래프로 구성되므로 운영 서버의 `mods`나 월드를 복사해 넣지 않습니다. 기존 테스트 설정의 EULA 동의 전제가 적용되며, 새로운 환경에서 필요한 명시적 동의 없이 실행하지 않습니다.

수동 `runServer`는 별도 개발용 `run/` 환경에만 사용합니다. 운영 서버 경로를 연결하거나 기존 운영 월드를 변환하지 않습니다. 자동 GameTest는 실제 GPU의 모델·폰트·VFX 표시나 다중 접속 성능을 보증하지 않으며, 선택 실행 성능 테스트의 `NOT_RUN`도 측정 완료로 세지 않습니다.

## 프로젝트 구조

| 경로 | 책임 |
|---|---|
| `src/main/java/kim/biryeong/semiontd/game` | 경기·레인·라운드·경제 진행 |
| `src/main/java/kim/biryeong/semiontd/tower`, `job`, `augment` | 빌더·타워 카탈로그·전투·증강 |
| `src/main/java/kim/biryeong/semiontd/config`, `persistence`, `ui`, `web` | 설정·저장·표시·외부 카탈로그 |
| `src/main/resources` | 모드 메타데이터·맵·패키지 기본값·리소스 |
| `src/test/java` | 책임 패키지별 JUnit |
| `src/gametest/java`, `src/gametest/resources/fabric.mod.json` | 서버 GameTest와 구체 테스트 클래스 등록 |
| `libs/` | 26.3 배포본이 없는 모드의 로컬 JAR(저장소 제외) |
| `docs/`, `.agents/skills/` | 유지 문서와 작업별 제작 지침 |

게임 구현과 테스트는 같은 책임 경계로 구성합니다. 빌더별 순수 계산과 상태는 JUnit, 실제 엔티티·전투·업그레이드·정리는 GameTest로 확인합니다. 흑마법사·엔드의 책임 분리 방식과 공통 fixture 사용법은 [제작 가이드](docs/production-tower-catalog.ko.md)를 따릅니다. `build/`, `run/`, `logs/`는 생성 상태이며 소스 커밋 대상이 아닙니다.

## 빠른 길잡이

- [서버 유지보수 인수인계](docs/next-session-handoff.ko.md): 빌드, 배포, 백업, 복구, 장애 확인 순서입니다.
- [서비스 준비 체크리스트](docs/service-readiness-checklist.ko.md): 운영 전 서버 및 실클라이언트 확인 항목입니다.
- [빌더와 타워](docs/builders-and-towers.ko.md): 현재 등록된 빌더와 계열별 타워 흐름입니다.
- [겜블 빌더](docs/gamble-builder.ko.md): 주사위 지원, 고정 수치 도박, 고유 능력 규칙입니다.
- [설정 파일](docs/config-reference.ko.md): `config/semion-td/*.json` 자동 생성 파일과 운영 데이터 구분입니다.
- [타워 수치 설정](docs/tower-balance-reference.ko.md): `tower_balance.json`의 공통 수치, 업그레이드 가격, 고유 능력값입니다.
- [명령어](docs/command-reference.ko.md): 플레이어용, 관리자용, 빌드 기록용, 내부/디버그용 명령어입니다.
- [직업 통계](docs/job-statistics.ko.md): 직업별 선택률, 승률, 1~40라운드 통과율, 전투·인컴·라인 지표와 SQLite 조회 기준입니다.
- [프로덕션 타워 카탈로그](docs/production-tower-catalog.ko.md): 새 타워를 코드에 등록할 때 보는 개발 문서입니다.
- [범위 효과 API](docs/area-effect-api.ko.md): 애드온에서 Semion 몬스터·타워 대상 범위 효과와 VFX 스타일을 추가하는 방법입니다.

## 운영 흐름

관리자가 먼저 볼 명령어는 다음 순서입니다.

1. `/semiontd create`: 로비와 아레나를 생성합니다.
2. `/semiontd ready`: 플레이어가 참가 준비를 표시합니다. 한국어 alias는 `/준비`입니다.
3. `/semiontd start`: 준비된 플레이어로 게임을 시작합니다.
4. `/semiontd reset`: 진행 중인 게임을 리셋하고 로비로 돌립니다.
5. `/semiontd reload`: 설정 파일과 타워 카탈로그를 다시 불러옵니다.
6. `/semiontd rating softreset`: ELO 데이터를 백업한 뒤 소프트 리셋합니다. 같은 관리자가 30초 안에 두 번 입력해야 실행됩니다.

## 설정 위치

서버 실행 후 설정 파일은 `config/semion-td/` 아래에 생성됩니다. `economy.json`, `wave.json`, `map.json`, `progression.json`, `rating.json`, `persistence.json`, `tower_balance.json`, `summons.json`, `leader_targeting.json`, `income_lane_routing.json`, `monster_scaling.json`, `vfx.json`, `tips.json`이 코드 기준 설정 파일입니다. 개인 스카이박스 PNG는 `config/semion-td/skyboxes/`에 추가합니다.

`cosmetics.json`, `profiles.json`, `build_guides.json`, `semiontd.db`, `job-statistics.db`는 운영 데이터입니다. 치장 상품은 주 손 아이템을 든 뒤 `/semiontd cosmetic add <id> <price> [head|offhand]`로 등록합니다. 슬롯을 생략하면 `head`를 사용합니다. 운영 데이터는 직접 수정하기보다 명령어와 서버 백업으로 관리합니다.

## 운영 적용 범위

개발 소스와 운영 설치는 별도 디렉터리입니다. 현재 Windows 작업 환경에서는 소스가 `C:\steve-td`, 운영 설치가 `C:\SemionTD`이며, 다른 환경에서는 실제 경로를 확인합니다. 빌드 성공이나 로컬 커밋은 운영 배포 완료를 뜻하지 않습니다.

운영 파일 교체·서버 시작/종료·리소스팩 외부 업로드·원격 푸시는 별도 지시에 따릅니다. 승인된 운영 적용 시에는 기존 설정·DB·월드·JAR의 백업과 롤백을 먼저 준비하고 [서버 유지보수 인수인계](docs/next-session-handoff.ko.md)의 절차를 확인합니다. 사용자 삭제 설정이나 기존 월드를 개발 검증을 위해 복원·변환하지 않습니다.

## 비공개 리소스

`src/main/resources/assets/semion-td/`에는 별도 구매한 모델과 텍스처가 들어가며 Git에서 제외합니다. 새 작업 환경에는 소유자가 제공한 원본을 같은 경로에 따로 배치해야 합니다. 이 디렉터리를 강제 추가하거나 공개 저장소, CI artifact, 릴리스 첨부 파일에 올리지 않습니다.

## 문서 기준

이 README는 현재 코드 기준으로 작성했습니다. 실행 예시는 `run/config/semion-td/`의 생성 파일을 참고할 수 있지만, 필드와 기본값 판단은 `SemionConfigLoader`, config record, `src/main/resources/semiontd/balance-defaults/`, command registration, job/tower catalog를 기준으로 합니다. 패키지 기본값과 운영자가 지정한 현재 값은 구분하며, 공식 패치 동기화의 출처·단위와 기존 오버라이드 보존은 설정 계약 테스트로 검증합니다.
