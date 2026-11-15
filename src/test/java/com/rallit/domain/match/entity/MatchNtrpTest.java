package com.rallit.domain.match.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.rallit.domain.match.exception.InvalidMatchNtrpException;
import com.rallit.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class MatchNtrpTest {

	@ParameterizedTest
	@ValueSource(strings = {"0.5", "3.6", "3.9", "4.5"})
	void rejectsUnsupportedValuesForEitherRecruitmentBound(String value) {
		var ntrp = new BigDecimal(value);
		assertThatThrownBy(() -> Match.builder().minNtrp(ntrp).build())
				.isInstanceOf(InvalidMatchNtrpException.class);
		assertThatThrownBy(() -> Match.builder().maxNtrp(ntrp).build())
				.isInstanceOf(InvalidMatchNtrpException.class);
	}

	@Test
	void classifiesUnsupportedRecruitmentNtrpAsABadRequest() {
		assertThatThrownBy(() -> Match.builder().maxNtrp(new BigDecimal("3.6")).build())
				.isInstanceOfSatisfying(GeneralException.class, failure -> {
					var reason = failure.getErrorReasonHttpStatus();
					assertThat(reason.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(reason.getCode()).isEqualTo("MATCH_INVALID_RECRUITMENT_NTRP");
				});
	}

	@Test
	void acceptsTheUpperBandAndUnsetRecruitmentBounds() {
		var match = Match.builder().minNtrp(new BigDecimal("3.5")).maxNtrp(new BigDecimal("4.0")).build();

		assertThat(match.getMinNtrp()).isEqualByComparingTo("3.5");
		assertThat(match.getMaxNtrp()).isEqualByComparingTo("4.0");
		assertThat(Match.builder().minNtrp(new BigDecimal("1.0")).build().getMaxNtrp()).isNull();
		assertThat(Match.builder().maxNtrp(new BigDecimal("2.5")).build().getMinNtrp()).isNull();
	}
}
