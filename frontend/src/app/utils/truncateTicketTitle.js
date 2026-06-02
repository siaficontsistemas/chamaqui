const MAX_TICKET_TITLE_LENGTH = 30

export function truncateTicketTitle(title, maxLength = MAX_TICKET_TITLE_LENGTH) {
  const normalizedTitle = typeof title === 'string' ? title.trim() : ''

  if (normalizedTitle.length <= maxLength) {
    return normalizedTitle
  }

  if (maxLength <= 3) {
    return '.'.repeat(Math.max(maxLength, 0))
  }

  return `${normalizedTitle.slice(0, maxLength - 3)}...`
}

export { MAX_TICKET_TITLE_LENGTH }
