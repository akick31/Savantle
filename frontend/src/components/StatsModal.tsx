import Modal from './Modal';
import { PlayerStats } from '../types';

interface StatsModalProps {
  open: boolean;
  onClose: () => void;
  stats: PlayerStats;
}

export default function StatsModal({ open, onClose, stats }: StatsModalProps) {
  const winPct = stats.gamesPlayed > 0
    ? Math.round((stats.gamesWon / stats.gamesPlayed) * 100)
    : 0;

  const maxDist = Math.max(...Object.values(stats.guessDistribution), 1);

  return (
    <Modal open={open} onClose={onClose} title="Statistics">
      <div className="space-y-5">
        <div className="grid grid-cols-4 gap-2 text-center">
          {[
            [stats.gamesPlayed, 'Played'],
            [winPct + '%', 'Win %'],
            [stats.currentStreak, 'Win Streak'],
            [stats.maxStreak, 'Best Streak'],
          ].map(([val, label]) => (
            <div key={String(label)} className="flex flex-col gap-1">
              <span className="text-2xl font-bold text-sv-text">{val}</span>
              <span className="text-xs text-sv-muted leading-tight">{label}</span>
            </div>
          ))}
        </div>

        <div className="border-t border-sv-border pt-4">
          <p className="text-xs font-medium text-sv-text mb-3 uppercase tracking-wide">Guess Distribution</p>
          <div className="space-y-1.5">
            {[1, 2, 3, 4, 5].map(n => {
              const count = stats.guessDistribution[n] ?? 0;
              const pct = Math.round((count / maxDist) * 100);
              return (
                <div key={n} className="flex items-center gap-2 text-sm">
                  <span className="text-sv-muted w-3 flex-shrink-0">{n}</span>
                  <div className="flex-1 bg-sv-border rounded-full h-5 overflow-hidden">
                    <div
                      className="h-full bg-sv-accent rounded-full flex items-center justify-end pr-1.5 transition-all"
                      style={{ width: `${Math.max(pct, count > 0 ? 10 : 0)}%` }}
                    >
                      {count > 0 && <span className="text-xs font-bold text-sv-bg">{count}</span>}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </Modal>
  );
}
