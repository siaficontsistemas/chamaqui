import { useMemo, useState } from 'react'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getRoleLabel, getTeamContent, getTeamMembers } from '../../dashboardData'
import '../Home/Home.css'

function Team({
  currentUser,
  headerProps,
  isTeamDataLoading,
  navigationGroups,
  onAcceptCompanyInvite,
  onInviteMember,
  onInviteCompanyMember,
  onNavigatePage,
  onPublishNotification,
  onAcceptInvite,
  onAcceptTicketTransfer,
  onDeleteNotification,
  onDeleteSector,
  onDeclineCompanyInvite,
  onDeclineInvite,
  onDeclineTicketTransfer,
  onRemoveMemberFromCompany,
  onUpdateMemberSectors,
  receivedInvites = [],
  sectors = [],
  sentInvites = [],
  ticketNotifications = [],
  teamDataError = '',
  teamMembers = [],
  userRole = 'user',
}) {
  const roleLabel = getRoleLabel(userRole)
  const activeContent = getTeamContent(userRole)
  const visibleTeamMembers = getTeamMembers(userRole, teamMembers)
  const companyType = String(currentUser?.companyType || '').toUpperCase()
  const companyUsesSectors = companyType === 'RESPONDER'
  const [activeInviteTab, setActiveInviteTab] = useState(companyUsesSectors ? 'sector' : 'company')
  const [inviteCpf, setInviteCpf] = useState('')
  const [inviteSearch, setInviteSearch] = useState('')
  const [inviteSectorIds, setInviteSectorIds] = useState([])
  const [companyInviteValues, setCompanyInviteValues] = useState({
    fullName: '',
    email: '',
    documentNumber: '',
  })
  const [isSubmittingInvite, setIsSubmittingInvite] = useState(false)
  const [isSubmittingCompanyInvite, setIsSubmittingCompanyInvite] = useState(false)
  const [isInviteOptionsOpen, setIsInviteOptionsOpen] = useState(false)
  const [processingInviteId, setProcessingInviteId] = useState('')
  const [deletingInviteId, setDeletingInviteId] = useState('')
  const [processingSectorId, setProcessingSectorId] = useState('')
  const [processingMemberId, setProcessingMemberId] = useState('')
  const [pendingConfirmation, setPendingConfirmation] = useState(null)
  const [isConfirmingAction, setIsConfirmingAction] = useState(false)
  const sectorNameById = useMemo(
    () =>
      Object.fromEntries(
        sectors.map((sector) => [
          sector.id,
          sector.name,
        ])
      ),
    [sectors]
  )
  const membersWithSector = visibleTeamMembers.filter((member) => (member.sectors ?? []).length > 0).length
  const pendingReceivedInvites = receivedInvites.filter((invite) => invite.status === 'PENDING')
  const pendingInviteSectorCount = pendingReceivedInvites.reduce(
    (total, invite) => total + (invite.sectorIds?.length ?? 0),
    0
  )
  const handledSentInvites = sentInvites.filter((invite) => invite.status !== 'PENDING')
  const employeeNotifications = [...pendingReceivedInvites, ...ticketNotifications].sort(
    (firstNotification, secondNotification) =>
      new Date(
        secondNotification.updatedAt ||
          secondNotification.acceptedAt ||
          secondNotification.expiresAt ||
          secondNotification.createdAt
      ).getTime() -
      new Date(
        firstNotification.updatedAt ||
          firstNotification.acceptedAt ||
          firstNotification.expiresAt ||
          firstNotification.createdAt
      ).getTime()
  )

  const companyName =
    (!companyUsesSectors && userRole === 'user'
      ? visibleTeamMembers.map((member) => member.companyName).filter(Boolean).join(', ')
      : '') ||
    currentUser?.companyName ||
    sectors.find((sector) => sector.companyName)?.companyName ||
    'Empresa não informada'
  const normalizedResponderCompanyOwnerId = String(currentUser?.id || '')
  const normalizedResponderCompanyName = String(currentUser?.companyName || '').trim().toLowerCase()
  const directionColumnTitle =
    companyUsesSectors ? 'Setores' : userRole === 'user' ? 'Empresas cliente' : 'Vinculo'
  const selectableTeamMembers = useMemo(
    () =>
      visibleTeamMembers.filter(
        (member) => member.documentNumber && String(member.documentNumber).replace(/\D/g, '')
      ),
    [visibleTeamMembers]
  )
  const inviteSuggestions = useMemo(
    () =>
      selectableTeamMembers.map((member) => {
        const normalizedDocument = String(member.documentNumber || '').replace(/\D/g, '')
        const formattedDocument = formatCpf(normalizedDocument)

        return {
          ...member,
          normalizedDocument,
          formattedDocument,
          displayLabel: `${member.name} - ${formattedDocument}`,
        }
      }),
    [selectableTeamMembers]
  )
  const filteredInviteSuggestions = useMemo(() => {
    const normalizedSearch = String(inviteSearch || '').trim().toLowerCase()
    const normalizedSearchDigits = String(inviteSearch || '').replace(/\D/g, '')
    const hasTextSearch = normalizedSearch.length > 0
    const hasDigitSearch = normalizedSearchDigits.length > 0

    if (!hasTextSearch && !hasDigitSearch) {
      return inviteSuggestions
    }

    return inviteSuggestions.filter((member) => {
      return (
        (hasTextSearch && member.displayLabel.toLowerCase().includes(normalizedSearch)) ||
        (hasDigitSearch && member.normalizedDocument.includes(normalizedSearchDigits))
      )
    })
  }, [inviteSearch, inviteSuggestions])

  useEffect(() => {
    if (!companyUsesSectors && activeInviteTab !== 'company') {
      setActiveInviteTab('company')
    }
  }, [activeInviteTab, companyUsesSectors])

  async function toggleMemberSector(memberId, sectorId) {
    const currentMember = visibleTeamMembers.find((member) => member.id === memberId)
    const memberSectors = currentMember?.sectors ?? []
    const nextSectors = memberSectors.includes(sectorId)
      ? memberSectors.filter((currentSectorId) => currentSectorId !== sectorId)
      : [...memberSectors, sectorId]

    setFeedbackMessage('')
    await onUpdateMemberSectors(memberId, nextSectors)
  }

  async function handleRemoveMember(member) {
    if (!onRemoveMemberFromCompany) {
      return
    }

    setProcessingMemberId(member.id)

    try {
      await onRemoveMemberFromCompany(member.id)
      publishTeamNotification({
        title: 'Funcionário removido',
        description: `${member.name} foi removido(a) da empresa e recebeu uma notificação.`,
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Remoção não concluída',
        description: error.message,
        status: 'DECLINED',
      })
    } finally {
      setProcessingMemberId('')
    }
  }

  function toggleInviteSector(sectorId) {
    setInviteSectorIds((currentSectorIds) =>
      currentSectorIds.includes(sectorId)
        ? currentSectorIds.filter((currentSectorId) => currentSectorId !== sectorId)
        : [...currentSectorIds, sectorId]
    )
  }

  async function handleInviteSubmit(event) {
    event.preventDefault()

    if (!inviteCpf.trim() || inviteSectorIds.length === 0) {
      return
    }

    setIsSubmittingInvite(true)

    try {
      const createdInvite = await onInviteMember({
        cpf: inviteCpf,
        sectors: inviteSectorIds,
      })
      setInviteCpf('')
      setInviteSearch('')
      setInviteSectorIds([])
      setIsInviteOptionsOpen(false)
      const sectorNames = createdInvite?.sectorNames?.join(', ') || 'setores selecionados'
      const invitedName = createdInvite?.invitedName || 'o funcionário'
      publishTeamNotification({
        title: 'Convite enviado com sucesso',
        description: `${invitedName} já pode responder pela central de notificações nos setores ${sectorNames}.`,
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Convite não enviado',
        description: `O convite não conseguiu ser enviado. Motivo: ${error.message}`,
        status: 'DECLINED',
      })
    } finally {
      setIsSubmittingInvite(false)
    }
  }

  async function handleDeleteSector(sector) {
    if (!onDeleteSector) {
      return
    }

    setProcessingSectorId(sector.id)

    try {
      await onDeleteSector(sector.id)
      publishTeamNotification({
        title: 'Setor excluído',
        description: `O setor ${sector.name} foi excluído com sucesso.`,
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Setor não excluído',
        description: error.message,
        status: 'DECLINED',
      })
    } finally {
      setProcessingSectorId('')
    }
  }

  async function handleEmployeeNotificationDecision(notification, action) {
    setProcessingInviteId(notification.id)

    try {
      if (notification.type === 'company-invite') {
        if (action === 'accept') {
          await onAcceptCompanyInvite(notification.id)
          publishTeamNotification({
            title: 'Convite da empresa aceito',
            description: 'Seu vínculo com a empresa foi atualizado com sucesso.',
          })
          return
        }

        await onDeclineCompanyInvite(notification.id)
        publishTeamNotification({
          title: 'Convite da empresa recusado',
          description: 'Nenhuma alteração foi feita no seu acesso.',
          status: 'DECLINED',
        })
        return
      }

      if (notification.type === 'ticket-transfer') {
        if (action === 'accept') {
          await onAcceptTicketTransfer(notification.id)
          publishTeamNotification({
            title: 'Transferência aceita',
            description: 'O chamado agora aparece na sua lista.',
          })
          return
        }

        await onDeclineTicketTransfer(notification.id)
        publishTeamNotification({
          title: 'Transferência recusada',
          description: 'O chamado continua com o responsável anterior.',
          status: 'DECLINED',
        })
        return
      }

      if (action === 'accept') {
        await onAcceptInvite(notification.id)
        publishTeamNotification({
          title: 'Convite aceito',
          description: 'Sua participação na equipe foi atualizada com sucesso.',
        })
        return
      }

      await onDeclineInvite(notification.id)
      publishTeamNotification({
        title: 'Convite recusado',
        description: 'Nenhuma alteração foi feita na equipe.',
        status: 'DECLINED',
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Ação não concluída',
        description: error.message,
        status: 'DECLINED',
      })
    } finally {
      setProcessingInviteId('')
    }
  }

  async function handleDeleteNotification(notificationOrId) {
    const notificationId =
      typeof notificationOrId === 'object' ? notificationOrId?.id : notificationOrId

    setDeletingInviteId(notificationId)

    try {
      await onDeleteNotification(notificationOrId)
      publishTeamNotification({
        title: 'Notificação excluída',
        description: 'A notificação foi excluída com sucesso.',
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Não foi possível excluir a notificação',
        description: error.message,
        status: 'DECLINED',
      })
    } finally {
      setDeletingInviteId('')
    }
  }

  function openConfirmation(config) {
    setPendingConfirmation(config)
  }

  function closeConfirmation() {
    if (isConfirmingAction) {
      return
    }

    setPendingConfirmation(null)
  }

  async function handleConfirmAction() {
    if (!pendingConfirmation?.onConfirm) {
      return
    }

    setIsConfirmingAction(true)

    try {
      await pendingConfirmation.onConfirm()
      setPendingConfirmation(null)
    } finally {
      setIsConfirmingAction(false)
    }
  }

  function requestRemoveMemberConfirmation(member) {
    openConfirmation({
      title: 'Remover da empresa',
      description: [
        `Tem certeza que deseja remover ${member.name} da empresa?`,
        'O funcionário perderá acesso aos setores da empresa e receberá uma notificação.',
      ],
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => handleRemoveMember(member),
    })
  }

  function requestDeleteNotificationConfirmation(notificationOrId) {
    openConfirmation({
      title: 'Excluir notificação',
      description: 'Tem certeza que deseja excluir esta notificação?',
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => handleDeleteNotification(notificationOrId),
    })
  }

  function requestDeleteSectorConfirmation(sector) {
    openConfirmation({
      title: 'Excluir setor',
      description: [
        `Tem certeza que deseja excluir o setor ${sector.name}?`,
        'Os funcionários serão removidos desse setor e não poderão mais acessá-lo.',
      ],
      confirmLabel: 'Excluir',
      confirmVariant: 'danger',
      onConfirm: () => handleDeleteSector(sector),
    })
  }

  function requestDecisionConfirmation(notification, action) {
    const isAccepting = action === 'accept'

    if (notification.type === 'company-invite') {
      openConfirmation({
        title: isAccepting ? 'Aceitar convite da empresa' : 'Recusar convite da empresa',
        description: isAccepting
          ? `Tem certeza que deseja aceitar o convite da empresa ${notification.companyName}?`
          : `Tem certeza que deseja recusar o convite da empresa ${notification.companyName}?`,
        confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
        confirmVariant: isAccepting ? 'primary' : 'danger',
        onConfirm: () => handleEmployeeNotificationDecision(notification, action),
      })
      return
    }

    if (notification.type === 'ticket-transfer') {
      openConfirmation({
        title: isAccepting ? 'Aceitar transferência' : 'Recusar transferência',
        description: `Tem certeza que deseja ${isAccepting ? 'aceitar' : 'recusar'} a transferência do chamado ${notification.ticketProtocol}?`,
        confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
        confirmVariant: isAccepting ? 'primary' : 'danger',
        onConfirm: () => handleEmployeeNotificationDecision(notification, action),
      })
      return
    }

    const sectorNames = notification.sectorNames?.join(', ') || 'setor não informado'

    openConfirmation({
      title: isAccepting ? 'Aceitar convite' : 'Recusar convite',
      description: isAccepting
        ? `Tem certeza que deseja aceitar o convite para os setores ${sectorNames}?`
        : `Tem certeza que deseja recusar o convite para os setores ${sectorNames}?`,
      confirmLabel: isAccepting ? 'Aceitar' : 'Recusar',
      confirmVariant: isAccepting ? 'primary' : 'danger',
      onConfirm: () => handleEmployeeNotificationDecision(notification, action),
    })
  }

  function getEmployeeNotificationTitle(notification) {
    if (notification.type === 'ticket-assignment') {
      return `Novo chamado ${notification.ticketProtocol}`
    }

    if (notification.type === 'ticket-transfer') {
      return `${notification.senderName} quer transferir o chamado ${notification.ticketProtocol}`
    }

    if (notification.type === 'ticket-reply') {
      return `${notification.requesterName} respondeu ${notification.ticketProtocol}`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return 'Você entrou na empresa'
      }

      if (notification.removalType === 'COMPANY_DELETED') {
        return 'A empresa foi excluída'
      }

      return notification.removalType === 'COMPANY_REMOVED'
        ? 'Você foi removido da empresa'
        : `Você foi removido do setor ${notification.sectorName}`
    }

    if (notification.type === 'calendar-reminder') {
      return `Prazo: ${notification.obligationTitle}`
    }

    if (notification.type === 'company-invite') {
      return `${notification.companyName} convidou você para entrar na empresa`
    }

    return notification.invitedByName
  }

  function getEmployeeNotificationDescription(notification) {
    if (notification.type === 'ticket-assignment') {
      return `${notification.requesterName} abriu "${notification.ticketTitle}" para o setor ${notification.sectorName}.`
    }

    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return `${notification.removedByName} vinculou seu cadastro à empresa ${notification.companyName || 'informada'}.`
      }

      if (notification.removalType === 'COMPANY_DELETED') {
        return `${notification.removedByName} excluiu a empresa ${notification.companyName || 'informada'}. Os setores dessa empresa não existem mais para você.`
      }

      if (notification.removalType === 'COMPANY_REMOVED') {
        return `${notification.removedByName} removeu seu acesso da empresa ${notification.companyName || 'informada'}.`
      }

      return `${notification.removedByName} removeu sua participação do setor ${notification.sectorName}.`
    }

    if (notification.type === 'ticket-transfer') {
      return `O chamado "${notification.ticketTitle}" foi transferido para você por ${notification.senderName}.`
    }

    if (notification.type === 'ticket-reply') {
      return notification.messagePreview
        ? `${notification.requesterName} enviou uma nova mensagem: "${notification.messagePreview}".`
        : `${notification.requesterName} enviou uma nova mensagem neste chamado.`
    }

    if (notification.type === 'calendar-reminder') {
      return `A obrigação "${notification.obligationTitle}" vence em ${formatNotificationDate(notification.dueAt)}.`
    }

    if (notification.type === 'company-invite') {
      return `Ao aceitar, você entra na empresa ${notification.companyName} com o acesso para ${
        notification.requestedRole === 'employee' ? 'responder chamados' : 'criar chamados'
      }.`
    }

    return notification.sectorNames.join(', ')
  }

  function canDeleteNotification(notification) {
    return !(
      (notification.type === 'ticket-transfer' && notification.status === 'PENDING') ||
      (notification.type === 'company-invite' && notification.status === 'PENDING')
    )
  }

  function getNotificationStatusLabel(notification) {
    if (notification.type === 'team-membership-removed') {
      if (notification.removalType === 'COMPANY_JOINED') {
        return 'Novo acesso'
      }

      return 'Removido'
    }

    if (notification.type === 'ticket-assignment') {
      return 'Novo chamado'
    }

    if (notification.type === 'ticket-reply') {
      return 'Nova resposta'
    }

    if (notification.type === 'calendar-reminder') {
      if (notification.status === 'OVERDUE') {
        return 'Atrasado'
      }

      if (notification.status === 'DUE_TODAY') {
        return 'Vence hoje'
      }

      return 'Lembrete'
    }

    if (notification.type === 'company-invite') {
      return 'Pendente'
    }

    return getInviteStatusLabel(notification.status)
  }

  function formatNotificationDate(value) {
    if (!value) {
      return 'data não informada'
    }

    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value))
  }

  function formatMemberStatus(status) {
    if (status === 'ACTIVE') {
      return 'Ativo'
    }

    if (status === 'INACTIVE') {
      return 'Inativo'
    }

    return status
  }

  function getInviteStatusLabel(status) {
    if (status === 'ACCEPTED') {
      return 'Aceito'
    }

    if (status === 'CANCELED') {
      return 'Recusado'
    }

    if (status === 'EXPIRED') {
      return 'Expirado'
    }

    return 'Pendente'
  }

  function handleInviteCpfChange(value) {
    setInviteSearch(value)
    setIsInviteOptionsOpen(true)

    const normalizedValue = String(value || '').trim()
    const normalizedCpf = normalizedValue.replace(/\D/g, '')
    const matchingMember = inviteSuggestions.find(
      (member) =>
        member.displayLabel.toLowerCase() === normalizedValue.toLowerCase() ||
        member.normalizedDocument === normalizedCpf
    )

    if (matchingMember) {
      setInviteCpf(matchingMember.normalizedDocument)
      return
    }

    setInviteCpf(normalizedCpf.length === 11 ? normalizedCpf : '')
  }

  function handleInviteSuggestionSelect(member) {
    setInviteSearch(member.displayLabel)
    setInviteCpf(member.normalizedDocument)
    setIsInviteOptionsOpen(false)
  }

  function publishTeamNotification({ title, description, status = 'ACCEPTED' }) {
    onPublishNotification?.({
      title,
      description,
      status,
    })
  }

  function handleCompanyInviteValueChange(field, value) {
    setCompanyInviteValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }))
  }

  function isResponderCompanyMember(member) {
    const normalizedMemberCompanyOwnerId = String(member?.companyOwnerId || '')
    const normalizedMemberCompanyName = String(member?.companyName || '').trim().toLowerCase()

    if (
      normalizedResponderCompanyOwnerId &&
      normalizedMemberCompanyOwnerId &&
      normalizedResponderCompanyOwnerId === normalizedMemberCompanyOwnerId
    ) {
      return true
    }

    return Boolean(
      normalizedResponderCompanyName &&
      normalizedMemberCompanyName &&
      normalizedResponderCompanyName === normalizedMemberCompanyName
    )
  }

  async function handleCompanyInviteSubmit(event) {
    event.preventDefault()

    if (
      !companyInviteValues.fullName.trim() ||
      !companyInviteValues.email.trim() ||
      !companyInviteValues.documentNumber.trim()
    ) {
      return
    }

    setIsSubmittingCompanyInvite(true)

    try {
      const createdInvite = await onInviteCompanyMember?.({
        fullName: companyInviteValues.fullName.trim(),
        email: companyInviteValues.email.trim().toLowerCase(),
        documentNumber: companyInviteValues.documentNumber.trim(),
      })
      setCompanyInviteValues({
        fullName: '',
        email: '',
        documentNumber: '',
      })
      publishTeamNotification({
        title:
          createdInvite?.deliveryChannel === 'EMAIL'
            ? 'Convite enviado por email'
            : 'Convite enviado na plataforma',
        description:
          createdInvite?.deliveryChannel === 'EMAIL'
            ? `O convite foi enviado para ${createdInvite.invitedEmail}. A pessoa recebeu um link para entrar direto no cadastro da empresa.`
            : `O convite foi enviado por notificação na plataforma para ${createdInvite?.invitedName || 'a pessoa informada'}.`,
      })
    } catch (error) {
      publishTeamNotification({
        title: 'Convite para empresa não enviado',
        description: `Não foi possível convidar a pessoa para a empresa. Motivo: ${error.message}`,
        status: 'DECLINED',
      })
    } finally {
      setIsSubmittingCompanyInvite(false)
    }
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="team"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="team"
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--team">
            <div className="team-view">
              <div className="home-content__header">
                <div className="home-content__heading">
                  <span className="home-content__eyebrow">Colaboração interna</span>
                  <h1>{activeContent.contentTitle}</h1>
                  <p>{activeContent.contentText}</p>
                </div>
              </div>

              {teamDataError ? <p className="team-feedback">{teamDataError}</p> : null}

              <div className="team-view__summary">
                <article className="team-view__summary-card">
                  <span>Total de pessoas</span>
                  <strong>{visibleTeamMembers.length}</strong>
                  <small>Integrantes cadastrados na equipe de trabalho</small>
                </article>
                <article className="team-view__summary-card">
                  <span>Empresa da equipe</span>
                  <strong>{companyName}</strong>
                  <small>
                    {companyUsesSectors
                      ? 'Empresa vinculada aos setores e integrantes dessa equipe'
                      : 'Empresa à qual esses participantes estão vinculados para criar chamados'}
                  </small>
                </article>
                <article className="team-view__summary-card">
                  <span>Seu acesso</span>
                  <strong>{roleLabel}</strong>
                  <small>
                    {userRole === 'admin'
                      ? companyUsesSectors
                        ? 'Pode escolher os setores da equipe e direcionar funcionários'
                        : 'Pode vincular e remover participantes da empresa sem usar setores'
                      : companyUsesSectors
                        ? 'Pode apenas visualizar os setores definidos pelo administrador'
                        : 'Seu acesso depende apenas do vínculo com a empresa, sem setores'}
                  </small>
                </article>
                <article className="team-view__summary-card">
                  <span>
                    {companyUsesSectors
                      ? userRole === 'admin'
                        ? 'Setores criados'
                        : 'Seus setores ativos'
                      : 'Modelo da equipe'}
                  </span>
                  <strong>{companyUsesSectors ? sectors.length : 'Sem setores'}</strong>
                  <small>
                    {companyUsesSectors
                      ? userRole === 'admin'
                        ? 'Setores disponíveis para distribuição na equipe'
                        : 'Setores em que você participa dentro da equipe'
                      : 'Os participantes aceitos ficam soltos na empresa e podem criar chamados sem setor'}
                  </small>
                </article>
                <article className="team-view__summary-card">
                  <span>
                    {userRole === 'admin'
                      ? companyUsesSectors
                        ? 'Pessoas alocadas'
                        : 'Participantes vinculados'
                      : 'Convites pendentes'}
                  </span>
                  <strong>
                    {userRole === 'admin'
                      ? companyUsesSectors
                        ? membersWithSector
                        : visibleTeamMembers.length
                      : pendingReceivedInvites.length}
                  </strong>
                  <small>
                    {userRole === 'admin'
                      ? companyUsesSectors
                        ? 'Integrantes que já foram vinculados a pelo menos um setor'
                        : 'Integrantes aceitos para criar chamados dentro da empresa'
                      : 'Convites que ainda esperam sua resposta para entrar na equipe'}
                  </small>
                </article>
                {userRole !== 'admin' && companyUsesSectors ? (
                  <article className="team-view__summary-card">
                    <span>Setores convidados</span>
                    <strong>{pendingInviteSectorCount}</strong>
                    <small>Setores aguardando sua resposta nos convites pendentes</small>
                  </article>
                ) : null}
              </div>

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">Notificações</span>
                    <h2>
                      {userRole === 'admin' ? 'Retornos dos convites enviados' : 'Suas notificações'}
                    </h2>
                  </div>
                  <span className="home-panel__badge">
                    {userRole === 'admin'
                      ? `${handledSentInvites.length} retorno(s)`
                      : `${employeeNotifications.length} item(ns)`}
                  </span>
                </div>

                {userRole === 'admin' ? (
                  handledSentInvites.length > 0 ? (
                    <div className="team-invite-list">
                      {handledSentInvites.map((invite) => (
                        <article className="team-invite-list__item" key={invite.id}>
                          <div>
                            <strong>{invite.invitedName}</strong>
                            <p>{invite.sectorNames.join(', ')}</p>
                          </div>
                          <div className="team-invite-list__meta">
                            <span className={`team-invite-list__status team-invite-list__status--${invite.status.toLowerCase()}`}>
                              {getInviteStatusLabel(invite.status)}
                            </span>
                            <button
                              className="team-invite-list__icon-button"
                              type="button"
                              onClick={() => requestDeleteNotificationConfirmation(invite.id)}
                              disabled={deletingInviteId === invite.id}
                              aria-label="Excluir notificação"
                            >
                              <TrashIcon />
                            </button>
                          </div>
                        </article>
                      ))}
                    </div>
                  ) : (
                    <span className="team-panel__empty">
                      Nenhum retorno de convite recebido até o momento.
                    </span>
                  )
                ) : employeeNotifications.length > 0 ? (
                  <div className="team-invite-list">
                    {employeeNotifications.map((notification) => (
                      <article className="team-invite-list__item" key={notification.id}>
                        <div>
                          <strong>{getEmployeeNotificationTitle(notification)}</strong>
                          <p>{getEmployeeNotificationDescription(notification)}</p>
                        </div>
                        <div className="team-invite-list__meta">
                          {notification.type === 'received' ||
                          notification.type === 'ticket-transfer' ||
                          notification.type === 'company-invite' ? (
                            <div className="team-invite-list__actions">
                              <button
                                className="team-invite-list__button"
                                type="button"
                                onClick={() => requestDecisionConfirmation(notification, 'accept')}
                                disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              >
                                {processingInviteId === notification.id ? 'Processando...' : 'Aceitar'}
                              </button>
                              <button
                                className="team-invite-list__button team-invite-list__button--ghost"
                                type="button"
                                onClick={() => requestDecisionConfirmation(notification, 'decline')}
                                disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              >
                                Recusar
                              </button>
                            </div>
                          ) : (
                            <span
                              className={`team-invite-list__status team-invite-list__status--${notification.status.toLowerCase()}`}
                            >
                              {getNotificationStatusLabel(notification)}
                            </span>
                          )}
                          {canDeleteNotification(notification) ? (
                            <button
                              className="team-invite-list__icon-button"
                              type="button"
                              onClick={() => requestDeleteNotificationConfirmation(notification)}
                              disabled={processingInviteId === notification.id || deletingInviteId === notification.id}
                              aria-label="Excluir notificação"
                            >
                              <TrashIcon />
                            </button>
                          ) : null}
                        </div>
                      </article>
                    ))}
                  </div>
                ) : (
                  <span className="team-panel__empty">
                    Nenhuma notificação disponível para sua conta neste momento.
                  </span>
                )}
              </div>

              {userRole === 'admin' ? (
                <div className="team-invite">
                  <div className="team-invite__header">
                    <div>
                      <span className="home-panel__eyebrow">Convite de funcionário</span>
                      <h2>Adicionar novo integrante</h2>
                    </div>
                    {companyUsesSectors ? (
                      <div className="team-invite__tabs" role="tablist" aria-label="Tipos de convite">
                        <button
                          className={`team-invite__tab${activeInviteTab === 'sector' ? ' is-active' : ''}`}
                          type="button"
                          onClick={() => setActiveInviteTab('sector')}
                        >
                          Convidar para setores
                        </button>
                        <button
                          className={`team-invite__tab${activeInviteTab === 'company' ? ' is-active' : ''}`}
                          type="button"
                          onClick={() => setActiveInviteTab('company')}
                        >
                          Adicionar à empresa
                        </button>
                      </div>
                    ) : null}
                  </div>
                  {companyUsesSectors && activeInviteTab === 'sector' ? (
                    <form onSubmit={handleInviteSubmit}>
                      <label className="ticket-field ticket-field--combobox">
                        <span>CPF do funcionário</span>
                        <div className="ticket-field__control">
                          <input
                            placeholder="Digite o CPF ou o nome do funcionário"
                            type="text"
                            value={inviteSearch}
                            onChange={(event) => handleInviteCpfChange(event.target.value)}
                            onFocus={() => {
                              if (inviteSuggestions.length > 0) {
                                setIsInviteOptionsOpen(true)
                              }
                            }}
                            onBlur={() => {
                              window.setTimeout(() => setIsInviteOptionsOpen(false), 150)
                            }}
                          />
                        </div>
                        {isInviteOptionsOpen ? (
                          <div className="ticket-field__options" role="listbox" aria-label="Funcionários da empresa">
                            {filteredInviteSuggestions.length > 0 ? (
                              filteredInviteSuggestions.map((member) => (
                                <button
                                  className={`ticket-field__option${
                                    inviteCpf === member.normalizedDocument ? ' is-active' : ''
                                  }`}
                                  key={member.id}
                                  type="button"
                                  onMouseDown={(event) => event.preventDefault()}
                                  onClick={() => handleInviteSuggestionSelect(member)}
                                >
                                  {member.displayLabel}
                                </button>
                              ))
                            ) : (
                              <span className="ticket-field__option ticket-field__option--empty">
                                Nenhum CPF encontrado na equipe para esse filtro
                              </span>
                            )}
                          </div>
                        ) : null}
                      </label>

                      <label className="ticket-field">
                        <span>Setor ou setores do funcionário</span>
                        {sectors.length > 0 ? (
                          <div className="team-sectors">
                            {sectors.map((sector) => {
                              const isSelected = inviteSectorIds.includes(sector.id)

                              return (
                                <button
                                  className={`team-sector-chip${isSelected ? ' is-active' : ''}`}
                                  key={`invite-${sector.id}`}
                                  type="button"
                                  onClick={() => toggleInviteSector(sector.id)}
                                >
                                  {sector.name}
                                </button>
                              )
                            })}
                          </div>
                        ) : (
                          <span className="team-panel__empty">
                            Crie pelo menos um setor antes de convidar um novo integrante.
                          </span>
                        )}
                      </label>

                      <div className="team-invite__footer team-invite__footer--company">
                        <span>
                          {inviteSectorIds.length > 0
                            ? `${inviteSectorIds.length} setor(es) selecionado(s) para esse funcionário.`
                            : 'Selecione ao menos um setor para o funcionário convidado.'}
                        </span>
                        <button
                          className="team-invite__button"
                          type="submit"
                          disabled={
                            isSubmittingInvite ||
                            !inviteCpf.trim() || inviteSectorIds.length === 0
                          }
                        >
                          {isSubmittingInvite ? 'Enviando...' : 'Convidar funcionário'}
                        </button>
                      </div>
                    </form>
                  ) : (
                    <form onSubmit={handleCompanyInviteSubmit}>
                      <div className="ticket-form__grid">
                        <label className="ticket-field">
                          <span>Nome da pessoa</span>
                          <div className="ticket-field__control">
                            <input
                              placeholder="Digite o nome completo"
                              type="text"
                              value={companyInviteValues.fullName}
                              onChange={(event) =>
                                handleCompanyInviteValueChange('fullName', event.target.value)
                              }
                            />
                          </div>
                        </label>

                        <label className="ticket-field">
                          <span>Email</span>
                          <div className="ticket-field__control">
                            <input
                              placeholder="Digite o email da pessoa"
                              type="email"
                              value={companyInviteValues.email}
                              onChange={(event) =>
                                handleCompanyInviteValueChange('email', event.target.value)
                              }
                            />
                          </div>
                        </label>
                      </div>

                      <label className="ticket-field">
                        <span>CPF</span>
                        <div className="ticket-field__control">
                          <input
                            placeholder="Digite o CPF da pessoa"
                            type="text"
                            value={companyInviteValues.documentNumber}
                            onChange={(event) =>
                              handleCompanyInviteValueChange('documentNumber', event.target.value)
                            }
                          />
                        </div>
                      </label>

                      <div className="team-invite__footer">
                        <span>
                          {companyUsesSectors
                            ? 'Se o CPF já existir na plataforma, a pessoa recebe a notificação. Se ainda não existir, enviamos um email com link direto para o cadastro.'
                            : 'A pessoa ficará vinculada diretamente à empresa para criar chamados, sem precisar ser distribuída em setores.'}
                        </span>
                        <button
                          className="team-invite__button"
                          type="submit"
                          disabled={
                            isSubmittingCompanyInvite ||
                            !companyInviteValues.fullName.trim() ||
                            !companyInviteValues.email.trim() ||
                            !companyInviteValues.documentNumber.trim()
                          }
                        >
                          {isSubmittingCompanyInvite ? 'Enviando...' : 'Adicionar à empresa'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              ) : null}

              {companyUsesSectors ? (
                <div className="team-panel">
                  <div className="team-panel__header">
                    <div>
                      <span className="home-panel__eyebrow">Setores da equipe</span>
                      <h2>
                        {userRole === 'admin'
                          ? 'Setores cadastrados pelo administrador'
                          : 'Setores em que você participa'}
                      </h2>
                    </div>
                    {userRole === 'admin' ? (
                      <button
                        className="home-content__button home-content__button--ghost"
                        type="button"
                        onClick={() => onNavigatePage('createSector')}
                      >
                        Criar setor
                      </button>
                    ) : null}
                  </div>

                  {sectors.length > 0 ? (
                    <div className="team-sectors">
                      {sectors.map((sector) => (
                        <div className="team-sectors__item is-active" key={sector.id}>
                          <span>{sector.name}</span>
                          <strong>{sector.description}</strong>
                          {userRole === 'admin' ? (
                            <button
                              className="team-panel__action-button team-panel__action-button--danger"
                              type="button"
                              onClick={() => requestDeleteSectorConfirmation(sector)}
                              disabled={isTeamDataLoading || processingSectorId === sector.id}
                            >
                              {processingSectorId === sector.id ? 'Excluindo...' : 'Excluir setor'}
                            </button>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <span className="team-panel__empty">
                      {userRole === 'admin'
                        ? 'Nenhum setor criado. Use a tela de criação de setores para começar.'
                        : 'Você ainda não foi vinculado a nenhum setor.'}
                    </span>
                  )}
                </div>
              ) : null}

              <div className="team-panel">
                <div className="team-panel__header">
                  <div>
                    <span className="home-panel__eyebrow">
                      {companyUsesSectors ? 'Direcionamento da equipe' : 'Participantes da empresa'}
                    </span>
                    <h2>
                      {companyUsesSectors
                        ? userRole === 'admin'
                          ? 'Definir setores dos funcionários'
                          : 'Funcionários por setor'
                        : userRole === 'admin'
                          ? 'Participantes vinculados à empresa'
                          : 'Participantes da empresa'}
                    </h2>
                  </div>
                  <span className="home-panel__badge">
                    {companyUsesSectors ? 'Acesso compartilhado' : 'Sem setores'}
                  </span>
                </div>

                <div className="team-panel__table">
                  {visibleTeamMembers.length > 0 ? (
                    <>
                      <div className={`team-panel__head${userRole === 'admin' ? ' team-panel__head--admin' : ''}`}>
                        <span>Nome</span>
                        <span>Função</span>
                        <span>{directionColumnTitle}</span>
                        <span>Status</span>
                        {userRole === 'admin' ? <span>Ações</span> : null}
                      </div>

                      {visibleTeamMembers.map((member) => (
                        <div
                          className={`team-panel__row${userRole === 'admin' ? ' team-panel__row--admin' : ''}`}
                          key={member.id}
                        >
                          <span>{member.name}</span>
                          <span>{member.role}</span>
                          <span className="team-panel__sectors">
                            {companyUsesSectors ? (
                              isResponderCompanyMember(member) ? (
                              <>
                                {sectors.map((sector) => {
                                  const isAssigned = (member.sectors ?? []).includes(sector.id)

                                  return (
                                    <button
                                      className={`team-sector-chip${isAssigned ? ' is-active' : ''}`}
                                      key={`${member.id}-${sector.id}`}
                                      type="button"
                                      onClick={
                                        userRole === 'admin'
                                          ? () => toggleMemberSector(member.id, sector.id)
                                          : undefined
                                      }
                                      disabled={userRole !== 'admin' || sectors.length === 0 || isTeamDataLoading}
                                    >
                                      {sector.name}
                                    </button>
                                  )
                                })}
                                {(member.sectors ?? []).length > 0 && sectors.length === 0 ? (
                                  <span className="team-panel__empty">
                                    {member.sectors
                                      .map((sectorId) => sectorNameById[sectorId])
                                      .filter(Boolean)
                                      .join(', ')}
                                  </span>
                                ) : null}
                                {sectors.length === 0 ? (
                                  <span className="team-panel__empty">Nenhum setor definido</span>
                                ) : null}
                              </>
                              ) : (
                                <span className="team-panel__empty">
                                  {member.companyName || 'Funcionário vinculado a empresa cliente'}
                                </span>
                              )
                            ) : (
                              <span className="team-panel__empty">
                                {member.companyName || 'Acesso livre na empresa, sem setores'}
                              </span>
                            )}
                          </span>
                          <span className="team-panel__status">{formatMemberStatus(member.status)}</span>
                          {userRole === 'admin' ? (
                            <span className="team-panel__actions">
                              <button
                                className="team-panel__action-button team-panel__action-button--danger"
                                type="button"
                                onClick={() => requestRemoveMemberConfirmation(member)}
                                disabled={isTeamDataLoading || processingMemberId === member.id}
                              >
                                {processingMemberId === member.id ? 'Removendo...' : 'Remover da empresa'}
                              </button>
                            </span>
                          ) : null}
                        </div>
                      ))}
                    </>
                  ) : (
                    <span className="team-panel__empty">
                      {userRole === 'admin'
                        ? 'Nenhum funcionário cadastrado na equipe até o momento.'
                        : 'Nenhum funcionário disponível na equipe até o momento.'}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <ConfirmActionModal
        isOpen={Boolean(pendingConfirmation)}
        title={pendingConfirmation?.title}
        description={pendingConfirmation?.description}
        confirmLabel={pendingConfirmation?.confirmLabel}
        confirmVariant={pendingConfirmation?.confirmVariant}
        onCancel={closeConfirmation}
        onConfirm={handleConfirmAction}
        isProcessing={isConfirmingAction}
      />
    </main>
  )
}

export default Team

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 7h16m-10 4v5m4-5v5M9 4h6l1 2H8l1-2Zm1 16h4a2 2 0 0 0 2-2V7H8v11a2 2 0 0 0 2 2Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function formatCpf(value) {
  const digits = String(value || '').replace(/\D/g, '')

  if (digits.length !== 11) {
    return value || ''
  }

  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}
