import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'es.uclm.esibuy',
  appName: 'ESIBuy',
  webDir: 'dist/frontend/browser',
  ios: {
    contentInset: 'always'
  }
};

export default config;
