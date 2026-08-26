export function formatTicketRequesterName(fullName) {
  const nameParts = fullName?.trim().split(/\s+/).filter(Boolean) || []

  return nameParts.slice(0, 2).join(' ') || 'Não informado'
}
