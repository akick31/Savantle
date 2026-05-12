import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGameState } from '../hooks/useGameState';
import { useStats } from '../hooks/useStats';
import GamePlay from '../components/game/GamePlay';
import EndScreen from '../components/game/EndScreen';
import LoadingScreen from '../components/layout/LoadingScreen';
import PercentileDisplay from '../components/game/PercentileDisplay';
import { dailyScreenshotUrl, recordAnalytics, fetchGlobalStats } from '../services/api';
import { GlobalStats } from '../types';

const HTP_SHOWN_KEY = 'savantle-htp-shown';

export default function DailyPage() {
  const navigate = useNavigate();
  const dailyState = useGameState({ persist: true });
  const { stats, recordGame } = useStats();
  const recordedForDate = useRef<string | null>(null);
  const guessesRef = useRef(dailyState.guesses);
  guessesRef.current = dailyState.guesses;
  const [globalStats, setGlobalStats] = useState<GlobalStats | null>(null);

  useEffect(() => {
    if (dailyState.status === 'playing' && !localStorage.getItem(HTP_SHOWN_KEY)) {
      localStorage.setItem(HTP_SHOWN_KEY, '1');
    }
  }, [dailyState.status]);

  useEffect(() => {
    const s = dailyState.status;
    const date = dailyState.dailyData?.date;
    if (!date || (s !== 'won' && s !== 'lost')) return;
    if (recordedForDate.current === date) return;
    const analyticsKey = `savantle-analytics-${date}`;
    if (localStorage.getItem(analyticsKey)) return;

    const guessCount = guessesRef.current.length;
    if (guessCount === 0) return;

    recordedForDate.current = date;
    localStorage.setItem(analyticsKey, '1');

    recordGame(s === 'won', guessCount, date);

    const analyticsRequests = [recordAnalytics(s === 'won' ? 'GAME_WON' : 'GAME_LOST')];
    if (s === 'won' && guessCount >= 1 && guessCount <= 5) {
      analyticsRequests.push(recordAnalytics(`GUESS_${guessCount}`));
    }
    Promise.allSettled(analyticsRequests).then(() => {
      fetchGlobalStats().then(setGlobalStats).catch(() => {});
    });
  }, [dailyState.status, dailyState.dailyData?.date, dailyState.guesses.length, recordGame]);

  useEffect(() => {
    const s = dailyState.status;
    const date = dailyState.dailyData?.date;
    if (!date || (s !== 'won' && s !== 'lost')) return;
    const analyticsKey = `savantle-analytics-${date}`;
    if (!localStorage.getItem(analyticsKey)) return;
    fetchGlobalStats().then(setGlobalStats).catch(() => {});
  }, [dailyState.status, dailyState.dailyData?.date]);

  const playerType = dailyState.dailyData?.playerType ?? 'BATTER';
  const screenshotUrl = dailyState.dailyData?.date ? dailyScreenshotUrl(dailyState.dailyData.date) : '';
  const isLoading = dailyState.status === 'loading' && !dailyState.error;
  const isFinished = dailyState.status === 'won' || dailyState.status === 'lost';

  if (isLoading) return <LoadingScreen error={null} />;
  if (dailyState.error && dailyState.status === 'loading') return <LoadingScreen error={dailyState.error} />;

  return (
    <main className="w-full max-w-[480px] flex-1 space-y-4">
      {dailyState.status === 'playing' && (
        <GamePlay
          dailyData={dailyState.dailyData ?? { date: '', playerType }}
          screenshotUrl={screenshotUrl}
          players={dailyState.players}
          guesses={dailyState.guesses}
          hints={dailyState.hints}
          isSubmitting={dailyState.isSubmitting}
          onGuess={dailyState.makeGuess}
        />
      )}
      {isFinished && dailyState.playerInfo && (
        <>
          <PercentileDisplay screenshotUrl={screenshotUrl} playerType={playerType} />
          <EndScreen
            status={dailyState.status}
            playerInfo={dailyState.playerInfo}
            guessCount={dailyState.guesses.length}
            guesses={dailyState.guesses}
            players={dailyState.players}
            date={dailyState.dailyData?.date ?? ''}
            hints={dailyState.hints}
            currentStreak={stats.currentStreak}
            isDailyMode
            globalStats={globalStats}
            onReplay={() => navigate('/replay')}
            onRandom={() => navigate('/random')}
          />
        </>
      )}
    </main>
  );
}
