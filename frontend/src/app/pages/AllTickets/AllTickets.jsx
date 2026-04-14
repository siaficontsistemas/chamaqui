import { useEffect, useMemo, useState } from 'react'
import { getTickets } from '../../api'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'

import { SearchIcon } from '../../dashboardIcons'
import '../Home/Home.css'

function AllTickets({ currentUser, headerProps, navigationGroups, onNavigatePage, onOpenTicket }) {
  const [tickets, setTickets] = useState([])
  const [searchValue, setSearchValue] = useState('')
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
      <Sidebar activeSection="all" navigationGroups={navigationGroups} onSectionChange={onNavigatePage} />

      <div className="home-main-column">
        <Header
          activeSection="all"
          {...headerProps}
          onSectionChange={onNavigatePage}
          ticketSummary={resolvedTicketSummary}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--ticket-list">
            <div className="ticket-list">
              <div className="ticket-list__toolbar">
                <label className="ticket-list__search" htmlFor="ticket-search-all">
                  <SearchIcon />
                  <input
                    id="ticket-search-all"
                    placeholder="Buscar chamados"
                    type="text"
                    value={searchValue}
                    onChange={(event) => setSearchValue(event.target.value)}
                  />
                </label>

                {!isLoading && !errorMessage ? (
                  <div className="ticket-list__pagination">
                    <span>{filteredTickets.length} chamado(s)</span>
                  </div>
                ) : null}
              </div>

              <div className="ticket-list__table">
                {filteredTickets.length > 0 ? (
                  <>
                    <div className="ticket-list__head">
                      <span>Protocolo</span>
                      <span>Assunto</span>
                      <span>Status</span>
                      <span>Data</span>
                      <span>Ação</span>
                    </div>

                    {filteredTickets.map((ticket) => (
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
                            onClick={() => onOpenTicket?.(ticket, 'all')}
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

export default AllTickets
