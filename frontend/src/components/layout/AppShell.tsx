import { NavLink } from 'react-router-dom';
import Icon from '../ui/Icon';
import { useSessao } from '../../auth';
import './AppShell.css';

/** Sem o tipo explícito o TS infere uma união por item e perde `end`/`destaque`. */
interface ItemNav {
  to: string;
  label: string;
  icon: React.ComponentProps<typeof Icon>['name'];
  end?: boolean;
  destaque?: boolean;
  soGestor?: boolean;
}

const NAV_GROUPS: { label: string; items: ItemNav[] }[] = [
  {
    label: 'Operação',
    items: [
      { to: '/', label: 'Painel', icon: 'gauge' as const, end: true },
      { to: '/viagem', label: 'Viagem atual', icon: 'route' as const, destaque: true },
      { to: '/logistica', label: 'Logística', icon: 'layers' as const },
      { to: '/nova-viagem', label: 'Nova viagem', icon: 'fileText' as const },
      { to: '/documentos', label: 'Documentos', icon: 'layers' as const },
      { to: '/historico', label: 'Minhas viagens', icon: 'clock' as const },
      { to: '/telemetria', label: 'Telemetria', icon: 'radio' as const },
    ],
  },
  {
    label: 'Rede',
    items: [
      { to: '/frota', label: 'Frota', icon: 'truck' as const },
      { to: '/credenciados', label: 'Credenciados', icon: 'building' as const },
    ],
  },
  {
    label: 'Geral',
    items: [
      { to: '/ranking', label: 'Ranking', icon: 'trophy' as const },
      { to: '/loja', label: 'Loja', icon: 'wallet' as const },
      { to: '/financeiro', label: 'Financeiro', icon: 'wallet' as const },
      { to: '/habilitacao', label: 'Habilitação', icon: 'shield' as const },
      { to: '/perfil', label: 'Meu perfil', icon: 'users' as const },
      { to: '/gestao', label: 'Gestão', icon: 'settings' as const, soGestor: true },
      { to: '/conferencia', label: 'Conferência', icon: 'shield' as const, soGestor: true },
      { to: '/admin', label: 'Administração', icon: 'settings' as const, soGestor: true },
    ],
  },
];

export default function AppShell({ children }: { children: React.ReactNode }) {
  const { usuario, eGestor, sair } = useSessao();

  const grupos = NAV_GROUPS
    .map((g) => ({ ...g, items: g.items.filter((i) => !i.soGestor || eGestor) }))
    .filter((g) => g.items.length > 0);

  return (
    <div className="shell">
      <aside className="shell__sidebar">
        <div className="shell__brand">
          <div className="shell__logo">LK</div>
          <div className="shell__brand-text">
            <strong>LK Transportes</strong>
            <span>Painel Logístico</span>
          </div>
        </div>

        <div className="shell__search">
          <Icon name="search" size={15} />
          <input placeholder="Buscar carga, motorista, placa" />
        </div>

        <div className="shell__scroll">
          {grupos.map((group) => (
            <div className="shell__group" key={group.label}>
              <span className="shell__group-label">{group.label}</span>
              <nav className="shell__nav">
                {group.items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      'shell__navlink'
                      + (isActive ? ' is-active' : '')
                      + ('destaque' in item && item.destaque ? ' is-destaque' : '')
                    }
                  >
                    <Icon name={item.icon} size={17} />
                    <span>{item.label}</span>
                    {'destaque' in item && item.destaque && <span className="shell__pulse" />}
                  </NavLink>
                ))}
              </nav>
            </div>
          ))}
        </div>

        <div className="shell__user">
          <div className="shell__avatar">{usuario.nome.charAt(0).toUpperCase()}</div>
          <div className="shell__user-text">
            <strong>{usuario.nome}</strong>
            <span>{eGestor ? 'Gestor' : 'Motorista'}</span>
          </div>
          <button className="shell__user-action" title="Sair" onClick={sair}>
            <Icon name="logout" size={16} />
          </button>
        </div>
      </aside>

      <main className="shell__main">{children}</main>
    </div>
  );
}
