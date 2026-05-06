import { useState, useEffect, useMemo } from 'react';
import Modal from './Modal';
import { fetchAvailableDates } from '../../services/api';

interface ReplayPickerModalProps {
  open: boolean;
  onClose: () => void;
  onSelect: (date: string) => void;
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export default function ReplayPickerModal({ open, onClose, onSelect }: ReplayPickerModalProps) {
  const [availableDates, setAvailableDates] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [viewYear, setViewYear] = useState(() => new Date().getFullYear());
  const [viewMonth, setViewMonth] = useState(() => new Date().getMonth()); // 0-indexed

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    fetchAvailableDates()
      .then(dates => setAvailableDates(new Set(dates)))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [open]);

  const calendarDays = useMemo(() => {
    const firstDay = new Date(viewYear, viewMonth, 1).getDay();
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
    const cells: (number | null)[] = Array(firstDay).fill(null);
    for (let d = 1; d <= daysInMonth; d++) cells.push(d);
    while (cells.length % 7 !== 0) cells.push(null);
    return cells;
  }, [viewYear, viewMonth]);

  function toDateStr(day: number) {
    return `${viewYear}-${String(viewMonth + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }

  function prevMonth() {
    if (viewMonth === 0) { setViewMonth(11); setViewYear(y => y - 1); }
    else setViewMonth(m => m - 1);
  }

  function nextMonth() {
    const now = new Date();
    const isCurrentMonth = viewYear === now.getFullYear() && viewMonth === now.getMonth();
    if (isCurrentMonth) return; // can't go past current month
    if (viewMonth === 11) { setViewMonth(0); setViewYear(y => y + 1); }
    else setViewMonth(m => m + 1);
  }

  const now = new Date();
  const isCurrentMonth = viewYear === now.getFullYear() && viewMonth === now.getMonth();
  const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

  function pickRandomDay() {
    if (availableDates.size === 0) return;
    const dates = Array.from(availableDates);
    const randomDate = dates[Math.floor(Math.random() * dates.length)];
    onSelect(randomDate);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Play a Previous Day">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-xs text-sv-muted">
            Pick a day or let us choose one for you.
          </p>
          <button
            onClick={pickRandomDay}
            disabled={loading || availableDates.size === 0}
            className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border border-sv-border text-sv-muted hover:text-sv-text hover:border-sv-accent transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="16 3 21 3 21 8" />
              <line x1="4" y1="20" x2="21" y2="3" />
              <polyline points="21 16 21 21 16 21" />
              <line x1="15" y1="15" x2="21" y2="21" />
            </svg>
            Random Day
          </button>
        </div>

        {loading ? (
          <div className="text-center py-6 text-sv-muted text-sm">Loading dates...</div>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <button
                onClick={prevMonth}
                className="p-1.5 text-sv-muted hover:text-sv-text transition-colors rounded"
                aria-label="Previous month"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="15 18 9 12 15 6" />
                </svg>
              </button>
              <span className="text-sm font-semibold text-sv-text">
                {MONTH_NAMES[viewMonth]} {viewYear}
              </span>
              <button
                onClick={nextMonth}
                disabled={isCurrentMonth}
                className="p-1.5 text-sv-muted hover:text-sv-text transition-colors rounded disabled:opacity-30 disabled:cursor-not-allowed"
                aria-label="Next month"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="9 18 15 12 9 6" />
                </svg>
              </button>
            </div>

            <div className="grid grid-cols-7 text-center">
              {['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(d => (
                <div key={d} className="text-xs font-medium text-sv-muted py-1">{d}</div>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-y-1 text-center">
              {calendarDays.map((day, i) => {
                if (day === null) return <div key={i} />;
                const dateStr = toDateStr(day);
                const isAvailable = availableDates.has(dateStr);
                const isToday = dateStr === todayStr;

                return (
                  <button
                    key={dateStr}
                    disabled={!isAvailable}
                    onClick={() => { onSelect(dateStr); onClose(); }}
                    className={`
                      w-8 h-8 mx-auto rounded-full text-xs font-medium transition-colors
                      ${isToday ? 'ring-1 ring-sv-accent' : ''}
                      ${isAvailable
                        ? 'bg-sv-accent/15 text-sv-accent hover:bg-sv-accent hover:text-sv-bg cursor-pointer'
                        : 'text-sv-border cursor-default'}
                    `}
                  >
                    {day}
                  </button>
                );
              })}
            </div>

            <p className="text-xs text-sv-muted text-center">
              <span className="inline-block w-2.5 h-2.5 rounded-full bg-sv-accent/15 border border-sv-accent/40 mr-1 align-middle" />
              = game available
            </p>
          </>
        )}
      </div>
    </Modal>
  );
}
