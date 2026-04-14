import { useMemo, useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages, getRoleLabel } from '../../dashboardData'
import '../Home/Home.css'

function CreateSector({
  headerProps,
  navigationGroups,
  onCreateSector,
  onNavigatePage,
  sectors = [],
  userRole = 'user',
}) {
  const roleLabel = getRoleLabel(userRole)
  const [sectorName, setSectorName] = useState('')
  const [sectorDescription, setSectorDescription] = useState('')
  const existingSectorNames = useMemo(
    () => sectors.map((sector) => sector.name.toLowerCase()),
    [sectors]
  )
  const normalizedName = sectorName.trim().toLowerCase()
  const hasDuplicate = normalizedName.length > 0 && existingSectorNames.includes(normalizedName)

  function handleSubmit(event) {
    event.preventDefault()

    if (!sectorName.trim() || hasDuplicate) {
      return
    }

    onCreateSector({
      name: sectorName,
      description: sectorDescription,
    })
    setSectorName('')
    setSectorDescription('')
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="createSector"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="createSector"
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--team">
            <div className="team-view">
              <div className="home-content__header">
                <div className="home-content__heading">
                  <span className="home-content__eyebrow">Gestão de setores</span>
                  <h1>{dashboardPages.createSector.contentTitle}</h1>
                  <p>{dashboardPages.createSector.contentText}</p>
                </div>
              </div>

              <div className="team-view__summary">
                <article className="team-view__summary-card">
                  <span>Total de setores</span>
                  <strong>{sectors.length}</strong>
                  <small>Setores disponíveis para distribuição na equipe</small>
                </article>
                <article className="team-view__summary-card">
                  <span>Seu acesso</span>
                  <strong>{roleLabel}</strong>
                  <small>Pode criar novos setores e depois direcionar a equipe na tela de equipe</small>
                </article>
              </div>

              <form className="team-invite" onSubmit={handleSubmit}>
                <div className="team-invite__header">
                  <div>
                    <span className="home-panel__eyebrow">Novo setor</span>
                    <h2>Cadastrar setor da equipe</h2>
                  </div>
                </div>

                <div className="ticket-form__grid">
                  <label className="ticket-field">
                    <span>Nome do setor</span>
                    <div className="ticket-field__control">
                      <input
                        placeholder="Digite o nome do setor"
                        type="text"
                        value={sectorName}
                        onChange={(event) => setSectorName(event.target.value)}
                      />
                    </div>
                  </label>

                  <label className="ticket-field">
                    <span>Descrição</span>
                    <div className="ticket-field__control">
                      <input
                        placeholder="Descreva o objetivo do setor"
                        type="text"
                        value={sectorDescription}
                        onChange={(event) => setSectorDescription(event.target.value)}
                      />
                    </div>
                  </label>
                </div>

                <div className="team-invite__footer">
                  <span>
                    {hasDuplicate
                      ? 'Já existe um setor com esse nome.'
                      : 'Depois de criar, vincule os funcionários a esse setor na tela de equipe.'}
                  </span>
                  <button className="team-invite__button" type="submit" disabled={hasDuplicate}>
                    Criar setor
                  </button>
                </div>
              </form>

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">Setores cadastrados</span>
                    <h2>Lista de setores criados</h2>
                  </div>
                  <span className="home-panel__badge">Administrador</span>
                </div>

                {sectors.length > 0 ? (
                  <div className="team-sectors">
                    {sectors.map((sector) => (
                      <button
                        className="team-sectors__item is-active"
                        key={sector.id}
                        type="button"
                        onClick={() => onNavigatePage(sector.id)}
                      >
                        <span>{sector.name}</span>
                        <strong>{sector.description}</strong>
                      </button>
                    ))}
                  </div>
                ) : (
                  <span className="team-panel__empty">
                    Nenhum setor criado ainda. Cadastre o primeiro setor acima.
                  </span>
                )}
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default CreateSector
