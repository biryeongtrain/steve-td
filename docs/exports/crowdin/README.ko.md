# 직업별 증강 Crowdin 편집 안내

## 파일 구성

[season-3-builder-augments.ko.json](season-3-builder-augments.ko.json)에 31개 직업의 증강 초안 124개를 담았다. 실버 31개, 골드 62개, 프리즘 31개이며, 명칭과 설명을 나누어 문자열은 총 248개다.

2026-09-21 기준은 사용자가 보낸 `Semion Humanize (ko).zip`이다. 복제 공장 3세대·추가 12기 제한, 무료 조커 타워 3기 배치권, 급속 개화 성장 5라운드 추가도 후속 답변대로 반영했다. [개정 기획과 개발 계획](../../plans/2026-09-21-season-3-builder-augment-development.ko.md)에 확인할 규칙을 정리했다.

이전 [엑셀](../../../outputs/01a051d2-3e38-7db0-bdc0-742f5b8fcdb9/시즌3_직업별_고유증강_수정용.xlsx)과 [9월 11일 JSON](history/season-3-builder-augments.ko.2026-09-11.json)은 비교 원본으로 보존한다. 비활성으로 분류했던 마도사·벌레·미래기관도 포함했다. 현재 서버의 활성 상태는 다시 확인하지 않았다. `history` 폴더의 파일을 Crowdin에 함께 업로드하지 않는다.

UTF-8 일반 JSON 형식이다. Crowdin은 별도 앱 설치 없이 JSON 업로드와 원문 편집을 지원한다. [Crowdin JSON 안내](https://store.crowdin.com/json)

## 한국어 문구 수정 순서

파일 기반 프로젝트를 기준으로 설명한다. 원본 언어(Source language)가 한국어인 프로젝트와 원문 편집 권한이 있는 계정을 사용한다.

1. **Sources → Files**에서 JSON 파일을 업로드한다. 파일 형식을 묻는 경우 **JSON**을 고른다. [원본 파일 업로드](https://support.crowdin.com/uploading-files/)
2. **Sources → Strings**에서 업로드한 파일로 필터링한다. 원하는 항목의 **Edit**을 눌러 **String**에 있는 한국어 문구를 수정하고 저장한다. 한국어 설명을 다듬는 작업이므로 번역문 입력란이 아닌 원문을 수정한다. **Identifier**는 그대로 둔다. [원문 편집](https://support.crowdin.com/string-management/)
3. 수정 후 **Sources → Files**에서 해당 파일을 우클릭하고 **Download source**로 원본 JSON을 내려받는다. [원본 파일 다운로드](https://support.crowdin.com/file-management/#downloading-source-files)
4. 내려받은 JSON을 보내주면 검토번호로 기존 기획과 대조할 수 있다. 편집을 시작한 뒤에는 이전 JSON을 다시 업로드해 수정 내용을 덮어쓰지 않는다.

## 항목 구분

Crowdin에서 표시하는 식별자 예시는 `villager_towers.J01-S.description`이다.

- `villager_towers`: 직업 구분. 아래 직업표에서 찾을 수 있다.
- `J01-S`: 엑셀의 검토번호. `S`는 실버, `G1`·`G2`는 서로 다른 골드 증강, `P`는 프리즘이다.
- `name`: 증강 명칭. `description`: 상세 효과와 수치.

이름을 바꿔도 식별자는 유지한다. 검토번호는 이 기획안의 대조용 번호이며 게임에 등록된 증강 ID가 아니다.

```json
{
  "villager_towers": {
    "J01-S": {
      "name": "유산 상속",
      "description": "라운드에 처음 사망한 타워의 생존 스택의 50%를 가장 가까운 같은 종류 타워에게 일시적으로 부여합니다."
    }
  }
}
```

## 직업표

| 검토번호 앞부분 | 직업 | JSON의 직업 구분 |
| --- | --- | --- |
| J01 | 주민 빌더 | `villager_towers` |
| J02 | 주민 ADV 빌더 | `villager_adv_towers` |
| J03 | 언데드 빌더 | `undead_towers` |
| J04 | 동물 빌더 | `animal_towers` |
| J05 | 흑마법사 | `warlock_towers` |
| J06 | 무리 빌더 | `legion_towers` |
| J07 | 무블룸 빌더 | `resonance_towers` |
| J08 | 우민 빌더 | `illager_towers` |
| J09 | 네더 빌더 | `nether` |
| J10 | 엔드 빌더 | `end_towers` |
| J11 | 바다 빌더 | `ocean` |
| J12 | 고대 도시 빌더 | `ancient_city` |
| J13 | 용사 빌더 | `hero_party` |
| J14 | 서큐버스 빌더 | `succubus` |
| J15 | 히어로 빌더 | `adversary_towers` |
| J16 | 기술자 | `engineer_towers` |
| J17 | 붉은 여왕 빌더 | `queen_towers` |
| J18 | 아틀란티스 빌더 | `atlantis` |
| J19 | 식물 빌더 | `plant_towers` |
| J20 | 군대 빌더 | `army` |
| J21 | 람쥐썬더 빌더 | `thunder` |
| J22 | 마왕 빌더 | `demon_lord_towers` |
| J23 | 겜블 빌더 | `gamble_towers` |
| J24 | 신체 빌더 | `body` |
| J25 | 반려동물 빌더 | `pet_towers` |
| J26 | 개발자 빌더 | `developer` |
| J27 | 혹한 빌더 | `frost` |
| J28 | 해적 빌더 | `pirate` |
| J29 | 마도사 빌더 | `mage_towers` |
| J30 | 벌레 빌더 | `insect_towers` |
| J31 | 미래기관 빌더 | `future_agency_towers` |

## 적용 범위와 확인 결과

JSON 구문과 검토번호의 중복·누락을 검사했다. 명칭·설명 248개를 첨부본과 대조했으며, 후속 답변을 반영한 설명 5개 외에는 첨부 문구를 유지했다. 기존 3개에 흉조의 첫 적중·표식 합산 규칙과 라이벌 매치의 추가 회복을 반영했다. Crowdin 프로젝트에 업로드하거나 화면을 확인하지는 않았다.

이번 파일은 기획 문구 편집용이다. Crowdin 수정 내용을 게임에 자동으로 반영하는 연결은 없다. 설명에 적힌 수치를 바꾸어도 실제 게임 능력치는 바뀌지 않는다.
