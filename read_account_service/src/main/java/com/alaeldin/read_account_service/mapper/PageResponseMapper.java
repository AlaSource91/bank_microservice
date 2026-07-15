package com.alaeldin.read_account_service.mapper;

import com.alaeldin.read_account_service.dto.PageResponse;
import org.springframework.data.domain.Page;

public class PageResponseMapper {

    private PageResponseMapper() {}

    public static <T> PageResponse<T> from(Page<T> page) {

        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalElements())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
