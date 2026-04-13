import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getRoleLabel, isTeamRole } from '../../dashboardData'
import '../Home/Home.css'

function Sector({
  headerProps,
  navigationGroups,
  onNavigatePage,
  sector,
  teamMembers = [],
  userRole = 'user',
}) {
  const roleLabel = getRoleLabel(userRole)
  const assignedMembers = teamMembers.filter((member) => (member.sectors ?? []).includes(sector.id))
  const summaryCards = [
    {
      id: 'sector',
      label: 'Setor',
      value: sector.name,
      detail: 'Setor criado pelo administrador',
    },
    {
      id: 'members',
      label: 'Participantes',
      value: String(assignedMembers.length),
      detail: 'Funcionários vinculados atualmente',
    },
    {
      id: 'profile',
      label: 'Acesso',
      value: roleLabel,
      detail:
        userRole === 'admin'
          ? 'Pode ajustar participantes na tela de equipe'
          : 'Visualiza somente o setor em que participa',
    },
  ]

  return (
    <main className="home-page">
      <Sidebar
        activeSection={sector.id}
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection={sector.id}
          {...headerProps}
          isTeamRole={isTeamRole(userRole)}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card">
            <div className="home-content__header">
              <div className="home-content__heading">
                <span className="home-content__eyebrow">Setor da equipe</span>
                <h1>{sector.name}</h1>
                <p>{sector.description}</p>
              </div>

              {userRole === 'admin' ? (
                <div className="home-content__actions">
                  <button className="home-content__button" type="button" onClick={() => onNavigatePage('team')}>
                    Ajustar equipe
                  </button>
                  <button
                    className="home-content__button home-content__button--ghost"
                    type="button"
                    onClick={() => onNavigatePage('createSector')}
                  >
                    Criar setor
                  </button>
                </div>
              ) : null}
            </div>

            <div className="home-summary">
              {summaryCards.map((card) => (
                <article className="home-summary__card" key={card.id}>
                  <span className="home-summary__label">{card.label}</span>
                  <strong className="home-summary__value">{card.value}</strong>
                  <span className="home-summary__detail">{card.detail}</span>
                </article>
              ))}
            </div>

            <div className="home-panel">
              <div className="home-panel__header">
                <div>
                  <span className="home-panel__eyebrow">Equipe vinculada</span>
                  <h2>Participantes do setor</h2>
                </div>
                <span className="home-panel__badge">{assignedMembers.length} integrante(s)</span>
              </div>

              <div className="home-panel__table">
                <div className="home-panel__table-head">
                  <span>Nome</span>
                  <span>Função</span>
                  <span>Status</span>
                  <span>Setores</span>
                </div>

                {assignedMembers.map((member) => (
                  <div className="home-panel__table-row" key={member.id}>
                    <span>{member.name}</span>
                    <span>{member.role}</span>
                    <span>{member.status}</span>
                    <span>{member.sectors.length}</span>
                  </div>
                ))}

                {assignedMembers.length === 0 ? (
                  <div className="home-panel__table-row">
                    <span>Nenhum integrante</span>
                    <span>Aguardando vínculo</span>
                    <span>Sem participação</span>
                    <span>0</span>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default Sector
