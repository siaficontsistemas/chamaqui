import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicTicketAttachmentApiUrl } from '../../api'

function readFileNameFromDisposition(dispositionHeader) {
  if (!dispositionHeader) {
    return ''
  }

  const utf8Match = dispositionHeader.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }

  const quotedMatch = dispositionHeader.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) {
    return quotedMatch[1]
  }

  const plainMatch = dispositionHeader.match(/filename=([^;]+)/i)
  return plainMatch?.[1]?.trim() || ''
}

function isPreviewableContentType(contentType) {
  if (!contentType) {
    return false
  }

  return (
    contentType.startsWith('image/') ||
    contentType.startsWith('audio/') ||
    contentType.startsWith('video/') ||
    contentType.startsWith('text/') ||
    contentType === 'application/pdf'
  )
}

export default function TicketAttachmentViewer() {
  const { ticketId, attachmentId } = useParams()
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')
  const [blobUrl, setBlobUrl] = useState('')
  const [fileName, setFileName] = useState('anexo')
  const [contentType, setContentType] = useState('')
  const [imageZoom, setImageZoom] = useState(1)
  const downloadTriggeredRef = useRef(false)

  useEffect(() => {
    if (!ticketId || !attachmentId) {
      setStatus('error')
      setError('Anexo invalido.')
      return undefined
    }

    let cancelled = false
    let objectUrl = ''

    async function loadAttachment() {
      setStatus('loading')
      setError('')

      try {
        const response = await fetch(getPublicTicketAttachmentApiUrl(ticketId, attachmentId), {
          credentials: 'omit',
        })

        if (!response.ok) {
          throw new Error('Nao foi possivel abrir este anexo.')
        }

        const nextContentType = response.headers.get('content-type') || ''
        const dispositionHeader = response.headers.get('content-disposition') || ''
        const nextFileName = readFileNameFromDisposition(dispositionHeader) || 'anexo'
        const blob = await response.blob()

        if (cancelled) {
          return
        }

        objectUrl = window.URL.createObjectURL(blob)
        setBlobUrl(objectUrl)
        setContentType(nextContentType)
        setFileName(nextFileName)
        setImageZoom(1)
        setStatus('ready')
      } catch (nextError) {
        if (cancelled) {
          return
        }

        setBlobUrl('')
        setContentType('')
        setStatus('error')
        setError(nextError.message || 'Nao foi possivel abrir este anexo.')
      }
    }

    loadAttachment()

    return () => {
      cancelled = true
      downloadTriggeredRef.current = false
      if (objectUrl) {
        window.URL.revokeObjectURL(objectUrl)
      }
    }
  }, [attachmentId, ticketId])

  const shouldPreviewInline = useMemo(() => isPreviewableContentType(contentType), [contentType])
  const isImage = contentType.startsWith('image/')

  useEffect(() => {
    if (!blobUrl || shouldPreviewInline || downloadTriggeredRef.current) {
      return
    }

    downloadTriggeredRef.current = true
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName || 'anexo'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }, [blobUrl, fileName, shouldPreviewInline])

  if (status === 'loading') {
    return (
      <main className="attachment-viewer">
        <div className="attachment-viewer__card">
          <span className="attachment-viewer__eyebrow">Anexo do chamado</span>
          <h1>Abrindo arquivo...</h1>
          <p>O documento esta sendo carregado neste subdominio.</p>
        </div>
      </main>
    )
  }

  if (status === 'error') {
    return (
      <main className="attachment-viewer">
        <div className="attachment-viewer__card">
          <span className="attachment-viewer__eyebrow">Anexo do chamado</span>
          <h1>Nao foi possivel abrir o arquivo</h1>
          <p>{error || 'Tente novamente em alguns instantes.'}</p>
        </div>
      </main>
    )
  }

  return (
    <main className="attachment-viewer">
      <header className="attachment-viewer__header">
        <div>
          <span className="attachment-viewer__eyebrow">Anexo do chamado</span>
          <h1>{fileName}</h1>
        </div>
        <a className="attachment-viewer__action" download={fileName} href={blobUrl}>
          Baixar arquivo
        </a>
      </header>

      <section className="attachment-viewer__content">
        {shouldPreviewInline ? (
          isImage ? (
            <div className="attachment-viewer__image-stage">
              <div className="attachment-viewer__zoom-controls" aria-label="Controles de zoom">
                <button
                  type="button"
                  onClick={() => setImageZoom((currentZoom) => Math.max(1, currentZoom - 0.25))}
                  disabled={imageZoom <= 1}
                  aria-label="Diminuir zoom"
                >
                  −
                </button>
                <span>{Math.round(imageZoom * 100)}%</span>
                <button
                  type="button"
                  onClick={() => setImageZoom((currentZoom) => Math.min(4, currentZoom + 0.25))}
                  disabled={imageZoom >= 4}
                  aria-label="Aumentar zoom"
                >
                  +
                </button>
                <button type="button" onClick={() => setImageZoom(1)} disabled={imageZoom === 1}>
                  Ajustar
                </button>
              </div>
              <div className="attachment-viewer__image-scroll-area">
                <img
                  alt={fileName}
                  className="attachment-viewer__image"
                  src={blobUrl}
                  style={{ transform: `scale(${imageZoom})` }}
                />
              </div>
            </div>
          ) : contentType.startsWith('audio/') ? (
            <div className="attachment-viewer__media">
              <audio controls src={blobUrl}>
                Seu navegador nao conseguiu reproduzir este audio.
              </audio>
            </div>
          ) : contentType.startsWith('video/') ? (
            <video className="attachment-viewer__video" controls src={blobUrl}>
              Seu navegador nao conseguiu reproduzir este video.
            </video>
          ) : contentType.startsWith('text/') ? (
            <iframe className="attachment-viewer__frame" src={blobUrl} title={fileName} />
          ) : (
            <iframe className="attachment-viewer__frame" src={blobUrl} title={fileName} />
          )
        ) : (
          <div className="attachment-viewer__fallback">
            <p>Este tipo de arquivo nao possui visualizacao direta no navegador.</p>
            <p>O download ja foi iniciado. Se precisar, use o botao acima para baixar novamente.</p>
          </div>
        )}
      </section>
    </main>
  )
}
