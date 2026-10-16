import config from './config.js'

const statusEl = document.getElementById('status')
const spinnerEl = document.getElementById('spinner')
const backButton = document.getElementById('backButton')
const debugEl = document.getElementById('debug')

// The identifier comes in the URL fragment and is stripped before the SDK
// bundle loads, so it never reaches server logs or the collected data.
const externalUserId =
  new URLSearchParams(window.location.hash.slice(1)).get('externalUserId') ||
  new URLSearchParams(window.location.search).get('externalUserId') ||
  ''
history.replaceState(null, '', window.location.pathname)

function debug(message) {
  debugEl.textContent += `${message}\n`
}

function hold(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
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

async function run() {
  if (!externalUserId) {
    finish('error', 'externalUserId ausente na URL.')
    return
  }
  debug(`externalUserId: ${externalUserId}`)
  debug(`useCase: ${config.USE_CASE}`)

  const { UnicoCheckBuilder, UnicoConfig, SDKEnvironmentTypes } =
    await import('./UnicoCheckBuilder.min.js')

  const unicoConfig = new UnicoConfig()
    .setHostname(config.HOSTNAME || window.location.origin)
    .setHostKey(config.SDK_KEY)

  const camera = new UnicoCheckBuilder()
    .setEnvironment(SDKEnvironmentTypes[config.SDK_ENVIRONMENT])
    .build()

  try {
    await camera.initializeSDK(unicoConfig)
    debug('initializeSDK: ok')

    // Resolves once the collection has been sent.
    await camera.startSilentValidation(externalUserId, config.USE_CASE)
    debug('collection sent')
  } catch (error) {
    const detail = `${error?.code ?? ''} ${error?.message ?? error}`.trim()
    debug(`silent validation failed: ${detail}`)
    finish('error', `Não foi possível coletar os dados do dispositivo. (${detail})`)
    return
  }

  await hold(config.POST_SEND_WAIT_MS)
  finish('ok', 'Tudo certo! Voltando ao app…')
}

run()
