function Sidebar({ activeSection, navigationGroups, onSectionChange }) {
  const currentSections = Array.isArray(activeSection) ? activeSection : [activeSection]
  const currentPage = currentSections[0]

  return (
    <div className="home-sidebar-column">
      <div className="home-brand-panel">
        <BrandMark />
      </div>

      <aside className="home-sidebar">
        {navigationGroups.map((group) => (
          <div className="home-sidebar__group" key={group.title}>
            <h2 className="home-sidebar__title">{group.title}</h2>

            <div className="home-sidebar__items">
              {group.items.map((item) => {
                const isActive =
                  currentSections.includes(item.id) ||
                  (item.id === 'tickets' && currentPage !== 'reports') ||
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

        <span className="home-sidebar__footer">Lopes Consultoria</span>
      </aside>
    </div>
  )
}

export default Sidebar

function SidebarIcon({ icon, itemId }) {
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

function BrandMark() {
  return (
    <div className="home-brand" aria-label="Lopes Consultoria">
      <strong className="home-brand__name">LOPES</strong>
      <span className="home-brand__accent">CONSULTORIA</span>
      <span className="home-brand__subtitle">GESTÃO PÚBLICA</span>
    </div>
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
