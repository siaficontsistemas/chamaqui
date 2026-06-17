import { useEffect, useMemo, useState } from 'react'
import {
  getAvailableCompanies,
  getRegisterInvite,
  registerUser,
} from '../../api'
import { PUBLIC_ROUTE_PATHS } from '../../routes'
import TenantBrandImage from '../../components/branding/TenantBrandImage'
import { useTenantBranding } from '../../context/TenantBrandingContext'
import './Register.css'

const formFields = [
  { id: 'name', label: 'Nome', type: 'text', icon: UserIcon },
  { id: 'email', label: 'Email', type: 'email', icon: MailIcon },
  { id: 'phone', label: 'Telefone', type: 'tel', icon: PhoneIcon },
  { id: 'cpf', label: 'CPF', type: 'text', icon: DocumentIcon },
]

const adminOnlyFields = [
  { id: 'companyName', label: 'Nome da empresa', type: 'text', icon: BuildingIcon },
  { id: 'companyCnpj', label: 'CNPJ da empresa', type: 'text', icon: DocumentIcon },
]

const passwordFields = [
  { id: 'password', label: 'Senha', type: 'password', icon: LockIcon },
  {
    id: 'passwordConfirm',
    label: 'Digite novamente',
    type: 'password',
    icon: LockIcon,
  },
]

const INITIAL_FORM_VALUES = {
  name: '',
  email: '',
  phone: '',
  cpf: '',
  companyName: '',
  companyCnpj: '',
  companyType: '',
  companyOwnerId: '',
  password: '',
  passwordConfirm: '',
}

function Register({ onNavigateHome, onNavigateLogin }) {
  const inviteToken =
    typeof window !== 'undefined'
      ? new URLSearchParams(window.location.search).get('companyInvite') || ''
      : ''
  const { branding: tenantBranding, companyLogoUrl: tenantLogoUrl, isTenantExperience } =
    useTenantBranding()
  const [selectedRole, setSelectedRole] = useState('')
  const [selectedParticipation, setSelectedParticipation] = useState('')
  const [formValues, setFormValues] = useState(INITIAL_FORM_VALUES)
  const [inviteContext, setInviteContext] = useState(null)
  const [availableCompanies, setAvailableCompanies] = useState([])
  const [isLoadingCompanies, setIsLoadingCompanies] = useState(false)
  const [isLoadingInvite, setIsLoadingInvite] = useState(false)
  const [passwordVisibility, setPasswordVisibility] = useState({
    password: false,
    passwordConfirm: false,
  })
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [acceptedPrivacyPolicy, setAcceptedPrivacyPolicy] = useState(false)
  const [isTermsModalOpen, setIsTermsModalOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [pendingApprovalRegistration, setPendingApprovalRegistration] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const participantCompanyType = useMemo(
    () => getParticipantCompanyType(selectedParticipation),
    [selectedParticipation]
  )
  const tenantRegistrationOptions = useMemo(
    () => getTenantRegistrationOptions(tenantBranding),
    [tenantBranding]
  )
  const selectedRoleLabel = selectedRole === 'admin' ? 'Administrador' : 'Usuário'
  const selectedParticipationLabel =
    getSelectedParticipationLabel(selectedParticipation, tenantBranding, isTenantExperience)
  const isResponderTenant = String(tenantBranding?.companyType || '').toUpperCase() === 'RESPONDER'
  const requiresTenantApproval =
    isTenantExperience &&
    selectedRole === 'user' &&
    isResponderTenant &&
    (selectedParticipation === 'requester' || selectedParticipation === 'responder')
  const visibleFormFields = [
    ...formFields,
    ...(selectedRole === 'admin' ? adminOnlyFields : []),
    ...passwordFields,
  ]

  useEffect(() => {
    let ignore = false

    async function loadInviteContext() {
      if (!inviteToken) {
        setInviteContext(null)
        setIsLoadingInvite(false)
        return
      }

      try {
        setIsLoadingInvite(true)
        setErrorMessage('')
        const invite = await getRegisterInvite(inviteToken)

        if (ignore) {
          return
        }

        setInviteContext(invite)
        setSelectedRole('user')
        setSelectedParticipation(invite.participation || '')
        setFormValues((currentValues) => ({
          ...currentValues,
          name: invite.fullName || '',
          email: invite.email || '',
          cpf: invite.documentNumber || '',
          companyOwnerId: '',
        }))
      } catch (error) {
        if (!ignore) {
          setInviteContext(null)
          setErrorMessage(error.message)
        }
      } finally {
        if (!ignore) {
          setIsLoadingInvite(false)
        }
      }
    }

    loadInviteContext()

    return () => {
      ignore = true
    }
  }, [inviteToken])

  useEffect(() => {
    if (!tenantBranding?.tenantResolved || inviteContext) {
      return
    }

    setAvailableCompanies([])
    setSelectedRole('')
    setSelectedParticipation('')
    setFormValues((currentValues) => ({
      ...currentValues,
      companyOwnerId: '',
    }))
  }, [inviteContext, tenantBranding])

  useEffect(() => {
    let ignore = false

    async function loadCompanies() {
      if (inviteContext || selectedRole !== 'user' || !participantCompanyType) {
        setAvailableCompanies([])
        setIsLoadingCompanies(false)
        return
      }

      try {
        setErrorMessage('')
        setAvailableCompanies([])
        setIsLoadingCompanies(true)
        const companies = await getAvailableCompanies(participantCompanyType)
        if (!ignore) {
          setAvailableCompanies(companies)
        }
      } catch (error) {
        if (!ignore) {
          setAvailableCompanies([])
          setErrorMessage(error.message)
        }
      } finally {
        if (!ignore) {
          setIsLoadingCompanies(false)
        }
      }
    }

    loadCompanies()

    return () => {
      ignore = true
    }
  }, [inviteContext, isTenantExperience, participantCompanyType, selectedRole])

  function handleSelectRole(role) {
    if (inviteContext || isTenantExperience) {
      return
    }

    setSelectedRole(role)
    setSelectedParticipation('')
    setErrorMessage('')
    setSuccessMessage('')
    setPendingApprovalRegistration(null)
    setAvailableCompanies([])
    setIsLoadingCompanies(false)
    setFormValues((currentValues) => ({
      ...currentValues,
      companyName: role === 'admin' ? currentValues.companyName : '',
      companyCnpj: role === 'admin' ? currentValues.companyCnpj : '',
      companyType: '',
      companyOwnerId: '',
    }))
  }

  function handleSelectTenantRegistration(participation) {
    if (!isTenantExperience || inviteContext) {
      return
    }

    setSelectedRole('user')
    setSelectedParticipation(participation)
    setErrorMessage('')
    setSuccessMessage('')
    setPendingApprovalRegistration(null)
    setAvailableCompanies([])
    setIsLoadingCompanies(false)
    setFormValues((currentValues) => ({
      ...currentValues,
      companyOwnerId:
        participation === 'responder' ? tenantBranding?.ownerUserId || '' : '',
    }))
  }

  function handleSelectParticipation(participation) {
    if (inviteContext) {
      return
    }

    setSelectedParticipation(participation)
    setErrorMessage('')
    setSuccessMessage('')
    setPendingApprovalRegistration(null)
    setFormValues((currentValues) => ({
      ...currentValues,
      companyOwnerId:
        isTenantExperience && participation === 'responder' ? tenantBranding?.ownerUserId || '' : '',
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const normalizedEmail = formValues.email.trim().toLowerCase()
    const normalizedPhone = normalizePhoneNumber(formValues.phone)
    const normalizedCpf = formValues.cpf.replace(/\D/g, '')
    const normalizedCompanyCnpj = formValues.companyCnpj.replace(/\D/g, '')

    if (!selectedRole) {
      setErrorMessage('Escolha o tipo de cadastro para continuar.')
      return
    }

    if (selectedRole === 'user' && !selectedParticipation) {
      setErrorMessage(
        isTenantExperience
          ? 'Escolha primeiro para qual tipo de empresa esse cadastro será feito.'
          : 'Escolha se esse usuário vai criar ou responder chamados.'
      )
      return
    }

    if (
      selectedRole === 'user' &&
      isTenantExperience &&
      String(tenantBranding?.companyType || '').toUpperCase() === 'RESPONDER' &&
      selectedParticipation === 'requester' &&
      !formValues.companyOwnerId
    ) {
      setErrorMessage('Selecione a empresa cliente para concluir esse cadastro.')
      return
    }

    if (selectedRole === 'admin' && !formValues.companyType) {
      setErrorMessage('Selecione se a empresa vai criar ou responder chamados.')
      return
    }

    const missingFieldLabels = visibleFormFields
      .filter((field) => !formValues[field.id].trim())
      .map((field) => field.label)

    if (missingFieldLabels.length > 0) {
      setErrorMessage(
        `Erro no cadastro: preencha os seguintes dados obrigatórios: ${missingFieldLabels.join(', ')}.`
      )
      return
    }

    if (!isValidEmailFormat(normalizedEmail)) {
      setErrorMessage('Erro no cadastro: o dado "Email" está inválido.')
      return
    }

    if (!isValidCpf(normalizedCpf)) {
      setErrorMessage('Erro no cadastro: o dado "CPF" está inválido.')
      return
    }

    if (selectedRole === 'admin' && normalizedCompanyCnpj.length !== 14) {
      setErrorMessage('Erro no cadastro: o dado "CNPJ da empresa" está inválido.')
      return
    }

    if (formValues.password !== formValues.passwordConfirm) {
      setErrorMessage('Erro no cadastro: os dados "Senha" e "Digite novamente" precisam ser iguais.')
      return
    }

    if (!acceptedTerms) {
      setErrorMessage('Você precisa concordar com os termos para concluir o cadastro.')
      return
    }

    if (!acceptedPrivacyPolicy) {
      setErrorMessage('Você precisa concordar com a politica de privacidade para concluir o cadastro.')
      return
    }

    try {
      setIsSubmitting(true)
      setErrorMessage('')
      setSuccessMessage('')

      const selectedCompany = availableCompanies.find(
        (company) => company.id === formValues.companyOwnerId
      )
      const effectiveCompanyOwnerId =
        selectedRole === 'user'
          ? isTenantExperience
            ? selectedParticipation === 'responder'
              ? tenantBranding?.ownerUserId || formValues.companyOwnerId
              : formValues.companyOwnerId
            : formValues.companyOwnerId
          : null
      const effectiveCompanyType =
        selectedRole === 'admin'
          ? formValues.companyType
          : participantCompanyType
      const effectiveSelectedCompanyName =
        selectedParticipation === 'responder'
          ? tenantBranding?.companyName || selectedCompany?.name || 'empresa selecionada'
          : selectedCompany?.name || tenantBranding?.companyName || 'empresa selecionada'

      const roleToSubmit = selectedRole === 'admin' ? 'admin' : getParticipantRole(selectedParticipation)
      const user = await registerUser({
        fullName: formValues.name.trim(),
        email: normalizedEmail,
        phoneNumber: normalizedPhone,
        documentNumber: normalizedCpf,
        companyOwnerId: effectiveCompanyOwnerId,
        companyName: selectedRole === 'admin' ? formValues.companyName.trim() : null,
        companyDocument: selectedRole === 'admin' ? normalizedCompanyCnpj : null,
        companyType: effectiveCompanyType,
        password: formValues.password,
        role: roleToSubmit,
        inviteToken: inviteToken || null,
        acceptedTerms,
        acceptedPrivacyPolicy,
      })

      if (selectedRole === 'user' && user?.status === 'PENDING') {
        setPendingApprovalRegistration({
          companyName: effectiveSelectedCompanyName,
          participationLabel: selectedParticipationLabel || 'Criar chamados',
        })
        setAcceptedTerms(false)
        setAcceptedPrivacyPolicy(false)
        setFormValues((currentValues) => ({
          ...INITIAL_FORM_VALUES,
          companyOwnerId:
            isTenantExperience && selectedParticipation === 'requester'
              ? currentValues.companyOwnerId
              : INITIAL_FORM_VALUES.companyOwnerId,
        }))
        setSuccessMessage(
          `Cadastro concluído. Sua solicitação foi enviada e precisa ser aprovada por um administrador da empresa ${tenantBranding?.companyName || effectiveSelectedCompanyName} antes do acesso.`
        )
        return
      }

      if (inviteContext) {
        setSuccessMessage(`Cadastro concluído. Você entrou diretamente na empresa ${inviteContext.companyName}.`)
      }

      onNavigateHome(user)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <aside className="auth-card__brand">
          {isTenantExperience && tenantLogoUrl ? (
            <div className="auth-card__tenant-brand" aria-label={tenantBranding.companyName}>
              <TenantBrandImage
                className="auth-card__tenant-logo"
                src={tenantLogoUrl}
                alt={tenantBranding.companyName}
                label={tenantBranding.companyName}
              />
            </div>
          ) : null}
          <BrandMark />
          <div className="auth-card__welcome">
            <h1>
              {isTenantExperience
                ? `Cadastro em ${tenantBranding.companyName}`
                : 'Bem-vindo ao ChamAqui Helpdesk'}
            </h1>
            <p>
              {isTenantExperience
                ? 'Conclua seu cadastro para entrar diretamente no portal da empresa dentro do ChamaAqui Helpdesk.'
                : 'Acesse sua conta para acompanhar chamados e solicitações em um só lugar.'}
            </p>
          </div>
          <button
            className="auth-card__login-button"
            type="button"
            onClick={onNavigateLogin}
          >
            Entrar
          </button>
        </aside>

        <section className="auth-card__form-section">
          <div className="auth-card__form-header">
            <h2>Crie sua Conta Aqui</h2>
            <p>
              {inviteContext
                ? `Você está entrando na empresa ${inviteContext.companyName}. Confira os dados e conclua o cadastro.`
                : isTenantExperience
                  ? requiresTenantApproval
                    ? selectedParticipation === 'requester'
                      ? `Seu cadastro será enviado para aprovação do administrador da empresa ${tenantBranding.companyName}.`
                      : `Seu cadastro será enviado para aprovação da empresa ${tenantBranding.companyName}.`
                    : `Seu cadastro será vinculado à empresa ${tenantBranding.companyName}.`
                : selectedRole
                  ? `Preencha os dados para concluir seu cadastro como ${selectedRoleLabel.toLowerCase()}.`
                  : 'Primeiro escolha como deseja se cadastrar.'}
            </p>
          </div>

          <form className="signup-form" onSubmit={handleSubmit}>
            {selectedRole ? (
              <>
                <div className="signup-form__step-header">
                  <div className="signup-form__selected-role">
                    <span>Cadastrando como</span>
                    <strong>{selectedRoleLabel}</strong>
                    {selectedParticipationLabel ? <span>• {selectedParticipationLabel}</span> : null}
                  </div>

                  {!inviteContext ? (
                    <button
                      className="signup-form__change-role"
                      type="button"
                      onClick={() => {
                        setSelectedRole('')
                        setSelectedParticipation('')
                        setAvailableCompanies([])
                        setErrorMessage('')
                        setSuccessMessage('')
                        setFormValues((currentValues) => ({
                          ...currentValues,
                          companyName: '',
                          companyCnpj: '',
                          companyType: '',
                          companyOwnerId: '',
                        }))
                      }}
                    >
                      {isTenantExperience ? 'Alterar opção' : 'Alterar tipo'}
                    </button>
                  ) : null}
                </div>

                {inviteContext ? (
                  <div className="signup-form__flow-block">
                    <span className="signup-form__role-label">Convite da empresa</span>
                    <p className="signup-form__role-text">
                      Seu cadastro será vinculado automaticamente à empresa {inviteContext.companyName}
                      {' '}com permissão para {selectedParticipationLabel.toLowerCase()}.
                    </p>
                  </div>
                ) : isTenantExperience ? (
                  <div className="signup-form__flow-block">
                    <span className="signup-form__role-label">Empresa identificada pelo subdomínio</span>
                    {requiresTenantApproval ? (
                      <p className="signup-form__role-text">
                        {selectedParticipation === 'requester'
                          ? `Você está se cadastrando para uma empresa cliente da ${tenantBranding.companyName}. Escolha abaixo a empresa cliente em que vai atuar. A aprovação será feita pelo administrador da empresa ${tenantBranding.companyName}.`
                          : `Você está se cadastrando para responder chamados na empresa ${tenantBranding.companyName}. Esse acesso ficará aguardando aprovação de um administrador.`}
                      </p>
                    ) : (
                      <p className="signup-form__role-text">
                        Seu cadastro será vinculado automaticamente à empresa {tenantBranding.companyName}
                        {' '}com permissão para {selectedParticipationLabel.toLowerCase()}.
                      </p>
                    )}
                  </div>
                ) : null}

                {selectedRole === 'user' && !isTenantExperience ? (
                  <div className="signup-form__flow-block">
                    <span className="signup-form__role-label">Como esse usuário vai participar?</span>

                    <div className="signup-form__role-options signup-form__role-options--two">
                      <button
                        className={`signup-form__role-card ${selectedParticipation === 'requester' ? 'is-active' : ''}`}
                        type="button"
                        onClick={() => handleSelectParticipation('requester')}
                        disabled={Boolean(inviteContext)}
                      >
                        <span className="signup-form__role-title">Criar chamados</span>
                        <span className="signup-form__role-text">
                          Mostra somente as empresas que abrem chamados e tiram duvidas.
                        </span>
                      </button>

                      <button
                        className={`signup-form__role-card ${selectedParticipation === 'responder' ? 'is-active' : ''}`}
                        type="button"
                        onClick={() => handleSelectParticipation('responder')}
                        disabled={Boolean(inviteContext)}
                      >
                        <span className="signup-form__role-title">Responder chamados</span>
                        <span className="signup-form__role-text">
                          Mostra somente as empresas que atendem e respondem os chamados.
                        </span>
                      </button>
                    </div>
                  </div>
                ) : null}

                {selectedRole === 'admin' ? (
                  <div className="signup-form__flow-block">
                    <span className="signup-form__role-label">Qual sera o tipo da empresa?</span>

                    <div className="signup-form__role-options signup-form__role-options--two">
                      <button
                        className={`signup-form__role-card ${formValues.companyType === 'requester' ? 'is-active' : ''}`}
                        type="button"
                        onClick={() => {
                          setFormValues((currentValues) => ({
                            ...currentValues,
                            companyType: 'requester',
                          }))
                          setSuccessMessage('')
                        }}
                      >
                        <span className="signup-form__role-title">Empresa solicitante</span>
                        <span className="signup-form__role-text">
                          Empresa responsavel por abrir chamados e registrar duvidas.
                        </span>
                      </button>

                      <button
                        className={`signup-form__role-card ${formValues.companyType === 'responder' ? 'is-active' : ''}`}
                        type="button"
                        onClick={() => {
                          setFormValues((currentValues) => ({
                            ...currentValues,
                            companyType: 'responder',
                          }))
                          setSuccessMessage('')
                        }}
                      >
                        <span className="signup-form__role-title">Empresa prestadora</span>
                        <span className="signup-form__role-text">
                          Empresa responsavel por atender e responder os chamados.
                        </span>
                      </button>
                    </div>
                  </div>
                ) : null}

                {pendingApprovalRegistration ? (
                  <div className="signup-form__flow-block">
                    <span className="signup-form__role-label">Cadastro enviado para aprovação</span>
                    <p className="signup-form__role-text">
                      O acesso para {pendingApprovalRegistration.participationLabel.toLowerCase()} foi solicitado
                      para a empresa {pendingApprovalRegistration.companyName}. Assim que um administrador da empresa{' '}
                      {tenantBranding?.companyName || 'provedora'} aprovar, essa pessoa poderá entrar com o email e a
                      senha cadastrados.
                    </p>
                    <button
                      className="signup-form__change-role"
                      type="button"
                      onClick={() => {
                        setPendingApprovalRegistration(null)
                        setSuccessMessage('')
                        setErrorMessage('')
                      }}
                    >
                      Fazer outro cadastro
                    </button>
                  </div>
                ) : visibleFormFields.map((field) => (
                  <label className="form-field" htmlFor={field.id} key={field.id}>
                    <span className="form-field__icon" aria-hidden="true">
                      {field.icon()}
                    </span>
                    <input
                      id={field.id}
                      name={field.id}
                      type={passwordVisibility[field.id] ? 'text' : field.type}
                      placeholder={field.label}
                      value={formValues[field.id]}
                      onChange={(event) => {
                        setSuccessMessage('')
                        setFormValues((currentValues) => ({
                          ...currentValues,
                          [field.id]: event.target.value,
                        }))
                      }}
                    />
                    {field.type === 'password' ? (
                      <button
                        className="form-field__toggle"
                        type="button"
                        aria-label={
                          passwordVisibility[field.id] ? 'Ocultar senha' : 'Mostrar senha'
                        }
                        aria-pressed={passwordVisibility[field.id]}
                        onClick={() =>
                          setPasswordVisibility((currentVisibility) => ({
                            ...currentVisibility,
                            [field.id]: !currentVisibility[field.id],
                          }))
                        }
                      >
                        {passwordVisibility[field.id] ? <EyeOffIcon /> : <EyeIcon />}
                      </button>
                    ) : null}
                  </label>
                ))}

                {!pendingApprovalRegistration &&
                selectedRole === 'user' &&
                selectedParticipation &&
                !inviteContext &&
                (
                  !isTenantExperience ||
                  (
                    String(tenantBranding?.companyType || '').toUpperCase() === 'RESPONDER' &&
                    selectedParticipation === 'requester'
                  )
                ) ? (
                  <>
                    <label className="form-field form-field--select" htmlFor="companyOwnerId">
                      <span className="form-field__icon" aria-hidden="true">
                        <BuildingIcon />
                      </span>
                      <select
                        id="companyOwnerId"
                        name="companyOwnerId"
                        value={formValues.companyOwnerId}
                        onChange={(event) => {
                          setSuccessMessage('')
                          setFormValues((currentValues) => ({
                            ...currentValues,
                            companyOwnerId: event.target.value,
                          }))
                        }}
                        disabled={isLoadingCompanies}
                      >
                        <option value="">
                          {isLoadingCompanies
                            ? 'Carregando empresas...'
                            : availableCompanies.length > 0
                              ? isTenantExperience
                                ? 'Selecione a empresa cliente'
                                : 'Selecione a empresa (opcional)'
                              : 'Nenhuma empresa disponivel'}
                        </option>
                        {availableCompanies.map((company) => (
                          <option key={company.id} value={company.id}>
                            {company.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    {isTenantExperience ? (
                      <p className="signup-form__role-text">
                        Escolha em qual empresa cliente essa pessoa vai atuar. A aprovação ficará com a empresa{' '}
                        {tenantBranding.companyName}.
                      </p>
                    ) : (
                      <p className="signup-form__role-text">
                        Se você deixar a empresa em branco, sua conta será criada normalmente e convites
                        pendentes para empresa aparecerão como notificação depois do acesso.
                      </p>
                    )}
                  </>
                ) : null}

                <div className="signup-form__feedback-slot" aria-live="polite">
                  {successMessage ? <p className="signup-form__feedback">{successMessage}</p> : null}
                  {errorMessage ? <p className="signup-form__feedback">{errorMessage}</p> : null}
                </div>

                {!pendingApprovalRegistration ? (
                  <>
                    <label className="terms-check" htmlFor="terms">
                      <input
                        id="terms"
                        name="terms"
                        type="checkbox"
                        checked={acceptedTerms}
                        onChange={(event) => {
                          setSuccessMessage('')
                          setAcceptedTerms(event.target.checked)
                        }}
                      />
                      <span>
                        Eu li e concordo com os{' '}
                        <button
                          className="terms-check__button"
                          type="button"
                          onClick={() => setIsTermsModalOpen(true)}
                        >
                          termos e condições de uso
                        </button>
                      </span>
                    </label>
                    <label className="terms-check" htmlFor="privacy-policy">
                      <input
                        id="privacy-policy"
                        name="privacy-policy"
                        type="checkbox"
                        checked={acceptedPrivacyPolicy}
                        onChange={(event) => {
                          setSuccessMessage('')
                          setAcceptedPrivacyPolicy(event.target.checked)
                        }}
                      />
                      <span>
                        Eu li e concordo com a{' '}
                        <a
                          className="terms-check__button"
                          href={PUBLIC_ROUTE_PATHS.privacy}
                          target="_blank"
                          rel="noreferrer"
                        >
                          politica de privacidade
                        </a>
                      </span>
                    </label>
                  </>
                ) : null}

                {!pendingApprovalRegistration ? (
                <button
                  className="signup-form__submit"
                  type="submit"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Cadastrando...' : 'Cadastrar'}
                </button>
                ) : null}
              </>
            ) : isLoadingInvite ? (
              <div className="signup-form__role-step">
                <span className="signup-form__role-label">Carregando convite...</span>
              </div>
            ) : (
              <div className="signup-form__role-step">
                <span className="signup-form__role-label">
                  {isTenantExperience ? 'Escolha como deseja se cadastrar' : 'Escolha o tipo de cadastro'}
                </span>

                {isTenantExperience ? (
                  <div className="signup-form__role-options signup-form__role-options--two">
                    {tenantRegistrationOptions.map((option) => (
                      <button
                        key={option.participation}
                        className="signup-form__role-card"
                        type="button"
                        onClick={() => handleSelectTenantRegistration(option.participation)}
                      >
                        <span className="signup-form__role-title">{option.title}</span>
                        <span className="signup-form__role-text">{option.description}</span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="signup-form__role-options signup-form__role-options--two">
                    <button
                      className="signup-form__role-card"
                      type="button"
                      onClick={() => handleSelectRole('user')}
                      disabled={isTenantExperience}
                    >
                      <span className="signup-form__role-title">Usuário</span>
                      <span className="signup-form__role-text">
                        Cadastro para participantes que vao criar ou responder chamados dentro de
                        uma empresa.
                      </span>
                    </button>

                    <button
                      className="signup-form__role-card"
                      type="button"
                      onClick={() => handleSelectRole('admin')}
                      disabled={isTenantExperience}
                    >
                      <span className="signup-form__role-title">Administrador</span>
                      <span className="signup-form__role-text">
                        Cadastro para quem vai gerenciar a empresa e definir se ela cria ou
                        responde chamados.
                      </span>
                    </button>
                  </div>
                )}
              </div>
            )}
          </form>
        </section>
      </section>

      {isTermsModalOpen ? <TermsOfUseModal onClose={() => setIsTermsModalOpen(false)} /> : null}
    </main>
  )
}

export default Register

function getParticipantRole(participation) {
  return participation === 'responder' ? 'employee' : 'user'
}

function getParticipantCompanyType(participation) {
  if (participation === 'responder') {
    return 'responder'
  }

  if (participation === 'requester') {
    return 'requester'
  }

  return ''
}

function getTenantParticipationOptions(tenantBranding) {
  const companyType = String(tenantBranding?.companyType || '').toUpperCase()

  if (companyType === 'RESPONDER') {
    return ['requester', 'responder']
  }

  if (companyType === 'REQUESTER') {
    return ['requester']
  }

  return []
}

function getTenantRegistrationOptions(tenantBranding) {
  const companyName = tenantBranding?.companyName || 'empresa do domínio'

  return getTenantParticipationOptions(tenantBranding).map((participation) => {
    if (participation === 'requester') {
      return {
        participation,
        title: `Cadastrar para uma empresa cliente da ${companyName}`,
        description:
          'Esse cadastro será vinculado à empresa cliente escolhida, com aprovação feita pela empresa provedora.',
      }
    }

    return {
      participation,
      title: `Cadastrar como funcionário da empresa ${companyName}`,
      description:
        'Esse cadastro será feito para atuar diretamente na empresa respondedora deste subdomínio.',
    }
  })
}

function getSelectedParticipationLabel(participation, tenantBranding, isTenantExperience) {
  if (participation === 'responder') {
    return isTenantExperience
      ? `Funcionário da empresa ${tenantBranding?.companyName || 'do domínio'}`
      : 'Responder chamados'
  }

  if (participation === 'requester') {
    return isTenantExperience
      ? `Empresa cliente da ${tenantBranding?.companyName || 'provedora'}`
      : 'Criar chamados'
  }

  return ''
}

function isValidEmailFormat(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || ''))
}

function normalizePhoneNumber(value) {
  const digits = String(value || '').replace(/\D/g, '')
  if (digits.startsWith('55') && digits.length === 13) {
    return digits.slice(2)
  }
  return digits
}

function isValidCpf(cpf) {
  if (!/^\d{11}$/.test(cpf) || hasAllDigitsEqual(cpf)) {
    return false
  }

  return (
    calculateCpfCheckDigit(cpf, 9) === Number(cpf[9]) &&
    calculateCpfCheckDigit(cpf, 10) === Number(cpf[10])
  )
}

function calculateCpfCheckDigit(cpf, length) {
  let sum = 0
  let weight = length + 1

  for (let index = 0; index < length; index += 1) {
    sum += Number(cpf[index]) * (weight - index)
  }

  const remainder = (sum * 10) % 11
  return remainder === 10 ? 0 : remainder
}

function hasAllDigitsEqual(value) {
  return String(value || '')
    .split('')
    .every((digit) => digit === value[0])
}

function TermsOfUseModal({ onClose }) {
  return (
    <div className="terms-modal" role="dialog" aria-modal="true" aria-labelledby="terms-title">
      <div className="terms-modal__backdrop" onClick={onClose} />
      <section className="terms-modal__content">
        <div className="terms-modal__header">
          <div>
            <h3 id="terms-title">Termo de Uso do Helpdesk Lopes Consultoria</h3>
            <p>
              Leia com atencao as regras de utilizacao da plataforma antes de concluir o
              cadastro.
            </p>
          </div>

          <button className="terms-modal__close" type="button" onClick={onClose}>
            Fechar
          </button>
        </div>

        <div className="terms-modal__body">
          <section>
            <h4>1. Finalidade da plataforma</h4>
            <p>
              O Helpdesk Lopes Consultoria e uma plataforma destinada ao registro,
              acompanhamento e atendimento de chamados, solicitacoes e comunicacoes entre
              usuarios, funcionarios e administradores vinculados ao projeto.
            </p>
          </section>

          <section>
            <h4>2. Responsabilidade do usuario</h4>
            <p>
              Ao se cadastrar, voce declara que as informacoes fornecidas sao verdadeiras,
              atualizadas e de sua titularidade. O usuario e responsavel por manter a
              confidencialidade de sua senha e por todas as acoes realizadas com sua conta.
            </p>
          </section>

          <section>
            <h4>3. Uso adequado</h4>
            <p>
              E proibido utilizar o sistema para envio de conteudo ilegal, ofensivo,
              fraudulento, enganoso ou que comprometa a seguranca da plataforma, dos dados
              cadastrados ou do atendimento prestado.
            </p>
          </section>

          <section>
            <h4>4. Tratamento de dados</h4>
            <p>
              Os dados informados no cadastro e nas interacoes do sistema podem ser utilizados
              para autenticar acessos, organizar atendimentos, registrar historicos e melhorar
              a operacao do helpdesk, sempre de acordo com a finalidade do projeto.
            </p>
          </section>

          <section>
            <h4>5. Perfis de acesso</h4>
            <p>
              Cada tipo de conta possui permissoes especificas. O usuario concorda em utilizar
              apenas os recursos compativeis com seu perfil e reconhece que acessos indevidos
              podem ser bloqueados ou revisados pela administracao do sistema.
            </p>
          </section>

          <section>
            <h4>6. Disponibilidade e manutencao</h4>
            <p>
              O sistema pode passar por indisponibilidades temporarias, atualizacoes,
              manutencoes ou ajustes sem aviso previo, quando necessario para garantir a
              continuidade e a seguranca da plataforma.
            </p>
          </section>

          <section>
            <h4>7. Aceite</h4>
            <p>
              Ao marcar a opcao de concordancia e concluir o cadastro, voce declara que leu,
              compreendeu e aceita este Termo de Uso para utilizacao do Helpdesk Lopes
              Consultoria.
            </p>
          </section>
        </div>
      </section>
    </div>
  )
}

function BrandMark() {
  return (
    <div className="brand-mark" aria-label="ChamaAqui Helpdesk">
      <img className="brand-mark__logo" src="/logo_chamaqui.png" alt="ChamaAqui Helpdesk" />
    </div>
  )
}

function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 1 1 14 0"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M4 7.5h16v9H4v-9Zm0 .5 8 5 8-5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M7.5 4.5c0 6.627 5.373 12 12 12l2-3.5-4-2-1.5 1.5a10.5 10.5 0 0 1-4.5-4.5L13 6.5l-2-4-3.5 2Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function DocumentIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M6 4.5h12v15H6v-15Zm3 5.5h6M9 14h6M9 7.5H8"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function BuildingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M5 19.5h14M7 19.5v-11l5-3 5 3v11M10 11h.01M14 11h.01M10 14.5h.01M14 14.5h.01"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M7 11V8a5 5 0 1 1 10 0v3m-9 0h8a1 1 0 0 1 1 1v7H7v-7a1 1 0 0 1 1-1Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M2.75 12S6.5 5.75 12 5.75 21.25 12 21.25 12 17.5 18.25 12 18.25 2.75 12 2.75 12Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M12 14.75a2.75 2.75 0 1 0 0-5.5 2.75 2.75 0 0 0 0 5.5Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none">
      <path
        d="M3 3 21 21"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M10.58 6.93A9.77 9.77 0 0 1 12 6.75c5.5 0 9.25 5.25 9.25 5.25a18.8 18.8 0 0 1-3.2 3.74"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M6.63 6.63A18.2 18.2 0 0 0 2.75 12s3.75 5.25 9.25 5.25c1.61 0 3.05-.45 4.31-1.09"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M9.88 9.88A3 3 0 0 0 14.12 14.12"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
