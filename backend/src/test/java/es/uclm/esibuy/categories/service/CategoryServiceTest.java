package es.uclm.esibuy.categories.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;

import es.uclm.esibuy.categories.dto.CategoryRequest;
import es.uclm.esibuy.categories.model.Category;
import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.common.exception.CategoryIdNotFoundException;
import es.uclm.esibuy.common.exception.CategoryInUseException;
import es.uclm.esibuy.common.exception.DuplicateCategoryException;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, productRepository, mongoTemplate);
    }

    @Test
    void createsCategoryWithTrimmedName() {
        CategoryRequest request = request("  Papelería  ");
        when(categoryRepository.existsByNameIgnoreCase("Papelería")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        Category result = service.create(request);

        assertEquals("Papelería", result.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void rejectsDuplicateNameIgnoringCaseAndSurroundingSpaces() {
        CategoryRequest request = request("  eLeCtRóNiCa  ");
        when(categoryRepository.existsByNameIgnoreCase("eLeCtRóNiCa")).thenReturn(true);

        assertThrows(DuplicateCategoryException.class, () -> service.create(request));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updatesExistingCategoryWithoutTreatingItsOwnNameAsDuplicate() {
        Category category = new Category("category-1", "Electrónica");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Electrónica", "category-1")).thenReturn(false);
        when(categoryRepository.save(category)).thenReturn(category);

        Category result = service.update("category-1", request(" Electrónica "));

        assertSame(category, result);
        assertEquals("Electrónica", category.getName());
        verify(categoryRepository).save(category);
    }

    @Test
    void rejectsRenameToAnExistingCategoryName() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("hogar", "category-1")).thenReturn(true);

        assertThrows(DuplicateCategoryException.class, () -> service.update("category-1", request("hogar")));

        assertEquals("Moda", category.getName());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deletesCategoryWhenNoProductUsesIt() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdsContaining("category-1")).thenReturn(false);

        service.delete("category-1");

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("HU26-CP01: al borrar una categoría se quita de las favoritas de todos los clientes")
    void removesTheDeletedCategoryFromEveryCustomersFavorites() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdsContaining("category-1")).thenReturn(false);

        service.delete("category-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(User.class));

        assertEquals(new Document("clientProfile.favoriteCategoryIds", "category-1"), query.getValue().getQueryObject());
        assertEquals(
                new Document("$pull", new Document("clientProfile.favoriteCategoryIds", "category-1")),
                update.getValue().getUpdateObject());
    }

    @Test
    @DisplayName("HU26-CP02: las favoritas se limpian después de borrar la categoría, no antes")
    void removesTheCategoryFromFavoritesOnlyAfterDeletingIt() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdsContaining("category-1")).thenReturn(false);

        service.delete("category-1");

        InOrder inOrder = inOrder(categoryRepository, mongoTemplate);
        inOrder.verify(categoryRepository).delete(category);
        inOrder.verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    @DisplayName("HU26-CP03: si la categoría no se puede borrar, las favoritas no se tocan")
    void keepsTheFavoritesWhenTheCategoryCannotBeDeleted() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdsContaining("category-1")).thenReturn(true);

        assertThrows(CategoryInUseException.class, () -> service.delete("category-1"));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsDeletingCategoryUsedByAProduct() {
        Category category = new Category("category-1", "Moda");
        when(categoryRepository.findById("category-1")).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryIdsContaining("category-1")).thenReturn(true);

        assertThrows(CategoryInUseException.class, () -> service.delete("category-1"));

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void rejectsUpdatesAndDeletesForUnknownIds() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(CategoryIdNotFoundException.class, () -> service.update("missing", request("Hogar")));
        assertThrows(CategoryIdNotFoundException.class, () -> service.delete("missing"));

        verify(productRepository, never()).existsByCategoryIdsContaining("missing");
    }

    private CategoryRequest request(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        return request;
    }
}
