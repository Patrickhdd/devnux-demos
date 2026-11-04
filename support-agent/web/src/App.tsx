import { useEffect, useState } from 'react';
import { api, loadJson, saveJson, type Customer, type Session } from './api';
import Chat from './Chat';
import Staff from './Staff';

const SESSION_KEY = 'harbor.session';

function Logo() {
  return (
    <a className="brand" href="/">
      <svg viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="8" /><path d="M16 6l7 11h-4l5 8H8l5-8H9z" /></svg>
      Harbor &amp; Pine
    </a>
  );
}

export default function App() {
  const isStaff = window.location.pathname.startsWith('/staff');
  const [session, setSession] = useState<Session | null>(() => loadJson<Session>(SESSION_KEY));

  const signOut = () => { saveJson(SESSION_KEY, null); saveJson('harbor.conversation', null); setSession(null); };

  return (
    <div className="shell">
      <header className="top">
        <Logo />
        <nav>
          {isStaff ? <a href="/">Customer view</a> : <a href="/staff">Staff console</a>}
          {!isStaff && session && (
            <>
              <span className="who">{session.customer.fullName}</span>
              <button className="link" onClick={signOut}>Sign out</button>
            </>
          )}
        </nav>
      </header>
      <main>
        {isStaff ? <Staff /> : session ? <Chat session={session} onExpired={signOut} /> : (
          <PickCustomer onPick={(s) => { saveJson(SESSION_KEY, s); setSession(s); }} />
        )}
      </main>
      <footer>
        Demo app by <a href="https://devnux.tech">Devnux</a> · Claude agent with tools, Node.js, PostgreSQL and React · Harbor &amp; Pine is a fictional store
      </footer>
    </div>
  );
}

function PickCustomer({ onPick }: { onPick: (s: Session) => void }) {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [error, setError] = useState('');
  useEffect(() => { api.customers().then(setCustomers).catch((e) => setError(e.message)); }, []);

  return (
    <section className="pick">
      <h1>How can we help?</h1>
      <p className="lead">Chat with our assistant about orders, returns, shipping and more. It can look up your orders, start a refund for our team to review, and hand you to a person any time.</p>
      <h2>Sign in as a demo customer</h2>
      {error && <p className="error">{error}</p>}
      <div className="people">
        {customers.map((c) => (
          <button key={c.id} className="person" onClick={() => api.signIn(c.id).then(onPick).catch((e) => setError(e.message))}>
            <span className="avatar">{c.fullName.split(' ').map((p) => p[0]).join('')}</span>
            <span><strong>{c.fullName}</strong><small>{c.email}</small></span>
          </button>
        ))}
      </div>
    </section>
  );
}
