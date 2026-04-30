import { useState } from 'react';
import { GameStatus, PlayerInfo, HintData } from '../types';
import { buildShareText, calculateSaventleNumber } from '../utils/share';

interface EndScreenProps {
  status: GameStatus;
  playerInfo: PlayerInfo;
  guessCount: number;
  date: string;
  hints: HintData[];
  currentStreak: number;
}

export default function EndScreen({ status, playerInfo, guessCount, date, hints, currentStreak }: EndScreenProps) {
  const [copied, setCopied] = useState(false);
  const won = status === 'won';

  const handleShare = async () => {
    const saventleNum = calculateSaventleNumber(date);
    const text = buildShareText(status, guessCount, currentStreak, saventleNum);
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      alert(text);
    }
  };

  const imageUrl = `https://img.mlbstatic.com/mlb-photos/image/upload/d_people:generic:headshot:67:current.png/h_120,w_107/v1/people/${playerInfo.mlbamId}/headshot/67/current`;

  const savantUrl =
    playerInfo.savantUrl ||
    `https://baseballsavant.mlb.com/savant-player/${playerInfo.mlbamId}`;

  return (
    <div className="bg-sv-card border border-sv-border rounded-xl p-5 space-y-5">
      <div className="text-center">
        <p className={`font-bold text-lg ${won ? 'text-sv-green' : 'text-sv-red'}`}>
          {won ? `Got it in ${guessCount}!` : 'Better luck tomorrow.'}
        </p>
        <p className="mt-1 text-xs text-sv-muted">
          Current win streak: {currentStreak} day{currentStreak === 1 ? '' : 's'}
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
          <a
            href={savantUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 mt-1.5 text-xs text-sv-accent hover:underline"
          >
            View on Baseball Savant
            <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
              <polyline points="15 3 21 3 21 9" />
              <line x1="10" y1="14" x2="21" y2="3" />
            </svg>
          </a>
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
        {copied ? 'Copied!' : 'Share Result'}
      </button>
    </div>
  );
}
