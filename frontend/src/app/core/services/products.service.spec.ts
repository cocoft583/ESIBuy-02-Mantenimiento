import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogPage, ProductsService } from '../../core/services/products.service';

describe('ProductsService', () => {
  let service: ProductsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  it('should load available categories from the backend', () => {
    const categories = [{ id: 'category-1', name: 'Electrónica' }];

    service.getCategories().subscribe((result) => expect(result).toEqual(categories));

    const request = httpTesting.expectOne('/api/categorias');
    expect(request.request.method).toBe('GET');
    request.flush(categories);
  });

  it('should submit product data to the product creation endpoint', () => {
    const product = {
      name: 'Auriculares',
      categoryIds: ['category-1'],
      price: 19.99,
      available: true,
    };

    service.createProduct(product).subscribe((result) => expect(result.id).toBe('product-1'));

    const request = httpTesting.expectOne('/api/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(product);
    request.flush({ id: 'product-1' });
  });

  it('should load product details for the edit form', () => {
    const product = {
      id: 'product-1',
      name: 'Auriculares',
      categoryIds: ['category-1'],
      stock: 3,
      description: 'Inalámbricos',
      price: 19.99,
      discount: null,
      premiumDiscount: 10,
      imageUrl: null,
      available: true,
      sellerId: 'seller-1',
    };

    service.getProduct('product-1').subscribe((result) => expect(result).toEqual(product));

    const request = httpTesting.expectOne('/api/products/product-1');
    expect(request.request.method).toBe('GET');
    request.flush(product);
  });

  it('should load available products for the authenticated client catalog', () => {
    const products = [{
      id: 'product-1',
      name: 'Auriculares',
      categoryIds: ['category-1'],
      stock: 2,
      description: null,
      price: 19.99,
      discount: null,
      premiumDiscount: null,
      imageUrl: null,
      available: true,
      sellerId: 'seller-1',
    }];

    service.getAvailableProducts().subscribe((result) => expect(result).toEqual(products));

    const request = httpTesting.expectOne('/api/catalog/products');
    expect(request.request.method).toBe('GET');
    request.flush(products);
  });

  it('should send product edits to the update endpoint', () => {
    const payload = {
      name: 'Auriculares nuevos',
      categoryIds: ['category-1'],
      price: 25,
      available: true,
    };

    service.updateProduct('product-1', payload).subscribe((result) => expect(result.id).toBe('product-1'));

    const request = httpTesting.expectOne('/api/products/product-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 'product-1' });
  });

  it('asks the catalog for one page with its filters and sort', () => {
    let page: CatalogPage | undefined;

    service.getPublicCatalog({
      query: 'auriculares',
      onlyOffers: true,
      sort: 'price-asc',
      page: 2,
      size: 12,
    }).subscribe((result) => (page = result));

    const request = httpTesting.expectOne((req) => req.url === '/api/products');
    expect(request.request.params.get('query')).toBe('auriculares');
    expect(request.request.params.get('onlyOffers')).toBe('true');
    expect(request.request.params.get('sort')).toBe('price-asc');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('12');
    request.flush({
      content: [{ id: 'p-1', name: 'Auriculares', imageUrl: null }],
      page: 2,
      size: 12,
      totalElements: 30,
      totalPages: 3,
      maxPrice: 200,
    });

    expect(page?.content.map((product) => product.id)).toEqual(['p-1']);
    expect(page?.totalPages).toBe(3);
  });

  it('does not send the recommended sort or a disabled offers filter', () => {
    service.getPublicCatalog({ sort: 'recommended', onlyOffers: false, page: 0, size: 12 }).subscribe();

    const request = httpTesting.expectOne((req) => req.url === '/api/products');
    expect(request.request.params.has('sort')).toBe(false);
    expect(request.request.params.has('onlyOffers')).toBe(false);
    request.flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, maxPrice: 0 });
  });
});
