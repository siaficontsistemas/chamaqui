import { useEffect, useMemo, useState } from 'react'
import { getWhatsappQrCode, getWhatsappSessionStatus } from '../../api'

function toQrCodeImageSource(qrCodeResponse) {
  const primaryImage = (qrCodeResponse?.qrCode || '').trim()
  if (primaryImage) {
    if (primaryImage.startsWith('data:')) {
      return primaryImage
    }

    if (/^[A-Za-z0-9+/=\s]+$/.test(primaryImage)) {
      return `data:image/png;base64,${primaryImage}`
    }
  }

  const fallbackImage = (qrCodeResponse?.data || '').trim()
  if (!fallbackImage || fallbackImage.startsWith('{') || fallbackImage.startsWith('[')) {
    return ''
  }

  if (fallbackImage.startsWith('data:')) {
    return fallbackImage
  }

  if (/^[A-Za-z0-9+/=\s]+$/.test(fallbackImage)) {
    return `data:image/png;base64,${fallbackImage}`
  }

  return ''
}

export default function WhatsappQrCodePage() {
  const [qrCodeResponse, setQrCodeResponse] = useState(null)
  const [sessionStatus, setSessionStatus] = useState(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function loadQrCode() {
      setIsLoading(true)
      setError('')

      try {
        const [nextQrCode, nextStatus] = await Promise.all([
          getWhatsappQrCode(),
          getWhatsappSessionStatus(),
        ])

        if (cancelled) {
          return
        }

        setQrCodeResponse(nextQrCode)
        setSessionStatus(nextStatus)
      } catch (nextError) {
        if (cancelled) {
          return
        }

        setError(nextError.message || 'Nao foi possivel carregar o QR Code.')
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadQrCode()

    const intervalId = window.setInterval(loadQrCode, 10000)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [])

  const qrCodeImageSource = useMemo(() => toQrCodeImageSource(qrCodeResponse), [qrCodeResponse])
  const isConnected =
    sessionStatus?.connected === true || sessionStatus?.status?.toUpperCase() === 'CONNECTED'

  if (isLoading && !qrCodeImageSource) {
    return (
      <main className="attachment-viewer">
        <div className="attachment-viewer__card">
          <span className="attachment-viewer__eyebrow">WhatsApp da empresa</span>
          <h1>Carregando QR Code...</h1>
          <p>Estamos buscando o QR Code da sessao atual neste subdominio.</p>
        </div>
      </main>
    )
  }

  if (isConnected) {
    return (
      <main className="attachment-viewer">
        <div className="attachment-viewer__card">
          <span className="attachment-viewer__eyebrow">WhatsApp da empresa</span>
          <h1>WhatsApp conectado</h1>
          <p>{sessionStatus?.message || 'A sessao ja esta conectada para esta empresa.'}</p>
        </div>
      </main>
    )
  }

  if (error && !qrCodeImageSource) {
    return (
      <main className="attachment-viewer">
        <div className="attachment-viewer__card">
          <span className="attachment-viewer__eyebrow">WhatsApp da empresa</span>
          <h1>Nao foi possivel abrir o QR Code</h1>
          <p>{error}</p>
        </div>
      </main>
    )
  }

  return (
    <main className="attachment-viewer">
      <header className="attachment-viewer__header">
        <div>
          <span className="attachment-viewer__eyebrow">WhatsApp da empresa</span>
          <h1>Escaneie o QR Code</h1>
        </div>
      </header>

      <section className="attachment-viewer__content">
        <div className="whatsapp-qrcode-page">
          {sessionStatus?.sessionName || sessionStatus?.session ? (
            <p className="whatsapp-qrcode-page__meta">
              Sessao: {sessionStatus?.sessionName || sessionStatus?.session}
            </p>
          ) : null}
          <p className="whatsapp-qrcode-page__message">
            {qrCodeResponse?.message || sessionStatus?.message || 'Escaneie o QR Code com o WhatsApp.'}
          </p>
          {qrCodeImageSource ? (
            <img className="whatsapp-qrcode-page__image" src={qrCodeImageSource} alt="QR Code do WhatsApp" />
          ) : (
            <p className="whatsapp-qrcode-page__message">
              O QR Code ainda nao esta disponivel. Aguarde alguns segundos.
            </p>
          )}
          {error ? <p className="whatsapp-qrcode-page__warning">{error}</p> : null}
        </div>
      </section>
    </main>
  )
}
