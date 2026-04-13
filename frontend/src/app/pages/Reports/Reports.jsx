import { useEffect, useState } from 'react'
import { getPersonalReport } from '../../api'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages, isTeamRole } from '../../dashboardData'
import '../Home/Home.css'

function Reports({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
  userRole = 'user',
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
          isTeamRole={isTeamRole(userRole)}
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
                      <span>Tempo de Trabalho</span>
                    </div>

                    {reportRows.map((row) => (
                      <div className="reports-table__row" key={`${row.year}-${row.month}`}>
                        <span>{row.year}</span>
                        <span>{row.month}</span>
                        <span>{row.workTime}</span>
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
