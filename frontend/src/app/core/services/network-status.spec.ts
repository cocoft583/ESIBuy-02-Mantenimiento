import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { NETWORK_SOURCE, NetworkSource, NetworkStatus } from './network-status';

describe('NetworkStatus', () => {
  let connectedAtStart: boolean;
  let emitChange: (connected: boolean) => void;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  const source: NetworkSource = {
    isConnected: () => Promise.resolve(connectedAtStart),
    onChange: (listener) => { emitChange = listener; },
  };

  async function startService(): Promise<NetworkStatus> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: NETWORK_SOURCE, useValue: source }],
    });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/client/orders');
    navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    const service = TestBed.inject(NetworkStatus);
    await service.start();
    return service;
  }

  beforeEach(() => {
    connectedAtStart = true;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('con red al abrir la app está en línea', async () => {
    const service = await startService();

    expect(service.online()).toBe(true);
  });

  it('sin red al abrir la app está sin conexión', async () => {
    connectedAtStart = false;

    const service = await startService();

    expect(service.online()).toBe(false);
  });

  it('al perder la red pasa a sin conexión', async () => {
    const service = await startService();

    emitChange(false);

    expect(service.online()).toBe(false);
  });

  it('al volver la red recarga la página en la que estaba', async () => {
    const service = await startService();
    emitChange(false);

    emitChange(true);
    await Promise.resolve();

    expect(service.online()).toBe(true);
    expect(navigateByUrl).toHaveBeenLastCalledWith('/client/orders');
  });

  it('si no se había perdido la red no recarga nada', async () => {
    await startService();

    emitChange(true);
    await Promise.resolve();

    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
