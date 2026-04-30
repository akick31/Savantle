import { useState } from 'react';
import { PlayerType } from '../types';

interface PercentileDisplayProps {
  date: string;
  playerType: PlayerType;
}

export default function PercentileDisplay({ date, playerType }: PercentileDisplayProps) {
  const [errored, setErrored] = useState(false);
  const src = `/api/v1/savantle/screenshot/${date}`;

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

      <div className="rounded-lg overflow-hidden bg-white flex items-center justify-center min-h-[200px]">
        {errored ? (
          <div className="text-sv-muted text-sm p-6 text-center">
            Percentile chart unavailable.
          </div>
        ) : (
          <img
            src={src}
            alt="Player percentile rankings"
            className="w-full h-auto block"
            onError={() => setErrored(true)}
          />
        )}
      </div>

      <p className="mt-3 text-[11px] text-sv-muted text-center">
        Image source: Baseball Savant
      </p>
    </div>
  );
}
