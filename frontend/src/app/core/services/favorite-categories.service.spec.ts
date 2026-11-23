import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FavoriteCategoriesService } from './favorite-categories.service';

describe('FavoriteCategoriesService', () => {
  let service: FavoriteCategoriesService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FavoriteCategoriesService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('asks for the favorite categories of the session client', () => {
    let favorites: string[] | undefined;
    service.list().subscribe((value) => (favorites = value));

    const request = httpTesting.expectOne('/api/users/me/favorite-categories');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ categoryIds: ['cat-1', 'cat-2'] });

    expect(favorites).toEqual(['cat-1', 'cat-2']);
  });

  it('treats a missing list as no favorites', () => {
    let favorites: string[] | undefined;
    service.list().subscribe((value) => (favorites = value));

    httpTesting.expectOne('/api/users/me/favorite-categories').flush({});

    expect(favorites).toEqual([]);
  });

  it('replaces the whole list of favorites and returns the one saved', () => {
    let favorites: string[] | undefined;
    service.replace(['cat-2']).subscribe((value) => (favorites = value));

    const request = httpTesting.expectOne('/api/users/me/favorite-categories');
    expect(request.request.method).toBe('PUT');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ categoryIds: ['cat-2'] });
    request.flush({ categoryIds: ['cat-2'] });

    expect(favorites).toEqual(['cat-2']);
  });
});
