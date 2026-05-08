import { useEffect, useRef } from 'react';
import { useRandomGameState } from '../hooks/useRandomGameState';
import GamePlay from '../components/game/GamePlay';
import EndScreen from '../components/game/EndScreen';
import PercentileDisplay from '../components/game/PercentileDisplay';
import { randomGameScreenshotUrl, recordAnalytics } from '../services/api';

export default function RandomPage() {
  const randomState = useRandomGameState();
  const started = useRef(false);
  const recordedRef = useRef(false);

  useEffect(() => {
    if (!started.current) {
      started.current = true;
      randomState.startGame();
    }
  }, []);

  useEffect(() => {
    const s = randomState.status;
    if ((s === 'won' || s === 'lost') && !recordedRef.current) {
      recordedRef.current = true;
      recordAnalytics('RANDOM_PLAYED');
    }
  }, [randomState.status]);

  const playerType = randomState.gameData?.playerType ?? 'BATTER';
  const screenshotUrl = randomState.gameData ? randomGameScreenshotUrl(randomState.gameData.gameId) : '';
  const isLoading = randomState.status === 'loading' && !randomState.error;
  const isFinished = randomState.status === 'won' || randomState.status === 'lost';

  if (isLoading) {
    return (
      <div className="w-full max-w-[480px] flex-1 flex flex-col items-center justify-center gap-2 py-16">
        <p className="text-sv-muted text-sm animate-pulse">Finding a random player…</p>
        <p className="text-sv-muted text-xs opacity-60">Capturing their percentile chart from Baseball Savant</p>
      </div>
    );
  }

  if (randomState.error) {
    return (
      <div className="w-full max-w-[480px] flex-1 flex flex-col items-center justify-center gap-3 py-16">
        <p className="text-sv-muted text-sm">Couldn't load a random player. Try again?</p>
        <button
          onClick={() => { recordedRef.current = false; randomState.startGame(); }}
          className="px-4 py-2 text-xs font-semibold bg-sv-accent text-sv-bg rounded-lg hover:opacity-90 transition-opacity"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-[480px] flex-1 space-y-4">
      <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-sv-border text-xs text-sv-muted">
        Random Player
      </div>

      {randomState.status === 'playing' && randomState.gameData && (
        <GamePlay
          dailyData={{ date: randomState.gameData.gameId, playerType }}
          screenshotUrl={screenshotUrl}
          players={randomState.players}
          guesses={randomState.guesses}
          hints={randomState.hints}
          isSubmitting={randomState.isSubmitting}
          onGuess={randomState.makeGuess}
        />
      )}
      {isFinished && randomState.playerInfo && (
        <>
          <PercentileDisplay screenshotUrl={screenshotUrl} playerType={playerType} />
          <EndScreen
            status={randomState.status}
            playerInfo={randomState.playerInfo}
            guessCount={randomState.guesses.length}
            guesses={randomState.guesses}
            players={randomState.players}
            date=""
            hints={randomState.hints}
            currentStreak={0}
            isDailyMode={false}
            onPlayAnother={() => { recordedRef.current = false; randomState.startGame(); }}
            onPlayAnotherLabel="Play Another Random Player"
          />
        </>
      )}
    </div>
  );
}
