import { useState } from 'react'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import '../Home/Home.css'
import './MyData.css'

function MyData({
  currentUser,
  headerProps,
  isProfileLoading,
  navigationGroups,
  onNavigatePage,
  onDeleteAccount,
  onDeleteCompany,
  profileError,
  ticketSummary,
  isTicketSummaryLoading,
}) {
  const [deleteTarget, setDeleteTarget] = useState('')
  const [isDeletingAction, setIsDeletingAction] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const activeContent = dashboardPages.myData
  const isAdmin = currentUser?.roles?.includes('admin')
  const profileFields = [
    { label: 'Nome', value: currentUser?.fullName || 'Não informado', type: 'text' },
    { label: 'Email', value: currentUser?.email || 'Não informado', type: 'email' },
    {
      label: 'Telefone',
      value: currentUser?.phoneNumber || 'Não informado',
      type: 'tel',
    },
    {
      label: 'Documento',
      value: currentUser?.documentNumber || 'Não informado',
      type: 'text',
    },
    ...(isAdmin
      ? [
          {
            label: 'Nome da empresa',
            value: currentUser?.companyName || 'Obrigatório não informado',
            type: 'text',
          },
          {
            label: 'CNPJ da empresa',
            value: currentUser?.companyDocument || 'Obrigatório não informado',
            type: 'text',
          },
        ]
      : []),
    {
      label: 'Status',
      value: currentUser?.status || 'Não informado',
      type: 'text',
    },
  ]

  async function handleConfirmDelete() {
    try {
      setIsDeletingAction(true)
      setDeleteError('')
      if (deleteTarget === 'company') {
        await onDeleteCompany?.()
        return
      }
      await onDeleteAccount?.()
    } catch (error) {
      setDeleteError(error.message)
      setIsDeletingAction(false)
    }
  }

  function handleOpenDeleteModal(target) {
    setDeleteError('')
    setDeleteTarget(target)
  }

  function handleCloseDeleteModal() {
    setDeleteTarget('')
    setDeleteError('')
  }

  const isDeleteModalOpen = Boolean(deleteTarget)
  const deleteModalTitle = deleteTarget === 'company' ? 'Excluir empresa' : 'Excluir conta'
  const deleteModalDescription =
    deleteTarget === 'company'
      ? [
          'Tem certeza que deseja excluir sua empresa? Essa ação remove os setores, chamados, convites e demais dados vinculados a ela do sistema.',
          'Os funcionários vinculados serão notificados de que a empresa foi excluída e perderão acesso aos setores dessa empresa.',
        ]
      : [
          'Tem certeza que deseja excluir sua conta? Essa ação remove seus dados do sistema e não pode ser desfeita.',
          'Chamados, vínculos e demais registros relacionados a esta conta também serão apagados do banco de dados.',
        ]

  return (
    <main className="home-page">
      <Sidebar
        activeSection="myData"
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection="myData"
          {...headerProps}
          isTicketSummaryLoading={isTicketSummaryLoading}
          onSectionChange={onNavigatePage}
          ticketSummary={ticketSummary}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--profile">
            <div className="home-profile">
              <h1 className="home-profile__title">{activeContent.contentTitle}</h1>

              {profileError ? <p className="profile-form__feedback">{profileError}</p> : null}

              <form className="profile-form" onSubmit={(event) => event.preventDefault()}>
                {profileFields.map((field) => (
                  <label className="ticket-field" key={field.label}>
                    <span>{field.label}</span>
                    <div className="ticket-field__control">
                      <input
                        value={isProfileLoading ? 'Carregando...' : field.value}
                        readOnly
                        type={field.type}
                      />
                    </div>
                  </label>
                ))}

                <div className="my-data__actions">
                  {isAdmin ? (
                    <button
                      className="my-data__delete-button"
                      type="button"
                      onClick={() => handleOpenDeleteModal('company')}
                    >
                      Excluir empresa
                    </button>
                  ) : null}
                  <button
                    className="my-data__delete-button"
                    type="button"
                    onClick={() => handleOpenDeleteModal('account')}
                  >
                    Excluir conta
                  </button>
                </div>
              </form>
            </div>
          </div>
        </section>

        {isDeleteModalOpen ? (
          <div className="my-data-modal" role="dialog" aria-modal="true" aria-labelledby="delete-account-title">
            <div className="my-data-modal__card">
              <h2 className="my-data-modal__title" id="delete-account-title">
                {deleteModalTitle}
              </h2>
              {deleteModalDescription.map((paragraph) => (
                <p className="my-data-modal__text" key={paragraph}>
                  {paragraph}
                </p>
              ))}

              {deleteError ? <p className="profile-form__feedback">{deleteError}</p> : null}

              <div className="my-data-modal__actions">
                <button
                  className="my-data-modal__button my-data-modal__button--secondary"
                  type="button"
                  onClick={handleCloseDeleteModal}
                  disabled={isDeletingAction}
                >
                  Cancelar
                </button>
                <button
                  className="my-data-modal__button my-data-modal__button--danger"
                  type="button"
                  onClick={handleConfirmDelete}
                  disabled={isDeletingAction}
                >
                  {isDeletingAction ? 'Excluindo...' : 'Confirmar'}
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </main>
  )
}

export default MyData
