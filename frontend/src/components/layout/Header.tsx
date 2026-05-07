type GameMode = 'daily' | 'replay' | 'random';

interface HeaderProps {
  onHowToPlay: () => void;
  onStats: () => void;
  onGlobalStats: () => void;
  onSettings: () => void;
  onReplay: () => void;
  onRandom: () => void;
  gameMode?: GameMode;
  onBackToToday?: () => void;
}

export default function Header({
  onHowToPlay,
  onStats,
  onGlobalStats,
  onSettings,
  onReplay,
  onRandom,
  gameMode = 'daily',
  onBackToToday,
}: HeaderProps) {
  const iconBtn = "p-2 text-sv-muted hover:text-sv-text transition-colors";
  const textBtn = (active: boolean) =>
    `px-2 py-1 text-xs font-medium transition-colors ${active ? 'text-sv-accent font-semibold' : 'text-sv-muted hover:text-sv-text'}`;

  return (
    <header className="w-full max-w-[480px] mb-4 space-y-1">
      <h1 className="text-xl md:text-2xl font-pixel text-sv-accent text-center">
        Savantle
      </h1>

      <div className="flex items-center justify-center gap-1">
        <button onClick={onHowToPlay} className={iconBtn} aria-label="How to play" title="How to play">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </button>

        <div className="w-px h-4 bg-sv-border mx-1" />

        <button onClick={onBackToToday} className={textBtn(gameMode === 'daily')} title="Go to today's Savantle">
          Today
        </button>
        <button onClick={onReplay} className={textBtn(gameMode === 'replay')} title="Play a previous day">
          Replay
        </button>
        <button onClick={onRandom} className={textBtn(gameMode === 'random')} title="Play a random player">
          Random
        </button>

        <div className="w-px h-4 bg-sv-border mx-1" />

        <button onClick={onGlobalStats} className={iconBtn} aria-label="Global statistics" title="Global statistics">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
          </svg>
        </button>
        <button onClick={onStats} className={iconBtn} aria-label="Statistics" title="Statistics">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="12" width="4" height="9" rx="1" />
            <rect x="10" y="7" width="4" height="14" rx="1" />
            <rect x="17" y="3" width="4" height="18" rx="1" />
          </svg>
        </button>
        <button onClick={onSettings} className={iconBtn} aria-label="Settings" title="Settings">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </button>
      </div>

    </header>
  );
}
