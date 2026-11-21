package es.uclm.esibuy.products.dto;

import java.math.BigDecimal;
import java.util.List;

public record CatalogPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal maxPrice) {
}