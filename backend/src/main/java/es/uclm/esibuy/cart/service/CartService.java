package es.uclm.esibuy.cart.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.cart.dto.AddCartItemDTO;
import es.uclm.esibuy.cart.dto.CartDTO;
import es.uclm.esibuy.cart.dto.CartItemDTO;
import es.uclm.esibuy.cart.dto.UpdateCartItemQuantityDTO;
import es.uclm.esibuy.cart.model.Cart;
import es.uclm.esibuy.cart.model.CartItem;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.common.exception.ProductNotFoundException;
import es.uclm.esibuy.common.exception.ProductoExclusivoPremiumException;
import es.uclm.esibuy.common.exception.ProductoNoDisponibleException;
import es.uclm.esibuy.common.exception.StockInsuficienteException;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.products.service.ProductPricing;
import es.uclm.esibuy.products.service.ProductPricing.Discount;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.repository.UserRepository;

@Service
public class CartService {

    private static final String CUSTOMER_ID_REQUIRED = "El identificador del cliente es obligatorio";

    private static final String PRODUCT_ID_REQUIRED = "El identificador del producto es obligatorio";

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    @Autowired
    public CartService(
            ProductRepository productRepository,
            CartRepository cartRepository,
            UserRepository userRepository) {

        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    public CartService(
            ProductRepository productRepository,
            CartRepository cartRepository) {

        this(productRepository, cartRepository, null);
    }

    public CartDTO addItem(String customerId, AddCartItemDTO request) {
        validateRequest(customerId, request);

        Product product = productRepository.findById(request.productId())
                .orElseThrow(ProductNotFoundException::new);

        validateProductAvailability(product);
        if (product.isPremiumOnly() && !isPremiumCustomer(customerId)) {
            throw new ProductoExclusivoPremiumException();
        }

        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> new Cart(customerId));

        validateStock(cart, product, request.quantity());

        boolean isPremium = isPremiumCustomer(customerId);
        BigDecimal effectivePrice = ProductPricing.effectivePrice(product, isPremium);

        cart.addOrIncrementItem(
                product.getId(),
                product.getName(),
                effectivePrice,
                request.quantity(),
                product.getImageUrl());

        return toCartDTO(cartRepository.save(cart));
    }

    public CartDTO getCart(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException(CUSTOMER_ID_REQUIRED);
        }

        return cartRepository.findByCustomerId(customerId)
                .map(this::toCartDTO)
                .orElseGet(() -> toCartDTO(new Cart(customerId)));
    }

    public CartDTO updateItemQuantity(
            String customerId,
            String productId,
            UpdateCartItemQuantityDTO request) {

        validateUpdateRequest(customerId, productId, request);

        Cart cart = getExistingCart(customerId);
        CartItem item = findCartItem(cart, productId);

        if (request.quantity() > item.getQuantity()) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(ProductNotFoundException::new);

            validateProductAvailability(product);
            validateStock(product, request.quantity());
        }

        cart.updateItemQuantity(productId, request.quantity());

        return toCartDTO(cartRepository.save(cart));
    }

    public CartDTO removeItem(String customerId, String productId) {
        validateCustomerAndProduct(customerId, productId);

        Cart cart = getExistingCart(customerId);

        if (!cart.removeItem(productId)) {
            throw new ProductNotFoundException();
        }

        return toCartDTO(cartRepository.save(cart));
    }

    private void validateRequest(String customerId, AddCartItemDTO request) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException(CUSTOMER_ID_REQUIRED);
        }

        if (request == null || request.productId() == null || request.productId().isBlank()) {
            throw new IllegalArgumentException(PRODUCT_ID_REQUIRED);
        }

        if (request.quantity() == null || request.quantity() < 1) {
            throw new IllegalArgumentException("La cantidad debe ser como mínimo 1");
        }
    }

    private void validateUpdateRequest(
            String customerId,
            String productId,
            UpdateCartItemQuantityDTO request) {

        validateCustomerAndProduct(customerId, productId);

        if (request == null || request.quantity() == null || request.quantity() < 0) {
            throw new IllegalArgumentException("La cantidad debe ser como mínimo 0");
        }
    }

    private void validateCustomerAndProduct(String customerId, String productId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException(CUSTOMER_ID_REQUIRED);
        }

        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException(PRODUCT_ID_REQUIRED);
        }
    }

    private Cart getExistingCart(String customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private CartItem findCartItem(Cart cart, String productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(ProductNotFoundException::new);
    }

    private void validateProductAvailability(Product product) {
        if (!product.isAvailable()) {
            throw new ProductoNoDisponibleException();
        }
    }

    private void validateStock(Cart cart, Product product, int requestedQuantity) {
        int currentQuantity = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(product.getId()))
                .mapToInt(CartItem::getQuantity)
                .findFirst()
                .orElse(0);

        validateStock(product, (long) currentQuantity + requestedQuantity);
    }

    private void validateStock(Product product, long requestedQuantity) {
        if (requestedQuantity > product.getStock()) {
            throw new StockInsuficienteException();
        }
    }

    private CartDTO toCartDTO(Cart cart) {
        boolean isPremium = isPremiumCustomer(cart.getCustomerId());

        List<CartItemDTO> items = cart.getItems().stream()
                .map(item -> toCartItemDTO(item, isPremium))
                .toList();

        BigDecimal totalBeforeDiscount = items.stream()
                .map(item -> (item.originalPrice() != null
                        ? item.originalPrice()
                        : item.unitPrice())
                        .multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // El total sale de los precios de ahora, no del importe guardado al añadir
        BigDecimal totalPrice = items.stream()
                .map(CartItemDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = totalBeforeDiscount.compareTo(totalPrice) > 0
                ? totalBeforeDiscount.subtract(totalPrice)
                : BigDecimal.ZERO;

        return new CartDTO(
                cart.getId(),
                cart.getCustomerId(),
                items,
                totalPrice,
                totalBeforeDiscount,
                totalDiscount);
    }

    private CartItemDTO toCartItemDTO(CartItem item, boolean isPremium) {
        // Sin producto que consultar, la línea conserva el precio guardado al añadirla
        ItemPricing storedPricing = new ItemPricing(item.getUnitPrice(), item.getUnitPrice(), Discount.none());

        if (item.getProductId() == null) {
            return createCartItemDTO(item, item.getImageUrl(), null, null, storedPricing, null);
        }

        Product product = productRepository.findById(item.getProductId()).orElse(null);
        if (product == null) {
            return createCartItemDTO(item, item.getImageUrl(), null, null, storedPricing, null);
        }

        String imageUrl = (item.getImageUrl() != null && !item.getImageUrl().isBlank())
                ? item.getImageUrl()
                : product.getImageUrl();
        String sellerId = product.getSellerId();
        String sellerName = resolveSellerName(sellerId);

        // El precio de la línea es el que tiene el producto ahora para este tipo de cliente
        // (si el producto no tiene precio, por datos antiguos, se conserva el guardado)
        ItemPricing currentPricing = product.getPrice() == null
                ? storedPricing
                : new ItemPricing(
                        ProductPricing.effectivePrice(product, isPremium),
                        product.getPrice(),
                        ProductPricing.resolveDiscount(product, isPremium));

        return createCartItemDTO(
                item,
                imageUrl,
                sellerId,
                sellerName,
                currentPricing,
                product.getStock());
    }

    private CartItemDTO createCartItemDTO(
            CartItem item,
            String imageUrl,
            String sellerId,
            String sellerName,
            ItemPricing pricing,
            Integer availableStock) {
        return new CartItemDTO(
                item.getProductId(),
                item.getName(),
                pricing.unitPrice(),
                item.getQuantity(),
                pricing.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                imageUrl,
                sellerId,
                sellerName,
                pricing.originalPrice(),
                pricing.discount().percentage(),
                pricing.discount().premiumApplied(),
                availableStock);
    }

    private record ItemPricing(BigDecimal unitPrice, BigDecimal originalPrice, Discount discount) {
    }

    private String resolveSellerName(String sellerId) {
        if (sellerId == null || sellerId.isBlank() || userRepository == null) {
            return null;
        }

        return userRepository.findById(sellerId)
                .map(user -> {
                    if (user.getSellerProfile() != null
                            && user.getSellerProfile().getBusinessName() != null
                            && !user.getSellerProfile().getBusinessName().isBlank()) {

                        return user.getSellerProfile().getBusinessName();
                    }

                    String firstName = user.getFirstName() != null ? user.getFirstName() : "";
                    String lastName = user.getLastName() != null ? user.getLastName() : "";

                    String fullName = (firstName + " " + lastName).trim();

                    return !fullName.isBlank()
                            ? fullName
                            : "Vendedor " + sellerId;
                })
                .orElse("Vendedor " + sellerId);
    }

    private boolean isPremiumCustomer(String customerId) {
        if (customerId == null || userRepository == null) {
            return false;
        }

        return userRepository.findById(customerId)
                .map(user -> user.getClientProfile() != null
                        && CustomerType.PREMIUM.equals(
                                user.getClientProfile().getCustomerType()))
                .orElse(false);
    }
}
