import { useMemo, useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages, isTeamRole } from '../../dashboardData'
import { ChevronDownIcon, PlusCircleIcon } from '../../dashboardIcons'
import '../Home/Home.css'

function NewTicket({
  currentUser,
  headerProps,
  navigationGroups,
  onCreateTicket,
  onNavigatePage,
  sectors = [],
  userRole = 'user',
}) {
  const activeContent = dashboardPages.newTicket
  const [formValues, setFormValues] = useState({
    sectorId: '',
    priorityCode: '',
    title: '',
    copyEmail: '',
    description: '',
  })
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const availableSectors = useMemo(() => sectors.filter((sector) => sector.active !== false), [sectors])

  function handleChange(field, value) {
    setFormValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()

    if (!formValues.sectorId) {
      setFeedbackMessage('Selecione um setor para criar o chamado.')
      return
    }

    if (!formValues.priorityCode) {
      setFeedbackMessage('Selecione a prioridade do chamado.')
      return
    }

    if (formValues.title.trim().length < 3) {
      setFeedbackMessage('Informe um assunto com pelo menos 3 caracteres.')
      return
    }

    if (formValues.description.trim().length < 10) {
      setFeedbackMessage('Descreva o chamado com pelo menos 10 caracteres.')
      return
    }

    try {
      setIsSubmitting(true)
      setFeedbackMessage('')
      await onCreateTicket({
        title: formValues.title,
        description: formValues.description,
        priorityCode: formValues.priorityCode,
        sectorId: formValues.sectorId,
      })
      setFormValues({
        sectorId: '',
        priorityCode: '',
        title: '',
        copyEmail: '',
        description: '',
      })
      setFeedbackMessage('Chamado criado com sucesso.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="newTicket"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="newTicket"
          {...headerProps}
          isTeamRole={isTeamRole(userRole)}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--form">
            <div className="home-content__header">
              <div className="home-content__heading">
                <span className="home-content__eyebrow">Abertura de chamado</span>
                <h1>{activeContent.contentTitle}</h1>
                <p>{activeContent.contentText}</p>
              </div>
            </div>

            <form className="ticket-form" onSubmit={handleSubmit}>
              <div className="ticket-form__grid">
                <label className="ticket-field">
                  <span>Setor</span>
                  <div className="ticket-field__control ticket-field__control--select">
                    <select
                      value={formValues.sectorId}
                      onChange={(event) => handleChange('sectorId', event.target.value)}
                    >
                      {availableSectors.length > 0 ? (
                        <>
                          <option disabled value="">
                            Selecione o setor...
                          </option>
                          {availableSectors.map((sector) => (
                            <option key={sector.id} value={sector.id}>
                              {sector.name}
                            </option>
                          ))}
                        </>
                      ) : (
                        <option disabled value="">
                          Nenhum setor disponível
                        </option>
                      )}
                    </select>
                    <ChevronDownIcon />
                  </div>
                </label>

                <label className="ticket-field">
                  <span>Prioridade</span>
                  <div className="ticket-field__control ticket-field__control--select">
                    <select
                      value={formValues.priorityCode}
                      onChange={(event) => handleChange('priorityCode', event.target.value)}
                    >
                      <option disabled value="">
                        Selecione a prioridade...
                      </option>
                      <option value="LOW">Baixa</option>
                      <option value="MEDIUM">Média</option>
                      <option value="HIGH">Alta</option>
                    </select>
                    <ChevronDownIcon />
                  </div>
                </label>
              </div>

              <label className="ticket-field">
                <span>Assunto</span>
                <div className="ticket-field__control">
                  <input
                    placeholder="Digite o assunto do chamado"
                    type="text"
                    value={formValues.title}
                    onChange={(event) => handleChange('title', event.target.value)}
                  />
                </div>
              </label>

              <label className="ticket-field">
                <span>Enviar Cópia</span>
                <div className="ticket-field__control">
                  <input
                    placeholder="Digite o email e pressione ENTER para adicioná-lo"
                    type="email"
                    value={formValues.copyEmail}
                    onChange={(event) => handleChange('copyEmail', event.target.value)}
                  />
                </div>
              </label>

              <label className="ticket-field">
                <span>Mensagem</span>
                <div className="ticket-field__control ticket-field__control--textarea">
                  <textarea
                    placeholder="Descreva aqui o seu chamado"
                    rows="6"
                    value={formValues.description}
                    onChange={(event) => handleChange('description', event.target.value)}
                  />
                </div>
              </label>

              {feedbackMessage ? <p className="team-feedback">{feedbackMessage}</p> : null}

              <div className="ticket-form__footer">
                <button className="ticket-form__attachment" type="button">
                  <PlusCircleIcon />
                  <span>Anexar Arquivos</span>
                </button>

                <button
                  className="ticket-form__submit"
                  type="submit"
                  disabled={isSubmitting || availableSectors.length === 0 || !currentUser?.email}
                >
                  {isSubmitting ? 'Criando...' : 'Criar Chamado'}
                </button>
              </div>
            </form>
          </div>
        </section>
      </div>
    </main>
  )
}

export default NewTicket
