import { useMemo, useState } from 'react'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getRoleLabel, getTeamContent, getTeamMembers } from '../../dashboardData'
import '../Home/Home.css'

function Team({
  currentUser,
  headerProps,
  isTeamDataLoading,
  navigationGroups,
  onInviteMember,
  onLeaveSector,
  onNavigatePage,
  onAcceptInvite,
  onAcceptTicketTransfer,
  onDeleteNotification,
  onDeclineInvite,
  onDeclineTicketTransfer,
  onRemoveMemberFromCompany,
  onUpdateMemberSectors,
  receivedInvites = [],
  sectors = [],
  sentInvites = [],
  ticketNotifications = [],
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
  const [deletingInviteId, setDeletingInviteId] = useState('')
  const [processingMemberId, setProcessingMemberId] = useState('')
  const [leavingSectorId, setLeavingSectorId] = useState('')
  const [pendingConfirmation, setPendingConfirmation] = useState(null)
  const [isConfirmingAction, setIsConfirmingAction] = useState(false)
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
  const employeeNotifications = [...pendingReceivedInvites, ...ticketNotifications].sort(
    (firstNotification, secondNotification) =>
      new Date(
        secondNotification.updatedAt ||
          secondNotification.acceptedAt ||
          secondNotification.expiresAt ||
          secondNotification.createdAt
      ).getTime() -
      new Date(
        firstNotification.updatedAt ||
          firstNotification.acceptedAt ||
          firstNotification.expiresAt ||
          firstNotification.createdAt
      ).getTime()
  )
  const companyName =
    currentUser?.companyName ||
    sectors.find((sector) => sector.companyName)?.companyName ||
    'Empresa não informada'

  async function toggleMemberSector(memberId, sectorId) {
    const currentMember = visibleTeamMembers.find((member) => member.id === memberId)
    const memberSectors = currentMember?.sectors ?? []
    if (memberSectors.length === 1 && memberSectors.includes(sectorId)) {
      setFeedbackMessage('Para remover o último setor, use a ação de remover da empresa.')
      return
    }
    const nextSectors = memberSectors.includes(sectorId)
      ? memberSectors.filter((currentSectorId) => currentSectorId !== sectorId)
      : [...memberSectors, sectorId]

    setFeedbackMessage('')
    await onUpdateMemberSectors(memberId, nextSectors)
  }

  async function handleRemoveMember(member) {
    if (!onRemoveMemberFromCompany) {
      return
    }

    setProcessingMemberId(member.id)
    setFeedbackMessage('')

    try {
      await onRemoveMemberFromCompany(member.id)
      setFeedbackMessage(`${member.name} foi removido(a) da empresa e recebeu uma notificação.`)
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingMemberId('')
    }
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

  async function handleEmployeeNotificationDecision(notification, action) {
    setProcessingInviteId(notification.id)
    setFeedbackMessage('')

    try {
      if (notification.type === 'ticket-transfer') {
        if (action === 'accept') {
          await onAcceptTicketTransfer(notification.id)
          setFeedbackMessage('Transferência aceita com sucesso. O chamado agora aparece na sua lista.')
          return
        }

        await onDeclineTicketTransfer(notification.id)
        setFeedbackMessage('Transferência recusada. O chamado continua com o responsável anterior.')
        return
      }

      if (action === 'accept') {
        await onAcceptInvite(notification.id)
        setFeedbackMessage('Convite aceito com sucesso. Sua participação na equipe foi atualizada.')
        return
      }

      await onDeclineInvite(notification.id)
      setFeedbackMessage('Convite recusado. Nenhuma alteração foi feita na equipe.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingInviteId('')
    }
  }

  async function handleDeleteNotification(notificationOrId) {
    const notificationId =
      typeof notificationOrId === 'object' ? notificationOrId?.id : notificationOrId

    setDeletingInviteId(notificationId)
    setFeedbackMessage('')

    try {
      await onDeleteNotification(notificationOrId)
      setFeedbackMessage('Notificação excluída com sucesso.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setDeletingInviteId('')
    }
  }

  async function handleLeaveSector(sector) {
    if (!onLeaveSector) {
      return
    }

    setLeavingSectorId(sector.id)
    setFeedbackMessage('')

    try {
      await onLeaveSector(sector.id)
      setFeedbackMessage(`Você saiu do setor ${sector.name} com sucesso.`)
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setLeavingSectorId('')
    }
  }

  function openConfirmation(config) {
    setPendingConfirmation(config)
  }

  function closeConfirmation() {
    if (isConfirmingAction) {
      return
    }

    setPendingConfirmation(null)
  }

  async function handleConfirmAction() {
    if (!pendingConfirmation?.onConfirm) {
      return
    }

    setIsConfirmingAction(true)

    try {
      await pendingConfirmation.onConfirm()
      setPendingConfirmation(null)
    } finally {
      setIsConfirmingAction(false)
    }
  }

  function requestRemoveMemberConfirmation(member) {
    openConfirmation({
      title: 'Remover da empresa',
      description: [
        `Tem certeza que deseja remover ${member.name} da empresa?`,
        'O funcionário perderá acesso aos setores da empresa e receberá uma notificação.',
      ],
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => handleRemoveMember(member),
    })
  }

  function requestDeleteNotificationConfirmation(notificationOrId) {
    openConfirmation({
      title: 'Excluir notificação',
      description: 'Tem certeza que deseja excluir esta notificação?',
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => handleDeleteNotification(notificationOrId),
    })
  }

  function requestDecisionConfirmation(notification, action) {
    const isAccepting = action === 'accept'

    if (notification.type === 'ticket-transfer') {
      openConfirmation({
        title: isAccepting ? 'Aceitar transferência' : 'Recusar transferência',
        description: `Tem certeza que deseja ${isAccepting ? 'aceitar' : 'recusar'} a transferência do chamado ${notification.ticketProtocol}?`,
        confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
        confirmVariant: isAccepting ? 'primary' : 'danger',
        onConfirm: () => handleEmployeeNotificationDecision(notification, action),
      })
      return
    }

    const sectorNames = notification.sectorNames?.join(', ') || 'setor não informado'

    openConfirmation({
      title: isAccepting ? 'Aceitar convite' : 'Recusar convite',
      description: isAccepting
        ? `Tem certeza que deseja aceitar o convite para os setores ${sectorNames}?`
        : `Tem certeza que deseja recusar o convite para os setores ${sectorNames}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () => handleEmployeeNotificationDecision(notification, action),
    })
  }

  function requestLeaveSectorConfirmation(sector) {
    openConfirmation({
      title: 'Sair do setor',
      description: `Tem certeza que deseja sair do setor ${sector.name}?`,
      confirmLabel: 'Sair do setor',
      confirmVariant: 'danger',
      onConfirm: () => handleLeaveSector(sector),
    })
  }

  function getEmployeeNotificationTitle(notification) {
    if (notification.type === 'ticket-assignment') {
      return `Novo chamado ${notification.ticketProtocol}`
    }

    if (notification.type === 'ticket-transfer') {
      return `${notification.senderName} quer transferir o chamado ${notification.ticketProtocol}`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_DELETED') {
        return 'A empresa foi excluída'
      }

      return notification.removalType === 'COMPANY_REMOVED'
        ? 'Você foi removido da empresa'
        : `Você foi removido do setor ${notification.sectorName}`
    }

    return notification.invitedByName
  }

  function getEmployeeNotificationDescription(notification) {
    if (notification.type === 'ticket-assignment') {
      return `${notification.requesterName} abriu "${notification.ticketTitle}" para o setor ${notification.sectorName}.`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_DELETED') {
        return `${notification.removedByName} excluiu a empresa ${notification.companyName || 'informada'}. Os setores dessa empresa não existem mais para você.`
      }

      if (notification.removalType === 'COMPANY_REMOVED') {
        return `${notification.removedByName} removeu seu acesso da empresa ${notification.companyName || 'informada'}.`
      }

      return `${notification.removedByName} removeu sua participação do setor ${notification.sectorName}.`
    }

    if (notification.type === 'ticket-transfer') {
      return `O chamado "${notification.ticketTitle}" foi transferido para você por ${notification.senderName}.`
    }

    return notification.sectorNames.join(', ')
  }

  function canDeleteNotification(notification) {
    return !(notification.type === 'ticket-transfer' && notification.status === 'PENDING')
  }

  function getNotificationStatusLabel(notification) {
    if (notification.type === 'team-membership-removed') {
      return 'Removido'
    }

    if (notification.type === 'ticket-assignment') {
      return 'Novo chamado'
    }

    return getInviteStatusLabel(notification.status)
  }

  function formatMemberStatus(status) {
    if (status === 'ACTIVE') {
      return 'Ativo'
    }

    if (status === 'INACTIVE') {
      return 'Inativo'
    }

    return status
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
                  <span>Empresa da equipe</span>
                  <strong>{companyName}</strong>
                  <small>Empresa vinculada aos setores e integrantes dessa equipe</small>
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
                      {userRole === 'admin' ? 'Retornos dos convites enviados' : 'Suas notificações'}
                    </h2>
                  </div>
                  <span className="home-panel__badge">
                    {userRole === 'admin'
                      ? `${handledSentInvites.length} retorno(s)`
                      : `${employeeNotifications.length} item(ns)`}
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
                          <div className="team-invite-list__meta">
                            <span className={`team-invite-list__status team-invite-list__status--${invite.status.toLowerCase()}`}>
                              {getInviteStatusLabel(invite.status)}
                            </span>
                            <button
                              className="team-invite-list__icon-button"
                              type="button"
                              onClick={() => requestDeleteNotificationConfirmation(invite.id)}
                              disabled={deletingInviteId === invite.id}
                              aria-label="Excluir notificação"
                            >
                              <TrashIcon />
                            </button>
                          </div>
                        </article>
                      ))}
                    </div>
                  ) : (
                    <span className="team-panel__empty">
                      Nenhum retorno de convite recebido até o momento.
                    </span>
                  )
                ) : employeeNotifications.length > 0 ? (
                  <div className="team-invite-list">
                    {employeeNotifications.map((notification) => (
                      <article className="team-invite-list__item" key={notification.id}>
                        <div>
                          <strong>{getEmployeeNotificationTitle(notification)}</strong>
                          <p>{getEmployeeNotificationDescription(notification)}</p>
                        </div>
                        <div className="team-invite-list__meta">
                          {notification.type === 'received' || notification.type === 'ticket-transfer' ? (
                            <div className="team-invite-list__actions">
                              <button
                                className="team-invite-list__button"
                                type="button"
                                onClick={() => requestDecisionConfirmation(notification, 'accept')}
                                disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              >
                                {processingInviteId === notification.id ? 'Processando...' : 'Aceitar'}
                              </button>
                              <button
                                className="team-invite-list__button team-invite-list__button--ghost"
                                type="button"
                                onClick={() => requestDecisionConfirmation(notification, 'decline')}
                                disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              >
                                Recusar
                              </button>
                            </div>
                          ) : (
                            <span
                              className={`team-invite-list__status team-invite-list__status--${notification.status.toLowerCase()}`}
                            >
                              {getNotificationStatusLabel(notification)}
                            </span>
                          )}
                          {canDeleteNotification(notification) ? (
                            <button
                              className="team-invite-list__icon-button"
                              type="button"
                              onClick={() => requestDeleteNotificationConfirmation(notification)}
                              disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              aria-label="Excluir notificação"
                            >
                              <TrashIcon />
                            </button>
                          ) : null}
                        </div>
                      </article>
                    ))}
                  </div>
                ) : (
                  <span className="team-panel__empty">
                    Nenhuma notificação disponível para sua conta neste momento.
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
                    <span className="home-panel__badge">Você pode sair</span>
                  )}
                </div>

                {sectors.length > 0 ? (
                  <div className="team-sectors">
                    {sectors.map((sector) => (
                      <div className="team-sectors__item is-active" key={sector.id}>
                        <span>{sector.name}</span>
                        <strong>{sector.description}</strong>
                        {userRole === 'employee' ? (
                          <>
                            <p className="team-sectors__hint">Clique abaixo para sair deste setor.</p>
                            <button
                              className="team-panel__action-button team-panel__action-button--danger team-sectors__leave-button"
                              type="button"
                              onClick={() => requestLeaveSectorConfirmation(sector)}
                              disabled={leavingSectorId === sector.id}
                            >
                              {leavingSectorId === sector.id ? 'Saindo...' : 'Sair do setor'}
                            </button>
                          </>
                        ) : null}
                      </div>
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
                      <div className={`team-panel__head${userRole === 'admin' ? ' team-panel__head--admin' : ''}`}>
                        <span>Nome</span>
                        <span>Função</span>
                        <span>Setores</span>
                        <span>Status</span>
                        {userRole === 'admin' ? <span>Ações</span> : null}
                      </div>

                      {visibleTeamMembers.map((member) => (
                        <div
                          className={`team-panel__row${userRole === 'admin' ? ' team-panel__row--admin' : ''}`}
                          key={member.id}
                        >
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
                          <span className="team-panel__status">{formatMemberStatus(member.status)}</span>
                          {userRole === 'admin' ? (
                            <span className="team-panel__actions">
                              <button
                                className="team-panel__action-button team-panel__action-button--danger"
                                type="button"
                                onClick={() => requestRemoveMemberConfirmation(member)}
                                disabled={isTeamDataLoading || processingMemberId === member.id}
                              >
                                {processingMemberId === member.id ? 'Removendo...' : 'Remover da empresa'}
                              </button>
                            </span>
                          ) : null}
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

      <ConfirmActionModal
        isOpen={Boolean(pendingConfirmation)}
        title={pendingConfirmation?.title}
        description={pendingConfirmation?.description}
        confirmLabel={pendingConfirmation?.confirmLabel}
        confirmVariant={pendingConfirmation?.confirmVariant}
        onCancel={closeConfirmation}
        onConfirm={handleConfirmAction}
        isProcessing={isConfirmingAction}
      />
    </main>
  )
}

export default Team

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 7h16m-10 4v5m4-5v5M9 4h6l1 2H8l1-2Zm1 16h4a2 2 0 0 0 2-2V7H8v11a2 2 0 0 0 2 2Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
