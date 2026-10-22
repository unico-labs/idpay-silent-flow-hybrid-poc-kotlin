// Collect page configuration. No real credentials are committed here.
export default {
  // Your web SDK Key (by client mode). The page host must be registered for
  // this key — for local testing use localhost (see README).
  SDK_KEY: 'YOUR_SDK_KEY',

  // Optional hostname override sent to the SDK. Empty = window.location.origin.
  HOSTNAME: '',

  // Web SDK bundle. Real integrations should serve it from their own
  // infrastructure (versioned, long-lived cache) instead of a public CDN.
  SDK_BUNDLE_URL: 'https://cdn.jsdelivr.net/npm/unico-webframe@3.28.2/obfuscated/dist/UnicoCheck.esm.js',

  // DEV | UAT | PROD
  SDK_ENVIRONMENT: 'UAT',

  // Collection identification.
  USE_CASE: 'idpay-silent-flow-hybrid-poc',

  // Deep link that returns control to the native app.
  DEEP_LINK: 'silentflowhybrid://done',
}
