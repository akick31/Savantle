import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useStats } from '../hooks/useStats';
import { useSettings } from '../hooks/useSettings';
import Header from '../components/layout/Header';
import HowToPlay from '../components/modals/HowToPlay';
import StatsModal from '../components/modals/StatsModal';
import SettingsModal from '../components/modals/SettingsModal';
import ContactModal from '../components/modals/ContactModal';
import ProjectsModal from '../components/modals/ProjectsModal';
import ReplayPickerModal from '../components/modals/ReplayPickerModal';
import GlobalStatsModal from '../components/modals/GlobalStatsModal';
import ApiNoticeModal, { API_NOTICE_ACTIVE } from '../components/modals/ApiNoticeModal';
import WardleAnnounceModal, {
  daysSinceWardleAnnounceLaunch,
  WARDLE_ANNOUNCE_ICON_WINDOW_DAYS,
} from '../components/modals/WardleAnnounceModal';
import { getSavantleAnalyticsDate } from '../utils/share';
import { GameMode, ModalId } from '../types';

const API_NOTICE_SEEN_KEY = 'savantle-api-notice-seen-v2';
const WARDLE_ANNOUNCE_SEEN_KEY = 'savantle-wardle-announce-seen-v1';

interface ShellProps {
  gameMode: GameMode;
  children: React.ReactNode;
}

export default function Shell({ gameMode, children }: ShellProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { stats } = useStats();
  const { settings, updateSettings } = useSettings();
  const [apiNoticeOpen, setApiNoticeOpen] = useState(false);
  const [wardleAnnounceOpen, setWardleAnnounceOpen] = useState(false);

  const wardleAnnounceDaysSinceLaunch = daysSinceWardleAnnounceLaunch(getSavantleAnalyticsDate());
  const showWardleAnnounceIcon =
    wardleAnnounceDaysSinceLaunch >= 0 && wardleAnnounceDaysSinceLaunch <= WARDLE_ANNOUNCE_ICON_WINDOW_DAYS;

  useEffect(() => {
    if (!API_NOTICE_ACTIVE) return;
    if (localStorage.getItem(API_NOTICE_SEEN_KEY)) return;
    localStorage.setItem(API_NOTICE_SEEN_KEY, '1');
    setApiNoticeOpen(true);
  }, []);

  useEffect(() => {
    if (wardleAnnounceDaysSinceLaunch !== 0) return;
    if (localStorage.getItem(WARDLE_ANNOUNCE_SEEN_KEY)) return;
    localStorage.setItem(WARDLE_ANNOUNCE_SEEN_KEY, '1');
    setWardleAnnounceOpen(true);
  }, [wardleAnnounceDaysSinceLaunch]);

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
        onWardleAnnounce={() => setWardleAnnounceOpen(true)}
        showWardleAnnounceIcon={showWardleAnnounceIcon}
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
          <span>Contact me</span>
        </button>
        <a
          href="https://ko-fi.com/andrewk26515"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 text-sv-muted text-xs hover:text-sv-accent transition-colors"
        >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
            >
                <polyline points="16 18 22 12 16 6"/>
                <polyline points="8 6 2 12 8 18"/>
            </svg>
            <span>Support development</span>
        </a>
        <button
          onClick={() => openModal('projects')}
          className="inline-flex items-center gap-1 text-sv-muted text-xs hover:text-sv-accent transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="3" width="7" height="7" />
            <rect x="14" y="3" width="7" height="7" />
            <rect x="3" y="14" width="7" height="7" />
            <rect x="14" y="14" width="7" height="7" />
          </svg>
          <span>My other projects</span>
        </button>
      </footer>

        <HowToPlay open={activeModal === 'how-to-play'} onClose={closeModal} onContact={() => openModal('contact')}/>
        <StatsModal open={activeModal === 'stats'} onClose={closeModal} stats={stats}/>
        <SettingsModal open={activeModal === 'settings'} onClose={closeModal} settings={settings} onUpdate={updateSettings} />
      <ContactModal open={activeModal === 'contact'} onClose={closeModal} />
      <ProjectsModal open={activeModal === 'projects'} onClose={closeModal} />
      <ReplayPickerModal open={activeModal === 'replay-picker'} onClose={closeModal} onSelect={handleReplaySelect} />
      <GlobalStatsModal open={activeModal === 'global-stats'} onClose={closeModal} />
      <ApiNoticeModal open={apiNoticeOpen} onClose={() => setApiNoticeOpen(false)} />
      <WardleAnnounceModal open={wardleAnnounceOpen} onClose={() => setWardleAnnounceOpen(false)} />
    </div>
  );
}
