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
        List<ProductSalesRow> rows = statisticsRepository.findTopSellingProducts(RANKING_SIZE);
        if (rows.isEmpty()) {
            return List.of();
        }

        // Los productos del ranking se piden de una vez: dan el nombre actual y, si hace falta, el vendedor
        Map<String, Product> products = new HashMap<>();
        productRepository.findAllById(rows.stream().map(ProductSalesRow::productId).toList())
                .forEach(product -> products.put(product.getId(), product));

        // Un mismo vendedor puede aparecer en varias filas: su nombre se busca una sola vez
        Map<String, String> sellerNames = new HashMap<>();

        return rows.stream()
                .map(row -> toDto(row, products.get(row.productId()), sellerNames))
                .toList();
    }

    private ProductSalesDTO toDto(ProductSalesRow row, Product product, Map<String, String> sellerNames) {
        String sellerId = resolveSellerId(row, product);
        String sellerName = sellerId == null
                ? null
                : sellerNames.computeIfAbsent(sellerId, this::resolveSellerName);

        return new ProductSalesDTO(
                row.productId(),
                resolveProductName(row, product),
                sellerId,
                sellerName,
                row.unitsSold(),
                row.revenue());
    }

    /**
     * Si el producto se renombró, el ranking muestra el nombre actual y no uno de los que
     * quedaron guardados en los pedidos. Si el producto ya no existe se usa el del pedido.
     */
    private String resolveProductName(ProductSalesRow row, Product product) {
        if (product != null && product.getName() != null && !product.getName().isBlank()) {
            return product.getName();
        }

        return row.productName();
    }

    /**
     * Las líneas de pedidos antiguas pueden no guardar el vendedor: entonces se toma
     * el del producto, si todavía existe.
     */
    private String resolveSellerId(ProductSalesRow row, Product product) {
        if (row.sellerId() != null && !row.sellerId().isBlank()) {
            return row.sellerId();
        }

        if (product == null || product.getSellerId() == null || product.getSellerId().isBlank()) {
            return null;
        }

        return product.getSellerId();
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
