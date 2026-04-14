import { createPortal } from 'react-dom'
import './ConfirmActionModal.css'

function ConfirmActionModal({
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
  const modalContent = (
    <div className="confirm-action-modal" role="dialog" aria-modal="true" aria-labelledby="confirm-action-modal-title">
      <div className="confirm-action-modal__card">
        <h2 className="confirm-action-modal__title" id="confirm-action-modal-title">
          {title}
        </h2>
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
            Cancelar
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
