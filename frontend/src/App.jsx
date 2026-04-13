import { useEffect, useMemo, useState } from 'react'
import {
  acceptTeamInvite,
  createTicket,
  createSector,
  createTeamInvite,
  declineTeamInvite,
  getProfile,
  getReceivedTeamInvites,
  getSectors,
  getSentTeamInvites,
  getTeamMembers,
  getTicketSummary,
  updateTeamMemberSectors,
} from './app/api'
import {
  buildNavigationGroups,
  getVisibleSectors,
  isTeamRole,
} from './app/dashboardData'
import AllTickets from './app/pages/AllTickets/AllTickets'
import ClosedTickets from './app/pages/ClosedTickets/ClosedTickets'
import CreateSector from './app/pages/CreateSector/CreateSector'
import Home from './app/pages/Home/Home'
import Login from './app/pages/Login/Login'
import MyData from './app/pages/MyData/MyData'
import NewTicket from './app/pages/NewTicket/NewTicket'
import OpenTickets from './app/pages/OpenTickets/OpenTickets'
import Reports from './app/pages/Reports/Reports'
import Register from './app/pages/Register/Register'
import Sector from './app/pages/Sector/Sector'
import Team from './app/pages/Team/Team'

const dashboardPageComponents = {
  tickets: Home,
  reports: Reports,
  all: AllTickets,
  open: OpenTickets,
  closed: ClosedTickets,
  createSector: CreateSector,
  newTicket: NewTicket,
  myData: MyData,
  team: Team,
}

const SESSION_STORAGE_KEY = 'helpdesk.session'

function normalizeSector(sector) {
  return {
    id: sector.id,
    name: sector.name,
    description: sector.description || 'Setor criado pelo administrador para organização da equipe.',
    slug: sector.slug,
    active: sector.active,
    memberCount: sector.memberCount ?? 0,
  }
}

function normalizeTeamMember(member) {
  return {
    id: member.userId,
    name: member.fullName,
    email: member.email,
    role: member.role,
    status: member.status,
    sectors: Array.isArray(member.sectorIds) ? member.sectorIds : [],
  }
}

function normalizeInvite(invite) {
  return {
    id: invite.id,
    invitedName: invite.invitedName,
    email: invite.email,
    status: invite.status,
    invitedByEmail: invite.invitedByEmail,
    invitedByName: invite.invitedByName,
    expiresAt: invite.expiresAt,
    acceptedAt: invite.acceptedAt,
    updatedAt: invite.updatedAt,
    sectorIds: Array.isArray(invite.sectorIds) ? invite.sectorIds : [],
    sectorNames: Array.isArray(invite.sectorNames) ? invite.sectorNames : [],
  }
}

async function fetchDashboardBundle(email) {
  const [nextProfile, nextSummary, nextSectors, nextMembers, nextReceivedInvites, nextSentInvites] =
    await Promise.all([
      getProfile(email),
      getTicketSummary(),
      getSectors(),
      getTeamMembers(),
      getReceivedTeamInvites(email),
      getSentTeamInvites(email),
    ])

  return {
    profile: nextProfile,
    ticketSummary: nextSummary,
    sectors: Array.isArray(nextSectors) ? nextSectors.map(normalizeSector) : [],
    teamMembers: Array.isArray(nextMembers) ? nextMembers.map(normalizeTeamMember) : [],
    receivedInvites: Array.isArray(nextReceivedInvites)
      ? nextReceivedInvites.map(normalizeInvite)
      : [],
    sentInvites: Array.isArray(nextSentInvites) ? nextSentInvites.map(normalizeInvite) : [],
  }
}

function getPrimaryRole(roles = []) {
  if (roles.includes('admin')) {
    return 'admin'
  }

  if (roles.includes('employee')) {
    return 'employee'
  }

  if (roles.includes('user')) {
    return 'user'
  }

  return 'user'
}

function loadStoredSession() {
  try {
    const rawSession = window.localStorage.getItem(SESSION_STORAGE_KEY)

    if (!rawSession) {
      return null
    }

    return JSON.parse(rawSession)
  } catch {
    return null
  }
}

function App() {
  const [currentPage, setCurrentPage] = useState(() => (loadStoredSession() ? 'tickets' : 'login'))
  const [authUser, setAuthUser] = useState(loadStoredSession)
  const [currentUserRole, setCurrentUserRole] = useState(() =>
    getPrimaryRole(loadStoredSession()?.roles)
  )
  const [createdSectors, setCreatedSectors] = useState([])
  const [teamMembers, setTeamMembers] = useState([])
  const [receivedInvites, setReceivedInvites] = useState([])
  const [sentInvites, setSentInvites] = useState([])
  const [profile, setProfile] = useState(() => loadStoredSession())
  const [isProfileLoading, setIsProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [ticketSummary, setTicketSummary] = useState(null)
  const [isTicketSummaryLoading, setIsTicketSummaryLoading] = useState(false)
  const [isTeamDataLoading, setIsTeamDataLoading] = useState(false)
  const [teamDataError, setTeamDataError] = useState('')
  const currentUserEmail = profile?.email || authUser?.email || ''
  const currentMemberId =
    teamMembers.find((member) => member.email?.toLowerCase() === currentUserEmail.toLowerCase())?.id ?? null
  const canAccessTeamPage = isTeamRole(currentUserRole)
  const navigationGroups = useMemo(
    () =>
      buildNavigationGroups({
        userRole: currentUserRole,
        sectors: createdSectors,
        teamMembers,
        currentMemberId,
      }),
    [createdSectors, currentMemberId, currentUserRole, teamMembers]
  )
  const visibleSectors = useMemo(
    () => getVisibleSectors(currentUserRole, createdSectors, teamMembers, currentMemberId),
    [createdSectors, currentMemberId, currentUserRole, teamMembers]
  )
  const canAccessCreateSector = currentUserRole === 'admin'
  const sectorPage = visibleSectors.find((sector) => sector.id === currentPage)
  const notificationItems = useMemo(() => {
    const pendingReceived = receivedInvites
      .filter((invite) => invite.status === 'PENDING')
      .map((invite) => ({
        ...invite,
        type: 'received',
      }))

    const sentUpdates = sentInvites
      .filter((invite) => invite.status !== 'PENDING')
      .map((invite) => ({
        ...invite,
        type: 'sent',
      }))

    return [...pendingReceived, ...sentUpdates].sort(
      (firstInvite, secondInvite) =>
        new Date(secondInvite.updatedAt || secondInvite.acceptedAt || secondInvite.expiresAt).getTime() -
        new Date(firstInvite.updatedAt || firstInvite.acceptedAt || firstInvite.expiresAt).getTime()
    )
  }, [receivedInvites, sentInvites])
  const isSectorRoute = currentPage.startsWith('sector-')
  const safeDashboardPage =
    (currentPage === 'team' && !canAccessTeamPage) ||
    (currentPage === 'createSector' && !canAccessCreateSector) ||
    (!sectorPage && !(currentPage in dashboardPageComponents) && isSectorRoute)
      ? 'tickets'
      : currentPage
  const headerProps = {
    isTeamRole: canAccessTeamPage,
    isTicketSummaryLoading: isTicketSummaryLoading || isTeamDataLoading,
    isNotificationLoading: isTeamDataLoading,
    notifications: notificationItems,
    onAcceptInvite: handleAcceptInvite,
    onDeclineInvite: handleDeclineInvite,
    onNavigateLogin: handleNavigateLogin,
    onSectionChange: setCurrentPage,
    roleLabel:
      currentUserRole === 'admin'
        ? 'Administrador'
        : currentUserRole === 'employee'
          ? 'Funcionário'
          : 'Usuário',
    ticketSummary,
  }

  function applyDashboardBundle(bundle) {
    setProfile(bundle.profile)
    setAuthUser(bundle.profile)
    setCurrentUserRole(getPrimaryRole(bundle.profile.roles))
    setTicketSummary(bundle.ticketSummary)
    setCreatedSectors(bundle.sectors)
    setTeamMembers(bundle.teamMembers)
    setReceivedInvites(bundle.receivedInvites)
    setSentInvites(bundle.sentInvites)
  }

  useEffect(() => {
    if (authUser) {
      window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(authUser))
      return
    }

    window.localStorage.removeItem(SESSION_STORAGE_KEY)
  }, [authUser])

  useEffect(() => {
    if (!authUser?.email) {
      setProfile(null)
      setProfileError('')
      setTicketSummary(null)
      setCreatedSectors([])
      setTeamMembers([])
      setReceivedInvites([])
      setSentInvites([])
      setTeamDataError('')
      setIsProfileLoading(false)
      setIsTicketSummaryLoading(false)
      setIsTeamDataLoading(false)
      return
    }

    let isCancelled = false

    async function loadDashboardData() {
      setIsProfileLoading(true)
      setIsTicketSummaryLoading(true)
      setIsTeamDataLoading(true)
      setProfileError('')
      setTeamDataError('')

      try {
        const bundle = await fetchDashboardBundle(authUser.email)

        if (isCancelled) {
          return
        }

        applyDashboardBundle(bundle)
      } catch (error) {
        if (isCancelled) {
          return
        }

        setProfileError(error.message)
        setTeamDataError(error.message)
      } finally {
        if (!isCancelled) {
          setIsProfileLoading(false)
          setIsTicketSummaryLoading(false)
          setIsTeamDataLoading(false)
        }
      }
    }

    loadDashboardData()

    return () => {
      isCancelled = true
    }
  }, [authUser?.email])

  function handleAuthenticatedUser(user) {
    setAuthUser(user)
    setProfile(user)
    setProfileError('')
    setCurrentUserRole(getPrimaryRole(user.roles))
    setCurrentPage('tickets')
  }

  function handleNavigateLogin() {
    setAuthUser(null)
    setProfile(null)
    setProfileError('')
    setTicketSummary(null)
    setCreatedSectors([])
    setTeamMembers([])
    setReceivedInvites([])
    setSentInvites([])
    setTeamDataError('')
    setCurrentUserRole('user')
    setCurrentPage('login')
  }

  async function refreshDashboardData(email = currentUserEmail) {
    if (!email) {
      return
    }

    setIsTeamDataLoading(true)
    setTeamDataError('')

    try {
      const bundle = await fetchDashboardBundle(email)
      applyDashboardBundle(bundle)
    } catch (error) {
      setTeamDataError(error.message)
      throw error
    } finally {
      setIsTeamDataLoading(false)
    }
  }

  async function handleCreateSector({ name, description }) {
    const trimmedName = name.trim()

    if (!trimmedName || !currentUserEmail) {
      return
    }

    const createdSector = await createSector({
      name: trimmedName,
      description: description.trim(),
      createdByEmail: currentUserEmail,
    })

    await refreshDashboardData(currentUserEmail)
    setCurrentPage(createdSector.id)
  }

  async function handleCreateTicket({ title, description, priorityCode, sectorId }) {
    const trimmedTitle = title.trim()
    const trimmedDescription = description.trim()

    if (!trimmedTitle || !trimmedDescription || !priorityCode || !sectorId || !currentUserEmail) {
      return
    }

    const createdTicket = await createTicket({
      title: trimmedTitle,
      description: trimmedDescription,
      priorityCode,
      requesterEmail: currentUserEmail,
      sectorId,
    })

    await refreshDashboardData(currentUserEmail)
    setCurrentPage('all')
    return createdTicket
  }

  async function handleUpdateMemberSectors(memberId, nextSectors) {
    if (!currentUserEmail) {
      return
    }

    const updatedMembers = await updateTeamMemberSectors(memberId, {
      assignedByEmail: currentUserEmail,
      sectorIds: nextSectors,
    })

    setTeamMembers(Array.isArray(updatedMembers) ? updatedMembers.map(normalizeTeamMember) : [])
  }

  async function handleInviteMember({ email, name, sectors }) {
    const trimmedName = name.trim()
    const trimmedEmail = email.trim()

    if (!trimmedName || !trimmedEmail || sectors.length === 0 || !currentUserEmail) {
      return
    }

    await createTeamInvite({
      email: trimmedEmail,
      invitedName: trimmedName,
      invitedByEmail: currentUserEmail,
      sectorIds: sectors,
    })
    await refreshDashboardData(currentUserEmail)
  }

  async function handleAcceptInvite(inviteId) {
    if (!currentUserEmail) {
      return
    }

    await acceptTeamInvite(inviteId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeclineInvite(inviteId) {
    if (!currentUserEmail) {
      return
    }

    await declineTeamInvite(inviteId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  if (sectorPage) {
    return (
      <Sector
        headerProps={headerProps}
        navigationGroups={navigationGroups}
        onNavigatePage={setCurrentPage}
        sector={sectorPage}
        teamMembers={teamMembers}
        userRole={currentUserRole}
      />
    )
  }

  if (authUser && safeDashboardPage in dashboardPageComponents) {
    const CurrentDashboardPage = dashboardPageComponents[safeDashboardPage]

    return (
      <CurrentDashboardPage
        currentUser={profile || authUser}
        headerProps={headerProps}
        isProfileLoading={isProfileLoading}
        isTeamDataLoading={isTeamDataLoading}
        navigationGroups={navigationGroups}
        onInviteMember={handleInviteMember}
        onNavigatePage={setCurrentPage}
        onCreateTicket={handleCreateTicket}
        onCreateSector={handleCreateSector}
        onAcceptInvite={handleAcceptInvite}
        onDeclineInvite={handleDeclineInvite}
        onUpdateMemberSectors={handleUpdateMemberSectors}
        profileError={profileError}
        receivedInvites={receivedInvites}
        sectors={visibleSectors}
        sentInvites={sentInvites}
        teamDataError={teamDataError}
        teamMembers={teamMembers}
        userRole={currentUserRole}
      />
    )
  }

  if (currentPage === 'register') {
    return (
      <Register
        onNavigateHome={handleAuthenticatedUser}
        onNavigateLogin={() => setCurrentPage('login')}
      />
    )
  }

  return (
    <Login
      onNavigateHome={handleAuthenticatedUser}
      onNavigateRegister={() => setCurrentPage('register')}
    />
  )
}

export default App
