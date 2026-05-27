import { useState } from 'react'
import ConfirmActionModal from '../../components/confirm-action-modal/ConfirmActionModal'
import Header from '../../components/header/Header'
import Sidebar from '../../components/sidebar/Sidebar'
import { getRoleLabel } from '../../dashboardData'
import '../Home/Home.css'

function Sector({
  headerProps,
  isTeamDataLoading,
  navigationGroups,
  onNavigatePage,
  onUpdateMemberSectors,
  sector,
  teamMembers = [],
  userRole = 'user',
}) {
  const roleLabel = getRoleLabel(userRole)
  const assignedMembers = teamMembers.filter((member) => (member.sectors ?? []).includes(sector.id))
  const [memberPendingRemoval, setMemberPendingRemoval] = useState(null)
  const [processingMemberId, setProcessingMemberId] = useState('')
  const [feedbackMessage, setFeedbackMessage] = useState('')
  const summaryCards = [
    {
      id: 'sector',
      label: 'Setor',
      value: sector.name,
      detail: 'Setor criado pelo administrador',
    },
    {
      id: 'company',
      label: 'Empresa',
      value: sector.companyName || 'Empresa não informada',
      detail: sector.companyDocument
        ? `CNPJ ${sector.companyDocument}`
        : 'Empresa vinculada a este setor',
    },
    {
      id: 'members',
      label: 'Participantes',
      value: String(assignedMembers.length),
      detail: 'Funcionários vinculados atualmente',
    },
    {
      id: 'profile',
      label: 'Acesso',
      value: roleLabel,
      detail:
        userRole === 'admin'
          ? headerProps.isTeamRole
            ? 'Pode ajustar participantes na tela de equipe'
            : 'Aguarda aceite de convite para acessar a equipe'
          : 'Visualiza somente o setor em que participa',
    },
  ]

  function requestRemoveMemberFromSector(member) {
    setMemberPendingRemoval(member)
  }

  function closeRemoveMemberModal() {
    if (processingMemberId) {
      return
    }
    setMemberPendingRemoval(null)
  }

  async function handleConfirmRemoveMember() {
    if (!memberPendingRemoval || !onUpdateMemberSectors) {
      return
    }

    setProcessingMemberId(memberPendingRemoval.id)
    setFeedbackMessage('')

    try {
      const nextSectors = (memberPendingRemoval.sectors ?? []).filter(
        (sectorId) => sectorId !== sector.id
      )
      await onUpdateMemberSectors(memberPendingRemoval.id, nextSectors)
      setFeedbackMessage(
        `${memberPendingRemoval.name} foi removido(a) apenas do setor ${sector.name}.`
      )
      setMemberPendingRemoval(null)
    } catch (error) {
      setFeedbackMessage(error.message)
    } finally {
      setProcessingMemberId('')
    }
  }

  return (
    <main className="home-page">
      <Sidebar
        activeSection={sector.id}
        navigationGroups={navigationGroups}
        onSectionChange={onNavigatePage}
      />

      <div className="home-main-column">
        <Header
          activeSection={sector.id}
          {...headerProps}
          onSectionChange={onNavigatePage}
        />

        <section className="home-content">
          <div className="home-content__card home-content__card--team">
            <div className="home-content__header">
              <div className="home-content__heading">
                <span className="home-content__eyebrow">Setor da equipe</span>
                <h1>{sector.name}</h1>
                <p>{sector.description}</p>
              </div>

              {userRole === 'admin' ? (
                <div className="home-content__actions">
                  {headerProps.isTeamRole ? (
                    <button className="home-content__button" type="button" onClick={() => onNavigatePage('team')}>
                      Ajustar equipe
                    </button>
                  ) : null}
                  <button
                    className="home-content__button home-content__button--ghost"
                    type="button"
                    onClick={() => onNavigatePage('createSector')}
                  >
                    Criar setor
                  </button>
                </div>
              ) : null}
            </div>

            <div className="home-summary">
              {summaryCards.map((card) => (
                <article className="home-summary__card" key={card.id}>
                  <span className="home-summary__label">{card.label}</span>
                  <strong className="home-summary__value">{card.value}</strong>
                  <span className="home-summary__detail">{card.detail}</span>
                </article>
              ))}
            </div>

            <div className="home-panel">
              <div className="home-panel__header">
                <div>
                  <span className="home-panel__eyebrow">Equipe vinculada</span>
                  <h2>Participantes do setor</h2>
                </div>
                <span className="home-panel__badge">{assignedMembers.length} integrante(s)</span>
              </div>

              {feedbackMessage ? <p className="profile-form__feedback">{feedbackMessage}</p> : null}

              <div className="home-panel__table">
                <div className={`home-panel__table-head${userRole === 'admin' ? ' team-panel__head--admin' : ''}`}>
                  <span>Nome</span>
                  <span>Função</span>
                  <span>Status</span>
                  <span>Setores</span>
                  {userRole === 'admin' ? <span>Ações</span> : null}
                </div>

                {assignedMembers.map((member) => (
                  <div
                    className={`home-panel__table-row${userRole === 'admin' ? ' team-panel__row--admin' : ''}`}
                    key={member.id}
                  >
                    <span>{member.name}</span>
                    <span>{member.role}</span>
                    <span>{member.status}</span>
                    <span>{member.sectors.length}</span>
                    {userRole === 'admin' ? (
                      <span>
                        <button
                          className="team-panel__action-button team-panel__action-button--danger"
                          type="button"
                          onClick={() => requestRemoveMemberFromSector(member)}
                          disabled={
                            isTeamDataLoading ||
                            processingMemberId === member.id
                          }
                        >
                          {processingMemberId === member.id ? 'Removendo...' : 'Remover funcionário'}
                        </button>
                      </span>
                    ) : null}
                  </div>
                ))}

                {assignedMembers.length === 0 ? (
                  <div className="home-panel__table-row">
                    <span>Nenhum integrante</span>
                    <span>Aguardando vínculo</span>
                    <span>Sem participação</span>
                    <span>0</span>
                    {userRole === 'admin' ? <span>Sem ações</span> : null}
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </section>
      </div>

      <ConfirmActionModal
        title="Remover funcionário do setor"
        description={
          memberPendingRemoval
            ? [
                `Tem certeza que deseja remover ${memberPendingRemoval.name} do setor ${sector.name}?`,
                'Essa ação remove apenas o vínculo com este setor específico.',
              ].filter(Boolean)
            : []
        }
        isOpen={Boolean(memberPendingRemoval)}
        isProcessing={Boolean(processingMemberId)}
        confirmLabel={processingMemberId ? 'Removendo...' : 'Confirmar'}
        confirmVariant="danger"
        onCancel={closeRemoveMemberModal}
        onConfirm={handleConfirmRemoveMember}
      />

    </main>
  )
}

export default Sector
