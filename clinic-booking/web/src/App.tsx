import { useState } from 'react';
import { loadSession, saveSession, type Session } from './api';
import Login from './Login';
import BookPage from './BookPage';
import MyAppointments from './MyAppointments';
import DoctorSchedule from './DoctorSchedule';

type Tab = 'book' | 'mine';

export default function App() {
  const [session, setSession] = useState<Session | null>(loadSession);
  const [tab, setTab] = useState<Tab>('book');
  const [refreshKey, setRefreshKey] = useState(0);

  const signIn = (s: Session) => { saveSession(s); setSession(s); setTab('book'); };
  const signOut = () => { saveSession(null); setSession(null); };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="logo" aria-hidden="true">+</span> Northside Clinic</div>
        {session && (
          <nav className="tabs">
            {session.user.role === 'PATIENT' && (<>
              <button className={tab === 'book' ? 'on' : ''} onClick={() => setTab('book')}>Book</button>
              <button className={tab === 'mine' ? 'on' : ''} onClick={() => setTab('mine')}>My appointments</button>
            </>)}
            {session.user.role === 'DOCTOR' && <button className="on">My schedule</button>}
          </nav>
        )}
        {session && (
          <div className="who">
            <span>{session.user.fullName}</span>
            <button className="link" onClick={signOut}>Sign out</button>
          </div>
        )}
      </header>

      <main className="content">
        {!session && <Login onSignedIn={signIn} />}
        {session?.user.role === 'PATIENT' && tab === 'book' &&
          <BookPage session={session} onBooked={() => { setRefreshKey((k) => k + 1); setTab('mine'); }} />}
        {session?.user.role === 'PATIENT' && tab === 'mine' && <MyAppointments key={refreshKey} session={session} />}
        {session?.user.role === 'DOCTOR' && <DoctorSchedule session={session} />}
      </main>

      <footer className="foot">
        Demo app by <a href="https://devnux.tech" rel="noopener">Devnux</a> · Spring Boot, React and PostgreSQL ·
        Times shown in clinic time (New York)
      </footer>
    </div>
  );
}
