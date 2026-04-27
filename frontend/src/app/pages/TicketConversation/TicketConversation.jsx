import { useEffect, useMemo, useRef, useState } from 'react'
import { addTicketMessage, getTicketAttachmentDownloadUrl, getTicketMessages } from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { PlusCircleIcon } from '../../dashboardIcons'
import './TicketConversation.css'

function TicketConversation({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
  onBack,
  onCloseTicket,
  onLoadTransferCandidates,
  onRequestTicketTransfer,
  ticket,
  userRole,
}) {
  const [draftMessage, setDraftMessage] = useState('')
  const [attachedFiles, setAttachedFiles] = useState([])
  const [messages, setMessages] = useState([])
  const [isLoadingMessages, setIsLoadingMessages] = useState(false)
  const [isSendingMessage, setIsSendingMessage] = useState(false)
  const [isClosingTicket, setIsClosingTicket] = useState(false)
  const [transferCandidates, setTransferCandidates] = useState([])
  const [selectedTransferRecipientId, setSelectedTransferRecipientId] = useState('')
  const [isLoadingTransferCandidates, setIsLoadingTransferCandidates] = useState(false)
  const [isSubmittingTransfer, setIsSubmittingTransfer] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [isTransferConfirmationOpen, setIsTransferConfirmationOpen] = useState(false)
  const [isConfirmingTransfer, setIsConfirmingTransfer] = useState(false)
  const [isCloseConfirmationOpen, setIsCloseConfirmationOpen] = useState(false)
  const fileInputRef = useRef(null)
  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
      }),
    []
  )

  const isWhatsappTicket = ticket?.channel === 'WHATSAPP'

  const loadMessages = async (shouldKeepError = false) => {
    if (!ticket?.id || !currentUser?.email) {
      setMessages([])
      return
    }

    if (!shouldKeepError) {
      setIsLoadingMessages(true)
    }

    setErrorMessage('')

    try {
      const response = await getTicketMessages(ticket.id, currentUser.email)
      setMessages(Array.isArray(response) ? response : [])
    } catch (error) {
      setMessages([])
      setErrorMessage(error.message)
    } finally {
      if (!shouldKeepError) {
        setIsLoadingMessages(false)
      }
    }
  }

  useEffect(() => {
    if (!ticket?.id || !currentUser?.email) {
      setMessages([])
      return undefined
    }

    let isCancelled = false

    loadMessages()

    let pollTimer = null
    if (isWhatsappTicket) {
      pollTimer = window.setInterval(async () => {
        if (isCancelled) {
          return
        }

        try {
          const response = await getTicketMessages(ticket.id, currentUser.email)

          if (isCancelled) {
            return
          }

          setMessages(Array.isArray(response) ? response : [])
        } catch {
          // Keep the current history visible if the background sync fails temporarily.
        }
      }, 10000)
    }

    return () => {
      isCancelled = true
      if (pollTimer) {
        window.clearInterval(pollTimer)
      }
    }
  }, [currentUser?.email, isWhatsappTicket, ticket?.id])

  const isTicketClosed = ticket?.statusCode === 'CLOSED' || Boolean(ticket?.closedAt)

  const canTransferTicket =
    userRole === 'employee' &&
    !isTicketClosed &&
    ticket?.assignedToEmail?.toLowerCase() === currentUser?.email?.toLowerCase() &&
    !ticket?.pendingTransferToName

  useEffect(() => {
    if (!ticket?.id || !canTransferTicket || !onLoadTransferCandidates) {
      setTransferCandidates([])
      setSelectedTransferRecipientId('')
      setIsLoadingTransferCandidates(false)
      return undefined
    }

    let isCancelled = false

    async function loadTransferCandidates() {
      setIsLoadingTransferCandidates(true)

      try {
        const response = await onLoadTransferCandidates(ticket.id)

        if (isCancelled) {
          return
        }

        const nextCandidates = Array.isArray(response) ? response : []
        setTransferCandidates(nextCandidates)
        setSelectedTransferRecipientId((currentRecipientId) =>
          currentRecipientId && nextCandidates.some((candidate) => candidate.userId === currentRecipientId)
            ? currentRecipientId
            : nextCandidates[0]?.userId || ''
        )
      } catch (error) {
        if (isCancelled) {
          return
        }

        setTransferCandidates([])
        setSelectedTransferRecipientId('')
        setErrorMessage(error.message)
      } finally {
        if (!isCancelled) {
          setIsLoadingTransferCandidates(false)
        }
      }
    }

    loadTransferCandidates()

    return () => {
      isCancelled = true
    }
  }, [canTransferTicket, onLoadTransferCandidates, ticket?.id])

  const visibleMessages = useMemo(() => {
    if (!ticket) {
      return []
    }

    const nextMessages = messages.map((message) => ({
      id: message.id,
      authorName: message.authorName || 'Usuário',
      authorEmail: message.authorEmail || '',
      authorRole: message.authorRole || '',
      kind:
        message.authorEmail?.toLowerCase() === ticket.requesterEmail?.toLowerCase() ? 'requester' : 'agent',
      createdAt: message.createdAt,
      text: message.message,
      attachments: Array.isArray(message.attachments)
        ? message.attachments.map((attachment) => ({
            ...attachment,
            downloadUrl: getTicketAttachmentDownloadUrl(ticket.id, attachment.id, currentUser?.email || ''),
          }))
        : [],
    }))

    if (ticket.closedAt) {
      nextMessages.push({
        id: `ticket-${ticket.id}-closed`,
        authorName: 'Sistema',
        authorEmail: '',
        authorRole: '',
        kind: 'system',
        createdAt: ticket.closedAt,
        text: 'Este chamado foi encerrado.',
        attachments: [],
      })
    }

    return nextMessages
  }, [currentUser?.email, messages, ticket?.closedAt, ticket?.id, ticket?.requesterEmail])
  const displayStatusName = useMemo(() => {
    if (!ticket) {
      return 'Não informado'
    }

    if (ticket.statusCode === 'IN_PROGRESS_TRANSFER_PENDING' || ticket.pendingTransferToName) {
      return (
        ticket.statusName ||
        `Em andamento - chamado transferido para ${ticket.pendingTransferToName}`
      )
    }

    if (ticket.statusCode === 'CLOSED' || ticket.closedAt) {
      return ticket.statusName || 'Fechado'
    }

    const conversationMessages = visibleMessages.filter((message) => message.kind !== 'system')
    const lastMessage = conversationMessages[conversationMessages.length - 1]

    if (!lastMessage) {
      return 'Aberto'
    }

    if (lastMessage.kind === 'requester' && conversationMessages.length <= 1) {
      return 'Aberto'
    }

    if (lastMessage.kind === 'requester') {
      return `Em andamento - replica de ${lastMessage.authorName}`
    }

    return `Em andamento - respondido por ${lastMessage.authorName}`
  }, [ticket, visibleMessages])

  const canCloseTicket =
    (userRole === 'admin' || userRole === 'employee') && ticket.statusCode !== 'CLOSED'
  const selectedTransferRecipient = transferCandidates.find(
    (candidate) => candidate.userId === selectedTransferRecipientId
  )

  if (!ticket) {
    return null
  }

  function formatDateTime(value) {
    if (!value) {
      return 'Não informado'
    }

    return dateFormatter.format(new Date(value))
  }

  function getInitials(name) {
    if (!name) {
      return '??'
    }

    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() || '')
      .join('')
  }

  function formatFileSize(sizeBytes) {
    if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) {
      return '0 B'
    }

    if (sizeBytes < 1024) {
      return `${sizeBytes} B`
    }

    if (sizeBytes < 1024 * 1024) {
      return `${(sizeBytes / 1024).toFixed(1)} KB`
    }

    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
  }

  function handleFileSelection(event) {
    const nextFiles = Array.from(event.target.files || [])

    setAttachedFiles((currentFiles) => {
      const existingKeys = new Set(
        currentFiles.map((file) => `${file.name}-${file.size}-${file.lastModified}`)
      )

      const uniqueFiles = nextFiles.filter((file) => {
        const fileKey = `${file.name}-${file.size}-${file.lastModified}`

        if (existingKeys.has(fileKey)) {
          return false
        }

        existingKeys.add(fileKey)
        return true
      })

      return [...currentFiles, ...uniqueFiles]
    })

    event.target.value = ''
  }

  function handleRemoveFile(fileToRemove) {
    setAttachedFiles((currentFiles) =>
      currentFiles.filter(
        (file) =>
          !(
            file.name === fileToRemove.name &&
            file.size === fileToRemove.size &&
            file.lastModified === fileToRemove.lastModified
          )
      )
    )
  }

  async function handleSendMessage(event) {
    event.preventDefault()

    const nextMessage = draftMessage.trim()

    if ((!nextMessage && attachedFiles.length === 0) || !currentUser?.email) {
      return
    }

    setIsSendingMessage(true)
    setErrorMessage('')

    try {
      const savedMessage = await addTicketMessage(ticket.id, {
        authorEmail: currentUser.email,
        files: attachedFiles,
        message: nextMessage,
      })

      setMessages((currentMessages) => [...currentMessages, savedMessage])
      setDraftMessage('')
      setAttachedFiles([])
      if (isWhatsappTicket) {
        await loadMessages(true)
      }
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSendingMessage(false)
    }
  }

  async function handleCloseCurrentTicket() {
    if (!ticket?.id || !onCloseTicket) {
      return
    }

    setIsClosingTicket(true)
    setErrorMessage('')

    try {
      await onCloseTicket(ticket.id)
    } catch (error) {
      setErrorMessage(error.message)
      setIsClosingTicket(false)
      throw error
    }
  }

  function openCloseConfirmation() {
    if (isClosingTicket || !canCloseTicket) {
      return
    }

    setIsCloseConfirmationOpen(true)
  }

  function closeCloseConfirmation() {
    if (isClosingTicket) {
      return
    }

    setIsCloseConfirmationOpen(false)
  }

  async function handleConfirmCloseTicket() {
    try {
      await handleCloseCurrentTicket()
      setIsCloseConfirmationOpen(false)
    } catch {
      // The existing feedback area already shows the error.
    }
  }

  async function handleTransferTicket() {
    if (!ticket?.id || !selectedTransferRecipientId || !onRequestTicketTransfer) {
      return
    }

    setIsSubmittingTransfer(true)
    setErrorMessage('')

    try {
      await onRequestTicketTransfer(ticket.id, selectedTransferRecipientId)
      setTransferCandidates([])
      setSelectedTransferRecipientId('')
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSubmittingTransfer(false)
    }
  }

  function openTransferConfirmation() {
    if (!selectedTransferRecipientId || isLoadingTransferCandidates || isSubmittingTransfer) {
      return
    }

    setIsTransferConfirmationOpen(true)
  }

  function closeTransferConfirmation() {
    if (isConfirmingTransfer) {
      return
    }

    setIsTransferConfirmationOpen(false)
  }

  async function handleConfirmTransfer() {
    setIsConfirmingTransfer(true)

    try {
      await handleTransferTicket()
      setIsTransferConfirmationOpen(false)
    } finally {
      setIsConfirmingTransfer(false)
    }
  }

  return (
    <main className="home-page">
      <Sidebar activeSection="tickets" navigationGroups={navigationGroups} onSectionChange={onNavigatePage} />

      <div className="home-main-column">
        <Header
          activeSection="tickets"
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="ticket-chat">
            <section className="ticket-chat__thread">
              <div className="ticket-chat__header">
                <div className="ticket-chat__actions">
                  <button className="ticket-chat__back" type="button" onClick={onBack}>
                    Voltar
                  </button>
                  {canCloseTicket ? (
                    <button
                      className="ticket-chat__close"
                      type="button"
                      onClick={openCloseConfirmation}
                      disabled={isClosingTicket}
                    >
                      {isClosingTicket ? 'Fechando...' : 'Fechar chamado'}
                    </button>
                  ) : null}
                </div>

                <div className="ticket-chat__title-group">
                  <span className="ticket-chat__eyebrow">
                    {displayStatusName}
                  </span>
                  {isWhatsappTicket ? (
                    <span className="ticket-chat__channel-badge">Canal: WhatsApp</span>
                  ) : null}
                  <h1 className="ticket-chat__title">
                    #{ticket.protocol} - {ticket.title}
                  </h1>
                </div>
              </div>

              <div className="ticket-chat__messages">
                {isLoadingMessages ? (
                  <div className="ticket-chat__empty">Carregando conversa...</div>
                ) : visibleMessages.length > 0 ? (
                  visibleMessages.map((message) => (
                    <article
                      className={`ticket-message ticket-message--${message.kind}`}
                      key={message.id}
                    >
                      <div className="ticket-message__avatar">{getInitials(message.authorName)}</div>

                      <div className="ticket-message__body">
                        <div className="ticket-message__meta">
                          <strong>{message.authorName}</strong>
                          <span>{formatDateTime(message.createdAt)}</span>
                        </div>
                        {message.authorEmail ? (
                          <span className="ticket-message__email">{message.authorEmail}</span>
                        ) : null}
                        {message.authorRole ? (
                          <span className="ticket-message__role">{message.authorRole}</span>
                        ) : null}
                        <p>{message.text}</p>
                        {message.attachments.length > 0 ? (
                          <div className="ticket-message__attachments">
                            {message.attachments.map((attachment) => (
                              <a
                                className="ticket-message__attachment"
                                href={attachment.downloadUrl}
                                key={attachment.id}
                                rel="noreferrer"
                                target="_blank"
                              >
                                <strong>{attachment.originalFileName}</strong>
                                <span>{formatFileSize(attachment.sizeBytes)}</span>
                              </a>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </article>
                  ))
                ) : (
                  <div className="ticket-chat__empty">Nenhuma mensagem salva para este chamado.</div>
                )}
              </div>

              <form className="ticket-chat__composer" onSubmit={handleSendMessage}>
                <input
                  hidden
                  multiple
                  ref={fileInputRef}
                  type="file"
                  onChange={handleFileSelection}
                />
                <textarea
                  placeholder={
                    isTicketClosed
                      ? 'Este chamado foi encerrado.'
                      : isWhatsappTicket
                        ? 'Responder cliente pelo WhatsApp...'
                        : 'Responder chamado...'
                  }
                  rows={3}
                  value={draftMessage}
                  onChange={(event) => setDraftMessage(event.target.value)}
                  disabled={isSendingMessage || isTicketClosed}
                />
                {attachedFiles.length > 0 ? (
                  <div className="ticket-chat__pending-attachments">
                    {attachedFiles.map((file) => (
                      <div
                        className="ticket-chat__pending-attachment"
                        key={`${file.name}-${file.size}-${file.lastModified}`}
                      >
                        <span>{file.name}</span>
                        <button type="button" onClick={() => handleRemoveFile(file)}>
                          Remover
                        </button>
                      </div>
                    ))}
                  </div>
                ) : null}
                <button
                  className="ticket-chat__attach"
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isSendingMessage || isTicketClosed}
                  aria-label={
                    attachedFiles.length > 0
                      ? `Anexar arquivos (${attachedFiles.length} selecionado(s))`
                      : 'Anexar arquivos'
                  }
                  title={
                    attachedFiles.length > 0
                      ? `Anexar arquivos (${attachedFiles.length})`
                      : 'Anexar arquivos'
                  }
                >
                  <PlusCircleIcon />
                </button>
                <button
                  type="submit"
                  disabled={
                    isSendingMessage ||
                    (attachedFiles.length === 0 && !draftMessage.trim()) ||
                    isTicketClosed
                  }
                >
                  {isSendingMessage ? 'Enviando...' : isWhatsappTicket ? 'Enviar ao WhatsApp' : 'Enviar'}
                </button>
              </form>

              {isWhatsappTicket ? (
                <div className="ticket-chat__feedback">
                  As respostas enviadas por esta tela tambem sao encaminhadas para o WhatsApp do cliente.
                </div>
              ) : null}

              {errorMessage ? <div className="ticket-chat__feedback">{errorMessage}</div> : null}
            </section>

            <aside className="ticket-chat__sidebar">
              <article className="ticket-chat__panel">
                <h2>Informações do chamado</h2>

                <dl className="ticket-chat__details">
                  <div>
                    <dt>Canal</dt>
                    <dd>{isWhatsappTicket ? 'WhatsApp' : 'Portal'}</dd>
                  </div>
                  <div>
                    <dt>Setor</dt>
                    <dd>{ticket.sectorName || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>Prioridade</dt>
                    <dd>{ticket.priorityName || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>Protocolo</dt>
                    <dd>{ticket.protocol || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>Data da abertura</dt>
                    <dd>{formatDateTime(ticket.openedAt)}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{displayStatusName}</dd>
                  </div>
                  <div>
                    <dt>Responsável</dt>
                    <dd>{ticket.assignedToName || 'Aguardando atribuição'}</dd>
                  </div>
                  <div>
                    <dt>Solicitante</dt>
                    <dd>{ticket.requesterName || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>E-mail</dt>
                    <dd>{ticket.requesterEmail || 'Não informado'}</dd>
                  </div>
                  {ticket.requesterPhoneNumber ? (
                    <div>
                      <dt>Telefone</dt>
                      <dd>{ticket.requesterPhoneNumber}</dd>
                    </div>
                  ) : null}
                  {ticket.requesterDocumentNumber ? (
                    <div>
                      <dt>CPF</dt>
                      <dd>{ticket.requesterDocumentNumber}</dd>
                    </div>
                  ) : null}
                </dl>
              </article>

              {(canTransferTicket || ticket.pendingTransferToName) ? (
                <article className="ticket-chat__panel">
                  <h2>Transferir chamado</h2>

                  {ticket.pendingTransferToName ? (
                    <div className="ticket-chat__transfer-feedback">
                      Aguardando a resposta de {ticket.pendingTransferToName} para assumir este chamado.
                    </div>
                  ) : transferCandidates.length > 0 ? (
                    <div className="ticket-chat__transfer-box">
                      <label className="ticket-chat__transfer-field" htmlFor="ticket-transfer-recipient">
                        <span>Funcionário destinatário</span>
                        <select
                          id="ticket-transfer-recipient"
                          value={selectedTransferRecipientId}
                          onChange={(event) => setSelectedTransferRecipientId(event.target.value)}
                          disabled={isLoadingTransferCandidates || isSubmittingTransfer}
                        >
                          {transferCandidates.map((candidate) => (
                            <option key={candidate.userId} value={candidate.userId}>
                              {candidate.fullName} ({candidate.email})
                            </option>
                          ))}
                        </select>
                      </label>
                      <button
                        className="ticket-chat__transfer-button"
                        type="button"
                        onClick={openTransferConfirmation}
                        disabled={
                          isLoadingTransferCandidates ||
                          isSubmittingTransfer ||
                          !selectedTransferRecipientId
                        }
                      >
                        {isSubmittingTransfer ? 'Transferindo...' : 'Transferir chamado'}
                      </button>
                    </div>
                  ) : (
                    <div className="ticket-chat__transfer-feedback">
                      {isLoadingTransferCandidates
                        ? 'Carregando funcionários disponíveis...'
                        : 'Nenhum outro funcionário elegível foi encontrado para receber este chamado.'}
                    </div>
                  )}
                </article>
              ) : null}

              <article className="ticket-chat__panel">
                <h2>Informações do cliente</h2>

                <dl className="ticket-chat__details">
                  <div>
                    <dt>Nome</dt>
                    <dd>{ticket.requesterName || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>E-mail</dt>
                    <dd>{ticket.requesterEmail || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt>Papel</dt>
                    <dd>Solicitante</dd>
                  </div>
                </dl>
              </article>
            </aside>
          </div>
        </section>
      </div>

      <ConfirmActionModal
        isOpen={isCloseConfirmationOpen}
        title="Fechar chamado"
        description={[
          `Tem certeza que deseja fechar o chamado #${ticket.protocol}?`,
          'Depois de confirmar, este chamado sera encerrado e nao ficara mais em aberto.',
        ]}
        confirmLabel={isClosingTicket ? 'Fechando...' : 'Fechar chamado'}
        confirmVariant="danger"
        onCancel={closeCloseConfirmation}
        onConfirm={handleConfirmCloseTicket}
        isProcessing={isClosingTicket}
      />
      <ConfirmActionModal
        isOpen={isTransferConfirmationOpen}
        title="Transferir chamado"
        description={
          selectedTransferRecipient
            ? `Tem certeza que deseja transferir este chamado para ${selectedTransferRecipient.fullName}?`
            : 'Tem certeza que deseja transferir este chamado?'
        }
        confirmLabel="Transferir"
        confirmVariant="primary"
        onCancel={closeTransferConfirmation}
        onConfirm={handleConfirmTransfer}
        isProcessing={isConfirmingTransfer}
      />
    </main>
  )
}

export default TicketConversation
