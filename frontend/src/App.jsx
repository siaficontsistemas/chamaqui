import { useEffect, useMemo, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  acceptCompanyAccessRequestNotification,
  acceptCompanyPartnership,
  acceptTicketTransferNotification,
  acceptTeamInvite,
  closeTicket,
  createCompanyPartnership,
  createTicket,
  createSector,
  createTeamInvite,
  declineCompanyAccessRequestNotification,
  declineCompanyPartnership,
  deleteTeamSector,
  deleteCompanyPartnershipNotification,
  deleteTicketAssignmentNotification,
  deleteCalendarReminderNotification,
  deleteTeamMembershipNotification,
  deleteTicketTransferNotification,
  deleteTeamNotification,
  deleteCompanyProfile,
  deleteProfile,
  declineTicketTransferNotification,
  declineTeamInvite,
  getCompanyPartnershipTicketTargets,
  getProfile,
  getMyCompanyPartnerships,
  getCalendarReminderNotifications,
  getCompanyAccessRequestNotifications,
  getCompanyPartnershipNotifications,
  getTicketAssignmentNotifications,
  getTeamMembershipNotifications,
  getTicketTransferNotifications,
  getReceivedTeamInvites,
  getSectors,
  getSentTeamInvites,
  getTeamMembers,
  getTicketById,
  getTicketSummary,
  getTicketTransferCandidates,
  removeTeamMemberFromCompany,
  requestTicketTransfer,
  searchCompanyPartnershipTargets,
  unlinkCompanyPartnership,
  updateTeamMemberSectors,
} from './app/api'
import { buildNavigationGroups, getVisibleSectors } from './app/dashboardData'
import Header from './app/components/header/Header'
import Sidebar from './app/components/sidebar/Sidebar'
import {
  PUBLIC_ROUTE_PATHS,
  SECTION_ROUTE_PATHS,
  getSectionIdFromPathname,
  getSectionPath,
  getTicketPath,
} from './app/routes'
import AllTickets from './app/pages/AllTickets/AllTickets'
import Calendar from './app/pages/Calendar/Calendar'
import ClosedTickets from './app/pages/ClosedTickets/ClosedTickets'
import CreateSector from './app/pages/CreateSector/CreateSector'
import Home from './app/pages/Home/Home'
import Login from './app/pages/Login/Login'
import MyData from './app/pages/MyData/MyData'
import NewTicket from './app/pages/NewTicket/NewTicket'
import OpenTickets from './app/pages/OpenTickets/OpenTickets'
import Register from './app/pages/Register/Register'
import Reports from './app/pages/Reports/Reports'
import Sector from './app/pages/Sector/Sector'
import Team from './app/pages/Team/Team'
import TicketConversation from './app/pages/TicketConversation/TicketConversation'

const dashboardPageComponents = {
  tickets: Home,
  calendar: Calendar,
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
    companyOwnerId: sector.companyOwnerId,
    companyName: sector.companyName || 'Empresa não informada',
    companyDocument: sector.companyDocument || '',
    createdByEmail: sector.createdByEmail || '',
    memberCount: sector.memberCount ?? 0,
  }
}

function normalizeCurrentUser(user) {
  if (!user) {
    return user
  }

  return {
    ...user,
    companyType: user.companyType || '',
    roles: Array.isArray(user.roles) ? user.roles : [],
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

function normalizeCompanyPartnership(partnership) {
  return {
    id: partnership.id,
    status: partnership.status,
    requesterCompanyId: partnership.requesterCompanyId,
    requesterCompanyName: partnership.requesterCompanyName || 'Empresa não informada',
    requesterCompanyDocument: partnership.requesterCompanyDocument || '',
    targetCompanyId: partnership.targetCompanyId,
    targetCompanyName: partnership.targetCompanyName || 'Empresa não informada',
    targetCompanyDocument: partnership.targetCompanyDocument || '',
    createdAt: partnership.createdAt,
    respondedAt: partnership.respondedAt,
    canRespond: Boolean(partnership.canRespond),
    outgoing: Boolean(partnership.outgoing),
  }
}

function normalizeTicketNotification(notification) {
  return {
    id: notification.id,
    ticketId: notification.ticketId,
    ticketProtocol: notification.ticketProtocol,
    ticketTitle: notification.ticketTitle,
    requesterName: notification.requesterName,
    sectorName: notification.sectorName,
    status: notification.status,
    createdAt: notification.createdAt,
    type: 'ticket-assignment',
  }
}

function normalizeTicketTransferNotification(notification) {
  return {
    id: notification.id,
    ticketId: notification.ticketId,
    ticketProtocol: notification.ticketProtocol,
    ticketTitle: notification.ticketTitle,
    requesterName: notification.requesterName,
    sectorName: notification.sectorName,
    senderName: notification.senderName,
    recipientName: notification.recipientName,
    status: notification.status,
    createdAt: notification.createdAt,
    updatedAt: notification.updatedAt,
    respondedAt: notification.respondedAt,
    type: 'ticket-transfer',
  }
}

function normalizeTeamMembershipNotification(notification) {
  return {
    id: notification.id,
    removedByName: notification.removedByName,
    sectorName: notification.sectorName,
    companyName: notification.companyName,
    status: 'REMOVED',
    createdAt: notification.createdAt,
    type: 'team-membership-removed',
    removalType: notification.type,
  }
}

function normalizeCalendarReminderNotification(notification) {
  return {
    id: notification.id,
    obligationId: notification.obligationId,
    obligationTitle: notification.obligationTitle,
    obligationDescription: notification.obligationDescription,
    dueAt: notification.dueAt,
    reminderAt: notification.reminderAt,
    createdByName: notification.createdByName,
    companyName: notification.companyName,
    status: notification.status,
    createdAt: notification.createdAt,
    type: 'calendar-reminder',
  }
}

function normalizeCompanyPartnershipNotification(notification) {
  return {
    id: notification.id,
    partnershipId: notification.partnershipId,
    eventType: notification.eventType,
    actorName: notification.actorName || 'Administrador não informado',
    actorCompanyName: notification.actorCompanyName || 'Empresa não informada',
    requesterCompanyId: notification.requesterCompanyId,
    requesterCompanyName: notification.requesterCompanyName || 'Empresa não informada',
    targetCompanyId: notification.targetCompanyId,
    targetCompanyName: notification.targetCompanyName || 'Empresa não informada',
    status: notification.status || 'PENDING',
    canRespond: Boolean(notification.canRespond),
    createdAt: notification.createdAt,
    type: 'company-partnership',
  }
}

function normalizeCompanyAccessRequestNotification(notification) {
  return {
    id: notification.id,
    requesterUserId: notification.requesterUserId,
    requesterName: notification.requesterName || 'Usuário não informado',
    requesterEmail: notification.requesterEmail || '',
    requesterDocumentNumber: notification.requesterDocumentNumber || '',
    requestedRole: notification.requestedRole || 'user',
    companyName: notification.companyName || 'Empresa não informada',
    companyType: notification.companyType || '',
    status: notification.status || 'PENDING',
    createdAt: notification.createdAt,
    type: 'company-access-request',
  }
}

async function fetchDashboardBundle(email) {
  const [
    nextProfile,
    nextSummary,
    nextSectors,
    nextTicketTargets,
    nextMembers,
    nextReceivedInvites,
    nextSentInvites,
    nextCompanyPartnerships,
    nextTicketNotifications,
    nextTicketTransferNotifications,
    nextTeamMembershipNotifications,
    nextCalendarReminderNotifications,
    nextCompanyPartnershipNotifications,
    nextCompanyAccessRequestNotifications,
  ] = await Promise.all([
    getProfile(email),
    getTicketSummary(email),
    getSectors(email),
    getCompanyPartnershipTicketTargets(email),
    getTeamMembers(email),
    getReceivedTeamInvites(email),
    getSentTeamInvites(email),
    getMyCompanyPartnerships(email),
    getTicketAssignmentNotifications(email),
    getTicketTransferNotifications(email),
    getTeamMembershipNotifications(email),
    getCalendarReminderNotifications(email),
    getCompanyPartnershipNotifications(email),
    getCompanyAccessRequestNotifications(email),
  ])

  return {
    profile: normalizeCurrentUser(nextProfile),
    ticketSummary: nextSummary,
    sectors: Array.isArray(nextSectors) ? nextSectors.map(normalizeSector) : [],
    ticketTargets: Array.isArray(nextTicketTargets) ? nextTicketTargets.map(normalizeSector) : [],
    teamMembers: Array.isArray(nextMembers) ? nextMembers.map(normalizeTeamMember) : [],
    receivedInvites: Array.isArray(nextReceivedInvites)
      ? nextReceivedInvites.map(normalizeInvite)
      : [],
    sentInvites: Array.isArray(nextSentInvites) ? nextSentInvites.map(normalizeInvite) : [],
    companyPartnerships: Array.isArray(nextCompanyPartnerships)
      ? nextCompanyPartnerships.map(normalizeCompanyPartnership)
      : [],
    ticketNotifications: [
      ...(Array.isArray(nextTicketNotifications)
        ? nextTicketNotifications.map(normalizeTicketNotification)
        : []),
      ...(Array.isArray(nextTicketTransferNotifications)
        ? nextTicketTransferNotifications.map(normalizeTicketTransferNotification)
        : []),
      ...(Array.isArray(nextTeamMembershipNotifications)
        ? nextTeamMembershipNotifications.map(normalizeTeamMembershipNotification)
        : []),
      ...(Array.isArray(nextCalendarReminderNotifications)
        ? nextCalendarReminderNotifications.map(normalizeCalendarReminderNotification)
        : []),
      ...(Array.isArray(nextCompanyPartnershipNotifications)
        ? nextCompanyPartnershipNotifications.map(normalizeCompanyPartnershipNotification)
        : []),
      ...(Array.isArray(nextCompanyAccessRequestNotifications)
        ? nextCompanyAccessRequestNotifications.map(normalizeCompanyAccessRequestNotification)
        : []),
    ],
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

    return normalizeCurrentUser(JSON.parse(rawSession))
  } catch {
    return null
  }
}

function TicketConversationRoute({
  currentUser,
  headerProps,
  navigationGroups,
  onCloseTicket,
  onLoadTransferCandidates,
  onNavigatePage,
  onRequestTicketTransfer,
  selectedTicket,
  setSelectedTicket,
  userRole,
}) {
  const { ticketId } = useParams()
  const location = useLocation()
  const [isLoadingTicket, setIsLoadingTicket] = useState(false)
  const [ticketError, setTicketError] = useState('')

  useEffect(() => {
    if (!ticketId || !currentUser?.email) {
      setIsLoadingTicket(false)
      setTicketError('')
      return undefined
    }

    if (selectedTicket?.id === ticketId) {
      setIsLoadingTicket(false)
      setTicketError('')
      return undefined
    }

    let isCancelled = false

    async function loadTicket() {
      setIsLoadingTicket(true)
      setTicketError('')

      try {
        const response = await getTicketById(ticketId, currentUser.email)

        if (isCancelled) {
          return
        }

        setSelectedTicket(response)
      } catch (error) {
        if (isCancelled) {
          return
        }

        setSelectedTicket(null)
        setTicketError(error.message)
      } finally {
        if (!isCancelled) {
          setIsLoadingTicket(false)
        }
      }
    }

    loadTicket()

    return () => {
      isCancelled = true
    }
  }, [currentUser?.email, selectedTicket?.id, setSelectedTicket, ticketId])

  const ticket = selectedTicket?.id === ticketId ? selectedTicket : null
  const backSection = location.state?.from ?? 'tickets'

  if (!ticket) {
    return (
      <main className="home-page">
        <Sidebar activeSection="tickets" navigationGroups={navigationGroups} onSectionChange={onNavigatePage} />

        <div className="home-main-column">
          <Header activeSection="tickets" {...headerProps} onSectionChange={onNavigatePage} />

          <section className="home-content">
            <div className="home-content__card">
              {isLoadingTicket ? 'Carregando chamado...' : ticketError || 'Chamado não encontrado.'}
            </div>
          </section>
        </div>
      </main>
    )
  }

  return (
    <TicketConversation
      currentUser={currentUser}
      headerProps={headerProps}
      navigationGroups={navigationGroups}
      onBack={() => onNavigatePage(backSection)}
      onCloseTicket={onCloseTicket}
      onLoadTransferCandidates={onLoadTransferCandidates}
      onNavigatePage={onNavigatePage}
      onRequestTicketTransfer={onRequestTicketTransfer}
      ticket={ticket}
      userRole={userRole}
    />
  )
}

function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [authUser, setAuthUser] = useState(loadStoredSession)
  const [currentUserRole, setCurrentUserRole] = useState(() => getPrimaryRole(loadStoredSession()?.roles))
  const [createdSectors, setCreatedSectors] = useState([])
  const [ticketTargetSectors, setTicketTargetSectors] = useState([])
  const [teamMembers, setTeamMembers] = useState([])
  const [receivedInvites, setReceivedInvites] = useState([])
  const [sentInvites, setSentInvites] = useState([])
  const [companyPartnerships, setCompanyPartnerships] = useState([])
  const [ticketNotifications, setTicketNotifications] = useState([])
  const [profile, setProfile] = useState(() => loadStoredSession())
  const [isProfileLoading, setIsProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [ticketSummary, setTicketSummary] = useState(null)
  const [isTicketSummaryLoading, setIsTicketSummaryLoading] = useState(false)
  const [isTeamDataLoading, setIsTeamDataLoading] = useState(false)
  const [teamDataError, setTeamDataError] = useState('')

  const currentRouteSection = useMemo(
    () => getSectionIdFromPathname(location.pathname) ?? 'tickets',
    [location.pathname]
  )
  const currentUser = normalizeCurrentUser(profile || authUser)
  const currentUserEmail = currentUser?.email || ''
  const currentMemberId =
    teamMembers.find((member) => member.email?.toLowerCase() === currentUserEmail.toLowerCase())?.id ?? null
  const effectiveUserRole = currentUserRole
  const canAccessTeamPage = currentUserRole === 'admin' || currentMemberId !== null
  const navigationGroups = useMemo(
    () =>
      buildNavigationGroups({
        userRole: effectiveUserRole,
        sectors: createdSectors,
        teamMembers,
        currentMemberId,
      }),
    [createdSectors, currentMemberId, effectiveUserRole, teamMembers]
  )
  const visibleSectors = useMemo(
    () => getVisibleSectors(effectiveUserRole, createdSectors, teamMembers, currentMemberId),
    [createdSectors, currentMemberId, effectiveUserRole, teamMembers]
  )
  const canAccessCreateSector = currentUserRole === 'admin'
  const sectorPage = visibleSectors.find((sector) => sector.id === currentRouteSection)
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

    return [...pendingReceived, ...sentUpdates, ...ticketNotifications].sort(
      (firstInvite, secondInvite) =>
        new Date(
          secondInvite.updatedAt ||
            secondInvite.acceptedAt ||
            secondInvite.expiresAt ||
            secondInvite.createdAt
        ).getTime() -
        new Date(
          firstInvite.updatedAt ||
            firstInvite.acceptedAt ||
            firstInvite.expiresAt ||
            firstInvite.createdAt
        ).getTime()
    )
  }, [receivedInvites, sentInvites, ticketNotifications])

  const headerProps = {
    isTeamRole: canAccessTeamPage,
    isTicketSummaryLoading: isTicketSummaryLoading || isTeamDataLoading,
    isNotificationLoading: isTeamDataLoading,
    navigationGroups,
    notifications: notificationItems,
    onAcceptInvite: handleAcceptInvite,
    onAcceptCompanyAccessRequest: handleAcceptCompanyAccessRequest,
    onAcceptCompanyPartnership: handleAcceptCompanyPartnership,
    onAcceptTicketTransfer: handleAcceptTicketTransfer,
    onDeleteNotification: handleDeleteNotification,
    onDeclineCompanyAccessRequest: handleDeclineCompanyAccessRequest,
    onDeclineCompanyPartnership: handleDeclineCompanyPartnership,
    onDeclineInvite: handleDeclineInvite,
    onDeclineTicketTransfer: handleDeclineTicketTransfer,
    onNavigateLogin: handleNavigateLogin,
    onSectionChange: handleNavigatePage,
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
    setTicketTargetSectors(bundle.ticketTargets)
    setTeamMembers(bundle.teamMembers)
    setReceivedInvites(bundle.receivedInvites)
    setSentInvites(bundle.sentInvites)
    setCompanyPartnerships(bundle.companyPartnerships)
    setTicketNotifications(bundle.ticketNotifications)
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
      setTicketTargetSectors([])
      setTeamMembers([])
      setReceivedInvites([])
      setSentInvites([])
      setCompanyPartnerships([])
      setTicketNotifications([])
      setTeamDataError('')
      setIsProfileLoading(false)
      setIsTicketSummaryLoading(false)
      setIsTeamDataLoading(false)
      setSelectedTicket(null)
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
    const normalizedUser = normalizeCurrentUser(user)
    setAuthUser(normalizedUser)
    setProfile(normalizedUser)
    setProfileError('')
    setCurrentUserRole(getPrimaryRole(normalizedUser.roles))
    setSelectedTicket(null)
    navigate(SECTION_ROUTE_PATHS.tickets, { replace: true })
  }

  function handleNavigateLogin() {
    setAuthUser(null)
    setProfile(null)
    setProfileError('')
    setTicketSummary(null)
    setCreatedSectors([])
    setTicketTargetSectors([])
    setTeamMembers([])
    setReceivedInvites([])
    setSentInvites([])
    setCompanyPartnerships([])
    setTicketNotifications([])
    setTeamDataError('')
    setCurrentUserRole('user')
    setSelectedTicket(null)
    navigate(PUBLIC_ROUTE_PATHS.login, { replace: true })
  }

  function handleNavigatePage(nextPage) {
    setSelectedTicket(null)
    navigate(getSectionPath(nextPage))
  }

  function handleOpenTicket(ticket, originPage = currentRouteSection || 'tickets') {
    if (!ticket?.id) {
      return
    }

    setSelectedTicket(ticket)
    navigate(getTicketPath(ticket.id), {
      state: {
        from: originPage,
      },
    })
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

    if (currentUserRole !== 'admin') {
      throw new Error('Somente administradores podem criar setores.')
    }

    const createdSector = await createSector({
      name: trimmedName,
      description: description.trim(),
      createdByEmail: currentUserEmail,
    })

    await refreshDashboardData(currentUserEmail)
    navigate(getSectionPath(createdSector.id))
  }

  async function handleCreateTicket({
    title,
    description,
    priorityCode,
    companyOwnerId,
    sectorId,
    copyEmail,
    files = [],
  }) {
    const trimmedTitle = title.trim()
    const trimmedDescription = description.trim()
    const trimmedCopyEmail = copyEmail?.trim() || ''

    if (
      !trimmedTitle ||
      !trimmedDescription ||
      !priorityCode ||
      !companyOwnerId ||
      !sectorId ||
      !currentUserEmail
    ) {
      return
    }

    const createdTicket = await createTicket({
      title: trimmedTitle,
      description: trimmedDescription,
      files,
      priorityCode,
      companyOwnerId,
      copyEmail: trimmedCopyEmail || undefined,
      requesterEmail: currentUserEmail,
      sectorId,
    })

    await refreshDashboardData(currentUserEmail)
    setSelectedTicket(null)
    navigate(SECTION_ROUTE_PATHS.all)
    return createdTicket
  }

  async function handleSearchPartnershipCompanies(query) {
    if (!currentUserEmail) {
      return []
    }

    return searchCompanyPartnershipTargets(currentUserEmail, query)
  }

  async function handleCreateCompanyPartnership(targetCompanyId) {
    if (!currentUserEmail || !targetCompanyId) {
      return null
    }

    const partnership = await createCompanyPartnership({
      requesterEmail: currentUserEmail,
      targetCompanyId,
    })
    await refreshDashboardData(currentUserEmail)
    return normalizeCompanyPartnership(partnership)
  }

  async function handleAcceptCompanyPartnership(partnershipId) {
    if (!currentUserEmail || !partnershipId) {
      return null
    }

    const partnership = await acceptCompanyPartnership(partnershipId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
    return normalizeCompanyPartnership(partnership)
  }

  async function handleDeclineCompanyPartnership(partnershipId) {
    if (!currentUserEmail || !partnershipId) {
      return null
    }

    const partnership = await declineCompanyPartnership(partnershipId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
    return normalizeCompanyPartnership(partnership)
  }

  async function handleUnlinkCompanyPartnership(partnershipId) {
    if (!currentUserEmail || !partnershipId) {
      return null
    }

    const previousPartnerships = companyPartnerships
    setCompanyPartnerships((currentPartnerships) =>
      currentPartnerships.filter((partnership) => partnership.id !== partnershipId)
    )

    try {
      await unlinkCompanyPartnership(partnershipId, currentUserEmail)
    } catch (error) {
      setCompanyPartnerships(previousPartnerships)
      throw error
    }

    await refreshDashboardData(currentUserEmail)
    return true
  }

  async function handleCloseTicket(ticketId) {
    if (!ticketId || !currentUserEmail) {
      return null
    }

    const closedTicket = await closeTicket(ticketId, {
      authorEmail: currentUserEmail,
    })

    setSelectedTicket(closedTicket)
    await refreshDashboardData(currentUserEmail)
    navigate(SECTION_ROUTE_PATHS.closed)

    return closedTicket
  }

  async function handleRequestTicketTransfer(ticketId, recipientUserId) {
    if (!ticketId || !recipientUserId || !currentUserEmail) {
      return null
    }

    const updatedTicket = await requestTicketTransfer(ticketId, {
      authorEmail: currentUserEmail,
      recipientUserId,
    })

    setSelectedTicket(updatedTicket)
    await refreshDashboardData(currentUserEmail)

    return updatedTicket
  }

  async function handleLoadTransferCandidates(ticketId) {
    if (!ticketId || !currentUserEmail) {
      return []
    }

    return getTicketTransferCandidates(ticketId, currentUserEmail)
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

  async function handleRemoveMemberFromCompany(memberId) {
    if (!currentUserEmail) {
      return
    }

    const updatedMembers = await removeTeamMemberFromCompany(memberId, currentUserEmail)
    setTeamMembers(Array.isArray(updatedMembers) ? updatedMembers.map(normalizeTeamMember) : [])
    await refreshDashboardData(currentUserEmail)
  }

  async function handleInviteMember({ cpf, sectors }) {
    const trimmedCpf = cpf.trim()

    if (!trimmedCpf || sectors.length === 0 || !currentUserEmail) {
      return
    }

    await createTeamInvite({
      documentNumber: trimmedCpf,
      invitedByEmail: currentUserEmail,
      sectorIds: sectors,
    })
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeleteSector(sectorId) {
    if (!sectorId || !currentUserEmail) {
      return
    }

    await deleteTeamSector(sectorId, currentUserEmail)
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

  async function handleAcceptCompanyAccessRequest(requestId) {
    if (!currentUserEmail) {
      return
    }

    await acceptCompanyAccessRequestNotification(requestId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeclineCompanyAccessRequest(requestId) {
    if (!currentUserEmail) {
      return
    }

    await declineCompanyAccessRequestNotification(requestId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleAcceptTicketTransfer(notificationId) {
    if (!currentUserEmail) {
      return
    }

    await acceptTicketTransferNotification(notificationId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeclineTicketTransfer(notificationId) {
    if (!currentUserEmail) {
      return
    }

    await declineTicketTransferNotification(notificationId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeleteNotification(notificationOrId) {
    if (!currentUserEmail) {
      return
    }

    if (typeof notificationOrId === 'object') {
      if (notificationOrId.type === 'ticket-assignment') {
        await deleteTicketAssignmentNotification(notificationOrId.id, currentUserEmail)
        await refreshDashboardData(currentUserEmail)
        return
      }

      if (notificationOrId.type === 'ticket-transfer') {
        await deleteTicketTransferNotification(notificationOrId.id, currentUserEmail)
        await refreshDashboardData(currentUserEmail)
        return
      }

      if (notificationOrId.type === 'team-membership-removed') {
        await deleteTeamMembershipNotification(notificationOrId.id, currentUserEmail)
        await refreshDashboardData(currentUserEmail)
        return
      }

      if (notificationOrId.type === 'calendar-reminder') {
        await deleteCalendarReminderNotification(notificationOrId.id, currentUserEmail)
        await refreshDashboardData(currentUserEmail)
        return
      }

      if (notificationOrId.type === 'company-partnership') {
        await deleteCompanyPartnershipNotification(notificationOrId.id, currentUserEmail)
        await refreshDashboardData(currentUserEmail)
        return
      }
    }

    const inviteId =
      typeof notificationOrId === 'object' ? notificationOrId?.id : notificationOrId

    await deleteTeamNotification(inviteId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeleteAccount() {
    if (!currentUserEmail) {
      return
    }

    await deleteProfile(currentUserEmail)
    handleNavigateLogin()
  }

  async function handleDeleteCompany() {
    if (!currentUserEmail) {
      return
    }

    await deleteCompanyProfile(currentUserEmail)
    handleNavigateLogin()
  }

  function renderDashboardPage(pageId) {
    const CurrentDashboardPage = dashboardPageComponents[pageId]

    return (
      <CurrentDashboardPage
        currentUser={currentUser}
        headerProps={headerProps}
        isProfileLoading={isProfileLoading}
        isTeamDataLoading={isTeamDataLoading}
        isTicketSummaryLoading={isTicketSummaryLoading}
        navigationGroups={navigationGroups}
        onAcceptInvite={handleAcceptInvite}
        onAcceptTicketTransfer={handleAcceptTicketTransfer}
        onCreateSector={handleCreateSector}
        onCreateCompanyPartnership={handleCreateCompanyPartnership}
        onCreateTicket={handleCreateTicket}
        onAcceptCompanyPartnership={handleAcceptCompanyPartnership}
        onDeclineInvite={handleDeclineInvite}
        onDeclineCompanyPartnership={handleDeclineCompanyPartnership}
        onDeclineTicketTransfer={handleDeclineTicketTransfer}
        onDeleteAccount={handleDeleteAccount}
        onDeleteCompany={handleDeleteCompany}
        onUnlinkCompanyPartnership={handleUnlinkCompanyPartnership}
        onDeleteNotification={handleDeleteNotification}
        onDeleteSector={handleDeleteSector}
        onInviteMember={handleInviteMember}
        onNavigatePage={handleNavigatePage}
        onOpenTicket={handleOpenTicket}
        onRefreshDashboardData={refreshDashboardData}
        onRemoveMemberFromCompany={handleRemoveMemberFromCompany}
        onSearchPartnershipCompanies={handleSearchPartnershipCompanies}
        onUpdateMemberSectors={handleUpdateMemberSectors}
        availableTicketSectors={ticketTargetSectors}
        companyPartnerships={companyPartnerships}
        profileError={profileError}
        receivedInvites={receivedInvites}
        sectors={visibleSectors}
        sentInvites={sentInvites}
        teamDataError={teamDataError}
        teamMembers={teamMembers}
        ticketNotifications={ticketNotifications}
        userRole={effectiveUserRole}
      />
    )
  }

  return (
    <div className="app-shell">
      <div className="app-shell__routes">
        <Routes>
          <Route
            path="/"
            element={
              <Navigate
                replace
                to={authUser ? SECTION_ROUTE_PATHS.tickets : PUBLIC_ROUTE_PATHS.login}
              />
            }
          />
          <Route
            path={PUBLIC_ROUTE_PATHS.login}
            element={
              authUser ? (
                <Navigate replace to={SECTION_ROUTE_PATHS.tickets} />
              ) : (
                <Login
                  onNavigateHome={handleAuthenticatedUser}
                  onNavigateRegister={() => navigate(PUBLIC_ROUTE_PATHS.register)}
                />
              )
            }
          />
          <Route
            path={PUBLIC_ROUTE_PATHS.register}
            element={
              authUser ? (
                <Navigate replace to={SECTION_ROUTE_PATHS.tickets} />
              ) : (
                <Register
                  onNavigateHome={handleAuthenticatedUser}
                  onNavigateLogin={() => navigate(PUBLIC_ROUTE_PATHS.login)}
                />
              )
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.tickets}
            element={
              authUser ? renderDashboardPage('tickets') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.calendar}
            element={
              authUser ? renderDashboardPage('calendar') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.reports}
            element={
              authUser ? renderDashboardPage('reports') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.all}
            element={
              authUser ? renderDashboardPage('all') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.open}
            element={
              authUser ? renderDashboardPage('open') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.closed}
            element={
              authUser ? renderDashboardPage('closed') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.newTicket}
            element={
              authUser ? renderDashboardPage('newTicket') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.myData}
            element={
              authUser ? renderDashboardPage('myData') : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.team}
            element={
              authUser ? (
                canAccessTeamPage ? (
                  renderDashboardPage('team')
                ) : (
                  <Navigate replace to={SECTION_ROUTE_PATHS.tickets} />
                )
              ) : (
                <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
              )
            }
          />
          <Route
            path={SECTION_ROUTE_PATHS.createSector}
            element={
              authUser ? (
                canAccessCreateSector ? (
                  renderDashboardPage('createSector')
                ) : (
                  <Navigate replace to={SECTION_ROUTE_PATHS.tickets} />
                )
              ) : (
                <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
              )
            }
          />
          <Route
            path="/sectors/:sectorId"
            element={
              authUser ? (
                sectorPage ? (
                  <Sector
                    headerProps={headerProps}
                    navigationGroups={navigationGroups}
                    onNavigatePage={handleNavigatePage}
                    sector={sectorPage}
                    teamMembers={teamMembers}
                    userRole={currentUserRole}
                  />
                ) : (
                  <Navigate replace to={SECTION_ROUTE_PATHS.tickets} />
                )
              ) : (
                <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
              )
            }
          />
          <Route
            path="/tickets/:ticketId"
            element={
              authUser ? (
                <TicketConversationRoute
                  currentUser={currentUser}
                  headerProps={headerProps}
                  navigationGroups={navigationGroups}
                  onCloseTicket={handleCloseTicket}
                  onLoadTransferCandidates={handleLoadTransferCandidates}
                  onNavigatePage={handleNavigatePage}
                  onRequestTicketTransfer={handleRequestTicketTransfer}
                  selectedTicket={selectedTicket}
                  setSelectedTicket={setSelectedTicket}
                  userRole={effectiveUserRole}
                />
              ) : (
                <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
              )
            }
          />
          <Route
            path="*"
            element={
              <Navigate
                replace
                to={authUser ? SECTION_ROUTE_PATHS.tickets : PUBLIC_ROUTE_PATHS.login}
              />
            }
          />
        </Routes>
      </div>

      <footer className="app-shell__footer">
        <span>&copy; 2026 Siaficont Sistemas. Todos os direitos reservados.</span>
      </footer>
    </div>
  )
}

export default App
