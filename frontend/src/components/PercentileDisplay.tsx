import { PlayerType } from '../types';

interface PercentileDisplayProps {
  stats: Record<string, number>;
  playerType: PlayerType;
}

function getPercentileColor(p: number): string {
  if (p >= 90) return 'var(--sv-p100)';
  if (p >= 70) return 'var(--sv-p80)';
  if (p >= 45) return 'var(--sv-p50)';
  if (p >= 25) return 'var(--sv-p20)';
  return 'var(--sv-p0)';
}

function getTextColor(p: number): string {
  // Gray circle needs darker text in light mode, white for others
  if (p >= 45 && p < 70) return 'var(--sv-text)';
  return '#ffffff';
}

function PercentileBadge({ label, value }: { label: string; value: number }) {
  const bg = getPercentileColor(value);
  const textColor = getTextColor(value);

  return (
    <div className="flex flex-col items-center gap-1.5">
      <div
        className="w-14 h-14 rounded-full flex items-center justify-center font-bold text-lg shadow-sm flex-shrink-0"
        style={{ backgroundColor: bg, color: textColor }}
      >
        {value}
      </div>
      <span className="text-xs text-sv-muted text-center leading-tight max-w-[60px]">{label}</span>
    </div>
  );
}

export default function PercentileDisplay({ stats, playerType }: PercentileDisplayProps) {
  const entries = Object.entries(stats);

  return (
    <div className="bg-sv-card rounded-xl border border-sv-border p-4">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-semibold uppercase tracking-wider text-sv-muted">
          Percentile Rankings
        </span>
        <span className="text-xs text-sv-muted bg-sv-border px-2 py-0.5 rounded-full">
          {playerType === 'PITCHER' ? 'Pitcher' : 'Batter'}
        </span>
      </div>

      <div className="grid grid-cols-4 sm:grid-cols-5 gap-3 justify-items-center">
        {entries.map(([label, value]) => (
          <PercentileBadge key={label} label={label} value={value} />
        ))}
      </div>

      <div className="mt-4 pt-3 border-t border-sv-border">
        <div className="flex justify-between text-xs text-sv-muted">
          <span className="flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: 'var(--sv-p0)' }} />
            Low
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: 'var(--sv-p50)' }} />
            Avg
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: 'var(--sv-p100)' }} />
            Elite
          </span>
        </div>
      </div>
    </div>
  );
}
