import { useEffect, useMemo, useState } from 'react'
import {
  completeCalendarObligation,
  createCalendarObligation,
  deleteCalendarObligation,
  getCalendarObligations,
  updateCalendarObligation,
} from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import '../Home/Home.css'
import './Calendar.css'

const WEEK_DAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sab']

const EMPTY_FORM_VALUES = {
  title: '',
  description: '',
  dueAt: '',
  reminderAt: '',
  recipientDocumentNumber: '',
}

function Calendar({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
  onRefreshDashboardData,
  userRole = 'user',
}) {
  const [obligations, setObligations] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [processingObligationId, setProcessingObligationId] = useState('')
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [selectedMonth, setSelectedMonth] = useState(() => formatMonthInput(new Date()))
  const [pendingConfirmation, setPendingConfirmation] = useState(null)
  const [isConfirmingAction, setIsConfirmingAction] = useState(false)
  const [editingObligationId, setEditingObligationId] = useState('')
  const [formValues, setFormValues] = useState(EMPTY_FORM_VALUES)

  const isAdmin = userRole === 'admin'
  const fullDateTimeFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
      }),
    []
  )
  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat('pt-BR', {
        day: '2-digit',
        month: '2-digit',
      }),
    []
  )

  useEffect(() => {
    if (!currentUser?.email) {
      setObligations([])
      setFeedbackMessage('')
      setIsLoading(false)
      return undefined
    }

    let isCancelled = false

    async function loadObligations() {
      setIsLoading(true)
      setFeedbackMessage('')

      try {
        const response = await getCalendarObligations(currentUser.email)

        if (isCancelled) {
          return
        }

        setObligations(Array.isArray(response) ? response : [])
      } catch (error) {
        if (isCancelled) {
          return
        }

        setObligations([])
        setFeedbackMessage(error.message)
      } finally {
        if (!isCancelled) {
          setIsLoading(false)
        }
      }
    }

    loadObligations()

    return () => {
      isCancelled = true
    }
  }, [currentUser?.email])

  const monthObligations = useMemo(() => {
    const [year, month] = selectedMonth.split('-').map(Number)

    return obligations.filter((obligation) => {
      const dueDate = new Date(obligation.dueAt)
      return dueDate.getFullYear() === year && dueDate.getMonth() + 1 === month
    })
  }, [obligations, selectedMonth])

  const obligationsByDay = useMemo(() => {
    return monthObligations.reduce((result, obligation) => {
      const key = formatDayKey(new Date(obligation.dueAt))
      result[key] = [...(result[key] || []), obligation]
      return result
    }, {})
  }, [monthObligations])

  const monthDays = useMemo(() => {
    const [year, month] = selectedMonth.split('-').map(Number)
    const firstDay = new Date(year, month - 1, 1)
    const firstWeekDay = firstDay.getDay()
    const lastDayOfMonth = new Date(year, month, 0).getDate()
    const previousMonthLastDay = new Date(year, month - 1, 0).getDate()
    const days = []

    for (let index = firstWeekDay - 1; index >= 0; index -= 1) {
      days.push({
        key: `prev-${index}`,
        date: new Date(year, month - 2, previousMonthLastDay - index),
        isCurrentMonth: false,
      })
    }

    for (let day = 1; day <= lastDayOfMonth; day += 1) {
      days.push({
        key: `current-${day}`,
        date: new Date(year, month - 1, day),
        isCurrentMonth: true,
      })
    }

    while (days.length % 7 !== 0 || days.length < 35) {
      const nextDay = days.length - (firstWeekDay + lastDayOfMonth) + 1
      days.push({
        key: `next-${nextDay}`,
        date: new Date(year, month, nextDay),
        isCurrentMonth: false,
      })
    }

    return days
  }, [selectedMonth])

  const stats = useMemo(() => {
    return obligations.reduce(
      (summary, obligation) => {
        if (obligation.status === 'COMPLETED') {
          summary.completed += 1
        } else if (obligation.status === 'OVERDUE') {
          summary.overdue += 1
        } else if (obligation.status === 'DUE_TODAY') {
          summary.today += 1
        } else {
          summary.upcoming += 1
        }

        if (obligation.reminderActive) {
          summary.reminders += 1
        }

        return summary
      },
      {
        overdue: 0,
        today: 0,
        upcoming: 0,
        completed: 0,
        reminders: 0,
      }
    )
  }, [obligations])

  const reminderList = useMemo(() => {
    return obligations
      .filter(
        (obligation) =>
          obligation.status !== 'COMPLETED' &&
          (obligation.reminderActive ||
            obligation.status === 'OVERDUE' ||
            obligation.status === 'DUE_TODAY')
      )
      .slice(0, 8)
  }, [obligations])

  const upcomingList = useMemo(() => {
    return obligations
      .filter((obligation) => obligation.status === 'UPCOMING' || obligation.status === 'DUE_TODAY')
      .slice(0, 6)
  }, [obligations])

  function closeConfirmation() {
    if (isConfirmingAction) {
      return
    }

    setPendingConfirmation(null)
  }

  async function handleConfirmAction() {
    if (!pendingConfirmation?.onConfirm) {
      return
    }

    setIsConfirmingAction(true)

    try {
      await pendingConfirmation.onConfirm()
      setPendingConfirmation(null)
    } finally {
      setIsConfirmingAction(false)
    }
  }

  function updateFormValue(field, value) {
    setFormValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }))
  }

  function resetForm() {
    setEditingObligationId('')
    setFormValues(EMPTY_FORM_VALUES)
  }

  function startEditingObligation(obligation) {
    setEditingObligationId(obligation.id)
    setFormValues({
      title: obligation.title || '',
      description: obligation.description || '',
      dueAt: formatDateTimeLocal(obligation.dueAt),
      reminderAt: formatDateTimeLocal(obligation.reminderAt),
      recipientDocumentNumber: obligation.recipientDocumentNumber || '',
    })
    setSelectedMonth(formatMonthInput(new Date(obligation.dueAt)))
    setFeedbackMessage('')
  }

  async function reloadObligations() {
    if (!currentUser?.email) {
      return
    }

    const response = await getCalendarObligations(currentUser.email)
    setObligations(Array.isArray(response) ? response : [])
  }

  async function syncCalendarData() {
    await reloadObligations()

    if (onRefreshDashboardData && currentUser?.email) {
      await onRefreshDashboardData(currentUser.email)
    }
  }

  async function handleSubmitObligation(event) {
    event.preventDefault()

    if (!currentUser?.email || !formValues.title.trim() || !formValues.dueAt) {
      return
    }

    setIsSubmitting(true)
    setFeedbackMessage('')

    try {
      const payload = {
        title: formValues.title.trim(),
        description: formValues.description.trim() || null,
        dueAt: new Date(formValues.dueAt).toISOString(),
        reminderAt: formValues.reminderAt ? new Date(formValues.reminderAt).toISOString() : null,
        recipientDocumentNumber: formValues.recipientDocumentNumber.trim(),
      }

      if (editingObligationId) {
        await updateCalendarObligation(editingObligationId, {
          ...payload,
          updatedByEmail: currentUser.email,
        })
      } else {
        await createCalendarObligation({
          ...payload,
          createdByEmail: currentUser.email,
        })
      }

      resetForm()
      await syncCalendarData()
      setFeedbackMessage(
        editingObligationId
          ? 'Obrigação atualizada com sucesso no calendário.'
          : 'Obrigação criada com sucesso no calendário.'
      )
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  async function markAsCompleted(obligation) {
    if (!currentUser?.email) {
      return
    }

    setProcessingObligationId(obligation.id)
    setFeedbackMessage('')

    try {
      await completeCalendarObligation(obligation.id, currentUser.email)
      await syncCalendarData()
      setFeedbackMessage(`Obrigação "${obligation.title}" concluída com sucesso.`)
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingObligationId('')
    }
  }

  async function removeObligation(obligation) {
    if (!currentUser?.email) {
      return
    }

    setProcessingObligationId(obligation.id)
    setFeedbackMessage('')

    try {
      await deleteCalendarObligation(obligation.id, currentUser.email)
      if (editingObligationId === obligation.id) {
        resetForm()
      }

      await syncCalendarData()
      setFeedbackMessage(`Obrigação "${obligation.title}" excluída com sucesso.`)
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingObligationId('')
    }
  }

  function requestCompleteConfirmation(obligation) {
    setPendingConfirmation({
      title: 'Concluir obrigação',
      description: `Tem certeza que deseja marcar "${obligation.title}" como concluída?`,
      confirmLabel: 'Concluir',
      confirmVariant: 'primary',
      onConfirm: () => markAsCompleted(obligation),
    })
  }

  function requestDeleteConfirmation(obligation) {
    setPendingConfirmation({
      title: 'Excluir obrigação',
      description: `Tem certeza que deseja excluir "${obligation.title}" do calendário?`,
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => removeObligation(obligation),
    })
  }

  function formatDateTime(value) {
    if (!value) {
      return 'Não informado'
    }

    return fullDateTimeFormatter.format(new Date(value))
  }

  function shiftMonth(step) {
    const [year, month] = selectedMonth.split('-').map(Number)
    const nextDate = new Date(year, month - 1 + step, 1)
    setSelectedMonth(formatMonthInput(nextDate))
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="calendar"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header activeSection="calendar" {...headerProps} onSectionChange={onNavigatePage} />

        <section className="home-content">
          <div className="calendar-view">
            <div className="calendar-summary">
              <article className="calendar-summary__card is-overdue">
                <span>Prazos atrasados</span>
                <strong>{stats.overdue}</strong>
              </article>
              <article className="calendar-summary__card is-today">
                <span>Vencem hoje</span>
                <strong>{stats.today}</strong>
              </article>
              <article className="calendar-summary__card is-upcoming">
                <span>Próximas obrigações</span>
                <strong>{stats.upcoming}</strong>
              </article>
              <article className="calendar-summary__card is-reminder">
                <span>Lembretes ativos</span>
                <strong>{stats.reminders}</strong>
              </article>
            </div>

            {feedbackMessage ? <div className="calendar-feedback">{feedbackMessage}</div> : null}

            {isAdmin ? (
              <form className="calendar-form" onSubmit={handleSubmitObligation}>
                <div className="calendar-form__header">
                  <div>
                    <span className="home-panel__eyebrow">Calendário de obrigações</span>
                    <h2>{editingObligationId ? 'Editar prazo' : 'Adicionar prazo'}</h2>
                  </div>
                </div>

                <div className="ticket-form__grid">
                  <label className="ticket-field">
                    <span>Título da obrigação</span>
                    <div className="ticket-field__control">
                      <input
                        placeholder="Ex.: Entrega do relatório mensal"
                        type="text"
                        value={formValues.title}
                        onChange={(event) => updateFormValue('title', event.target.value)}
                      />
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>Prazo final</span>
                    <div className="ticket-field__control">
                      <input
                        type="datetime-local"
                        value={formValues.dueAt}
                        onChange={(event) => updateFormValue('dueAt', event.target.value)}
                      />
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>CPF do destinatário</span>
                    <div className="ticket-field__control">
                      <input
                        placeholder="Digite o CPF de qualquer usuário cadastrado"
                        type="text"
                        value={formValues.recipientDocumentNumber}
                        onChange={(event) =>
                          updateFormValue('recipientDocumentNumber', event.target.value)
                        }
                      />
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>Lembrete</span>
                    <div className="ticket-field__control">
                      <input
                        type="datetime-local"
                        value={formValues.reminderAt}
                        onChange={(event) => updateFormValue('reminderAt', event.target.value)}
                      />
                    </div>
                  </label>

                  <label className="ticket-field ticket-field--full">
                    <span>Descrição</span>
                    <div className="ticket-field__control">
                      <textarea
                        placeholder="Detalhes importantes sobre a obrigação e o que deve ser entregue."
                        rows={4}
                        value={formValues.description}
                        onChange={(event) => updateFormValue('description', event.target.value)}
                      />
                    </div>
                  </label>
                </div>

                <div className="calendar-form__footer">
                  <span>
                    Cadastre pelo CPF de qualquer usuário ativo no sistema. O lembrete aparece para
                    o destinatário quando a data de aviso for alcançada.
                  </span>
                  <div className="calendar-form__buttons">
                    {editingObligationId ? (
                      <button
                        className="calendar-form__button is-secondary"
                        type="button"
                        onClick={resetForm}
                        disabled={isSubmitting}
                      >
                        Cancelar edição
                      </button>
                    ) : null}
                    <button
                      className="team-invite__button"
                      type="submit"
                      disabled={
                        isSubmitting ||
                        !formValues.title.trim() ||
                        !formValues.dueAt ||
                        !formValues.recipientDocumentNumber.trim()
                      }
                    >
                      {isSubmitting
                        ? 'Salvando...'
                        : editingObligationId
                          ? 'Atualizar obrigação'
                          : 'Salvar obrigação'}
                    </button>
                  </div>
                </div>
              </form>
            ) : (
              <div className="calendar-feedback">
                As obrigações do calendário são cadastradas pelo administrador e aparecem aqui com
                lembretes para você.
              </div>
            )}

            <div className="calendar-layout">
              <div className="calendar-board">
                <div className="calendar-board__toolbar">
                  <div>
                    <span className="home-panel__eyebrow">Visão mensal</span>
                    <h2>{formatMonthTitle(selectedMonth)}</h2>
                  </div>

                  <div className="calendar-board__actions">
                    <button type="button" onClick={() => shiftMonth(-1)}>
                      Mês anterior
                    </button>
                    <input
                      type="month"
                      value={selectedMonth}
                      onChange={(event) => setSelectedMonth(event.target.value)}
                    />
                    <button type="button" onClick={() => shiftMonth(1)}>
                      Próximo mês
                    </button>
                  </div>
                </div>

                <div className="calendar-grid">
                  {WEEK_DAYS.map((day) => (
                    <div className="calendar-grid__weekday" key={day}>
                      {day}
                    </div>
                  ))}

                  {monthDays.map(({ key, date, isCurrentMonth }) => {
                    const dayKey = formatDayKey(date)
                    const dayObligations = obligationsByDay[dayKey] || []
                    const visibleDayObligations = dayObligations.slice(0, 3)

                    return (
                      <article
                        className={`calendar-grid__day${isCurrentMonth ? '' : ' is-outside'}`}
                        key={key}
                      >
                        <span className="calendar-grid__date">{date.getDate()}</span>

                        <div className="calendar-grid__items">
                          {visibleDayObligations.map((obligation) => (
                            <span
                              className={`calendar-chip is-${obligation.status.toLowerCase()}`}
                              key={obligation.id}
                              title={obligation.title}
                            >
                              {obligation.title}
                            </span>
                          ))}

                          {dayObligations.length > visibleDayObligations.length ? (
                            <span className="calendar-grid__more">
                              +{dayObligations.length - visibleDayObligations.length} a mais
                            </span>
                          ) : null}
                        </div>
                      </article>
                    )
                  })}
                </div>
              </div>

              <aside className="calendar-sidepanels">
                <div className="calendar-panel">
                  <div className="calendar-panel__header">
                    <div>
                      <span className="home-panel__eyebrow">Lembretes de prazo</span>
                      <h2>Avisos ativos</h2>
                    </div>
                  </div>

                  <div className="calendar-panel__list">
                    {reminderList.length > 0 ? (
                      reminderList.map((obligation) => (
                        <article className="calendar-task-card" key={`reminder-${obligation.id}`}>
                          <div className="calendar-task-card__top">
                            <strong>{obligation.title}</strong>
                            <span className={`calendar-badge is-${obligation.status.toLowerCase()}`}>
                              {getStatusLabel(obligation.status)}
                            </span>
                          </div>
                          <span>{formatDateTime(obligation.dueAt)}</span>
                          <small>
                            Destinatário: {obligation.recipientName}
                            {obligation.recipientDocumentNumber
                              ? ` • CPF ${formatCpf(obligation.recipientDocumentNumber)}`
                              : ''}
                          </small>
                          {obligation.description ? <p>{obligation.description}</p> : null}
                          <div className="calendar-task-card__actions">
                            {obligation.status !== 'COMPLETED' ? (
                              <button
                                type="button"
                                onClick={() => requestCompleteConfirmation(obligation)}
                                disabled={processingObligationId === obligation.id}
                              >
                                Concluir
                              </button>
                            ) : null}
                            {isAdmin ? (
                              <>
                                <button
                                  type="button"
                                  onClick={() => startEditingObligation(obligation)}
                                  disabled={processingObligationId === obligation.id}
                                >
                                  Editar
                                </button>
                                <button
                                  className="is-danger"
                                  type="button"
                                  onClick={() => requestDeleteConfirmation(obligation)}
                                  disabled={processingObligationId === obligation.id}
                                >
                                  Excluir
                                </button>
                              </>
                            ) : null}
                          </div>
                        </article>
                      ))
                    ) : (
                      <span className="team-panel__empty">
                        Nenhum lembrete ativo no momento.
                      </span>
                    )}
                  </div>
                </div>

                <div className="calendar-panel">
                  <div className="calendar-panel__header">
                    <div>
                      <span className="home-panel__eyebrow">Agenda da empresa</span>
                      <h2>Próximos vencimentos</h2>
                    </div>
                  </div>

                  <div className="calendar-panel__list">
                    {upcomingList.length > 0 ? (
                      upcomingList.map((obligation) => (
                        <article className="calendar-task-card" key={obligation.id}>
                          <div className="calendar-task-card__top">
                            <strong>{obligation.title}</strong>
                            <span className={`calendar-badge is-${obligation.status.toLowerCase()}`}>
                              {getStatusLabel(obligation.status)}
                            </span>
                          </div>
                          <span>{dateFormatter.format(new Date(obligation.dueAt))}</span>
                          <small>
                            Cadastro por {obligation.createdByName} • Destinatário:{' '}
                            {obligation.recipientName}
                          </small>
                          <div className="calendar-task-card__actions">
                            {isAdmin ? (
                              <button
                                type="button"
                                onClick={() => startEditingObligation(obligation)}
                                disabled={processingObligationId === obligation.id}
                              >
                                Editar
                              </button>
                            ) : null}
                            {obligation.status !== 'COMPLETED' ? (
                              <button
                                type="button"
                                onClick={() => requestCompleteConfirmation(obligation)}
                                disabled={processingObligationId === obligation.id}
                              >
                                Concluir
                              </button>
                            ) : null}
                            {isAdmin ? (
                              <button
                                className="is-danger"
                                type="button"
                                onClick={() => requestDeleteConfirmation(obligation)}
                                disabled={processingObligationId === obligation.id}
                              >
                                Excluir
                              </button>
                            ) : null}
                          </div>
                        </article>
                      ))
                    ) : (
                      <span className="team-panel__empty">
                        {isLoading ? 'Carregando obrigações...' : 'Nenhum prazo cadastrado.'}
                      </span>
                    )}
                  </div>
                </div>
              </aside>
            </div>
          </div>
        </section>
      </div>

      <ConfirmActionModal
        confirmLabel={pendingConfirmation?.confirmLabel}
        confirmVariant={pendingConfirmation?.confirmVariant}
        description={pendingConfirmation?.description}
        isLoading={isConfirmingAction}
        isOpen={Boolean(pendingConfirmation)}
        title={pendingConfirmation?.title}
        onCancel={closeConfirmation}
        onConfirm={handleConfirmAction}
      />
    </main>
  )
}

function getStatusLabel(status) {
  if (status === 'COMPLETED') {
    return 'Concluído'
  }

  if (status === 'OVERDUE') {
    return 'Atrasado'
  }

  if (status === 'DUE_TODAY') {
    return 'Vence hoje'
  }

  return 'Próximo'
}

function formatDayKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatMonthInput(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

function formatMonthTitle(value) {
  const [year, month] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('pt-BR', {
    month: 'long',
    year: 'numeric',
  }).format(new Date(year, month - 1, 1))
}

function formatDateTimeLocal(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function formatCpf(value) {
  const digits = String(value || '').replace(/\D/g, '')

  if (digits.length !== 11) {
    return value || ''
  }

  return digits.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4')
}

export default Calendar
