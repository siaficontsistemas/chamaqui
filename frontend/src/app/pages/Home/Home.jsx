import { useEffect, useMemo, useState } from 'react'
import { getTickets } from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import TicketListPagination from '../../components/TicketListPagination/TicketListPagination'
import { truncateTicketTitle } from '../../utils/truncateTicketTitle'

import { SearchIcon } from '../../dashboardIcons'
import './Home.css'

const ITEMS_PER_PAGE = 20
const AUTO_REFRESH_INTERVAL_MS = 5000

function Home({
  currentUser,
  headerProps,
  isTicketSummaryLoading,
  navigationGroups,
  onDeleteTickets,
  onNavigatePage,
  onOpenTicket,
  userRole,
}) {
  const [tickets, setTickets] = useState([])
  const [searchValue, setSearchValue] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [selectedTicketIds, setSelectedTicketIds] = useState([])
  const [isDeleteConfirmationOpen, setIsDeleteConfirmationOpen] = useState(false)
  const [isDeletingTickets, setIsDeletingTickets] = useState(false)
  const normalizedSearchValue = searchValue.trim().toLowerCase()
  const canDeleteTickets = userRole === 'admin'
  const formattedDate = useMemo(
    () =>
      new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
      }),
    []
  )
  const filteredTickets = useMemo(() => {
    if (!normalizedSearchValue) {
      return tickets
    }

    return tickets.filter((ticket) =>
      [ticket.protocol, ticket.title, ticket.statusName].some((value) =>
        value?.toLowerCase().includes(normalizedSearchValue)
      )
    )
  }, [normalizedSearchValue, tickets])
  const resolvedTicketSummary = useMemo(() => {
    return tickets.reduce(
      (summary, ticket) => {
        if (ticket.statusCode === 'CLOSED') {
          summary.closed += 1
        } else if (ticket.statusCode === 'OPEN') {
          summary.open += 1
        } else {
          summary.inProgress += 1
        }

        summary.total += 1
        return summary
      },
      { total: 0, open: 0, inProgress: 0, closed: 0 }
    )
  }, [tickets])
  const totalPages = Math.max(1, Math.ceil(filteredTickets.length / ITEMS_PER_PAGE))
  const safeCurrentPage = Math.min(currentPage, totalPages)
  const paginatedTickets = useMemo(() => {
    const startIndex = (safeCurrentPage - 1) * ITEMS_PER_PAGE
    return filteredTickets.slice(startIndex, startIndex + ITEMS_PER_PAGE)
  }, [filteredTickets, safeCurrentPage])
  const selectedTicketsCount = selectedTicketIds.filter((ticketId) =>
    tickets.some((ticket) => ticket.id === ticketId)
  ).length
  const areAllVisibleTicketsSelected =
    paginatedTickets.length > 0 &&
    paginatedTickets.every((ticket) => selectedTicketIds.includes(ticket.id))

  useEffect(() => {
    if (!currentUser?.email) {
      setTickets([])
      setErrorMessage('')
      setIsLoading(false)
      return undefined
    }

    let isCancelled = false

    async function loadTickets({ silently = false } = {}) {
      if (!silently) {
        setIsLoading(true)
        setErrorMessage('')
      }

      try {
        const response = await getTickets(currentUser.email)

        if (isCancelled) {
          return
        }

        setTickets(Array.isArray(response) ? response : [])
      } catch (error) {
        if (isCancelled) {
          return
        }

        setErrorMessage(error.message)
      } finally {
        if (!isCancelled && !silently) {
          setIsLoading(false)
        }
      }
    }

    loadTickets()
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'hidden') {
        return
      }

      loadTickets({ silently: true })
    }, AUTO_REFRESH_INTERVAL_MS)
    const handleWindowFocus = () => {
      loadTickets({ silently: true })
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        loadTickets({ silently: true })
      }
    }

    window.addEventListener('focus', handleWindowFocus)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      isCancelled = true
      window.clearInterval(intervalId)
      window.removeEventListener('focus', handleWindowFocus)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [currentUser?.email])

  useEffect(() => {
    setCurrentPage(1)
  }, [normalizedSearchValue])

  useEffect(() => {
    if (currentPage !== safeCurrentPage) {
      setCurrentPage(safeCurrentPage)
    }
  }, [currentPage, safeCurrentPage])

  useEffect(() => {
    setSelectedTicketIds((currentSelection) =>
      currentSelection.filter((ticketId) => tickets.some((ticket) => ticket.id === ticketId))
    )
  }, [tickets])

  function getStatusClass(statusCode) {
    if (statusCode === 'CLOSED') {
      return 'ticket-list__status--closed'
    }

    if (statusCode === 'IN_PROGRESS' || statusCode === 'IN_PROGRESS_TRANSFER_PENDING') {
      return 'ticket-list__status--in-progress'
    }

    if (statusCode === 'IN_PROGRESS_REQUESTER_REPLY') {
      return 'ticket-list__status--requester-reply'
    }

    return 'ticket-list__status--open'
  }

  function formatDate(value) {
    if (!value) {
      return 'Não informado'
    }

    return formattedDate.format(new Date(value))
  }

  function handleToggleTicketSelection(ticketId) {
    setSelectedTicketIds((currentSelection) =>
      currentSelection.includes(ticketId)
        ? currentSelection.filter((currentTicketId) => currentTicketId !== ticketId)
        : [...currentSelection, ticketId]
    )
  }

  function handleToggleVisibleTicketsSelection() {
    if (areAllVisibleTicketsSelected) {
      const visibleTicketIds = new Set(paginatedTickets.map((ticket) => ticket.id))
      setSelectedTicketIds((currentSelection) =>
        currentSelection.filter((ticketId) => !visibleTicketIds.has(ticketId))
      )
      return
    }

    setSelectedTicketIds((currentSelection) => [
      ...new Set([...currentSelection, ...paginatedTickets.map((ticket) => ticket.id)]),
    ])
  }

  function openDeleteConfirmation() {
    if (!canDeleteTickets || selectedTicketsCount === 0 || isDeletingTickets) {
      return
    }

    setIsDeleteConfirmationOpen(true)
  }

  function closeDeleteConfirmation() {
    if (isDeletingTickets) {
      return
    }

    setIsDeleteConfirmationOpen(false)
  }

  async function handleConfirmDeleteTickets() {
    if (!onDeleteTickets || selectedTicketsCount === 0) {
      return
    }

    setIsDeletingTickets(true)
    setErrorMessage('')

    try {
      await onDeleteTickets(selectedTicketIds)
      setSelectedTicketIds([])
      setIsDeleteConfirmationOpen(false)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsDeletingTickets(false)
    }
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="tickets"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="tickets"
          {...headerProps}
          isTicketSummaryLoading={isTicketSummaryLoading}
          onSectionChange={onNavigatePage}
          ticketSummary={resolvedTicketSummary}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--ticket-list">
            <div className="ticket-list">
              <div className="ticket-list__toolbar">
                <label className="ticket-list__search" htmlFor="ticket-search-home">
                  <SearchIcon />
                  <input
                    id="ticket-search-home"
                    placeholder="Buscar chamados"
                    type="text"
                    value={searchValue}
                    onChange={(event) => setSearchValue(event.target.value)}
                  />
                </label>

                {!isLoading && !errorMessage ? (
                  <TicketListPagination
                    currentPage={safeCurrentPage}
                    onPageChange={setCurrentPage}
                    pageSize={ITEMS_PER_PAGE}
                    totalItems={filteredTickets.length}
                  />
                ) : null}
                {canDeleteTickets ? (
                  <button
                    className="ticket-list__bulk-action"
                    type="button"
                    onClick={openDeleteConfirmation}
                    disabled={selectedTicketsCount === 0 || isDeletingTickets}
                  >
                    {isDeletingTickets
                      ? 'Excluindo...'
                      : selectedTicketsCount > 0
                        ? `Excluir selecionados (${selectedTicketsCount})`
                        : 'Excluir selecionados'}
                  </button>
                ) : null}
              </div>

              <div className="ticket-list__table">
                {paginatedTickets.length > 0 ? (
                  <>
                    <div className={`ticket-list__head${canDeleteTickets ? ' ticket-list__head--with-select' : ''}`}>
                      {canDeleteTickets ? (
                        <span className="ticket-list__select-cell">
                          <input
                            type="checkbox"
                            checked={areAllVisibleTicketsSelected}
                            onChange={handleToggleVisibleTicketsSelection}
                            aria-label="Selecionar chamados visíveis"
                          />
                        </span>
                      ) : null}
                      <span>Protocolo</span>
                      <span>Assunto</span>
                      <span>Status</span>
                      <span>Data</span>
                      <span>Ação</span>
                    </div>

                    {paginatedTickets.map((ticket) => (
                      <div className={`ticket-list__row${canDeleteTickets ? ' ticket-list__row--with-select' : ''}`} key={ticket.id}>
                        {canDeleteTickets ? (
                          <span className="ticket-list__select-cell">
                            <input
                              type="checkbox"
                              checked={selectedTicketIds.includes(ticket.id)}
                              onChange={() => handleToggleTicketSelection(ticket.id)}
                              aria-label={`Selecionar chamado ${ticket.protocol}`}
                            />
                          </span>
                        ) : null}
                        <span>{ticket.protocol}</span>
                        <span title={ticket.title || ''}>{truncateTicketTitle(ticket.title)}</span>
                        <span className="ticket-list__status-cell">
                          <strong
                            className={`ticket-list__status ${getStatusClass(ticket.statusCode)}`}
                          >
                            {ticket.statusName}
                          </strong>
                        </span>
                        <span>{formatDate(ticket.openedAt)}</span>
                        <span>
                          <button
                            className="ticket-list__action"
                            type="button"
                            onClick={() => onOpenTicket?.(ticket, 'tickets')}
                          >
                            Ver chamado
                          </button>
                        </span>
                      </div>
                    ))}
                  </>
                ) : (
                  <div className="ticket-list__empty">
                    {isLoading
                      ? 'Carregando chamados...'
                      : errorMessage || 'Nenhum chamado criado até o momento.'}
                  </div>
                )}
              </div>
            </div>
          </div>
        </section>
      </div>
      <ConfirmActionModal
        isOpen={isDeleteConfirmationOpen}
        title="Excluir chamados"
        description={[
          `Tem certeza que deseja excluir ${selectedTicketsCount} chamado(s)?`,
          'Se algum deles ainda estiver aberto, o solicitante será informado que o chamado foi fechado antes da exclusão.',
        ]}
        confirmLabel={isDeletingTickets ? 'Excluindo...' : 'Excluir chamados'}
        confirmVariant="danger"
        onCancel={closeDeleteConfirmation}
        onConfirm={handleConfirmDeleteTickets}
        isProcessing={isDeletingTickets}
      />
    </main>
  )
}

export default Home
