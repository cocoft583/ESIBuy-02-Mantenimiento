import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

interface FavoriteCategoriesResponse {
  categoryIds?: string[] | null;
}

const API = '/api/users/me/favorite-categories';

/** Categorías favoritas del cliente de la sesión (HU26): de ellas salen sus alertas de productos nuevos. */
@Injectable({ providedIn: 'root' })
export class FavoriteCategoriesService {
  private readonly http = inject(HttpClient);

  list(): Observable<string[]> {
    return this.http
      .get<FavoriteCategoriesResponse>(API, { withCredentials: true })
      .pipe(map((response) => response.categoryIds ?? []));
  }

  /** Sustituye la lista entera: una lista vacía quita todas las favoritas. */
  replace(categoryIds: string[]): Observable<string[]> {
    return this.http
      .put<FavoriteCategoriesResponse>(API, { categoryIds }, { withCredentials: true })
      .pipe(map((response) => response.categoryIds ?? []));
  }
}
