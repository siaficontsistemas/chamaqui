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

  function renderCompanies(row) {
    if (!Array.isArray(row.repliedCompanies) || row.repliedCompanies.length === 0) {
      return <span className="reports-table__value">Nenhuma empresa atendida</span>
    }

    return (
      <div className="reports-table__company-list">
        {row.repliedCompanies.map((company) => (
          <span
            className="reports-table__company-chip"
            key={`${row.year}-${row.month}-${company.companyName}`}
          >
            {company.companyName}: {company.repliedTickets}
          </span>
        ))}
      </div>
    )
  }

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

              {reportRows.length > 0 ? (
                <>
                  <section className="reports-section">
                    <div className="reports-section__header">
                      <h2 className="reports-section__title">Empresas respondidas</h2>
                    </div>

                    <div className="reports-table-wrap">
                      <div className="reports-table reports-table--companies">
                        <div className="reports-table__head">
                          <span>Ano</span>
                          <span>Mês</span>
                          <span>Empresas respondidas</span>
                        </div>

                        {reportRows.map((row) => (
                          <div className="reports-table__row" key={`companies-${row.year}-${row.month}`}>
                            <div className="reports-table__cell">
                              <span className="reports-table__label">Ano</span>
                              <span className="reports-table__value">{row.year}</span>
                            </div>
                            <div className="reports-table__cell">
                              <span className="reports-table__label">Mês</span>
                              <span className="reports-table__value">{row.month}</span>
                            </div>
                            <div className="reports-table__cell">
                              <span className="reports-table__label">Empresas respondidas</span>
                              {renderCompanies(row)}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </section>

                  <section className="reports-section">
                    <div className="reports-section__header">
                      <h2 className="reports-section__title">Respostas enviadas</h2>
                    </div>

                    <div className="reports-table-wrap">
                      <div className="reports-table reports-table--summary">
                        <div className="reports-table__head">
                          <span>Ano</span>
                          <span>Mês</span>
                          <span>Novos chamados</span>
                          <span>Respostas enviadas</span>
                        </div>

                        {reportRows.map((row) => (
                          <div className="reports-table__row" key={`summary-${row.year}-${row.month}`}>
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
                      </div>
                    </div>
                  </section>
                </>
              ) : (
                <div className="reports-table reports-table--empty">
                  <div className="reports-table__empty">
                    {isLoading
                      ? 'Carregando relatório...'
                      : errorMessage || 'Nenhum dado de relatório disponível até o momento.'}
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default Reports
