package es.uclm.esibuy.users.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Lista completa de categorías favoritas: sustituye a la que había guardada.
 * Una lista vacía quita todas las favoritas.
 */
public record FavoriteCategoriesRequest(
        @NotNull(message = "La lista de categorías es obligatoria") List<String> categoryIds) {
}
