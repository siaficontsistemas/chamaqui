import { useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import '../Home/Home.css'
import './ClientCompanyRegister.css'

const INITIAL_FORM_VALUES = {
  companyName: '',
  companyDocument: '',
  companyEmail: '',
  companyPhoneNumber: '',
}

function ClientCompanyRegister({
  currentUser,
  headerProps,
  navigationGroups,
  onCreateClientCompany,
  onLookupClientCompany,
  onNavigatePage,
}) {
  const [formValues, setFormValues] = useState(INITIAL_FORM_VALUES)
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [feedbackType, setFeedbackType] = useState('info')
  const [createdCompany, setCreatedCompany] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()

    if (!formValues.companyName.trim() || !formValues.companyDocument.trim()) {
      setFeedbackType('error')
      setFeedbackMessage('Preencha o nome e o CNPJ da empresa cliente para concluir o cadastro.')
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

      const response = await onCreateClientCompany?.({
        companyName: formValues.companyName.trim(),
        companyDocument: formValues.companyDocument.trim(),
        companyEmail: formValues.companyEmail.trim().toLowerCase(),
        companyPhoneNumber: formValues.companyPhoneNumber.trim(),
      })

      setCreatedCompany(response)
      setFormValues(INITIAL_FORM_VALUES)
      setFeedbackType('success')
      setFeedbackMessage('Empresa cliente cadastrada com sucesso.')
    } catch (error) {
      setFeedbackType('error')
      setFeedbackMessage(error.message || 'Não foi possível cadastrar a empresa cliente.')
    } finally {
      setIsSubmitting(false)
    }
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
                    <small>A nova empresa cliente ficará gerenciada dentro da sua operação de atendimento.</small>
                  </article>
                  <article className="team-view__summary-card">
                    <span>Tipo criado</span>
                    <strong>Empresa cliente</strong>
                    <small>A empresa cliente não terá administrador fixo e funcionará apenas com funcionários.</small>
                  </article>
                </div>

                <form className="team-invite client-company-register" onSubmit={handleSubmit}>
                  <div className="team-invite__header">
                    <div>
                      <span className="home-panel__eyebrow">Novo cadastro</span>
                      <h2>Dados da empresa cliente</h2>
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
                      <span>Email da empresa cliente</span>
                      <div className="ticket-field__control">
                        <input
                          type="email"
                          placeholder="Digite o email da empresa cliente"
                          value={formValues.companyEmail}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              companyEmail: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>

                    <label className="ticket-field">
                      <span>Telefone da empresa cliente</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o telefone da empresa cliente"
                          value={formValues.companyPhoneNumber}
                          disabled={isSubmitting}
                          onChange={(event) =>
                            setFormValues((currentValues) => ({
                              ...currentValues,
                              companyPhoneNumber: event.target.value,
                            }))
                          }
                        />
                      </div>
                    </label>
                  </div>

                  <div className="team-invite__footer">
                    <span>
                      O cadastro cria a empresa cliente dentro do seu subdomínio. Depois, os funcionários
                      dessa empresa poderão se cadastrar e você fará a aprovação e a gestão deles.
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
                        <span>Contato</span>
                        <strong>{createdCompany.companyEmail || 'Email não informado'}</strong>
                        <small>{createdCompany.companyPhoneNumber || 'Telefone não informado'}</small>
                      </article>
                      <article className="client-company-register__result-card">
                        <span>Subdomínio de acesso</span>
                        <strong>{createdCompany.subdomain || 'Mesmo subdomínio da provedora'}</strong>
                        <small>Os funcionários da empresa cliente acessam pelo mesmo subdomínio da empresa provedora.</small>
                      </article>
                    </div>
                  </section>
                ) : null}
              </div>
            </div>
          </section>
        </div>
      </main>

    </>
  )
}

export default ClientCompanyRegister
