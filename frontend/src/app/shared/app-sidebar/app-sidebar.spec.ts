import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AlertsService } from '../../core/services/alerts.service';
import { AppSidebarComponent } from './app-sidebar';

function simulateViewport(mobile: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => ({
      matches: mobile && query.includes('max-width: 760px'),
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe('AppSidebarComponent', () => {
  let fixture: ComponentFixture<AppSidebarComponent>;
  let component: AppSidebarComponent;
  let emitted: boolean[];

  function create(): void {
    fixture = TestBed.createComponent(AppSidebarComponent);
    component = fixture.componentInstance;
    component.variant = 'client';
    emitted = [];
    component.collapsedChange.subscribe((value) => emitted.push(value));
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AppSidebarComponent],
      providers: [
        provideRouter([]),
        { provide: AlertsService, useValue: { unreadCount: signal(2).asReadonly(), startWatching: vi.fn() } },
      ],
    });
  });

  afterEach(() => localStorage.clear());

  it('el menú de administración enlaza las estadísticas', () => {
    create();
    component.variant = 'admin';
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/admin/estadisticas"]') as HTMLAnchorElement | null;
    expect(link?.textContent?.trim()).toBe('Estadísticas');
  });

  it('el menú del vendedor enlaza sus estadísticas', () => {
    create();
    component.variant = 'seller';
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/seller/estadisticas"]') as HTMLAnchorElement | null;
    expect(link?.textContent?.trim()).toBe('Estadísticas');
    expect(link?.getAttribute('aria-disabled')).toBeNull();
  });

  it('el menú del cliente enlaza su bandeja de alertas con la campana y el contador', () => {
    create();
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/client/alerts"]') as HTMLAnchorElement | null;
    expect(link?.querySelector('app-alerts-bell')).not.toBeNull();
    expect(link?.querySelector('.alerts-badge')?.textContent?.trim()).toBe('2');
    expect(link?.textContent).toContain('Alertas');
  });

  it('los menús del vendedor y del administrador no tienen campana de alertas', () => {
    create();
    component.variant = 'seller';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-alerts-bell')).toBeNull();
  });

  it('en escritorio recupera la barra plegada que se guardó', () => {
    simulateViewport(false);
    localStorage.setItem('client-sidebar-collapsed', 'true');
    create();

    fixture.detectChanges();

    expect(emitted).toEqual([true]);
  });

  it('en móvil la barra se muestra desplegada aunque se guardara plegada', () => {
    simulateViewport(true);
    localStorage.setItem('client-sidebar-collapsed', 'true');
    create();

    fixture.detectChanges();

    expect(emitted).toEqual([false]);
  });

  it('en móvil no muestra el botón de plegar la barra', () => {
    simulateViewport(true);
    create();

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.app-sidebar-toggle')).toBeNull();
  });

  it('en escritorio muestra el botón de plegar la barra', () => {
    simulateViewport(false);
    create();

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.app-sidebar-toggle')).not.toBeNull();
  });
});
