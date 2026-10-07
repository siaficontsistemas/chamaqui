import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { completePreRegistration } from '../../api'
import '../Login/Login.css'
import './CompleteRegistration.css'

const fields = [
  { id: 'fullName', label: 'Nome completo', type: 'text', placeholder: 'Digite seu nome completo' },
  { id: 'email', label: 'Email', type: 'email', placeholder: 'Digite seu email' },
  { id: 'documentNumber', label: 'CPF', type: 'text', placeholder: 'Digite seu CPF' },
  { id: 'password', label: 'Senha', type: 'password', placeholder: 'Crie uma senha segura' },
  { id: 'confirmPassword', label: 'Confirme a senha', type: 'password', placeholder: 'Digite a senha novamente' },
]

const initialValues = {
  fullName: '',
  email: '',
  documentNumber: '',
  password: '',
  confirmPassword: '',
}

export default function CompleteRegistration() {
  const [params] = useSearchParams()
  const [values, setValues] = useState(initialValues)
  const [feedback, setFeedback] = useState('')
  const [done, setDone] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function update(field, value) {
    setValues((currentValues) => ({ ...currentValues, [field]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setFeedback('')

    try {
      setIsSubmitting(true)
      await completePreRegistration({
        token: params.get('preCadastroToken') || '',
        ...values,
        acceptedTerms: true,
        acceptedPrivacyPolicy: true,
      })
      setDone(true)
    } catch (error) {
      setFeedback(error.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page complete-registration-page">
      <section className="complete-registration-card" aria-labelledby="complete-registration-title">
        <div className="complete-registration__brand">
          <div className="brand-mark" aria-label="ChamaAqui Helpdesk">
            <img className="brand-mark__logo" src="/logo_chamaqui.png" alt="ChamaAqui Helpdesk" />
          </div>
        </div>

        <section className="complete-registration__content">
          <header className="complete-registration__header">
            <h1 id="complete-registration-title">Finalize seu cadastro</h1>
            <p>Complete seus dados para acessar sua conta no Chamaqui.</p>
          </header>

          {done ? (
            <div className="complete-registration__success" role="status">
              <div className="complete-registration__success-icon" aria-hidden="true">✓</div>
              <h2>Cadastro concluído</h2>
              <p>Sua conta está pronta. Agora você já pode acessar o sistema.</p>
              <a className="auth-card__submit-button complete-registration__login-link" href="/login">
                Ir para o login
              </a>
            </div>
          ) : (
            <form className="complete-registration-form" onSubmit={submit}>
              {fields.map((field) => (
                <label className="complete-registration-field" htmlFor={field.id} key={field.id}>
                  <span>{field.label}</span>
                  <div className="form-field">
                    <input
                      id={field.id}
                      name={field.id}
                      placeholder={field.placeholder}
                      required
                      type={field.type}
                      value={values[field.id]}
                      onChange={(event) => update(field.id, event.target.value)}
                    />
                  </div>
                </label>
              ))}

              {feedback ? <p className="login-form__feedback">{feedback}</p> : null}

              <button className="auth-card__submit-button complete-registration__submit" type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Finalizando...' : 'Finalizar cadastro'}
              </button>
            </form>
          )}
        </section>
      </section>
    </main>
  )
}
