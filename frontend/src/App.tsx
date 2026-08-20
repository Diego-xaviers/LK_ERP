import { Routes, Route, Navigate } from 'react-router-dom';
import AppShell from './components/layout/AppShell';
import { ProvedorUsuario } from './auth';
import Dashboard from './pages/Dashboard';
import ModoViagem from './pages/ModoViagem';
import NovaViagem from './pages/NovaViagem';
import Documentos from './pages/Documentos';
import Historico from './pages/Historico';
import Frota from './pages/Frota';
import Parceiros from './pages/Parceiros';
import Ranking from './pages/Ranking';
import Telemetria from './pages/Telemetria';
import Perfil from './pages/Perfil';
import Logistica from './pages/Logistica';
import Conferencia from './pages/Conferencia';
import Financeiro from './pages/Financeiro';
import Habilitacao from './pages/Habilitacao';
import Loja from './pages/Loja';
import Gestao from './pages/Gestao';
import Admin from './pages/Admin';

export default function App() {
  return (
    <ProvedorUsuario>
      <AppShell>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/viagem" element={<ModoViagem />} />
          <Route path="/nova-viagem" element={<NovaViagem />} />
          <Route path="/documentos" element={<Documentos />} />
          <Route path="/historico" element={<Historico />} />
          <Route path="/frota" element={<Frota />} />
          <Route path="/credenciados" element={<Parceiros />} />
          <Route path="/ranking" element={<Ranking />} />
          <Route path="/telemetria" element={<Telemetria />} />
          <Route path="/perfil" element={<Perfil />} />
          <Route path="/logistica" element={<Logistica />} />
          <Route path="/conferencia" element={<Conferencia />} />
          <Route path="/financeiro" element={<Financeiro />} />
          <Route path="/habilitacao" element={<Habilitacao />} />
          <Route path="/loja" element={<Loja />} />
          <Route path="/gestao" element={<Gestao />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell>
    </ProvedorUsuario>
  );
}
