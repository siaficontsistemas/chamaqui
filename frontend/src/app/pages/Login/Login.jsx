import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { loginUser } from '../../api'
import TenantBrandImage from '../../components/branding/TenantBrandImage'
import { useTenantBranding } from '../../context/TenantBrandingContext'
import { isPlatformAdminHost } from '../../platformAdminHost'
import { PUBLIC_ROUTE_PATHS } from '../../routes'
import './Login.css'

const formFields = [
  { id: 'email', label: 'Email', type: 'email', icon: MailIcon },
  { id: 'password', label: 'Senha', type: 'password', icon: LockIcon },
]

function Login({ onNavigateHome, onNavigateRegister, onRequestPasswordReset, onResetPassword }) {
  const location = useLocation()
  const navigate = useNavigate()
  const isAdminHost = isPlatformAdminHost()
  const { branding: tenantBranding, companyLogoUrl: tenantLogoUrl, isTenantExperience } =
    useTenantBranding()
  const resetPasswordToken = useMemo(
    () => new URLSearchParams(location.search).get('resetPasswordToken') || '',
    [location.search]
  )
  const [credentials, setCredentials] = useState({
    email: '',
    password: '',
  })
  const [isPasswordVisible, setIsPasswordVisible] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false)
  const [passwordRecoveryStep, setPasswordRecoveryStep] = useState('request')
  const [passwordRecoveryForm, setPasswordRecoveryForm] = useState({
    email: '',
    password: '',
    confirmPassword: '',
  })
  const [passwordRecoveryFeedback, setPasswordRecoveryFeedback] = useState('')
  const [passwordRecoveryError, setPasswordRecoveryError] = useState('')
  const [isPasswordRecoverySubmitting, setIsPasswordRecoverySubmitting] = useState(false)
  const [passwordRecoveryVisibility, setPasswordRecoveryVisibility] = useState({
    password: false,
    confirmPassword: false,
  })
  useEffect(() => {
    if (resetPasswordToken) {
      setPasswordRecoveryStep('reset')
      setIsPasswordModalOpen(true)
      setPasswordRecoveryFeedback('')
      setPasswordRecoveryError('')
    }
  }, [resetPasswordToken])

  async function handleSubmit(event) {
    event.preventDefault()

    if (!credentials.email.trim() || !credentials.password) {
      setErrorMessage('Informe o email e a senha para continuar.')
      return
    }

    try {
      setIsSubmitting(true)
      setErrorMessage('')
      const user = await loginUser({
        email: credentials.email.trim(),
        password: credentials.password,
      })
      onNavigateHome(user)
    } catch (error) {
      setErrorMessage(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleOpenForgotPasswordModal() {
    setPasswordRecoveryStep('request')
    setIsPasswordModalOpen(true)
    setPasswordRecoveryFeedback('')
    setPasswordRecoveryError('')
    setPasswordRecoveryForm((currentForm) => ({
      ...currentForm,
      email: credentials.email.trim() || currentForm.email,
      password: '',
      confirmPassword: '',
    }))
  }

  function handleClosePasswordModal() {
    if (isPasswordRecoverySubmitting) {
      return
    }

    setIsPasswordModalOpen(false)
    setPasswordRecoveryFeedback('')
    setPasswordRecoveryError('')
    setPasswordRecoveryStep('request')
    setPasswordRecoveryForm((currentForm) => ({
      ...currentForm,
      password: '',
      confirmPassword: '',
    }))
    setPasswordRecoveryVisibility({
      password: false,
      confirmPassword: false,
    })

    if (resetPasswordToken) {
      navigate(PUBLIC_ROUTE_PATHS.login, { replace: true })
    }
  }

  async function handleSubmitPasswordRecovery(event) {
    event.preventDefault()

    if (passwordRecoveryStep === 'request') {
      if (!passwordRecoveryForm.email.trim()) {
        setPasswordRecoveryError('Informe o email para recuperar a senha.')
        return
      }

      try {
        setIsPasswordRecoverySubmitting(true)
        setPasswordRecoveryError('')
        setPasswordRecoveryFeedback('')
        const response = await onRequestPasswordReset?.(passwordRecoveryForm.email.trim())
        setPasswordRecoveryFeedback(
          response?.message || 'Se o email estiver cadastrado, enviaremos um link para redefinir a senha.'
        )
      } catch (error) {
        setPasswordRecoveryError(error.message || 'Não foi possível solicitar a recuperação de senha.')
      } finally {
        setIsPasswordRecoverySubmitting(false)
      }

      return
    }

    if (!passwordRecoveryForm.password || !passwordRecoveryForm.confirmPassword) {
      setPasswordRecoveryError('Informe a nova senha e repita a senha para continuar.')
      return
    }

    if (passwordRecoveryForm.password !== passwordRecoveryForm.confirmPassword) {
      setPasswordRecoveryError('A nova senha e a confirmação precisam ser iguais.')
      return
    }

    try {
      setIsPasswordRecoverySubmitting(true)
      setPasswordRecoveryError('')
      setPasswordRecoveryFeedback('')
      const response = await onResetPassword?.({
        token: resetPasswordToken,
        password: passwordRecoveryForm.password,
        confirmPassword: passwordRecoveryForm.confirmPassword,
      })
      setPasswordRecoveryFeedback(response?.message || 'Senha redefinida com sucesso.')
      setPasswordRecoveryForm((currentForm) => ({
        ...currentForm,
        password: '',
        confirmPassword: '',
      }))
      window.setTimeout(() => {
        navigate(PUBLIC_ROUTE_PATHS.login, { replace: true })
        setIsPasswordModalOpen(false)
        setPasswordRecoveryStep('request')
      }, 1200)
    } catch (error) {
      setPasswordRecoveryError(error.message || 'Não foi possível redefinir a senha.')
    } finally {
      setIsPasswordRecoverySubmitting(false)
    }
  }

  return (
    <main className="auth-page login-page">
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
                ? `Bem-vindo a ${tenantBranding.companyName}`
                : 'Bem-vindo ao ChamAqui Helpdesk'}
            </h1>
            <p>
              {isTenantExperience
                ? 'Entre na sua conta para acessar o portal da empresa dentro do ChamaAqui Helpdesk.'
                : 'Entre na sua conta para acompanhar chamados e centralizar o seu atendimento.'}
            </p>
          </div>
          {!isAdminHost ? (
            <button
              className="auth-card__login-button"
              type="button"
              onClick={onNavigateRegister}
            >
              Cadastrar
            </button>
          ) : null}
        </aside>

        <section className="auth-card__form-section">
          <div className="auth-card__form-header">
            <h2>Login</h2>
          </div>

          <form className="login-form" onSubmit={handleSubmit}>
            {formFields.map((field) => (
              <label className="form-field" htmlFor={field.id} key={field.id}>
                <span className="form-field__icon" aria-hidden="true">
                  {field.icon()}
                </span>
                <input
                  id={field.id}
                  name={field.id}
                  type={field.id === 'password' && isPasswordVisible ? 'text' : field.type}
                  placeholder={field.label}
                  value={credentials[field.id]}
                  onChange={(event) =>
                    setCredentials((currentCredentials) => ({
                      ...currentCredentials,
                      [field.id]: event.target.value,
                    }))
                  }
                />
                {field.id === 'password' ? (
                  <button
                    className="form-field__toggle"
                    type="button"
                    aria-label={isPasswordVisible ? 'Ocultar senha' : 'Mostrar senha'}
                    aria-pressed={isPasswordVisible}
                    onClick={() => setIsPasswordVisible((currentValue) => !currentValue)}
                  >
                    {isPasswordVisible ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                ) : null}
              </label>
            ))}

            {!isAdminHost ? (
              <button
                className="login-form__forgot-button"
                type="button"
                onClick={handleOpenForgotPasswordModal}
              >
                Esqueceu a senha?
              </button>
            ) : null}

            {errorMessage ? <p className="login-form__feedback">{errorMessage}</p> : null}

            {!isAdminHost ? (
              <p className="login-form__helper">
                Não tem conta no Helpdesk ainda? Faça seu{' '}
                <button
                  className="login-form__text-button"
                  type="button"
                  onClick={onNavigateRegister}
                >
                  cadastro
                </button>
                .
              </p>
            ) : null}

            <button
              className="auth-card__submit-button"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        </section>
      </section>

      {isPasswordModalOpen ? (
        <div className="login-modal" role="dialog" aria-modal="true" aria-labelledby="password-help-title">
          <button
            className="login-modal__backdrop"
            type="button"
            aria-label="Fechar recuperação de senha"
            onClick={handleClosePasswordModal}
          />
          <section className="login-modal__content">
            <div className="login-modal__header">
              <div>
                <h3 id="password-help-title">
                  {passwordRecoveryStep === 'reset' ? 'Redefinir senha' : 'Esqueci minha senha'}
                </h3>
                <p>
                  {passwordRecoveryStep === 'reset'
                    ? 'Informe a nova senha e repita a senha para concluir a redefinição.'
                    : 'Informe seu email para receber o link de redefinição de senha.'}
                </p>
              </div>
              <button className="login-modal__close" type="button" onClick={handleClosePasswordModal}>
                Fechar
              </button>
            </div>

            <form className="login-modal__form" onSubmit={handleSubmitPasswordRecovery}>
              {passwordRecoveryStep === 'request' ? (
                <label className="form-field" htmlFor="password-recovery-email">
                  <span className="form-field__icon" aria-hidden="true">
                    <MailIcon />
                  </span>
                  <input
                    id="password-recovery-email"
                    type="email"
                    placeholder="Email"
                    value={passwordRecoveryForm.email}
                    onChange={(event) =>
                      setPasswordRecoveryForm((currentForm) => ({
                        ...currentForm,
                        email: event.target.value,
                      }))
                    }
                  />
                </label>
              ) : (
                <>
                  <label className="form-field" htmlFor="password-recovery-password">
                    <span className="form-field__icon" aria-hidden="true">
                      <LockIcon />
                    </span>
                    <input
                      id="password-recovery-password"
                      type={passwordRecoveryVisibility.password ? 'text' : 'password'}
                      placeholder="Nova senha"
                      value={passwordRecoveryForm.password}
                      onChange={(event) =>
                        setPasswordRecoveryForm((currentForm) => ({
                          ...currentForm,
                          password: event.target.value,
                        }))
                      }
                    />
                    <button
                      className="form-field__toggle"
                      type="button"
                      aria-label={passwordRecoveryVisibility.password ? 'Ocultar senha' : 'Mostrar senha'}
                      aria-pressed={passwordRecoveryVisibility.password}
                      onClick={() =>
                        setPasswordRecoveryVisibility((currentState) => ({
                          ...currentState,
                          password: !currentState.password,
                        }))
                      }
                    >
                      {passwordRecoveryVisibility.password ? <EyeOffIcon /> : <EyeIcon />}
                    </button>
                  </label>

                  <label className="form-field" htmlFor="password-recovery-confirm-password">
                    <span className="form-field__icon" aria-hidden="true">
                      <LockIcon />
                    </span>
                    <input
                      id="password-recovery-confirm-password"
                      type={passwordRecoveryVisibility.confirmPassword ? 'text' : 'password'}
                      placeholder="Repita a nova senha"
                      value={passwordRecoveryForm.confirmPassword}
                      onChange={(event) =>
                        setPasswordRecoveryForm((currentForm) => ({
                          ...currentForm,
                          confirmPassword: event.target.value,
                        }))
                      }
                    />
                    <button
                      className="form-field__toggle"
                      type="button"
                      aria-label={
                        passwordRecoveryVisibility.confirmPassword ? 'Ocultar senha' : 'Mostrar senha'
                      }
                      aria-pressed={passwordRecoveryVisibility.confirmPassword}
                      onClick={() =>
                        setPasswordRecoveryVisibility((currentState) => ({
                          ...currentState,
                          confirmPassword: !currentState.confirmPassword,
                        }))
                      }
                    >
                      {passwordRecoveryVisibility.confirmPassword ? <EyeOffIcon /> : <EyeIcon />}
                    </button>
                  </label>
                </>
              )}

              {passwordRecoveryFeedback ? (
                <p className="login-form__feedback login-form__feedback--success">
                  {passwordRecoveryFeedback}
                </p>
              ) : null}
              {passwordRecoveryError ? <p className="login-form__feedback">{passwordRecoveryError}</p> : null}

              <button
                className="auth-card__submit-button"
                type="submit"
                disabled={isPasswordRecoverySubmitting}
              >
                {isPasswordRecoverySubmitting
                  ? passwordRecoveryStep === 'reset'
                    ? 'Redefinindo...'
                    : 'Enviando...'
                  : passwordRecoveryStep === 'reset'
                    ? 'Redefinir senha'
                    : 'Enviar link'}
              </button>
            </form>
          </section>
        </div>
      ) : null}
    </main>
  )
}

export default Login

function BrandMark() {
  return (
    <div className="brand-mark" aria-label="ChamaAqui Helpdesk">
      <img className="brand-mark__logo" src="/logo_chamaqui.png" alt="ChamaAqui Helpdesk" />
    </div>
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
