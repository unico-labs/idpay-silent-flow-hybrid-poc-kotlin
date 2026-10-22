import config from './config.js'

const statusEl = document.getElementById('status')
const spinnerEl = document.getElementById('spinner')
const backButton = document.getElementById('backButton')
const debugEl = document.getElementById('debug')

// Custom Tabs: the identifier comes in the URL fragment and is stripped before
// the SDK bundle loads, so it never reaches server logs or the collected data.
// Preloaded WebView: there is no identifier yet — the app passes it to
// collect() when the user taps.
let externalUserId =
  new URLSearchParams(window.location.hash.slice(1)).get('externalUserId') ||
  new URLSearchParams(window.location.search).get('externalUserId') ||
  ''
history.replaceState(null, '', window.location.pathname)

// Exposed by the app only in the preloaded WebView. Without it (Custom Tabs),
// the page returns to the app via deep link.
const bridge = window.CollectBridge

function debug(message) {
  debugEl.textContent += `${message}\n`
}

function describe(error) {
  return `${error?.code ?? ''} ${error?.message ?? error}`.trim()
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
  // The browser may block automatic redirects to custom schemes without a
  // user gesture — the "back to app" button covers that case.
  window.location.href = deepLink(status)
}

async function prepare() {
  let sdk
  try {
    sdk = await import(config.SDK_BUNDLE_URL)
  } catch (error) {
    throw new Error(`SDK bundle failed to load: ${error?.message ?? error}`)
  }
  const { UnicoCheckBuilder, UnicoConfig, SDKEnvironmentTypes } = sdk

  const unicoConfig = new UnicoConfig()
    .setHostname(config.HOSTNAME || window.location.origin)
    .setHostKey(config.SDK_KEY)

  const camera = new UnicoCheckBuilder()
    .setEnvironment(SDKEnvironmentTypes[config.SDK_ENVIRONMENT])
    .build()

  await camera.initializeSDK(unicoConfig)
  debug('initializeSDK: ok')
  return camera
}

function report(status, message, detail = '') {
  if (bridge) bridge.onResult(status, detail)
  else finish(status, message)
}

// The SDK prepares as soon as the page loads: by the time collect() is called,
// only the send is left.
const prepared = prepare()
prepared.then(
  () => bridge?.onReady(),
  (error) => bridge?.onPrepareFailed(describe(error)),
)

let collecting = false

async function collect(id) {
  if (collecting) return
  collecting = true

  externalUserId = id
  if (!externalUserId) {
    report('error', 'externalUserId ausente.', 'missing externalUserId')
    return
  }
  debug(`externalUserId: ${externalUserId}`)
  debug(`useCase: ${config.USE_CASE}`)

  try {
    const camera = await prepared
    // Resolves once the collection has been sent.
    await camera.startSilentValidation(externalUserId, config.USE_CASE)
    debug('collection sent')
  } catch (error) {
    const detail = describe(error)
    debug(`silent validation failed: ${detail}`)
    report('error', `Não foi possível coletar os dados do dispositivo. (${detail})`, detail)
    return
  }

  report('ok', 'Tudo certo! Voltando ao app…')
}

window.collect = collect

if (externalUserId) collect(externalUserId)
