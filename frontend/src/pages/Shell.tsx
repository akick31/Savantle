import { useNavigate, useSearchParams } from 'react-router-dom';
import { useStats } from '../hooks/useStats';
import { useSettings } from '../hooks/useSettings';
import Header from '../components/layout/Header';
import HowToPlay from '../components/modals/HowToPlay';
import StatsModal from '../components/modals/StatsModal';
import SettingsModal from '../components/modals/SettingsModal';
import ContactModal from '../components/modals/ContactModal';
import ReplayPickerModal from '../components/modals/ReplayPickerModal';
import GlobalStatsModal from '../components/modals/GlobalStatsModal';
import DowntimeApologyModal from '../components/modals/DowntimeApologyModal';
import { GameMode, ModalId } from '../types';

interface ShellProps {
  gameMode: GameMode;
  children: React.ReactNode;
}

export default function Shell({ gameMode, children }: ShellProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { stats } = useStats();
  const { settings, updateSettings } = useSettings();

  const activeModal = searchParams.get('modal') as ModalId | null;

  function openModal(id: ModalId) {
    setSearchParams({ modal: id }, { replace: false });
  }

  function closeModal() {
    navigate(-1);
  }

  function handleReplaySelect(date: string) {
    navigate(`/replay/${date}`);
  }

  return (
    <div className="min-h-screen bg-sv-bg flex flex-col items-center px-4 py-4">
      <Header
        onHowToPlay={() => openModal('how-to-play')}
        onStats={() => openModal('stats')}
        onGlobalStats={() => openModal('global-stats')}
        onSettings={() => openModal('settings')}
        onReplay={() => openModal('replay-picker')}
        onRandom={() => navigate('/random')}
        gameMode={gameMode}
        onBackToToday={() => navigate('/')}
      />

      {children}

      <footer className="mt-8 pb-4 text-center flex flex-col items-center gap-1.5">
        <p className="text-sv-muted text-xs opacity-50">Stats via Baseball Savant</p>
        <button
          onClick={() => openModal('contact')}
          className="inline-flex items-center gap-1 text-sv-muted text-xs hover:text-sv-accent transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
            <polyline points="22,6 12,13 2,6" />
          </svg>
          <span>Contact Me</span>
        </button>
        <a
          href="https://buymeacoffee.com/flying_porygon"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 text-sv-muted text-xs hover:text-sv-accent transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M18 8h1a4 4 0 0 1 0 8h-1" />
            <path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
            <line x1="6" y1="1" x2="6" y2="4" /><line x1="10" y1="1" x2="10" y2="4" /><line x1="14" y1="1" x2="14" y2="4" />
          </svg>
          <span>Buy me a coffee</span>
        </a>
      </footer>

      <HowToPlay open={activeModal === 'how-to-play'} onClose={closeModal} onContact={() => openModal('contact')} />
      <StatsModal open={activeModal === 'stats'} onClose={closeModal} stats={stats} />
      <SettingsModal open={activeModal === 'settings'} onClose={closeModal} settings={settings} onUpdate={updateSettings} />
      <ContactModal open={activeModal === 'contact'} onClose={closeModal} />
      <ReplayPickerModal open={activeModal === 'replay-picker'} onClose={closeModal} onSelect={handleReplaySelect} />
      <GlobalStatsModal open={activeModal === 'global-stats'} onClose={closeModal} />
      <DowntimeApologyModal />
    </div>
  );
}
