import { useState } from 'react'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import '../Home/Home.css'
import './ClientCompanyRegister.css'

const INITIAL_FORM_VALUES = {
  companyName: '',
  companyDocument: '',
  fullName: '',
  email: '',
  phoneNumber: '',
  documentNumber: '',
  password: '',
}

function ClientCompanyRegister({
  currentUser,
  headerProps,
  navigationGroups,
  onCreateClientCompany,
  onLookupClientCompany,
  onLinkExistingClientCompany,
  onNavigatePage,
}) {
  const [formValues, setFormValues] = useState(INITIAL_FORM_VALUES)
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [feedbackType, setFeedbackType] = useState('info')
  const [createdCompany, setCreatedCompany] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [existingCompanyToLink, setExistingCompanyToLink] = useState(null)
  const [isLinkingExistingCompany, setIsLinkingExistingCompany] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()

    if (
      !formValues.companyName.trim() ||
      !formValues.companyDocument.trim() ||
      !formValues.fullName.trim() ||
      !formValues.email.trim() ||
      !formValues.documentNumber.trim() ||
      !formValues.password.trim()
    ) {
      setFeedbackType('error')
      setFeedbackMessage('Preencha todos os campos obrigatórios para concluir o cadastro da empresa cliente.')
      return
    }

    try {
      setIsSubmitting(true)
      setFeedbackMessage('')
      const companyLookup = await onLookupClientCompany?.(formValues.companyDocument.trim())

      if (companyLookup?.status === 'ALREADY_CLIENT' || companyLookup?.status === 'PENDING_LINK') {
        setFeedbackType('error')
        setFeedbackMessage(
          companyLookup.message || 'A empresa desse CNPJ já está vinculada à sua operação.'
        )
        return
      }

      if (companyLookup?.status === 'UNAVAILABLE') {
        setFeedbackType('error')
        setFeedbackMessage(
          companyLookup.message || 'Esse CNPJ já está vinculado a um cadastro que não pode ser usado como cliente.'
        )
        return
      }

      if (companyLookup?.status === 'CAN_LINK_EXISTING') {
        setExistingCompanyToLink(companyLookup)
        return
      }

      const response = await onCreateClientCompany?.({
        companyName: formValues.companyName.trim(),
        companyDocument: formValues.companyDocument.trim(),
        fullName: formValues.fullName.trim(),
        email: formValues.email.trim().toLowerCase(),
        phoneNumber: formValues.phoneNumber.trim(),
        documentNumber: formValues.documentNumber.trim(),
        password: formValues.password,
      })

      setCreatedCompany(response)
      setFormValues(INITIAL_FORM_VALUES)
      setFeedbackType('success')
      setFeedbackMessage('Empresa cliente cadastrada e vinculada com sucesso.')
    } catch (error) {
      setFeedbackType('error')
      setFeedbackMessage(error.message || 'Não foi possível cadastrar a empresa cliente.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleConfirmLinkExistingCompany() {
    if (!existingCompanyToLink?.companyOwnerId) {
      return
    }

    try {
      setIsLinkingExistingCompany(true)
      setFeedbackMessage('')
      const response = await onLinkExistingClientCompany?.(existingCompanyToLink.companyOwnerId)
      setCreatedCompany(response)
      setFeedbackType('success')
      setFeedbackMessage('Empresa cliente vinculada com sucesso.')
      setFormValues(INITIAL_FORM_VALUES)
      setExistingCompanyToLink(null)
    } catch (error) {
      setFeedbackType('error')
      setFeedbackMessage(error.message || 'Não foi possível vincular a empresa cliente existente.')
    } finally {
      setIsLinkingExistingCompany(false)
    }
  }

  function handleCancelLinkExistingCompany() {
    if (isLinkingExistingCompany) {
      return
    }
    setExistingCompanyToLink(null)
  }

  return (
    <>
      <main className="home-page">
        <Sidebar
          activeSection="clientCompanyRegister"
          navigationGroups={navigationGroups}
          onSectionChange={onNavigatePage}
        />

        <div className="home-main-column">
          <Header
            activeSection="clientCompanyRegister"
            {...headerProps}
            onSectionChange={onNavigatePage}
          />

          <section className="home-content">
            <div className="home-content__card home-content__card--team">
              <div className="team-view">
                <div className="home-content__header">
                  <div className="home-content__heading">
                    <span className="home-content__eyebrow">Empresas clientes</span>
                    <h1>{dashboardPages.clientCompanyRegister.contentTitle}</h1>
                    <p>{dashboardPages.clientCompanyRegister.contentText}</p>
                  </div>
                </div>

                <div className="team-view__summary">
                  <article className="team-view__summary-card">
                    <span>Empresa provedora</span>
                    <strong>{currentUser?.companyName || 'Não informada'}</strong>
                    <small>O novo cadastro será vinculado automaticamente à sua empresa atendente.</small>
                  </article>
                  <article className="team-view__summary-card">
                    <span>Tipo criado</span>
                    <strong>Empresa cliente</strong>
                    <small>O administrador da empresa cliente acessará pelo mesmo subdomínio da sua empresa.</small>
                  </article>
                </div>

                <form className="team-invite client-company-register" onSubmit={handleSubmit}>
                  <div className="team-invite__header">
                    <div>
                      <span className="home-panel__eyebrow">Novo cadastro</span>
                      <h2>Dados da empresa cliente e do administrador</h2>
                    </div>
                  </div>

                  {feedbackMessage ? (
                    <p
                      className={`profile-form__feedback${
                        feedbackType === 'success' ? ' profile-form__feedback--success' : ''
                      }`}
                    >
                      {feedbackMessage}
                    </p>
                  ) : null}

                  <div className="ticket-form__grid">
                    <label className="ticket-field">
                      <span>Nome da empresa cliente</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o nome da empresa cliente"
                          value={formValues.companyName}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              companyName: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>

                    <label className="ticket-field">
                      <span>CNPJ da empresa cliente</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o CNPJ da empresa cliente"
                          value={formValues.companyDocument}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              companyDocument: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>
                  </div>

                  <div className="ticket-form__grid">
                    <label className="ticket-field">
                      <span>Nome do administrador</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o nome completo"
                          value={formValues.fullName}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              fullName: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>

                    <label className="ticket-field">
                      <span>Email do administrador</span>
                      <div className="ticket-field__control">
                        <input
                          type="email"
                          placeholder="Digite o email do administrador"
                          value={formValues.email}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              email: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>
                  </div>

                  <div className="ticket-form__grid">
                    <label className="ticket-field">
                      <span>Telefone do administrador</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o telefone do administrador"
                          value={formValues.phoneNumber}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              phoneNumber: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>

                    <label className="ticket-field">
                      <span>CPF do administrador</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o CPF do administrador"
                          value={formValues.documentNumber}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              documentNumber: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>
                  </div>

                  <label className="ticket-field">
                    <span>Senha inicial do administrador</span>
                    <div className="ticket-field__control">
                      <input
                        type="password"
                        placeholder="Defina a senha inicial"
                        value={formValues.password}
                        disabled={isSubmitting}
                        onChange={(event) =>
                          setFormValues((currentValues) => ({
                            ...currentValues,
                            password: event.target.value,
                          }))
                        }
                      />
                    </div>
                  </label>

                  <div className="team-invite__footer">
                    <span>
                      O cadastro cria a empresa cliente dentro do seu subdomínio, ativa o administrador e
                      mantém o vínculo com a sua operação de atendimento.
                    </span>
                    <button className="team-invite__button" type="submit" disabled={isSubmitting}>
                      {isSubmitting ? 'Cadastrando...' : 'Cadastrar empresa cliente'}
                    </button>
                  </div>
                </form>

                {createdCompany ? (
                  <section className="client-company-register__result">
                    <div className="team-panel__header">
                      <div>
                        <span className="home-panel__eyebrow">Cadastro concluído</span>
                        <h2>Resumo da nova empresa cliente</h2>
                      </div>
                    </div>

                    <div className="client-company-register__result-grid">
                      <article className="client-company-register__result-card">
                        <span>Empresa</span>
                        <strong>{createdCompany.companyName}</strong>
                        <small>{createdCompany.companyDocument}</small>
                      </article>
                      <article className="client-company-register__result-card">
                        <span>Administrador</span>
                        <strong>{createdCompany.adminName}</strong>
                        <small>{createdCompany.adminEmail}</small>
                      </article>
                      <article className="client-company-register__result-card">
                        <span>Subdomínio de acesso</span>
                        <strong>{createdCompany.subdomain || 'Mesmo subdomínio da provedora'}</strong>
                        <small>A empresa cliente acessa pelo mesmo subdomínio da empresa provedora.</small>
                      </article>
                    </div>
                  </section>
                ) : null}
              </div>
            </div>
          </section>
        </div>
      </main>

      <ConfirmActionModal
        isOpen={Boolean(existingCompanyToLink)}
        title="CNPJ já cadastrado"
        description={
          existingCompanyToLink
            ? `Esse CNPJ já está cadastrado. O nome do administrador é "${existingCompanyToLink.adminName}" e o nome da empresa é "${existingCompanyToLink.companyName}". Quer colocá-lo como cliente?`
            : ''
        }
        cancelLabel="Não"
        confirmLabel="Sim"
        onCancel={handleCancelLinkExistingCompany}
        onConfirm={handleConfirmLinkExistingCompany}
        isProcessing={isLinkingExistingCompany}
      />
    </>
  )
}

export default ClientCompanyRegister
