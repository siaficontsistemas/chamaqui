const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:4200').replace(
  /\/$/,
  ''
)

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
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

export function getTicketSummary() {
  return apiRequest('/api/v1/tickets/summary')
}

export function getTickets(status) {
  const searchParams = new URLSearchParams()

  if (status) {
    searchParams.set('status', status)
  }

  const queryString = searchParams.toString()

  return apiRequest(`/api/v1/tickets${queryString ? `?${queryString}` : ''}`)
}

export function createTicket(payload) {
  return apiRequest('/api/v1/tickets', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getPersonalReport(email) {
  return apiRequest(`/api/v1/reports/personal?email=${encodeURIComponent(email)}`)
}

export function getSectors() {
  return apiRequest('/api/v1/sectors')
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

export function updateTeamMemberSectors(userId, payload) {
  return apiRequest(`/api/v1/team/members/${userId}/sectors`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
