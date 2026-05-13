import { useEffect, useState } from 'react'
import { getPersonalReport } from '../../api'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import '../Home/Home.css'

function Reports({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
}) {
  const [reportRows, setReportRows] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (!currentUser?.email) {
      setReportRows([])
      setIsLoading(false)
      setErrorMessage('')
      return
    }

    let isCancelled = false

    async function loadReport() {
      setIsLoading(true)
      setErrorMessage('')

      try {
        const response = await getPersonalReport(currentUser.email)

        if (isCancelled) {
          return
        }

        setReportRows(Array.isArray(response) ? response : [])
      } catch (error) {
        if (isCancelled) {
          return
        }

        setReportRows([])
        setErrorMessage(error.message)
      } finally {
        if (!isCancelled) {
          setIsLoading(false)
        }
      }
    }

    loadReport()

    return () => {
      isCancelled = true
    }
  }, [currentUser?.email])

  return (
    <main className="home-page">
      <Sidebar
        activeSection="reports"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="reports"
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--reports">
            <div className="reports-view">
              <h1 className="reports-view__title">{dashboardPages.reports.contentTitle} - Pessoal</h1>

              <div className="reports-table">
                {reportRows.length > 0 ? (
                  <>
                    <div className="reports-table__head">
                      <span>Ano</span>
                      <span>Mês</span>
                      <span>Novos chamados</span>
                      <span>Respostas enviadas</span>
                    </div>

                    {reportRows.map((row) => (
                      <div className="reports-table__row" key={`${row.year}-${row.month}`}>
                        <div className="reports-table__cell">
                          <span className="reports-table__label">Ano</span>
                          <span className="reports-table__value">{row.year}</span>
                        </div>
                        <div className="reports-table__cell">
                          <span className="reports-table__label">Mês</span>
                          <span className="reports-table__value">{row.month}</span>
                        </div>
                        <div className="reports-table__cell">
                          <span className="reports-table__label">Novos chamados</span>
                          <span className="reports-table__value">{row.createdTickets}</span>
                        </div>
                        <div className="reports-table__cell">
                          <span className="reports-table__label">Respostas enviadas</span>
                          <span className="reports-table__value">{row.repliedTickets}</span>
                        </div>
                      </div>
                    ))}
                  </>
                ) : (
                  <div className="reports-table__empty">
                    {isLoading
                      ? 'Carregando relatório...'
                      : errorMessage || 'Nenhum dado de relatório disponível até o momento.'}
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

export default Reports
