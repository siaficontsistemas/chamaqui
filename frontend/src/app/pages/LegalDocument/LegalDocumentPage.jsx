import { useEffect, useMemo, useState } from 'react'
import { getPublicLegalDocument } from '../../api'
import './LegalDocumentPage.css'

const DOCUMENT_TYPE_BY_SLUG = {
  'termos-de-uso': 'TERMS_OF_USE',
  'politica-de-privacidade': 'PRIVACY_POLICY',
}

function LegalDocumentPage({ documentSlug, backHref }) {
  const [documentData, setDocumentData] = useState(null)
  const [errorMessage, setErrorMessage] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const resolvedDocumentType = useMemo(
    () => DOCUMENT_TYPE_BY_SLUG[documentSlug] || 'TERMS_OF_USE',
    [documentSlug]
  )

  useEffect(() => {
    let ignore = false

    async function loadDocument() {
      try {
        setIsLoading(true)
        setErrorMessage('')
        const response = await getPublicLegalDocument(resolvedDocumentType)
        if (!ignore) {
          setDocumentData(response)
        }
      } catch (error) {
        if (!ignore) {
          setErrorMessage(error.message)
          setDocumentData(null)
        }
      } finally {
        if (!ignore) {
          setIsLoading(false)
        }
      }
    }

    loadDocument()

    return () => {
      ignore = true
    }
  }, [resolvedDocumentType])

  return (
    <main className="legal-document-page">
      <section className="legal-document-card">
        <a className="legal-document-card__back" href={backHref}>
          Voltar
        </a>

        {isLoading ? (
          <div className="legal-document-card__state">Carregando documento...</div>
        ) : errorMessage ? (
          <div className="legal-document-card__state legal-document-card__state--error">
            {errorMessage}
          </div>
        ) : documentData ? (
          <>
            <header className="legal-document-card__header">
              <span className="legal-document-card__badge">Documento legal</span>
              <h1>{documentData.title}</h1>
              <p>{documentData.summary}</p>
              <div className="legal-document-card__meta">
                <span>Versao {documentData.version}</span>
                <span>Vigencia {formatDate(documentData.effectiveAt)}</span>
              </div>
            </header>

            <div className="legal-document-card__body">
              {(documentData.sections || []).map((section) => (
                <section key={section.title} className="legal-document-card__section">
                  <h2>{section.title}</h2>
                  {(section.paragraphs || []).map((paragraph) => (
                    <p key={paragraph}>{paragraph}</p>
                  ))}
                </section>
              ))}
            </div>
          </>
        ) : null}
      </section>
    </main>
  )
}

function formatDate(value) {
  if (!value) {
    return '-'
  }

  const parsedDate = new Date(value)
  if (Number.isNaN(parsedDate.getTime())) {
    return value
  }

  return parsedDate.toLocaleDateString('pt-BR')
}

export default LegalDocumentPage
