import { generatePath, matchPath } from 'react-router-dom'

export const PUBLIC_ROUTE_PATHS = {
  login: '/login',
  register: '/register',
  terms: '/termos-de-uso',
  privacy: '/politica-de-privacidade',
}

export const SECTION_ROUTE_PATHS = {
  tickets: '/tickets',
  calendar: '/calendar',
  reports: '/reports',
  all: '/tickets/all',
  open: '/tickets/open',
  closed: '/tickets/closed',
  newTicket: '/tickets/new',
  myData: '/my-data',
  team: '/team',
  createSector: '/sectors/new',
  clientCompanyRegister: '/client-companies/new',
}

const SECTION_MATCHERS = [
  ['calendar', '/calendar'],
  ['reports', '/reports'],
  ['all', '/tickets/all'],
  ['open', '/tickets/open'],
  ['closed', '/tickets/closed'],
  ['newTicket', '/tickets/new'],
  ['myData', '/my-data'],
  ['team', '/team'],
  ['createSector', '/sectors/new'],
  ['clientCompanyRegister', '/client-companies/new'],
  ['tickets', '/tickets'],
]

export function getSectionPath(sectionId) {
  if (SECTION_ROUTE_PATHS[sectionId]) {
    return SECTION_ROUTE_PATHS[sectionId]
  }

  return generatePath('/sectors/:sectorId', { sectorId: sectionId })
}

export function getTicketPath(ticketId) {
  return generatePath('/tickets/:ticketId', { ticketId })
}

export function getTicketAttachmentPath(ticketId, attachmentId) {
  return generatePath('/tickets/:ticketId/attachments/:attachmentId', {
    ticketId,
    attachmentId,
  })
}

export function getSectionIdFromPathname(pathname) {
  for (const [sectionId, pattern] of SECTION_MATCHERS) {
    if (matchPath({ path: pattern, end: true }, pathname)) {
      return sectionId
    }
  }

  const sectorMatch = matchPath({ path: '/sectors/:sectorId', end: true }, pathname)
  if (sectorMatch?.params?.sectorId) {
    return sectorMatch.params.sectorId
  }

  const ticketMatch = matchPath({ path: '/tickets/:ticketId', end: true }, pathname)
  if (ticketMatch?.params?.ticketId) {
    return 'tickets'
  }

  const attachmentMatch = matchPath(
    { path: '/tickets/:ticketId/attachments/:attachmentId', end: true },
    pathname
  )
  if (attachmentMatch?.params?.ticketId && attachmentMatch?.params?.attachmentId) {
    return 'tickets'
  }

  return null
}
