# 서버 유지보수 인수인계

기준일은 2026-07-16이다. 이 문서는 새 유지보수 담당자가 Semion TD를 빌드하고 운영 서버에 배포한 뒤 장애 시 이전 JAR로 복구하는 절차를 다룬다.

## 운영 기준

| 항목 | 현재 값 |
|---|---|
| 저장소 | `steve-td` |
| 기본 브랜치 | `master` |
| Minecraft | `1.21.8` |
| Fabric Loader | `0.19.2` |
| Java target | `21` |
| 현재 검증 JDK | `25.0.2` |
| 빌드 JAR | `build/libs/semion-td-1.0-SNAPSHOT+1.21.8.jar` |
| 서버 루트 | `~/Desktop/SemionTd` |
| 배포 JAR | `~/Desktop/SemionTd/mods/semion-td-1.0-SNAPSHOT+1.21.8.jar` |
| 운영 설정·데이터 | `~/Desktop/SemionTd/config/semion-td/` |

버전 판단에는 `gradle.properties`와 `src/main/resources/fabric.mod.json`을 사용한다. 명령어는 `SemionCommands`, 설정 기본값은 `SemionConfigLoader`와 각 config record를 기준으로 확인한다.

## 새 작업 환경 준비

1. Java 21 이상을 설치한다.
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
- Fabric GameTest `264`개가 통과한다.
- `build/libs/semion-td-1.0-SNAPSHOT+1.21.8.jar`가 생성된다.
- `git diff --check`가 오류를 출력하지 않는다.
- `git status --short`에 `src/main/resources/assets/semion-td/`가 나타나지 않는다.

GameTest 수가 코드 추가로 바뀌면 실행 결과의 새 숫자를 이 문서와 서비스 준비 체크리스트에 함께 갱신한다.

## 운영 서버 배포

`~/Desktop/SemionTd/start.sh`는 Java 프로세스가 끝나면 5초 뒤 서버를 다시 실행한다. 유지보수 중에는 Java 프로세스만 종료하지 말고 `start.sh`를 실행한 터미널, screen, tmux 세션에서 실행 스크립트까지 중지한다.

1. 접속자에게 점검을 알리고 서버를 정상 종료한다.
2. 실행 스크립트가 서버를 다시 띄우지 않는지 확인한다.
3. 기존 JAR과 `config/semion-td/`를 백업한다.
4. 새 JAR을 mods 디렉터리에 복사한다.
5. 파일 해시를 비교한다.
6. `start.sh`로 서버를 시작한다.

```bash
cd ~/Desktop/SemionTd
mkdir -p backups
cp mods/semion-td-1.0-SNAPSHOT+1.21.8.jar \
  backups/semion-td-1.0-SNAPSHOT+1.21.8.jar.before-update
tar -czf backups/semion-td-config.before-update.tar.gz config/semion-td

cp /path/to/steve-td/build/libs/semion-td-1.0-SNAPSHOT+1.21.8.jar \
  mods/semion-td-1.0-SNAPSHOT+1.21.8.jar

shasum -a 256 \
  /path/to/steve-td/build/libs/semion-td-1.0-SNAPSHOT+1.21.8.jar \
  mods/semion-td-1.0-SNAPSHOT+1.21.8.jar
```

SQLite 파일과 WAL/SHM 파일의 시점을 맞추기 위해 운영 데이터 백업은 서버를 끈 상태에서 만든다.

## 기동 확인

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

1. `start.sh` 실행까지 중지한다.
2. 실패한 JAR을 보관하거나 삭제한다.
3. `backups/`의 이전 JAR을 mods 디렉터리에 복사한다.
4. 설정이나 운영 데이터까지 변경됐다면 같은 배포 전에 만든 config 백업을 복원한다.
5. 서버를 시작하고 기동 확인과 `create`/`reset` smoke를 반복한다.

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

서버는 공개 저장소 `biryeongtrain/semiontd-balance`의 `main` 최신 커밋을 5분마다 확인한다. 처음 확인할 때는 `balance_notification_state.json`에 현재 SHA만 저장하고 알림하지 않는다. 이후 새 커밋이 확인되면 그 시점에 접속 중인 플레이어에게 커밋 제목, 변경사항 최대 3개, GitHub 전체 내역 링크를 표시한다.

조회 실패나 잘못된 응답에서는 마지막 SHA를 바꾸지 않고 다음 주기에 다시 시도한다. 이 기능은 밸런스 파일을 내려받거나 `/semiontd reload`를 실행하지 않으므로, 실제 설정 반영은 기존 운영 절차대로 별도로 수행한다. Webhook 포트나 GitHub 인증 정보는 필요하지 않다.

## 현재 치장 시스템

- 서버는 Polymer 치장 아이템 120개를 등록한다. 머리 치장 119개와 왼손 `rabbit_body` 1개다.
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
