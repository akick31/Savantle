import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const TODAY = new Date().toISOString().split('T')[0]

const MOCK_DAILY = {
  date: TODAY,
  playerType: 'BATTER',
  stats: {
    'Exit Velo': 87, 'Hard Hit%': 72, 'Barrel%': 65,
    'xBA': 91, 'xSLG': 88, 'xwOBA': 93,
    'K%': 78, 'BB%': 82, 'Whiff%': 61, 'Chase Rate': 55, 'Sprint Speed': 70,
  },
}

const MOCK_PLAYERS = [
  { fullName: 'Aaron Judge', normalizedName: 'aaron judge' },
  { fullName: 'Shohei Ohtani', normalizedName: 'shohei ohtani' },
  { fullName: 'Mookie Betts', normalizedName: 'mookie betts' },
  { fullName: 'Fernando Tatis Jr.', normalizedName: 'fernando tatis jr.' },
  { fullName: 'Yordan Alvarez', normalizedName: 'yordan alvarez' },
  { fullName: 'Ronald Acuna Jr.', normalizedName: 'ronald acuna jr.' },
  { fullName: 'Julio Rodriguez', normalizedName: 'julio rodriguez' },
  { fullName: 'Juan Soto', normalizedName: 'juan soto' },
]

const MOCK_HINTS: Record<number, object> = {
  1: { type: 'POSITION', label: 'Position', value: 'RF' },
  2: { type: 'LEAGUE', label: 'League', value: 'AL' },
  3: { type: 'DIVISION', label: 'Division', value: 'AL East' },
  4: { type: 'TEAM', label: 'Team', value: 'New York Yankees' },
}

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'mock-api',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (!req.url?.startsWith('/api/v1/savantle')) return next()
          res.setHeader('Content-Type', 'application/json')

          if (req.url.includes('/daily')) {
            res.end(JSON.stringify(MOCK_DAILY))
          } else if (req.url.includes('/players')) {
            res.end(JSON.stringify(MOCK_PLAYERS))
          } else if (req.url.includes('/guess') && req.method === 'POST') {
            let body = ''
            req.on('data', (c: Buffer) => { body += c.toString() })
            req.on('end', () => {
              try {
                const { guessNumber } = JSON.parse(body) as { guessNumber: number }
                if (guessNumber >= 5) {
                  res.end(JSON.stringify({
                    correct: false, gameOver: true,
                    playerInfo: { fullName: 'Aaron Judge', position: 'RF', teamName: 'New York Yankees', teamAbbr: 'NYY', league: 'AL', division: 'AL East', mlbamId: '592450' },
                  }))
                } else {
                  res.end(JSON.stringify({ correct: false, hint: MOCK_HINTS[guessNumber] ?? null }))
                }
              } catch { res.statusCode = 400; res.end('{}') }
            })
          } else {
            res.statusCode = 404; res.end('{}')
          }
        })
      },
    },
  ],
  server: {
    port: 3000,
    proxy: {
      // Only used when the mock plugin doesn't match (i.e., in future with real backend)
    },
  },
})
