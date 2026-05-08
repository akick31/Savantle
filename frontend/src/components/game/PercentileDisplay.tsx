import { useEffect, useRef, useState } from 'react';
import { PlayerType } from '../../types';

interface PercentileDisplayProps {
  screenshotUrl: string;
  playerType: PlayerType;
}

export default function PercentileDisplay({ screenshotUrl, playerType }: PercentileDisplayProps) {
  const [imgSrc, setImgSrc] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [errored, setErrored] = useState(false);
  const urlRef = useRef<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    abortRef.current?.abort();

    setImgSrc(null);
    setLoading(true);
    setErrored(false);

    const controller = new AbortController();
    abortRef.current = controller;

    const cb = Date.now();
    fetch(`${screenshotUrl}?cb=${cb}`, { signal: controller.signal })
      .then(r => {
        if (!r.ok) throw new Error('non-ok');
        return r.blob();
      })
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        urlRef.current = objectUrl;
        setImgSrc(objectUrl);
        setLoading(false);
      })
      .catch(err => {
        if (err.name === 'AbortError') return;
        setErrored(true);
        setLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [screenshotUrl]);

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
        {loading && (
          <div className="flex flex-col items-center gap-2 p-6">
            <svg className="animate-spin text-sv-muted" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 12a9 9 0 1 1-6.219-8.56" />
            </svg>
            <span className="text-sv-muted text-xs">Loading percentiles…</span>
          </div>
        )}
        {errored && (
          <div className="text-sv-muted text-sm p-6 text-center">
            Percentile chart unavailable.
          </div>
        )}
        {imgSrc && !loading && !errored && (
          <img
            src={imgSrc}
            alt="Player percentile rankings"
            className="w-full h-auto block"
          />
        )}
      </div>

      <p className="mt-3 text-[11px] text-sv-muted text-center">
        Image source: Baseball Savant
      </p>
    </div>
  );
}
