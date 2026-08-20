# 화면 명세 인덱스

화면 명세는 사용자가 검토할 수 있는 UX 원장이다. 기능 동작은 `docs/features/`, 화면 간 전환은 `docs/flows/`, 서버 데이터는 `docs/contracts/`를 기준으로 하고 이곳에 중복 작성하지 않는다.

| 화면 | 문서 | 상태 | 설명 |
| --- | --- | --- | --- |
| 홈 | [home.md](home.md) | `draft` | 지금 할 일을 선택하고 학습을 재개하는 시작 화면 |
| 학습 | [learning.md](learning.md) | `draft` | 자료 가져오기·문제 만들기·복습을 한곳에서 다루는 화면 |
| 프로필 | [profile.md](profile.md) | `draft` | 계정과 개인 설정을 관리하는 화면 |
| 회원가입 | [signup.md](signup.md) | `draft` | 이메일 인증과 가입정보 설정을 2단계로 완료하는 화면 |

## 상태 의미

- `draft`: 확정 요구사항과 설계 가정을 분리해 작성한 초안
- `review`: 사용자 검토 중
- `approved`: 구현 기준으로 사용자 승인 완료
- `implemented`: 승인 명세가 구현되었고 차이를 반영함

화면을 구현하는 에이전트는 반드시 문서 상태와 미결정 사항을 확인한다. `draft`를 승인된 UI로 간주하지 않는다. 새 문서는 [화면 명세 템플릿](../templates/screen-spec.md)을 사용한다.
