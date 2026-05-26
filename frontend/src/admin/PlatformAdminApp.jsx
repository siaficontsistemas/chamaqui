import { useEffect, useMemo, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import {
  activatePlatformAdminCompany,
  createPlatformAdminCompany,
  deactivatePlatformAdminCompany,
  getPlatformAdminCompanies,
  getPlatformAdminMe,
  loginPlatformAdmin,
  logoutPlatformAdmin,
} from '../app/api'
import './PlatformAdminApp.css'

const SESSION_STORAGE_KEY = 'helpdesk.platform-admin.session'

function loadStoredAdmin() {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawValue = window.localStorage.getItem(SESSION_STORAGE_KEY)
    return rawValue ? JSON.parse(rawValue) : null
  } catch {
    return null
  }
}

function PlatformAdminApp() {
  const navigate = useNavigate()
  const [adminUser, setAdminUser] = useState(loadStoredAdmin)
  const [companies, setCompanies] = useState([])
  const [searchValue, setSearchValue] = useState('')
  const [isAuthLoading, setIsAuthLoading] = useState(true)
  const [isCompaniesLoading, setIsCompaniesLoading] = useState(false)
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmittingCompany, setIsSubmittingCompany] = useState(false)
  const [companyForm, setCompanyForm] = useState({
    companyName: '',
    companyDocument: '',
    subdomain: '',
    adminFullName: '',
    adminEmail: '',
    adminPhoneNumber: '',
    adminDocumentNumber: '',
    adminPassword: '',
  })

  useEffect(() => {
    let isCancelled = false

    async function loadCurrentAdmin() {
      try {
        const response = await getPlatformAdminMe()
        if (isCancelled) {
          return
        }
        setAdminUser(response)
      } catch {
        if (isCancelled) {
          return
        }
        setAdminUser(null)
      } finally {
        if (!isCancelled) {
          setIsAuthLoading(false)
        }
      }
    }

    loadCurrentAdmin()
    return () => {
      isCancelled = true
    }
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    if (adminUser) {
      window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(adminUser))
      return
    }

    window.localStorage.removeItem(SESSION_STORAGE_KEY)
  }, [adminUser])

  useEffect(() => {
    if (!adminUser?.email) {
      setCompanies([])
      return
    }

    loadCompanies()
  }, [adminUser?.email])

  const filteredCompanies = useMemo(() => {
    const normalizedQuery = searchValue.trim().toLowerCase()
    if (!normalizedQuery) {
      return companies
    }

    return companies.filter((company) =>
      [
        company.companyName,
        company.companyDocument,
        company.subdomain,
        company.adminFullName,
        company.adminEmail,
      ].some((value) => String(value || '').toLowerCase().includes(normalizedQuery))
    )
  }, [companies, searchValue])

  const companyStats = useMemo(() => {
    return companies.reduce(
      (summary, company) => {
        summary.total += 1
        if (company.active) {
          summary.active += 1
        } else {
          summary.inactive += 1
        }
        summary.users += company.activeUsersCount || 0
        return summary
      },
      { total: 0, active: 0, inactive: 0, users: 0 }
    )
  }, [companies])

  async function loadCompanies() {
    try {
      setIsCompaniesLoading(true)
      setErrorMessage('')
      const response = await getPlatformAdminCompanies()
      setCompanies(Array.isArray(response) ? response : [])
    } catch (error) {
      setErrorMessage(error.message || 'Não foi possível carregar as empresas da plataforma.')
    } finally {
      setIsCompaniesLoading(false)
    }
  }

  async function handleLogin({ email, password }) {
    setErrorMessage('')
    setFeedbackMessage('')
    const response = await loginPlatformAdmin({ email, password })
    setAdminUser(response)
    navigate('/companies', { replace: true })
  }

  async function handleLogout() {
    try {
      await logoutPlatformAdmin()
    } catch {
      // A sessão local é encerrada mesmo se a API já tiver invalidado o cookie.
    } finally {
      setAdminUser(null)
      setCompanies([])
      navigate('/login', { replace: true })
    }
  }

  async function handleCreateCompany(event) {
    event.preventDefault()

    try {
      setIsSubmittingCompany(true)
      setErrorMessage('')
      setFeedbackMessage('')
      const response = await createPlatformAdminCompany(companyForm)
      setCompanies((currentCompanies) =>
        [...currentCompanies, response].sort((leftCompany, rightCompany) =>
          leftCompany.companyName.localeCompare(rightCompany.companyName, 'pt-BR', {
            sensitivity: 'base',
          })
        )
      )
      setFeedbackMessage(`Empresa ${response.companyName} criada com sucesso.`)
      setCompanyForm({
        companyName: '',
        companyDocument: '',
        subdomain: '',
        adminFullName: '',
        adminEmail: '',
        adminPhoneNumber: '',
        adminDocumentNumber: '',
        adminPassword: '',
      })
    } catch (error) {
      setErrorMessage(error.message || 'Não foi possível criar a empresa.')
    } finally {
      setIsSubmittingCompany(false)
    }
  }

  async function handleToggleCompanyStatus(company) {
    try {
      setErrorMessage('')
      setFeedbackMessage('')
      const response = company.active
        ? await deactivatePlatformAdminCompany(company.companyId)
        : await activatePlatformAdminCompany(company.companyId)
      setCompanies((currentCompanies) =>
        currentCompanies.map((currentCompany) =>
          currentCompany.companyId === response.companyId ? response : currentCompany
        )
      )
      setFeedbackMessage(
        response.active
          ? `Empresa ${response.companyName} reativada com sucesso.`
          : `Empresa ${response.companyName} desativada com sucesso.`
      )
    } catch (error) {
      setErrorMessage(error.message || 'Não foi possível atualizar o status da empresa.')
    }
  }

  if (isAuthLoading) {
    return <div className="platform-admin-app__loading">Carregando administração da plataforma...</div>
  }

  return (
    <Routes>
      <Route
        path="/login"
        element={
          adminUser ? <Navigate replace to="/companies" /> : <PlatformAdminLoginPage onSubmit={handleLogin} />
        }
      />
      <Route
        path="/companies"
        element={
          adminUser ? (
            <PlatformAdminDashboard
              adminUser={adminUser}
              companies={filteredCompanies}
              companyForm={companyForm}
              companyStats={companyStats}
              errorMessage={errorMessage}
              feedbackMessage={feedbackMessage}
              isCompaniesLoading={isCompaniesLoading}
              isSubmittingCompany={isSubmittingCompany}
              onChangeCompanyForm={setCompanyForm}
              onCreateCompany={handleCreateCompany}
              onLogout={handleLogout}
              onRefreshCompanies={loadCompanies}
              onSearch={setSearchValue}
              onToggleCompanyStatus={handleToggleCompanyStatus}
              searchValue={searchValue}
            />
          ) : (
            <Navigate replace to="/login" />
          )
        }
      />
      <Route path="*" element={<Navigate replace to={adminUser ? '/companies' : '/login'} />} />
    </Routes>
  )
}

function PlatformAdminLoginPage({ onSubmit }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()

    if (!email.trim() || !password) {
      setErrorMessage('Informe o email e a senha do administrador da plataforma.')
      return
    }

    try {
      setIsSubmitting(true)
      setErrorMessage('')
      await onSubmit({
        email: email.trim(),
        password,
      })
    } catch (error) {
      setErrorMessage(error.message || 'Não foi possível entrar na administração da plataforma.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <main className="platform-admin-login">
        <section className="platform-admin-login__hero">
          <PlatformBrandMark />
          <span className="platform-admin-login__badge">Administração da Plataforma</span>
          <h1>Controle central das empresas do ChamAqui</h1>
          <p>
            Use este painel para cadastrar novas empresas respondedoras, acompanhar subdomínios
            ativos e desativar acessos de forma segura.
          </p>
        </section>

        <section className="platform-admin-login__card">
          <h2>Entrar</h2>
          <p className="platform-admin-login__card-text">
            Acesso restrito ao administrador da plataforma.
          </p>

          <form className="platform-admin-login__form" onSubmit={handleSubmit}>
            <label>
              <span>Email</span>
              <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" />
            </label>

            <label>
              <span>Senha</span>
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
              />
            </label>

            {errorMessage ? <p className="platform-admin-login__error">{errorMessage}</p> : null}

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Entrando...' : 'Acessar painel'}
            </button>
          </form>
        </section>
      </main>

      <PlatformMiniFooter />
    </>
  )
}

function PlatformAdminDashboard(props) {
  const formattedDate = useMemo(
    () =>
      new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
      }),
    []
  )

  return (
    <>
      <main className="platform-admin-dashboard">
        <header className="platform-admin-dashboard__header">
          <div className="platform-admin-dashboard__header-brand">
            <PlatformBrandMark />
            <div>
              <span className="platform-admin-dashboard__eyebrow">admin.chamaqui.app.br</span>
              <h1>Painel administrativo da plataforma</h1>
              <p>
                Logado como <strong>{props.adminUser.fullName}</strong> ({props.adminUser.email}).
              </p>
            </div>
          </div>

          <div className="platform-admin-dashboard__header-actions">
            <button type="button" onClick={props.onRefreshCompanies}>
              Atualizar lista
            </button>
            <button type="button" className="platform-admin-dashboard__logout" onClick={props.onLogout}>
              Sair
            </button>
          </div>
        </header>

        <section className="platform-admin-dashboard__stats">
          <StatCard label="Empresas" value={props.companyStats.total} />
          <StatCard label="Ativas" value={props.companyStats.active} />
          <StatCard label="Inativas" value={props.companyStats.inactive} />
          <StatCard label="Usuários ativos" value={props.companyStats.users} />
        </section>

        {props.feedbackMessage ? (
          <div className="platform-admin-dashboard__feedback platform-admin-dashboard__feedback--success">
            {props.feedbackMessage}
          </div>
        ) : null}

        {props.errorMessage ? (
          <div className="platform-admin-dashboard__feedback platform-admin-dashboard__feedback--error">
            {props.errorMessage}
          </div>
        ) : null}

        <section className="platform-admin-dashboard__content">
          <form className="platform-admin-dashboard__panel" onSubmit={props.onCreateCompany}>
            <div className="platform-admin-dashboard__panel-header">
              <h2>Nova empresa respondedora</h2>
              <p>Crie a empresa, o subdomínio e o administrador inicial em um único fluxo.</p>
            </div>

            <div className="platform-admin-dashboard__grid">
              {adminFormFields.map((field) => (
                <label key={field.id} className={field.wide ? 'platform-admin-dashboard__field--wide' : ''}>
                  <span>{field.label}</span>
                  <input
                    type={field.type || 'text'}
                    value={props.companyForm[field.id]}
                    onChange={(event) =>
                      props.onChangeCompanyForm((currentForm) => ({
                        ...currentForm,
                        [field.id]: event.target.value,
                      }))
                    }
                  />
                </label>
              ))}
            </div>

            <button type="submit" disabled={props.isSubmittingCompany}>
              {props.isSubmittingCompany ? 'Criando empresa...' : 'Cadastrar empresa'}
            </button>
          </form>

          <section className="platform-admin-dashboard__panel platform-admin-dashboard__panel--table">
            <div className="platform-admin-dashboard__panel-header">
              <div>
                <h2>Empresas respondedoras</h2>
                <p>Gerencie os subdomínios ativos da plataforma.</p>
              </div>

              <input
                className="platform-admin-dashboard__search"
                placeholder="Buscar por nome, CNPJ, subdomínio ou administrador"
                value={props.searchValue}
                onChange={(event) => props.onSearch(event.target.value)}
              />
            </div>

            <div className="platform-admin-dashboard__table-wrapper">
              <table className="platform-admin-dashboard__table">
                <thead>
                  <tr>
                    <th>Empresa</th>
                    <th>Subdomínio</th>
                    <th>Administrador</th>
                    <th>Status</th>
                    <th>Usuários ativos</th>
                    <th>Criada em</th>
                    <th>Ação</th>
                  </tr>
                </thead>
                <tbody>
                  {props.isCompaniesLoading ? (
                    <tr>
                      <td colSpan="7" className="platform-admin-dashboard__empty">
                        Carregando empresas...
                      </td>
                    </tr>
                  ) : props.companies.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="platform-admin-dashboard__empty">
                        Nenhuma empresa respondedora encontrada.
                      </td>
                    </tr>
                  ) : (
                    props.companies.map((company) => (
                      <tr key={company.companyId}>
                        <td>
                          <strong>{company.companyName}</strong>
                          <span>{company.companyDocument}</span>
                        </td>
                        <td>
                          <strong>{company.subdomain}.chamaqui.app.br</strong>
                          <span>{company.schemaName}</span>
                        </td>
                        <td>
                          <strong>{company.adminFullName}</strong>
                          <span>{company.adminEmail}</span>
                        </td>
                        <td>
                          <span
                            className={
                              company.active
                                ? 'platform-admin-dashboard__status platform-admin-dashboard__status--active'
                                : 'platform-admin-dashboard__status platform-admin-dashboard__status--inactive'
                            }
                          >
                            {company.active ? 'Ativa' : 'Inativa'}
                          </span>
                        </td>
                        <td>{company.activeUsersCount}</td>
                        <td>{company.createdAt ? formattedDate.format(new Date(company.createdAt)) : '-'}</td>
                        <td>
                          <button type="button" onClick={() => props.onToggleCompanyStatus(company)}>
                            {company.active ? 'Desativar' : 'Reativar'}
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </section>
      </main>

      <PlatformMiniFooter />
    </>
  )
}

function StatCard({ label, value }) {
  return (
    <article className="platform-admin-dashboard__stat-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

function PlatformBrandMark() {
  return (
    <div className="platform-admin-brand-mark" aria-label="ChamaAqui Helpdesk">
      <img className="platform-admin-brand-mark__logo" src="/logo_chamaqui.png" alt="ChamaAqui Helpdesk" />
    </div>
  )
}

function PlatformMiniFooter() {
  return (
    <footer className="platform-admin-footer">
      <span>
        &copy; 2026{' '}
        <a href="https://www.siaficont.com.br/" target="_blank" rel="noreferrer">
          Siaficont Sistemas
        </a>
        . Todos os direitos reservados.
      </span>
    </footer>
  )
}

const adminFormFields = [
  { id: 'companyName', label: 'Nome da empresa', wide: true },
  { id: 'companyDocument', label: 'CNPJ da empresa' },
  { id: 'subdomain', label: 'Subdomínio' },
  { id: 'adminFullName', label: 'Nome do administrador', wide: true },
  { id: 'adminEmail', label: 'Email do administrador', type: 'email' },
  { id: 'adminPhoneNumber', label: 'Telefone do administrador' },
  { id: 'adminDocumentNumber', label: 'CPF do administrador' },
  { id: 'adminPassword', label: 'Senha inicial', type: 'password' },
]

export default PlatformAdminApp
