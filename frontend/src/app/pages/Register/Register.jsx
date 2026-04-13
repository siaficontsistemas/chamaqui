import { useState } from 'react'
import { registerUser } from '../../api'
import './Register.css'

const formFields = [
  { id: 'name', label: 'Nome', type: 'text', icon: UserIcon },
  { id: 'email', label: 'Email', type: 'email', icon: MailIcon },
  { id: 'phone', label: 'Telefone', type: 'tel', icon: PhoneIcon },
  { id: 'cpf', label: 'CPF', type: 'text', icon: DocumentIcon },
  { id: 'password', label: 'Senha', type: 'password', icon: LockIcon },
  {
    id: 'passwordConfirm',
    label: 'Digite novamente',
    type: 'password',
    icon: LockIcon,
  },
]

function Register({ onNavigateHome, onNavigateLogin }) {
  const [selectedRole, setSelectedRole] = useState('')
  const [formValues, setFormValues] = useState({
    name: '',
    email: '',
    phone: '',
    cpf: '',
    password: '',
    passwordConfirm: '',
  })
  const [acceptedTerms, setAcceptedTerms] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const selectedRoleLabel =
    selectedRole === 'admin'
      ? 'Administrador'
      : selectedRole === 'employee'
        ? 'Funcionário'
        : 'Usuário'

  async function handleSubmit(event) {
    event.preventDefault()

    if (!selectedRole) {
      setErrorMessage('Escolha o tipo de cadastro para continuar.')
      return
    }

    if (Object.values(formValues).some((value) => !value.trim())) {
      setErrorMessage('Preencha todos os campos obrigatórios do cadastro.')
      return
    }

    if (formValues.password !== formValues.passwordConfirm) {
      setErrorMessage('As senhas informadas precisam ser iguais.')
      return
    }

    if (!acceptedTerms) {
      setErrorMessage('Você precisa concordar com os termos para concluir o cadastro.')
      return
    }

    try {
      setIsSubmitting(true)
      setErrorMessage('')

      const user = await registerUser({
        fullName: formValues.name.trim(),
        email: formValues.email.trim(),
        phoneNumber: formValues.phone.trim(),
        documentNumber: formValues.cpf.trim(),
        password: formValues.password,
        role: selectedRole,
      })

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
          <BrandMark />
          <div className="auth-card__welcome">
            <h1>Bem-vindo ao helpdesk da Lopes Consultoria</h1>
            <p>
              Acesse sua conta para acompanhar chamados e solicitações em um só
              lugar.
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
              {selectedRole === 'employee'
                ? 'Preencha os dados para criar seu acesso. Você só entra na equipe depois de aceitar um convite.'
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
                    Cadastrando como <strong>{selectedRoleLabel}</strong>
                  </div>

                  <button
                    className="signup-form__change-role"
                    type="button"
                    onClick={() => {
                      setSelectedRole('')
                      setErrorMessage('')
                    }}
                  >
                    Alterar tipo
                  </button>
                </div>

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
                      value={formValues[field.id]}
                      onChange={(event) =>
                        setFormValues((currentValues) => ({
                          ...currentValues,
                          [field.id]: event.target.value,
                        }))
                      }
                    />
                  </label>
                ))}

                {errorMessage ? <p className="signup-form__feedback">{errorMessage}</p> : null}

                <div className="signup-form__divider">
                  <span>ou continuar com</span>
                </div>

                <button className="google-button" type="button">
                  <GoogleIcon />
                  Continue with Google
                </button>

                <label className="terms-check" htmlFor="terms">
                  <input
                    id="terms"
                    name="terms"
                    type="checkbox"
                    checked={acceptedTerms}
                    onChange={(event) => setAcceptedTerms(event.target.checked)}
                  />
                  <span>
                    Eu li e concordo com os <a href="/">termos e condições de uso</a>
                  </span>
                </label>

                <button
                  className="signup-form__submit"
                  type="submit"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Cadastrando...' : 'Cadastrar'}
                </button>
              </>
            ) : (
              <div className="signup-form__role-step">
                <span className="signup-form__role-label">Escolha o tipo de cadastro</span>

                <div className="signup-form__role-options">
                  <button
                    className="signup-form__role-card"
                    type="button"
                    onClick={() => {
                      setSelectedRole('employee')
                      setErrorMessage('')
                    }}
                  >
                    <span className="signup-form__role-title">Funcionário</span>
                    <span className="signup-form__role-text">
                      Crie sua conta de acesso. A entrada na equipe só acontece após aceitar um convite.
                    </span>
                  </button>

                  <button
                    className="signup-form__role-card"
                    type="button"
                    onClick={() => {
                      setSelectedRole('user')
                      setErrorMessage('')
                    }}
                  >
                    <span className="signup-form__role-title">Usuário</span>
                    <span className="signup-form__role-text">
                      Cadastro para quem deseja acessar o sistema e acompanhar suas solicitações.
                    </span>
                  </button>
                </div>
              </div>
            )}
          </form>
        </section>
      </section>
    </main>
  )
}

export default Register

function BrandMark() {
  return (
    <div className="brand-mark" aria-label="Lopes Consultoria">
      <strong className="brand-mark__name">LOPES</strong>
      <span className="brand-mark__accent">CONSULTORIA</span>
      <span className="brand-mark__subtitle">GESTÃO PÚBLICA</span>
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
