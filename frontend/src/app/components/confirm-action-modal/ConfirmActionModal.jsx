import { createPortal } from 'react-dom'
import './ConfirmActionModal.css'

function ConfirmActionModal({
  cancelLabel = 'Cancelar',
  confirmLabel = 'Confirmar',
  confirmVariant = 'primary',
  description = '',
  isOpen = false,
  isProcessing = false,
  onCancel,
  onConfirm,
  title,
}) {
  if (!isOpen) {
    return null
  }

  const paragraphs = Array.isArray(description) ? description : [description]
  const toneLabel =
    confirmVariant === 'danger' ? 'Atenção' : confirmVariant === 'primary' ? 'Confirmação' : 'Aviso'
  const toneIcon = confirmVariant === 'danger' ? '!' : '?'
  const modalContent = (
    <div
      className="confirm-action-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-action-modal-title"
    >
      <div className="confirm-action-modal__card">
        <button
          className="confirm-action-modal__close"
          type="button"
          onClick={onCancel}
          disabled={isProcessing}
          aria-label="Fechar modal"
        >
          x
        </button>
        <div className="confirm-action-modal__header">
          <div
            className={`confirm-action-modal__icon confirm-action-modal__icon--${confirmVariant}`}
            aria-hidden="true"
          >
            {toneIcon}
          </div>
          <div className="confirm-action-modal__heading">
            <span className="confirm-action-modal__eyebrow">{toneLabel}</span>
            <h2 className="confirm-action-modal__title" id="confirm-action-modal-title">
              {title}
            </h2>
          </div>
        </div>
        {paragraphs.filter(Boolean).map((paragraph) => (
          <p className="confirm-action-modal__text" key={paragraph}>
            {paragraph}
          </p>
        ))}

        <div className="confirm-action-modal__actions">
          <button
            className="confirm-action-modal__button confirm-action-modal__button--secondary"
            type="button"
            onClick={onCancel}
            disabled={isProcessing}
          >
            {cancelLabel}
          </button>
          <button
            className={`confirm-action-modal__button confirm-action-modal__button--${confirmVariant}`}
            type="button"
            onClick={onConfirm}
            disabled={isProcessing}
          >
            {isProcessing ? 'Processando...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )

  return createPortal(modalContent, document.body)
}

export default ConfirmActionModal
