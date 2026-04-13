import { useMemo, useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getRoleLabel, getTeamContent, getTeamMembers, isTeamRole } from '../../dashboardData'
import '../Home/Home.css'

function Team({
  headerProps,
  isTeamDataLoading,
  navigationGroups,
  onInviteMember,
  onNavigatePage,
  onAcceptInvite,
  onDeclineInvite,
  onUpdateMemberSectors,
  receivedInvites = [],
  sectors = [],
  sentInvites = [],
  teamDataError = '',
  teamMembers = [],
  userRole = 'user',
}) {
  const roleLabel = getRoleLabel(userRole)
  const activeContent = getTeamContent(userRole)
  const visibleTeamMembers = getTeamMembers(userRole, teamMembers)
  const [inviteName, setInviteName] = useState('')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteSectorIds, setInviteSectorIds] = useState([])
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [isSubmittingInvite, setIsSubmittingInvite] = useState(false)
  const [processingInviteId, setProcessingInviteId] = useState('')
  const sectorNameById = useMemo(
    () =>
      Object.fromEntries(
        sectors.map((sector) => [
          sector.id,
          sector.name,
        ])
      ),
    [sectors]
  )
  const membersWithSector = visibleTeamMembers.filter((member) => (member.sectors ?? []).length > 0).length
  const pendingReceivedInvites = receivedInvites.filter((invite) => invite.status === 'PENDING')
  const handledSentInvites = sentInvites.filter((invite) => invite.status !== 'PENDING')

  async function toggleMemberSector(memberId, sectorId) {
    const currentMember = visibleTeamMembers.find((member) => member.id === memberId)
    const memberSectors = currentMember?.sectors ?? []
    const nextSectors = memberSectors.includes(sectorId)
      ? memberSectors.filter((currentSectorId) => currentSectorId !== sectorId)
      : [...memberSectors, sectorId]

    setFeedbackMessage('')
    await onUpdateMemberSectors(memberId, nextSectors)
  }

  function toggleInviteSector(sectorId) {
    setInviteSectorIds((currentSectorIds) =>
      currentSectorIds.includes(sectorId)
        ? currentSectorIds.filter((currentSectorId) => currentSectorId !== sectorId)
        : [...currentSectorIds, sectorId]
    )
  }

  async function handleInviteSubmit(event) {
    event.preventDefault()

    if (!inviteName.trim() || !inviteEmail.trim() || inviteSectorIds.length === 0) {
      return
    }

    setIsSubmittingInvite(true)
    setFeedbackMessage('')

    try {
      await onInviteMember({
        email: inviteEmail,
        name: inviteName,
        sectors: inviteSectorIds,
      })
      setInviteName('')
      setInviteEmail('')
      setInviteSectorIds([])
      setFeedbackMessage('Convite enviado com sucesso. O funcionário já pode responder pela central de notificações.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setIsSubmittingInvite(false)
    }
  }

  async function handleInviteDecision(inviteId, action) {
    setProcessingInviteId(inviteId)
    setFeedbackMessage('')

    try {
      if (action === 'accept') {
        await onAcceptInvite(inviteId)
        setFeedbackMessage('Convite aceito com sucesso. Sua participação na equipe foi atualizada.')
        return
      }

      await onDeclineInvite(inviteId)
      setFeedbackMessage('Convite recusado. Nenhuma alteração foi feita na equipe.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingInviteId('')
    }
  }

  function getInviteStatusLabel(status) {
    if (status === 'ACCEPTED') {
      return 'Aceito'
    }

    if (status === 'CANCELED') {
      return 'Recusado'
    }

    if (status === 'EXPIRED') {
      return 'Expirado'
    }

    return 'Pendente'
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="team"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="team"
          {...headerProps}
          isTeamRole={isTeamRole(userRole)}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--team">
            <div className="team-view">
              <div className="home-content__header">
                <div className="home-content__heading">
                  <span className="home-content__eyebrow">Colaboração interna</span>
                  <h1>{activeContent.contentTitle}</h1>
                  <p>{activeContent.contentText}</p>
                </div>
              </div>

              {feedbackMessage || teamDataError ? (
                <p className="team-feedback">{feedbackMessage || teamDataError}</p>
              ) : null}

              <div className="team-view__summary">
                <article className="team-view__summary-card">
                  <span>Total de pessoas</span>
                  <strong>{visibleTeamMembers.length}</strong>
                  <small>Integrantes cadastrados na equipe de trabalho</small>
                </article>
                <article className="team-view__summary-card">
                  <span>Seu acesso</span>
                  <strong>{roleLabel}</strong>
                  <small>
                    {userRole === 'admin'
                      ? 'Pode escolher os setores da equipe e direcionar funcionários'
                      : 'Pode apenas visualizar os setores definidos pelo administrador'}
                  </small>
                </article>
                <article className="team-view__summary-card">
                  <span>Setores criados</span>
                  <strong>{sectors.length}</strong>
                  <small>
                    {userRole === 'admin'
                      ? 'Setores disponíveis para distribuição na equipe'
                      : 'Setores em que você participa dentro da equipe'}
                  </small>
                </article>
                <article className="team-view__summary-card">
                  <span>Pessoas alocadas</span>
                  <strong>{membersWithSector}</strong>
                  <small>Integrantes que já foram vinculados a pelo menos um setor</small>
                </article>
              </div>

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">Notificações</span>
                    <h2>
                      {userRole === 'admin' ? 'Retornos dos convites enviados' : 'Convites recebidos'}
                    </h2>
                  </div>
                  <span className="home-panel__badge">
                    {userRole === 'admin'
                      ? `${handledSentInvites.length} retorno(s)`
                      : `${pendingReceivedInvites.length} pendente(s)`}
                  </span>
                </div>

                {userRole === 'admin' ? (
                  handledSentInvites.length > 0 ? (
                    <div className="team-invite-list">
                      {handledSentInvites.map((invite) => (
                        <article className="team-invite-list__item" key={invite.id}>
                          <div>
                            <strong>{invite.invitedName}</strong>
                            <p>{invite.sectorNames.join(', ')}</p>
                          </div>
                          <span className={`team-invite-list__status team-invite-list__status--${invite.status.toLowerCase()}`}>
                            {getInviteStatusLabel(invite.status)}
                          </span>
                        </article>
                      ))}
                    </div>
                  ) : (
                    <span className="team-panel__empty">
                      Nenhum retorno de convite recebido até o momento.
                    </span>
                  )
                ) : pendingReceivedInvites.length > 0 ? (
                  <div className="team-invite-list">
                    {pendingReceivedInvites.map((invite) => (
                      <article className="team-invite-list__item" key={invite.id}>
                        <div>
                          <strong>{invite.invitedByName}</strong>
                          <p>{invite.sectorNames.join(', ')}</p>
                        </div>
                        <div className="team-invite-list__actions">
                          <button
                            className="team-invite-list__button"
                            type="button"
                            onClick={() => handleInviteDecision(invite.id, 'accept')}
                            disabled={processingInviteId === invite.id}
                          >
                            {processingInviteId === invite.id ? 'Processando...' : 'Aceitar'}
                          </button>
                          <button
                            className="team-invite-list__button team-invite-list__button--ghost"
                            type="button"
                            onClick={() => handleInviteDecision(invite.id, 'decline')}
                            disabled={processingInviteId === invite.id}
                          >
                            Recusar
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                ) : (
                  <span className="team-panel__empty">
                    Nenhum convite pendente para sua conta neste momento.
                  </span>
                )}
              </div>

              {userRole === 'admin' ? (
                <form className="team-invite" onSubmit={handleInviteSubmit}>
                  <div className="team-invite__header">
                    <div>
                      <span className="home-panel__eyebrow">Convite de funcionário</span>
                      <h2>Adicionar novo integrante</h2>
                    </div>
                  </div>

                  <div className="ticket-form__grid">
                    <label className="ticket-field">
                      <span>Nome do funcionário</span>
                      <div className="ticket-field__control">
                        <input
                          placeholder="Digite o nome do funcionário"
                          type="text"
                          value={inviteName}
                          onChange={(event) => setInviteName(event.target.value)}
                        />
                      </div>
                    </label>

                    <label className="ticket-field">
                      <span>Email do funcionário</span>
                      <div className="ticket-field__control">
                        <input
                          placeholder="Digite o email de convite"
                          type="email"
                          value={inviteEmail}
                          onChange={(event) => setInviteEmail(event.target.value)}
                        />
                      </div>
                    </label>
                  </div>

                  <label className="ticket-field">
                    <span>Setor ou setores do funcionário</span>
                    {sectors.length > 0 ? (
                      <div className="team-sectors">
                        {sectors.map((sector) => {
                          const isSelected = inviteSectorIds.includes(sector.id)

                          return (
                            <button
                              className={`team-sector-chip${isSelected ? ' is-active' : ''}`}
                              key={`invite-${sector.id}`}
                              type="button"
                              onClick={() => toggleInviteSector(sector.id)}
                            >
                              {sector.name}
                            </button>
                          )
                        })}
                      </div>
                    ) : (
                      <span className="team-panel__empty">
                        Crie pelo menos um setor antes de convidar um novo integrante.
                      </span>
                    )}
                  </label>

                  <div className="team-invite__footer">
                    <span>
                      {inviteSectorIds.length > 0
                        ? `${inviteSectorIds.length} setor(es) selecionado(s) para esse funcionário.`
                        : 'Selecione ao menos um setor para o funcionário convidado.'}
                    </span>
                    <button
                      className="team-invite__button"
                      type="submit"
                      disabled={
                        isSubmittingInvite ||
                        !inviteName.trim() || !inviteEmail.trim() || inviteSectorIds.length === 0
                      }
                    >
                      {isSubmittingInvite ? 'Enviando...' : 'Convidar funcionário'}
                    </button>
                  </div>
                </form>
              ) : null}

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">Setores da equipe</span>
                    <h2>
                      {userRole === 'admin'
                        ? 'Setores cadastrados pelo administrador'
                        : 'Setores em que você participa'}
                    </h2>
                  </div>
                  {userRole === 'admin' ? (
                    <button
                      className="home-content__button home-content__button--ghost"
                      type="button"
                      onClick={() => onNavigatePage('createSector')}
                    >
                      Criar setor
                    </button>
                  ) : (
                    <span className="home-panel__badge">Visualização</span>
                  )}
                </div>

                {sectors.length > 0 ? (
                  <div className="team-sectors">
                    {sectors.map((sector) => (
                      <button className="team-sectors__item is-active" key={sector.id} type="button">
                        <span>{sector.name}</span>
                        <strong>{sector.description}</strong>
                      </button>
                    ))}
                  </div>
                ) : (
                  <span className="team-panel__empty">
                    {userRole === 'admin'
                      ? 'Nenhum setor criado. Use a tela de criação de setores para começar.'
                      : 'Você ainda não foi vinculado a nenhum setor.'}
                  </span>
                )}
              </div>

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">Direcionamento da equipe</span>
                    <h2>{userRole === 'admin' ? 'Definir setores dos funcionários' : 'Funcionários por setor'}</h2>
                  </div>
                  <span className="home-panel__badge">Acesso compartilhado</span>
                </div>

                <div className="team-panel__table">
                  {visibleTeamMembers.length > 0 ? (
                    <>
                      <div className="team-panel__head">
                        <span>Nome</span>
                        <span>Função</span>
                        <span>Setores</span>
                        <span>Status</span>
                      </div>

                      {visibleTeamMembers.map((member) => (
                        <div className="team-panel__row" key={member.id}>
                          <span>{member.name}</span>
                          <span>{member.role}</span>
                          <span className="team-panel__sectors">
                            {sectors.map((sector) => {
                              const isAssigned = (member.sectors ?? []).includes(sector.id)

                              return (
                                <button
                                  className={`team-sector-chip${isAssigned ? ' is-active' : ''}`}
                                  key={`${member.id}-${sector.id}`}
                                  type="button"
                                  onClick={
                                    userRole === 'admin'
                                      ? () => toggleMemberSector(member.id, sector.id)
                                      : undefined
                                  }
                                  disabled={userRole !== 'admin' || sectors.length === 0 || isTeamDataLoading}
                                >
                                  {sector.name}
                                </button>
                              )
                            })}
                            {(member.sectors ?? []).length > 0 && sectors.length === 0 ? (
                              <span className="team-panel__empty">
                                {member.sectors
                                  .map((sectorId) => sectorNameById[sectorId])
                                  .filter(Boolean)
                                  .join(', ')}
                              </span>
                            ) : null}
                            {sectors.length === 0 ? (
                              <span className="team-panel__empty">Nenhum setor definido</span>
                            ) : null}
                          </span>
                          <span className="team-panel__status">{member.status}</span>
                        </div>
                      ))}
                    </>
                  ) : (
                    <span className="team-panel__empty">
                      {userRole === 'admin'
                        ? 'Nenhum funcionário cadastrado na equipe até o momento.'
                        : 'Nenhum funcionário disponível na equipe até o momento.'}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default Team
