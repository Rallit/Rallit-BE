# 온보딩 API 합의

## 내 정보와 온보딩 완료 응답

`GET /api/v1/users/me`와 `POST /api/v1/users/me/onboarding`의 성공 응답은 같은 회원 형식을 사용한다. 최종 제출 성공은 HTTP 200을 반환한다.

| 필드 | 내용 |
| --- | --- |
| `id`, `nickname`, `role`, `onboardingCompleted` | 기존 내 정보 필드. 가입 대기 회원의 `nickname`은 랠릿이 생성한 기본값이다. |
| `profileImageUrl` | 직접 업로드한 프로필 사진의 조회 주소. `null`이면 프론트가 랠릿 기본 이미지를 표시한다. |
| `socialDisplayName` | 가입 상태와 관계없이 반환하는 본인의 소셜 표시 이름. 저장된 이름이 없으면 `null`이다. |
| `profile` | 온보딩 완료 후 저장된 프로필과 선호 조건. |

## 온보딩 완료 요청

`POST /api/v1/users/me/onboarding`은 기존 Bearer 액세스 토큰과 다음 JSON을 받는다. 사진 등록은 별도 API로 처리한다.

```json
{
  "nickname": "테니스곰123",
  "gender": "FEMALE",
  "ageGroup": "TWENTIES",
  "experiencePeriod": "ONE_TO_THREE_YEARS",
  "ntrp": "4.0+",
  "sameGenderOnly": false,
  "preferredMatchTypes": ["DOUBLES", "RALLY"],
  "activityAreaCodes": ["11440", "11410"],
  "preferredTimeSlots": ["WEEKEND_MORNING"]
}
```

- 성별은 `MALE`, `FEMALE`을 사용한다. 연령대는 `TEENS`, `TWENTIES`, `THIRTIES`, `FORTIES`, `FIFTIES`, `SIXTIES_OR_OLDER`를 사용한다.
- 구력은 `UNDER_SIX_MONTHS`, `SIX_MONTHS_TO_ONE_YEAR`, `ONE_TO_THREE_YEARS`, `THREE_YEARS_OR_MORE`를 사용한다.
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

`PUT /api/v1/users/me/profile-image`는 `multipart/form-data`의 `file` 한 장을 받아 업로드 성공 시 표시할 URL을 반환한다. JPG·JPEG·PNG를 파일당 최대 5MB까지 허용한다. 초기 파일은 EC2의 Docker 볼륨에 저장한다.

디코딩 검증은 최대 가로·세로 16,384px 및 총 6,400만 픽셀까지 지원한다. 파일 크기 초과는 `413`, 형식·디코딩 오류는 `400`을 반환한다.

서버는 EXIF 방향을 적용하고 촬영 메타데이터를 제거한 사진을 저장한다. 저장되는 사진은 긴 변이 최대 1,024px이다.

`DELETE /api/v1/users/me/profile-image`는 본인이 올린 사진을 제거하고 `204 No Content`를 반환한다. 이후 내 정보의 `profileImageUrl`은 `null`이다. 이미 사진이 없는 경우에도 성공으로 처리한다.

`profileImageUrl`은 `/api/v1/users/{userId}/profile-image` 형식의 API 상대 경로다. 프론트는 백엔드 기본 주소에 이 경로를 붙여 `GET`을 보내고 `Authorization: Bearer <accessToken>`을 사용한다. 성공 응답은 `image/jpeg` 또는 `image/png` 바이트다. 새 파일로 교체한 뒤에는 같은 경로로 다시 조회할 수 있다.
