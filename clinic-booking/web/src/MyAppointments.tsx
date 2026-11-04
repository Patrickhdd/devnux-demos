import { useEffect, useState } from 'react';
import { api, fmtDay, fmtTime, type Appointment, type Session } from './api';

export default function MyAppointments({ session }: { session: Session }) {
  const [items, setItems] = useState<Appointment[] | null>(null);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState<number | null>(null);

  const load = () => api.mine(session.token).then(setItems).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function cancel(id: number) {
    try { await api.cancel(session.token, id); setConfirming(null); load(); } catch (e) { setError((e as Error).message); }
  }

  if (!items) return <p className="muted">{error || 'Loading...'}</p>;
  const now = Date.now();
  const upcoming = items.filter((a) => a.status === 'BOOKED' && new Date(a.startsAt).getTime() > now).reverse();
  const past = items.filter((a) => !upcoming.includes(a));

  return (
    <div className="mine">
      <h1>My appointments</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <h2>Upcoming</h2>
      {upcoming.length === 0 && <p className="muted">No upcoming appointments.</p>}
      <div className="list">
        {upcoming.map((a) => (
          <article key={a.id} className="appt card">
            <div className="when"><b>{fmtTime(a.startsAt)}</b><span>{fmtDay(a.startsAt)}</span></div>
            <div className="what"><b>{a.doctorName}</b><span>{a.specialty}{a.reason ? ` · ${a.reason}` : ''}</span></div>
            {confirming === a.id ? (
              <div className="actions">
                <button className="btn danger" onClick={() => cancel(a.id)}>Yes, cancel it</button>
                <button className="btn ghost" onClick={() => setConfirming(null)}>Keep it</button>
              </div>
            ) : (
              <button className="btn ghost" onClick={() => setConfirming(a.id)}>Cancel</button>
            )}
          </article>
        ))}
      </div>
      {past.length > 0 && (<>
        <h2>Past and cancelled</h2>
        <div className="list">
          {past.map((a) => (
            <article key={a.id} className="appt card dim">
              <div className="when"><b>{fmtTime(a.startsAt)}</b><span>{fmtDay(a.startsAt)}</span></div>
              <div className="what"><b>{a.doctorName}</b><span>{a.specialty}{a.reason ? ` · ${a.reason}` : ''}</span></div>
              <span className={`badge ${a.status.toLowerCase()}`}>{a.status === 'BOOKED' ? 'Past' : a.status.toLowerCase()}</span>
            </article>
          ))}
        </div>
      </>)}
    </div>
  );
}
