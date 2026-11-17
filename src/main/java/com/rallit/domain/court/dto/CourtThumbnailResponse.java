package com.rallit.domain.court.dto;

public record CourtThumbnailResponse(String thumbnailUrl) {
  public static String urlFor(Long courtId) {
    return "/api/v1/courts/" + courtId + "/thumbnail";
  }
}
