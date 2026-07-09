# 명령어

기본 루트 명령어는 `/semiontd`입니다. 관리자 명령어는 permission level `2`를 요구합니다.

## 플레이어용 명령어

| 명령어 | 설명 |
|---|---|
| `/semiontd ready` | 현재 게임 참가 준비를 표시합니다. |
| `/semiontd unready` | 준비 상태를 해제합니다. |
| `/semiontd spectate` | 관전자로 전환합니다. |
| `/semiontd spectate <team>` | 지정 팀을 관전합니다. |
| `/semiontd economy` | 현재 다이아, 에메랄드, 인컴 상태를 봅니다. |
| `/semiontd money request <amount>` | 같은 팀에게 다이아 지원을 요청합니다. |
| `/semiontd money accept <requestId>` | 팀원의 지원 요청을 수락합니다. |
| `/semiontd profile` | 내 프로필을 봅니다. |
| `/semiontd rating` | 내 ELO 정보를 봅니다. |
| `/semiontd rating top` | ELO 순위표를 봅니다. |
| `/semiontd job list` | 선택 가능한 빌더 목록을 봅니다. |
| `/semiontd job ui` | 빌더 선택 UI를 엽니다. |
| `/semiontd job current` | 현재 선택한 빌더를 봅니다. |
| `/semiontd job select <id>` | 빌더를 선택합니다. |
| `/semiontd trait` | 특성 UI를 엽니다. |
| `/semiontd trait ui [slot]` | 특성 UI를 열거나 특정 slot을 엽니다. |
| `/semiontd trait current` | 현재 특성을 봅니다. |
| `/semiontd trait list` | 선택 가능한 특성을 봅니다. |
| `/semiontd trait select <slot> <id>` | 특성을 선택합니다. |
| `/semiontd tower list` | 현재 빌더가 설치할 수 있는 starter 타워를 봅니다. |
| `/semiontd tower ui` | 타워 UI를 엽니다. |
| `/semiontd tower limitup` | 타워 제한을 구매로 올립니다. |
| `/semiontd tower build <id>` | starter 타워를 설치합니다. |
| `/semiontd tower sell [x y z]` | 바라보는 타워 또는 지정 좌표 타워를 판매합니다. |
| `/semiontd tower test` | 테스트 타워를 설치합니다. 운영 서버에서는 관리자 안내 없이 쓰지 않습니다. |
| `/semiontd tower upgrades` | 선택한 타워의 업그레이드 목록을 봅니다. |
| `/semiontd tower upgrade <id> [x y z]` | 타워를 업그레이드합니다. |
| `/semiontd gasup` | 에메랄드 생산을 업그레이드합니다. |
| `/semiontd emeraldup` | 에메랄드 생산을 업그레이드합니다. |
| `/semiontd summon <id>` | 인컴 유닛을 소환합니다. |
| `/semiontd summons` | 소환 가능한 인컴 유닛 목록을 봅니다. |
| `/semiontd summonui [page]` | 인컴 유닛 UI를 엽니다. |
| `/semiontd leader target <team>` | 선두 팀을 타겟팅합니다. |
| `/semiontd status` | 현재 게임 상태를 봅니다. |
| `/semiontd status teams` | 팀 상태를 봅니다. |
| `/semiontd status lanes` | 라인 상태를 봅니다. |
| `/semiontd status players` | 플레이어 상태를 봅니다. |
| `/semiontd ui` | 상태 UI를 엽니다. |

## 관리자용 명령어

| 명령어 | 설명 |
|---|---|
| `/semiontd create` | 로비와 아레나를 생성합니다. |
| `/semiontd start` | 준비된 플레이어로 게임을 시작합니다. |
| `/semiontd end` | 진행 중인 게임을 강제 종료하고 로비로 돌립니다. |
| `/semiontd reset` | 게임을 리셋하고 로비로 돌립니다. |
| `/semiontd reload` | 설정 파일과 타워 카탈로그를 다시 불러옵니다. |
| `/semiontd testmode <true or false>` | 테스트 모드를 켜거나 끕니다. |
| `/semiontd autojoin` | 다음 시작을 위한 팀 배정을 실행합니다. |
| `/semiontd playerlimit add <player>` | 정원 초과 입장 허용 목록에 플레이어를 추가합니다. |
| `/semiontd playerlimit remove <player>` | 정원 초과 입장 허용 목록에서 플레이어를 제거합니다. |
| `/semiontd playerlimit list` | 정원 초과 입장 허용 목록을 봅니다. |
| `/semiontd killboss <team>` | 지정 팀의 보스를 제거합니다. |
| `/semiontd rating softreset` | ELO 데이터를 백업한 뒤 소프트 리셋합니다. 같은 관리자가 30초 안에 두 번 입력해야 실행됩니다. |

`/semiontd reload`는 경제, 웨이브, 타워 카탈로그를 즉시 갱신합니다. 맵 설정은 다음 게임 생성부터 반영됩니다.

## 샌드박스 명령어

| 명령어 | 설명 |
|---|---|
| `/semiontd sandbox` | 샌드박스를 시작합니다. |
| `/semiontd sandbox start` | 샌드박스를 시작합니다. |
| `/semiontd sandbox reset` | 샌드박스를 리셋합니다. |
| `/semiontd sandbox leave` | 샌드박스를 나갑니다. |
| `/semiontd sandbox give ...` | 샌드박스 재화를 지급합니다. |
| `/semiontd sandbox money ...` | 샌드박스 재화를 조정합니다. |

샌드박스는 테스트와 빌드 확인용입니다. 운영 중 일반 경기 흐름과 섞어 쓰지 않습니다.

## 빌드 기록 명령어

일반 플레이어가 보는 alias:

| 명령어 | 설명 |
|---|---|
| `/빌드 기록 <title>` | 현재 빌드를 저장하고 제목을 붙입니다. |
| `/빌드 목록` | 빌드 목록 UI를 엽니다. |

내부 루트:

| 명령어 | 설명 |
|---|---|
| `/semiontd-internal build list <publicPage> <myPage>` | 공개 빌드와 내 빌드 목록을 엽니다. |
| `/semiontd-internal build track <code>` | 지정 빌드를 추적합니다. |
| `/semiontd-internal build detail <code>` | 빌드 상세를 봅니다. |
| `/semiontd-internal build public <code>` | 빌드를 공개로 전환합니다. |
| `/semiontd-internal build private <code>` | 빌드를 비공개로 전환합니다. |
| `/semiontd-internal build delete <code>` | 빌드를 삭제합니다. |
| `/semiontd-internal build clear` | 추적 중인 빌드를 해제합니다. |

`semiontd-internal`은 UI 버튼과 빌드 기록 흐름을 보조하는 명령어입니다. 일반 플레이어 안내 문서에서는 `/빌드` alias를 먼저 보여줍니다.

## 한국어 alias

| alias | 연결 명령 |
|---|---|
| `/직업` | `/semiontd job ui` |
| `/특성` | `/semiontd trait` |
| `/레이팅` | `/semiontd rating` |
| `/랭크` | `/semiontd rating` |
| `/순위` | `/semiontd rating top` |
| `/준비` | `/semiontd ready` |
| `/요청 <amount>` | `/semiontd money request <amount>` |
| `/샌드박스` | `/semiontd sandbox` |
| `/빌드` | 빌드 기록 UI와 저장 흐름 |

## 내부/디버그 명령어

`/semiontd-debug`는 permission level `2`를 요구합니다.

| 명령어 | 설명 |
|---|---|
| `/semiontd-debug towerui` | 디버그 타워 UI를 엽니다. |
| `/semiontd-debug tower ui` | 디버그 타워 UI를 엽니다. |
| `/semiontd-debug summonui [page]` | 디버그 소환 UI를 엽니다. |
| `/semiontd-debug summon ui [page]` | 디버그 소환 UI를 엽니다. |
| `/semiontd-debug give diamond <amount> [player]` | 다이아를 지급합니다. |
| `/semiontd-debug give emerald <amount> [player]` | 에메랄드를 지급합니다. |
| `/semiontd-debug buildguide [list/detail/towerui]` | 빌드 가이드 화면을 디버그합니다. |

디버그 명령어는 운영자가 서버 상태를 확인하거나 UI를 점검할 때만 사용합니다.
