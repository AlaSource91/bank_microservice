package com.alaeldin.bank_query_service.mapper;

import com.alaeldin.bank_query_service.dto.PageResponse;
import org.springframework.data.domain.Page;

public class PageResponseMapper
{
    private PageResponseMapper() {}

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
