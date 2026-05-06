import { useState } from 'react';
import { GameStatus, PlayerInfo, HintData, PlayerSearchItem } from '../../types';
import { buildShareText, calculateSaventleNumber } from '../../utils/share';
import { normalizeForSearch } from '../../utils/normalize';

interface EndScreenProps {
  status: GameStatus;
  playerInfo: PlayerInfo;
  guessCount: number;
  date: string;
  hints: HintData[];
  currentStreak: number;
  guesses: string[];
  players: PlayerSearchItem[];
  isDailyMode?: boolean;
  onReplay?: () => void;
  onRandom?: () => void;
  onPlayAnother?: () => void;
  onPlayAnotherLabel?: string;
}

export default function EndScreen({ status, playerInfo, guessCount, date, hints, currentStreak, guesses, players, isDailyMode = true, onReplay, onRandom, onPlayAnother, onPlayAnotherLabel }: EndScreenProps) {
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
          {won ? `Got it in ${guessCount}!` : isDailyMode ? 'Better luck tomorrow.' : 'Better luck next time.'}
        </p>
        {isDailyMode && (
          <p className="mt-1 text-xs text-sv-muted">
            Current win streak: {currentStreak} day{currentStreak === 1 ? '' : 's'}
          </p>
        )}
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

      {guesses.length > 0 && (
        <div className="border-t border-sv-border pt-4 space-y-1.5">
          <p className="text-xs font-semibold uppercase tracking-wider text-sv-muted mb-2">Your guesses</p>
          <div className="space-y-1.5">
            {guesses.map((name, i) => {
              const isCorrect = status === 'won' && i === guesses.length - 1;
              const found = players.find(p => p.normalizedName === normalizeForSearch(name));
              const mlbamId = found?.mlbamId;
              const url = mlbamId ? `https://baseballsavant.mlb.com/savant-player/${mlbamId}` : null;
              return (
                <div key={i} className={`flex items-center justify-between px-3 py-2 rounded-lg text-xs ${isCorrect ? 'bg-sv-green/10 border border-sv-green/30' : 'bg-sv-border'}`}>
                  <div className="flex items-center gap-2">
                    <span className={isCorrect ? 'text-sv-green' : 'text-sv-red'}>
                      {isCorrect ? '✓' : '✗'}
                    </span>
                    <span className={`font-medium ${isCorrect ? 'text-sv-green' : 'text-sv-text'}`}>{name}</span>
                  </div>
                  {url && (
                    <a
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sv-accent hover:underline flex items-center gap-0.5"
                    >
                      Savant
                      <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                        <polyline points="15 3 21 3 21 9" />
                        <line x1="10" y1="14" x2="21" y2="3" />
                      </svg>
                    </a>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

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

      {isDailyMode && (
        <button
          onClick={handleShare}
          className="w-full py-3 bg-sv-accent text-sv-bg rounded-lg font-bold text-sm hover:opacity-90 transition-opacity"
        >
          {copied ? 'Copied!' : 'Share Result'}
        </button>
      )}

      {!isDailyMode && onPlayAnother && (
        <button
          onClick={onPlayAnother}
          className="w-full py-2.5 border border-sv-border text-sv-muted rounded-lg text-xs font-semibold hover:text-sv-text hover:border-sv-accent transition-colors flex items-center justify-center gap-1.5"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 2v6h-6" /><path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
            <path d="M3 22v-6h6" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
          </svg>
          {onPlayAnotherLabel ?? 'Play Again'}
        </button>
      )}

      {isDailyMode && (onReplay || onRandom) && (
        <div className="flex gap-2 pt-1">
          {onReplay && (
            <button
              onClick={onReplay}
              className="flex-1 py-2.5 border border-sv-border text-sv-muted rounded-lg text-xs font-semibold hover:text-sv-text hover:border-sv-accent transition-colors flex items-center justify-center gap-1.5"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              Play Previous Day
            </button>
          )}
          {onRandom && (
              <button
                  onClick={onRandom}
                  className="flex-1 py-2.5 border border-sv-border text-sv-muted rounded-lg text-xs font-semibold hover:text-sv-text hover:border-sv-accent transition-colors flex items-center justify-center gap-1.5"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                Play Random Player
              </button>
          )}
        </div>
      )}
    </div>
  );
}
