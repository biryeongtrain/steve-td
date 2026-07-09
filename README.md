# Semion TD

Semion TD는 Minecraft `1.21.8` Fabric 서버에서 실행하는 서버 전용 타워 디펜스 미니게임입니다. 플레이어는 빌더를 고르고, 라인에 타워를 배치하며, 웨이브와 인컴 유닛을 버팁니다.

## 요구 환경

기준 버전은 `gradle.properties`와 `fabric.mod.json`입니다.

- Minecraft `1.21.8`
- Fabric Loader `0.19.2` 이상
- Fabric API
- Polymer: `polymer-core`, `polymer-networking`, `polymer-resource-pack`, `polymer-virtual-entity`
- BIL, Placeholder API, Sidebar API, FactoryTools
- Friends & Foes, Flowery Mooblooms, 각 Polymer patch

개발용 빌드는 Gradle Loom을 사용합니다.

```bash
./gradlew build
```

## 빠른 길잡이

- [빌더와 타워](docs/builders-and-towers.ko.md): 현재 등록된 빌더와 계열별 타워 흐름입니다.
- [설정 파일](docs/config-reference.ko.md): `config/semion-td/*.json` 자동 생성 파일과 운영 데이터 구분입니다.
- [타워 수치 설정](docs/tower-balance-reference.ko.md): `tower_balance.json`의 공통 수치, 업그레이드 가격, 고유 능력값입니다.
- [명령어](docs/command-reference.ko.md): 플레이어용, 관리자용, 빌드 기록용, 내부/디버그용 명령어입니다.
- [서비스 준비 체크리스트](docs/service-readiness-checklist.ko.md): 서버 오픈 전 확인 항목입니다.
- [프로덕션 타워 카탈로그](docs/production-tower-catalog.ko.md): 새 타워를 코드에 등록할 때 보는 개발 문서입니다.

## 운영 흐름

관리자가 먼저 볼 명령어는 다음 순서입니다.

1. `/semiontd create`: 로비와 아레나를 생성합니다.
2. `/semiontd ready`: 플레이어가 참가 준비를 표시합니다. 한국어 alias는 `/준비`입니다.
3. `/semiontd start`: 준비된 플레이어로 게임을 시작합니다.
4. `/semiontd reset`: 진행 중인 게임을 리셋하고 로비로 돌립니다.
5. `/semiontd reload`: 설정 파일과 타워 카탈로그를 다시 불러옵니다.
6. `/semiontd rating softreset`: ELO 데이터를 백업한 뒤 소프트 리셋합니다. 같은 관리자가 30초 안에 두 번 입력해야 실행됩니다.

## 설정 위치

서버 실행 후 설정 파일은 `config/semion-td/` 아래에 생성됩니다. `economy.json`, `wave.json`, `map.json`, `progression.json`, `rating.json`, `persistence.json`, `tower_balance.json`, `summons.json`, `leader_targeting.json`, `income_lane_routing.json`, `monster_scaling.json`이 코드 기준 설정 파일입니다.

`profiles.json`, `build_guides.json`, `semiontd.db`는 운영 데이터입니다. 직접 수정하기보다 명령어와 서버 백업으로 관리합니다.

## 문서 기준

이 README는 현재 코드 기준으로 작성했습니다. 실행 예시는 `run/config/semion-td/`의 생성 파일을 참고할 수 있지만, 필드와 기본값 판단은 `SemionConfigLoader`, config record, command registration, job/tower catalog를 기준으로 합니다.
