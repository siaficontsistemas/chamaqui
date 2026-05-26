import TenantBrandImage from '../branding/TenantBrandImage'
import { useTenantBranding } from '../../context/TenantBrandingContext'

function Sidebar({ activeSection, navigationGroups, onSectionChange }) {
  const currentSections = Array.isArray(activeSection) ? activeSection : [activeSection]
  const currentPage = currentSections[0]
  const ticketSections = ['tickets', 'all', 'open', 'closed', 'newTicket']
  const { branding: companyBranding, companyLogoUrl } = useTenantBranding()

  return (
    <div className="home-sidebar-column">
      <div className="home-brand-panel">
        <BrandMark
          onClick={() => onSectionChange('tickets')}
          companyLogoUrl={companyLogoUrl}
          companyName={companyBranding?.companyName || ''}
        />
      </div>

      <aside className="home-sidebar">
        {navigationGroups.map((group) => (
          <div className="home-sidebar__group" key={group.title}>
            <h2 className="home-sidebar__title">{group.title}</h2>

            <div className="home-sidebar__items">
              {group.items.map((item) => {
                const isActive =
                  currentSections.includes(item.id) ||
                  (item.id === 'tickets' && ticketSections.includes(currentPage)) ||
                  (item.id === 'all' && currentPage === 'tickets')

                return (
                  <button
                    className={`home-sidebar__item${isActive ? ' is-active' : ''}`}
                    key={item.id}
                    type="button"
                    onClick={() => onSectionChange(item.id)}
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
                )
              })}
            </div>
          </div>
        ))}

        <span className="home-sidebar__footer">ChamaAqui Helpdesk</span>
      </aside>
    </div>
  )
}

export default Sidebar

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

function BrandMark({ onClick, companyLogoUrl, companyName }) {
  return (
    <button
      className="home-brand"
      type="button"
      onClick={onClick}
      aria-label="Voltar para a tela de tickets"
    >
      <img className="home-brand__logo" src="/logo_chamaqui.png" alt="ChamaAqui Helpdesk" />
      {companyLogoUrl ? (
        <div className="home-brand__company">
          <TenantBrandImage
            className="home-brand__company-logo"
            src={companyLogoUrl}
            alt={companyName ? `Logo da empresa ${companyName}` : 'Logo da empresa'}
            label={companyName || 'Logo'}
          />
        </div>
      ) : null}
    </button>
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
