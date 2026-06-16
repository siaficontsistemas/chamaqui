import { createPortal } from 'react-dom'
import { useMemo, useState } from 'react'
import ConfirmActionModal from '../confirm-action-modal/ConfirmActionModal'

function Header({
  activeSection,
  isTeamRole,
  isNotificationLoading,
  navigationGroups = [],
  onNavigateLogin,
  onAcceptInvite,
  onAcceptCompanyAccessRequest,
  onAcceptCompanyInvite,
  onAcceptCompanyPartnership,
  onAcceptTicketTransfer,
  onDeleteNotification,
  onDeclineCompanyAccessRequest,
  onDeclineCompanyInvite,
  onDeclineCompanyPartnership,
  onDeclineInvite,
  onDeclineTicketTransfer,
  onOpenNotification,
  onSectionChange,
  notifications = [],
  roleLabel,
  ticketSummary,
  isTicketSummaryLoading,
}) {
  const [notificationActionError, setNotificationActionError] = useState('')
  const [processingNotificationAction, setProcessingNotificationAction] = useState({
    inviteId: '',
    type: '',
  })
  const [isMobileNavigationOpen, setIsMobileNavigationOpen] = useState(false)
  const [isNotificationMenuOpen, setIsNotificationMenuOpen] = useState(false)
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false)
  const [pendingConfirmation, setPendingConfirmation] = useState(null)
  const [isConfirmingAction, setIsConfirmingAction] = useState(false)
  const currentSectionId = Array.isArray(activeSection) ? activeSection[0] : activeSection
  const summaryItems = useMemo(
    () => [
      {
        className: 'home-stat home-stat--open',
        label: 'Abertos',
        value: (ticketSummary?.open ?? 0) + (ticketSummary?.inProgress ?? 0),
      },
      {
        className: 'home-stat home-stat--closed',
        label: 'Fechados',
        value: ticketSummary?.closed ?? 0,
      },
    ],
    [ticketSummary]
  )
  const currentSectionLabel = useMemo(() => {
    const allItems = navigationGroups.flatMap((group) => group.items ?? [])
    return allItems.find((item) => item.id === currentSectionId)?.label ?? 'Painel'
  }, [currentSectionId, navigationGroups])
  const notificationCount = notifications.length

  function getNotificationTitle(notification) {
    if (notification.type === 'app-feedback') {
      return notification.title
    }

    if (notification.type === 'ticket-assignment') {
      return `Novo chamado ${notification.ticketProtocol} em ${notification.companyName}`
    }

    if (notification.type === 'ticket-transfer') {
      return `${notification.senderName} transferiu ${notification.ticketProtocol} em ${notification.companyName}`
    }

    if (notification.type === 'ticket-reply') {
      return `${notification.requesterName} respondeu ${notification.ticketProtocol}`
    }

    if (notification.type === 'ticket-closure') {
      return `Chamado ${notification.ticketProtocol} foi fechado`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return 'Você entrou na empresa'
      }

      if (notification.removalType === 'COMPANY_DELETED') {
        return 'A empresa foi excluída'
      }

      return notification.removalType === 'COMPANY_REMOVED'
        ? 'Você foi removido da empresa'
        : `Você foi removido do setor ${notification.sectorName}`
    }

    if (notification.type === 'calendar-reminder') {
      return `Prazo: ${notification.obligationTitle}`
    }

    if (notification.type === 'company-partnership') {
      if (notification.eventType === 'REQUESTED') {
        return `${notification.actorCompanyName} solicitou parceria`
      }

      if (notification.eventType === 'ACCEPTED') {
        return `${notification.actorCompanyName} aceitou a parceria`
      }

      return `${notification.actorCompanyName} desfez a parceria`
    }

    if (notification.type === 'company-access-request') {
      return `${notification.requesterName} solicitou entrada na empresa`
    }

    if (notification.type === 'company-invite') {
      return `${notification.companyName} convidou você para entrar na empresa`
    }

    if (notification.type === 'received') {
      return `${notification.invitedByName} convidou você`
    }

    if (notification.status === 'ACCEPTED') {
      return `${notification.invitedName} aceitou o convite`
    }

    if (notification.status === 'CANCELED') {
      return `${notification.invitedName} recusou o convite`
    }

    return `${notification.invitedName} não respondeu o convite`
  }

  function getNotificationDescription(notification) {
    if (notification.type === 'app-feedback') {
      return notification.description
    }

    if (notification.type === 'ticket-assignment') {
      const requesterCompanyLabel = notification.requesterCompanyName
        ? ` Empresa solicitante: ${notification.requesterCompanyName}.`
        : ''
      return `${notification.requesterName} abriu "${notification.ticketTitle}" para o setor ${notification.sectorName} da empresa ${notification.companyName}.${requesterCompanyLabel}`
    }

    if (notification.type === 'ticket-transfer') {
      const requesterCompanyLabel = notification.requesterCompanyName
        ? ` Empresa solicitante: ${notification.requesterCompanyName}.`
        : ''
      return `O chamado "${notification.ticketTitle}" do setor ${notification.sectorName} da empresa ${notification.companyName} foi transferido para você por ${notification.senderName}.${requesterCompanyLabel}`
    }

    if (notification.type === 'ticket-reply') {
      const requesterCompanyLabel = notification.requesterCompanyName
        ? ` Empresa solicitante: ${notification.requesterCompanyName}.`
        : ''
      const previewLabel = notification.messagePreview
        ? ` Mensagem: "${notification.messagePreview}".`
        : ''
      return `${notification.requesterName} enviou uma nova mensagem no chamado "${notification.ticketTitle}" do setor ${notification.sectorName} da empresa ${notification.companyName}.${requesterCompanyLabel}${previewLabel}`
    }

    if (notification.type === 'ticket-closure') {
      return `O seu chamado "${notification.ticketTitle}" no setor ${notification.sectorName} da empresa ${notification.companyName} foi fechado por ${notification.closedByName}.`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return `${notification.removedByName} vinculou seu cadastro à empresa ${notification.companyName || 'informada'}.`
      }

      if (notification.removalType === 'COMPANY_DELETED') {
        return `${notification.removedByName} excluiu a empresa ${notification.companyName || 'informada'}. Os setores dessa empresa não existem mais para você.`
      }

      if (notification.removalType === 'COMPANY_REMOVED') {
        return `${notification.removedByName} removeu seu acesso da empresa ${notification.companyName || 'informada'}.`
      }

      return `${notification.removedByName} removeu sua participação do setor ${notification.sectorName}.`
    }

    if (notification.type === 'calendar-reminder') {
      return `A obrigação "${notification.obligationTitle}" da empresa ${notification.companyName || 'informada'} vence em ${formatNotificationDate(notification.dueAt)}.`
    }

    if (notification.type === 'company-partnership') {
      if (notification.eventType === 'REQUESTED') {
        return `${notification.actorName} enviou uma solicitação entre ${notification.requesterCompanyName} e ${notification.targetCompanyName}.`
      }

      if (notification.eventType === 'ACCEPTED') {
        return `${notification.actorName} confirmou o vínculo entre ${notification.requesterCompanyName} e ${notification.targetCompanyName}.`
      }

      return `${notification.actorName} removeu o vínculo entre ${notification.requesterCompanyName} e ${notification.targetCompanyName}.`
    }

    if (notification.type === 'company-access-request') {
      const participationLabel =
        notification.requestedRole === 'employee' ? 'responder chamados' : 'criar chamados'
      const documentLabel = notification.requesterDocumentNumber
        ? ` CPF ${notification.requesterDocumentNumber}.`
        : ''
      return `${notification.requesterName} (${notification.requesterEmail}) pediu acesso à empresa ${notification.companyName} para ${participationLabel}.${documentLabel}`
    }

    if (notification.type === 'company-invite') {
      const participationLabel =
        notification.requestedRole === 'employee' ? 'responder chamados' : 'criar chamados'
      const documentLabel = notification.requesterDocumentNumber
        ? ` CPF ${notification.requesterDocumentNumber}.`
        : ''
      return `${notification.companyName} convidou você para entrar na empresa e ${participationLabel}.${documentLabel}`
    }

    const sectorNames = notification.sectorNames?.join(', ') || 'setor não informado'

    if (notification.type === 'received') {
      return `Participação na equipe para ${sectorNames}.`
    }

    if (notification.status === 'ACCEPTED') {
      return `Entrou na equipe pelos setores ${sectorNames}.`
    }

    if (notification.status === 'CANCELED') {
      return `Recusou o convite para os setores ${sectorNames}.`
    }

    return `O convite para os setores ${sectorNames} expirou.`
  }

  function getNotificationStatusLabel(notification) {
    if (notification.type === 'app-feedback') {
      if (notification.status === 'DECLINED') {
        return 'Atenção'
      }

      return 'Atualização'
    }

    if (notification.type === 'ticket-assignment') {
      return 'Novo'
    }

    if (notification.type === 'ticket-transfer') {
      if (notification.status === 'PENDING') {
        return 'Pendente'
      }

      if (notification.status === 'ACCEPTED') {
        return 'Aceito'
      }

      if (notification.status === 'DECLINED') {
        return 'Recusado'
      }
    }

    if (notification.type === 'ticket-reply') {
      return 'Nova resposta'
    }

    if (notification.type === 'ticket-closure') {
      return 'Fechado'
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return 'Novo acesso'
      }

      return 'Removido'
    }

    if (notification.type === 'calendar-reminder') {
      if (notification.status === 'OVERDUE') {
        return 'Atrasado'
      }

      if (notification.status === 'DUE_TODAY') {
        return 'Vence hoje'
      }

      return 'Lembrete'
    }

    if (notification.type === 'company-partnership') {
      if (notification.eventType === 'REQUESTED') {
        return 'Pendente'
      }

      if (notification.eventType === 'ACCEPTED') {
        return 'Aceito'
      }

      return 'Removido'
    }

    if (notification.type === 'company-access-request') {
      return 'Pendente'
    }

    if (notification.type === 'company-invite') {
      return 'Pendente'
    }

    if (notification.status === 'PENDING') {
      return 'Pendente'
    }

    if (notification.status === 'ACCEPTED') {
      return 'Aceito'
    }

    if (notification.status === 'CANCELED') {
      return 'Recusado'
    }

    return 'Expirado'
  }

  function canDeleteNotification(notification) {
    if (notification.type === 'app-feedback') {
      return true
    }

    return !(
      (notification.type === 'ticket-transfer' && notification.status === 'PENDING') ||
      notification.type === 'company-access-request' ||
      notification.type === 'company-invite'
    )
  }

  function formatNotificationDate(value) {
    if (!value) {
      return 'data não informada'
    }

    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value))
  }

  async function handleNotificationAction(inviteId, type, action) {
    setNotificationActionError('')
    setProcessingNotificationAction({
      inviteId,
      type,
    })

    try {
      await action(inviteId)
    } catch (error) {
      setNotificationActionError(error.message || 'Não foi possível atualizar a notificação.')
    } finally {
      setProcessingNotificationAction({
        inviteId: '',
        type: '',
      })
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

  function requestDeleteConfirmation(notification) {
    openConfirmation({
      title: 'Excluir notificação',
      description: 'Tem certeza que deseja excluir esta notificação?',
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () =>
        handleNotificationAction(notification.id, 'delete', () => onDeleteNotification(notification)),
    })
  }

  function requestInviteConfirmation(notification, actionType) {
    const sectorNames = notification.sectorNames?.join(', ') || 'setor não informado'
    const isAccepting = actionType === 'accept'

    openConfirmation({
      title: isAccepting ? 'Aceitar convite' : 'Recusar convite',
      description: isAccepting
        ? `Tem certeza que deseja aceitar o convite para os setores ${sectorNames}?`
        : `Tem certeza que deseja recusar o convite para os setores ${sectorNames}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () =>
        handleNotificationAction(
          notification.id,
          isAccepting ? 'accept' : 'decline',
          isAccepting ? onAcceptInvite : onDeclineInvite
        ),
    })
  }

  function requestTransferConfirmation(notification, actionType) {
    const isAccepting = actionType === 'accept'
    const actionLabel = isAccepting ? 'aceitar' : 'recusar'

    openConfirmation({
      title: isAccepting ? 'Aceitar transferência' : 'Recusar transferência',
      description: `Tem certeza que deseja ${actionLabel} a transferência do chamado ${notification.ticketProtocol}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () =>
        handleNotificationAction(
          notification.id,
          isAccepting ? 'accept-transfer' : 'decline-transfer',
          isAccepting ? onAcceptTicketTransfer : onDeclineTicketTransfer
        ),
    })
  }

  function requestPartnershipConfirmation(notification, actionType) {
    const isAccepting = actionType === 'accept'
    const actionLabel = isAccepting ? 'aceitar' : 'recusar'

    openConfirmation({
      title: isAccepting ? 'Aceitar parceria' : 'Recusar parceria',
      description: `Tem certeza que deseja ${actionLabel} a solicitação de parceria da empresa ${notification.actorCompanyName}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () =>
        handleNotificationAction(
          notification.partnershipId,
          isAccepting ? 'accept-partnership' : 'decline-partnership',
          isAccepting ? onAcceptCompanyPartnership : onDeclineCompanyPartnership
        ),
    })
  }

  function requestCompanyAccessConfirmation(notification, actionType) {
    const isAccepting = actionType === 'accept'
    const actionLabel = isAccepting ? 'aceitar' : 'recusar'

    openConfirmation({
      title: isAccepting ? 'Aprovar acesso' : 'Recusar acesso',
      description: `Tem certeza que deseja ${actionLabel} a solicitação de ${notification.requesterName} para entrar na empresa ${notification.companyName}?`,
      confirmLabel: isAccepting ? 'Aprovar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () =>
        handleNotificationAction(
          notification.id,
          isAccepting ? 'accept-company-access' : 'decline-company-access',
          isAccepting ? onAcceptCompanyAccessRequest : onDeclineCompanyAccessRequest
        ),
    })
  }

  function requestCompanyInviteConfirmation(notification, actionType) {
    const isAccepting = actionType === 'accept'
    const actionLabel = isAccepting ? 'aceitar' : 'recusar'

    openConfirmation({
      title: isAccepting ? 'Aceitar convite da empresa' : 'Recusar convite da empresa',
      description: `Tem certeza que deseja ${actionLabel} o convite da empresa ${notification.companyName}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () =>
        handleNotificationAction(
          notification.id,
          isAccepting ? 'accept-company-invite' : 'decline-company-invite',
          isAccepting ? onAcceptCompanyInvite : onDeclineCompanyInvite
        ),
    })
  }

  function requestLogoutConfirmation() {
    openConfirmation({
      title: 'Sair da conta',
      description: 'Tem certeza que deseja sair da sua conta agora?',
      confirmLabel: 'Sair',
      confirmVariant: 'danger',
      onConfirm: async () => {
        onNavigateLogin()
      },
    })
  }

  function handleSectionNavigation(sectionId) {
    onSectionChange(sectionId)
    setIsMobileNavigationOpen(false)
    setIsNotificationMenuOpen(false)
    setIsUserMenuOpen(false)
  }

  function isNotificationClickable(notification) {
    return (
      (notification?.type === 'calendar-reminder' && Boolean(notification?.obligationId)) ||
      ((notification?.type === 'ticket-assignment' || notification?.type === 'ticket-reply') &&
        Boolean(notification?.ticketId))
    )
  }

  function handleNotificationClick(notification) {
    if (!isNotificationClickable(notification) || !onOpenNotification) {
      return
    }

    onOpenNotification(notification)
    setIsNotificationMenuOpen(false)
    setIsMobileNavigationOpen(false)
    setIsUserMenuOpen(false)
  }

  function handleOpenNotifications() {
    setIsNotificationMenuOpen((currentState) => !currentState)
    setIsUserMenuOpen(false)
    setIsMobileNavigationOpen(false)
  }

  function handleOpenUserMenu() {
    setIsUserMenuOpen((currentState) => !currentState)
    setIsNotificationMenuOpen(false)
    setIsMobileNavigationOpen(false)
  }

  function handleToggleMobileNavigation() {
    setIsMobileNavigationOpen((currentState) => !currentState)
    setIsNotificationMenuOpen(false)
    setIsUserMenuOpen(false)
  }

  function openNotificationsFromMobileMenu() {
    setIsMobileNavigationOpen(false)
    setIsUserMenuOpen(false)
    setIsNotificationMenuOpen(true)
  }

  const mobileNavigation =
    isMobileNavigationOpen && typeof document !== 'undefined'
      ? createPortal(
          <div className="home-mobile-menu-layer">
            <button
              className="home-mobile-menu-layer__backdrop"
              type="button"
              aria-label="Fechar menu"
              onClick={() => setIsMobileNavigationOpen(false)}
            />
            <aside className="home-mobile-menu" role="dialog" aria-modal="true" aria-label="Navegação principal">
              <div className="home-mobile-menu__header">
                <div>
                  <span className="home-mobile-menu__eyebrow">Menu</span>
                  <strong>{currentSectionLabel}</strong>
                </div>
                <button
                  className="home-mobile-menu__close"
                  type="button"
                  onClick={() => setIsMobileNavigationOpen(false)}
                  aria-label="Fechar menu"
                >
                  <CloseIcon />
                </button>
              </div>

              <div className="home-mobile-menu__section">
                <span className="home-mobile-menu__eyebrow">Resumo</span>
                <div className="home-mobile-menu__stats">
                  {summaryItems.map((item) => (
                    <div className={item.className} key={`mobile-${item.label}`}>
                      <span className="home-stat__value">{isTicketSummaryLoading ? '...' : item.value}</span>
                      <span className="home-stat__label">{item.label}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="home-mobile-menu__section">
                <span className="home-mobile-menu__eyebrow">Ações rápidas</span>
                <div className="home-mobile-menu__actions">
                  {isTeamRole ? (
                    <button className="home-mobile-menu__action" type="button" onClick={() => handleSectionNavigation('team')}>
                      Equipe
                    </button>
                  ) : null}
                  <button className="home-mobile-menu__action" type="button" onClick={() => handleSectionNavigation('newTicket')}>
                    Novo chamado
                  </button>
                  <button className="home-mobile-menu__action" type="button" onClick={openNotificationsFromMobileMenu}>
                    Notificações
                  </button>
                  <button className="home-mobile-menu__action home-mobile-menu__action--ghost" type="button" onClick={() => handleSectionNavigation('myData')}>
                    Meus dados
                  </button>
                </div>
              </div>

              {navigationGroups.map((group) => (
                <div className="home-mobile-menu__section" key={`mobile-${group.title}`}>
                  <span className="home-mobile-menu__eyebrow">{group.title}</span>
                  <div className="home-mobile-menu__nav">
                    {group.items.map((item) => (
                      <button
                        className={`home-sidebar__item${currentSectionId === item.id ? ' is-active' : ''}`}
                        key={`mobile-item-${item.id}`}
                        type="button"
                        onClick={() => handleSectionNavigation(item.id)}
                      >
                        <span
                          className={`home-sidebar__icon${
                            item.marker ? ` home-sidebar__icon--${item.marker}` : ''
                          }`}
                          aria-hidden="true"
                        >
                          <SidebarIcon icon={item.icon} itemId={item.id} />
                        </span>
                        <span>{item.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </aside>
          </div>,
          document.body
        )
      : null

  return (
    <header className="home-topbar">
      <div className="home-topbar__mobile-bar">
        <button
          className="home-topbar__menu-button"
          type="button"
          onClick={handleToggleMobileNavigation}
          aria-label="Abrir menu principal"
          aria-expanded={isMobileNavigationOpen}
        >
          <MenuIcon />
        </button>
        <div className="home-topbar__mobile-current">
          <span className="home-topbar__mobile-label">Painel</span>
          <strong>{currentSectionLabel}</strong>
        </div>
      </div>

      <div className="home-topbar__stats">
        {summaryItems.map((item) => (
          <div className={item.className} key={item.label}>
            <span className="home-stat__value">
              {isTicketSummaryLoading ? '...' : item.value}
            </span>
            <span className="home-stat__label">{item.label}</span>
          </div>
        ))}
        {isTeamRole ? (
          <button
            className={`home-topbar__action${activeSection === 'team' ? ' is-active' : ''}`}
            type="button"
            onClick={() => handleSectionNavigation('team')}
          >
            Equipe
          </button>
        ) : null}
        <button
          className={`home-topbar__action${activeSection === 'newTicket' ? ' is-active' : ''}`}
          type="button"
          onClick={() => handleSectionNavigation('newTicket')}
        >
          Novo chamado
        </button>
      </div>

      <div className="home-topbar__actions">
        <button
          className="home-notifications"
          type="button"
          onClick={handleOpenNotifications}
          aria-label="Abrir notificações"
          aria-expanded={isNotificationMenuOpen}
        >
          <span className="home-notifications__icon">
            <BellIcon />
          </span>
          <span className="home-notifications__label">Notificações</span>
          <span className="home-notifications__count">{isNotificationLoading ? '...' : notificationCount}</span>
        </button>

        <button
          className="home-user"
          type="button"
          onClick={handleOpenUserMenu}
          aria-label="Abrir menu do usuário"
          aria-expanded={isUserMenuOpen}
        >
          <span className="home-user__avatar">
            <UserIcon />
          </span>
          <span className="home-user__name">{roleLabel}</span>
          <ChevronDownIcon />
        </button>
      </div>

      {isNotificationMenuOpen ? (
        <div className="home-notification-menu" role="dialog" aria-label="Notificações">
          <div className="home-notification-menu__header">
            <strong>Atualizações da equipe</strong>
            <span>{isNotificationLoading ? 'Carregando...' : `${notificationCount} item(ns)`}</span>
          </div>

          <div className="home-notification-menu__list">
            {notificationCount > 0 ? (
              notifications.map((notification) => (
                <article
                  className={`home-notification-card${isNotificationClickable(notification) ? ' home-notification-card--clickable' : ''}`}
                  key={notification.id}
                  onClick={() => handleNotificationClick(notification)}
                  onKeyDown={(event) => {
                    if (!isNotificationClickable(notification)) {
                      return
                    }
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      handleNotificationClick(notification)
                    }
                  }}
                  role={isNotificationClickable(notification) ? 'button' : undefined}
                  tabIndex={isNotificationClickable(notification) ? 0 : undefined}
                >
                  <div className="home-notification-card__top">
                    <span className={`home-notification-card__status home-notification-card__status--${notification.status.toLowerCase()}`}>
                      {getNotificationStatusLabel(notification)}
                    </span>
                    {canDeleteNotification(notification) ? (
                      <button
                        className="home-notification-card__icon-button"
                        type="button"
                        onClick={(event) => {
                          event.stopPropagation()
                          requestDeleteConfirmation(notification)
                        }}
                        disabled={processingNotificationAction.inviteId === notification.id}
                        aria-label="Excluir notificação"
                      >
                        <TrashIcon />
                      </button>
                    ) : null}
                  </div>
                  <strong>{getNotificationTitle(notification)}</strong>
                  <p>{getNotificationDescription(notification)}</p>
                  {notification.type === 'received' ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => requestInviteConfirmation(notification, 'accept')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id
                        && processingNotificationAction.type === 'accept'
                          ? 'Processando...'
                          : 'Aceitar'}
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => requestInviteConfirmation(notification, 'decline')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id
                        && processingNotificationAction.type === 'decline'
                          ? 'Processando...'
                          : 'Recusar'}
                      </button>
                    </div>
                  ) : null}
                  {notification.type === 'ticket-transfer' && notification.status === 'PENDING' ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => requestTransferConfirmation(notification, 'accept')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id
                        && processingNotificationAction.type === 'accept-transfer'
                          ? 'Processando...'
                          : 'Aceitar'}
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => requestTransferConfirmation(notification, 'decline')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id
                        && processingNotificationAction.type === 'decline-transfer'
                          ? 'Processando...'
                          : 'Recusar'}
                      </button>
                    </div>
                  ) : null}
                  {notification.type === 'company-partnership' &&
                  notification.eventType === 'REQUESTED' &&
                  notification.canRespond ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => requestPartnershipConfirmation(notification, 'accept')}
                        disabled={processingNotificationAction.inviteId === notification.partnershipId}
                      >
                        {processingNotificationAction.inviteId === notification.partnershipId &&
                        processingNotificationAction.type === 'accept-partnership'
                          ? 'Processando...'
                          : 'Aceitar'}
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => requestPartnershipConfirmation(notification, 'decline')}
                        disabled={processingNotificationAction.inviteId === notification.partnershipId}
                      >
                        {processingNotificationAction.inviteId === notification.partnershipId &&
                        processingNotificationAction.type === 'decline-partnership'
                          ? 'Processando...'
                          : 'Recusar'}
                      </button>
                    </div>
                  ) : null}
                  {notification.type === 'company-access-request' &&
                  notification.status === 'PENDING' ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => requestCompanyAccessConfirmation(notification, 'accept')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id &&
                        processingNotificationAction.type === 'accept-company-access'
                          ? 'Processando...'
                          : 'Aprovar'}
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => requestCompanyAccessConfirmation(notification, 'decline')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id &&
                        processingNotificationAction.type === 'decline-company-access'
                          ? 'Processando...'
                          : 'Recusar'}
                      </button>
                    </div>
                  ) : null}
                  {notification.type === 'company-invite' &&
                  notification.status === 'PENDING' ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => requestCompanyInviteConfirmation(notification, 'accept')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id &&
                        processingNotificationAction.type === 'accept-company-invite'
                          ? 'Processando...'
                          : 'Aceitar'}
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => requestCompanyInviteConfirmation(notification, 'decline')}
                        disabled={processingNotificationAction.inviteId === notification.id}
                      >
                        {processingNotificationAction.inviteId === notification.id &&
                        processingNotificationAction.type === 'decline-company-invite'
                          ? 'Processando...'
                          : 'Recusar'}
                      </button>
                    </div>
                  ) : null}
                </article>
              ))
            ) : (
              <div className="home-notification-menu__empty">
                Nenhuma notificação disponível no momento.
              </div>
            )}
            {notificationActionError ? (
              <div className="home-notification-menu__error">{notificationActionError}</div>
            ) : null}
          </div>
        </div>
      ) : null}

      {isUserMenuOpen ? (
        <div className="home-user-menu" role="dialog" aria-label="Menu do usuário">
          <button
            className="home-user-menu__item"
            type="button"
            onClick={() => {
              handleSectionNavigation('myData')
              setIsUserMenuOpen(false)
            }}
          >
            Meus dados
          </button>
          <button
            className="home-user-menu__item home-user-menu__item--danger"
            type="button"
            onClick={() => {
              setIsUserMenuOpen(false)
              requestLogoutConfirmation()
            }}
          >
            Sair
          </button>
        </div>
      ) : null}

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
      {mobileNavigation}
    </header>
  )
}

export default Header

function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 1 1 14 0"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ChevronDownIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="m6 9 6 6 6-6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function MenuIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M4 7h16M4 12h16M4 17h16"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="m6 6 12 12M18 6 6 18"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function SidebarIcon({ icon, itemId }) {
  if (icon === 'calendar' || itemId === 'calendar') {
    return <CalendarIcon />
  }

  if (itemId === 'reports') {
    return <ReportIcon />
  }

  if (icon === 'plus') {
    return <PlusIcon />
  }

  if (icon === 'building' || itemId.startsWith('sector-')) {
    return <BuildingIcon />
  }

  if (itemId === 'open' || itemId === 'closed') {
    return <StatusDotIcon />
  }

  return <PhoneIcon />
}

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M15 17H9m7-6a4 4 0 1 0-8 0c0 4-2 5-2 5h12s-2-1-2-5Zm-5 10a2 2 0 0 0 4 0"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

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

function PhoneIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M7.5 4.5c0 6.627 5.373 12 12 12l2-3.5-4-2-1.5 1.5a10.5 10.5 0 0 1-4.5-4.5L13 6.5l-2-4-3.5 2Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ReportIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M7 4.5h10v15H7v-15Zm3 4h4M10 12h4M10 15.5h4M8.5 8.5h.01M8.5 12h.01M8.5 15.5h.01"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function BuildingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M4 20V8.5L10 5v15M20 20V11l-6-3v12M2 20h20M7 9.5h.01M7 12.5h.01M7 15.5h.01M13.5 11.5h.01M13.5 14.5h.01M16.5 11.5h.01M16.5 14.5h.01"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function StatusDotIcon() {
  return <span className="status-dot" />
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M12 5v14M5 12h14"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M7 3.5v3M17 3.5v3M4.5 9h15M6.5 5.5h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-10a2 2 0 0 1 2-2ZM8 12.5h3M8 16h5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
