const dashboardPages = {
  tickets: {
    id: 'tickets',
    label: 'Chamados',
    contentTitle: 'Chamados',
    contentText:
      'Aqui ficarão os painéis e listagens principais de atendimento. Por enquanto, essa área já responde aos botões laterais.',
  },
  calendar: {
    id: 'calendar',
    label: 'Calendário',
    contentTitle: 'Calendário',
    contentText:
      'Acompanhe obrigações, vencimentos e lembretes de prazo da sua empresa em um único painel.',
  },
  reports: {
    id: 'reports',
    label: 'Relatórios',
    contentTitle: 'Relatórios',
    contentText:
      'Nesta área você poderá exibir indicadores, gráficos e exportações. No momento, a estrutura visual já está pronta.',
  },
  all: {
    id: 'all',
    label: 'Todos os Chamados',
    contentTitle: 'Todos os Chamados',
    contentText:
      'Esse espaço central pode receber futuramente a tabela completa de chamados com filtros e paginação.',
  },
  open: {
    id: 'open',
    label: 'Abertos',
    contentTitle: 'Chamados Abertos',
    contentText:
      'Quando você integrar os dados, essa área pode mostrar somente os chamados em aberto e seus detalhes.',
  },
  closed: {
    id: 'closed',
    label: 'Fechados',
    contentTitle: 'Chamados Fechados',
    contentText:
      'Aqui podem ser listados os chamados concluídos, histórico de resolução e métricas de fechamento.',
  },
  newTicket: {
    id: 'newTicket',
    label: 'Novo chamado',
    contentTitle: 'Novo Chamado',
    contentText:
      'Preencha os campos abaixo para registrar uma nova solicitação e direcionar o atendimento ao setor correto.',
  },
  myData: {
    id: 'myData',
    label: 'Meus dados',
    contentTitle: 'Meus Dados',
    contentText: 'Visualize abaixo as informações principais do seu cadastro.',
  },
  team: {
    id: 'team',
    label: 'Equipe',
    contentTitle: 'Equipe de trabalho',
    contentText:
      'Acompanhe os integrantes da equipe e convide novos funcionários para participar.',
  },
  createSector: {
    id: 'createSector',
    label: 'Criar setor',
    contentTitle: 'Criar Setor',
    contentText:
      'Cadastre novos setores para a equipe e disponibilize esses setores para vinculação dos funcionários.',
  },
}

const roleLabels = {
  admin: 'Administrador',
  employee: 'Funcionário',
  user: 'Usuário',
}

function getRoleLabel(userRole) {
  return roleLabels[userRole] ?? roleLabels.user
}

function getCurrentMemberId() {
  return null
}

function getTeamMembers(userRole, teamMembers = []) {
  return isTeamRole(userRole) ? teamMembers : []
}

function isTeamRole(userRole) {
  return userRole === 'admin' || userRole === 'employee'
}

function createSectorId(sectorName) {
  return `sector-${sectorName
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')}`
}

function getVisibleSectors(userRole, sectors, teamMembers, currentMemberId = getCurrentMemberId(userRole)) {
  if (userRole === 'admin') {
    return sectors
  }

  if (userRole === 'employee') {
    const currentMember = teamMembers.find((member) => member.id === currentMemberId)
    const sectorIds = currentMember?.sectors ?? []

    return sectors.filter((sector) => sectorIds.includes(sector.id))
  }

  return []
}

function buildNavigationGroups({ userRole, sectors, teamMembers, currentMemberId, canAccessTeamPage = false }) {
  const visibleSectors = getVisibleSectors(userRole, sectors, teamMembers, currentMemberId)
  const sectorItems = [
    ...(userRole === 'admin'
      ? [{ id: 'createSector', label: 'Criar setor', icon: 'plus', marker: 'neutral' }]
      : []),
    ...visibleSectors.map((sector) => ({
      id: sector.id,
      label: sector.name,
      icon: 'building',
    })),
  ]

  return [
    {
      title: 'Principal',
      items: [
        {
          id: 'tickets',
          label: 'Chamados',
        },
        {
          id: 'calendar',
          label: 'Calendário',
          icon: 'calendar',
        },
        {
          id: 'reports',
          label: 'Relatórios',
        },
        ...(canAccessTeamPage
          ? [
              {
                id: 'team',
                label: 'Equipe',
                icon: 'building',
              },
            ]
          : []),
      ],
    },
    {
      title: 'Chamados',
      items: [
        {
          id: 'all',
          label: 'Todos os Chamados',
          marker: 'neutral',
        },
        {
          id: 'open',
          label: 'Abertos',
          marker: 'open',
        },
        {
          id: 'closed',
          label: 'Fechados',
          marker: 'closed',
        },
      ],
    },
    ...(sectorItems.length > 0
      ? [
          {
            title: 'Setores',
            items: sectorItems,
          },
        ]
      : []),
  ]
}

function getTeamContent(userRole) {
  return {
    ...dashboardPages.team,
    contentText:
      userRole === 'admin'
        ? 'Gerencie os setores criados, convide integrantes e defina em quais setores cada funcionário participa.'
        : 'Visualize abaixo os setores em que você participa e o direcionamento atual da equipe.',
  }
}

export {
  buildNavigationGroups,
  createSectorId,
  dashboardPages,
  getCurrentMemberId,
  getRoleLabel,
  getTeamContent,
  getTeamMembers,
  getVisibleSectors,
  isTeamRole,
}
