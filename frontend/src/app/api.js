const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:4200').replace(
  /\/$/,
  ''
)

async function apiRequest(path, options = {}) {
  const isFormData = options.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
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

  return response.json()
}

async function extractErrorMessage(response) {
  try {
    const data = await response.json()

    if (typeof data === 'string' && data.trim()) {
      return data
    }

    if (data?.message) {
      return data.message
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

export function loginUser(credentials) {
  return apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function registerUser(payload) {
  return apiRequest('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getProfile(email) {
  return apiRequest(`/api/v1/profile?email=${encodeURIComponent(email)}`)
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

export function getSectors(email) {
  const searchParams = new URLSearchParams()

  if (email) {
    searchParams.set('email', email)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/sectors${queryString ? `?${queryString}` : ''}`)
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

export function getTeamMembershipNotifications(email) {
  return apiRequest(`/api/v1/notifications/team-memberships?email=${encodeURIComponent(email)}`)
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

export function deleteTicketTransferNotification(notificationId, email) {
  return apiRequest(
    `/api/v1/notifications/ticket-transfers/${notificationId}?email=${encodeURIComponent(email)}`,
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

export function leaveTeamSector(sectorId, email) {
  return apiRequest(`/api/v1/team/sectors/${sectorId}/leave?email=${encodeURIComponent(email)}`, {
    method: 'DELETE',
  })
}
