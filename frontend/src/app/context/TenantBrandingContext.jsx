import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { getPublicTenantBranding, resolveApiAssetUrl } from '../api'

const TenantBrandingContext = createContext(null)
const BRANDING_STORAGE_PREFIX = 'helpdesk.tenant-branding.'
const BRAND_IMAGE_STORAGE_PREFIX = 'helpdesk.brand-image.'
const brandingMemoryCache = new Map()
const brandingRequestCache = new Map()
const loadedBrandImages = new Set()
const imagePreloadCache = new Map()

function getBrandingStorageKey(host) {
  return `${BRANDING_STORAGE_PREFIX}${host || 'default'}`
}

function getBrandImageStorageKey(url) {
  return `${BRAND_IMAGE_STORAGE_PREFIX}${url}`
}

function readSessionStorage(key) {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    return window.sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function writeSessionStorage(key, value) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    if (value == null) {
      window.sessionStorage.removeItem(key)
      return
    }

    window.sessionStorage.setItem(key, value)
  } catch {
    // Ignore storage write failures.
  }
}

function readCachedBranding(host) {
  if (!host) {
    return null
  }

  if (brandingMemoryCache.has(host)) {
    return brandingMemoryCache.get(host)
  }

  const serializedBranding = readSessionStorage(getBrandingStorageKey(host))
  if (!serializedBranding) {
    return null
  }

  try {
    const branding = JSON.parse(serializedBranding)
    brandingMemoryCache.set(host, branding)
    return branding
  } catch {
    return null
  }
}

function writeCachedBranding(host, branding) {
  if (!host) {
    return
  }

  if (!branding) {
    brandingMemoryCache.delete(host)
    writeSessionStorage(getBrandingStorageKey(host), null)
    return
  }

  brandingMemoryCache.set(host, branding)
  writeSessionStorage(getBrandingStorageKey(host), JSON.stringify(branding))
}

export function isBrandImageLoaded(url) {
  if (!url) {
    return false
  }

  if (loadedBrandImages.has(url)) {
    return true
  }

  const isLoaded = readSessionStorage(getBrandImageStorageKey(url)) === 'loaded'
  if (isLoaded) {
    loadedBrandImages.add(url)
  }
  return isLoaded
}

function markBrandImageAsLoaded(url) {
  if (!url) {
    return
  }

  loadedBrandImages.add(url)
  writeSessionStorage(getBrandImageStorageKey(url), 'loaded')
}

export function preloadBrandImage(url) {
  if (!url) {
    return Promise.resolve()
  }

  if (isBrandImageLoaded(url)) {
    return Promise.resolve()
  }

  if (imagePreloadCache.has(url)) {
    return imagePreloadCache.get(url)
  }

  const preloadPromise = new Promise((resolve, reject) => {
    const image = new window.Image()
    image.loading = 'lazy'
    image.decoding = 'async'
    image.onload = () => {
      markBrandImageAsLoaded(url)
      resolve()
    }
    image.onerror = reject
    image.src = url
  }).finally(() => {
    imagePreloadCache.delete(url)
  })

  imagePreloadCache.set(url, preloadPromise)
  return preloadPromise
}

async function fetchTenantBranding(host) {
  if (!host) {
    return null
  }

  const cachedBranding = readCachedBranding(host)
  if (cachedBranding) {
    return cachedBranding
  }

  if (brandingRequestCache.has(host)) {
    return brandingRequestCache.get(host)
  }

  const brandingPromise = getPublicTenantBranding(host)
    .then((branding) => {
      writeCachedBranding(host, branding)
      return branding
    })
    .finally(() => {
      brandingRequestCache.delete(host)
    })

  brandingRequestCache.set(host, brandingPromise)
  return brandingPromise
}

export function TenantBrandingProvider({ children }) {
  const currentHost = typeof window !== 'undefined' ? window.location.host : ''
  const [branding, setBrandingState] = useState(() => readCachedBranding(currentHost))
  const [isLoading, setIsLoading] = useState(() => !readCachedBranding(currentHost) && Boolean(currentHost))

  const applyBranding = useCallback(
    (nextBranding) => {
      setBrandingState((currentBranding) => {
        const resolvedBranding =
          typeof nextBranding === 'function' ? nextBranding(currentBranding) : nextBranding
        writeCachedBranding(currentHost, resolvedBranding)
        return resolvedBranding
      })
    },
    [currentHost]
  )

  const refreshBranding = useCallback(async () => {
    if (!currentHost) {
      applyBranding(null)
      return null
    }

    const nextBranding = await getPublicTenantBranding(currentHost)
    applyBranding(nextBranding)
    return nextBranding
  }, [applyBranding, currentHost])

  useEffect(() => {
    let isCancelled = false

    async function loadBranding() {
      if (!currentHost) {
        applyBranding(null)
        setIsLoading(false)
        return
      }

      setIsLoading(!readCachedBranding(currentHost))

      try {
        const nextBranding = await fetchTenantBranding(currentHost)
        if (!isCancelled) {
          applyBranding(nextBranding)
        }
      } catch {
        if (!isCancelled) {
          applyBranding(null)
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false)
        }
      }
    }

    loadBranding()

    return () => {
      isCancelled = true
    }
  }, [applyBranding, currentHost])

  const companyLogoUrl = useMemo(
    () => resolveApiAssetUrl(branding?.logoUrl || branding?.loginLogoUrl || ''),
    [branding?.loginLogoUrl, branding?.logoUrl]
  )

  useEffect(() => {
    if (!companyLogoUrl || typeof window === 'undefined') {
      return
    }

    preloadBrandImage(companyLogoUrl).catch(() => {
      // Ignore preload failures and let the visible image handle retries.
    })
  }, [companyLogoUrl])

  const contextValue = useMemo(
    () => ({
      branding,
      companyLogoUrl,
      isLoading,
      isTenantExperience: Boolean(branding?.tenantResolved),
      refreshBranding,
      setBranding: applyBranding,
    }),
    [applyBranding, branding, companyLogoUrl, isLoading, refreshBranding]
  )

  return (
    <TenantBrandingContext.Provider value={contextValue}>
      {children}
    </TenantBrandingContext.Provider>
  )
}

export function useTenantBranding() {
  const context = useContext(TenantBrandingContext)

  if (!context) {
    throw new Error('useTenantBranding precisa ser usado dentro de TenantBrandingProvider.')
  }

  return context
}
