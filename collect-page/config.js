// Collect page configuration. No real credentials are committed here.
export default {
  // Your web SDK Key (by client mode). The page host must be registered for
  // this key — for local testing use localhost (see README).
  SDK_KEY: 'YOUR_SDK_KEY',

  // Optional hostname override sent to the SDK. Empty = window.location.origin.
  HOSTNAME: '',

  // DEV | UAT | PROD
  SDK_ENVIRONMENT: 'UAT',

  // Collection identification.
  USE_CASE: 'idpay-silent-flow-hybrid-poc',

  // Deep link that returns control to the native app.
  DEEP_LINK: 'silentflowhybrid://done',

  // Single wait (ms) after the prepare resolves, before returning to the
  // app. It's important to upload collect after the prepare,
  // so the page holds this window for it to leave.
  POST_PREPARE_WAIT_MS: 2500,
}
