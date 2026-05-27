import { useEffect, useMemo, useRef, useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import { ChevronDownIcon, PlusCircleIcon } from '../../dashboardIcons'
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
  availableTicketSectors = [],
  currentUser,
  headerProps,
  navigationGroups,
  onCreateTicket,
  onNavigatePage,
}) {
  const activeContent = dashboardPages.newTicket
  const canCreateTickets =
    Array.isArray(currentUser?.roles) &&
    (currentUser.roles.includes('user') ||
      currentUser.roles.includes('admin') ||
      currentUser.roles.includes('employee'))
  const [formValues, setFormValues] = useState({
    companyName: '',
    companyOwnerId: '',
    sectorId: '',
    assignedToUserId: '',
    priorityCode: '',
    copyEmail: '',
    description: '',
  })
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isCompanyOptionsOpen, setIsCompanyOptionsOpen] = useState(false)
  const [attachedFiles, setAttachedFiles] = useState([])
  const fileInputRef = useRef(null)
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

  async function handleSubmit(event) {
    event.preventDefault()

    if (!formValues.companyOwnerId) {
      setFeedbackMessage('Digite ou selecione uma empresa cadastrada para a qual o chamado será enviado.')
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

    if (formValues.description.trim().length < 10) {
      setFeedbackMessage('Escreva a primeira mensagem do chamado com pelo menos 10 caracteres.')
      return
    }

    try {
      setIsSubmitting(true)
      setFeedbackMessage('')
      await onCreateTicket({
        description: formValues.description,
        files: attachedFiles,
        priorityCode: formValues.priorityCode,
        companyOwnerId: formValues.companyOwnerId,
        sectorId: formValues.sectorId,
        assignedToUserId: formValues.assignedToUserId || undefined,
        copyEmail: formValues.copyEmail,
      })
      setFormValues({
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
                    placeholder="Descreva aqui o seu chamado. O assunto sera gerado automaticamente a partir desta mensagem."
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
                >
                  <PlusCircleIcon />
                  <span>{attachedFiles.length > 0 ? `Anexar Arquivos (${attachedFiles.length})` : 'Anexar Arquivos'}</span>
                </button>

                <button
                  className="ticket-form__submit"
                  type="submit"
                  disabled={isSubmitting || availableCompanies.length === 0 || !currentUser?.email}
                >
                  {isSubmitting ? 'Criando...' : 'Criar Chamado'}
                </button>
              </div>
              </form>
            )}
          </div>
        </section>
      </div>
    </main>
  )
}

export default NewTicket
