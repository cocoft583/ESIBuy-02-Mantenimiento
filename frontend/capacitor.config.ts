import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'es.uclm.esibuy',
  appName: 'ESIBuy',
  webDir: 'dist/frontend/browser',
  plugins: {
    // Las peticiones salen por la capa nativa: sin CORS ni bloqueo de cookies
    // de terceros del WebView, y la cookie de sesión vive en el almacén nativo.
    CapacitorHttp: {
      enabled: true
    }
  }
};

export default config;
