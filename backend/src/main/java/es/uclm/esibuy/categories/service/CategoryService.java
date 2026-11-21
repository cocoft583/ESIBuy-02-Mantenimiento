package es.uclm.esibuy.categories.service;

import java.util.List;

import es.uclm.esibuy.categories.dto.CategoryRequest;
import es.uclm.esibuy.categories.model.Category;
import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.common.exception.CategoryIdNotFoundException;
import es.uclm.esibuy.common.exception.CategoryInUseException;
import es.uclm.esibuy.common.exception.DuplicateCategoryException;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    // Ruta de ClientProfile.favoriteCategoryIds dentro de la colección de usuarios
    static final String FAVORITE_CATEGORIES_FIELD = "clientProfile.favoriteCategoryIds";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            MongoTemplate mongoTemplate) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryException();
        }
        return categoryRepository.save(new Category(name));
    }

    public Category update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryIdNotFoundException::new);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateCategoryException();
        }
        category.setName(name);
        return categoryRepository.save(category);
    }

    public void delete(String id) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryIdNotFoundException::new);
        if (productRepository.existsByCategoryIdsContaining(id)) {
            throw new CategoryInUseException();
        }
        categoryRepository.delete(category);

        // Primero se borra la categoría: si esto fallara, quedaría un id suelto en las
        // favoritas, que es inofensivo, en vez de perder favoritas de una categoría viva
        removeFromFavorites(id);
    }

    private void removeFromFavorites(String categoryId) {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where(FAVORITE_CATEGORIES_FIELD).is(categoryId)),
                new Update().pull(FAVORITE_CATEGORIES_FIELD, categoryId),
                User.class);
    }
}
