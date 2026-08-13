import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  acceptCompanyAccessRequestNotification,
  acceptCompanyInviteNotification,
  acceptCompanyPartnership,
  acceptTicketTransferNotification,
  acceptTeamInvite,
  clearStoredAppAuthToken,
  changePassword as changePasswordRequest,
  closeTicket,
  createClientCompany,
  createCompanyInvite,
  createCompanyPartnership,
  createTicket,
  createSector,
  createTeamInvite,
  declineCompanyAccessRequestNotification,
  declineCompanyInviteNotification,
  declineCompanyPartnership,
  deleteTeamSector,
  deleteCompanyPartnershipNotification,
  deleteTicketClosureNotification,
  deleteTicketAssignmentNotification,
  deleteCalendarReminderNotification,
  deleteTeamMembershipNotification,
  deleteTicketTransferNotification,
  deleteTicketReplyNotification,
  deleteTeamNotification,
  deleteCompanyLogo as deleteCompanyLogoRequest,
  deleteCompanyProfile,
  deleteProfile,
  deleteTickets,
  declineTicketTransferNotification,
  declineTeamInvite,
  getStoredAppAuthToken,
  getCompanyPartnershipTicketTargets,
  getAuthMe,
  getProfile,
  getMyCompanyPartnerships,
  getCalendarReminderNotifications,
  getCompanyAccessRequestNotifications,
  getCompanyInviteNotifications,
  getCompanyPartnershipNotifications,
  getTicketClosureNotifications,
  getTicketAssignmentNotifications,
  getTicketReplyNotifications,
  getTeamMembershipNotifications,
  getTicketTransferNotifications,
  getReceivedTeamInvites,
  getSectors,
  getSentTeamInvites,
  getTeamMembers,
  getTicketById,
  getTicketSummary,
  getTicketTransferCandidates,
  linkExistingClientCompany,
  lookupClientCompany,
  requestPasswordReset,
  removeTeamMemberFromCompany,
  resetPasswordWithToken,
  requestTicketTransfer,
  logoutCurrentUser,
  searchCompanyPartnershipTargets,
  unlinkCompanyPartnership,
  updateTicketTitle as updateTicketTitleRequest,
  updateTicketClassification as updateTicketClassificationRequest,
  updateProfile as updateProfileRequest,
  uploadCompanyLogo as uploadCompanyLogoRequest,
  updateTeamMemberSectors,
} from './app/api'
import { buildNavigationGroups, getVisibleSectors } from './app/dashboardData'
import Header from './app/components/header/Header'
import Sidebar from './app/components/sidebar/Sidebar'
import { useTenantBranding } from './app/context/TenantBrandingContext'
import PlatformAdminApp from './admin/PlatformAdminApp'
import {
  PUBLIC_ROUTE_PATHS,
  SECTION_ROUTE_PATHS,
  getSectionIdFromPathname,
  getSectionPath,
  getTicketPath,
} from './app/routes'
import { isManagedTenantHost, isPlatformAdminHost } from './app/platformAdminHost'
import AllTickets from './app/pages/AllTickets/AllTickets'
import Calendar from './app/pages/Calendar/Calendar'
import ClosedTickets from './app/pages/ClosedTickets/ClosedTickets'
import ClientCompanyRegister from './app/pages/ClientCompanyRegister/ClientCompanyRegister'
import CreateSector from './app/pages/CreateSector/CreateSector'
import Home from './app/pages/Home/Home'
import Login from './app/pages/Login/Login'
import LegalDocumentPage from './app/pages/LegalDocument/LegalDocumentPage'
import MyData from './app/pages/MyData/MyData'
import NewTicket from './app/pages/NewTicket/NewTicket'
import OpenTickets from './app/pages/OpenTickets/OpenTickets'
import Register from './app/pages/Register/Register'
import Reports from './app/pages/Reports/Reports'
import Sector from './app/pages/Sector/Sector'
import Team from './app/pages/Team/Team'
import TicketAttachmentViewer from './app/pages/TicketAttachmentViewer/TicketAttachmentViewer'
import TicketConversation from './app/pages/TicketConversation/TicketConversation'
import WhatsappQrCodePage from './app/pages/WhatsappQrCodePage/WhatsappQrCodePage'

const dashboardPageComponents = {
  tickets: Home,
  calendar: Calendar,
  reports: Reports,
  all: AllTickets,
  open: OpenTickets,
  closed: ClosedTickets,
  createSector: CreateSector,
  clientCompanyRegister: ClientCompanyRegister,
  newTicket: NewTicket,
  myData: MyData,
  team: Team,
}

const AUTO_REFRESH_INTERVAL_MS = 5000
const DEFAULT_DOCUMENT_TITLE = 'ChamAqui Helpdesk'


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
    assignees: Array.isArray(sector.assignees)
      ? sector.assignees.map((assignee) => ({
          id: assignee.id,
          fullName: assignee.fullName || 'Funcionário não informado',
          email: assignee.email || '',
        }))
      : [],
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
    documentNumber: member.documentNumber || '',
    companyOwnerId: member.companyOwnerId || '',
    companyName: member.companyName || '',
    role: member.role,
    status: member.status,
    sectors: Array.isArray(member.sectorIds) ? Array.from(new Set(member.sectorIds)) : [],
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
    companyName: notification.companyName || 'Empresa não informada',
    requesterCompanyName: notification.requesterCompanyName || '',
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
    companyName: notification.companyName || 'Empresa não informada',
    requesterCompanyName: notification.requesterCompanyName || '',
    senderName: notification.senderName,
    recipientName: notification.recipientName,
    status: notification.status,
    createdAt: notification.createdAt,
    updatedAt: notification.updatedAt,
    respondedAt: notification.respondedAt,
    type: 'ticket-transfer',
  }
}

function normalizeTicketClosureNotification(notification) {
  return {
    id: notification.id,
    ticketId: notification.ticketId,
    ticketProtocol: notification.ticketProtocol,
    ticketTitle: notification.ticketTitle,
    sectorName: notification.sectorName,
    companyName: notification.companyName || 'Empresa não informada',
    closedByName: notification.closedByName || 'Usuário não informado',
    createdAt: notification.createdAt,
    status: 'CLOSED',
    type: 'ticket-closure',
  }
}

function normalizeTicketReplyNotification(notification) {
  return {
    id: notification.id,
    ticketId: notification.ticketId,
    ticketProtocol: notification.ticketProtocol,
    ticketTitle: notification.ticketTitle,
    requesterName: notification.requesterName,
    sectorName: notification.sectorName,
    companyName: notification.companyName || 'Empresa não informada',
    requesterCompanyName: notification.requesterCompanyName || '',
    messagePreview: notification.messagePreview || '',
    status: notification.status,
    createdAt: notification.createdAt,
    type: 'ticket-reply',
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

function normalizeCompanyInviteNotification(notification) {
  return {
    id: notification.id,
    requesterName: notification.requesterName || 'Pessoa não informada',
    requesterEmail: notification.requesterEmail || '',
    requesterDocumentNumber: notification.requesterDocumentNumber || '',
    requestedRole: notification.requestedRole || 'user',
    companyName: notification.companyName || 'Empresa não informada',
    companyType: notification.companyType || '',
    status: notification.status || 'PENDING',
    createdAt: notification.createdAt,
    type: 'company-invite',
  }
}

function normalizeAppFeedbackNotification(notification) {
  return {
    id: notification.id,
    title: notification.title || 'Atualização da equipe',
    description: notification.description || '',
    status: notification.status || 'ACCEPTED',
    createdAt: notification.createdAt || new Date().toISOString(),
    type: 'app-feedback',
  }
}

function shouldAutoDeleteNotificationAfterView(notification) {
  if (!notification || typeof notification !== 'object') {
    return false
  }

  if (notification.type === 'calendar-reminder') {
    return false
  }

  if (notification.type === 'received') {
    return false
  }

  if (notification.type === 'ticket-transfer' && notification.status === 'PENDING') {
    return false
  }

  if (notification.type === 'company-access-request' && notification.status === 'PENDING') {
    return false
  }

  if (notification.type === 'company-invite' && notification.status === 'PENDING') {
    return false
  }

  if (notification.type === 'company-partnership') {
    return !(notification.eventType === 'REQUESTED' && notification.canRespond)
  }

  return true
}

async function fetchDashboardBundle() {
  const nextProfile = await getProfile()
  const normalizedProfile = normalizeCurrentUser(nextProfile)
  const currentRole = getPrimaryRole(normalizedProfile.roles)
  const canManageCompanyRequests = currentRole === 'admin'

  async function safeRequest(request, fallbackValue) {
    try {
      return await request()
    } catch {
      return fallbackValue
    }
  }

  const [
    nextSummary,
    nextSectors,
    nextTicketTargets,
    nextMembers,
    nextReceivedInvites,
    nextSentInvites,
    nextCompanyPartnerships,
    nextTicketNotifications,
    nextTicketTransferNotifications,
    nextTicketClosureNotifications,
    nextTicketReplyNotifications,
    nextTeamMembershipNotifications,
    nextCalendarReminderNotifications,
    nextCompanyPartnershipNotifications,
    nextCompanyAccessRequestNotifications,
    nextCompanyInviteNotifications,
  ] = await Promise.all([
    safeRequest(() => getTicketSummary(), null),
    safeRequest(() => getSectors(), []),
    safeRequest(() => getCompanyPartnershipTicketTargets(), []),
    safeRequest(() => getTeamMembers(), []),
    safeRequest(() => getReceivedTeamInvites(), []),
    safeRequest(() => getSentTeamInvites(), []),
    canManageCompanyRequests ? safeRequest(() => getMyCompanyPartnerships(), []) : Promise.resolve([]),
    safeRequest(() => getTicketAssignmentNotifications(), []),
    safeRequest(() => getTicketTransferNotifications(), []),
    safeRequest(() => getTicketClosureNotifications(), []),
    safeRequest(() => getTicketReplyNotifications(), []),
    safeRequest(() => getTeamMembershipNotifications(), []),
    safeRequest(() => getCalendarReminderNotifications(), []),
    safeRequest(() => getCompanyPartnershipNotifications(), []),
    canManageCompanyRequests
      ? safeRequest(() => getCompanyAccessRequestNotifications(), [])
      : Promise.resolve([]),
    safeRequest(() => getCompanyInviteNotifications(), []),
  ])

  return {
    profile: normalizedProfile,
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
      ...(Array.isArray(nextTicketClosureNotifications)
        ? nextTicketClosureNotifications.map(normalizeTicketClosureNotification)
        : []),
      ...(Array.isArray(nextTicketReplyNotifications)
        ? nextTicketReplyNotifications.map(normalizeTicketReplyNotification)
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
      ...(Array.isArray(nextCompanyInviteNotifications)
        ? nextCompanyInviteNotifications.map(normalizeCompanyInviteNotification)
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

function TicketConversationRoute({
  currentUser,
  headerProps,
  navigationGroups,
  onCloseTicket,
  onLoadTransferCandidates,
  onNavigatePage,
  onRefreshDashboardData,
  onRequestTicketTransfer,
  onUpdateTicketTitle,
  onUpdateTicketClassification,
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
      onRefreshDashboardData={onRefreshDashboardData}
      onRequestTicketTransfer={onRequestTicketTransfer}
      onUpdateTicketTitle={onUpdateTicketTitle}
      onUpdateTicketClassification={onUpdateTicketClassification}
      ticket={ticket}
      userRole={userRole}
    />
  )
}

function App() {
  if (isPlatformAdminHost()) {
    return <PlatformAdminApp />
  }

  const { branding: tenantBranding, isLoading: isTenantBrandingLoading } = useTenantBranding()
  const shouldValidateTenantHost = isManagedTenantHost()

  if (shouldValidateTenantHost && isTenantBrandingLoading) {
    return <UnknownTenantHostPage isLoading />
  }

  if (shouldValidateTenantHost && !tenantBranding?.tenantResolved) {
    return <UnknownTenantHostPage />
  }

  const navigate = useNavigate()
  const location = useLocation()
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [authUser, setAuthUser] = useState(null)
  const [isSessionBootstrapping, setIsSessionBootstrapping] = useState(true)
  const [currentUserRole, setCurrentUserRole] = useState('user')
  const [createdSectors, setCreatedSectors] = useState([])
  const [ticketTargetSectors, setTicketTargetSectors] = useState([])
  const [teamMembers, setTeamMembers] = useState([])
  const [receivedInvites, setReceivedInvites] = useState([])
  const [sentInvites, setSentInvites] = useState([])
  const [companyPartnerships, setCompanyPartnerships] = useState([])
  const [ticketNotifications, setTicketNotifications] = useState([])
  const [appFeedbackNotifications, setAppFeedbackNotifications] = useState([])
  const [profile, setProfile] = useState(null)
  const [isProfileLoading, setIsProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [ticketSummary, setTicketSummary] = useState(null)
  const [isTicketSummaryLoading, setIsTicketSummaryLoading] = useState(false)
  const [isTeamDataLoading, setIsTeamDataLoading] = useState(false)
  const [teamDataError, setTeamDataError] = useState('')
  const autoViewedNotificationIdsRef = useRef(new Set())

  const currentRouteSection = useMemo(
    () => getSectionIdFromPathname(location.pathname) ?? 'tickets',
    [location.pathname]
  )
  const currentUser = normalizeCurrentUser(profile || authUser)
  const currentUserEmail = currentUser?.email || ''
  const currentMemberId =
    teamMembers.find((member) => member.email?.toLowerCase() === currentUserEmail.toLowerCase())?.id ?? null
  const effectiveUserRole = currentUserRole
  const currentCompanyUsesSectors = currentUser?.companyType === 'RESPONDER'
  const hasPendingTeamInvites = receivedInvites.some((invite) => invite.status === 'PENDING')
  const canAccessTeamPage =
    currentUserRole === 'admin' || currentMemberId !== null || hasPendingTeamInvites
  const canAccessCreateSector = currentUserRole === 'admin' && currentCompanyUsesSectors
  const navigationGroups = useMemo(
    () =>
      buildNavigationGroups({
        canAccessTeamPage,
        canCreateSector: canAccessCreateSector,
        userRole: effectiveUserRole,
        sectors: currentCompanyUsesSectors ? createdSectors : [],
        teamMembers,
        currentMemberId,
      }),
    [
      canAccessCreateSector,
      canAccessTeamPage,
      createdSectors,
      currentCompanyUsesSectors,
      currentMemberId,
      effectiveUserRole,
      teamMembers,
    ]
  )
  const visibleSectors = useMemo(
    () =>
      currentCompanyUsesSectors
        ? getVisibleSectors(effectiveUserRole, createdSectors, teamMembers, currentMemberId)
        : [],
    [createdSectors, currentCompanyUsesSectors, currentMemberId, effectiveUserRole, teamMembers]
  )
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

    return [...appFeedbackNotifications, ...pendingReceived, ...sentUpdates, ...ticketNotifications].sort(
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
  }, [appFeedbackNotifications, receivedInvites, sentInvites, ticketNotifications])
  const browserTicketNotificationCount = useMemo(
    () =>
      currentUserRole === 'employee' || currentUserRole === 'admin'
        ? ticketNotifications.filter(
            (notification) =>
              notification.type === 'ticket-assignment' || notification.type === 'ticket-reply'
          ).length
        : 0,
    [currentUserRole, ticketNotifications]
  )

  const headerProps = {
    isTeamRole: canAccessTeamPage,
    isTicketSummaryLoading: isTicketSummaryLoading || isTeamDataLoading,
    isNotificationLoading: isTeamDataLoading,
    navigationGroups,
    notifications: notificationItems,
    onAcceptInvite: handleAcceptInvite,
    onAcceptCompanyAccessRequest: handleAcceptCompanyAccessRequest,
    onAcceptCompanyPartnership: handleAcceptCompanyPartnership,
    onAcceptCompanyInvite: handleAcceptCompanyInvite,
    onAcceptTicketTransfer: handleAcceptTicketTransfer,
    onDeleteNotification: handleDeleteNotification,
    onDeclineCompanyAccessRequest: handleDeclineCompanyAccessRequest,
    onDeclineCompanyPartnership: handleDeclineCompanyPartnership,
    onDeclineCompanyInvite: handleDeclineCompanyInvite,
    onDeclineInvite: handleDeclineInvite,
    onDeclineTicketTransfer: handleDeclineTicketTransfer,
    onOpenNotification: handleOpenNotification,
    onNotificationsViewed: handleViewedNotifications,
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

  function pushAppFeedbackNotification(notification) {
    setAppFeedbackNotifications((currentNotifications) => [
      normalizeAppFeedbackNotification({
        id: `app-feedback-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        ...notification,
        createdAt: new Date().toISOString(),
      }),
      ...currentNotifications,
    ])
  }

  useEffect(() => {
    let isCancelled = false

    async function bootstrapSession() {
      const storedAuthToken = getStoredAppAuthToken()
      if (!storedAuthToken) {
        if (!isCancelled) {
          setAuthUser(null)
          setProfile(null)
          setCurrentUserRole('user')
          setIsSessionBootstrapping(false)
        }
        return
      }

      try {
        const currentSessionUser = await getAuthMe()
        if (isCancelled) {
          return
        }
        const normalizedUser = normalizeCurrentUser(currentSessionUser)
        setAuthUser(normalizedUser)
        setCurrentUserRole(getPrimaryRole(normalizedUser?.roles))
      } catch {
        if (!isCancelled) {
          clearStoredAppAuthToken()
          setAuthUser(null)
          setProfile(null)
          setCurrentUserRole('user')
        }
      } finally {
        if (!isCancelled) {
          setIsSessionBootstrapping(false)
        }
      }
    }

    bootstrapSession()

    return () => {
      isCancelled = true
    }
  }, [])

  useEffect(() => {
    if (typeof document === 'undefined') {
      return undefined
    }

    document.title =
      browserTicketNotificationCount > 0
        ? `(${browserTicketNotificationCount}) ${DEFAULT_DOCUMENT_TITLE}`
        : DEFAULT_DOCUMENT_TITLE

    return () => {
      document.title = DEFAULT_DOCUMENT_TITLE
    }
  }, [browserTicketNotificationCount])

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
        const bundle = await fetchDashboardBundle()

        if (isCancelled) {
          return
        }

        applyDashboardBundle(bundle)
      } catch (error) {
        if (isCancelled) {
          return
        }

        if (error?.status === 401) {
          handleNavigateLogin({ invalidateServerSession: false })
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

  useEffect(() => {
    if (!authUser?.email) {
      return undefined
    }

    let isCancelled = false

    async function refreshBundleSilently() {
      try {
        const bundle = await fetchDashboardBundle()

        if (isCancelled) {
          return
        }

        applyDashboardBundle(bundle)
      } catch (error) {
        if (error?.status === 401) {
          handleNavigateLogin({ invalidateServerSession: false })
        }
        // Mantem os dados atuais quando a atualizacao silenciosa falha.
      }
    }

    const intervalId = window.setInterval(refreshBundleSilently, AUTO_REFRESH_INTERVAL_MS)
    const handleWindowFocus = () => {
      refreshBundleSilently()
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refreshBundleSilently()
      }
    }

    window.addEventListener('focus', handleWindowFocus)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      isCancelled = true
      window.clearInterval(intervalId)
      window.removeEventListener('focus', handleWindowFocus)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [authUser?.email])

  function handleAuthenticatedUser(user) {
    const normalizedUser = normalizeCurrentUser(user)
    setIsSessionBootstrapping(false)
    setAuthUser(normalizedUser)
    setProfile(normalizedUser)
    setProfileError('')
    setCurrentUserRole(getPrimaryRole(normalizedUser.roles))
    setSelectedTicket(null)
    navigate(SECTION_ROUTE_PATHS.tickets, { replace: true })
  }

  function handleNavigateLogin({ invalidateServerSession = true } = {}) {
    if (invalidateServerSession) {
      void logoutCurrentUser().catch(() => {})
    }
    clearStoredAppAuthToken()
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

  async function refreshDashboardData() {
    if (!currentUserEmail) {
      return
    }

    setIsTeamDataLoading(true)
    setTeamDataError('')

    try {
      const bundle = await fetchDashboardBundle()
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
    description,
    priorityCode,
    companyOwnerId,
    sectorId,
    assignedToUserId,
    copyEmail,
    files = [],
  }) {
    const trimmedDescription = description.trim()
    const trimmedCopyEmail = copyEmail?.trim() || ''

    if (
      !trimmedDescription ||
      !priorityCode ||
      !companyOwnerId ||
      !sectorId ||
      !currentUserEmail
    ) {
      return
    }

    const createdTicket = await createTicket({
      description: trimmedDescription,
      files,
      priorityCode,
      companyOwnerId,
      copyEmail: trimmedCopyEmail || undefined,
      requesterEmail: currentUserEmail,
      sectorId,
      assignedToUserId,
    })

    await refreshDashboardData(currentUserEmail)
    setSelectedTicket(null)
    navigate(SECTION_ROUTE_PATHS.all)
    return createdTicket
  }

  async function handleUpdateTicketTitle(ticketId, title) {
    const trimmedTitle = title?.trim() || ''

    if (!ticketId || !trimmedTitle || !currentUserEmail) {
      return null
    }

    const updatedTicket = await updateTicketTitleRequest(ticketId, {
      title: trimmedTitle,
      authorEmail: currentUserEmail,
    })

    setSelectedTicket(updatedTicket)
    await refreshDashboardData(currentUserEmail)

    return updatedTicket
  }

  async function handleUpdateTicketClassification(ticketId, typeCode, systemAreaCode) {
    if (!ticketId || !currentUserEmail) {
      return null
    }

    const updatedTicket = await updateTicketClassificationRequest(ticketId, {
      typeCode: typeCode || null,
      systemAreaCode: systemAreaCode || null,
      authorEmail: currentUserEmail,
    })

    setSelectedTicket(updatedTicket)
    await refreshDashboardData(currentUserEmail)
    return updatedTicket
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

  async function handleCreateClientCompany(payload) {
    if (!currentUserEmail) {
      return null
    }

    const response = await createClientCompany({
      ...payload,
      createdByEmail: currentUserEmail,
    })
    await refreshDashboardData(currentUserEmail)
    return response
  }

  async function handleLookupClientCompany(companyDocument) {
    if (!currentUserEmail || !companyDocument) {
      return null
    }

    return lookupClientCompany(companyDocument, currentUserEmail)
  }

  async function handleLinkExistingClientCompany(companyOwnerId) {
    if (!currentUserEmail || !companyOwnerId) {
      return null
    }

    const response = await linkExistingClientCompany({
      companyOwnerId,
      createdByEmail: currentUserEmail,
    })
    await refreshDashboardData(currentUserEmail)
    return response
  }

  async function handleInviteCompanyMember({ fullName, email, documentNumber }) {
    if (!currentUserEmail) {
      return null
    }

    const createdInvite = await createCompanyInvite({
      fullName,
      email,
      documentNumber,
      invitedByEmail: currentUserEmail,
    })
    await refreshDashboardData(currentUserEmail)
    return createdInvite
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

  async function handleDeleteTickets(ticketIds) {
    if (!currentUserEmail || !Array.isArray(ticketIds) || ticketIds.length === 0) {
      return
    }

    await deleteTickets({
      authorEmail: currentUserEmail,
      ticketIds,
    })

    if (selectedTicket && ticketIds.includes(selectedTicket.id)) {
      setSelectedTicket(null)
      navigate(getSectionPath('all'))
    }

    await refreshDashboardData(currentUserEmail)
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

  const handleLoadTransferCandidates = useCallback(
    async (ticketId) => {
      if (!ticketId || !currentUserEmail) {
        return []
      }

      return getTicketTransferCandidates(ticketId, currentUserEmail)
    },
    [currentUserEmail]
  )

  async function handleUpdateMemberSectors(memberId, nextSectors) {
    if (!currentUserEmail) {
      return
    }

    const updatedMembers = await updateTeamMemberSectors(memberId, {
      assignedByEmail: currentUserEmail,
      sectorIds: Array.from(new Set(nextSectors || [])),
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
      return null
    }

    const createdInvite = await createTeamInvite({
      documentNumber: trimmedCpf,
      invitedByEmail: currentUserEmail,
      sectorIds: sectors,
    })
    await refreshDashboardData(currentUserEmail)
    return normalizeInvite(createdInvite)
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

  async function handleAcceptCompanyInvite(requestId) {
    if (!currentUserEmail) {
      return
    }

    await acceptCompanyInviteNotification(requestId, currentUserEmail)
    await refreshDashboardData(currentUserEmail)
  }

  async function handleDeclineCompanyInvite(requestId) {
    if (!currentUserEmail) {
      return
    }

    await declineCompanyInviteNotification(requestId, currentUserEmail)
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

		await deleteNotificationResource(notificationOrId)
		removeDeletedNotificationFromState(notificationOrId)
	}

	function removeDeletedNotificationFromState(notificationOrId) {
		const notification = typeof notificationOrId === 'object' ? notificationOrId : null
		const notificationId = notification?.id || notificationOrId

		if (!notificationId) {
			return
		}

		if (notification?.type === 'app-feedback') {
			setAppFeedbackNotifications((currentNotifications) =>
				currentNotifications.filter((item) => item.id !== notificationId)
			)
			return
		}

		if (notification?.type === 'received') {
			setReceivedInvites((currentInvites) =>
				currentInvites.filter((invite) => invite.id !== notificationId)
			)
			return
		}

		if (notification?.type === 'sent') {
			setSentInvites((currentInvites) =>
				currentInvites.filter((invite) => invite.id !== notificationId)
			)
			return
		}

		setTicketNotifications((currentNotifications) =>
			currentNotifications.filter((item) => item.id !== notificationId)
		)
	}

  async function deleteNotificationResource(notificationOrId) {
    if (typeof notificationOrId === 'object') {
		if (notificationOrId.type === 'app-feedback') {
			return
		}

      if (notificationOrId.type === 'ticket-assignment') {
        await deleteTicketAssignmentNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'ticket-transfer') {
        await deleteTicketTransferNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'ticket-closure') {
        await deleteTicketClosureNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'ticket-reply') {
        await deleteTicketReplyNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'team-membership-removed') {
        await deleteTeamMembershipNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'calendar-reminder') {
        await deleteCalendarReminderNotification(notificationOrId.id, currentUserEmail)
        return
      }

      if (notificationOrId.type === 'company-partnership') {
        await deleteCompanyPartnershipNotification(notificationOrId.id, currentUserEmail)
        return
      }
    }

    const inviteId =
      typeof notificationOrId === 'object' ? notificationOrId?.id : notificationOrId

    await deleteTeamNotification(inviteId, currentUserEmail)
  }

  async function handleViewedNotifications(visibleNotifications) {
    if (!currentUserEmail || !Array.isArray(visibleNotifications) || visibleNotifications.length === 0) {
      return
    }

    const notificationsToDelete = visibleNotifications.filter(
      (notification) =>
        shouldAutoDeleteNotificationAfterView(notification) &&
        !autoViewedNotificationIdsRef.current.has(notification.id)
    )

    if (notificationsToDelete.length === 0) {
      return
    }

    notificationsToDelete.forEach((notification) => {
      autoViewedNotificationIdsRef.current.add(notification.id)
    })

    const deleteResults = await Promise.allSettled(
      notificationsToDelete.map((notification) => deleteNotificationResource(notification))
    )
    const deletedAtLeastOne = deleteResults.some((result) => result.status === 'fulfilled')

    deleteResults.forEach((result, index) => {
      if (result.status === 'rejected') {
        autoViewedNotificationIdsRef.current.delete(notificationsToDelete[index].id)
      }
    })

    if (deletedAtLeastOne) {
      await refreshDashboardData(currentUserEmail)
    }
  }

  function handleOpenNotification(notification) {
    if (!notification) {
      return
    }

    if (
      (notification.type === 'ticket-assignment' || notification.type === 'ticket-reply') &&
      notification.ticketId
    ) {
      navigate(getTicketPath(notification.ticketId))
      return
    }

    if (notification.type === 'calendar-reminder' && notification.obligationId) {
      navigate(`${SECTION_ROUTE_PATHS.calendar}?obligationId=${notification.obligationId}`)
    }
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

  async function handleUpdateProfile(profileData) {
    if (!currentUserEmail) {
      return null
    }

    const updatedProfile = await updateProfileRequest({
      currentEmail: currentUserEmail,
      ...profileData,
    })

    const normalizedProfile = normalizeCurrentUser(updatedProfile)
    setProfile(normalizedProfile)
    setAuthUser(normalizedProfile)
    setCurrentUserRole(getPrimaryRole(normalizedProfile.roles))
    await refreshDashboardData(normalizedProfile.email)

    return normalizedProfile
  }

  async function handleChangePassword(passwordData) {
    if (!currentUserEmail) {
      return null
    }

    const response = await changePasswordRequest({
      currentEmail: currentUserEmail,
      ...passwordData,
    })
    await refreshDashboardData(currentUserEmail)
    return response
  }

  async function handleRequestPasswordReset(email) {
    return requestPasswordReset({ email })
  }

  async function handleResetPassword(resetData) {
    return resetPasswordWithToken(resetData)
  }

  async function handleUploadCompanyLogo(file) {
    if (!currentUserEmail || !file) {
      return null
    }

    const response = await uploadCompanyLogoRequest(currentUserEmail, file)
    await refreshDashboardData(currentUserEmail)
    return response
  }

  async function handleDeleteCompanyLogo() {
    if (!currentUserEmail) {
      return null
    }

    const response = await deleteCompanyLogoRequest(currentUserEmail)
    await refreshDashboardData(currentUserEmail)
    return response
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
        onCreateClientCompany={handleCreateClientCompany}
        onCreateCompanyPartnership={handleCreateCompanyPartnership}
        onCreateTicket={handleCreateTicket}
        onChangePassword={handleChangePassword}
        onLookupClientCompany={handleLookupClientCompany}
        onLinkExistingClientCompany={handleLinkExistingClientCompany}
        onAcceptCompanyPartnership={handleAcceptCompanyPartnership}
        onAcceptCompanyInvite={handleAcceptCompanyInvite}
        onDeclineInvite={handleDeclineInvite}
        onDeclineCompanyPartnership={handleDeclineCompanyPartnership}
        onDeclineCompanyInvite={handleDeclineCompanyInvite}
        onDeclineTicketTransfer={handleDeclineTicketTransfer}
        onDeleteAccount={handleDeleteAccount}
        onDeleteCompany={handleDeleteCompany}
        onDeleteTickets={handleDeleteTickets}
        onUnlinkCompanyPartnership={handleUnlinkCompanyPartnership}
        onDeleteNotification={handleDeleteNotification}
        onDeleteSector={handleDeleteSector}
        onInviteMember={handleInviteMember}
        onInviteCompanyMember={handleInviteCompanyMember}
        onPublishNotification={pushAppFeedbackNotification}
        onNavigatePage={handleNavigatePage}
        onOpenTicket={handleOpenTicket}
        onRefreshDashboardData={refreshDashboardData}
        onRemoveMemberFromCompany={handleRemoveMemberFromCompany}
        onUpdateProfile={handleUpdateProfile}
        onUploadCompanyLogo={handleUploadCompanyLogo}
        onDeleteCompanyLogo={handleDeleteCompanyLogo}
        onSearchPartnershipCompanies={handleSearchPartnershipCompanies}
        onUpdateMemberSectors={handleUpdateMemberSectors}
        onViewNotifications={handleViewedNotifications}
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

  if (isSessionBootstrapping) {
    return <div className="app-shell" />
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
            path={PUBLIC_ROUTE_PATHS.terms}
            element={
              <LegalDocumentPage
                documentSlug="termos-de-uso"
                backHref={authUser ? SECTION_ROUTE_PATHS.tickets : PUBLIC_ROUTE_PATHS.register}
              />
            }
          />
          <Route
            path={PUBLIC_ROUTE_PATHS.privacy}
            element={
              <LegalDocumentPage
                documentSlug="politica-de-privacidade"
                backHref={authUser ? SECTION_ROUTE_PATHS.tickets : PUBLIC_ROUTE_PATHS.register}
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
                  onRequestPasswordReset={handleRequestPasswordReset}
                  onResetPassword={handleResetPassword}
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
            path="/my-data/whatsapp/qrcode"
            element={
              authUser ? <WhatsappQrCodePage /> : <Navigate replace to={PUBLIC_ROUTE_PATHS.login} />
            }
          />
          <Route path="/tickets/:ticketId/attachments/:attachmentId" element={<TicketAttachmentViewer />} />
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
            path={SECTION_ROUTE_PATHS.clientCompanyRegister}
            element={
              authUser ? (
                currentUserRole === 'admin' && currentUser?.companyType === 'RESPONDER' ? (
                  renderDashboardPage('clientCompanyRegister')
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
                    isTeamDataLoading={isTeamDataLoading}
                    navigationGroups={navigationGroups}
                    onNavigatePage={handleNavigatePage}
                    onUpdateMemberSectors={handleUpdateMemberSectors}
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
                onRefreshDashboardData={refreshDashboardData}
                  onRequestTicketTransfer={handleRequestTicketTransfer}
                  onUpdateTicketTitle={handleUpdateTicketTitle}
                  onUpdateTicketClassification={handleUpdateTicketClassification}
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
        <span>
          &copy; 2026{' '}
          <a href="https://www.siaficont.com.br/" target="_blank" rel="noreferrer">
            Siaficont Sistemas
          </a>
          . Todos os direitos reservados.
        </span>
      </footer>
    </div>
  )
}

export default App

function UnknownTenantHostPage({ isLoading = false }) {
  return (
    <main className="tenant-host-page" aria-live="polite">
      <div className="tenant-host-page__card">
        <span className="tenant-host-page__eyebrow">Subdomínio</span>
        <h1>{isLoading ? 'Verificando acesso...' : 'Subdomínio não encontrado'}</h1>
        {!isLoading ? (
          <p>Esse endereço não corresponde a nenhuma empresa ativa da plataforma.</p>
        ) : null}
      </div>
    </main>
  )
}
