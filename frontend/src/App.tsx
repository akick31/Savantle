import { useState, useEffect, useRef } from 'react';
import { useGameState } from './hooks/useGameState';
import { useStats } from './hooks/useStats';
import { useSettings } from './hooks/useSettings';
import Header from './components/Header';
import GamePlay from './components/GamePlay';
import EndScreen from './components/EndScreen';
import LoadingScreen from './components/LoadingScreen';
import PercentileDisplay from './components/PercentileDisplay';
import HowToPlay from './components/HowToPlay';
import StatsModal from './components/StatsModal';
import SettingsModal from './components/SettingsModal';

const HTP_SHOWN_KEY = 'savantle-htp-shown';

export default function App() {
  const { dailyData, players, status, guesses, hints, playerInfo, error, isSubmitting, makeGuess } = useGameState();
  const { stats, recordGame } = useStats();
  const { settings, updateSettings } = useSettings();

  const [htpOpen, setHtpOpen] = useState(false);
  const [statsOpen, setStatsOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const hasRecorded = useRef(false);

  useEffect(() => {
    if (status === 'playing' && !localStorage.getItem(HTP_SHOWN_KEY)) {
      setHtpOpen(true);
      localStorage.setItem(HTP_SHOWN_KEY, '1');
    }
  }, [status]);

  useEffect(() => {
    if ((status === 'won' || status === 'lost') && !hasRecorded.current) {
      hasRecorded.current = true;
      recordGame(status === 'won', guesses.length);
    }
  }, [status, guesses.length, recordGame]);

  if (status === 'loading') {
    return <LoadingScreen error={error} />;
  }

  const isFinished = status === 'won' || status === 'lost';

  return (
    <div className="min-h-screen bg-sv-bg flex flex-col items-center px-4 py-4">
      <Header
        onHowToPlay={() => setHtpOpen(true)}
        onStats={() => setStatsOpen(true)}
        onSettings={() => setSettingsOpen(true)}
      />

      <main className="w-full max-w-[480px] flex-1 space-y-4">
        {status === 'playing' && dailyData && (
          <GamePlay
            dailyData={dailyData}
            players={players}
            guesses={guesses}
            hints={hints}
            isSubmitting={isSubmitting}
            onGuess={makeGuess}
          />
        )}

        {isFinished && dailyData && playerInfo && (
          <>
            <PercentileDisplay stats={dailyData.stats} playerType={dailyData.playerType} />
            <EndScreen
              status={status}
              playerInfo={playerInfo}
              guessCount={guesses.length}
              date={dailyData.date}
              hints={hints}
            />
          </>
        )}
      </main>

      <footer className="mt-8 pb-4 text-center space-y-1.5">
        <a
          href="https://buymeacoffee.com/flying_porygon"
          target="_blank"
          rel="noopener noreferrer"
          className="text-sv-muted text-xs hover:text-sv-accent transition-colors block"
        >
          ☕ Buy me a coffee
        </a>
        <p className="text-sv-muted text-xs opacity-50">
          Stats via Baseball Savant & MLB Stats API
        </p>
      </footer>

      <HowToPlay open={htpOpen} onClose={() => setHtpOpen(false)} />
      <StatsModal open={statsOpen} onClose={() => setStatsOpen(false)} stats={stats} />
      <SettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} settings={settings} onUpdate={updateSettings} />
    </div>
  );
}
