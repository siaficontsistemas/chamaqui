import { useEffect, useMemo, useState } from 'react'
import { getTickets } from '../../api'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import TicketListPagination from '../../components/TicketListPagination/TicketListPagination'

import { SearchIcon } from '../../dashboardIcons'
import './Home.css'

const ITEMS_PER_PAGE = 20

function Home({
  currentUser,
  headerProps,
  isTicketSummaryLoading,
  navigationGroups,
  onNavigatePage,
  onOpenTicket,
}) {
  const [tickets, setTickets] = useState([])
  const [searchValue, setSearchValue] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const normalizedSearchValue = searchValue.trim().toLowerCase()
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

  useEffect(() => {
    if (!currentUser?.email) {
      setTickets([])
      setErrorMessage('')
      setIsLoading(false)
      return undefined
    }

    let isCancelled = false

    async function loadTickets() {
      setIsLoading(true)
      setErrorMessage('')

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

        setTickets([])
        setErrorMessage(error.message)
      } finally {
        if (!isCancelled) {
          setIsLoading(false)
        }
      }
    }

    loadTickets()

    return () => {
      isCancelled = true
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
              </div>

              <div className="ticket-list__table">
                {paginatedTickets.length > 0 ? (
                  <>
                    <div className="ticket-list__head">
                      <span>Protocolo</span>
                      <span>Assunto</span>
                      <span>Status</span>
                      <span>Data</span>
                      <span>Ação</span>
                    </div>

                    {paginatedTickets.map((ticket) => (
                      <div className="ticket-list__row" key={ticket.id}>
                        <span>{ticket.protocol}</span>
                        <span>{ticket.title}</span>
                        <span>
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
    </main>
  )
}

export default Home
