import { useEffect, useMemo, useRef, useState } from 'react'
import {
  addTicketMessage,
  getPublicTicketAttachmentApiUrl,
  getTicketAttachmentDownloadUrl,
  getTicketMessages,
} from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { MicIcon, PlusCircleIcon, ReplyIcon } from '../../dashboardIcons'
import {
  buildAudioFile,
  formatAudioDuration,
  getSupportedAudioMimeType,
} from '../../utils/audioRecorder'
import './TicketConversation.css'

function PencilIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path
        d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zm2.92 2.33H5v-.92l9.06-9.06.92.92L5.92 19.58zM20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.33a1.003 1.003 0 0 0-1.42 0L15.12 5.1l3.75 3.75 1.84-1.81z"
        fill="currentColor"
      />
    </svg>
  )
}

function mergeUniqueFiles(currentFiles, nextFiles) {
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
}

function buildPastedImageFile(item, index) {
  const blob = item.getAsFile()

  if (!blob) {
    return null
  }

  const extension = blob.type?.split('/')[1] || 'png'
  return new File([blob], `${Date.now()}${index}.${extension}`, {
    type: blob.type || 'image/png',
    lastModified: Date.now(),
  })
}

function TicketConversation({
  currentUser,
  headerProps,
  navigationGroups,
  onNavigatePage,
  onBack,
  onCloseTicket,
  onLoadTransferCandidates,
  onRefreshDashboardData,
  onRequestTicketTransfer,
  onUpdateTicketTitle,
  onUpdateTicketClassification,
  ticket,
  userRole,
}) {
  const [draftMessage, setDraftMessage] = useState('')
  const [attachedFiles, setAttachedFiles] = useState([])
  const [isRecordingAudio, setIsRecordingAudio] = useState(false)
  const [recordingDuration, setRecordingDuration] = useState(0)
  const [messages, setMessages] = useState([])
  const [isLoadingMessages, setIsLoadingMessages] = useState(false)
  const [isSendingMessage, setIsSendingMessage] = useState(false)
  const [isClosingTicket, setIsClosingTicket] = useState(false)
  const [transferCandidates, setTransferCandidates] = useState([])
  const [selectedTransferRecipientId, setSelectedTransferRecipientId] = useState('')
  const [isLoadingTransferCandidates, setIsLoadingTransferCandidates] = useState(false)
  const [isSubmittingTransfer, setIsSubmittingTransfer] = useState(false)
  const [titleDraft, setTitleDraft] = useState('')
  const [isEditingTitle, setIsEditingTitle] = useState(false)
  const [isSavingTitle, setIsSavingTitle] = useState(false)
  const [classificationType, setClassificationType] = useState('')
  const [systemArea, setSystemArea] = useState('')
  const [isSavingClassification, setIsSavingClassification] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [isTransferConfirmationOpen, setIsTransferConfirmationOpen] = useState(false)
  const [isConfirmingTransfer, setIsConfirmingTransfer] = useState(false)
  const [isCloseConfirmationOpen, setIsCloseConfirmationOpen] = useState(false)
  const [selectedImage, setSelectedImage] = useState(null)
  const [replyingToMessage, setReplyingToMessage] = useState(null)
  const [imageZoom, setImageZoom] = useState(1)
  const [imagePan, setImagePan] = useState({ x: 0, y: 0 })
  const imageViewportRef = useRef(null)
  const fileInputRef = useRef(null)
  const audioRecorderRef = useRef(null)
  const audioStreamRef = useRef(null)
  const audioChunksRef = useRef([])
  const audioTimerRef = useRef(null)
  const titleInputRef = useRef(null)
  const composerRef = useRef(null)
  const messageInputRef = useRef(null)

  useEffect(() => () => {
    audioRecorderRef.current?.stop()
    audioStreamRef.current?.getTracks().forEach((track) => track.stop())
    window.clearInterval(audioTimerRef.current)
  }, [])

  useEffect(() => {
    setImageZoom(1)
    setImagePan({ x: 0, y: 0 })
    imageViewportRef.current?.scrollTo({ left: 0, top: 0 })
  }, [selectedImage?.url])
  const initialScrollTicketIdRef = useRef(null)
  const initialMessagesLoadedTicketIdRef = useRef(null)
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
        initialMessagesLoadedTicketIdRef.current = ticket.id
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

    let isSyncingMessages = false

    async function syncMessages() {
      if (isCancelled || isSyncingMessages) {
        return
      }

      isSyncingMessages = true

      try {
        const response = await getTicketMessages(ticket.id, currentUser.email)

        if (isCancelled) {
          return
        }

        setMessages(Array.isArray(response) ? response : [])
      } catch {
        // Keep the current history visible if the background sync fails temporarily.
      } finally {
        isSyncingMessages = false
      }
    }

    // Keep the open conversation synchronized for both requester and employee
    // messages. This is intentionally independent of the ticket channel because
    // messages can be created by the system, through WhatsApp, or from the UI.
    const pollTimer = window.setInterval(syncMessages, 5000)
    const handleWindowFocus = () => syncMessages()
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        syncMessages()
      }
    }

    window.addEventListener('focus', handleWindowFocus)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      isCancelled = true
      window.clearInterval(pollTimer)
      window.removeEventListener('focus', handleWindowFocus)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [currentUser?.email, ticket?.id])

  const isTicketClosed = ticket?.statusCode === 'CLOSED' || Boolean(ticket?.closedAt)

  const canEditTitle =
    userRole === 'admin' ||
    (userRole === 'employee' &&
      ticket?.assignedToEmail?.toLowerCase() === currentUser?.email?.toLowerCase())

  const canEditClassification = userRole === 'admin' || userRole === 'employee'

  const canTransferTicket =
    (userRole === 'admin' ||
      (userRole === 'employee' &&
        ticket?.assignedToEmail?.toLowerCase() === currentUser?.email?.toLowerCase())) &&
    !isTicketClosed &&
    !ticket?.pendingTransferToName

  useEffect(() => {
    setTitleDraft(ticket?.title || '')
    setIsEditingTitle(false)
  }, [ticket?.id, ticket?.title])

  useEffect(() => {
    setClassificationType(ticket?.internalTypeCode || '')
    setSystemArea(ticket?.internalSystemAreaCode || '')
  }, [ticket?.id, ticket?.internalTypeCode, ticket?.internalSystemAreaCode])

  async function handleClassificationChange(nextType, nextSystemArea) {
    if (!canEditClassification || isSavingClassification || !onUpdateTicketClassification) {
      return
    }

    setClassificationType(nextType)
    setSystemArea(nextType === 'SYSTEM_ERROR' ? nextSystemArea : '')
    setIsSavingClassification(true)
    setErrorMessage('')

    try {
      await onUpdateTicketClassification(ticket.id, nextType, nextType === 'SYSTEM_ERROR' ? nextSystemArea : '')
    } catch (error) {
      setClassificationType(ticket.internalTypeCode || '')
      setSystemArea(ticket.internalSystemAreaCode || '')
      setErrorMessage(error.message)
    } finally {
      setIsSavingClassification(false)
    }
  }

  useEffect(() => {
    if (!isEditingTitle) {
      return
    }

    titleInputRef.current?.focus()
    titleInputRef.current?.select()
  }, [isEditingTitle])

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
      replyToMessageId: message.replyToMessageId || null,
      replyToAuthorName: message.replyToAuthorName || '',
      replyToMessage: message.replyToMessage || '',
        attachments: Array.isArray(message.attachments)
        ? message.attachments.map((attachment) => ({
            ...attachment,
            downloadUrl: getTicketAttachmentDownloadUrl(ticket.id, attachment.id),
            publicUrl: getPublicTicketAttachmentApiUrl(ticket.id, attachment.id),
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
  }, [messages, ticket?.closedAt, ticket?.id, ticket?.requesterEmail])

  useEffect(() => {
    initialScrollTicketIdRef.current = null
  }, [ticket?.id])

  useEffect(() => {
    if (
      !ticket?.id ||
      isLoadingMessages ||
      initialMessagesLoadedTicketIdRef.current !== ticket.id ||
      initialScrollTicketIdRef.current === ticket.id
    ) {
      return undefined
    }

    if (visibleMessages.length === 0) {
      return undefined
    }

    initialScrollTicketIdRef.current = ticket.id

    const frameId = window.requestAnimationFrame(() => {
      composerRef.current?.scrollIntoView({
        behavior: 'auto',
        block: 'end',
      })
    })

    return () => window.cancelAnimationFrame(frameId)
  }, [isLoadingMessages, ticket?.id, visibleMessages.length])

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

  function isAudioAttachment(attachment) {
    return attachment.contentType?.toLowerCase().startsWith('audio/')
  }

  function isImageAttachment(attachment) {
    return attachment.contentType?.toLowerCase().startsWith('image/')
  }

  function hasVisibleMessageText(message) {
    return message.text && message.text !== 'Anexo enviado.'
  }

  function handleReplyToMessage(message) {
    setReplyingToMessage(message)
    window.requestAnimationFrame(() => {
      composerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      messageInputRef.current?.focus()
    })
  }

  function handleImageZoom(event) {
    event.preventDefault()
    const viewport = event.currentTarget.getBoundingClientRect()
    const cursorOffsetX = event.clientX - viewport.left - viewport.width / 2
    const cursorOffsetY = event.clientY - viewport.top - viewport.height / 2
    const zoomDirection = event.deltaY < 0 ? 0.2 : -0.2

    setImageZoom((currentZoom) => {
      const nextZoom = Math.min(3, Math.max(1, currentZoom + zoomDirection))
      if (nextZoom !== currentZoom) {
        if (nextZoom === 1) {
          setImagePan({ x: 0, y: 0 })
          imageViewportRef.current?.scrollTo({ left: 0, top: 0 })
        } else {
          setImagePan((currentPan) => ({
            x: currentPan.x + cursorOffsetX * (currentZoom - nextZoom),
            y: currentPan.y + cursorOffsetY * (currentZoom - nextZoom),
          }))
        }
      }
      return nextZoom
    })
  }

  async function handleDownloadFile(file) {
    if (!file?.url) return

    const response = await fetch(selectedImage.url)
    if (!response.ok) return

    const blobUrl = window.URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = file.name || 'arquivo'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(blobUrl)
  }

  function handleFileSelection(event) {
    const nextFiles = Array.from(event.target.files || [])

    setAttachedFiles((currentFiles) => mergeUniqueFiles(currentFiles, nextFiles))

    event.target.value = ''
  }

  function handlePasteFiles(event) {
    const clipboardItems = Array.from(event.clipboardData?.items || [])
    const pastedImageFiles = clipboardItems
      .filter((item) => item.type?.startsWith('image/'))
      .map((item, index) => buildPastedImageFile(item, index))
      .filter(Boolean)

    if (pastedImageFiles.length === 0) {
      return
    }

    event.preventDefault()
    setAttachedFiles((currentFiles) => mergeUniqueFiles(currentFiles, pastedImageFiles))
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

  async function handleToggleAudioRecording() {
    if (isRecordingAudio) {
      const recorder = audioRecorderRef.current
      if (recorder && recorder.state === 'recording') {
        recorder.requestData()
        recorder.stop()
      }
      return
    }

    const mimeType = getSupportedAudioMimeType()
    if (!mimeType || !navigator.mediaDevices?.getUserMedia) {
      setErrorMessage('Seu navegador não oferece suporte à gravação de áudio.')
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream, { mimeType })
      audioStreamRef.current = stream
      audioRecorderRef.current = recorder
      audioChunksRef.current = []

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) audioChunksRef.current.push(event.data)
      }
      recorder.onstop = () => {
        window.clearInterval(audioTimerRef.current)
        const recordingMimeType = recorder.mimeType || mimeType
        const blob = new Blob(audioChunksRef.current, { type: recordingMimeType })
        if (blob.size > 0) {
          setAttachedFiles((currentFiles) => mergeUniqueFiles(currentFiles, [buildAudioFile(blob, recordingMimeType)]))
        }
        stream.getTracks().forEach((track) => track.stop())
        audioStreamRef.current = null
        audioRecorderRef.current = null
        audioChunksRef.current = []
        setIsRecordingAudio(false)
      }
      recorder.start(250)
      setErrorMessage('')
      setRecordingDuration(0)
      setIsRecordingAudio(true)
      audioTimerRef.current = window.setInterval(() => {
        setRecordingDuration((currentDuration) => currentDuration + 1)
      }, 1000)
    } catch {
      audioStreamRef.current?.getTracks().forEach((track) => track.stop())
      audioStreamRef.current = null
      setErrorMessage('Não foi possível acessar o microfone. Verifique a permissão do navegador.')
    }
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
        replyToMessageId: replyingToMessage?.id || undefined,
      })

      setMessages((currentMessages) => [...currentMessages, savedMessage])
      setDraftMessage('')
      setAttachedFiles([])
      setReplyingToMessage(null)
      await onRefreshDashboardData?.(currentUser.email)
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

  async function handleUpdateCurrentTitle(event) {
    event.preventDefault()

    const nextTitle = titleDraft.trim()
    const currentTitle = ticket?.title?.trim() || ''

    if (
      !ticket?.id ||
      !currentUser?.email ||
      !canEditTitle ||
      !onUpdateTicketTitle ||
      !nextTitle ||
      nextTitle === currentTitle
    ) {
      return
    }

    setIsSavingTitle(true)
    setErrorMessage('')

    try {
      const updatedTicket = await onUpdateTicketTitle(ticket.id, nextTitle)

      if (updatedTicket?.title) {
        setTitleDraft(updatedTicket.title)
      }
      setIsEditingTitle(false)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSavingTitle(false)
    }
  }

  function handleStartTitleEditing() {
    if (!canEditTitle || isSavingTitle) {
      return
    }

    setTitleDraft(ticket?.title || '')
    setIsEditingTitle(true)
  }

  function handleTitleInputKeyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault()
      setTitleDraft(ticket?.title || '')
      setIsEditingTitle(false)
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
                  {canEditTitle ? (
                    <form className="ticket-chat__title-editor" onSubmit={handleUpdateCurrentTitle}>
                      <span className="ticket-chat__title-prefix">#{ticket.protocol} - </span>
                      {isEditingTitle ? (
                        <input
                          ref={titleInputRef}
                          className="ticket-chat__title-input"
                          id="ticket-title"
                          type="text"
                          value={titleDraft}
                          onChange={(event) => setTitleDraft(event.target.value)}
                          onKeyDown={handleTitleInputKeyDown}
                          maxLength={180}
                          disabled={isSavingTitle}
                        />
                      ) : (
                        <h1 className="ticket-chat__title">{ticket.title}</h1>
                      )}
                      {!isEditingTitle ? (
                        <button
                          className="ticket-chat__title-edit-button"
                          type="button"
                          onClick={handleStartTitleEditing}
                          disabled={isSavingTitle}
                          aria-label="Editar assunto"
                          title="Editar assunto"
                        >
                          <PencilIcon />
                        </button>
                      ) : null}
                    </form>
                  ) : (
                    <div className="ticket-chat__title-display">
                      <span className="ticket-chat__title-prefix">#{ticket.protocol} - </span>
                      <h1 className="ticket-chat__title">{ticket.title}</h1>
                    </div>
                  )}
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
                          {message.kind !== 'system' ? (
                            <button
                              className="ticket-message__reply-button"
                              type="button"
                              onClick={() => handleReplyToMessage(message)}
                              aria-label={`Responder a ${message.authorName}`}
                              title="Responder"
                            >
                              <ReplyIcon />
                              <span>Responder</span>
                            </button>
                          ) : null}
                        </div>
                        {message.authorEmail ? (
                          <span className="ticket-message__email">{message.authorEmail}</span>
                        ) : null}
                        {message.authorRole ? (
                          <span className="ticket-message__role">{message.authorRole}</span>
                        ) : null}
                        {message.replyToMessageId ? (
                          <div className="ticket-message__reply-preview">
                            <strong>{message.replyToAuthorName || 'Mensagem anterior'}</strong>
                            <span>
                              {message.replyToMessage === 'Anexo enviado.'
                                ? 'Mídia anexada'
                                : message.replyToMessage}
                            </span>
                          </div>
                        ) : null}
                        {hasVisibleMessageText(message) ? <p>{message.text}</p> : null}
                        {message.attachments.length > 0 ? (
                          <div className="ticket-message__attachments">
                            {message.attachments.map((attachment) => (
                              isImageAttachment(attachment) ? (
                                <button
                                  className="ticket-message__image-button"
                                  type="button"
                                  key={attachment.id}
                                  onClick={() => setSelectedImage({
                                    name: attachment.originalFileName,
                                    url: attachment.publicUrl,
                                  })}
                                  title="Ampliar imagem"
                                >
                                  <img
                                    alt={attachment.originalFileName}
                                    className="ticket-message__image"
                                    src={attachment.publicUrl}
                                  />
                                </button>
                              ) : isAudioAttachment(attachment) ? (
                                <div className="ticket-message__attachment" key={attachment.id}>
                                  <strong>{attachment.originalFileName}</strong>
                                  <audio controls preload="metadata" src={attachment.downloadUrl} />
                                </div>
                              ) : (
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
                              )
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

              <form ref={composerRef} className="ticket-chat__composer" onSubmit={handleSendMessage}>
                {replyingToMessage ? (
                  <div className="ticket-chat__replying-to">
                    <div>
                      <strong>Respondendo a {replyingToMessage.authorName}</strong>
                      <span>
                        {hasVisibleMessageText(replyingToMessage)
                          ? replyingToMessage.text
                          : 'Mídia anexada'}
                      </span>
                    </div>
                    <button type="button" onClick={() => setReplyingToMessage(null)}>
                      ×
                    </button>
                  </div>
                ) : null}
                <input
                  hidden
                  multiple
                  ref={fileInputRef}
                  type="file"
                  onChange={handleFileSelection}
                />
                <textarea
                  ref={messageInputRef}
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
                  onPaste={handlePasteFiles}
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
                  className={`ticket-chat__attach${isRecordingAudio ? ' ticket-chat__attach--recording' : ''}`}
                  type="button"
                  onClick={handleToggleAudioRecording}
                  disabled={isSendingMessage || isTicketClosed}
                  aria-label={isRecordingAudio ? 'Parar gravação de áudio' : 'Gravar áudio'}
                  title={isRecordingAudio ? 'Parar gravação de áudio' : 'Gravar áudio'}
                >
                  <MicIcon />
                  {isRecordingAudio && (
                    <span className="ticket-chat__recording-duration">
                      {formatAudioDuration(recordingDuration)}
                    </span>
                  )}
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
                  {canEditClassification || ticket.internalTypeName ? (
                    <div>
                      <dt>Classificação</dt>
                      <dd>
                        {canEditClassification ? (
                          <select
                            className="ticket-chat__classification-select"
                            value={classificationType}
                            onChange={(event) => handleClassificationChange(event.target.value, systemArea)}
                            disabled={isSavingClassification}
                          >
                            <option value="">Não classificado</option>
                            <option value="SYSTEM_ERROR">Erro do sistema</option>
                            <option value="QUESTION">Dúvida</option>
                            <option value="USER_ERROR">Erro do usuário</option>
                            <option value="DOCUMENTATION_ERROR">Erro de Documentação</option>
                          </select>
                        ) : (
                          ticket.internalTypeName
                        )}
                      </dd>
                    </div>
                  ) : null}
                  {canEditClassification && classificationType === 'SYSTEM_ERROR' ? (
                    <div>
                      <dt>Área do erro</dt>
                      <dd>
                        <select
                          className="ticket-chat__classification-select"
                          value={systemArea}
                          onChange={(event) => handleClassificationChange(classificationType, event.target.value)}
                          disabled={isSavingClassification}
                        >
                          <option value="">Não informado</option>
                          <option value="DATABASE">Banco de dados</option>
                          <option value="APPLICATION">Aplicação</option>
                          <option value="INSTABILITY">Instabilidade</option>
                        </select>
                      </dd>
                    </div>
                  ) : null}
                  {!canEditClassification && ticket.internalTypeName === 'Erro do sistema' && ticket.internalSystemAreaName ? (
                    <div>
                      <dt>Área do erro</dt>
                      <dd>{ticket.internalSystemAreaName}</dd>
                    </div>
                  ) : null}
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
                    <dt>Empresa do solicitante</dt>
                    <dd>{ticket.requesterCompanyName || 'Não informado'}</dd>
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
      {selectedImage ? (
        <div
          className="ticket-image-lightbox"
          role="dialog"
          aria-label={`Imagem ampliada: ${selectedImage.name}`}
          onClick={() => setSelectedImage(null)}
        >
          <div
            className="ticket-image-lightbox__controls"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              aria-label="Diminuir zoom"
              title="Diminuir zoom"
              onClick={() => setImageZoom((currentZoom) => {
                const nextZoom = Math.max(1, currentZoom - 0.2)
                if (nextZoom === 1) {
                  setImagePan({ x: 0, y: 0 })
                  imageViewportRef.current?.scrollTo({ left: 0, top: 0 })
                }
                return nextZoom
              })}
            >
              −
            </button>
            <button
              type="button"
              aria-label="Redefinir zoom"
              title="Redefinir zoom"
              onClick={() => {
                setImageZoom(1)
                setImagePan({ x: 0, y: 0 })
              }}
            >
              {Math.round(imageZoom * 100)}%
            </button>
            <button
              type="button"
              aria-label="Aumentar zoom"
              title="Aumentar zoom"
              onClick={() => setImageZoom((currentZoom) => Math.min(3, currentZoom + 0.2))}
            >
              +
            </button>
            <button
              type="button"
              aria-label="Baixar imagem"
              title="Baixar imagem"
              onClick={() => handleDownloadFile(selectedImage)}
            >
              Baixar
            </button>
            <button
              type="button"
              aria-label="Abrir imagem em nova aba"
              title="Abrir imagem em nova aba"
              onClick={() => window.open(selectedImage.url, '_blank', 'noopener,noreferrer')}
            >
              Abrir
            </button>
          </div>
          <button
            className="ticket-image-lightbox__close"
            type="button"
            aria-label="Fechar imagem ampliada"
            onClick={() => setSelectedImage(null)}
          >
            ×
          </button>
          <div
            className={`ticket-image-lightbox__viewport${imageZoom > 1 ? ' is-zoomed' : ''}`}
            ref={imageViewportRef}
            onWheel={handleImageZoom}
            onClick={(event) => event.stopPropagation()}
          >
            <img
              className={`ticket-image-lightbox__image${imageZoom > 1 ? ' is-zoomed' : ''}`}
              src={selectedImage.url}
              alt={selectedImage.name}
              style={{ transform: `translate(${imagePan.x}px, ${imagePan.y}px) scale(${imageZoom})` }}
            />
          </div>
        </div>
      ) : null}
    </main>
  )
}

export default TicketConversation
