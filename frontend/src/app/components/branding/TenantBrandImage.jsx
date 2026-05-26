import { useEffect, useMemo, useState } from 'react'
import { isBrandImageLoaded, preloadBrandImage } from '../../context/TenantBrandingContext'

function buildPlaceholderDataUri(label) {
  const normalizedLabel = String(label || 'Logo')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('') || 'LG'

  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 96" preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#f3f6fb" />
          <stop offset="100%" stop-color="#dde5ef" />
        </linearGradient>
      </defs>
      <rect width="160" height="96" rx="14" fill="url(#g)" />
      <rect x="16" y="16" width="128" height="64" rx="10" fill="#ffffff" fill-opacity="0.75" />
      <text x="80" y="57" font-family="Arial, sans-serif" font-size="28" text-anchor="middle" fill="#26415a">${normalizedLabel}</text>
    </svg>
  `

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function TenantBrandImage({ src, alt, className, label, placeholderClassName, wrapperClassName }) {
  const [isLoaded, setIsLoaded] = useState(() => isBrandImageLoaded(src))

  useEffect(() => {
    let isCancelled = false

    if (!src) {
      setIsLoaded(false)
      return () => {
        isCancelled = true
      }
    }

    if (isBrandImageLoaded(src)) {
      setIsLoaded(true)
      return () => {
        isCancelled = true
      }
    }

    setIsLoaded(false)
    preloadBrandImage(src)
      .then(() => {
        if (!isCancelled) {
          setIsLoaded(true)
        }
      })
      .catch(() => {
        if (!isCancelled) {
          setIsLoaded(true)
        }
      })

    return () => {
      isCancelled = true
    }
  }, [src])

  const placeholderSrc = useMemo(() => buildPlaceholderDataUri(label || alt), [alt, label])
  const shouldLazyLoad = !isBrandImageLoaded(src)

  return (
    <div className={wrapperClassName} style={{ position: 'relative', display: 'inline-flex' }}>
      {!isLoaded ? (
        <img
          className={placeholderClassName || className}
          src={placeholderSrc}
          alt=""
          aria-hidden="true"
        />
      ) : null}
      {src ? (
        <img
          className={className}
          src={src}
          alt={alt}
          loading={shouldLazyLoad ? 'lazy' : 'eager'}
          decoding="async"
          onLoad={() => setIsLoaded(true)}
          style={
            isLoaded
              ? {}
              : {
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  opacity: 0,
                  pointerEvents: 'none',
                }
          }
        />
      ) : null}
    </div>
  )
}

export default TenantBrandImage
