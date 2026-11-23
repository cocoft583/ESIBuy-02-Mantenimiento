import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { FavoriteCategoriesService } from '../../../core/services/favorite-categories.service';
import { Category, ProductsService } from '../../../core/services/products.service';
import { httpErrorMessage } from '../../../core/utils/http-error-message';

const LOAD_ERROR = 'No se pudieron cargar las categorías. Inténtalo de nuevo.';
const SAVE_ERROR = 'No se pudieron guardar tus categorías favoritas. Inténtalo de nuevo.';
const SAVED = 'Tus categorías favoritas se han guardado.';

function sameSelection(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  return a.size === b.size && [...a].every((id) => b.has(id));
}

/**
 * Categorías favoritas del cliente (HU26). Los productos nuevos de estas categorías son los que
 * le generan alertas (HU28). Se guardan todas de una vez, porque el backend sustituye la lista.
 */
@Component({
  selector: 'app-favorite-categories',
  standalone: true,
  templateUrl: './favorite-categories.html',
  styleUrl: './favorite-categories.css',
})
export class FavoriteCategories implements OnInit {
  private readonly productsService = inject(ProductsService);
  private readonly favoritesService = inject(FavoriteCategoriesService);

  readonly categories = signal<Category[]>([]);
  readonly selected = signal<ReadonlySet<string>>(new Set());
  private readonly saved = signal<ReadonlySet<string>>(new Set());

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly dirty = computed(() => !sameSelection(this.selected(), this.saved()));
  readonly selectedLabel = computed(() => {
    const count = this.selected().size;
    if (count === 0) {
      return 'Ninguna seleccionada';
    }
    return `${count} ${count === 1 ? 'seleccionada' : 'seleccionadas'}`;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    forkJoin({
      categories: this.productsService.getCategories(),
      favorites: this.favoritesService.list(),
    }).subscribe({
      next: ({ categories, favorites }) => {
        const sorted = [...categories].sort((a, b) => a.name.localeCompare(b.name, 'es'));
        this.categories.set(sorted);
        this.applySaved(favorites);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.loadError.set(httpErrorMessage(error) ?? LOAD_ERROR);
      },
    });
  }

  isSelected(categoryId: string): boolean {
    return this.selected().has(categoryId);
  }

  toggle(categoryId: string): void {
    this.success.set(null);
    this.saveError.set(null);
    this.selected.update((current) => {
      const next = new Set(current);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
      return next;
    });
  }

  discard(): void {
    this.selected.set(new Set(this.saved()));
    this.saveError.set(null);
  }

  save(): void {
    if (!this.dirty() || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);
    this.success.set(null);
    // En el orden en que se muestran, para que la lista guardada sea siempre la misma
    const categoryIds = this.categories().map((category) => category.id).filter((id) => this.isSelected(id));

    this.favoritesService.replace(categoryIds).subscribe({
      next: (favorites) => {
        this.applySaved(favorites);
        this.saving.set(false);
        this.success.set(SAVED);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.saveError.set(httpErrorMessage(error) ?? SAVE_ERROR);
      },
    });
  }

  /** Una favorita de una categoría que ya no existe no se enseña ni se vuelve a guardar. */
  private applySaved(favorites: string[]): void {
    const existing = new Set(this.categories().map((category) => category.id));
    const saved = new Set(favorites.filter((id) => existing.has(id)));
    this.saved.set(saved);
    this.selected.set(new Set(saved));
  }
}
