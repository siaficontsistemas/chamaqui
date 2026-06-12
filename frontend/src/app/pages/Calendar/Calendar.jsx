import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  completeCalendarObligation,
  createCalendarObligation,
  deleteCalendarObligation,
  getCalendarLinkedCompanies,
  getCalendarObligations,
  getProfile,
  getTeamMembers,
  moveCalendarObligationCompany,
  searchCalendarTickets,
  updateCalendarObligation,
} from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getTicketPath } from '../../routes'
import '../Home/Home.css'
import './Calendar.css'

const WEEK_DAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sab']
const KANBAN_REFRESH_INTERVAL_MS = 15000
const TICKET_SEARCH_PAGE_SIZE = 10
const ALL_KANBAN_FILTER_VALUE = '__ALL__'
const PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'Baixa' },
  { value: 'MEDIUM', label: 'Media' },
  { value: 'HIGH', label: 'Alta' },
]

const EMPTY_FORM_VALUES = {
  title: '',
  description: '',
  dueAt: '',
  reminderAt: '',
  priority: 'MEDIUM',
  linkedCompanyOwnerId: '',
  recipientDocumentNumbers: '',
}

function Calendar({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
  onRefreshDashboardData,
  userRole = 'user',
}) {
  const location = useLocation()
  const [obligations, setObligations] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [processingObligationId, setProcessingObligationId] = useState('')
  const [movingObligationId, setMovingObligationId] = useState('')
  const [linkedPeople, setLinkedPeople] = useState([])
  const [linkedCompanies, setLinkedCompanies] = useState([])
  const [isRecipientPickerOpen, setIsRecipientPickerOpen] = useState(false)
  const [isLinkedTicketPickerOpen, setIsLinkedTicketPickerOpen] = useState(false)
  const [linkedTicketSearch, setLinkedTicketSearch] = useState('')
  const [linkedTicketResults, setLinkedTicketResults] = useState([])
  const [selectedLinkedTickets, setSelectedLinkedTickets] = useState([])
  const [isLoadingLinkedTickets, setIsLoadingLinkedTickets] = useState(false)
  const [linkedTicketsOffset, setLinkedTicketsOffset] = useState(0)
  const [linkedTicketsHasMore, setLinkedTicketsHasMore] = useState(false)
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [selectedMonth, setSelectedMonth] = useState(() => formatMonthInput(new Date()))
  const [pendingConfirmation, setPendingConfirmation] = useState(null)
  const [isConfirmingAction, setIsConfirmingAction] = useState(false)
  const [editingObligationId, setEditingObligationId] = useState('')
  const [draggedObligationId, setDraggedObligationId] = useState('')
  const [dragOverCompanyId, setDragOverCompanyId] = useState('')
  const [kanbanFilters, setKanbanFilters] = useState({
    search: '',
    dueDate: '',
    priority: ALL_KANBAN_FILTER_VALUE,
    companyId: ALL_KANBAN_FILTER_VALUE,
  })
  const [formValues, setFormValues] = useState(EMPTY_FORM_VALUES)
  const highlightedObligationRef = useRef(null)
  const obligationFormRef = useRef(null)
  const focusedObligationId = useMemo(
    () => new URLSearchParams(location.search).get('obligationId') || '',
    [location.search]
  )

  const isAdmin = userRole === 'admin'
  const canMoveKanbanCards = userRole === 'admin' || userRole === 'employee'
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
      setLinkedCompanies([])
      setFeedbackMessage('')
      setIsLoading(false)
      return undefined
    }

    let isCancelled = false

    async function loadCalendarData(showLoader) {
      if (showLoader) {
        setIsLoading(true)
      }

      try {
        const [obligationsResponse, companiesResponse] = await Promise.all([
          getCalendarObligations(currentUser.email),
          getCalendarLinkedCompanies(currentUser.email),
        ])

        if (isCancelled) {
          return
        }

        setObligations(Array.isArray(obligationsResponse) ? obligationsResponse : [])
        setLinkedCompanies(Array.isArray(companiesResponse) ? companiesResponse : [])
      } catch (error) {
        if (isCancelled) {
          return
        }

        setObligations([])
        setLinkedCompanies([])
        setFeedbackMessage(error.message)
      } finally {
        if (!isCancelled && showLoader) {
          setIsLoading(false)
        }
      }
    }

    setFeedbackMessage('')
    loadCalendarData(true)

    const intervalId = window.setInterval(() => {
      loadCalendarData(false)
    }, KANBAN_REFRESH_INTERVAL_MS)

    return () => {
      isCancelled = true
      window.clearInterval(intervalId)
    }
  }, [currentUser?.email])

  useEffect(() => {
    if (!currentUser?.email || !isAdmin) {
      setLinkedPeople([])
      return undefined
    }

    let isCancelled = false

    async function loadLinkedPeople() {
      try {
        const [teamMembers, profile] = await Promise.all([
          getTeamMembers(currentUser.email),
          getProfile(currentUser.email),
        ])

        if (isCancelled) {
          return
        }

        const peopleByKey = new Map()

        if (profile?.documentNumber) {
          peopleByKey.set(profile.id || `profile-${profile.email}`, {
            id: profile.id || `profile-${profile.email}`,
            fullName: profile.fullName || 'Nome não informado',
            documentNumber: profile.documentNumber,
          })
        }

        ;(Array.isArray(teamMembers) ? teamMembers : []).forEach((member) => {
          const normalizedDocument = String(member?.documentNumber || '').replace(/\D/g, '')
          if (!normalizedDocument) {
            return
          }

          const memberId = member.userId || member.id || `${member.fullName}-${normalizedDocument}`
          peopleByKey.set(memberId, {
            id: memberId,
            fullName: member.fullName || member.name || 'Nome não informado',
            documentNumber: normalizedDocument,
          })
        })

        setLinkedPeople(
          Array.from(peopleByKey.values()).sort((firstPerson, secondPerson) =>
            firstPerson.fullName.localeCompare(secondPerson.fullName, 'pt-BR')
          )
        )
      } catch (_error) {
        if (!isCancelled) {
          setLinkedPeople([])
        }
      }
    }

    loadLinkedPeople()

    return () => {
      isCancelled = true
    }
  }, [currentUser?.email, isAdmin])

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
    return prioritizeObligations(
      obligations
      .filter(
        (obligation) =>
          obligation.status !== 'COMPLETED' &&
          (obligation.reminderActive ||
            obligation.status === 'OVERDUE' ||
            obligation.status === 'DUE_TODAY')
      )
      ,
      focusedObligationId,
      8
    )
  }, [focusedObligationId, obligations])

  const upcomingList = useMemo(() => {
    return prioritizeObligations(
      obligations.filter(
        (obligation) => obligation.status === 'UPCOMING' || obligation.status === 'DUE_TODAY'
      ),
      focusedObligationId,
      6
    )
  }, [focusedObligationId, obligations])
  const selectedRecipientDocuments = useMemo(
    () => parseRecipientDocumentNumbers(formValues.recipientDocumentNumbers),
    [formValues.recipientDocumentNumbers]
  )
  const recipientSearchTerm = useMemo(
    () => extractRecipientSearchTerm(formValues.recipientDocumentNumbers),
    [formValues.recipientDocumentNumbers]
  )
  const filteredLinkedPeople = useMemo(() => {
    const selectedDocuments = new Set(selectedRecipientDocuments)
    const normalizedSearchText = recipientSearchTerm.trim().toLowerCase()
    const normalizedSearchDigits = recipientSearchTerm.replace(/\D/g, '')

    return linkedPeople.filter((person) => {
      if (selectedDocuments.has(person.documentNumber)) {
        return false
      }

      if (!normalizedSearchText && !normalizedSearchDigits) {
        return true
      }

      const formattedDocument = formatCpf(person.documentNumber)
      return (
        person.fullName.toLowerCase().includes(normalizedSearchText) ||
        person.documentNumber.includes(normalizedSearchDigits) ||
        formattedDocument.includes(recipientSearchTerm)
      )
    })
  }, [linkedPeople, recipientSearchTerm, selectedRecipientDocuments])
  const availableLinkedTicketResults = useMemo(() => {
    const selectedIds = new Set(selectedLinkedTickets.map((ticket) => ticket.id))
    return linkedTicketResults.filter((ticket) => !selectedIds.has(ticket.id))
  }, [linkedTicketResults, selectedLinkedTickets])
  const kanbanCompanies = useMemo(() => {
    const companiesById = new Map()

    ;(Array.isArray(linkedCompanies) ? linkedCompanies : []).forEach((company) => {
      if (!company?.id) {
        return
      }

      companiesById.set(company.id, company)
    })

    if (currentUser?.id && !companiesById.has(currentUser.id)) {
      companiesById.set(currentUser.id, {
        id: currentUser.id,
        name: currentUser.companyName || currentUser.fullName || 'Minha empresa',
        companyType: currentUser.companyType || 'RESPONDER',
      })
    }

    ;(Array.isArray(obligations) ? obligations : []).forEach((obligation) => {
      if (!obligation?.linkedCompanyOwnerId || companiesById.has(obligation.linkedCompanyOwnerId)) {
        return
      }

      companiesById.set(obligation.linkedCompanyOwnerId, {
        id: obligation.linkedCompanyOwnerId,
        name: obligation.linkedCompanyName || 'Empresa vinculada',
        companyType: 'REQUESTER',
      })
    })

    return Array.from(companiesById.values()).sort((firstCompany, secondCompany) =>
      String(firstCompany.name || '').localeCompare(String(secondCompany.name || ''), 'pt-BR')
    )
  }, [currentUser?.companyName, currentUser?.companyType, currentUser?.fullName, currentUser?.id, linkedCompanies, obligations])
  const filteredKanbanObligations = useMemo(() => {
    const normalizedSearch = String(kanbanFilters.search || '').trim().toLowerCase()

    return obligations.filter((obligation) => {
      const matchesSearch =
        !normalizedSearch ||
        [obligation.title, obligation.description, obligation.createdByName, obligation.linkedCompanyName]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(normalizedSearch))

      const matchesDueDate =
        !kanbanFilters.dueDate ||
        formatDayKey(new Date(obligation.dueAt)) === kanbanFilters.dueDate

      const matchesPriority =
        kanbanFilters.priority === ALL_KANBAN_FILTER_VALUE ||
        obligation.priority === kanbanFilters.priority

      const matchesCompany =
        kanbanFilters.companyId === ALL_KANBAN_FILTER_VALUE ||
        obligation.linkedCompanyOwnerId === kanbanFilters.companyId

      return matchesSearch && matchesDueDate && matchesPriority && matchesCompany
    })
  }, [kanbanFilters.companyId, kanbanFilters.dueDate, kanbanFilters.priority, kanbanFilters.search, obligations])
  const kanbanColumns = useMemo(() => {
    return kanbanCompanies.map((company) => ({
      ...company,
      obligations: filteredKanbanObligations
        .filter((obligation) => obligation.linkedCompanyOwnerId === company.id)
        .sort((firstObligation, secondObligation) => new Date(firstObligation.dueAt) - new Date(secondObligation.dueAt)),
    }))
  }, [filteredKanbanObligations, kanbanCompanies])

  useEffect(() => {
    if (!focusedObligationId || obligations.length === 0) {
      return
    }

    const focusedObligation = obligations.find((obligation) => obligation.id === focusedObligationId)
    if (!focusedObligation?.dueAt) {
      return
    }

    setSelectedMonth(formatMonthInput(new Date(focusedObligation.dueAt)))
  }, [focusedObligationId, obligations])

  useEffect(() => {
    if (!focusedObligationId || !highlightedObligationRef.current) {
      return
    }

    const frameId = window.requestAnimationFrame(() => {
      highlightedObligationRef.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      })
    })

    return () => window.cancelAnimationFrame(frameId)
  }, [focusedObligationId, reminderList, upcomingList, selectedMonth])

  useEffect(() => {
    if (!currentUser?.email || !isAdmin || !isLinkedTicketPickerOpen) {
      setLinkedTicketResults([])
      setLinkedTicketsHasMore(false)
      setLinkedTicketsOffset(0)
      setIsLoadingLinkedTickets(false)
      return undefined
    }

    let isCancelled = false
    const timeoutId = window.setTimeout(async () => {
      setIsLoadingLinkedTickets(true)

      try {
        const response = await searchCalendarTickets(
          currentUser.email,
          linkedTicketSearch,
          0,
          TICKET_SEARCH_PAGE_SIZE
        )

        if (isCancelled) {
          return
        }

        setLinkedTicketResults(Array.isArray(response?.tickets) ? response.tickets : [])
        setLinkedTicketsHasMore(Boolean(response?.hasMore))
        setLinkedTicketsOffset(Array.isArray(response?.tickets) ? response.tickets.length : 0)
      } catch (_error) {
        if (!isCancelled) {
          setLinkedTicketResults([])
          setLinkedTicketsHasMore(false)
          setLinkedTicketsOffset(0)
        }
      } finally {
        if (!isCancelled) {
          setIsLoadingLinkedTickets(false)
        }
      }
    }, 250)

    return () => {
      isCancelled = true
      window.clearTimeout(timeoutId)
    }
  }, [currentUser?.email, isAdmin, isLinkedTicketPickerOpen, linkedTicketSearch])

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
    setIsRecipientPickerOpen(false)
    setIsLinkedTicketPickerOpen(false)
    setLinkedTicketSearch('')
    setLinkedTicketResults([])
    setSelectedLinkedTickets([])
    setLinkedTicketsOffset(0)
    setLinkedTicketsHasMore(false)
  }

  function startEditingObligation(obligation) {
    setEditingObligationId(obligation.id)
    setFormValues({
      title: obligation.title || '',
      description: obligation.description || '',
      dueAt: formatDateTimeLocal(obligation.dueAt),
      reminderAt: formatDateTimeLocal(obligation.reminderAt),
      priority: obligation.priority || 'MEDIUM',
      linkedCompanyOwnerId: obligation.linkedCompanyOwnerId || '',
      recipientDocumentNumbers: formatRecipientDocumentNumbers(
        obligation.recipientDocumentNumbers || []
      ),
    })
    setLinkedTicketSearch('')
    setLinkedTicketResults([])
    setSelectedLinkedTickets(Array.isArray(obligation.linkedTickets) ? obligation.linkedTickets : [])
    setLinkedTicketsOffset(0)
    setLinkedTicketsHasMore(false)
    setIsLinkedTicketPickerOpen(false)
    setSelectedMonth(formatMonthInput(new Date(obligation.dueAt)))
    setFeedbackMessage('')

    window.requestAnimationFrame(() => {
      obligationFormRef.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      })
    })
  }

  async function reloadObligations() {
    if (!currentUser?.email) {
      return
    }

    const [obligationsResponse, companiesResponse] = await Promise.all([
      getCalendarObligations(currentUser.email),
      getCalendarLinkedCompanies(currentUser.email),
    ])
    setObligations(Array.isArray(obligationsResponse) ? obligationsResponse : [])
    setLinkedCompanies(Array.isArray(companiesResponse) ? companiesResponse : [])
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
      const recipientDocumentNumbers = parseRecipientDocumentNumbers(
        formValues.recipientDocumentNumbers
      )
      const payload = {
        title: formValues.title.trim(),
        description: formValues.description.trim() || null,
        dueAt: new Date(formValues.dueAt).toISOString(),
        reminderAt: formValues.reminderAt ? new Date(formValues.reminderAt).toISOString() : null,
        priority: formValues.priority || 'MEDIUM',
        linkedCompanyOwnerId: formValues.linkedCompanyOwnerId || null,
        linkedTicketIds: selectedLinkedTickets.map((ticket) => ticket.id),
        recipientDocumentNumbers,
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

  function addRecipientDocument(documentNumber) {
    const nextDocumentNumbers = Array.from(new Set([...selectedRecipientDocuments, documentNumber]))
    const formattedRecipients = formatRecipientDocumentNumbers(nextDocumentNumbers)
    updateFormValue(
      'recipientDocumentNumbers',
      formattedRecipients ? `${formattedRecipients}, ` : ''
    )
    setIsRecipientPickerOpen(true)
  }

  function addLinkedTicket(ticket) {
    if (!ticket?.id) {
      return
    }

    setSelectedLinkedTickets((currentTickets) => {
      if (currentTickets.some((currentTicket) => currentTicket.id === ticket.id)) {
        return currentTickets
      }

      return [...currentTickets, ticket]
    })
    setLinkedTicketSearch('')
  }

  function removeLinkedTicket(ticketId) {
    setSelectedLinkedTickets((currentTickets) =>
      currentTickets.filter((ticket) => ticket.id !== ticketId)
    )
  }

  async function loadMoreLinkedTickets() {
    if (!currentUser?.email || !isAdmin || !linkedTicketsHasMore || isLoadingLinkedTickets) {
      return
    }

    setIsLoadingLinkedTickets(true)

    try {
      const response = await searchCalendarTickets(
        currentUser.email,
        linkedTicketSearch,
        linkedTicketsOffset,
        TICKET_SEARCH_PAGE_SIZE
      )
      const nextTickets = Array.isArray(response?.tickets) ? response.tickets : []

      setLinkedTicketResults((currentTickets) => {
        const ticketsById = new Map(currentTickets.map((ticket) => [ticket.id, ticket]))
        nextTickets.forEach((ticket) => ticketsById.set(ticket.id, ticket))
        return Array.from(ticketsById.values())
      })
      setLinkedTicketsHasMore(Boolean(response?.hasMore))
      setLinkedTicketsOffset((currentOffset) => currentOffset + nextTickets.length)
    } catch (_error) {
      setLinkedTicketsHasMore(false)
    } finally {
      setIsLoadingLinkedTickets(false)
    }
  }

  function updateKanbanFilter(field, value) {
    setKanbanFilters((currentFilters) => ({
      ...currentFilters,
      [field]: value,
    }))
  }

  async function handleMoveObligationToCompany(obligation, companyId) {
    if (!currentUser?.email || !obligation?.id || !companyId || obligation.linkedCompanyOwnerId === companyId) {
      return
    }

    setMovingObligationId(obligation.id)
    setFeedbackMessage('')

    try {
      const updatedObligation = await moveCalendarObligationCompany(obligation.id, {
        email: currentUser.email,
        linkedCompanyOwnerId: companyId,
      })

      setObligations((currentObligations) =>
        currentObligations.map((currentObligation) =>
          currentObligation.id === obligation.id ? updatedObligation : currentObligation
        )
      )
      setFeedbackMessage(`Obrigação "${obligation.title}" movida com sucesso no Kanban.`)

      if (onRefreshDashboardData && currentUser.email) {
        await onRefreshDashboardData(currentUser.email)
      }
    } catch (error) {
      setFeedbackMessage(error.message || 'Nao foi possivel mover a obrigacao entre as empresas.')
    } finally {
      setMovingObligationId('')
      setDraggedObligationId('')
      setDragOverCompanyId('')
    }
  }

  function renderLinkedTickets(linkedTickets, options = {}) {
    const tickets = Array.isArray(linkedTickets) ? linkedTickets : []
    const emptyMessage = options.emptyMessage || 'Nenhum chamado relacionado.'

    return (
      <div className={`calendar-linked-ticket-list${options.compact ? ' is-compact' : ''}`}>
        <div className="calendar-linked-ticket-list__header">
          <span className="calendar-linked-ticket-list__label">Chamados relacionados</span>
        </div>
        {tickets.length > 0 ? (
          <div className="calendar-linked-ticket-list__items">
            {tickets.map((ticket) => (
              <Link className="calendar-linked-ticket-list__item" key={ticket.id} to={getTicketPath(ticket.id)}>
                <strong>{ticket.protocol || 'Sem numero'}</strong>
                <span>{ticket.statusName} • {ticket.title}</span>
              </Link>
            ))}
          </div>
        ) : (
          <span className="calendar-linked-ticket-list__empty">{emptyMessage}</span>
        )}
      </div>
    )
  }

  function renderReminderBlock(obligation) {
    return (
      <div className="calendar-kanban__reminder-block">
        <span className="calendar-kanban__reminder-label">Lembretes de prazo</span>
        <strong>{obligation.reminderAt ? formatDateTime(obligation.reminderAt) : 'Nenhum lembrete configurado'}</strong>
      </div>
    )
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
              <form className="calendar-form" ref={obligationFormRef} onSubmit={handleSubmitObligation}>
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
                    <span>Empresa vinculada</span>
                    <div className="ticket-field__control">
                      <select
                        value={formValues.linkedCompanyOwnerId}
                        onChange={(event) => updateFormValue('linkedCompanyOwnerId', event.target.value)}
                      >
                        <option value="">Selecionar empresa</option>
                        {kanbanCompanies.map((company) => (
                          <option key={company.id} value={company.id}>
                            {company.name}
                          </option>
                        ))}
                      </select>
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>CPF dos destinatários</span>
                    <div className="ticket-field__control calendar-recipient-field">
                      <input
                        placeholder="Digite nome ou CPF e selecione na lista"
                        type="text"
                        value={formValues.recipientDocumentNumbers}
                        onFocus={() => setIsRecipientPickerOpen(true)}
                        onBlur={() => window.setTimeout(() => setIsRecipientPickerOpen(false), 120)}
                        onChange={(event) =>
                          updateFormValue('recipientDocumentNumbers', event.target.value)
                        }
                      />

                      {isRecipientPickerOpen ? (
                        <div className="calendar-recipient-picker">
                          <div className="calendar-recipient-picker__header">
                            <strong>Pessoas ligadas a empresa</strong>
                            <span>{filteredLinkedPeople.length} disponivel(is)</span>
                          </div>

                          <div className="calendar-recipient-picker__list">
                            {filteredLinkedPeople.length > 0 ? (
                              filteredLinkedPeople.map((person) => (
                                <button
                                  className="calendar-recipient-picker__item"
                                  key={person.id}
                                  type="button"
                                  onMouseDown={(event) => {
                                    event.preventDefault()
                                    addRecipientDocument(person.documentNumber)
                                  }}
                                >
                                  <strong>{person.fullName}</strong>
                                  <span>CPF {formatCpf(person.documentNumber)}</span>
                                </button>
                              ))
                            ) : (
                              <div className="calendar-recipient-picker__empty">
                                Nenhuma pessoa encontrada para esse filtro.
                              </div>
                            )}
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>Prioridade</span>
                    <div className="ticket-field__control">
                      <select
                        value={formValues.priority}
                        onChange={(event) => updateFormValue('priority', event.target.value)}
                      >
                        {PRIORITY_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
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

                  <div className="ticket-field ticket-field--full">
                    <span>Chamados relacionados</span>
                    <div className="ticket-field__control calendar-linked-tickets">
                      <button
                        className={`calendar-linked-tickets__toggle${isLinkedTicketPickerOpen ? ' is-open' : ''}`}
                        type="button"
                        onClick={() => setIsLinkedTicketPickerOpen((currentValue) => !currentValue)}
                      >
                        <div className="calendar-linked-tickets__toggle-content">
                          <strong>Chamados relacionados</strong>
                          <span>
                            {selectedLinkedTickets.length > 0
                              ? `${selectedLinkedTickets.length} chamado(s) selecionado(s)`
                              : 'Clique para exibir ou ocultar as opções'}
                          </span>
                        </div>
                        <span>{isLinkedTicketPickerOpen ? 'Ocultar' : 'Selecionar'}</span>
                      </button>

                      {selectedLinkedTickets.length > 0 ? (
                        <div className="calendar-linked-tickets__selected">
                          {selectedLinkedTickets.map((ticket) => (
                            <button
                              className="calendar-linked-tickets__chip"
                              key={ticket.id}
                              type="button"
                              onClick={() => removeLinkedTicket(ticket.id)}
                            >
                              <span>{ticket.protocol} • {ticket.title}</span>
                              <strong>remover</strong>
                            </button>
                          ))}
                        </div>
                      ) : (
                        <span className="calendar-linked-tickets__empty">
                          Nenhum chamado vinculado ainda.
                        </span>
                      )}

                      {isLinkedTicketPickerOpen ? (
                        <>
                          <input
                            type="text"
                            placeholder="Buscar por numero, titulo ou responsavel"
                            value={linkedTicketSearch}
                            onChange={(event) => setLinkedTicketSearch(event.target.value)}
                          />

                          <div className="calendar-linked-tickets__results">
                            {availableLinkedTicketResults.length > 0 ? (
                              availableLinkedTicketResults.map((ticket) => (
                                <button
                                  className="calendar-linked-tickets__result"
                                  key={ticket.id}
                                  type="button"
                                  onClick={() => addLinkedTicket(ticket)}
                                >
                                  <strong>{ticket.protocol} • {ticket.title}</strong>
                                  <span>
                                    {ticket.statusName} • Responsável: {ticket.responsibleName || 'Não informado'}
                                  </span>
                                </button>
                              ))
                            ) : (
                              <span className="calendar-linked-tickets__empty">
                                {isLoadingLinkedTickets
                                  ? 'Buscando chamados...'
                                  : 'Nenhum chamado encontrado para esse filtro.'}
                              </span>
                            )}

                            {linkedTicketsHasMore ? (
                              <button
                                className="calendar-linked-tickets__more"
                                type="button"
                                onClick={loadMoreLinkedTickets}
                                disabled={isLoadingLinkedTickets}
                              >
                                {isLoadingLinkedTickets ? 'Carregando...' : 'Carregar mais chamados'}
                              </button>
                            ) : null}
                          </div>
                        </>
                      ) : null}
                    </div>
                  </div>
                </div>

                <div className="calendar-form__footer">
                  <span>
                    Cadastre um ou mais CPFs de usuários ativos no sistema. O lembrete aparece para
                    todos os destinatários quando a data de aviso for alcançada.
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
                        !formValues.recipientDocumentNumbers.trim()
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

            <section className="calendar-kanban">
              <div className="calendar-kanban__header">
                <div>
                  <span className="home-panel__eyebrow">Quadro Kanban</span>
                  <h2>Empresas vinculadas e obrigações</h2>
                </div>
              </div>

              <div className="calendar-kanban__filters">
                <label className="ticket-field">
                  <span>Buscar obrigação</span>
                  <div className="ticket-field__control">
                    <input
                      type="text"
                      placeholder="Nome, descrição ou responsável"
                      value={kanbanFilters.search}
                      onChange={(event) => updateKanbanFilter('search', event.target.value)}
                    />
                  </div>
                </label>

                <label className="ticket-field">
                  <span>Data de vencimento</span>
                  <div className="ticket-field__control">
                    <input
                      type="date"
                      value={kanbanFilters.dueDate}
                      onChange={(event) => updateKanbanFilter('dueDate', event.target.value)}
                    />
                  </div>
                </label>

                <label className="ticket-field">
                  <span>Prioridade</span>
                  <div className="ticket-field__control">
                    <select
                      value={kanbanFilters.priority}
                      onChange={(event) => updateKanbanFilter('priority', event.target.value)}
                    >
                      <option value={ALL_KANBAN_FILTER_VALUE}>Todas</option>
                      {PRIORITY_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </label>

                <label className="ticket-field">
                  <span>Empresa</span>
                  <div className="ticket-field__control">
                    <select
                      value={kanbanFilters.companyId}
                      onChange={(event) => updateKanbanFilter('companyId', event.target.value)}
                    >
                      <option value={ALL_KANBAN_FILTER_VALUE}>Todas</option>
                      {kanbanCompanies.map((company) => (
                        <option key={company.id} value={company.id}>
                          {company.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </label>
              </div>

              <div className="calendar-kanban__board">
                {kanbanColumns.map((company) => (
                  <section
                    className={`calendar-kanban__column${dragOverCompanyId === company.id ? ' is-drag-over' : ''}`}
                    key={company.id}
                    onDragOver={(event) => {
                      if (!canMoveKanbanCards) {
                        return
                      }

                      event.preventDefault()
                      setDragOverCompanyId(company.id)
                    }}
                    onDragLeave={() => {
                      if (dragOverCompanyId === company.id) {
                        setDragOverCompanyId('')
                      }
                    }}
                    onDrop={async (event) => {
                      if (!canMoveKanbanCards) {
                        return
                      }

                      event.preventDefault()
                      const draggedObligation = obligations.find((obligation) => obligation.id === draggedObligationId)
                      await handleMoveObligationToCompany(draggedObligation, company.id)
                    }}
                  >
                    <header className="calendar-kanban__column-header">
                      <strong>{company.name}</strong>
                      <span>{company.obligations.length} obrigação(ões)</span>
                    </header>

                    <div className="calendar-kanban__column-list">
                      {company.obligations.length > 0 ? (
                        company.obligations.map((obligation) => (
                          <article
                            className="calendar-kanban__card"
                            draggable={canMoveKanbanCards}
                            key={`kanban-${obligation.id}`}
                            onDragStart={() => setDraggedObligationId(obligation.id)}
                            onDragEnd={() => {
                              setDraggedObligationId('')
                              setDragOverCompanyId('')
                            }}
                          >
                            <div className="calendar-kanban__card-top">
                              <strong>{obligation.title}</strong>
                              <span className={`calendar-badge is-${obligation.status.toLowerCase()}`}>
                                {getStatusLabel(obligation.status)}
                              </span>
                            </div>

                            <div className="calendar-kanban__card-meta">
                              <span>Vence em {formatDateTime(obligation.dueAt)}</span>
                              <span>Prioridade {getPriorityLabel(obligation.priority)}</span>
                              <span>Responsável: {formatRecipientNames(obligation.recipientNames)}</span>
                            </div>

                            {renderLinkedTickets(obligation.linkedTickets, {
                              compact: true,
                              emptyMessage: 'Nenhum chamado relacionado a esta obrigação.',
                            })}

                            <div className="calendar-kanban__card-actions">
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

                              {canMoveKanbanCards ? (
                                <label className="calendar-kanban__move-control">
                                  <span>Mover para</span>
                                  <select
                                    value={obligation.linkedCompanyOwnerId}
                                    onChange={(event) =>
                                      handleMoveObligationToCompany(obligation, event.target.value)
                                    }
                                    disabled={movingObligationId === obligation.id}
                                  >
                                    {kanbanCompanies.map((option) => (
                                      <option key={option.id} value={option.id}>
                                        {option.name}
                                      </option>
                                    ))}
                                  </select>
                                </label>
                              ) : null}
                            </div>

                            {renderReminderBlock(obligation)}
                          </article>
                        ))
                      ) : (
                        <span className="calendar-kanban__empty">Nenhuma obrigação nesta coluna.</span>
                      )}
                    </div>
                  </section>
                ))}
              </div>
            </section>

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
                    const visibleDayObligations = dayObligations.slice(0, 1)

                    return (
                      <article
                        className={`calendar-grid__day${isCurrentMonth ? '' : ' is-outside'}`}
                        key={key}
                      >
                        <span className="calendar-grid__date">{date.getDate()}</span>

                        <div className="calendar-grid__items">
                          {visibleDayObligations.map((obligation) => (
                            <span
                              className={`calendar-chip is-${obligation.status.toLowerCase()}${focusedObligationId === obligation.id ? ' is-highlighted' : ''}`}
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
                        <article
                          className={`calendar-task-card${focusedObligationId === obligation.id ? ' is-highlighted' : ''}`}
                          key={`reminder-${obligation.id}`}
                          ref={focusedObligationId === obligation.id ? highlightedObligationRef : null}
                        >
                          <div className="calendar-task-card__top">
                            <strong>{obligation.title}</strong>
                            <span className={`calendar-badge is-${obligation.status.toLowerCase()}`}>
                              {getStatusLabel(obligation.status)}
                            </span>
                          </div>
                          <span>{formatDateTime(obligation.dueAt)}</span>
                          <small>
                            Destinatários: {formatRecipientNames(obligation.recipientNames)}
                            {obligation.recipientDocumentNumbers?.length
                              ? ` • CPF ${formatRecipientCpfList(obligation.recipientDocumentNumbers)}`
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
                        <article
                          className={`calendar-task-card${focusedObligationId === obligation.id ? ' is-highlighted' : ''}`}
                          key={obligation.id}
                          ref={focusedObligationId === obligation.id ? highlightedObligationRef : null}
                        >
                          <div className="calendar-task-card__top">
                            <strong>{obligation.title}</strong>
                            <span className={`calendar-badge is-${obligation.status.toLowerCase()}`}>
                              {getStatusLabel(obligation.status)}
                            </span>
                          </div>
                          <span>{dateFormatter.format(new Date(obligation.dueAt))}</span>
                          <small>
                            Cadastro por {obligation.createdByName} • Destinatários:{' '}
                            {formatRecipientNames(obligation.recipientNames)}
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

function getPriorityLabel(priority) {
  if (priority === 'HIGH') {
    return 'Alta'
  }

  if (priority === 'LOW') {
    return 'Baixa'
  }

  return 'Media'
}

function prioritizeObligations(obligations, focusedObligationId, limit) {
  if (!Array.isArray(obligations) || obligations.length === 0) {
    return []
  }

  if (!focusedObligationId) {
    return obligations.slice(0, limit)
  }

  const focusedObligation = obligations.find((obligation) => obligation.id === focusedObligationId)
  if (!focusedObligation) {
    return obligations.slice(0, limit)
  }

  return [focusedObligation, ...obligations.filter((obligation) => obligation.id !== focusedObligationId)].slice(
    0,
    limit
  )
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

function extractRecipientSearchTerm(value) {
  const tokens = String(value || '').split(',')
  return tokens[tokens.length - 1]?.trim() || ''
}

function parseRecipientDocumentNumbers(value) {
  return Array.from(
    new Set(
      String(value || '')
        .split(/[,\n;]+/)
        .map((item) => item.replace(/\D/g, ''))
        .filter(Boolean)
    )
  )
}

function formatRecipientDocumentNumbers(values) {
  return (Array.isArray(values) ? values : []).map((value) => formatCpf(value)).join(', ')
}

function formatRecipientCpfList(values) {
  return (Array.isArray(values) ? values : []).map((value) => formatCpf(value)).join(', ')
}

function formatRecipientNames(values) {
  const names = (Array.isArray(values) ? values : []).filter(Boolean)
  return names.length > 0 ? names.join(', ') : 'Não informado'
}

export default Calendar
