import { useState, useRef, useEffect, useCallback } from 'react';
import { PlayerSearchItem } from '../../types';
import { normalizeForSearch } from '../../utils/normalize';

interface PlayerSearchProps {
  players: PlayerSearchItem[];
  playerType: 'BATTER' | 'PITCHER';
  onSubmit: (name: string) => void;
  disabled: boolean;
}

export default function PlayerSearch({ players, playerType, onSubmit, disabled }: PlayerSearchProps) {
  const filteredPlayers = players.filter(p => p.playerType === playerType);
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<PlayerSearchItem[]>([]);
  const [activeIdx, setActiveIdx] = useState(-1);
  const [showDropdown, setShowDropdown] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const getSuggestions = useCallback((q: string): PlayerSearchItem[] => {
    if (q.length < 2) return [];
    const norm = normalizeForSearch(q);
    return filteredPlayers
      .filter(p => p.normalizedName.includes(norm))
      .slice(0, 8);
  }, [filteredPlayers]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setQuery(val);
    setActiveIdx(-1);
    const suggs = getSuggestions(val);
    setSuggestions(suggs);
    setShowDropdown(suggs.length > 0);
  };

  const handleSelect = (player: PlayerSearchItem) => {
    setQuery(player.fullName);
    setSuggestions([]);
    setShowDropdown(false);
    setActiveIdx(-1);
    inputRef.current?.focus();
  };

  const handleSubmit = () => {
    const trimmed = query.trim();
    if (!trimmed) return;

    const norm = normalizeForSearch(trimmed);
    const match = filteredPlayers.find(p => p.normalizedName === norm);
    if (!match) {
      inputRef.current?.setCustomValidity('Please select a valid player from the list.');
      inputRef.current?.reportValidity();
      return;
    }
    inputRef.current?.setCustomValidity('');

    setQuery('');
    setSuggestions([]);
    setShowDropdown(false);
    onSubmit(match.fullName);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIdx(i => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx(i => Math.max(i - 1, -1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeIdx >= 0 && suggestions[activeIdx]) {
        handleSelect(suggestions[activeIdx]);
      } else {
        handleSubmit();
      }
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
      setActiveIdx(-1);
    }
  };

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (
        !inputRef.current?.contains(e.target as Node) &&
        !dropdownRef.current?.contains(e.target as Node)
      ) {
        setShowDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  return (
    <div className="space-y-2">
      <div className="relative">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              onFocus={() => { if (suggestions.length > 0) setShowDropdown(true); }}
              disabled={disabled}
              placeholder="Enter full player name (e.g., Aaron Judge)"
              className="w-full bg-sv-card border border-sv-border rounded-lg px-4 py-2.5 text-sv-text placeholder-sv-muted text-sm focus:outline-none focus:border-sv-accent transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-base md:text-sm"
              autoComplete="off"
              spellCheck={false}
              style={{ fontSize: '16px' }}
            />

            {showDropdown && suggestions.length > 0 && (
              <div
                ref={dropdownRef}
                className="absolute z-20 left-0 right-0 bg-sv-card border border-sv-border rounded-lg shadow-xl overflow-hidden md:top-full md:mt-1 bottom-full md:bottom-auto mb-1 md:mb-0 max-h-48 overflow-y-auto"
              >
                {suggestions.map((p, i) => (
                  <button
                    key={p.fullName}
                    onMouseDown={e => { e.preventDefault(); handleSelect(p); }}
                    className={`w-full text-left px-4 py-2.5 text-sm transition-colors ${
                      i === activeIdx
                        ? 'bg-sv-accent text-sv-bg font-medium'
                        : 'text-sv-text hover:bg-sv-border'
                    }`}
                  >
                    {p.fullName}
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            onClick={handleSubmit}
            disabled={disabled || !query.trim()}
            className="px-4 py-2.5 bg-sv-accent text-sv-bg rounded-lg font-semibold text-sm hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
          >
            Guess
          </button>
        </div>

        <p className="text-xs text-sv-muted mt-1.5">
          Full name required · Accents optional
        </p>
      </div>
    </div>
  );
}
