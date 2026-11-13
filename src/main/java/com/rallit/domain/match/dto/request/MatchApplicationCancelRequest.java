package com.rallit.domain.match.dto.request;

import com.rallit.domain.match.enums.MatchApplicationCancelReason;

public record MatchApplicationCancelRequest(MatchApplicationCancelReason reason) {
}
