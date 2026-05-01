export type PlayerType = 'BATTER' | 'PITCHER';

export type GameStatus = 'loading' | 'playing' | 'won' | 'lost';

export interface DailyData {
  date: string;
  playerType: PlayerType;
}

export interface HintData {
  type: 'POSITION' | 'LEAGUE' | 'DIVISION' | 'TEAM';
  label: string;
  value: string;
}

export interface PlayerInfo {
  fullName: string;
  position: string;
  teamName: string;
  teamAbbr: string;
  league: string;
  division: string;
  mlbamId: string;
  savantUrl?: string;
}

export interface GuessResult {
  correct: boolean;
  gameOver?: boolean;
  hint?: HintData;
  playerInfo?: PlayerInfo;
}

export interface PlayerSearchItem {
  fullName: string;
  normalizedName: string;
  playerType: 'BATTER' | 'PITCHER';
}

export interface StoredGame {
  date: string;
  status: GameStatus;
  guesses: string[];
  hints: HintData[];
  playerInfo: PlayerInfo | null;
}

export interface PlayerStats {
  currentStreak: number;
  maxStreak: number;
  gamesPlayed: number;
  gamesWon: number;
  lastPlayedDate: string;
  guessDistribution: Record<number, number>;
}

export interface Settings {
  darkMode: boolean;
  highContrast: boolean;
}
