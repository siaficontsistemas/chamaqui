const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:4200').replace(
  /\/$/,
  ''
)

export function resolveRealtimeNotificationsWebSocketUrl(email) {
  if (!email) {
    return ''
  }

  const apiUrl = new URL(API_BASE_URL)
  apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  apiUrl.pathname = '/api/v1/realtime/ticket-notifications'
  apiUrl.searchParams.set('email', email)

  if (typeof window !== 'undefined' && window.location?.host) {
    apiUrl.searchParams.set('tenantHost', window.location.host)
  }

  return apiUrl.toString()
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
  const isFormData = options.body instanceof FormData
  const tenantHost =
    typeof window !== 'undefined' && window.location?.host ? window.location.host : ''
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: options.credentials || 'include',
    headers: {
      ...(tenantHost ? { 'X-Tenant-Host': tenantHost } : {}),
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    const errorMessage = await extractErrorMessage(response)
    throw new Error(errorMessage)
  }

  if (response.status === 204) {
    return null
  }

  const responseText = await response.text()

  if (!responseText.trim()) {
    return null
  }

  return JSON.parse(responseText)
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
  }

  return labelsByField[fieldName] || fieldName
}

export function loginUser(credentials) {
  return apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
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

export function getAvailableCompanies(companyType) {
  return apiRequest(`/api/v1/reference/companies?type=${encodeURIComponent(companyType)}`)
}

export function getProfile(email) {
  return apiRequest(`/api/v1/profile?email=${encodeURIComponent(email)}`)
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

export function uploadCompanyLogo(email, file) {
  const formData = new FormData()
  formData.append('file', file)

  return apiRequest(`/api/v1/profile/company/logo?email=${encodeURIComponent(email)}`, {
    method: 'PUT',
    body: formData,
  })
}

export function deleteCompanyLogo(email) {
  return apiRequest(`/api/v1/profile/company/logo?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function deleteProfile(email) {
  return apiRequest(`/api/v1/profile?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function deleteCompanyProfile(email) {
  return apiRequest(`/api/v1/profile/company?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function getTicketSummary(email) {
  return apiRequest(`/api/v1/tickets/summary?email=${encodeURIComponent(email)}`)
}

export function getTickets(email, status) {
  const searchParams = new URLSearchParams()
  searchParams.set('email', email)

  if (Array.isArray(status) && status.length > 0) {
    searchParams.set('status', status.join(','))
  } else if (status) {
    searchParams.set('status', status)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/tickets${queryString ? `?${queryString}` : ''}`)
}

export function getTicketById(ticketId, email) {
  return apiRequest(`/api/v1/tickets/${ticketId}?email=${encodeURIComponent(email)}`)
}

export function createTicket(payload) {
  return createMultipartRequest('/api/v1/tickets', payload)
}

export function getTicketMessages(ticketId, email) {
  return apiRequest(
    `/api/v1/tickets/${ticketId}/messages?email=${encodeURIComponent(email)}`
  )
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

export function getTicketAttachmentDownloadUrl(ticketId, attachmentId, email) {
  return `${API_BASE_URL}/api/v1/tickets/${ticketId}/attachments/${attachmentId}?email=${encodeURIComponent(email)}`
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

export function getTicketTransferCandidates(ticketId, email) {
  return apiRequest(
    `/api/v1/tickets/${ticketId}/transfer-candidates?email=${encodeURIComponent(email)}`
  )
}

export function requestTicketTransfer(ticketId, payload) {
  return apiRequest(`/api/v1/tickets/${ticketId}/transfer`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getPersonalReport(email) {
  return apiRequest(`/api/v1/reports/personal?email=${encodeURIComponent(email)}`)
}

export function getCalendarObligations(email) {
  return apiRequest(`/api/v1/calendar/obligations?email=${encodeURIComponent(email)}`)
}

export function getCalendarLinkedCompanies(email) {
  return apiRequest(`/api/v1/calendar/companies?email=${encodeURIComponent(email)}`)
}

export function searchCalendarTickets(email, query = '', offset = 0, limit = 20) {
  const searchParams = new URLSearchParams()
  searchParams.set('email', email)
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

export function completeCalendarObligation(obligationId, email) {
  return apiRequest(
    `/api/v1/calendar/obligations/${obligationId}/complete?email=${encodeURIComponent(email)}`,
    {
      method: 'POST',
    }
  )
}

export function deleteCalendarObligation(obligationId, email) {
  return apiRequest(`/api/v1/calendar/obligations/${obligationId}?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function getSectors(email) {
  const searchParams = new URLSearchParams()

  if (email) {
    searchParams.set('email', email)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/sectors${queryString ? `?${queryString}` : ''}`)
}

export function searchCompanyPartnershipTargets(email, query) {
  const searchParams = new URLSearchParams()
  searchParams.set('email', email)
  searchParams.set('query', query)
  return apiRequest(`/api/v1/company-partnerships/search?${searchParams.toString()}`)
}

export function getMyCompanyPartnerships(email) {
  return apiRequest(`/api/v1/company-partnerships/mine?email=${encodeURIComponent(email)}`)
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

export function lookupClientCompany(companyDocument, createdByEmail) {
  const searchParams = new URLSearchParams({
    companyDocument,
    createdByEmail,
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

export function unlinkCompanyPartnership(partnershipId, email) {
  return apiRequest(`/api/v1/company-partnerships/${partnershipId}?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function getCompanyPartnershipTicketTargets(email) {
  return apiRequest(`/api/v1/company-partnerships/ticket-targets?email=${encodeURIComponent(email)}`)
}

export function createSector(payload) {
  return apiRequest('/api/v1/sectors', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getTeamMembers(email) {
  const searchParams = new URLSearchParams()

  if (email) {
    searchParams.set('email', email)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/team/members${queryString ? `?${queryString}` : ''}`)
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

export function deleteTeamSector(sectorId, email) {
  return apiRequest(`/api/v1/team/sectors/${sectorId}?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function getReceivedTeamInvites(email) {
  return apiRequest(`/api/v1/team/invites/received?email=${encodeURIComponent(email)}`)
}

export function getSentTeamInvites(email) {
  return apiRequest(`/api/v1/team/invites/sent?email=${encodeURIComponent(email)}`)
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

export function deleteTeamNotification(inviteId, email) {
  return apiRequest(`/api/v1/team/invites/${inviteId}/notification?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function getTicketAssignmentNotifications(email) {
  return apiRequest(`/api/v1/notifications/ticket-assignments?email=${encodeURIComponent(email)}`)
}

export function deleteTicketAssignmentNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/ticket-assignments/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function getTicketTransferNotifications(email) {
  return apiRequest(`/api/v1/notifications/ticket-transfers?email=${encodeURIComponent(email)}`)
}

export function getTicketClosureNotifications(email) {
  return apiRequest(`/api/v1/notifications/ticket-closures?email=${encodeURIComponent(email)}`)
}

export function getTicketReplyNotifications(email) {
  return apiRequest(`/api/v1/notifications/ticket-replies?email=${encodeURIComponent(email)}`)
}

export function getTeamMembershipNotifications(email) {
  return apiRequest(`/api/v1/notifications/team-memberships?email=${encodeURIComponent(email)}`)
}

export function getCalendarReminderNotifications(email) {
  return apiRequest(`/api/v1/notifications/calendar-reminders?email=${encodeURIComponent(email)}`)
}

export function getCompanyPartnershipNotifications(email) {
  return apiRequest(`/api/v1/notifications/company-partnerships?email=${encodeURIComponent(email)}`)
}

export function getCompanyAccessRequestNotifications(email) {
  return apiRequest(`/api/v1/notifications/company-access-requests?email=${encodeURIComponent(email)}`)
}

export function getCompanyInviteNotifications(email) {
  return apiRequest(`/api/v1/notifications/company-invites?email=${encodeURIComponent(email)}`)
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

export function deleteTicketTransferNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/ticket-transfers/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function deleteTicketClosureNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/ticket-closures/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function deleteTicketReplyNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/ticket-replies/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function deleteTeamMembershipNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/team-memberships/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function deleteCalendarReminderNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/calendar-reminders/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function deleteCompanyPartnershipNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/company-partnerships/${notificationId}?email=${encodeURIComponent(email)}`,
    {
      method: 'DELETE',
    }
  )
}

export function updateTeamMemberSectors(userId, payload) {
  return apiRequest(`/api/v1/team/members/${userId}/sectors`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function removeTeamMemberFromCompany(userId, email) {
  return apiRequest(`/api/v1/team/members/${userId}?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}

export function startWhatsappSession(payload) {
  return apiRequest('/api/v1/whatsapp/session/start', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getWhatsappSessionStatus(adminEmail) {
  return apiRequest(`/api/v1/whatsapp/session/status?adminEmail=${encodeURIComponent(adminEmail)}`)
}

export function getWhatsappQrCodeViewUrl(adminEmail) {
  return `${API_BASE_URL}/api/v1/whatsapp/session/qrcode/view?adminEmail=${encodeURIComponent(adminEmail)}`
}
