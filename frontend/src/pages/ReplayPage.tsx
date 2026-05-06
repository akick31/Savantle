import { useCallback, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchRandomDate } from '../services/api';
import { useGameState } from '../hooks/useGameState';
import GamePlay from '../components/game/GamePlay';
import EndScreen from '../components/game/EndScreen';
import LoadingScreen from '../components/layout/LoadingScreen';
import PercentileDisplay from '../components/game/PercentileDisplay';
import { liveScreenshotUrl, recordAnalytics } from '../services/api';

export default function ReplayPage() {
  const { date } = useParams<{ date: string }>();
  const navigate = useNavigate();
  const replayState = useGameState({ overrideDate: date, persist: false });
  const recordedRef = useRef(false);

  const handlePlayAnotherDate = useCallback(async () => {
    try {
      const randomDate = await fetchRandomDate();
      navigate(`/replay/${randomDate}`);
    } catch {
      navigate('/replay');
    }
  }, [navigate]);

  useEffect(() => {
    const s = replayState.status;
    if ((s === 'won' || s === 'lost') && !recordedRef.current) {
      recordedRef.current = true;
      recordAnalytics('REPLAY_PLAYED');
    }
  }, [replayState.status]);

  const playerType = replayState.dailyData?.playerType ?? 'BATTER';
  const screenshotUrl = date ? liveScreenshotUrl(date) : '';
  const isLoading = replayState.status === 'loading' && !replayState.error;
  const isFinished = replayState.status === 'won' || replayState.status === 'lost';

  if (!date) { navigate('/'); return null; }
  if (isLoading) return <LoadingScreen error={null} />;
  if (replayState.error && replayState.status === 'loading') return <LoadingScreen error={replayState.error} />;

  return (
    <div className="w-full max-w-[480px] flex-1 space-y-4">
      <div className="space-y-1">
        <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-sv-border text-xs text-sv-muted">
          Replay · {date}
        </div>
        <p className="text-[11px] text-sv-muted opacity-70">
          Percentiles reflect today's stats, not those from the date of this game.
        </p>
      </div>

      {replayState.status === 'playing' && (
        <GamePlay
          dailyData={replayState.dailyData ?? { date, playerType }}
          screenshotUrl={screenshotUrl}
          players={replayState.players}
          guesses={replayState.guesses}
          hints={replayState.hints}
          isSubmitting={replayState.isSubmitting}
          onGuess={replayState.makeGuess}
        />
      )}
      {isFinished && replayState.playerInfo && (
        <>
          <PercentileDisplay screenshotUrl={screenshotUrl} playerType={playerType} />
          <EndScreen
            status={replayState.status}
            playerInfo={replayState.playerInfo}
            guessCount={replayState.guesses.length}
            guesses={replayState.guesses}
            players={replayState.players}
            date={date}
            hints={replayState.hints}
            currentStreak={0}
            isDailyMode={false}
            onPlayAnother={handlePlayAnotherDate}
            onPlayAnotherLabel="Play Another Previous Date"
          />
        </>
      )}
    </div>
  );
}
