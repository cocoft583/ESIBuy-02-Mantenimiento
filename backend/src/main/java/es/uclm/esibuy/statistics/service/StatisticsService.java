package es.uclm.esibuy.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductRatingDTO;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.dto.RatingStatisticsDTO;
import es.uclm.esibuy.statistics.dto.SalesStatisticsDTO;
import es.uclm.esibuy.statistics.dto.SellerRatingDTO;
import es.uclm.esibuy.statistics.model.StatisticsScope;
import es.uclm.esibuy.statistics.repository.ProductRatingRow;
import es.uclm.esibuy.statistics.repository.ProductSalesRow;
import es.uclm.esibuy.statistics.repository.SellerRatingRow;
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
    private final GeneralConfigurationService configurationService;

    public StatisticsService(
            StatisticsRepository statisticsRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            GeneralConfigurationService configurationService) {
        this.statisticsRepository = statisticsRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.configurationService = configurationService;
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

    /**
     * Ranking de ventas según el ámbito. En PLATAFORMA es el de {@link #getTopSellingProducts()}.
     * En VENDEDOR son los 10 productos más vendidos y los 10 menos vendidos del vendedor,
     * incluidos los que no se han vendido nunca (RF-EST-07, 08). El {@code sellerId} tiene que
     * salir siempre de la sesión, nunca de un parámetro de la petición.
     */
    public SalesStatisticsDTO getSales(StatisticsScope scope, String sellerId) {
        Objects.requireNonNull(scope, "El ámbito es obligatorio");

        if (scope == StatisticsScope.PLATAFORMA) {
            return new SalesStatisticsDTO(scope, getTopSellingProducts(), List.of());
        }

        return getSellerSales(sellerId);
    }

    public SalesStatisticsDTO getSellerSales(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("El identificador del vendedor es obligatorio");
        }

        // Los productos del vendedor dan el nombre actual y los que no tienen ninguna venta
        Map<String, Product> products = new LinkedHashMap<>();
        productRepository.findAllBySellerId(sellerId)
                .forEach(product -> products.put(product.getId(), product));

        Map<String, ProductSalesRow> rows = new LinkedHashMap<>();
        statisticsRepository.findSellerProductSales(sellerId, products.keySet())
                .forEach(row -> rows.put(row.productId(), row));
        products.values().forEach(product -> rows.computeIfAbsent(product.getId(),
                id -> new ProductSalesRow(id, product.getName(), sellerId, 0, BigDecimal.ZERO)));

        String sellerName = resolveSellerName(sellerId);
        List<ProductSalesDTO> sales = rows.values().stream()
                .map(row -> new ProductSalesDTO(
                        row.productId(),
                        resolveProductName(row, products.get(row.productId())),
                        sellerId,
                        sellerName,
                        row.unitsSold(),
                        row.revenue().setScale(2, RoundingMode.HALF_UP)))
                .toList();

        // Mismo desempate que el ranking de la plataforma: el top es siempre el mismo (RN-01)
        Comparator<ProductSalesDTO> fewestFirst = Comparator.comparingLong(ProductSalesDTO::unitsSold)
                .thenComparing(ProductSalesDTO::revenue)
                .thenComparing(ProductSalesDTO::productId);
        Comparator<ProductSalesDTO> mostFirst = Comparator.comparingLong(ProductSalesDTO::unitsSold).reversed()
                .thenComparing(ProductSalesDTO::revenue, Comparator.reverseOrder())
                .thenComparing(ProductSalesDTO::productId);

        List<ProductSalesDTO> topSold = sales.stream()
                .filter(product -> product.unitsSold() > 0)
                .sorted(mostFirst)
                .limit(RANKING_SIZE)
                .toList();

        // Un producto ya eliminado no se puede mejorar ni retirar: solo entran los que siguen existiendo
        List<ProductSalesDTO> leastSold = sales.stream()
                .filter(product -> products.containsKey(product.productId()))
                .sorted(fewestFirst)
                .limit(RANKING_SIZE)
                .toList();

        return new SalesStatisticsDTO(StatisticsScope.VENDEDOR, topSold, leastSold);
    }

    /**
     * Mejor valorados de la plataforma: los 10 productos con más nota (solo entre los que llegan
     * al mínimo de valoraciones de la configuración), los 10 vendedores con mejor media y la
     * media de la plataforma, para compararlos.
     */
    public RatingStatisticsDTO getRatingStatistics() {
        int minimumReviews = configurationService.getMinimumReviewsForRanking();
        List<ProductRatingRow> productRows = statisticsRepository.findTopRatedProducts(minimumReviews, RANKING_SIZE);
        List<SellerRatingRow> sellerRows = statisticsRepository.findTopRatedSellers(RANKING_SIZE);

        // Un mismo vendedor puede salir en los dos rankings: su nombre se busca una sola vez
        Map<String, String> sellerNames = new HashMap<>();

        List<ProductRatingDTO> topProducts = productRows.stream()
                .map(row -> new ProductRatingDTO(
                        row.productId(),
                        row.productName(),
                        row.sellerId(),
                        sellerName(row.sellerId(), sellerNames),
                        round(row.rating()),
                        row.reviewCount()))
                .toList();
        List<SellerRatingDTO> topSellers = sellerRows.stream()
                .map(row -> new SellerRatingDTO(
                        row.sellerId(),
                        sellerName(row.sellerId(), sellerNames),
                        round(row.averageRating()),
                        row.ratedProducts()))
                .toList();
        Double platformAverage = statisticsRepository.findPlatformAverageRating();

        return new RatingStatisticsDTO(
                topProducts,
                topSellers,
                platformAverage == null ? null : round(platformAverage),
                minimumReviews);
    }

    private String sellerName(String sellerId, Map<String, String> sellerNames) {
        return sellerId == null ? null : sellerNames.computeIfAbsent(sellerId, this::resolveSellerName);
    }

    private double round(double rating) {
        return BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private ProductSalesDTO toDto(ProductSalesRow row, Product product, Map<String, String> sellerNames) {
        String sellerId = resolveSellerId(row, product);

        return new ProductSalesDTO(
                row.productId(),
                resolveProductName(row, product),
                sellerId,
                sellerName(sellerId, sellerNames),
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
