import { formatCpf, formatPhone } from './clientUserValidation'

/**
 * Lista de clientes pré-cadastrados com os estados de carregamento, erro e vazio.
 */
function ClientUserList({
  clients,
  errorMessage,
  isLoading,
  onDelete,
  onEdit,
  onRetry,
  onSearchChange,
  searchTerm,
  totalCount,
}) {
  return (
    <section className="team-panel client-users-list" aria-busy={isLoading}>
      <div className="team-panel__header client-users-list__header">
        <div>
          <span className="home-panel__eyebrow">Clientes cadastrados</span>
          <h2>{isLoading ? 'Carregando...' : `${totalCount} ${totalCount === 1 ? 'cliente' : 'clientes'}`}</h2>
        </div>
        <label className="client-users-list__search">
          <span className="client-users-list__sr-only">Buscar cliente</span>
          <input
            type="search"
            placeholder="Buscar por nome, telefone, e-mail ou empresa"
            value={searchTerm}
            disabled={isLoading || Boolean(errorMessage)}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </label>
      </div>

      {isLoading ? (
        <ul className="client-users-list__items" aria-hidden="true">
          {[0, 1, 2].map((item) => (
            <li className="client-users-list__item client-users-list__item--skeleton" key={item} />
          ))}
        </ul>
      ) : errorMessage ? (
        <div className="client-users-list__state" role="alert">
          <strong>Não foi possível carregar os clientes.</strong>
          <p>{errorMessage}</p>
          <button className="team-invite__button" type="button" onClick={onRetry}>
            Tentar novamente
          </button>
        </div>
      ) : totalCount === 0 ? (
        <div className="client-users-list__state">
          <strong>Nenhum cliente cadastrado ainda.</strong>
          <p>Use o formulário acima para cadastrar o primeiro cliente de uma empresa cliente.</p>
        </div>
      ) : clients.length === 0 ? (
        <div className="client-users-list__state">
          <strong>Nenhum cliente encontrado para “{searchTerm}”.</strong>
          <p>Confira a grafia ou busque pelo telefone.</p>
        </div>
      ) : (
        <ul className="client-users-list__items">
          {clients.map((client) => (
            <li className="client-users-list__item" key={client.id}>
              <div className="client-users-list__main">
                <strong>{client.fullName}</strong>
                <span>{client.companyName || 'Empresa não informada'}</span>
              </div>
              <dl className="client-users-list__details">
                <div>
                  <dt>Telefone</dt>
                  <dd>{formatPhone(client.phoneNumber) || '—'}</dd>
                </div>
                <div>
                  <dt>E-mail</dt>
                  <dd>{client.email || '—'}</dd>
                </div>
                <div>
                  <dt>CPF</dt>
                  <dd>{client.documentNumber ? formatCpf(client.documentNumber) : '—'}</dd>
                </div>
              </dl>
              <div className="client-users-list__actions">
                <button type="button" onClick={() => onEdit(client)}>
                  Editar
                </button>
                <button className="client-users-list__danger" type="button" onClick={() => onDelete(client)}>
                  Excluir
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ClientUserList
