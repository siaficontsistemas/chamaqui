import { ChevronLeftIcon, ChevronRightIcon } from '../../dashboardIcons'

function TicketListPagination({ currentPage, onPageChange, pageSize, totalItems }) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1
  const endItem = totalItems === 0 ? 0 : Math.min(currentPage * pageSize, totalItems)
  const canGoBack = currentPage > 1
  const canGoForward = currentPage < totalPages

  return (
    <div className="ticket-list__pagination">
      <span>
        {startItem}-{endItem} de {totalItems}
      </span>

      <button
        aria-label="Página anterior"
        className="ticket-list__nav"
        disabled={!canGoBack}
        type="button"
        onClick={() => onPageChange(currentPage - 1)}
      >
        <ChevronLeftIcon />
      </button>

      <button
        aria-label="Próxima página"
        className="ticket-list__nav"
        disabled={!canGoForward}
        type="button"
        onClick={() => onPageChange(currentPage + 1)}
      >
        <ChevronRightIcon />
      </button>
    </div>
  )
}

export default TicketListPagination
