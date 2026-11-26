import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppPlatform } from './app-platform';
import { BACK_BUTTON_SOURCE, BackButton, BackButtonSource } from './back-button';

describe('BackButton', () => {
  let nativeApp: boolean;
  let pressBack: ((canGoBack: boolean) => void) | null;
  let source: {
    onBackButton: ReturnType<typeof vi.fn<BackButtonSource['onBackButton']>>;
    goBack: ReturnType<typeof vi.fn<BackButtonSource['goBack']>>;
    minimize: ReturnType<typeof vi.fn<BackButtonSource['minimize']>>;
  };

  function start(): BackButton {
    const service = TestBed.inject(BackButton);
    service.start();
    return service;
  }

  beforeEach(() => {
    nativeApp = true;
    pressBack = null;
    source = {
      onBackButton: vi.fn((listener) => {
        pressBack = listener;
      }),
      goBack: vi.fn(),
      minimize: vi.fn(async () => undefined),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppPlatform, useValue: { isNativeApp: () => nativeApp } },
        { provide: BACK_BUTTON_SOURCE, useValue: source },
      ],
    });
  });

  it('en el navegador no escucha el botón atrás', () => {
    nativeApp = false;
    start();

    expect(source.onBackButton).not.toHaveBeenCalled();
  });

  it('en la app escucha el botón atrás una sola vez aunque se arranque varias veces', () => {
    const service = start();
    service.start();

    expect(source.onBackButton).toHaveBeenCalledOnce();
  });

  it('si hay página anterior, vuelve a ella en lugar de cerrar la app', () => {
    start();
    pressBack?.(true);

    expect(source.goBack).toHaveBeenCalledOnce();
    expect(source.minimize).not.toHaveBeenCalled();
  });

  it('si no hay página anterior, minimiza la app sin cerrar la sesión', () => {
    start();
    pressBack?.(false);

    expect(source.minimize).toHaveBeenCalledOnce();
    expect(source.goBack).not.toHaveBeenCalled();
  });
});
