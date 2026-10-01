const fr = {
  app: {
    title: 'PMS Niirma',
    subtitle: 'Gestion hôtelière',
  },
  banner: {
    hotel: 'Hôtel',
    businessDate: "Date d'exploitation",
    chooseHotel: 'Choisir un hôtel',
  },
  nav: {
    dashboard: 'Dashboard',
    reception: 'Réception',
    reservations: 'Réservations',
    planning: 'Planning',
    customers: 'Clients',
    companies: 'Entreprises',
    rooms: 'Chambres',
    housekeeping: 'Housekeeping',
    maintenance: 'Maintenance',
    restaurant: 'Restaurant',
    services: 'Services',
    invoicing: 'Facturation',
    payments: 'Paiements',
    cash: 'Caisse',
    expenses: 'Dépenses',
    stock: 'Stocks',
    suppliers: 'Fournisseurs',
    reports: 'Rapports',
    nightAudit: 'Clôture journalière',
    administration: 'Administration',
    sections: {
      operations: 'Exploitation',
      finance: 'Finance',
      management: 'Gestion',
    },
  },
  dashboard: {
    title: 'Tableau de bord',
    hotelCard: 'Établissement',
    currency: 'Devise',
    timezone: 'Fuseau horaire',
    systemCard: 'État du système',
    apiUp: 'API opérationnelle',
    apiDown: 'API indisponible',
    upcoming: 'Les indicateurs (occupation, chiffre d’affaires, arrivées, départs) arrivent avec les itérations suivantes.',
  },
  comingSoon: {
    title: 'Module en préparation',
    body: 'Ce module sera livré à l’itération {{iteration}} du MVP.',
    later: 'Ce module est prévu dans une phase ultérieure.',
  },
  notFound: {
    title: 'Page introuvable',
    back: 'Retour au tableau de bord',
  },
  errors: {
    traceId: 'Identifiant de trace',
    retry: 'Réessayer',
    noHotel: 'Aucun hôtel accessible.',
  },
} as const

export default fr
