import { useState } from 'react'
import { loginUser } from '../../api'
import './Login.css'

const formFields = [
  { id: 'email', label: 'Email', type: 'email', icon: MailIcon },
  { id: 'password', label: 'Senha', type: 'password', icon: LockIcon },
]

function Login({ onNavigateHome, onNavigateRegister }) {
  const [credentials, setCredentials] = useState({
    email: '',
    password: '',
  })
  const [isPasswordVisible, setIsPasswordVisible] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

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

  return (
    <main className="auth-page login-page">
      <section className="auth-card">
        <aside className="auth-card__brand">
          <BrandMark />
          <div className="auth-card__welcome">
            <h1>Bem-vindo ao ChamAqui Helpdesk</h1>
            <p>
              Entre na sua conta para acompanhar chamados e centralizar o seu
              atendimento.
            </p>
          </div>
          <button
            className="auth-card__login-button"
            type="button"
            onClick={onNavigateRegister}
          >
            Cadastrar
          </button>
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

            {errorMessage ? <p className="login-form__feedback">{errorMessage}</p> : null}

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
