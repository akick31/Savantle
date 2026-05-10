export type PlayerType = 'BATTER' | 'PITCHER';

export type GameMode = 'daily' | 'replay' | 'random';

export type ModalId = 'how-to-play' | 'stats' | 'global-stats' | 'settings' | 'contact' | 'replay-picker' | 'projects';

export type GameStatus = 'loading' | 'playing' | 'won' | 'lost';

export interface DailyData {
  date: string;
  playerType: PlayerType;
}

export interface HintData {
  type: 'POSITION' | 'LEAGUE' | 'DIVISION' | 'TEAM';
  label: string;
  value: string;
  confirmed: boolean;
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
  hints?: HintData[];
  playerInfo?: PlayerInfo;
}

export interface PlayerSearchItem {
  fullName: string;
  normalizedName: string;
  playerType: PlayerType;
  mlbamId: string;
}

export interface RandomGameData {
  gameId: string;
  playerType: PlayerType;
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

export interface GlobalStats {
  totalWins: number;
  totalLosses: number;
  guessDistribution: Record<string, number>;
  averageGuesses: number;
}
