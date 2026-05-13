import { useEffect, useState } from 'react'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import {
  getWhatsappQrCodeViewUrl,
  getWhatsappSessionStatus,
  startWhatsappSession,
} from '../../api'
import { dashboardPages } from '../../dashboardData'
import '../Home/Home.css'
import './MyData.css'

function MyData({
  currentUser,
  headerProps,
  isProfileLoading,
  navigationGroups,
  onNavigatePage,
  onDeleteAccount,
  onDeleteCompany,
  onUpdateProfile,
  onSearchPartnershipCompanies,
  onCreateCompanyPartnership,
  onAcceptCompanyPartnership,
  onDeclineCompanyPartnership,
  onUnlinkCompanyPartnership,
  profileError,
  ticketSummary,
  isTicketSummaryLoading,
  companyPartnerships = [],
}) {
  const [deleteTarget, setDeleteTarget] = useState('')
  const [isDeletingAction, setIsDeletingAction] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const [isEditingProfile, setIsEditingProfile] = useState(false)
  const [isSavingProfile, setIsSavingProfile] = useState(false)
  const [profileFeedback, setProfileFeedback] = useState('')
  const [profileFeedbackType, setProfileFeedbackType] = useState('info')
  const [profileFormValues, setProfileFormValues] = useState({
    fullName: '',
    email: '',
    phoneNumber: '',
    companyName: '',
    companyDocument: '',
  })
  const [whatsappStatus, setWhatsappStatus] = useState(null)
  const [whatsappFeedback, setWhatsappFeedback] = useState('')
  const [isWhatsappLoading, setIsWhatsappLoading] = useState(false)
  const [partnershipQuery, setPartnershipQuery] = useState('')
  const [partnershipResults, setPartnershipResults] = useState([])
  const [partnershipFeedback, setPartnershipFeedback] = useState('')
  const [partnershipFeedbackType, setPartnershipFeedbackType] = useState('info')
  const [isSearchingPartnerships, setIsSearchingPartnerships] = useState(false)
  const [isSubmittingPartnership, setIsSubmittingPartnership] = useState(false)
  const [partnershipActionId, setPartnershipActionId] = useState('')
  const [unlinkTarget, setUnlinkTarget] = useState(null)
  const activeContent = dashboardPages.myData
  const isAdmin = currentUser?.roles?.includes('admin')
  const canManageWhatsapp = isAdmin && currentUser?.companyType === 'RESPONDER'
  const canManagePartnerships = isAdmin
  const partnershipSectionTitle =
    currentUser?.companyType === 'REQUESTER' ? 'Solicitar parceria' : 'Adicionar Empresa Cliente'
  const partnershipSectionDescription =
    currentUser?.companyType === 'REQUESTER'
      ? 'Pesquise pelo nome ou CNPJ e envie uma solicitação de parceria para a empresa atendente. Depois do aceite, sua empresa e seus funcionários podem abrir chamados para ela.'
      : 'Pesquise pelo nome ou CNPJ da empresa cliente e envie a solicitação para o administrador. Depois do aceite, as duas empresas podem abrir chamados uma para a outra.'
  const pendingIncomingPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'PENDING' && partnership.canRespond
  )
  const pendingOutgoingPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'PENDING' && partnership.outgoing
  )
  const acceptedPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'ACCEPTED'
  )
  const profileFields = [
    {
      label: 'Nome',
      value: isEditingProfile ? profileFormValues.fullName : currentUser?.fullName || 'Não informado',
      type: 'text',
      editable: true,
      field: 'fullName',
    },
    {
      label: 'Email',
      value: isEditingProfile ? profileFormValues.email : currentUser?.email || 'Não informado',
      type: 'email',
      editable: true,
      field: 'email',
    },
    {
      label: 'Telefone',
      value: isEditingProfile
        ? profileFormValues.phoneNumber
        : formatPhoneNumber(currentUser?.phoneNumber) || 'Não informado',
      type: 'tel',
      editable: true,
      field: 'phoneNumber',
    },
    {
      label: 'Documento',
      value: currentUser?.documentNumber || 'Não informado',
      type: 'text',
    },
    ...(isAdmin
      ? [
          {
            label: 'Nome da empresa',
            value: isEditingProfile
              ? profileFormValues.companyName
              : currentUser?.companyName || 'Obrigatório não informado',
            type: 'text',
            editable: true,
            field: 'companyName',
          },
          {
            label: 'CNPJ da empresa',
            value: isEditingProfile
              ? profileFormValues.companyDocument
              : currentUser?.companyDocument || 'Obrigatório não informado',
            type: 'text',
            editable: true,
            field: 'companyDocument',
          },
        ]
      : []),
    {
      label: 'Status',
      value: currentUser?.status || 'Não informado',
      type: 'text',
    },
  ]

  useEffect(() => {
    setProfileFormValues({
      fullName: currentUser?.fullName || '',
      email: currentUser?.email || '',
      phoneNumber: currentUser?.phoneNumber || '',
      companyName: currentUser?.companyName || '',
      companyDocument: currentUser?.companyDocument || '',
    })
  }, [
    currentUser?.companyDocument,
    currentUser?.companyName,
    currentUser?.email,
    currentUser?.fullName,
    currentUser?.phoneNumber,
  ])

  useEffect(() => {
    let isCancelled = false

    async function loadWhatsappStatus() {
      if (!canManageWhatsapp || !currentUser?.email) {
        setWhatsappStatus(null)
        setWhatsappFeedback('')
        setIsWhatsappLoading(false)
        return
      }

      setIsWhatsappLoading(true)
      setWhatsappFeedback('')

      try {
        const nextStatus = await getWhatsappSessionStatus(currentUser.email)

        if (!isCancelled) {
          setWhatsappStatus(nextStatus)
        }
      } catch (error) {
        if (!isCancelled) {
          setWhatsappStatus(null)
          setWhatsappFeedback(error.message || 'Não foi possível carregar o status do WhatsApp.')
        }
      } finally {
        if (!isCancelled) {
          setIsWhatsappLoading(false)
        }
      }
    }

    loadWhatsappStatus()

    return () => {
      isCancelled = true
    }
  }, [canManageWhatsapp, currentUser?.email])

  async function handleStartWhatsappSession() {
    if (!currentUser?.email) {
      return
    }

    try {
      setIsWhatsappLoading(true)
      setWhatsappFeedback('')

      const currentStatus = await getWhatsappSessionStatus(currentUser.email)
      if (currentStatus?.connected || currentStatus?.status === 'CONNECTED') {
        setWhatsappStatus(currentStatus)
        setWhatsappFeedback(
          'Já existe um número conectado para esta empresa. Para vincular outro, desvincule primeiro o número atual pelo próprio WhatsApp.'
        )
        return
      }

      const nextStatus = await startWhatsappSession({
        adminEmail: currentUser.email,
        waitQrCode: true,
      })
      setWhatsappStatus(nextStatus)

      if (nextStatus.connected || nextStatus.status === 'CONNECTED') {
        setWhatsappFeedback('WhatsApp conectado com sucesso para esta empresa.')
        return
      }

      window.open(getWhatsappQrCodeViewUrl(currentUser.email), '_blank', 'noopener,noreferrer')
      setWhatsappFeedback(
        nextStatus.message || 'Sessão iniciada. Escaneie o QR Code na nova aba para conectar o número da empresa.'
      )
    } catch (error) {
      const normalizedError = (error?.message || '').toLowerCase()
      if (normalizedError.includes('connected') || normalizedError.includes('qrcode ainda não disponível')) {
        setWhatsappFeedback(
          'Já existe um número conectado para esta empresa. Para vincular outro, desvincule primeiro o número atual pelo próprio WhatsApp.'
        )
      } else {
        setWhatsappFeedback(error.message || 'Não foi possível iniciar a sessão do WhatsApp.')
      }
    } finally {
      setIsWhatsappLoading(false)
    }
  }

  async function handleRefreshWhatsappStatus() {
    if (!currentUser?.email) {
      return
    }

    try {
      setIsWhatsappLoading(true)
      setWhatsappFeedback('')
      const nextStatus = await getWhatsappSessionStatus(currentUser.email)
      setWhatsappStatus(nextStatus)
      setWhatsappFeedback(nextStatus.message || 'Status do WhatsApp atualizado.')
    } catch (error) {
      setWhatsappFeedback(error.message || 'Não foi possível atualizar o status do WhatsApp.')
    } finally {
      setIsWhatsappLoading(false)
    }
  }

  async function handleSearchPartnerships(event) {
    event?.preventDefault()

    if (!partnershipQuery.trim()) {
      setPartnershipFeedback('Informe o nome ou o CNPJ da empresa para pesquisar.')
      setPartnershipFeedbackType('info')
      setPartnershipResults([])
      return
    }

    try {
      setIsSearchingPartnerships(true)
      setPartnershipFeedback('')
      setPartnershipFeedbackType('info')
      const results = await onSearchPartnershipCompanies?.(partnershipQuery.trim())
      setPartnershipResults(Array.isArray(results) ? results : [])
      if (!results?.length) {
        setPartnershipFeedback('Nenhuma empresa encontrada para os dados informados.')
        setPartnershipFeedbackType('info')
      }
    } catch (error) {
      setPartnershipResults([])
      setPartnershipFeedback(error.message || 'Não foi possível pesquisar empresas no momento.')
      setPartnershipFeedbackType('error')
    } finally {
      setIsSearchingPartnerships(false)
    }
  }

  async function handleCreatePartnership(targetCompanyId) {
    if (!targetCompanyId) {
      return
    }

    try {
      setIsSubmittingPartnership(true)
      setPartnershipFeedback('')
      setPartnershipFeedbackType('info')
      await onCreateCompanyPartnership?.(targetCompanyId)
      setPartnershipFeedback('Solicitação de parceria enviada com sucesso.')
      setPartnershipFeedbackType('success')
      setPartnershipResults([])
      setPartnershipQuery('')
    } catch (error) {
      setPartnershipFeedback(error.message || 'Não foi possível enviar a solicitação de parceria.')
      setPartnershipFeedbackType('error')
    } finally {
      setIsSubmittingPartnership(false)
    }
  }

  async function handleRespondPartnership(partnershipId, action) {
    if (!partnershipId) {
      return
    }

    try {
      setPartnershipActionId(partnershipId)
      setPartnershipFeedback('')
      setPartnershipFeedbackType('info')
      if (action === 'accept') {
        await onAcceptCompanyPartnership?.(partnershipId)
        setPartnershipFeedback('Parceria aceita com sucesso.')
        setPartnershipFeedbackType('success')
      } else {
        await onDeclineCompanyPartnership?.(partnershipId)
        setPartnershipFeedback('Solicitação de parceria recusada.')
        setPartnershipFeedbackType('info')
      }
    } catch (error) {
      setPartnershipFeedback(error.message || 'Não foi possível responder a solicitação de parceria.')
      setPartnershipFeedbackType('error')
    } finally {
      setPartnershipActionId('')
    }
  }

  async function handleUnlinkPartnership(partnershipId) {
    if (!partnershipId) {
      return
    }

    try {
      setUnlinkTarget(null)
      setPartnershipActionId(partnershipId)
      setPartnershipFeedback('')
      setPartnershipFeedbackType('info')
      await onUnlinkCompanyPartnership?.(partnershipId)
      setPartnershipFeedback('Parceria desfeita com sucesso.')
      setPartnershipFeedbackType('success')
    } catch (error) {
      setPartnershipFeedback(error.message || 'Não foi possível desfazer a parceria.')
      setPartnershipFeedbackType('error')
    } finally {
      setPartnershipActionId('')
    }
  }

  function getWhatsappStatusLabel() {
    if (isWhatsappLoading && !whatsappStatus) {
      return 'Carregando status...'
    }

    if (whatsappStatus?.connected) {
      return 'Conectado'
    }

    if (whatsappStatus?.status === 'QRCODE') {
      return 'Aguardando leitura do QR Code'
    }

    return whatsappStatus?.status || 'Não conectado'
  }

  async function handleConfirmDelete() {
    try {
      setIsDeletingAction(true)
      setDeleteError('')
      if (deleteTarget === 'company') {
        await onDeleteCompany?.()
        return
      }
      await onDeleteAccount?.()
    } catch (error) {
      setDeleteError(error.message)
      setIsDeletingAction(false)
    }
  }

  function handleOpenDeleteModal(target) {
    setDeleteError('')
    setDeleteTarget(target)
  }

  function handleProfileFieldChange(field, value) {
    setProfileFormValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }))
  }

  function handleStartEditingProfile() {
    setProfileFeedback('')
    setProfileFeedbackType('info')
    setIsEditingProfile(true)
  }

  function handleCancelEditingProfile() {
    setProfileFormValues({
      fullName: currentUser?.fullName || '',
      email: currentUser?.email || '',
      phoneNumber: currentUser?.phoneNumber || '',
      companyName: currentUser?.companyName || '',
      companyDocument: currentUser?.companyDocument || '',
    })
    setProfileFeedback('')
    setProfileFeedbackType('info')
    setIsEditingProfile(false)
  }

  async function handleSaveProfile() {
    try {
      setIsSavingProfile(true)
      setProfileFeedback('')
      setProfileFeedbackType('info')

      const updatedProfile = await onUpdateProfile?.({
        fullName: profileFormValues.fullName.trim(),
        email: profileFormValues.email.trim(),
        phoneNumber: normalizePhoneNumber(profileFormValues.phoneNumber),
        companyName: isAdmin ? profileFormValues.companyName.trim() : null,
        companyDocument: isAdmin ? profileFormValues.companyDocument.trim() : null,
      })

      setProfileFormValues({
        fullName: updatedProfile?.fullName || '',
        email: updatedProfile?.email || '',
        phoneNumber: updatedProfile?.phoneNumber || '',
        companyName: updatedProfile?.companyName || '',
        companyDocument: updatedProfile?.companyDocument || '',
      })
      setProfileFeedback('Seus dados foram atualizados com sucesso.')
      setProfileFeedbackType('success')
      setIsEditingProfile(false)
    } catch (error) {
      setProfileFeedback(error.message || 'Não foi possível atualizar seus dados.')
      setProfileFeedbackType('error')
    } finally {
      setIsSavingProfile(false)
    }
  }

  function handleCloseDeleteModal() {
    setDeleteTarget('')
    setDeleteError('')
  }

  function handleOpenUnlinkModal(partnership) {
    setPartnershipFeedback('')
    setPartnershipFeedbackType('info')
    setUnlinkTarget(partnership)
  }

  function handleCloseUnlinkModal() {
    if (partnershipActionId) {
      return
    }
    setUnlinkTarget(null)
  }

  const isDeleteModalOpen = Boolean(deleteTarget)
  const deleteModalTitle = deleteTarget === 'company' ? 'Excluir empresa' : 'Excluir conta'
  const deleteModalDescription =
    deleteTarget === 'company'
      ? [
          'Tem certeza que deseja excluir sua empresa? Essa ação remove os setores, chamados, convites e demais dados vinculados a ela do sistema.',
          'Os funcionários vinculados serão notificados de que a empresa foi excluída e perderão acesso aos setores dessa empresa.',
        ]
      : [
          'Tem certeza que deseja excluir sua conta? Essa ação remove seus dados do sistema e não pode ser desfeita.',
          'Chamados, vínculos e demais registros relacionados a esta conta também serão apagados do banco de dados.',
        ]
  const deleteModalContent = deleteError
    ? [...deleteModalDescription, deleteError]
    : deleteModalDescription
  const unlinkModalTitle = 'Desvincular empresa'
  const unlinkModalDescription = unlinkTarget
    ? [
        `Você está prestes a remover a parceria com ${unlinkTarget.outgoing ? unlinkTarget.targetCompanyName : unlinkTarget.requesterCompanyName}.`,
        'Após confirmar, as duas empresas deixarão de abrir chamados entre si até que uma nova solicitação seja aceita.',
      ]
    : []

  return (
    <main className="home-page">
      <Sidebar
        activeSection="myData"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="myData"
          {...headerProps}
          isTicketSummaryLoading={isTicketSummaryLoading}
          onSectionChange={onNavigatePage}
          ticketSummary={ticketSummary}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--profile">
            <div className="home-profile">
              <h1 className="home-profile__title">{activeContent.contentTitle}</h1>

              {profileError ? <p className="profile-form__feedback">{profileError}</p> : null}
              {profileFeedback ? (
                <p className={`my-data__profile-feedback my-data__profile-feedback--${profileFeedbackType}`}>
                  {profileFeedback}
                </p>
              ) : null}

              <form className="profile-form" onSubmit={(event) => event.preventDefault()}>
                {profileFields.map((field) => (
                  <label className="ticket-field" key={field.label}>
                    <span>{field.label}</span>
                    <div className="ticket-field__control">
                      <input
                        value={isProfileLoading ? 'Carregando...' : field.value}
                        readOnly={!isEditingProfile || !field.editable}
                        onChange={(event) =>
                          field.editable ? handleProfileFieldChange(field.field, event.target.value) : undefined
                        }
                        type={field.type}
                      />
                    </div>
                  </label>
                ))}

                <div className="my-data__actions">
                  {isEditingProfile ? (
                    <>
                      <button
                        className="my-data__edit-button my-data__edit-button--secondary"
                        type="button"
                        onClick={handleCancelEditingProfile}
                        disabled={isSavingProfile}
                      >
                        Cancelar
                      </button>
                      <button
                        className="my-data__edit-button"
                        type="button"
                        onClick={handleSaveProfile}
                        disabled={isSavingProfile}
                      >
                        {isSavingProfile ? 'Salvando...' : 'Salvar alterações'}
                      </button>
                    </>
                  ) : (
                    <button
                      className="my-data__edit-button"
                      type="button"
                      onClick={handleStartEditingProfile}
                    >
                      Editar meus dados
                    </button>
                  )}
                  {isAdmin ? (
                    <button
                      className="my-data__delete-button"
                      type="button"
                      onClick={() => handleOpenDeleteModal('company')}
                    >
                      Excluir empresa
                    </button>
                  ) : null}
                  <button
                    className="my-data__delete-button"
                    type="button"
                    onClick={() => handleOpenDeleteModal('account')}
                  >
                    Excluir conta
                  </button>
                </div>
              </form>

              {canManageWhatsapp ? (
                <section className="my-data__whatsapp-card" aria-labelledby="whatsapp-company-title">
                  <div className="my-data__whatsapp-header">
                    <div>
                      <h2 className="my-data__whatsapp-title" id="whatsapp-company-title">
                        WhatsApp da empresa
                      </h2>
                      <p className="my-data__whatsapp-description">
                        Conecte o número desta empresa para que o atendimento abra chamados por setor.
                      </p>
                    </div>
                    <span
                      className={`my-data__whatsapp-badge ${
                        whatsappStatus?.connected
                          ? 'my-data__whatsapp-badge--connected'
                          : 'my-data__whatsapp-badge--disconnected'
                      }`}
                    >
                      {getWhatsappStatusLabel()}
                    </span>
                  </div>

                  <div className="my-data__whatsapp-grid">
                    <div className="my-data__whatsapp-item">
                      <span className="my-data__whatsapp-label">Empresa</span>
                      <strong>{currentUser?.companyName || 'Não informada'}</strong>
                    </div>
                    <div className="my-data__whatsapp-item">
                      <span className="my-data__whatsapp-label">Sessão</span>
                      <strong>{whatsappStatus?.sessionName || whatsappStatus?.session || 'Ainda não iniciada'}</strong>
                    </div>
                  </div>

                  {whatsappFeedback ? (
                    <p
                      className={`my-data__whatsapp-feedback profile-form__feedback ${
                        whatsappStatus?.connected ? 'profile-form__feedback--success' : ''
                      }`}
                    >
                      {whatsappFeedback}
                    </p>
                  ) : null}

                  <div className="my-data__whatsapp-actions">
                    <button
                      className="my-data__whatsapp-button"
                      type="button"
                      onClick={handleStartWhatsappSession}
                      disabled={isWhatsappLoading}
                    >
                      {isWhatsappLoading ? 'Processando...' : 'Iniciar conexão'}
                    </button>
                    <button
                      className="my-data__whatsapp-button my-data__whatsapp-button--secondary"
                      type="button"
                      onClick={handleRefreshWhatsappStatus}
                      disabled={isWhatsappLoading}
                    >
                      Atualizar status
                    </button>
                  </div>
                </section>
              ) : null}

              {canManagePartnerships ? (
                <section className="my-data__partnership-card" aria-labelledby="company-partnership-title">
                  <div className="my-data__partnership-header">
                    <div>
                      <h2 className="my-data__partnership-title" id="company-partnership-title">
                        {partnershipSectionTitle}
                      </h2>
                      <p className="my-data__partnership-description">{partnershipSectionDescription}</p>
                    </div>
                  </div>

                  <form className="my-data__partnership-search" onSubmit={handleSearchPartnerships}>
                    <label className="ticket-field">
                      <span>Empresa</span>
                      <div className="ticket-field__control">
                        <input
                          type="text"
                          placeholder="Digite o nome ou o CNPJ da empresa"
                          value={partnershipQuery}
                          onChange={(event) => setPartnershipQuery(event.target.value)}
                        />
                      </div>
                    </label>
                    <button
                      className="my-data__partnership-button"
                      type="submit"
                      disabled={isSearchingPartnerships || isSubmittingPartnership}
                    >
                      {isSearchingPartnerships ? 'Pesquisando...' : 'Pesquisar empresa'}
                    </button>
                  </form>

                  {partnershipFeedback ? (
                    <p
                      className={`my-data__partnership-feedback my-data__partnership-feedback--${partnershipFeedbackType}`}
                    >
                      {partnershipFeedback}
                    </p>
                  ) : null}

                  {partnershipResults.length > 0 ? (
                    <div className="my-data__partnership-results">
                      {partnershipResults.map((company) => (
                        <article className="my-data__partnership-item" key={company.companyId}>
                          <div>
                            <strong>{company.companyName}</strong>
                            <p>{company.companyDocument || 'CNPJ não informado'}</p>
                          </div>
                          <button
                            className="my-data__partnership-button my-data__partnership-button--secondary"
                            type="button"
                            onClick={() => handleCreatePartnership(company.companyId)}
                            disabled={isSubmittingPartnership}
                          >
                            {isSubmittingPartnership ? 'Enviando...' : 'Enviar solicitação'}
                          </button>
                        </article>
                      ))}
                    </div>
                  ) : null}

                  <div className="my-data__partnership-groups">
                    <div className="my-data__partnership-group">
                      <h3>Solicitações recebidas</h3>
                      {pendingIncomingPartnerships.length > 0 ? (
                        pendingIncomingPartnerships.map((partnership) => (
                          <article className="my-data__partnership-item" key={partnership.id}>
                            <div>
                              <strong>{partnership.requesterCompanyName}</strong>
                              <p>{partnership.requesterCompanyDocument || 'CNPJ não informado'}</p>
                            </div>
                            <div className="my-data__partnership-actions">
                              <button
                                className="my-data__partnership-button"
                                type="button"
                                onClick={() => handleRespondPartnership(partnership.id, 'accept')}
                                disabled={partnershipActionId === partnership.id}
                              >
                                {partnershipActionId === partnership.id ? 'Processando...' : 'Aceitar'}
                              </button>
                              <button
                                className="my-data__partnership-button my-data__partnership-button--secondary"
                                type="button"
                                onClick={() => handleRespondPartnership(partnership.id, 'decline')}
                                disabled={partnershipActionId === partnership.id}
                              >
                                Recusar
                              </button>
                            </div>
                          </article>
                        ))
                      ) : (
                        <p className="my-data__partnership-empty">Nenhuma solicitação recebida no momento.</p>
                      )}
                    </div>

                    <div className="my-data__partnership-group">
                      <h3>Solicitações enviadas</h3>
                      {pendingOutgoingPartnerships.length > 0 ? (
                        pendingOutgoingPartnerships.map((partnership) => (
                          <article className="my-data__partnership-item" key={partnership.id}>
                            <div className="my-data__partnership-item-main">
                              <strong>{partnership.targetCompanyName}</strong>
                              <p>{partnership.targetCompanyDocument || 'CNPJ não informado'}</p>
                            </div>
                            <div className="my-data__partnership-status-block">
                              <span className="my-data__partnership-status-badge my-data__partnership-status-badge--pending">
                                Solicitação enviada
                              </span>
                              <span className="my-data__partnership-status">Aguardando aceite</span>
                            </div>
                          </article>
                        ))
                      ) : (
                        <p className="my-data__partnership-empty">Nenhuma solicitação enviada pendente.</p>
                      )}
                    </div>

                    <div className="my-data__partnership-group">
                      <h3>Parcerias ativas</h3>
                      {acceptedPartnerships.length > 0 ? (
                        acceptedPartnerships.map((partnership) => {
                          const partnerName = partnership.outgoing
                            ? partnership.targetCompanyName
                            : partnership.requesterCompanyName
                          const partnerDocument = partnership.outgoing
                            ? partnership.targetCompanyDocument
                            : partnership.requesterCompanyDocument

                          return (
                            <article className="my-data__partnership-item" key={partnership.id}>
                              <div className="my-data__partnership-item-main">
                                <strong>{partnerName}</strong>
                                <p>{partnerDocument || 'CNPJ não informado'}</p>
                              </div>
                              <div className="my-data__partnership-actions my-data__partnership-actions--active">
                                <div className="my-data__partnership-status-block">
                                    <span className="my-data__partnership-status-badge">Vinculo ativo</span>
                                  <span className="my-data__partnership-status my-data__partnership-status--accepted">
                                    Cliente e atendente vinculados
                                  </span>
                                </div>
                                <button
                                  className="my-data__partnership-button my-data__partnership-button--danger"
                                  type="button"
                                  onClick={() => handleOpenUnlinkModal(partnership)}
                                  disabled={partnershipActionId === partnership.id}
                                >
                                  {partnershipActionId === partnership.id ? 'Processando...' : 'Desvincular'}
                                </button>
                              </div>
                            </article>
                          )
                        })
                      ) : (
                        <p className="my-data__partnership-empty">Nenhuma parceria aceita até agora.</p>
                      )}
                    </div>
                  </div>
                </section>
              ) : null}
            </div>
          </div>
        </section>

        <ConfirmActionModal
          title={deleteModalTitle}
          description={deleteModalContent}
          isOpen={isDeleteModalOpen}
          isProcessing={isDeletingAction}
          confirmLabel={isDeletingAction ? 'Excluindo...' : 'Confirmar'}
          confirmVariant="danger"
          onCancel={handleCloseDeleteModal}
          onConfirm={handleConfirmDelete}
        />
        <ConfirmActionModal
          title={unlinkModalTitle}
          description={unlinkModalDescription}
          isOpen={Boolean(unlinkTarget)}
          isProcessing={partnershipActionId === unlinkTarget?.id}
          confirmLabel={partnershipActionId === unlinkTarget?.id ? 'Desvinculando...' : 'Desvincular'}
          confirmVariant="danger"
          onCancel={handleCloseUnlinkModal}
          onConfirm={() => handleUnlinkPartnership(unlinkTarget?.id)}
        />
      </div>
    </main>
  )
}

export default MyData

function normalizePhoneNumber(value) {
  const digits = String(value || '').replace(/\D/g, '')
  if (digits.startsWith('55') && digits.length === 13) {
    return digits.slice(2)
  }
  return digits
}

function formatPhoneNumber(value) {
  const digits = normalizePhoneNumber(value)

  if (digits.length === 11) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`
  }

  if (digits.length === 10) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`
  }

  return value || ''
}
