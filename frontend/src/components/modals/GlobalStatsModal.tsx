import { useEffect, useState } from 'react';
import Modal from './Modal';
import { fetchGlobalStats } from '../../services/api';
import { GlobalStats } from '../../types';

interface GlobalStatsModalProps {
  open: boolean;
  onClose: () => void;
}

export default function GlobalStatsModal({ open, onClose }: GlobalStatsModalProps) {
  const [stats, setStats] = useState<GlobalStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    setError(false);
    fetchGlobalStats()
      .then(setStats)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [open]);

  const totalGames = stats ? stats.totalWins + stats.totalLosses : 0;
  const winPct = totalGames > 0 ? Math.round((stats!.totalWins / totalGames) * 100) : 0;
  const maxDist = stats ? Math.max(...[1,2,3,4,5].map(n => stats.guessDistribution[String(n)] ?? 0), 1) : 1;

  return (
    <Modal open={open} onClose={onClose} title="Global Stats">
      {loading && (
        <div className="flex justify-center py-8">
          <svg className="animate-spin text-sv-muted" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12a9 9 0 1 1-6.219-8.56" />
          </svg>
        </div>
      )}

      {error && (
        <p className="text-sv-muted text-sm text-center py-8">Could not load global stats.</p>
      )}

      {stats && !loading && (
        <div className="space-y-5">
          <div className="grid grid-cols-4 gap-2 text-center">
            <div>
              <p className="text-xl font-bold text-sv-text">{stats.totalWins.toLocaleString()}</p>
              <p className="text-xs text-sv-muted mt-0.5">Wins</p>
            </div>
            <div>
              <p className="text-xl font-bold text-sv-text">{stats.totalLosses.toLocaleString()}</p>
              <p className="text-xs text-sv-muted mt-0.5">Losses</p>
            </div>
            <div>
              <p className="text-xl font-bold text-sv-text">{winPct}%</p>
              <p className="text-xs text-sv-muted mt-0.5">Win %</p>
            </div>
            <div>
              <p className="text-xl font-bold text-sv-text">{stats.averageGuesses.toFixed(2)}</p>
              <p className="text-xs text-sv-muted mt-0.5">Avg Guesses</p>
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-sv-muted mb-3">Winning Guess Distribution</p>
            <div className="space-y-1.5">
              {[1, 2, 3, 4, 5].map(n => {
                const count = stats.guessDistribution[String(n)] ?? 0;
                const barPct = Math.round((count / maxDist) * 100);
                const labelPct = totalGames > 0 ? Math.round((count / totalGames) * 100) : 0;
                return (
                  <div key={n} className="flex items-center gap-2">
                    <span className="text-xs font-medium text-sv-muted w-3 flex-shrink-0">{n}</span>
                    <div className="flex-1 bg-sv-border rounded h-5 overflow-hidden">
                      <div
                        className="h-full bg-sv-accent rounded flex items-center justify-end pr-2 transition-all"
                        style={{ width: `${Math.max(barPct, count > 0 ? 8 : 0)}%` }}
                      >
                        {count > 0 && <span className="text-[10px] font-bold text-sv-bg">{labelPct}%</span>}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <p className="text-[11px] text-sv-muted text-center">
            Stats reflect today's daily Savantle games. Avg guesses counts losses as 6.
          </p>
        </div>
      )}
    </Modal>
  );
}
