import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NetworkStatus } from '../../core/services/network-status';
import { OfflineBanner } from './offline-banner';

describe('OfflineBanner', () => {
  const online = signal(true);
  let fixture: ComponentFixture<OfflineBanner>;

  beforeEach(() => {
    online.set(true);
    TestBed.configureTestingModule({
      imports: [OfflineBanner],
      providers: [{ provide: NetworkStatus, useValue: { online: online.asReadonly() } }],
    });
    fixture = TestBed.createComponent(OfflineBanner);
  });

  it('con red no muestra nada', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });

  it('sin red avisa de que no hay conexión y de que se recargará al volver', () => {
    online.set(false);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(banner.textContent).toContain('Sin conexión');
    expect(banner.textContent).toContain('cuando vuelva');
  });
});
