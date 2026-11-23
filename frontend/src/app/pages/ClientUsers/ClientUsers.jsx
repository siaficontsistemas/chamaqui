import { useCallback, useEffect, useMemo, useState } from 'react'
import { listClientPreRegistrations } from '../../api'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { dashboardPages } from '../../dashboardData'
import ClientUserForm from './ClientUserForm'
import ClientUserList from './ClientUserList'
import { onlyDigits } from './clientUserValidation'
import '../Home/Home.css'
import './ClientUsers.css'

/**
 * Página "Usuários": cadastro e gestão dos clientes (pré-cadastro) das empresas clientes.
 * Visível apenas para a equipe de suporte da empresa provedora (administrador e funcionários).
 */
function ClientUsers({
  availableClientCompanies = [],
  headerProps,
  navigationGroups,
  onCreateClientPreRegistration,
  onDeleteClientPreRegistration,
  onNavigatePage,
  onUpdateClientPreRegistration,
}) {
  const [clients, setClients] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [editingClient, setEditingClient] = useState(null)
  const [clientToDelete, setClientToDelete] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [feedback, setFeedback] = useState({ type: 'success', message: '' })

  const loadClients = useCallback(async () => {
    try {
      setIsLoading(true)
      setLoadError('')
      const response = await listClientPreRegistrations()
      setClients(Array.isArray(response) ? response : [])
    } catch (error) {
      setLoadError(error?.message || 'Verifique sua conexão e tente novamente.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadClients()
  }, [loadClients])

  const filteredClients = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return clients
    const digitsTerm = onlyDigits(term)
    return clients.filter((client) =>
      [client.fullName, client.email, client.companyName].some((value) =>
        String(value || '').toLowerCase().includes(term)
      ) ||
      (digitsTerm && `${client.phoneNumber || ''} ${client.documentNumber || ''}`.includes(digitsTerm))
    )
  }, [clients, searchTerm])

  async function handleSubmit(payload) {
    if (editingClient) {
      await onUpdateClientPreRegistration?.(editingClient.id, payload)
      setFeedback({ type: 'success', message: `Cadastro de ${payload.fullName} atualizado.` })
      setEditingClient(null)
    } else {
      await onCreateClientPreRegistration?.(payload)
      setFeedback({ type: 'success', message: `${payload.fullName} cadastrado. O cliente já pode abrir chamados.` })
    }
    await loadClients()
  }

  function handleEdit(client) {
    setFeedback({ type: 'success', message: '' })
    setEditingClient(client)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function handleConfirmDelete() {
    if (!clientToDelete) return
    try {
      setIsDeleting(true)
      await onDeleteClientPreRegistration?.(clientToDelete.id)
      if (editingClient?.id === clientToDelete.id) setEditingClient(null)
      setFeedback({ type: 'success', message: `Cadastro de ${clientToDelete.fullName} excluído.` })
      await loadClients()
    } catch (error) {
      setFeedback({ type: 'error', message: error?.message || 'Não foi possível excluir o cadastro.' })
    } finally {
      setIsDeleting(false)
      setClientToDelete(null)
    }
  }

  const page = dashboardPages.clientUsers

  return (
    <main className="home-page">
      <Sidebar activeSection="clientUsers" navigationGroups={navigationGroups} onSectionChange={onNavigatePage} />

      <div className="home-main-column">
        <Header activeSection="clientUsers" {...headerProps} onSectionChange={onNavigatePage} />

        <section className="home-content">
          <div className="home-content__card home-content__card--team">
            <div className="team-view client-users">
              <div className="home-content__header">
                <div className="home-content__heading">
                  <span className="home-content__eyebrow">Suporte</span>
                  <h1>{page.contentTitle}</h1>
                  <p>{page.contentText}</p>
                </div>
              </div>

              {feedback.message ? (
                <p
                  className={`profile-form__feedback${feedback.type === 'success' ? ' profile-form__feedback--success' : ''}`}
                  role="status"
                >
                  {feedback.message}
                </p>
              ) : null}

              <ClientUserForm
                key={editingClient?.id || 'new'}
                clientCompanies={availableClientCompanies}
                editingClient={editingClient}
                onCancelEdit={() => setEditingClient(null)}
                onSubmit={handleSubmit}
              />

              <ClientUserList
                clients={filteredClients}
                errorMessage={loadError}
                isLoading={isLoading}
                onDelete={setClientToDelete}
                onEdit={handleEdit}
                onRetry={loadClients}
                onSearchChange={setSearchTerm}
                searchTerm={searchTerm}
                totalCount={clients.length}
              />
            </div>
          </div>
        </section>
      </div>

      <ConfirmActionModal
        title="Excluir cadastro"
        description={
          clientToDelete
            ? `Tem certeza que deseja excluir o cadastro de ${clientToDelete.fullName}? Se houver chamados associados, a exclusão será bloqueada para preservar o histórico.`
            : ''
        }
        confirmLabel="Excluir"
        confirmVariant="danger"
        isOpen={Boolean(clientToDelete)}
        isProcessing={isDeleting}
        onCancel={() => !isDeleting && setClientToDelete(null)}
        onConfirm={handleConfirmDelete}
      />
    </main>
  )
}

export default ClientUsers
