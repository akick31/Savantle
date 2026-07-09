export const SITE_NAME = 'Savantle';
export const SITE_URL = 'https://savantle.com';

export interface RouteMeta {
  path: string;
  title: string;
  description: string;
  changeFreq: 'daily' | 'weekly';
  priority: string;
}

export const routes = {
  home: {
    path: '/',
    title: 'Savantle - Daily Baseball Savant Player Guessing Game',
    description: 'Guess the mystery MLB player from their Baseball Savant percentile rankings chart. A free daily baseball guessing game, Wordle-style, with a new player every day.',
    changeFreq: 'daily',
    priority: '1.0',
  },
  replay: {
    path: '/replay',
    title: 'Play a Previous Day | Savantle',
    description: 'Pick any past day and guess that day\'s mystery MLB player from their Baseball Savant percentile rankings chart.',
    changeFreq: 'weekly',
    priority: '0.5',
  },
  random: {
    path: '/random',
    title: 'Random Player | Savantle',
    description: 'Guess a random MLB player from their Baseball Savant percentile rankings chart. Play as many rounds as you want.',
    changeFreq: 'weekly',
    priority: '0.5',
  },
} satisfies Record<string, RouteMeta>;

export const routeList: RouteMeta[] = Object.values(routes);
