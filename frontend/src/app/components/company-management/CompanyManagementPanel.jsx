import { useEffect, useState } from 'react'
import {
  getWhatsappQrCode,
  getWhatsappQrCodeViewUrl,
  getWhatsappSessionStatus,
  startWhatsappSession,
} from '../../api'
import { useTenantBranding } from '../../context/TenantBrandingContext'
import '../../pages/MyData/MyData.css'
import '../../pages/ClientCompanyRegister/ClientCompanyRegister.css'

export default function CompanyManagementPanel({
  currentUser,
  companyPartnerships = [],
  onAcceptCompanyPartnership,
  onCreateCompanyPartnership,
  onDeclineCompanyPartnership,
  onDeleteCompanyLogo,
  onCreateClientCompany,
  onLookupClientCompany,
  onUnlinkCompanyPartnership,
  onRemoveMemberFromCompany,
  onSearchPartnershipCompanies,
  onUploadCompanyLogo,
  teamMembers = [],
}) {
  const isAdmin = currentUser?.roles?.includes('admin')
  const canManageWhatsapp = isAdmin && currentUser?.companyType === 'RESPONDER'
  const canManagePartnerships = isAdmin
  const canManageClientCompanies = isAdmin && currentUser?.companyType === 'RESPONDER'
  const { companyLogoUrl, setBranding: setTenantBranding } = useTenantBranding()
  const [selectedLogoFile, setSelectedLogoFile] = useState(null)
  const [isUploadingLogo, setIsUploadingLogo] = useState(false)
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
  const [expandedPartnershipIds, setExpandedPartnershipIds] = useState([])
  const [clientCompanyFormValues, setClientCompanyFormValues] = useState({
    companyName: '',
    companyDocument: '',
    companyEmail: '',
    companyPhoneNumber: '',
  })
  const [clientCompanyFeedback, setClientCompanyFeedback] = useState('')
  const [clientCompanyFeedbackType, setClientCompanyFeedbackType] = useState('info')
  const [createdClientCompany, setCreatedClientCompany] = useState(null)
  const [isSubmittingClientCompany, setIsSubmittingClientCompany] = useState(false)

  const pendingIncomingPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'PENDING' && partnership.canRespond
  )
  const pendingOutgoingPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'PENDING' && partnership.outgoing
  )
  const acceptedPartnerships = companyPartnerships.filter(
    (partnership) => partnership.status === 'ACCEPTED'
  )
  const acceptedPartnershipsWithEmployees = acceptedPartnerships.map((partnership) => {
    const partnerCompanyId = partnership.outgoing
      ? partnership.targetCompanyId
      : partnership.requesterCompanyId

    return {
      ...partnership,
      partnerCompanyId,
      employees: teamMembers.filter((member) => member.companyOwnerId === partnerCompanyId),
    }
  })

  useEffect(() => {
    let cancelled = false

    async function loadWhatsappStatus() {
      if (!canManageWhatsapp || !currentUser?.email) {
        setWhatsappStatus(null)
        return
      }

      setIsWhatsappLoading(true)
      try {
        const status = await getWhatsappSessionStatus(currentUser.email)
        if (!cancelled) setWhatsappStatus(status)
      } catch (error) {
        if (!cancelled) setWhatsappFeedback(error.message || 'Não foi possível carregar o status do WhatsApp.')
      } finally {
        if (!cancelled) setIsWhatsappLoading(false)
      }
    }

    loadWhatsappStatus()
    return () => {
      cancelled = true
    }
  }, [canManageWhatsapp, currentUser?.email])

  async function handleStartWhatsappSession() {
    if (!currentUser?.email) return

    try {
      setIsWhatsappLoading(true)
      setWhatsappFeedback('')
      const currentStatus = await getWhatsappSessionStatus(currentUser.email)
      if (currentStatus?.connected || currentStatus?.status === 'CONNECTED') {
        setWhatsappStatus(currentStatus)
        setWhatsappFeedback('Já existe um número conectado para esta empresa.')
        return
      }

      const nextStatus = await startWhatsappSession({ adminEmail: currentUser.email, waitQrCode: true })
      setWhatsappStatus(nextStatus)
      if (nextStatus.connected || nextStatus.status === 'CONNECTED') {
        setWhatsappFeedback('WhatsApp conectado com sucesso para esta empresa.')
        return
      }

      await getWhatsappQrCode()
      window.open(getWhatsappQrCodeViewUrl(), '_blank', 'noopener,noreferrer')
      setWhatsappFeedback(nextStatus.message || 'Escaneie o QR Code na nova aba para conectar o número.')
    } catch (error) {
      setWhatsappFeedback(error.message || 'Não foi possível iniciar a sessão do WhatsApp.')
    } finally {
      setIsWhatsappLoading(false)
    }
  }

  async function handleRefreshWhatsappStatus() {
    if (!currentUser?.email) return
    try {
      setIsWhatsappLoading(true)
      const status = await getWhatsappSessionStatus(currentUser.email)
      setWhatsappStatus(status)
      setWhatsappFeedback(status.message || 'Status do WhatsApp atualizado.')
    } catch (error) {
      setWhatsappFeedback(error.message || 'Não foi possível atualizar o status do WhatsApp.')
    } finally {
      setIsWhatsappLoading(false)
    }
  }

  async function handleSearchPartnerships(event) {
    event.preventDefault()
    if (!partnershipQuery.trim()) {
      setPartnershipFeedback('Informe o nome ou o CNPJ da empresa para pesquisar.')
      setPartnershipFeedbackType('info')
      return
    }

    try {
      setIsSearchingPartnerships(true)
      setPartnershipFeedback('')
      const results = await onSearchPartnershipCompanies?.(partnershipQuery.trim())
      setPartnershipResults(Array.isArray(results) ? results : [])
      if (!results?.length) setPartnershipFeedback('Nenhuma empresa encontrada para os dados informados.')
    } catch (error) {
      setPartnershipResults([])
      setPartnershipFeedback(error.message || 'Não foi possível pesquisar empresas no momento.')
      setPartnershipFeedbackType('error')
    } finally {
      setIsSearchingPartnerships(false)
    }
  }

  async function handleCreatePartnership(companyId) {
    try {
      setIsSubmittingPartnership(true)
      await onCreateCompanyPartnership?.(companyId)
      setPartnershipResults([])
      setPartnershipQuery('')
      setPartnershipFeedback('Solicitação de parceria enviada com sucesso.')
      setPartnershipFeedbackType('success')
    } catch (error) {
      setPartnershipFeedback(error.message || 'Não foi possível enviar a solicitação de parceria.')
      setPartnershipFeedbackType('error')
    } finally {
      setIsSubmittingPartnership(false)
    }
  }

  async function handlePartnershipDecision(partnershipId, action) {
    try {
      setPartnershipActionId(partnershipId)
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
      setPartnershipFeedback(error.message || 'Não foi possível processar a parceria.')
      setPartnershipFeedbackType('error')
    } finally {
      setPartnershipActionId('')
    }
  }

  async function handleUnlinkPartnership(partnershipId) {
    try {
      setPartnershipActionId(partnershipId)
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

  async function handleRemoveClientEmployee(memberId, companyName) {
    try {
      setPartnershipActionId(memberId)
      await onRemoveMemberFromCompany?.(memberId)
      setPartnershipFeedback(`Funcionário removido da empresa ${companyName} com sucesso.`)
      setPartnershipFeedbackType('success')
    } catch (error) {
      setPartnershipFeedback(error.message || 'Não foi possível remover o funcionário da empresa cliente.')
      setPartnershipFeedbackType('error')
    } finally {
      setPartnershipActionId('')
    }
  }

  async function handleSaveLogo() {
    if (!selectedLogoFile) return
    try {
      setIsUploadingLogo(true)
      const branding = await onUploadCompanyLogo?.(selectedLogoFile)
      setTenantBranding((current) => ({
        ...(current || {}),
        tenantResolved: true,
        companyName: branding?.companyName || currentUser?.companyName || '',
        logoUrl: branding?.logoUrl || '',
        loginLogoUrl: branding?.loginLogoUrl || '',
      }))
      setSelectedLogoFile(null)
    } finally {
      setIsUploadingLogo(false)
    }
  }

  async function handleDeleteLogo() {
    try {
      setIsUploadingLogo(true)
      const branding = await onDeleteCompanyLogo?.()
      setTenantBranding((current) => ({ ...(current || {}), ...branding, tenantResolved: true }))
      setSelectedLogoFile(null)
    } finally {
      setIsUploadingLogo(false)
    }
  }

  async function handleCreateClientCompany(event) {
    event.preventDefault()

    if (!clientCompanyFormValues.companyName.trim() || !clientCompanyFormValues.companyDocument.trim()) {
      setClientCompanyFeedbackType('error')
      setClientCompanyFeedback('Preencha o nome e o CNPJ da empresa cliente para concluir o cadastro.')
      return
    }

    try {
      setIsSubmittingClientCompany(true)
      setClientCompanyFeedback('')
      const companyLookup = await onLookupClientCompany?.(clientCompanyFormValues.companyDocument.trim())

      if (companyLookup?.status === 'ALREADY_CLIENT' || companyLookup?.status === 'PENDING_LINK') {
        setClientCompanyFeedbackType('error')
        setClientCompanyFeedback(companyLookup.message || 'A empresa desse CNPJ já está vinculada à sua operação.')
        return
      }

      if (companyLookup?.status === 'UNAVAILABLE') {
        setClientCompanyFeedbackType('error')
        setClientCompanyFeedback(companyLookup.message || 'Esse CNPJ já está vinculado a um cadastro que não pode ser usado como cliente.')
        return
      }

      const response = await onCreateClientCompany?.({
        companyName: clientCompanyFormValues.companyName.trim(),
        companyDocument: clientCompanyFormValues.companyDocument.trim(),
        companyEmail: clientCompanyFormValues.companyEmail.trim().toLowerCase(),
        companyPhoneNumber: clientCompanyFormValues.companyPhoneNumber.trim(),
      })

      setCreatedClientCompany(response)
      setClientCompanyFormValues({ companyName: '', companyDocument: '', companyEmail: '', companyPhoneNumber: '' })
      setClientCompanyFeedbackType('success')
      setClientCompanyFeedback('Empresa cliente cadastrada com sucesso.')
    } catch (error) {
      setClientCompanyFeedbackType('error')
      setClientCompanyFeedback(error.message || 'Não foi possível cadastrar a empresa cliente.')
    } finally {
      setIsSubmittingClientCompany(false)
    }
  }

  function whatsappStatusLabel() {
    if (isWhatsappLoading && !whatsappStatus) return 'Carregando status...'
    if (whatsappStatus?.connected) return 'Conectado'
    if (whatsappStatus?.status === 'QRCODE') return 'Aguardando leitura do QR Code'
    return whatsappStatus?.status || 'Não conectado'
  }

  if (!isAdmin) return null

  return (
    <div className="company-management-panel">
      {currentUser?.companyType === 'RESPONDER' ? (
        <>
          <section className="my-data__company-logo-card" aria-labelledby="team-company-logo-title">
            <h2 className="my-data__company-logo-title" id="team-company-logo-title">Logo da empresa</h2>
            <p className="my-data__company-logo-description">Gerencie a logo exibida no login e no cadastro da empresa.</p>
            <div className="my-data__company-logo-content">
              <div className="my-data__company-logo-preview">
                {companyLogoUrl ? <img className="my-data__company-logo-image" src={companyLogoUrl} alt="Logo da empresa" /> : <div className="my-data__company-logo-placeholder">Nenhuma logo enviada ainda</div>}
              </div>
              <div className="my-data__company-logo-form">
                <label className="ticket-field"><span>Arquivo da logo</span><div className="ticket-field__control"><input type="file" accept="image/png,image/jpeg,image/jpg,image/webp,image/gif" onChange={(event) => setSelectedLogoFile(event.target.files?.[0] || null)} /></div></label>
                {selectedLogoFile ? <p className="my-data__company-logo-file">Arquivo selecionado: {selectedLogoFile.name}</p> : null}
                <div className="my-data__company-logo-actions">
                  <button className="my-data__edit-button" type="button" onClick={handleSaveLogo} disabled={isUploadingLogo}>{isUploadingLogo ? 'Enviando...' : 'Salvar logo'}</button>
                  <button className="my-data__delete-button" type="button" onClick={handleDeleteLogo} disabled={isUploadingLogo || !companyLogoUrl}>Excluir logo</button>
                </div>
              </div>
            </div>
          </section>

          <section className="my-data__whatsapp-card" aria-labelledby="team-whatsapp-title">
            <div className="my-data__whatsapp-header"><div><h2 className="my-data__whatsapp-title" id="team-whatsapp-title">WhatsApp da empresa</h2><p className="my-data__whatsapp-description">Conecte o número usado para abrir chamados por setor.</p></div><span className={`my-data__whatsapp-badge ${whatsappStatus?.connected ? 'my-data__whatsapp-badge--connected' : 'my-data__whatsapp-badge--disconnected'}`}>{whatsappStatusLabel()}</span></div>
            <div className="my-data__whatsapp-grid"><div className="my-data__whatsapp-item"><span className="my-data__whatsapp-label">Empresa</span><strong>{currentUser?.companyName || 'Não informada'}</strong></div><div className="my-data__whatsapp-item"><span className="my-data__whatsapp-label">Sessão</span><strong>{whatsappStatus?.sessionName || whatsappStatus?.session || 'Ainda não iniciada'}</strong></div></div>
            {whatsappFeedback ? <p className="my-data__whatsapp-feedback profile-form__feedback">{whatsappFeedback}</p> : null}
            <div className="my-data__whatsapp-actions"><button className="my-data__whatsapp-button" type="button" onClick={handleStartWhatsappSession} disabled={isWhatsappLoading}>{isWhatsappLoading ? 'Processando...' : 'Iniciar conexão'}</button><button className="my-data__whatsapp-button my-data__whatsapp-button--secondary" type="button" onClick={handleRefreshWhatsappStatus} disabled={isWhatsappLoading}>Atualizar status</button></div>
          </section>
        </>
      ) : null}

      {canManageClientCompanies ? (
        <>
          <form className="team-invite client-company-register" onSubmit={handleCreateClientCompany}>
            <div className="team-invite__header">
              <div>
                <span className="home-panel__eyebrow">Empresas clientes</span>
                <h2>Adicionar empresa cliente</h2>
                <p>Cadastre uma empresa cliente dentro da sua operação de atendimento.</p>
              </div>
            </div>

            {clientCompanyFeedback ? (
              <p className={`profile-form__feedback${clientCompanyFeedbackType === 'success' ? ' profile-form__feedback--success' : ''}`}>
                {clientCompanyFeedback}
              </p>
            ) : null}

            <div className="ticket-form__grid">
              <label className="ticket-field"><span>Nome da empresa cliente</span><div className="ticket-field__control"><input type="text" placeholder="Digite o nome da empresa cliente" value={clientCompanyFormValues.companyName} disabled={isSubmittingClientCompany} onChange={(event) => setClientCompanyFormValues((current) => ({ ...current, companyName: event.target.value }))} /></div></label>
              <label className="ticket-field"><span>CNPJ da empresa cliente</span><div className="ticket-field__control"><input type="text" placeholder="Digite o CNPJ da empresa cliente" value={clientCompanyFormValues.companyDocument} disabled={isSubmittingClientCompany} onChange={(event) => setClientCompanyFormValues((current) => ({ ...current, companyDocument: event.target.value }))} /></div></label>
            </div>
            <div className="ticket-form__grid">
              <label className="ticket-field"><span>Email da empresa cliente</span><div className="ticket-field__control"><input type="email" placeholder="Digite o email da empresa cliente" value={clientCompanyFormValues.companyEmail} disabled={isSubmittingClientCompany} onChange={(event) => setClientCompanyFormValues((current) => ({ ...current, companyEmail: event.target.value }))} /></div></label>
              <label className="ticket-field"><span>Telefone da empresa cliente</span><div className="ticket-field__control"><input type="text" placeholder="Digite o telefone da empresa cliente" value={clientCompanyFormValues.companyPhoneNumber} disabled={isSubmittingClientCompany} onChange={(event) => setClientCompanyFormValues((current) => ({ ...current, companyPhoneNumber: event.target.value }))} /></div></label>
            </div>
            <div className="team-invite__footer">
              <span>Os funcionários dessa empresa poderão se cadastrar e você fará a aprovação e a gestão deles.</span>
              <button className="team-invite__button" type="submit" disabled={isSubmittingClientCompany}>{isSubmittingClientCompany ? 'Cadastrando...' : 'Cadastrar empresa cliente'}</button>
            </div>
          </form>

          {createdClientCompany ? (
            <section className="client-company-register__result">
              <div className="team-panel__header"><div><span className="home-panel__eyebrow">Cadastro concluído</span><h2>Resumo da nova empresa cliente</h2></div></div>
              <div className="client-company-register__result-grid">
                <article className="client-company-register__result-card"><span>Empresa</span><strong>{createdClientCompany.companyName}</strong><small>{createdClientCompany.companyDocument}</small></article>
                <article className="client-company-register__result-card"><span>Contato</span><strong>{createdClientCompany.companyEmail || 'Email não informado'}</strong><small>{createdClientCompany.companyPhoneNumber || 'Telefone não informado'}</small></article>
                <article className="client-company-register__result-card"><span>Subdomínio de acesso</span><strong>{createdClientCompany.subdomain || 'Mesmo subdomínio da provedora'}</strong><small>Os funcionários acessam pelo mesmo subdomínio da empresa provedora.</small></article>
              </div>
            </section>
          ) : null}
        </>
      ) : null}

      {canManagePartnerships ? (
        <section className="my-data__partnership-card" aria-labelledby="team-partnership-title">
          <h2 className="my-data__partnership-title" id="team-partnership-title">Parcerias da empresa</h2>
          <form className="my-data__partnership-search" onSubmit={handleSearchPartnerships}><label className="ticket-field"><span>Empresa</span><div className="ticket-field__control"><input type="text" placeholder="Digite o nome ou o CNPJ" value={partnershipQuery} onChange={(event) => setPartnershipQuery(event.target.value)} /></div></label><button className="my-data__partnership-button" type="submit" disabled={isSearchingPartnerships}>{isSearchingPartnerships ? 'Pesquisando...' : 'Pesquisar empresa'}</button></form>
          {partnershipFeedback ? <p className={`my-data__partnership-feedback my-data__partnership-feedback--${partnershipFeedbackType}`}>{partnershipFeedback}</p> : null}
          {partnershipResults.map((company) => <article className="my-data__partnership-item" key={company.companyId}><div><strong>{company.companyName}</strong><p>{company.companyDocument || 'CNPJ não informado'}</p></div><button className="my-data__partnership-button my-data__partnership-button--secondary" type="button" onClick={() => handleCreatePartnership(company.companyId)} disabled={isSubmittingPartnership}>{isSubmittingPartnership ? 'Enviando...' : 'Enviar solicitação'}</button></article>)}
          <div className="my-data__partnership-groups">
            <div className="my-data__partnership-group"><h3>Solicitações recebidas</h3>{pendingIncomingPartnerships.length ? pendingIncomingPartnerships.map((partnership) => <article className="my-data__partnership-item" key={partnership.id}><div><strong>{partnership.requesterCompanyName}</strong><p>{partnership.requesterCompanyDocument || 'CNPJ não informado'}</p></div><div className="my-data__partnership-actions"><button className="my-data__partnership-button" type="button" onClick={() => handlePartnershipDecision(partnership.id, 'accept')}>Aceitar</button><button className="my-data__partnership-button my-data__partnership-button--secondary" type="button" onClick={() => handlePartnershipDecision(partnership.id, 'decline')}>Recusar</button></div></article>) : <p className="my-data__partnership-empty">Nenhuma solicitação recebida.</p>}</div>
            <div className="my-data__partnership-group"><h3>Solicitações enviadas</h3>{pendingOutgoingPartnerships.length ? pendingOutgoingPartnerships.map((partnership) => <article className="my-data__partnership-item" key={partnership.id}><div><strong>{partnership.targetCompanyName}</strong><p>{partnership.targetCompanyDocument || 'CNPJ não informado'}</p></div><span className="my-data__partnership-status">Aguardando aceite</span></article>) : <p className="my-data__partnership-empty">Nenhuma solicitação enviada pendente.</p>}</div>
            <div className="my-data__partnership-group"><h3>Parcerias ativas</h3>{acceptedPartnershipsWithEmployees.length ? acceptedPartnershipsWithEmployees.map((partnership) => { const partnerName = partnership.outgoing ? partnership.targetCompanyName : partnership.requesterCompanyName; const isExpanded = expandedPartnershipIds.includes(partnership.id); return <article className="my-data__partnership-item" key={partnership.id}><div className="my-data__partnership-item-main"><strong>{partnerName}</strong><p>{(partnership.outgoing ? partnership.targetCompanyDocument : partnership.requesterCompanyDocument) || 'CNPJ não informado'}</p></div><div className="my-data__partnership-actions my-data__partnership-actions--active"><button className="my-data__partnership-toggle" type="button" onClick={() => setExpandedPartnershipIds((ids) => ids.includes(partnership.id) ? ids.filter((id) => id !== partnership.id) : [...ids, partnership.id])}>Funcionários {isExpanded ? '▴' : '▾'}</button><button className="my-data__partnership-button my-data__partnership-button--danger" type="button" onClick={() => handleUnlinkPartnership(partnership.id)} disabled={partnershipActionId === partnership.id}>Desvincular</button></div>{isExpanded ? <div className="my-data__client-employees">{partnership.employees.length ? partnership.employees.map((member) => <div className="my-data__client-employees-item" key={member.id}><div className="my-data__client-employees-main"><strong>{member.name}</strong><span>{member.email || 'Email não informado'}</span></div><button className="my-data__partnership-button my-data__partnership-button--danger" type="button" onClick={() => handleRemoveClientEmployee(member.id, partnerName)}>Excluir funcionário</button></div>) : <p className="my-data__partnership-empty">Nenhum funcionário cadastrado.</p>}</div> : null}</article> }) : <p className="my-data__partnership-empty">Nenhuma parceria aceita até agora.</p>}</div>
          </div>
        </section>
      ) : null}
    </div>
  )
}
