import {
  UnicoCheckBuilder,
  UnicoConfig,
  SDKEnvironmentTypes,
  SelfieCameraTypes,
} from './UnicoCheckBuilder.min.js'
import config from './config.js'

const statusEl = document.getElementById('status')
const spinnerEl = document.getElementById('spinner')
const backButton = document.getElementById('backButton')
const debugEl = document.getElementById('debug')

const params = new URLSearchParams(window.location.search)
const externalUserId = params.get('externalUserId') || ''

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

async function prepareSilentCapture() {
  const hostname = config.HOSTNAME || window.location.origin
  const unicoConfig = new UnicoConfig()
    .setHostname(hostname)
    .setHostKey(config.SDK_KEY)

  const camera = new UnicoCheckBuilder()
    .setEnvironment(SDKEnvironmentTypes[config.SDK_ENVIRONMENT])
    .setModelsPath('/models')
    .setResourceDirectory('/resources')
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

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      await prepareSilentCapture()
      debug('prepare: ok')
      // Grace period so the fire-and-forget upload leaves before the page
      // closes.
      await new Promise((resolve) => setTimeout(resolve, config.GRACE_MS))
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
