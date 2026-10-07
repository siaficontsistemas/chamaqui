import { useEffect, useMemo, useRef, useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import { dashboardPages } from '../../dashboardData'
import { ChevronDownIcon, MicIcon, PlusCircleIcon } from '../../dashboardIcons'
import {
  buildAudioFile,
  formatAudioDuration,
  getSupportedAudioMimeType,
} from '../../utils/audioRecorder'
import { getAttachmentSizeError } from '../../utils/attachmentLimits'
import '../Home/Home.css'

function normalizeText(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase()
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

function NewTicket({
  availableClientCompanies = [],
  availableTicketSectors = [],
  currentUser,
  headerProps,
  navigationGroups,
  onCreateTicket,
  onCreateClientPreRegistration,
  onUpdateClientPreRegistration,
  onDeleteClientPreRegistration,
  onNavigatePage,
  ticketRequesters = [],
}) {
  const activeContent = dashboardPages.newTicket
  const canCreateTickets =
    Array.isArray(currentUser?.roles) &&
    (currentUser.roles.includes('user') ||
      currentUser.roles.includes('admin') ||
      currentUser.roles.includes('employee'))
  const isStaffRole =
    Array.isArray(currentUser?.roles) &&
    (currentUser.roles.includes('admin') || currentUser.roles.includes('employee'))
  const [formValues, setFormValues] = useState({
    requesterEmail: '',
    requesterName: '',
    deliveryMode: 'system',
    companyName: '',
    companyOwnerId: '',
    sectorId: '',
    assignedToUserId: '',
    priorityCode: '',
    copyEmail: '',
    description: '',
  })
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [preRegistrationOpen, setPreRegistrationOpen] = useState(false)
  const [preRegistrationValues, setPreRegistrationValues] = useState({ fullName: '', phoneNumber: '', companyOwnerId: '' })
  const [preRegistrationFeedback, setPreRegistrationFeedback] = useState('')
  const [editingPreRegistrationId, setEditingPreRegistrationId] = useState(null)
  const [preRegistrationToDelete, setPreRegistrationToDelete] = useState(null)
  const [isDeletingPreRegistration, setIsDeletingPreRegistration] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isCompanyOptionsOpen, setIsCompanyOptionsOpen] = useState(false)
  const [isRequesterOptionsOpen, setIsRequesterOptionsOpen] = useState(false)
  const [attachedFiles, setAttachedFiles] = useState([])
  const [isRecordingAudio, setIsRecordingAudio] = useState(false)
  const [recordingDuration, setRecordingDuration] = useState(0)
  const fileInputRef = useRef(null)
  const audioRecorderRef = useRef(null)
  const audioStreamRef = useRef(null)
  const audioChunksRef = useRef([])
  const audioTimerRef = useRef(null)
  const availableSectors = useMemo(
    () => availableTicketSectors.filter((sector) => sector.active !== false),
    [availableTicketSectors]
  )
  const availableCompanies = useMemo(() => {
    const companies = new Map()

    availableSectors.forEach((sector) => {
      if (!sector.companyOwnerId || companies.has(sector.companyOwnerId)) {
        return
      }

      companies.set(sector.companyOwnerId, {
        id: sector.companyOwnerId,
        name: sector.companyName || 'Empresa não informada',
        document: sector.companyDocument || '',
      })
    })

    return Array.from(companies.values()).sort((firstCompany, secondCompany) =>
      firstCompany.name.localeCompare(secondCompany.name, 'pt-BR', { sensitivity: 'base' })
    )
  }, [availableSectors])
  const selectedCompany = useMemo(
    () => availableCompanies.find((company) => company.id === formValues.companyOwnerId) ?? null,
    [availableCompanies, formValues.companyOwnerId]
  )
  const companySectors = useMemo(
    () =>
      availableSectors.filter((sector) => sector.companyOwnerId === selectedCompany?.id),
    [availableSectors, selectedCompany]
  )
  const selectedSector = useMemo(
    () => companySectors.find((sector) => sector.id === formValues.sectorId) ?? null,
    [companySectors, formValues.sectorId]
  )
  const sectorAssignees = useMemo(
    () => (Array.isArray(selectedSector?.assignees) ? selectedSector.assignees : []),
    [selectedSector]
  )
  const filteredCompanies = useMemo(() => {
    const normalizedCompanyName = normalizeText(formValues.companyName)

    if (!normalizedCompanyName) {
      return availableCompanies
    }

    return availableCompanies.filter((company) =>
      normalizeText(company.name).includes(normalizedCompanyName) ||
      company.document.includes(formValues.companyName.replace(/\D/g, ''))
    )
  }, [availableCompanies, formValues.companyName])
  const filteredRequesters = useMemo(() => {
    const normalizedName = normalizeText(formValues.requesterName)
    if (!normalizedName) {
      return ticketRequesters
    }

    return ticketRequesters.filter((requester) =>
      normalizeText(requester.fullName || '').includes(normalizedName)
    )
  }, [formValues.requesterName, ticketRequesters])

  useEffect(() => () => {
    audioRecorderRef.current?.stop()
    audioStreamRef.current?.getTracks().forEach((track) => track.stop())
    window.clearInterval(audioTimerRef.current)
  }, [])

  useEffect(() => {
    if (availableCompanies.length !== 1) {
      return
    }

    const onlyCompany = availableCompanies[0]
    setFormValues((currentValues) => {
      if (currentValues.companyOwnerId === onlyCompany.id && currentValues.companyName === onlyCompany.name) {
        return currentValues
      }

      return {
        ...currentValues,
        companyName: onlyCompany.name,
        companyOwnerId: onlyCompany.id,
      }
    })
  }, [availableCompanies])

  function handleChange(field, value) {
    setFormValues((currentValues) => {
      if (field === 'requesterName') {
        return {
          ...currentValues,
          requesterName: value,
          requesterEmail: '',
        }
      }

      if (field === 'companyName') {
        const matchedCompany =
          availableCompanies.find(
            (company) => normalizeText(company.name) === normalizeText(value)
          ) ?? null

        return {
          ...currentValues,
          companyName: value,
          companyOwnerId: matchedCompany?.id || '',
          assignedToUserId: '',
          sectorId:
            matchedCompany?.id && currentValues.companyOwnerId === matchedCompany.id
              ? currentValues.sectorId
              : '',
        }
      }

      if (field === 'sectorId') {
        return {
          ...currentValues,
          sectorId: value,
          assignedToUserId: '',
        }
      }

      return {
        ...currentValues,
        [field]: value,
      }
    })
  }

  function handleRequesterSelect(selectedRequester) {
    setFormValues((currentValues) => ({
      ...currentValues,
      requesterName: selectedRequester?.fullName || '',
      requesterEmail: selectedRequester?.email || '',
    }))
    setIsRequesterOptionsOpen(false)
  }

  async function handlePreRegistrationSubmit(event) {
    event?.preventDefault?.()
    if (!preRegistrationValues.fullName.trim()) { setPreRegistrationFeedback('Informe o nome do cliente.'); return }
    if (!preRegistrationValues.phoneNumber.trim()) { setPreRegistrationFeedback('Informe o telefone do cliente.'); return }
    if (!preRegistrationValues.companyOwnerId) { setPreRegistrationFeedback('Selecione a empresa cliente para criar o pré-cadastro.'); return }
    try {
      setPreRegistrationFeedback('')
      const saved = editingPreRegistrationId
        ? await onUpdateClientPreRegistration?.(editingPreRegistrationId, preRegistrationValues)
        : await onCreateClientPreRegistration?.(preRegistrationValues)
      if (saved) handleRequesterSelect(saved)
      closePreRegistration()
      setFeedbackMessage(editingPreRegistrationId
        ? 'Pré-cadastro atualizado com sucesso.'
        : 'Pré-cadastro criado. O cliente já pode receber chamados pelo sistema.')
    } catch (error) { setPreRegistrationFeedback(error.message) }
  }

  function handleCompanySelect(selectedOption) {
    setFormValues((currentValues) => ({
      ...currentValues,
      companyName: selectedOption?.name || '',
      companyOwnerId: selectedOption?.id || '',
      assignedToUserId: '',
      sectorId:
        selectedOption?.id && currentValues.companyOwnerId === selectedOption.id
          ? currentValues.sectorId
          : '',
    }))
    setIsCompanyOptionsOpen(false)
  }

  function handlePreRegistrationCompanySelect(event) {
    setPreRegistrationValues((currentValues) => ({
      ...currentValues,
      companyOwnerId: event.target.value,
    }))
  }

  function togglePreRegistration() {
    if (preRegistrationOpen) {
      closePreRegistration()
      return
    }

    setEditingPreRegistrationId(null)
    setPreRegistrationValues({ fullName: '', phoneNumber: '', companyOwnerId: '' })
    setPreRegistrationFeedback('')
    setPreRegistrationOpen(true)
  }

  function openPreRegistrationEdit(requester) {
    setEditingPreRegistrationId(requester.id)
    setPreRegistrationValues({
      fullName: requester.fullName || '',
      phoneNumber: requester.phoneNumber || '',
      companyOwnerId: requester.companyOwnerId || '',
    })
    setPreRegistrationFeedback('')
    setPreRegistrationOpen(true)
  }

  function closePreRegistration() {
    if (isSubmitting) return
    setPreRegistrationOpen(false)
    setPreRegistrationFeedback('')
    setEditingPreRegistrationId(null)
    setPreRegistrationValues({ fullName: '', phoneNumber: '', companyOwnerId: '' })
  }

  function openPreRegistrationDelete(requester) {
    setPreRegistrationToDelete(requester)
  }

  function closePreRegistrationDelete() {
    if (isDeletingPreRegistration) return
    setPreRegistrationToDelete(null)
  }

  async function handlePreRegistrationDelete() {
    if (!preRegistrationToDelete?.id || !onDeleteClientPreRegistration) return

    try {
      setIsDeletingPreRegistration(true)
      await onDeleteClientPreRegistration(preRegistrationToDelete.id)
      if (formValues.requesterEmail === preRegistrationToDelete.email) {
        handleRequesterSelect(null)
      }
      setPreRegistrationToDelete(null)
      setFeedbackMessage('Pré-cadastro excluído com sucesso.')
    } catch (error) {
      setPreRegistrationToDelete(null)
      setFeedbackMessage(error.message)
    } finally {
      setIsDeletingPreRegistration(false)
    }
  }

  function handleFileSelection(event) {
    const nextFiles = Array.from(event.target.files || [])
    const mergedFiles = mergeUniqueFiles(attachedFiles, nextFiles)
    const sizeError = getAttachmentSizeError(mergedFiles)

    if (sizeError) {
      setFeedbackMessage(sizeError)
      event.target.value = ''
      return
    }

    setFeedbackMessage('')
    setAttachedFiles(mergedFiles)

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
    const mergedFiles = mergeUniqueFiles(attachedFiles, pastedImageFiles)
    const sizeError = getAttachmentSizeError(mergedFiles)

    if (sizeError) {
      setFeedbackMessage(sizeError)
      return
    }

    setFeedbackMessage('')
    setAttachedFiles(mergedFiles)
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
      setFeedbackMessage('Seu navegador não oferece suporte à gravação de áudio.')
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
          const audioFile = buildAudioFile(blob, recordingMimeType)
          const mergedFiles = mergeUniqueFiles(attachedFiles, [audioFile])
          const sizeError = getAttachmentSizeError(mergedFiles)

          if (sizeError) {
            setFeedbackMessage(sizeError)
          } else {
            setAttachedFiles(mergedFiles)
          }
        }
        stream.getTracks().forEach((track) => track.stop())
        audioStreamRef.current = null
        audioRecorderRef.current = null
        audioChunksRef.current = []
        setIsRecordingAudio(false)
      }
      recorder.start(250)
      setFeedbackMessage('')
      setRecordingDuration(0)
      setIsRecordingAudio(true)
      audioTimerRef.current = window.setInterval(() => {
        setRecordingDuration((currentDuration) => currentDuration + 1)
      }, 1000)
    } catch {
      audioStreamRef.current?.getTracks().forEach((track) => track.stop())
      audioStreamRef.current = null
      setFeedbackMessage('Não foi possível acessar o microfone. Verifique a permissão do navegador.')
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()

    if (!formValues.companyOwnerId) {
      setFeedbackMessage('Digite ou selecione uma empresa cadastrada para a qual o chamado será enviado.')
      return
    }

    if (isStaffRole && !formValues.requesterEmail.trim()) {
      setFeedbackMessage('Informe o e-mail do cliente para quem o chamado será aberto.')
      return
    }

    if (!formValues.sectorId) {
      setFeedbackMessage('Selecione um setor para criar o chamado.')
      return
    }

    if (!formValues.priorityCode) {
      setFeedbackMessage('Selecione a prioridade do chamado.')
      return
    }

    if (formValues.description.trim().length < 10 && attachedFiles.length === 0) {
      setFeedbackMessage('Escreva a primeira mensagem do chamado com pelo menos 10 caracteres.')
      return
    }

    try {
      setIsSubmitting(true)
      setFeedbackMessage('')
      await onCreateTicket({
        requesterEmail: formValues.requesterEmail.trim(),
        whatsappEnabled: isStaffRole && formValues.deliveryMode === 'whatsapp',
        description: formValues.description,
        files: attachedFiles,
        priorityCode: formValues.priorityCode,
        companyOwnerId: formValues.companyOwnerId,
        sectorId: formValues.sectorId,
        assignedToUserId: formValues.assignedToUserId || undefined,
        copyEmail: formValues.copyEmail,
      })
      setFormValues({
        requesterEmail: '',
        requesterName: '',
        deliveryMode: 'system',
        companyName: '',
        companyOwnerId: '',
        sectorId: '',
        assignedToUserId: '',
        priorityCode: '',
        copyEmail: '',
        description: '',
      })
      setAttachedFiles([])
      setFeedbackMessage('Chamado criado com sucesso.')
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection="newTicket"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="newTicket"
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--form">
            <div className="home-content__header">
              <div className="home-content__heading">
                <span className="home-content__eyebrow">Abertura de chamado</span>
                <h1>{activeContent.contentTitle}</h1>
                <p>{activeContent.contentText}</p>
              </div>
            </div>

            {!canCreateTickets ? (
              <div className="home-content__placeholder">
                <p>
                  Sua conta ainda nao possui empresas parceiras aceitas ou nao pode abrir chamados por esta tela.
                </p>
              </div>
            ) : (
              <form className="ticket-form" onSubmit={handleSubmit}>
              <div className="ticket-form__grid">
                {isStaffRole ? (
                  <div className="ticket-client-selection">
                    <label className="ticket-field ticket-field--combobox">
                      <span>Cliente</span>
                      <div className="ticket-field__control ticket-field__control--select">
                        <input
                          placeholder={ticketRequesters.length > 0 ? 'Selecione ou digite o nome do cliente...' : 'Nenhum cliente disponível'}
                          type="text"
                          value={formValues.requesterName}
                          onChange={(event) => {
                            handleChange('requesterName', event.target.value)
                            setIsRequesterOptionsOpen(true)
                          }}
                          onFocus={() => setIsRequesterOptionsOpen(true)}
                          onBlur={() => window.setTimeout(() => setIsRequesterOptionsOpen(false), 150)}
                          disabled={ticketRequesters.length === 0}
                          required
                        />
                        <button
                          className="ticket-field__toggle"
                          type="button"
                          onClick={() => setIsRequesterOptionsOpen((currentValue) => !currentValue)}
                          aria-label="Abrir opções de cliente"
                          disabled={ticketRequesters.length === 0}
                        >
                          <ChevronDownIcon />
                        </button>
                      </div>
                      {isRequesterOptionsOpen ? (
                        <div className="ticket-field__options" role="listbox" aria-label="Clientes">
                          {filteredRequesters.length > 0 ? (
                          filteredRequesters.map((requester) => (
                              <div
                                className={`ticket-field__option-row${requester.email === formValues.requesterEmail ? ' is-active' : ''}`}
                                key={requester.id || requester.email}
                              >
                                <button
                                  className="ticket-field__option"
                                  type="button"
                                  onMouseDown={(event) => event.preventDefault()}
                                  onClick={() => handleRequesterSelect(requester)}
                                >
                                  <span>{requester.fullName} — {requester.email}</span>
                                </button>
                                {requester.preRegistered ? (
                                  <span className="ticket-field__option-actions">
                                    <button
                                      className="ticket-field__option-action"
                                      type="button"
                                      onMouseDown={(event) => event.preventDefault()}
                                      onClick={() => openPreRegistrationEdit(requester)}
                                      aria-label={`Editar pré-cadastro de ${requester.fullName}`}
                                      title="Editar pré-cadastro"
                                    >
                                      ✎
                                    </button>
                                    <button
                                      className="ticket-field__option-action ticket-field__option-action--danger"
                                      type="button"
                                      onMouseDown={(event) => event.preventDefault()}
                                      onClick={() => openPreRegistrationDelete(requester)}
                                      aria-label={`Excluir pré-cadastro de ${requester.fullName}`}
                                      title="Excluir pré-cadastro"
                                    >
                                      ×
                                    </button>
                                  </span>
                                ) : null}
                              </div>
                            ))
                          ) : (
                            <span className="ticket-field__option ticket-field__option--empty">
                              Nenhum cliente encontrado
                            </span>
                          )}
                        </div>
                      ) : null}
                    </label>
                    <button className="ticket-form__submit pre-registration-trigger" type="button" onClick={togglePreRegistration}>
                      <PlusCircleIcon />
                      <span>Adicionar novo cliente</span>
                    </button>
                  </div>
                ) : null}
                {isStaffRole ? (
                  <label className="ticket-field">
                    <span>Canal de atendimento</span>
                    <div className="ticket-field__control ticket-field__control--select">
                      <select
                        value={formValues.deliveryMode}
                        onChange={(event) => handleChange('deliveryMode', event.target.value)}
                      >
                        <option value="system">Somente sistema</option>
                        <option value="whatsapp">Sistema + WhatsApp</option>
                      </select>
                      <ChevronDownIcon />
                    </div>
                  </label>
                ) : null}
                <label className="ticket-field ticket-field--combobox">
                  <span>Empresa</span>
                  <div className="ticket-field__control ticket-field__control--select">
                    <input
                      placeholder={
                        availableCompanies.length === 1
                          ? 'Empresa identificada automaticamente'
                          : availableCompanies.length > 0
                          ? 'Digite o nome ou CNPJ da empresa parceira...'
                          : 'Nenhuma empresa parceira disponível'
                      }
                      type="text"
                      value={formValues.companyName}
                      onChange={(event) => {
                        handleChange('companyName', event.target.value)
                        setIsCompanyOptionsOpen(true)
                      }}
                      onFocus={() => {
                        if (availableCompanies.length > 0) {
                          setIsCompanyOptionsOpen(true)
                        }
                      }}
                      onBlur={() => {
                        window.setTimeout(() => setIsCompanyOptionsOpen(false), 150)
                      }}
                      disabled={availableCompanies.length === 0 || availableCompanies.length === 1}
                    />
                    <button
                      className="ticket-field__toggle"
                      type="button"
                      onClick={() => {
                        if (availableCompanies.length === 0) {
                          return
                        }

                        setIsCompanyOptionsOpen((currentValue) => !currentValue)
                      }}
                      aria-label="Abrir opções de empresa parceira"
                      disabled={availableCompanies.length === 0 || availableCompanies.length === 1}
                    >
                      <ChevronDownIcon />
                    </button>
                  </div>
                  {isCompanyOptionsOpen ? (
                    <div className="ticket-field__options" role="listbox" aria-label="Empresas parceiras">
                      {filteredCompanies.length > 0 ? (
                        filteredCompanies.map((company) => (
                          <button
                            className={`ticket-field__option${
                              company.id === formValues.companyOwnerId ? ' is-active' : ''
                            }`}
                            key={company.id}
                            type="button"
                            onMouseDown={(event) => event.preventDefault()}
                            onClick={() => handleCompanySelect(company)}
                          >
                            {company.document ? `${company.name} - ${company.document}` : company.name}
                          </button>
                        ))
                      ) : (
                        <span className="ticket-field__option ticket-field__option--empty">
                          Nenhuma empresa encontrada
                        </span>
                      )}
                    </div>
                  ) : null}
                </label>

                <label className="ticket-field">
                  <span>Setor</span>
                  <div className="ticket-field__control ticket-field__control--select">
                    <select
                      value={formValues.sectorId}
                      onChange={(event) => handleChange('sectorId', event.target.value)}
                      disabled={!formValues.companyOwnerId || companySectors.length === 0}
                    >
                      {selectedCompany ? (
                        companySectors.length > 0 ? (
                          <>
                            <option disabled value="">
                              Selecione o setor...
                            </option>
                            {companySectors.map((sector) => (
                              <option key={sector.id} value={sector.id}>
                                {sector.name}
                              </option>
                            ))}
                          </>
                        ) : (
                          <option disabled value="">
                            Nenhum setor disponível para essa empresa
                          </option>
                        )
                      ) : (
                        <option disabled value="">
                          Digite ou selecione uma empresa primeiro
                        </option>
                      )}
                    </select>
                    <ChevronDownIcon />
                  </div>
                </label>

                <label className="ticket-field">
                  <span>Prioridade</span>
                  <div className="ticket-field__control ticket-field__control--select">
                    <select
                      value={formValues.priorityCode}
                      onChange={(event) => handleChange('priorityCode', event.target.value)}
                    >
                      <option disabled value="">
                        Selecione a prioridade...
                      </option>
                      <option value="LOW">Baixa</option>
                      <option value="MEDIUM">Média</option>
                      <option value="HIGH">Alta</option>
                    </select>
                    <ChevronDownIcon />
                  </div>
                </label>

                {!isStaffRole ? (
                  <label className="ticket-field">
                    <span>Destinatário</span>
                    <div className="ticket-field__control ticket-field__control--select">
                      <select
                        value={formValues.assignedToUserId}
                        onChange={(event) => handleChange('assignedToUserId', event.target.value)}
                        disabled={!formValues.sectorId}
                      >
                        {!selectedSector ? (
                          <option disabled value="">
                            Selecione um setor primeiro
                          </option>
                        ) : (
                          <>
                            <option value="">Aleatoriamente</option>
                            {sectorAssignees.map((assignee) => (
                              <option key={assignee.id} value={assignee.id}>
                                {assignee.fullName}
                              </option>
                            ))}
                          </>
                        )}
                      </select>
                      <ChevronDownIcon />
                    </div>
                  </label>
                ) : null}
              </div>

              <label className="ticket-field">
                <span>Enviar Cópia</span>
                <div className="ticket-field__control">
                  <input
                    placeholder="Digite o email que deve receber a conversa ao encerrar o chamado"
                    type="email"
                    value={formValues.copyEmail}
                    onChange={(event) => handleChange('copyEmail', event.target.value)}
                  />
                </div>
              </label>

              <label className="ticket-field">
                <span>Primeira mensagem</span>
                <div className="ticket-field__control ticket-field__control--textarea">
                  <textarea
                    placeholder="Descreva aqui o seu chamado. O assunto sera gerado automaticamente com os 30 primeiros caracteres desta mensagem."
                    rows="6"
                    value={formValues.description}
                    onChange={(event) => handleChange('description', event.target.value)}
                    onPaste={handlePasteFiles}
                  />
                </div>
              </label>

              {feedbackMessage ? <p className="team-feedback">{feedbackMessage}</p> : null}

              {attachedFiles.length > 0 ? (
                <div className="ticket-form__attachments">
                  {attachedFiles.map((file) => (
                    <div
                      className="ticket-form__attachment-item"
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

              <div className="ticket-form__footer">
                <input
                  hidden
                  multiple
                  ref={fileInputRef}
                  type="file"
                  onChange={handleFileSelection}
                />
                <button
                  className="ticket-form__attachment"
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isSubmitting || isRecordingAudio}
                >
                  <PlusCircleIcon />
                  <span>{attachedFiles.length > 0 ? `Anexar Arquivos (${attachedFiles.length})` : 'Anexar Arquivos'}</span>
                </button>

                <button
                  className={`ticket-form__attachment${isRecordingAudio ? ' ticket-form__attachment--recording' : ''}`}
                  type="button"
                  onClick={handleToggleAudioRecording}
                  disabled={isSubmitting}
                  aria-label={isRecordingAudio ? 'Parar gravação de áudio' : 'Gravar áudio'}
                >
                  <MicIcon />
                  <span>
                    {isRecordingAudio
                      ? `Gravando ${formatAudioDuration(recordingDuration)}`
                      : 'Gravar Áudio'}
                  </span>
                </button>

                <button
                  className="ticket-form__submit"
                  type="submit"
                  disabled={
                    isSubmitting ||
                    isRecordingAudio ||
                    availableCompanies.length === 0 ||
                    !currentUser?.email
                  }
                >
                  {isSubmitting ? 'Criando...' : 'Criar Chamado'}
                </button>
              </div>
              </form>
            )}
          </div>
        </section>
      </div>
      <ConfirmActionModal
        cancelLabel="Cancelar"
        confirmLabel={editingPreRegistrationId ? 'Salvar alterações' : 'Criar cliente'}
        description={editingPreRegistrationId
          ? 'Atualize os dados do pré-cadastro sem perder os chamados associados.'
          : 'Cadastre rapidamente o cliente para continuar a abertura do chamado.'}
        isOpen={preRegistrationOpen}
        isProcessing={isSubmitting}
        onCancel={closePreRegistration}
        onConfirm={handlePreRegistrationSubmit}
        title={editingPreRegistrationId ? 'Editar pré-cadastro' : 'Novo pré-cadastro'}
      >
        <div className="pre-registration-modal__form">
          <label className="ticket-field">
            <span>Nome do cliente</span>
            <div className="ticket-field__control">
              <input
                type="text"
                placeholder="Digite o nome do cliente"
                value={preRegistrationValues.fullName}
                onChange={(event) => setPreRegistrationValues((currentValues) => ({ ...currentValues, fullName: event.target.value }))}
                autoFocus
              />
            </div>
          </label>
          <label className="ticket-field">
            <span>Telefone</span>
            <div className="ticket-field__control">
              <input
                type="tel"
                placeholder="Digite o telefone do cliente"
                value={preRegistrationValues.phoneNumber}
                onChange={(event) => setPreRegistrationValues((currentValues) => ({ ...currentValues, phoneNumber: event.target.value }))}
              />
            </div>
          </label>
          <label className="ticket-field">
            <span>Empresa cliente</span>
            <div className="ticket-field__control ticket-field__control--select">
              <select value={preRegistrationValues.companyOwnerId} onChange={handlePreRegistrationCompanySelect}>
                <option value="" disabled>Selecione a empresa cliente...</option>
                {availableClientCompanies.map((company) => (
                  <option key={company.id} value={company.id}>{company.name}</option>
                ))}
              </select>
              <ChevronDownIcon />
            </div>
          </label>
          {preRegistrationFeedback ? <p className="team-feedback pre-registration-modal__feedback">{preRegistrationFeedback}</p> : null}
        </div>
      </ConfirmActionModal>
      <ConfirmActionModal
        cancelLabel="Cancelar"
        confirmLabel="Excluir pré-cadastro"
        confirmVariant="danger"
        description={preRegistrationToDelete
          ? `Tem certeza que deseja excluir o pré-cadastro de ${preRegistrationToDelete.fullName}? Se houver chamados associados, a exclusão será bloqueada para preservar o histórico.`
          : ''}
        isOpen={Boolean(preRegistrationToDelete)}
        isProcessing={isDeletingPreRegistration}
        onCancel={closePreRegistrationDelete}
        onConfirm={handlePreRegistrationDelete}
        title="Excluir pré-cadastro"
      />
    </main>
  )
}

export default NewTicket
