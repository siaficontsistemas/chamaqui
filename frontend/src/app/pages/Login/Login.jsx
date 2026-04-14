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
