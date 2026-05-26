const BASE_DOMAIN = 'chamaqui.app.br'
const ROOT_HOSTS = new Set(['localhost', '127.0.0.1', BASE_DOMAIN, `www.${BASE_DOMAIN}`])
const RESERVED_SUBDOMAINS = new Set(['admin', 'api', 'www'])

function normalizeHost(hostname) {
  const rawHost =
    typeof hostname === 'string' && hostname.trim()
      ? hostname.trim().toLowerCase()
      : typeof window !== 'undefined' && window.location?.hostname
        ? window.location.hostname.toLowerCase()
        : ''

  return rawHost.replace(/:\d+$/, '').replace(/\.$/, '')
}

export function isPlatformAdminHost(hostname) {
  const normalizedHost = normalizeHost(hostname)
  return normalizedHost === `admin.${BASE_DOMAIN}` || normalizedHost.startsWith('admin.')
}

export function getTenantCandidateSubdomain(hostname) {
  const normalizedHost = normalizeHost(hostname)
  if (!normalizedHost || ROOT_HOSTS.has(normalizedHost)) {
    return null
  }

  const suffix = `.${BASE_DOMAIN}`
  if (!normalizedHost.endsWith(suffix)) {
    return null
  }

  const candidate = normalizedHost.slice(0, -suffix.length)
  if (!candidate || candidate.includes('.') || RESERVED_SUBDOMAINS.has(candidate)) {
    return null
  }

  return candidate
}

export function isManagedTenantHost(hostname) {
  return Boolean(getTenantCandidateSubdomain(hostname))
}
