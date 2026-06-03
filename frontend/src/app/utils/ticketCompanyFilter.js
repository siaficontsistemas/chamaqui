export const ALL_TICKET_COMPANIES_VALUE = '__ALL_TICKET_COMPANIES__'
export const UNINFORMED_TICKET_COMPANY_LABEL = 'Não informado'

export function normalizeTicketRequesterCompanyName(ticket) {
  const companyName = ticket?.requesterCompanyName?.trim()
  return companyName || UNINFORMED_TICKET_COMPANY_LABEL
}

export function buildTicketRequesterCompanyOptions(tickets) {
  return Array.from(
    new Set((Array.isArray(tickets) ? tickets : []).map((ticket) => normalizeTicketRequesterCompanyName(ticket)))
  ).sort((firstCompany, secondCompany) => firstCompany.localeCompare(secondCompany, 'pt-BR'))
}

export function matchesTicketRequesterCompany(ticket, selectedCompany) {
  if (!selectedCompany || selectedCompany === ALL_TICKET_COMPANIES_VALUE) {
    return true
  }

  return normalizeTicketRequesterCompanyName(ticket) === selectedCompany
}
