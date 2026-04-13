import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages, isTeamRole } from '../../dashboardData'
import '../Home/Home.css'

function MyData({
  currentUser,
  headerProps,
  isProfileLoading,
  navigationGroups,
  onNavigatePage,
  profileError,
  ticketSummary,
  isTicketSummaryLoading,
  userRole = 'user',
}) {
  const activeContent = dashboardPages.myData
  const profileFields = [
    { label: 'Nome', value: currentUser?.fullName || 'Não informado', type: 'text' },
    { label: 'Email', value: currentUser?.email || 'Não informado', type: 'email' },
    {
      label: 'Telefone',
      value: currentUser?.phoneNumber || 'Não informado',
      type: 'tel',
    },
    {
      label: 'Documento',
      value: currentUser?.documentNumber || 'Não informado',
      type: 'text',
    },
    {
      label: 'Status',
      value: currentUser?.status || 'Não informado',
      type: 'text',
    },
  ]

  return (
    <main className="home-page">
      <Sidebar
        activeSection="myData"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="myData"
          {...headerProps}
          isTeamRole={isTeamRole(userRole)}
          isTicketSummaryLoading={isTicketSummaryLoading}
          onSectionChange={onNavigatePage}
          ticketSummary={ticketSummary}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--profile">
            <div className="home-profile">
              <h1 className="home-profile__title">{activeContent.contentTitle}</h1>

              {profileError ? <p className="profile-form__feedback">{profileError}</p> : null}

              <form className="profile-form" onSubmit={(event) => event.preventDefault()}>
                {profileFields.map((field) => (
                  <label className="ticket-field" key={field.label}>
                    <span>{field.label}</span>
                    <div className="ticket-field__control">
                      <input
                        value={isProfileLoading ? 'Carregando...' : field.value}
                        readOnly
                        type={field.type}
                      />
                    </div>
                  </label>
                ))}
              </form>
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default MyData
