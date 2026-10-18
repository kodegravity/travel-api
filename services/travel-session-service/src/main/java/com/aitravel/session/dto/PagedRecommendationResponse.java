package com.aitravel.session.dto;

import java.util.List;

/**
 * One page of recommendations, best first.
 *
 * @param recommendations the items on this page
 * @param page            zero-based page number
 * @param size            requested page size
 * @param totalElements   eligible recommendations across all pages
 * @param totalPages      number of pages at this size (0 when there are no results)
 */
public record PagedRecommendationResponse(
        List<RecommendationDto> recommendations,
        int page,
        int size,
        int totalElements,
        int totalPages
) {

    /** Slices an already ranked list. A page past the end is empty but still reports the totals. */
    public static PagedRecommendationResponse of(List<RecommendationDto> all, int page, int size) {
        int total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        int pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PagedRecommendationResponse(List.copyOf(all.subList(from, to)), page, size, total, pages);
    }
}
