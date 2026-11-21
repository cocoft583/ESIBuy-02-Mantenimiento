package es.uclm.esibuy.products.dto;

import java.math.BigDecimal;

public record CatalogSearch(
        String query,
        String categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        Boolean inStock,
        Boolean onlyOffers,
        String sort,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;

    public CatalogSearch {
        page = Math.max(page, 0);
        size = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}