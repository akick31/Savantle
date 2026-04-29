import { useState } from 'react';
import { GameStatus, PlayerInfo, HintData } from '../types';
import { buildShareText } from '../utils/share';

interface EndScreenProps {
  status: GameStatus;
  playerInfo: PlayerInfo;
  guessCount: number;
  date: string;
  hints: HintData[];
}

export default function EndScreen({ status, playerInfo, guessCount, date, hints }: EndScreenProps) {
  const [copied, setCopied] = useState(false);
  const won = status === 'won';

  const handleShare = async () => {
    const text = buildShareText(status, guessCount, date);
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      alert(text);
    }
  };

  const imageUrl = `https://img.mlbstatic.com/mlb-photos/image/upload/d_people:generic:headshot:67:current.png/h_120,w_107/v1/people/${playerInfo.mlbamId}/headshot/67/current`;

  return (
    <div className="bg-sv-card border border-sv-border rounded-xl p-5 space-y-5">
      <div className="text-center">
        <div className={`text-3xl mb-1 ${won ? 'text-sv-green' : 'text-sv-red'}`}>
          {won ? '🎉' : '😔'}
        </div>
        <p className={`font-bold text-lg ${won ? 'text-sv-green' : 'text-sv-red'}`}>
          {won ? `Got it in ${guessCount}!` : 'Better luck tomorrow!'}
        </p>
      </div>

      <div className="flex gap-4 items-center">
        <img
          src={imageUrl}
          alt={playerInfo.fullName}
          className="w-20 h-20 rounded-full object-cover border-2 border-sv-border bg-sv-border flex-shrink-0"
          onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }}
        />
        <div>
          <p className="text-xl font-bold text-sv-text">{playerInfo.fullName}</p>
          <p className="text-sm text-sv-muted mt-0.5">
            {playerInfo.position} · {playerInfo.teamName}
          </p>
          <p className="text-xs text-sv-muted">{playerInfo.division}</p>
        </div>
      </div>

      {hints.length > 0 && (
        <div className="border-t border-sv-border pt-4 space-y-1.5">
          <p className="text-xs font-semibold uppercase tracking-wider text-sv-muted mb-2">Hints revealed</p>
          <div className="flex flex-wrap gap-2">
            {hints.map(hint => (
              <div key={hint.type} className="bg-sv-border rounded-lg px-3 py-1.5 text-xs">
                <span className="text-sv-muted">{hint.label}: </span>
                <span className="text-sv-text font-medium">{hint.value}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <button
        onClick={handleShare}
        className="w-full py-3 bg-sv-accent text-sv-bg rounded-lg font-bold text-sm hover:opacity-90 transition-opacity"
      >
        {copied ? '✓ Copied!' : '📋 Share Result'}
      </button>
    </div>
  );
}
