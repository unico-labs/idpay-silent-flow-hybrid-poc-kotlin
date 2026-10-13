import config from './config.js'

const statusEl = document.getElementById('status')
const spinnerEl = document.getElementById('spinner')
const backButton = document.getElementById('backButton')
const debugEl = document.getElementById('debug')

// The identifier arrives in the URL fragment and the URL is cleaned BEFORE
// the SDK bundle loads, so it never reaches server logs, Referer headers or
// the data collected from the page.
const externalUserId =
  new URLSearchParams(window.location.hash.slice(1)).get('externalUserId') ||
  new URLSearchParams(window.location.search).get('externalUserId') ||
  ''
history.replaceState(null, '', window.location.pathname)


// Transient network error while verifying the key — worth retrying.
const RETRYABLE_CODE = 73402
const MAX_ATTEMPTS = 3

function debug(message) {
  debugEl.textContent += `${message}\n`
}

function deepLink(status) {
  const q = new URLSearchParams({ externalUserId, status })
  return `${config.DEEP_LINK}?${q.toString()}`
}

function finish(status, message) {
  spinnerEl.style.display = 'none'
  statusEl.textContent = message
  backButton.href = deepLink(status)
  backButton.style.display = 'inline-block'
  if (status === 'ok') {
    // The browser may block automatic redirects to custom schemes without a
    // user gesture — the "back to app" button covers that case.
    window.location.href = deepLink('ok')
  }
}

async function prepareSilentCapture(sdk) {
  const { UnicoCheckBuilder, UnicoConfig, SDKEnvironmentTypes, SelfieCameraTypes } = sdk
  const hostname = config.HOSTNAME || window.location.origin
  const unicoConfig = new UnicoConfig()
    .setHostname(hostname)
    .setHostKey(config.SDK_KEY)

  const camera = new UnicoCheckBuilder()
    .setEnvironment(SDKEnvironmentTypes[config.SDK_ENVIRONMENT])
    .build()

  camera.setSilentInfo(externalUserId, config.USE_CASE)

  // prepare only — open() is never called, so no camera is shown. The SDK
  // sends the hashed externalUserId with the device collection in background.
  await camera.prepareSelfieCamera(unicoConfig, SelfieCameraTypes.SMART)
}

async function run() {
  if (!externalUserId) {
    finish('error', 'externalUserId ausente na URL.')
    return
  }
  debug(`externalUserId: ${externalUserId}`)
  debug(`useCase: ${config.USE_CASE}`)

  const sdk = await import('./UnicoCheckBuilder.min.js')

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      await prepareSilentCapture(sdk)
      debug('prepare: ok')
      // Single wait after the prepare: the uMonitor upload (sdk-build) fires
      // a few hundred ms after the prepare resolves, so the page holds this
      // window before returning to the app.
      debug(`holding ${config.POST_PREPARE_WAIT_MS}ms before returning`)
      await new Promise((resolve) => setTimeout(resolve, config.POST_PREPARE_WAIT_MS))

      finish('ok', 'Tudo certo! Voltando ao app…')
      return
    } catch (error) {
      const detail = `${error?.code ?? ''} ${error?.message ?? error}`.trim()
      debug(`prepare attempt ${attempt}: ${detail}`)
      if (error?.code !== RETRYABLE_CODE || attempt === MAX_ATTEMPTS) {
        finish('error', `Não foi possível validar o dispositivo. (${detail})`)
        return
      }
      await new Promise((resolve) => setTimeout(resolve, 1500))
    }
  }
}

run()
