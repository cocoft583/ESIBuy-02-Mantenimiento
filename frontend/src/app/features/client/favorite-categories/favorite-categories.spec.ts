import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FavoriteCategoriesService } from '../../../core/services/favorite-categories.service';
import { Category, ProductsService } from '../../../core/services/products.service';
import { FavoriteCategories } from './favorite-categories';

const CATEGORIES: Category[] = [
  { id: 'cat-moda', name: 'Moda' },
  { id: 'cat-electronica', name: 'Electrónica' },
  { id: 'cat-hogar', name: 'Hogar' },
];

describe('FavoriteCategories', () => {
  let fixture: ComponentFixture<FavoriteCategories>;
  let element: HTMLElement;
  let getCategories: ReturnType<typeof vi.fn>;
  let list: ReturnType<typeof vi.fn>;
  let replace: ReturnType<typeof vi.fn>;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(FavoriteCategories);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function options(): { name: string; checked: boolean }[] {
    return Array.from(element.querySelectorAll<HTMLLabelElement>('.category-option')).map((label) => ({
      name: label.textContent?.trim() ?? '',
      checked: (label.querySelector('input') as HTMLInputElement).checked,
    }));
  }

  function toggle(name: string): void {
    const label = Array.from(element.querySelectorAll<HTMLLabelElement>('.category-option'))
      .find((option) => option.textContent?.trim() === name);
    (label?.querySelector('input') as HTMLInputElement).click();
  }

  function saveButton(): HTMLButtonElement {
    return element.querySelector('.save-favorites') as HTMLButtonElement;
  }

  beforeEach(async () => {
    getCategories = vi.fn(() => of(CATEGORIES));
    list = vi.fn(() => of(['cat-hogar']));
    replace = vi.fn((ids: string[]) => of(ids));

    await TestBed.configureTestingModule({
      imports: [FavoriteCategories],
      providers: [
        { provide: ProductsService, useValue: { getCategories } },
        { provide: FavoriteCategoriesService, useValue: { list, replace } },
      ],
    }).compileComponents();
  });

  it('shows every category in alphabetical order with the favorites checked', async () => {
    await render();

    expect(options()).toEqual([
      { name: 'Electrónica', checked: false },
      { name: 'Hogar', checked: true },
      { name: 'Moda', checked: false },
    ]);
    expect(element.textContent).toContain('1 seleccionada');
  });

  it('does not show favorites of categories that no longer exist', async () => {
    list.mockReturnValue(of(['cat-hogar', 'cat-borrada']));

    await render();

    expect(options().filter((option) => option.checked).map((option) => option.name)).toEqual(['Hogar']);
    expect(element.textContent).toContain('1 seleccionada');
  });

  it('only lets the client save when something has changed', async () => {
    await render();

    expect(saveButton().disabled).toBe(true);
    toggle('Moda');
    await settle();
    expect(saveButton().disabled).toBe(false);
    toggle('Moda');
    await settle();
    expect(saveButton().disabled).toBe(true);
  });

  it('saves the whole list of favorites and confirms it', async () => {
    await render();

    toggle('Electrónica');
    toggle('Hogar');
    await settle();
    saveButton().click();
    await settle();

    expect(replace).toHaveBeenCalledWith(['cat-electronica']);
    expect(element.querySelector('.success-message')?.textContent).toContain('Tus categorías favoritas se han guardado');
    expect(saveButton().disabled).toBe(true);
    expect(options().find((option) => option.name === 'Electrónica')?.checked).toBe(true);
  });

  it('lets the client remove every favorite', async () => {
    await render();

    toggle('Hogar');
    await settle();
    saveButton().click();
    await settle();

    expect(replace).toHaveBeenCalledWith([]);
    expect(element.textContent).toContain('Ninguna seleccionada');
  });

  it('discards the changes that were not saved', async () => {
    await render();

    toggle('Moda');
    await settle();
    (element.querySelector('.discard-favorites') as HTMLButtonElement).click();
    await settle();

    expect(options().filter((option) => option.checked).map((option) => option.name)).toEqual(['Hogar']);
    expect(replace).not.toHaveBeenCalled();
  });

  it('keeps the selection and shows an error when it cannot be saved', async () => {
    replace.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await render();

    toggle('Moda');
    await settle();
    saveButton().click();
    await settle();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(options().find((option) => option.name === 'Moda')?.checked).toBe(true);
    expect(saveButton().disabled).toBe(false);
  });

  it('shows an error and retries when the categories cannot be loaded', async () => {
    getCategories.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

    await render();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.querySelector('.category-option')).toBeNull();
    (element.querySelector('.retry-button') as HTMLButtonElement).click();
    await settle();

    expect(getCategories).toHaveBeenCalledTimes(2);
    expect(options()).toHaveLength(3);
  });

  it('explains that there are no categories yet', async () => {
    getCategories.mockReturnValue(of([]));
    list.mockReturnValue(of([]));

    await render();

    expect(element.querySelector('.favorites-empty')?.textContent).toContain('Todavía no hay categorías');
    expect(element.querySelector('.save-favorites')).toBeNull();
  });
});
