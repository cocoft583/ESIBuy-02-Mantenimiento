package es.uclm.esibuy.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import es.uclm.esibuy.wishlist.model.Wishlist;

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(WishlistIndexInitializer.class)
class WishlistIndexInitializerTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private WishlistIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(Wishlist.class);
    }

    private List<IndexInfo> indexes() {
        return mongoTemplate.indexOps(Wishlist.class).getIndexInfo();
    }

    @Test
    @DisplayName("HU27-CP17: crea el índice por producto de las listas de favoritos")
    void createsTheProductIndex() {
        initializer.run(new DefaultApplicationArguments());

        assertThat(indexes()).anySatisfy(index -> {
            assertThat(index.getName()).isEqualTo("wishlist_product_ids_idx");
            assertThat(index.getIndexFields()).extracting(field -> field.getKey()).containsExactly("productIds");
        });
    }

    @Test
    @DisplayName("HU27-CP18: arrancar dos veces no duplica ni rompe el índice")
    void runningTwiceKeepsASingleIndex() {
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(indexes()).filteredOn(index -> "wishlist_product_ids_idx".equals(index.getName())).hasSize(1);
    }

    @Test
    @DisplayName("HU27-CP19: buscar por producto usa el índice y no recorre toda la colección")
    void searchingByProductUsesTheIndex() {
        initializer.run(new DefaultApplicationArguments());
        mongoTemplate.save(new Wishlist("u1", "Lista"));

        String plan = mongoTemplate.getCollection("wishlists")
                .find(new org.bson.Document("productIds", "p1"))
                .explain()
                .toJson();

        assertThat(plan).contains("IXSCAN").doesNotContain("COLLSCAN");
    }
}
