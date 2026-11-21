package es.uclm.esibuy.statistics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.repository.ProductSalesRow;
import es.uclm.esibuy.statistics.repository.StatisticsRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;

@Service
public class StatisticsService {

    /** Tamaño del ranking: con empate en el puesto 10 se enseñan solo 10 (RN-01). */
    static final int RANKING_SIZE = 10;

    private final StatisticsRepository statisticsRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public StatisticsService(
            StatisticsRepository statisticsRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.statisticsRepository = statisticsRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public List<ProductSalesDTO> getTopSellingProducts() {
        // Un mismo vendedor puede aparecer en varias filas: su nombre se busca una sola vez
        Map<String, String> sellerNames = new HashMap<>();

        return statisticsRepository.findTopSellingProducts(RANKING_SIZE).stream()
                .map(row -> toDto(row, sellerNames))
                .toList();
    }

    private ProductSalesDTO toDto(ProductSalesRow row, Map<String, String> sellerNames) {
        String sellerId = resolveSellerId(row);
        String sellerName = sellerId == null
                ? null
                : sellerNames.computeIfAbsent(sellerId, this::resolveSellerName);

        return new ProductSalesDTO(
                row.productId(),
                row.productName(),
                sellerId,
                sellerName,
                row.unitsSold(),
                row.revenue());
    }

    /**
     * Las líneas de pedidos antiguas pueden no guardar el vendedor: entonces se toma
     * el del producto, si todavía existe.
     */
    private String resolveSellerId(ProductSalesRow row) {
        if (row.sellerId() != null && !row.sellerId().isBlank()) {
            return row.sellerId();
        }

        return productRepository.findById(row.productId())
                .map(Product::getSellerId)
                .filter(id -> !id.isBlank())
                .orElse(null);
    }

    private String resolveSellerName(String sellerId) {
        return userRepository.findById(sellerId)
                .map(seller -> displayName(seller, sellerId))
                .orElse("Vendedor " + sellerId);
    }

    private String displayName(User seller, String sellerId) {
        if (seller.getSellerProfile() != null
                && seller.getSellerProfile().getBusinessName() != null
                && !seller.getSellerProfile().getBusinessName().isBlank()) {
            return seller.getSellerProfile().getBusinessName();
        }

        String fullName = ((seller.getFirstName() != null ? seller.getFirstName() : "") + " "
                + (seller.getLastName() != null ? seller.getLastName() : "")).trim();

        return fullName.isBlank() ? "Vendedor " + sellerId : fullName;
    }
}
