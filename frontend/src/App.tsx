import { useEffect, useState } from 'react';
import { Routes, Route, useNavigate } from 'react-router-dom';
import Shell from './pages/Shell';
import DailyPage from './pages/DailyPage';
import ReplayPage from './pages/ReplayPage';
import RandomPage from './pages/RandomPage';
import ReplayPickerModal from './components/modals/ReplayPickerModal';
import { recordAnalytics } from './services/api';

export default function App() {
  useEffect(() => {
    const today = new Date().toISOString().slice(0, 10);
    const sessionKey = `savantle-session-${today}`;
    if (!localStorage.getItem(sessionKey)) {
      localStorage.setItem(sessionKey, '1');
      recordAnalytics('UNIQUE_VISITORS');
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Routes>
      <Route path="/" element={<Shell gameMode="daily"><DailyPage /></Shell>} />
      <Route path="/replay" element={<Shell gameMode="replay"><ReplayPickerRedirect /></Shell>} />
      <Route path="/replay/:date" element={<Shell gameMode="replay"><ReplayPage /></Shell>} />
      <Route path="/random" element={<Shell gameMode="random"><RandomPage /></Shell>} />
      <Route path="*" element={<RedirectToHome />} />
    </Routes>
  );
}

function ReplayPickerRedirect() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(true);
  return (
    <ReplayPickerModal
      open={open}
      onClose={() => { setOpen(false); navigate('/'); }}
      onSelect={(date) => { setOpen(false); navigate(`/replay/${date}`); }}
    />
  );
}

function RedirectToHome() {
  const navigate = useNavigate();
  useEffect(() => { navigate('/'); }, [navigate]);
  return null;
}
