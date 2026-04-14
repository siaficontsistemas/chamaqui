import { useState } from 'react'
import { registerUser } from '../../api'
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

function Register({ onNavigateHome, onNavigateLogin }) {
  const [selectedRole, setSelectedRole] = useState('')
  const [formValues, setFormValues] = useState({
    name: '',
    email: '',
    phone: '',
    cpf: '',
    companyName: '',
    companyCnpj: '',
    password: '',
    passwordConfirm: '',
  })
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [isTermsModalOpen, setIsTermsModalOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const selectedRoleLabel =
    selectedRole === 'admin'
      ? 'Administrador'
      : selectedRole === 'employee'
        ? 'Funcionário'
        : 'Usuário'
  const visibleFormFields = [
    ...formFields,
    ...(selectedRole === 'admin' ? adminOnlyFields : []),
    ...passwordFields,
  ]

  function handleSelectRole(role) {
    setSelectedRole(role)
    setErrorMessage('')

    if (role !== 'admin') {
      setFormValues((currentValues) => ({
        ...currentValues,
        companyName: '',
        companyCnpj: '',
      }))
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()

    if (!selectedRole) {
      setErrorMessage('Escolha o tipo de cadastro para continuar.')
      return
    }

    if (visibleFormFields.some((field) => !formValues[field.id].trim())) {
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
        companyName: selectedRole === 'admin' ? formValues.companyName.trim() : null,
        companyDocument: selectedRole === 'admin' ? formValues.companyCnpj.trim() : null,
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
              {selectedRole
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

                {visibleFormFields.map((field) => (
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

                <label className="terms-check" htmlFor="terms">
                  <input
                    id="terms"
                    name="terms"
                    type="checkbox"
                    checked={acceptedTerms}
                    onChange={(event) => setAcceptedTerms(event.target.checked)}
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
                    onClick={() => handleSelectRole('employee')}
                  >
                    <span className="signup-form__role-title">Funcionário</span>
                    <span className="signup-form__role-text">
                      Cadastro para colaboradores que irão abrir e acompanhar chamados.
                    </span>
                  </button>

                  <button
                    className="signup-form__role-card"
                    type="button"
                    onClick={() => handleSelectRole('admin')}
                  >
                    <span className="signup-form__role-title">Administrador</span>
                    <span className="signup-form__role-text">
                      Cadastro para quem irá gerenciar usuários, filas e atendimentos.
                    </span>
                  </button>

                  <button
                    className="signup-form__role-card"
                    type="button"
                    onClick={() => handleSelectRole('user')}
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

      {isTermsModalOpen ? <TermsOfUseModal onClose={() => setIsTermsModalOpen(false)} /> : null}
    </main>
  )
}

export default Register

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
