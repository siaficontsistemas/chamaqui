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
            <h1>Bem-vindo ao helpdesk da Lopes Consultoria</h1>
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
                  type={field.type}
                  placeholder={field.label}
                  value={credentials[field.id]}
                  onChange={(event) =>
                    setCredentials((currentCredentials) => ({
                      ...currentCredentials,
                      [field.id]: event.target.value,
                    }))
                  }
                />
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

            <div className="login-form__divider">
              <span>ou fazer login com</span>
            </div>

            <button className="google-button" type="button">
              <GoogleIcon />
              Continue with Google
            </button>

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
    <div className="brand-mark" aria-label="Lopes Consultoria">
      <strong className="brand-mark__name">LOPES</strong>
      <span className="brand-mark__accent">CONSULTORIA</span>
      <span className="brand-mark__subtitle">GESTÃO PÚBLICA</span>
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

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M21.8 12.23c0-.69-.06-1.2-.2-1.74H12v3.45h5.64c-.11.86-.7 2.16-2 3.03l-.02.12 2.81 2.17.19.02c1.73-1.58 2.73-3.9 2.73-7.05Z"
        fill="#4285F4"
      />
      <path
        d="M12 22c2.76 0 5.07-.9 6.76-2.44l-3.22-2.5c-.86.59-2.01 1-3.54 1-2.7 0-4.99-1.77-5.8-4.22l-.12.01-2.92 2.25-.04.11A10.2 10.2 0 0 0 12 22Z"
        fill="#34A853"
      />
      <path
        d="M6.2 13.84A6.13 6.13 0 0 1 5.86 12c0-.64.12-1.25.32-1.84l-.01-.12-2.96-2.3-.1.04A10.06 10.06 0 0 0 2 12c0 1.62.39 3.16 1.1 4.54l3.1-2.7Z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.94c1.93 0 3.23.82 3.97 1.5l2.9-2.78C17.06 2.98 14.76 2 12 2a10.2 10.2 0 0 0-8.89 5.45l3.07 2.38C7.02 7.38 9.3 5.94 12 5.94Z"
        fill="#EA4335"
      />
    </svg>
  )
}
