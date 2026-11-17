# 온보딩 API 합의

## 내 정보와 온보딩 완료 응답

`GET /api/v1/users/me`와 `POST /api/v1/users/me/onboarding`의 성공 응답은 같은 회원 형식을 사용한다. 최종 제출 성공은 HTTP 200을 반환한다.

| 필드 | 내용 |
| --- | --- |
| `id`, `nickname`, `role`, `onboardingCompleted` | 기존 내 정보 필드. 가입 대기 회원의 `nickname`은 랠릿이 생성한 기본값이다. |
| `profileImageUrl` | 등록한 프로필 사진의 상대 경로. 끝에 `/url`을 붙여 서명 URL을 발급받는다. 값이 `null`이면 랠릿 기본 이미지를 표시한다. |
| `socialDisplayName` | 가입 상태와 관계없이 반환하는 본인의 소셜 표시 이름. 저장된 이름이 없으면 `null`이다. |
| `profile` | 온보딩 완료 후 저장된 프로필과 선호 조건. 구력은 `tennisStartedOn`과 `experienceYears`로 반환한다. |

## 온보딩 완료 요청

`POST /api/v1/users/me/onboarding`은 기존 Bearer 액세스 토큰과 다음 JSON을 받는다. 사진 등록은 별도 API로 처리한다.

```json
{
  "nickname": "테니스곰123",
  "gender": "FEMALE",
  "ageGroup": "TWENTIES",
  "tennisStartedOn": "2024-11",
  "ntrp": "4.0+",
  "sameGenderOnly": false,
  "preferredMatchTypes": ["DOUBLES", "RALLY"],
  "activityAreaCodes": ["11440", "11410"],
  "preferredTimeSlots": ["WEEKEND_MORNING"]
}
```

- 성별은 `MALE`, `FEMALE`을 사용한다. 연령대는 `TEENS`, `TWENTIES`, `THIRTIES`, `FORTIES`, `FIFTIES`, `SIXTIES_OR_OLDER`를 사용한다.
- 테니스 시작 시기 `tennisStartedOn`은 `yyyy-MM` 연월 문자열로 보낸다. 1900-01부터 서울 기준 현재 연월까지 허용하며, 범위를 벗어나면 `400`과 "테니스 시작 시기를 확인해 주세요." 메시지를 반환한다.
- 응답의 `profile.tennisStartedOn`은 저장된 시작 연월이고, `profile.experienceYears`는 조회 시점의 서울 기준 연월로 계산한 만 구력(년)이다. 시작 2024-11, 현재 2026-10이면 `1`이며 1년 미만은 `0`이다. 시작 연월이 없는 기존 회원은 두 값 모두 `null`이다.
- 경기 방식은 `SINGLES`, `RALLY`, `DOUBLES`, `MIXED_DOUBLES`를 사용한다. 시간대는 `WEEKDAY_MORNING`, `WEEKDAY_AFTERNOON`, `WEEKEND_MORNING`, `WEEKEND_AFTERNOON`을 사용한다.
- 같은 내용으로 재제출하면 `200`을 반환한다. 다른 회원이 사용 중인 닉네임으로 제출하거나 완료한 온보딩을 변경하려 하면 `409`를 반환한다.
- `GET /api/v1/users/nickname-availability?nickname=...`의 결과는 `available` 불리언이다. 본인 기본 닉네임은 사용할 수 있다.

## 활동 지역

| 메서드 및 경로 | 응답 |
| --- | --- |
| `GET /api/v1/cities` | 시·도 목록. 각 항목에 `code`, `name`을 반환한다. |
| `GET /api/v1/cities/{cityCode}/areas` | 지정한 시·도의 자치구 목록. 각 항목에 `code`, `name`을 반환한다. |

초기 온보딩은 `GET /api/v1/cities/11/areas`로 서울 자치구 목록을 조회할 수 있다. 온보딩 완료 요청의 `activityAreaCodes`에는 `["11440", "11410"]`처럼 서로 다른 구 코드 1~3개를 문자열로 보낸다. 공식 코드와 서울의 초기 지역 목록은 [시드 데이터](../../../src/main/resources/db/migration/V5__seed_seoul_regions.sql)에서 확인할 수 있다.

## 프로필 사진

### 등록과 교체

1. Bearer 인증으로 `POST /api/v1/users/me/profile-image/uploads`에 `fileName`, `contentType`, `size`(바이트)를 보낸다. `201 COMMON201`의 `result`에서 `mode`(`S3`), `uploadId`, S3 `url`, 서명 폼 `fields`, `expiresAt`을 받는다.
2. 브라우저가 S3 `url`에 `multipart/form-data`로 서명 필드와 `file`을 전송한다. 파일 필드는 마지막에 붙인다.
3. S3 업로드가 성공하면 Bearer 인증으로 `POST /api/v1/users/me/profile-image/uploads/{uploadId}/complete`를 호출한다. 최종 저장과 프로필 반영이 끝나면 `200 COMMON200`과 `result.profileImageUrl`을 반환한다.

JPG·JPEG·PNG를 최대 5MiB까지 받는다. 원본은 각 변 4,096px, 총 1,250만 픽셀까지 허용한다. EXIF 방향을 보정하고 촬영 메타데이터를 제거한 뒤 최대 320×320 정사각형으로 저장한다.

파일 크기 초과는 `413`, 원본 해상도 초과와 형식·디코딩 오류는 `400`을 반환한다. 완료 요청에서 미업로드는 `409 IMAGE_UPLOAD_NOT_READY`, 티켓 만료는 `410 IMAGE_UPLOAD_EXPIRED`, 저장소 장애나 동시 처리 한도 초과는 `503`으로 응답한다.

### 조회와 삭제

`profileImageUrl`은 `/api/v1/users/{userId}/profile-image` 형식이다. 브라우저는 이 값의 끝에 `/url`을 붙인 `GET /api/v1/users/{userId}/profile-image/url`을 Bearer 인증으로 호출한다. 응답의 `result.url`은 S3에서 사진을 읽는 데 사용하고 `result.expiresAt`은 서명 만료 시각이다.

`DELETE /api/v1/users/me/profile-image`는 본인이 올린 사진을 제거하고 `204 No Content`를 반환한다. 이후 내 정보의 `profileImageUrl`은 `null`이다. 이미 사진이 없는 경우에도 성공으로 처리한다.
