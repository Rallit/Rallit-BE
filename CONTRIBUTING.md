# 협업 규칙

## 개발 흐름

1. GitHub 이슈에 작업 설명과 체크리스트를 작성하고 담당자를 지정합니다.
2. 최신 `develop`에서 이슈 번호를 포함한 작업 브랜치를 만듭니다.
3. 작업을 구현하고 필요한 테스트와 확인을 진행합니다.
4. 작업 내용을 커밋하고 작업 브랜치를 push합니다.
5. `develop`을 대상으로 PR을 작성하고 다른 팀원에게 리뷰를 요청합니다.
6. 리뷰를 반영하고 승인 1명 이상을 받은 뒤 병합합니다.

`main`과 `develop`의 변경은 작업 브랜치에서 PR을 통해 반영합니다.

## Git Flow

저장소의 기본 브랜치는 `develop`으로 설정합니다.

| 브랜치 | 역할 | 분기 기준 | PR 대상 |
| --- | --- | --- | --- |
| `main` | 배포 가능한 버전 유지 | — | — |
| `develop` | 다음 배포에 포함할 작업 통합 | `main` | — |
| `feat/번호-작업내용` | 기능 개발 | `develop` | `develop` |
| `chore/번호-작업내용` | 개발 환경 및 공통 설정 | `develop` | `develop` |
| `fix/번호-작업내용` | 개발 중 발견한 오류 수정 | `develop` | `develop` |
| `docs/번호-작업내용` | 문서 작성 및 수정 | `develop` | `develop` |
| `refactor/번호-작업내용` | 동작 변경 없이 코드 구조 개선 | `develop` | `develop` |
| `test/번호-작업내용` | 테스트 추가 및 수정 | `develop` | `develop` |
| `release/버전` | 배포 전 최종 확인 및 수정 | `develop` | `main`, `develop` |
| `hotfix/번호-작업내용` | 배포된 버전의 긴급 오류 수정 | `main` | `main`, `develop` |

기능별 작업은 완료될 때마다 `develop`에 병합합니다. 배포할 작업이 모이면
`develop`에서 `release/0.1.0`처럼 릴리스 브랜치를 만듭니다.
릴리스 브랜치에서는 최종 테스트, 오류 수정, 배포 문서 정리를 진행합니다.

배포 준비가 끝나면 릴리스 브랜치를 PR로 `main`에 병합하고 `v0.1.0`처럼
버전 태그를 지정합니다. 릴리스에서 수정한 내용은 `develop`에도 PR로 반영합니다.
`hotfix`의 수정 내용도 `main`과 `develop` 모두에 반영합니다.
릴리스와 긴급 수정의 병합에는 merge commit 방식을 사용해 브랜치 이력을 유지합니다.

## 브랜치 이름

- 형식: `작업종류/이슈번호-작업내용`
- 이슈 번호 앞에 `#`을 붙이지 않습니다.
- 작업 내용은 소문자 영어 단어를 하이픈으로 연결합니다.
- 예: `chore/1-initial-setup`, `feat/2-match-list`, `fix/3-match-filter`

## 커밋 메시지

- 형식: `작업종류: 작업 내용`
- 작업 내용은 변경 사항을 알아볼 수 있도록 간결하게 작성합니다.
- 한 커밋에는 서로 관련된 변경 사항을 담습니다.

| 종류 | 용도 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 | `feat: 매치 목록 조회 구현` |
| `fix` | 오류 수정 | `fix: 성별 필터 적용 오류 수정` |
| `chore` | 환경 및 공통 설정 | `chore: Swagger 설정 추가` |
| `docs` | 문서 수정 | `docs: 로컬 실행 방법 정리` |
| `refactor` | 코드 구조 개선 | `refactor: 매치 필터 조건 분리` |
| `test` | 테스트 추가 및 수정 | `test: 매치 목록 필터 테스트 추가` |

## PR 작성 및 리뷰

- 제목: `[작업종류] 작업 내용` 형식으로 작성합니다.
  - 예: `[CHORE] 초기 개발 환경 및 공통 설정 구성`
- 일반 작업의 PR 대상은 `develop`입니다.
- 배포 및 긴급 수정 PR은 Git Flow 표에 따라 대상을 지정합니다.
- PR 템플릿에 작업 내용, 테스트 및 확인 방법, 결과를 작성합니다.
- 코드 변경은 관련 테스트와 필요한 실행 확인을 진행합니다.
- 문서 및 설정 변경은 변경 내용에 맞는 확인 방법을 사용합니다.
- 확인하지 못한 항목은 이유를 명시합니다.
- 리뷰어는 다른 팀원을 지정하고, 작성자는 리뷰를 반영하거나 의견을 남깁니다.
- 병합 전 리뷰 대화를 해결하고 다른 팀원의 승인 1명 이상을 받습니다.

### 이슈 연결

`develop` 대상 작업 PR의 본문에는 아래와 같이 이슈를 연결합니다.

```markdown
Closes #1
```

기본 브랜치가 `develop`일 때 해당 PR이 병합되면 연결된 이슈가 자동으로 닫힙니다.
여러 이슈를 완료하는 경우에는 `Closes #1`, `Closes #2`처럼 각각 작성합니다.

`main` 등 기본 브랜치가 아닌 브랜치 대상 PR에서는 종료 키워드에 의한 자동 종료가
적용되지 않습니다. 해당 PR에는 `Refs #1`처럼 참고할 이슈를 표시하고, 필요하면
GitHub의 Development 항목에서 연결합니다. 긴급 수정 이슈는 `develop` 반영까지
확인한 뒤 종료합니다.

## 참고 자료

- [Git Flow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
- [GitHub 이슈와 PR 연결](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
