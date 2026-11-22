import { resolveBuildApiUrl } from './build-api-url';

// Lo sustituye el compilador con --define. Si no se pasa, la app usa el servidor desplegado.
//   npx ng build --configuration mobile --define "ESIBUY_API_URL='https://<tu-tunel>.trycloudflare.com'"
declare const ESIBUY_API_URL: string | undefined;

export const environment = {
  production: true,
  apiUrl: resolveBuildApiUrl(
    typeof ESIBUY_API_URL === 'undefined' ? undefined : ESIBUY_API_URL,
    'https://esibuy-2627.onrender.com',
  ),
};
