package com.rallit.domain.user.controller;

import static com.rallit.domain.user.entity.OnboardingProfile.NICKNAME_PATTERN;

import com.rallit.domain.user.dto.NicknameAvailabilityResponse;
import com.rallit.domain.user.dto.OnboardingRequest;
import com.rallit.domain.user.dto.UserResponse;
import com.rallit.domain.user.service.UserService;
import com.rallit.global.apiPayload.ApiResponse;
import com.rallit.global.security.authentication.UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;

  @Operation(
      summary = "내 회원 정보 조회",
      description = "`result.onboardingCompleted`가 `false`이면 온보딩 화면으로 이동합니다.")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(
      @AuthenticationPrincipal UserDetails principal) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.getMe(principal.userId())));
  }

  @Operation(
      summary = "온보딩 완료",
      description = """
          - 내 기본 닉네임을 사용할 수 있습니다. 중복 여부는 영문 대소문자를 구분하지 않습니다.
          - `sameGenderOnly`가 `true`이면 `MIXED_DOUBLES`를 함께 선택할 수 없습니다.
          """,
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(name = "가입 완료 예시", value = """
                  {
                    "nickname": "날쌘발리127",
                    "gender": "FEMALE",
                    "ageGroup": "TWENTIES",
                    "experiencePeriod": "ONE_TO_THREE_YEARS",
                    "ntrp": "4.0+",
                    "sameGenderOnly": false,
                    "preferredMatchTypes": ["DOUBLES", "RALLY"],
                    "activityAreaCodes": ["11440"],
                    "preferredTimeSlots": ["WEEKEND_MORNING"]
                  }
                  """))),
      responses = {
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "200", description = "가입 완료 또는 동일 내용 재제출",
              useReturnTypeSchema = true),
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "400", description = "필수값 누락, 형식 오류 또는 유효하지 않은 선택",
              content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                  schema = @Schema(implementation = ApiResponse.class))),
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "409", description = "닉네임 중복 또는 완료 후 다른 내용 제출",
              content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                  schema = @Schema(implementation = ApiResponse.class)))
      })
  @PostMapping("/me/onboarding")
  public ResponseEntity<ApiResponse<UserResponse>> completeOnboarding(
      @AuthenticationPrincipal UserDetails principal, @Valid @RequestBody OnboardingRequest request) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.completeOnboarding(principal.userId(), request)));
  }

  @Operation(
      summary = "닉네임 사용 가능 여부 조회",
      description = "`result.available`이 `true`이면 사용할 수 있습니다. 내 기본 닉네임도 사용 가능합니다.")
  @GetMapping("/nickname-availability")
  public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNickname(
      @AuthenticationPrincipal UserDetails principal,
      @RequestParam @Pattern(regexp = NICKNAME_PATTERN) String nickname) {
    return ResponseEntity.ok(ApiResponse.onSuccess(users.checkNickname(principal.userId(), nickname)));
  }
}
