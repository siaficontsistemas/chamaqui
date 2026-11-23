import { useState } from 'react'
import { ChevronDownIcon } from '../../dashboardIcons'
import {
  EMPTY_CLIENT_USER,
  formatCpf,
  formatPhone,
  toClientUserFormValues,
  toClientUserPayload,
  validateClientUser,
} from './clientUserValidation'

const FIELD_FORMATTERS = {
  phoneNumber: formatPhone,
  documentNumber: formatCpf,
}

/**
 * Formulário de cadastro/edição de cliente (pré-cadastro).
 * Valida ao sair de cada campo e novamente ao enviar; erros do servidor aparecem no topo.
 */
function ClientUserForm({ clientCompanies, editingClient, onCancelEdit, onSubmit }) {
  const [values, setValues] = useState(() =>
    editingClient ? toClientUserFormValues(editingClient) : EMPTY_CLIENT_USER
  )
  const [errors, setErrors] = useState({})
  const [touched, setTouched] = useState({})
  const [serverError, setServerError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const isEditing = Boolean(editingClient)
  const hasCompanies = clientCompanies.length > 0

  function handleChange(fieldId, rawValue) {
    const formatter = FIELD_FORMATTERS[fieldId]
    const nextValues = { ...values, [fieldId]: formatter ? formatter(rawValue) : rawValue }
    setValues(nextValues)
    if (touched[fieldId]) {
      setErrors(validateClientUser(nextValues))
    }
  }

  function handleBlur(fieldId) {
    setTouched((current) => ({ ...current, [fieldId]: true }))
    setErrors(validateClientUser(values))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const nextErrors = validateClientUser(values)
    setErrors(nextErrors)
    setTouched({ fullName: true, phoneNumber: true, email: true, documentNumber: true, companyOwnerId: true })
    if (Object.keys(nextErrors).length > 0) {
      setServerError('')
      return
    }

    try {
      setIsSubmitting(true)
      setServerError('')
      await onSubmit(toClientUserPayload(values))
      if (!isEditing) {
        setValues(EMPTY_CLIENT_USER)
        setTouched({})
        setErrors({})
      }
    } catch (error) {
      setServerError(error?.message || 'Não foi possível salvar o cadastro. Tente novamente.')
    } finally {
      setIsSubmitting(false)
    }
  }

  function renderError(fieldId) {
    if (!touched[fieldId] || !errors[fieldId]) return null
    return (
      <small className="client-users-form__error" id={`client-user-${fieldId}-error`} role="alert">
        {errors[fieldId]}
      </small>
    )
  }

  function fieldProps(fieldId) {
    const hasError = Boolean(touched[fieldId] && errors[fieldId])
    return {
      id: `client-user-${fieldId}`,
      value: values[fieldId],
      disabled: isSubmitting,
      'aria-invalid': hasError,
      'aria-describedby': hasError ? `client-user-${fieldId}-error` : undefined,
      onBlur: () => handleBlur(fieldId),
      onChange: (event) => handleChange(fieldId, event.target.value),
    }
  }

  const controlClass = (fieldId) =>
    `ticket-field__control${touched[fieldId] && errors[fieldId] ? ' client-users-form__control--invalid' : ''}`

  return (
    <form className="team-invite client-users-form" noValidate onSubmit={handleSubmit}>
      <div className="team-invite__header">
        <div>
          <span className="home-panel__eyebrow">{isEditing ? 'Editar cadastro' : 'Novo cadastro'}</span>
          <h2>{isEditing ? `Editando ${editingClient.fullName}` : 'Dados do cliente'}</h2>
        </div>
      </div>

      {serverError ? <p className="profile-form__feedback" role="alert">{serverError}</p> : null}

      {!hasCompanies ? (
        <p className="client-users-form__notice">
          Nenhuma empresa cliente vinculada. Cadastre uma empresa cliente antes de cadastrar usuários.
        </p>
      ) : null}

      <div className="ticket-form__grid">
        <label className="ticket-field" htmlFor="client-user-fullName">
          <span>Nome completo *</span>
          <div className={controlClass('fullName')}>
            <input type="text" autoComplete="name" placeholder="Ex.: Maria Souza" maxLength={150} {...fieldProps('fullName')} />
          </div>
          {renderError('fullName')}
        </label>

        <label className="ticket-field" htmlFor="client-user-phoneNumber">
          <span>Telefone (WhatsApp) *</span>
          <div className={controlClass('phoneNumber')}>
            <input type="tel" inputMode="numeric" autoComplete="tel" placeholder="(11) 91234-5678" {...fieldProps('phoneNumber')} />
          </div>
          {renderError('phoneNumber')}
        </label>
      </div>

      <div className="ticket-form__grid">
        <label className="ticket-field" htmlFor="client-user-email">
          <span>E-mail <em className="client-users-form__optional">(opcional)</em></span>
          <div className={controlClass('email')}>
            <input type="email" autoComplete="email" placeholder="nome@empresa.com.br" maxLength={150} {...fieldProps('email')} />
          </div>
          {renderError('email')}
        </label>

        <label className="ticket-field" htmlFor="client-user-documentNumber">
          <span>CPF <em className="client-users-form__optional">(opcional)</em></span>
          <div className={controlClass('documentNumber')}>
            <input type="text" inputMode="numeric" placeholder="000.000.000-00" {...fieldProps('documentNumber')} />
          </div>
          {renderError('documentNumber')}
        </label>
      </div>

      <label className="ticket-field ticket-field--full" htmlFor="client-user-companyOwnerId">
        <span>Empresa cliente *</span>
        <div className={`${controlClass('companyOwnerId')} ticket-field__control--select`}>
          <select {...fieldProps('companyOwnerId')} disabled={isSubmitting || !hasCompanies}>
            <option value="">Selecione a empresa cliente</option>
            {clientCompanies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
          <ChevronDownIcon />
        </div>
        {renderError('companyOwnerId')}
      </label>

      <div className="team-invite__footer">
        <span>* Campos obrigatórios. O cliente poderá abrir chamados pelo WhatsApp com esse telefone.</span>
        <div className="client-users-form__actions">
          {isEditing ? (
            <button className="client-users-form__secondary" type="button" disabled={isSubmitting} onClick={onCancelEdit}>
              Cancelar
            </button>
          ) : null}
          <button className="team-invite__button" type="submit" disabled={isSubmitting || !hasCompanies}>
            {isSubmitting ? 'Salvando...' : isEditing ? 'Salvar alterações' : 'Cadastrar cliente'}
          </button>
        </div>
      </div>
    </form>
  )
}

export default ClientUserForm
