package es.uclm.esibuy.wishlist.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.wishlist.model.Wishlist;

/**
 * Crea el índice por producto de las listas de favoritos. La creación automática de índices
 * está desactivada, así que sin esto buscar quién tiene un producto en favoritos recorrería
 * todas las listas cada vez que un producto cambia.
 */
@Component
public class WishlistIndexInitializer implements ApplicationRunner {

    static final String PRODUCT_IDS_INDEX = "wishlist_product_ids_idx";

    private final MongoTemplate mongoTemplate;

    public WishlistIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Index index = new Index()
                .on("productIds", Sort.Direction.ASC)
                .named(PRODUCT_IDS_INDEX);

        mongoTemplate.indexOps(Wishlist.class).ensureIndex(index);
    }
}
