import { useMemo, useState } from 'react'

function Header({
  activeSection,
  isTeamRole,
  isNotificationLoading,
  onNavigateLogin,
  onAcceptInvite,
  onDeclineInvite,
  onSectionChange,
  notifications = [],
  roleLabel,
  ticketSummary,
  isTicketSummaryLoading,
}) {
  const [isNotificationMenuOpen, setIsNotificationMenuOpen] = useState(false)
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false)
  const summaryItems = useMemo(
    () => [
      {
        className: 'home-stat home-stat--open',
        label: 'Abertos',
        value: ticketSummary?.open ?? 0,
      },
      {
        className: 'home-stat home-stat--closed',
        label: 'Fechados',
        value: ticketSummary?.closed ?? 0,
      },
    ],
    [ticketSummary]
  )
  const notificationCount = notifications.length

  function getNotificationTitle(notification) {
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

  return (
    <header className="home-topbar">
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
            onClick={() => onSectionChange('team')}
          >
            Equipe
          </button>
        ) : null}
        <button
          className={`home-topbar__action${activeSection === 'newTicket' ? ' is-active' : ''}`}
          type="button"
          onClick={() => onSectionChange('newTicket')}
        >
          Novo chamado
        </button>
      </div>

      <div className="home-topbar__actions">
        <button
          className="home-notifications"
          type="button"
          onClick={() => {
            setIsNotificationMenuOpen((currentState) => !currentState)
            setIsUserMenuOpen(false)
          }}
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
          onClick={() => {
            setIsUserMenuOpen((currentState) => !currentState)
            setIsNotificationMenuOpen(false)
          }}
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
                <article className="home-notification-card" key={notification.id}>
                  <span className={`home-notification-card__status home-notification-card__status--${notification.status.toLowerCase()}`}>
                    {notification.status === 'PENDING'
                      ? 'Pendente'
                      : notification.status === 'ACCEPTED'
                        ? 'Aceito'
                        : notification.status === 'CANCELED'
                          ? 'Recusado'
                          : 'Expirado'}
                  </span>
                  <strong>{getNotificationTitle(notification)}</strong>
                  <p>{getNotificationDescription(notification)}</p>
                  {notification.type === 'received' ? (
                    <div className="home-notification-card__actions">
                      <button
                        className="home-notification-card__button"
                        type="button"
                        onClick={() => onAcceptInvite(notification.id)}
                      >
                        Aceitar
                      </button>
                      <button
                        className="home-notification-card__button home-notification-card__button--ghost"
                        type="button"
                        onClick={() => onDeclineInvite(notification.id)}
                      >
                        Recusar
                      </button>
                    </div>
                  ) : null}
                </article>
              ))
            ) : (
              <div className="home-notification-menu__empty">
                Nenhuma notificação de convite disponível no momento.
              </div>
            )}
          </div>
        </div>
      ) : null}

      {isUserMenuOpen ? (
        <div className="home-user-menu" role="dialog" aria-label="Menu do usuário">
          <button
            className="home-user-menu__item"
            type="button"
            onClick={() => {
              onSectionChange('myData')
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
              onNavigateLogin()
            }}
          >
            Sair
          </button>
        </div>
      ) : null}
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
