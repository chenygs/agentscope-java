package io.agentscope.builder.saton.common;

import java.util.List;

/**
 * Paginated data payload for use as {@code R<PageData<T>>}.
 *
 * @param <T> element type
 * @param list   current page of records
 * @param total  total record count across all pages
 * @param page   1-based page number
 * @param size   page size
 */
public record PageData<T>(List<T> list, long total, int page, int size) {}
