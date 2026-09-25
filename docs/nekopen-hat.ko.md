# 네코펭 모자

- 상점 ID / 아이템 ID: `nekopen_hat` / `semion-td:nekopen_hat`
- 가격: 치장 포인트 **100**
- 착용 슬롯: 머리
- 머리 착용 위치: `[0,16.4,-2.5]` — 머리 앞뒤 길이의 약 20%만큼 앞으로 이동
- 머리 착용 배율: `[1.2,1.2,1.2]` — 기존보다 20% 확대하고 바닥 높이 유지
- 상점에서 첫 클릭으로 구매하고, 다시 클릭하면 장착한다.

`SemionCosmeticItems`에 Polymer 아이템으로 등록하고, 기본 상점 목록인 `src/main/resources/semiontd/balance-defaults/cosmetics.json`에 추가했다.

모델은 사용자가 확인한 둥근 버전이다. 리소스 파일은 `src/main/resources/assets/semion-td/` 아래의 `items/nekopen_hat.json`, `models/item/cosmetics/nekopen_hat.json`, `textures/item/cosmetics/nekopen_hat.png`에 있다. 이 디렉터리는 저장소 정책에 따라 Git에서 제외하며, 로컬 빌드 JAR에는 포함된다.

## 기존 서버에 등록

기존 `config/semion-td/cosmetics.json`은 기본 목록이 변경되어도 자동 교체되지 않는다. 새 아이템과 모델이 포함된 JAR 및 서버 리소스팩 적용 후, 운영자가 아래 명령으로 받은 모자를 주 손에 들고 등록한다.

```mcfunction
/give @s semion-td:nekopen_hat[minecraft:item_name={text:"네코펭 모자",color:"gold",italic:false}] 1
/semiontd cosmetic add nekopen_hat 100 head
```

이미 등록되어 있으면 같은 모자를 들고 `/semiontd cosmetic update nekopen_hat 100 head`를 사용한다. 등록 명령은 상점 목록을 저장하고 즉시 반영한다. 상점은 `/semiontd cosmetic`으로 연다.

## 검증

`NekopenHatGameTest`는 번들 목록의 실제 항목을 읽고 이름·가격 표시, 99포인트 구매 거절, 100포인트 결제, 재클릭 장착, 모델 ID와 보유/장착 정보 저장을 확인한다. 실제 클라이언트 화면 및 운영 서버 반영 여부는 별도다.
