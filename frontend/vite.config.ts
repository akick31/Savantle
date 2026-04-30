import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const TODAY = new Date().toISOString().split('T')[0]

const MOCK_DAILY = {
  date: TODAY,
  playerType: 'BATTER',
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

const MOCK_PLAYER_INFO = {
  fullName: 'Aaron Judge',
  position: 'RF',
  teamName: 'New York Yankees',
  teamAbbr: 'NYY',
  league: 'AL',
  division: 'AL East',
  mlbamId: '592450',
  savantUrl: 'https://baseballsavant.mlb.com/savant-player/aaron-judge-592450?stats=statcast-r-batter',
}

// Tiny placeholder PNG (1x1 transparent pixel) for the screenshot mock endpoint.
const PLACEHOLDER_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
)

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'mock-api',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (!req.url?.startsWith('/api/v1/savantle')) return next()

          if (req.url.includes('/screenshot/')) {
            res.setHeader('Content-Type', 'image/png')
            res.setHeader('Cache-Control', 'public, max-age=86400')
            res.end(PLACEHOLDER_PNG)
            return
          }

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
                    playerInfo: MOCK_PLAYER_INFO,
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
    proxy: {},
  },
})
