package com.rallit.domain.match.controller;

final class HostMatchApplicationExamples {
	private HostMatchApplicationExamples() {
	}

	static final String LIST = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {
			    "match": {
			      "matchId": 42,
			      "status": "RECRUITING",
			      "matchTypes": ["MIXED_DOUBLES"],
			      "startsAt": "2026-11-14T14:00:00+09:00",
			      "endsAt": "2026-11-14T16:00:00+09:00",
			      "recruitmentDeadline": "2026-11-13T18:00:00+09:00",
			      "participationFee": 3500,
			      "courtName": "장충테니스장",
			      "courtNumber": "3"
			    },
			    "recruitment": {
			      "capacity": 4,
			      "currentParticipantCount": 2,
			      "pendingCount": 1,
			      "male": {"recruitCount": 1, "occupiedCount": 0, "remainingCount": 1},
			      "female": {"recruitCount": 2, "occupiedCount": 1, "remainingCount": 1},
			      "recruitmentFilled": false
			    },
			    "pendingApplications": [{
			      "applicationId": 103,
			      "appliedAt": "2026-11-12T10:15:00",
			      "applicant": {
			        "userId": 11,
			        "nickname": "강민서",
			        "profileImageUrl": null,
			        "gender": "MALE",
			        "ntrp": 3.0,
			        "ageGroup": "TWENTIES",
			        "experiencePeriod": "ONE_TO_THREE_YEARS"
			      }
			    }],
			    "participants": [{
			      "host": true,
			      "user": {
			        "userId": 7,
			        "nickname": "호스트",
			        "profileImageUrl": "/api/v1/users/7/profile-image",
			        "gender": "MALE",
			        "ntrp": 3.0,
			        "ageGroup": "THIRTIES",
			        "experiencePeriod": "ONE_TO_THREE_YEARS"
			      }
			    }, {
			      "host": false,
			      "applicationId": 101,
			      "status": "PAYMENT_PENDING",
			      "acceptedAt": "2026-11-12T11:00:00",
			      "paymentDueAt": "2026-11-13T11:00:00",
			      "user": {
			        "userId": 9,
			        "nickname": "이윤경",
			        "profileImageUrl": null,
			        "gender": "FEMALE",
			        "ntrp": 3.0,
			        "ageGroup": "TWENTIES",
			        "experiencePeriod": "SIX_MONTHS_TO_ONE_YEAR"
			      }
			    }]
			  }
			}
			""";

	static final String ACCEPT = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {
			    "applicationId": 103,
			    "status": "PAYMENT_PENDING",
			    "acceptedAt": "2026-11-12T12:00:00",
			    "paymentDueAt": "2026-11-13T12:00:00"
			  }
			}
			""";

	static final String REJECT = """
			{
			  "isSuccess": true,
			  "code": "COMMON200",
			  "message": "성공입니다.",
			  "result": {"applicationId": 103, "status": "REJECTED", "rejectReason": "SKILL_GAP"}
			}
			""";

	static final String BAD_REQUEST =
			"{\"isSuccess\":false,\"code\":\"COMMON400\",\"message\":\"잘못된 요청입니다.\"}";
	static final String UNAUTHORIZED =
			"{\"isSuccess\":false,\"code\":\"AUTH401\",\"message\":\"인증이 필요합니다.\"}";
	static final String NOT_MATCH_HOST =
			"{\"isSuccess\":false,\"code\":\"NOT_MATCH_HOST\",\"message\":\"매치를 개설한 호스트만 참가 신청을 관리할 수 있습니다.\"}";
	static final String MATCH_NOT_FOUND =
			"{\"isSuccess\":false,\"code\":\"MATCH_NOT_FOUND\",\"message\":\"매치를 찾을 수 없습니다.\"}";
	static final String APPLICATION_NOT_FOUND =
			"{\"isSuccess\":false,\"code\":\"APPLICATION_NOT_FOUND\",\"message\":\"참가 신청 내역을 찾을 수 없습니다.\"}";
	static final String APPLICATION_NOT_PENDING =
			"{\"isSuccess\":false,\"code\":\"APPLICATION_NOT_PENDING\",\"message\":\"수락 또는 거절을 기다리는 참가 신청이 아닙니다.\"}";
	static final String SEAT_UNAVAILABLE =
			"{\"isSuccess\":false,\"code\":\"SEAT_UNAVAILABLE\",\"message\":\"해당 성별의 잔여석이 없습니다.\"}";
	static final String MATCH_NOT_RECRUITING =
			"{\"isSuccess\":false,\"code\":\"MATCH_NOT_RECRUITING\",\"message\":\"모집 중인 매치가 아닙니다.\"}";
	static final String MATCH_RECRUITMENT_CLOSED =
			"{\"isSuccess\":false,\"code\":\"MATCH_RECRUITMENT_CLOSED\",\"message\":\"모집 기한이 지난 매치입니다.\"}";
}
