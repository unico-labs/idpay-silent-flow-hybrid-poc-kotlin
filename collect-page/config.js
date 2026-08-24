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

  // Grace period (ms) after the prepare for the SDK fire-and-forget upload to
  // leave the device before the page redirects back to the app.
  GRACE_MS: 5000,
}
