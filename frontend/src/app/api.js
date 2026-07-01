const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:4200').replace(
  /\/$/,
  ''
)
const APP_AUTH_TOKEN_STORAGE_KEY = 'helpdesk.app.auth-token'


function isPlatformAdminRequest(path) {
  return path.startsWith('/api/v1/platform-admin/')
}

export function getStoredAppAuthToken() {
  if (typeof window === 'undefined') {
    return ''
  }

  try {
    const storedValue = window.localStorage.getItem(APP_AUTH_TOKEN_STORAGE_KEY) || ''
    return storedValue
  } catch {
    return ''
  }
}

export function storeAppAuthToken(token) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    if (!token) {
      window.localStorage.removeItem(APP_AUTH_TOKEN_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(APP_AUTH_TOKEN_STORAGE_KEY, token)
  } catch {
    // Ignora falhas de armazenamento local e segue com a resposta em memória.
  }
}

export function clearStoredAppAuthToken() {
  storeAppAuthToken('')
}

export function resolveApiAssetUrl(assetUrl) {
  if (!assetUrl) {
    return ''
  }

  if (/^https?:\/\//i.test(assetUrl)) {
    return assetUrl
  }

  if (assetUrl.startsWith('/')) {
    return `${API_BASE_URL}${assetUrl}`
  }

  return `${API_BASE_URL}/${assetUrl}`
}

async function apiRequest(path, options = {}) {
  const { headers: customHeaders = {}, credentials, ...requestOptions } = options
  const isFormData = requestOptions.body instanceof FormData
  const tenantHost =
    typeof window !== 'undefined' && window.location?.host ? window.location.host : ''
  const appAuthToken = isPlatformAdminRequest(path) ? '' : getStoredAppAuthToken()
  const headers = {
    ...(tenantHost ? { 'X-Tenant-Host': tenantHost } : {}),
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(appAuthToken ? { Authorization: `Bearer ${appAuthToken}` } : {}),
    ...customHeaders,
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: credentials || (isPlatformAdminRequest(path) ? 'include' : 'omit'),
    headers,
    ...requestOptions,
  })

  if (!response.ok) {
    const errorMessage = await extractErrorMessage(response)
    const error = new Error(errorMessage)
    error.status = response.status
    throw error
  }

  if (response.status === 204) {
    return null
  }

  const responseText = await response.text()

  if (!responseText.trim()) {
    return null
  }

  const data = JSON.parse(responseText)

  if (!isPlatformAdminRequest(path) && typeof data?.authToken === 'string' && data.authToken.trim()) {
    storeAppAuthToken(data.authToken.trim())
  }

  return data
}

async function extractErrorMessage(response) {
  try {
    const data = await response.json()

    if (typeof data === 'string' && data.trim()) {
      return data
    }

    if (data?.message) {
      if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
        return `${data.message} ${formatFieldErrors(data.fieldErrors)}`
      }
      return data.message
    }

    if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
      return formatFieldErrors(data.fieldErrors)
    }

    if (Array.isArray(data?.errors) && data.errors.length > 0) {
      return data.errors.join(', ')
    }

    if (data?.error) {
      return data.error
    }
  } catch {
    return `Não foi possível concluir a requisição (${response.status}).`
  }

  return `Não foi possível concluir a requisição (${response.status}).`
}

function formatFieldErrors(fieldErrors) {
  const entries = Object.entries(fieldErrors || {}).filter(
    ([fieldName, fieldMessage]) => fieldName && fieldMessage
  )

  if (entries.length === 0) {
    return 'Verifique os dados informados.'
  }

  const formattedEntries = entries.map(([fieldName, fieldMessage]) => {
    const label = getFieldLabel(fieldName)
    return `${label}: ${fieldMessage}`
  })

  return `Campos com erro: ${formattedEntries.join(' | ')}.`
}

function getFieldLabel(fieldName) {
  const labelsByField = {
    fullName: 'Nome',
    email: 'Email',
    authorEmail: 'Email do funcionário',
    phoneNumber: 'Telefone',
    documentNumber: 'CPF',
    companyOwnerId: 'Empresa',
    assignedToUserId: 'Funcionário',
    companyName: 'Nome da empresa',
    companyDocument: 'CNPJ da empresa',
    companyType: 'Tipo da empresa',
    title: 'Título',
    password: 'Senha',
    role: 'Tipo de cadastro',
    acceptedTerms: 'Termos de uso',
    acceptedPrivacyPolicy: 'Politica de privacidade',
  }

  return labelsByField[fieldName] || fieldName
}

export function loginUser(credentials) {
  return apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function getAuthMe() {
  return apiRequest('/api/v1/auth/me')
}

export function logoutCurrentUser() {
  return apiRequest('/api/v1/auth/logout', {
    method: 'POST',
  })
}

export function requestPasswordReset(payload) {
  return apiRequest('/api/v1/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function resetPasswordWithToken(payload) {
  return apiRequest('/api/v1/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function registerUser(payload) {
  return apiRequest('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function loginPlatformAdmin(credentials) {
  return apiRequest('/api/v1/platform-admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function getPlatformAdminMe() {
  return apiRequest('/api/v1/platform-admin/auth/me')
}

export function logoutPlatformAdmin() {
  return apiRequest('/api/v1/platform-admin/auth/logout', {
    method: 'POST',
  })
}

export function getPlatformAdminCompanies() {
  return apiRequest('/api/v1/platform-admin/companies')
}

export function createPlatformAdminCompany(payload) {
  return apiRequest('/api/v1/platform-admin/companies', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function activatePlatformAdminCompany(companyId) {
  return apiRequest(`/api/v1/platform-admin/companies/${encodeURIComponent(companyId)}/activate`, {
    method: 'PATCH',
  })
}

export function deactivatePlatformAdminCompany(companyId) {
  return apiRequest(`/api/v1/platform-admin/companies/${encodeURIComponent(companyId)}/deactivate`, {
    method: 'PATCH',
  })
}

export function getRegisterInvite(token) {
  return apiRequest(`/api/v1/auth/register-invite?token=${encodeURIComponent(token)}`)
}

export function getPublicTenantBranding(host) {
  return apiRequest('/api/v1/public/tenant-branding', {
    headers: host ? { 'X-Tenant-Host': host } : {},
  })
}

export function getPublicLegalDocument(documentType) {
  return apiRequest(`/api/v1/public/legal-documents/${encodeURIComponent(documentType)}`)
}

export function getAvailableCompanies(companyType) {
  return apiRequest(`/api/v1/reference/companies?type=${encodeURIComponent(companyType)}`)
}

export function getProfile() {
  return apiRequest('/api/v1/profile')
}

export function updateProfile(payload) {
  return apiRequest('/api/v1/profile', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function changePassword(payload) {
  return apiRequest('/api/v1/profile/password', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function uploadCompanyLogo(_email, file) {
  const formData = new FormData()
  formData.append('file', file)

  return apiRequest('/api/v1/profile/company/logo', {
    method: 'PUT',
    body: formData,
  })
}

export function deleteCompanyLogo() {
  return apiRequest('/api/v1/profile/company/logo', {
    method: 'DELETE',
  })
}

export function deleteProfile() {
  return apiRequest('/api/v1/profile', {
    method: 'DELETE',
  })
}

export function deleteCompanyProfile() {
  return apiRequest('/api/v1/profile/company', {
    method: 'DELETE',
  })
}

export function getTicketSummary() {
  return apiRequest('/api/v1/tickets/summary')
}

export function getTickets(_email, status) {
  const searchParams = new URLSearchParams()

  if (Array.isArray(status) && status.length > 0) {
    searchParams.set('status', status.join(','))
  } else if (status) {
    searchParams.set('status', status)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/tickets${queryString ? `?${queryString}` : ''}`)
}

export function getTicketById(ticketId) {
  return apiRequest(`/api/v1/tickets/${ticketId}`)
}

export function createTicket(payload) {
  return createMultipartRequest('/api/v1/tickets', payload)
}

export function getTicketMessages(ticketId) {
  return apiRequest(`/api/v1/tickets/${ticketId}/messages`)
}

export function addTicketMessage(ticketId, payload) {
  return createMultipartRequest(`/api/v1/tickets/${ticketId}/messages`, payload)
}

export function updateTicketTitle(ticketId, payload) {
  return apiRequest(`/api/v1/tickets/${ticketId}/title`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

function createMultipartRequest(path, payload) {
  const { files = [], ...data } = payload || {}

  if (!Array.isArray(files) || files.length === 0) {
    return apiRequest(path, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  const formData = new FormData()
  formData.append('payload', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  files.forEach((file) => formData.append('files', file))

  return apiRequest(path, {
    method: 'POST',
    body: formData,
  })
}

export function getTicketAttachmentDownloadUrl(ticketId, attachmentId) {
  const tenantHost =
    typeof window !== 'undefined' && window.location?.host ? window.location.host : ''
  const searchParams = new URLSearchParams()

  if (tenantHost) {
    searchParams.set('tenantHost', tenantHost)
  }

  const queryString = searchParams.toString()
  return `${API_BASE_URL}/api/v1/public/tickets/${ticketId}/attachments/${attachmentId}${
    queryString ? `?${queryString}` : ''
  }`
}

export function closeTicket(ticketId, payload) {
  return apiRequest(`/api/v1/tickets/${ticketId}/close`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteTickets(payload) {
  return apiRequest('/api/v1/tickets/delete', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getTicketTransferCandidates(ticketId) {
  return apiRequest(`/api/v1/tickets/${ticketId}/transfer-candidates`)
}

export function requestTicketTransfer(ticketId, payload) {
  return apiRequest(`/api/v1/tickets/${ticketId}/transfer`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getPersonalReport() {
  return apiRequest('/api/v1/reports/personal')
}

export function getCalendarObligations() {
  return apiRequest('/api/v1/calendar/obligations')
}

export function getCalendarLinkedCompanies() {
  return apiRequest('/api/v1/calendar/companies')
}

export function searchCalendarTickets(_email, query = '', offset = 0, limit = 20) {
  const searchParams = new URLSearchParams()
  searchParams.set('query', query)
  searchParams.set('offset', String(offset))
  searchParams.set('limit', String(limit))

  return apiRequest(`/api/v1/calendar/tickets/search?${searchParams.toString()}`)
}

export function createCalendarObligation(payload) {
  return apiRequest('/api/v1/calendar/obligations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCalendarObligation(obligationId, payload) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateCalendarObligationLinkedTickets(obligationId, payload) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}/linked-tickets`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function moveCalendarObligationCompany(obligationId, payload) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}/linked-company`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function completeCalendarObligation(obligationId) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}/complete`, {
    method: 'POST',
  })
}

export function deleteCalendarObligation(obligationId) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}`, {
    method: 'DELETE',
  })
}

export function getSectors() {
  return apiRequest('/api/v1/sectors')
}

export function searchCompanyPartnershipTargets(_email, query) {
  const searchParams = new URLSearchParams()
  searchParams.set('query', query)
  return apiRequest(`/api/v1/company-partnerships/search?${searchParams.toString()}`)
}

export function getMyCompanyPartnerships() {
  return apiRequest('/api/v1/company-partnerships/mine')
}

export function createCompanyPartnership(payload) {
  return apiRequest('/api/v1/company-partnerships', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createClientCompany(payload) {
  return apiRequest('/api/v1/client-companies', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function lookupClientCompany(companyDocument) {
  const searchParams = new URLSearchParams({
    companyDocument,
  })
  return apiRequest(`/api/v1/client-companies/lookup?${searchParams.toString()}`)
}

export function linkExistingClientCompany(payload) {
  return apiRequest('/api/v1/client-companies/link-existing', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function acceptCompanyPartnership(partnershipId, email) {
  return apiRequest(`/api/v1/company-partnerships/${partnershipId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function declineCompanyPartnership(partnershipId, email) {
  return apiRequest(`/api/v1/company-partnerships/${partnershipId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function unlinkCompanyPartnership(partnershipId) {
  return apiRequest(`/api/v1/company-partnerships/${partnershipId}`, {
    method: 'DELETE',
  })
}

export function getCompanyPartnershipTicketTargets() {
  return apiRequest('/api/v1/company-partnerships/ticket-targets')
}

export function createSector(payload) {
  return apiRequest('/api/v1/sectors', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getTeamMembers() {
  return apiRequest('/api/v1/team/members')
}

export function createTeamInvite(payload) {
  return apiRequest('/api/v1/team/invites', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createCompanyInvite(payload) {
  return apiRequest('/api/v1/team/company-invites', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteTeamSector(sectorId) {
  return apiRequest(`/api/v1/team/sectors/${sectorId}`, {
    method: 'DELETE',
  })
}

export function getReceivedTeamInvites() {
  return apiRequest('/api/v1/team/invites/received')
}

export function getSentTeamInvites() {
  return apiRequest('/api/v1/team/invites/sent')
}

export function acceptTeamInvite(inviteId, email) {
  return apiRequest(`/api/v1/team/invites/${inviteId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function declineTeamInvite(inviteId, email) {
  return apiRequest(`/api/v1/team/invites/${inviteId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function deleteTeamNotification(inviteId) {
  return apiRequest(`/api/v1/team/invites/${inviteId}/notification`, {
    method: 'DELETE',
  })
}

export function getTicketAssignmentNotifications() {
  return apiRequest('/api/v1/notifications/ticket-assignments')
}

export function deleteTicketAssignmentNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/ticket-assignments/${notificationId}`, {
    method: 'DELETE',
  })
}

export function getTicketTransferNotifications() {
  return apiRequest('/api/v1/notifications/ticket-transfers')
}

export function getTicketClosureNotifications() {
  return apiRequest('/api/v1/notifications/ticket-closures')
}

export function getTicketReplyNotifications() {
  return apiRequest('/api/v1/notifications/ticket-replies')
}

export function getTeamMembershipNotifications() {
  return apiRequest('/api/v1/notifications/team-memberships')
}

export function getCalendarReminderNotifications() {
  return apiRequest('/api/v1/notifications/calendar-reminders')
}

export function getCompanyPartnershipNotifications() {
  return apiRequest('/api/v1/notifications/company-partnerships')
}

export function getCompanyAccessRequestNotifications() {
  return apiRequest('/api/v1/notifications/company-access-requests')
}

export function getCompanyInviteNotifications() {
  return apiRequest('/api/v1/notifications/company-invites')
}

export function acceptTicketTransferNotification(notificationId, email) {
  return apiRequest(`/api/v1/notifications/ticket-transfers/${notificationId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function declineTicketTransferNotification(notificationId, email) {
  return apiRequest(`/api/v1/notifications/ticket-transfers/${notificationId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function acceptCompanyAccessRequestNotification(requestId, email) {
  return apiRequest(`/api/v1/notifications/company-access-requests/${requestId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function declineCompanyAccessRequestNotification(requestId, email) {
  return apiRequest(`/api/v1/notifications/company-access-requests/${requestId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function acceptCompanyInviteNotification(requestId, email) {
  return apiRequest(`/api/v1/notifications/company-invites/${requestId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function declineCompanyInviteNotification(requestId, email) {
  return apiRequest(`/api/v1/notifications/company-invites/${requestId}/decline`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function deleteTicketTransferNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/ticket-transfers/${notificationId}`, {
    method: 'DELETE',
  })
}

export function deleteTicketClosureNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/ticket-closures/${notificationId}`, {
    method: 'DELETE',
  })
}

export function deleteTicketReplyNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/ticket-replies/${notificationId}`, {
    method: 'DELETE',
  })
}

export function deleteTeamMembershipNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/team-memberships/${notificationId}`, {
    method: 'DELETE',
  })
}

export function deleteCalendarReminderNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/calendar-reminders/${notificationId}`, {
    method: 'DELETE',
  })
}

export function deleteCompanyPartnershipNotification(notificationId) {
  return apiRequest(`/api/v1/notifications/company-partnerships/${notificationId}`, {
    method: 'DELETE',
  })
}

export function updateTeamMemberSectors(userId, payload) {
  return apiRequest(`/api/v1/team/members/${userId}/sectors`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function removeTeamMemberFromCompany(userId) {
  return apiRequest(`/api/v1/team/members/${userId}`, {
    method: 'DELETE',
  })
}

export function startWhatsappSession(payload) {
  return apiRequest('/api/v1/whatsapp/session/start', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getWhatsappSessionStatus() {
  return apiRequest('/api/v1/whatsapp/session/status')
}

export function getWhatsappQrCodeViewUrl() {
  return `${API_BASE_URL}/api/v1/whatsapp/session/qrcode/view`
}
