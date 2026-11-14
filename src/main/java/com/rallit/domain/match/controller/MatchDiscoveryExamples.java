package com.rallit.domain.match.controller;

final class MatchDiscoveryExamples {
	private MatchDiscoveryExamples() {
	}

	static final String LIST = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {
			    "items": [{
			      "matchId": 42,
			      "matchStatus": "RECRUITING",
			      "matchTypes": ["SINGLES"],
			      "startsAt": "2026-11-15T14:00:00+09:00",
			      "endsAt": "2026-11-15T16:00:00+09:00",
			      "courtName": "랠릿 데모 마포 코트",
			      "participationFee": 8000,
			      "minNtrp": 2.5,
			      "maxNtrp": 3.5,
			      "currentParticipantCount": 1,
			      "capacity": 2
			    }],
			    "page": 0,
			    "size": 20,
			    "hasNext": false,
			    "totalElements": 1
			  }
			}
			""";

	static final String EMPTY_LIST = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {"items": [], "page": 0, "size": 20, "hasNext": false, "totalElements": 0}
			}
			""";

	static final String DETAIL = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {
			    "matchId": 42,
			    "matchStatus": "RECRUITING",
			    "matchTypes": ["SINGLES"],
			    "startsAt": "2026-11-15T14:00:00+09:00",
			    "endsAt": "2026-11-15T16:00:00+09:00",
			    "participationFee": 8000,
			    "minNtrp": 2.5,
			    "maxNtrp": 3.5,
			    "currentParticipantCount": 1,
			    "capacity": 2,
			    "recruitmentDeadline": "2026-11-15T13:00:00+09:00",
			    "recruitmentOpen": true,
			    "canApply": true,
			    "applicationBlockReason": null,
			    "maleRecruitCount": 0,
			    "femaleRecruitCount": 1,
			    "preferredAgeGroups": [],
			    "court": {
			      "courtId": 1,
			      "name": "랠릿 데모 마포 코트",
			      "address": "서울특별시 마포구 (가상 코트)",
			      "courtNumber": "1",
			      "latitude": 37.555000,
			      "longitude": 126.910000,
			      "hasParking": true,
			      "hasShower": false,
			      "facilityNotes": "랠릿 로컬 매치 조회 데모"
			    },
			    "host": {
			      "userId": 7,
			      "nickname": "랠릿조회데모호스트",
			      "experiencePeriod": "ONE_TO_THREE_YEARS",
			      "profileImageUrl": null
			    }
			  }
			}
			""";

	static final String BAD_REQUEST =
			"{\"isSuccess\":false,\"code\":\"COMMON400\",\"message\":\"잘못된 요청입니다.\"}";
	static final String DATE_REQUIRED =
			"{\"isSuccess\":false,\"code\":\"MATCH_DATE_REQUIRED_FOR_TIME_FILTER\",\"message\":\"시간 조건을 사용하려면 조회 날짜를 지정해 주세요.\"}";
	static final String INVALID_TIME_RANGE =
			"{\"isSuccess\":false,\"code\":\"MATCH_INVALID_TIME_RANGE\",\"message\":\"조회 시작 시간은 종료 시간보다 빨라야 합니다.\"}";
	static final String PAGE_OUT_OF_RANGE =
			"{\"isSuccess\":false,\"code\":\"MATCH_PAGE_OUT_OF_RANGE\",\"message\":\"요청한 페이지가 매치 목록의 범위를 벗어났습니다.\"}";
	static final String UNAUTHORIZED =
			"{\"isSuccess\":false,\"code\":\"AUTH401\",\"message\":\"인증이 필요합니다.\"}";
	static final String ONBOARDING_REQUIRED =
			"{\"isSuccess\":false,\"code\":\"MATCH_ONBOARDING_REQUIRED\",\"message\":\"온보딩을 완료한 회원만 매치를 조회할 수 있습니다.\"}";
	static final String MATCH_NOT_FOUND =
			"{\"isSuccess\":false,\"code\":\"MATCH_NOT_FOUND\",\"message\":\"매치를 찾을 수 없습니다.\"}";
	static final String SERVICE_UNAVAILABLE =
			"{\"isSuccess\":false,\"code\":\"COMMON503\",\"message\":\"잠시 후 다시 시도해 주세요.\"}";
}
