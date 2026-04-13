import { useEffect, useMemo, useState } from 'react'
import { getTickets } from '../../api'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { isTeamRole } from '../../dashboardData'
import { SearchIcon } from '../../dashboardIcons'
import '../Home/Home.css'

function OpenTickets({ headerProps, navigationGroups, onNavigatePage, userRole = 'user' }) {
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

  useEffect(() => {
    let isCancelled = false

    async function loadTickets() {
      setIsLoading(true)
      setErrorMessage('')

      try {
        const response = await getTickets('OPEN')

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
  }, [])

  function formatDate(value) {
    if (!value) {
      return 'Não informado'
    }

    return formattedDate.format(new Date(value))
  }

  return (
    <main className="home-page">
      <Sidebar activeSection="open" navigationGroups={navigationGroups} onSectionChange={onNavigatePage} />

      <div className="home-main-column">
        <Header
          activeSection="open"
          {...headerProps}
          isTeamRole={isTeamRole(userRole)}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--ticket-list">
            <div className="ticket-list">
              <div className="ticket-list__toolbar">
                <label className="ticket-list__search" htmlFor="ticket-search-open">
                  <SearchIcon />
                  <input
                    id="ticket-search-open"
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
                    </div>

                    {filteredTickets.map((ticket) => (
                      <div className="ticket-list__row" key={ticket.id}>
                        <span>{ticket.protocol}</span>
                        <span>{ticket.title}</span>
                        <span>
                          <strong className="ticket-list__status ticket-list__status--open">
                            {ticket.statusName}
                          </strong>
                        </span>
                        <span>{formatDate(ticket.openedAt)}</span>
                      </div>
                    ))}
                  </>
                ) : (
                  <div className="ticket-list__empty">
                    {isLoading
                      ? 'Carregando chamados...'
                      : errorMessage || 'Nenhum chamado aberto até o momento.'}
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

export default OpenTickets
