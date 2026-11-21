package es.uclm.esibuy.users.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.users.dto.FavoriteCategoriesRequest;
import es.uclm.esibuy.users.dto.FavoriteCategoriesResponse;
import es.uclm.esibuy.users.service.FavoriteCategoryService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me/favorite-categories")
public class FavoriteCategoryController {

    private final FavoriteCategoryService favoriteCategoryService;

    public FavoriteCategoryController(FavoriteCategoryService favoriteCategoryService) {
        this.favoriteCategoryService = favoriteCategoryService;
    }

    @GetMapping
    public ResponseEntity<FavoriteCategoriesResponse> list(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new FavoriteCategoriesResponse(favoriteCategoryService.list(principal.id())));
    }

    @PutMapping
    public ResponseEntity<FavoriteCategoriesResponse> replace(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody FavoriteCategoriesRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new FavoriteCategoriesResponse(
                favoriteCategoryService.replace(principal.id(), request.categoryIds())));
    }
}
