import { useEffect, useState } from 'react';
import { api, addDays, dayKey, fmtDay, fmtTime, todayInClinic, type Appointment, type Session } from './api';

const DAYS = 7;

export default function DoctorSchedule({ session }: { session: Session }) {
  const [from, setFrom] = useState(todayInClinic());
  const [items, setItems] = useState<Appointment[] | null>(null);
  const [error, setError] = useState('');

  const load = () => api.schedule(session.token, from, DAYS).then(setItems).catch((e) => setError(e.message));
  useEffect(() => { setItems(null); load(); }, [from]); // eslint-disable-line react-hooks/exhaustive-deps

  async function act(fn: () => Promise<unknown>) {
    setError('');
    try { await fn(); load(); } catch (e) { setError((e as Error).message); }
  }

  const days = Array.from({ length: DAYS }, (_, i) => addDays(from, i));
  const booked = items?.filter((a) => a.status === 'BOOKED').length ?? 0;

  return (
    <div className="schedule">
      <div className="sched-head">
        <div>
          <h1>My schedule</h1>
          <p className="muted">{items ? `${booked} booked appointment${booked === 1 ? '' : 's'} in these ${DAYS} days` : 'Loading...'}</p>
        </div>
        <div className="pager">
          <button className="btn ghost" onClick={() => setFrom(addDays(from, -DAYS))}>Previous week</button>
          <button className="btn ghost" onClick={() => setFrom(todayInClinic())}>Today</button>
          <button className="btn ghost" onClick={() => setFrom(addDays(from, DAYS))}>Next week</button>
        </div>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {items && days.map((d) => {
        const list = items.filter((a) => dayKey(a.startsAt) === d);
        return (
          <section key={d} className="sched-day">
            <h2>{fmtDay(`${d}T16:00:00Z`)}</h2>
            {list.length === 0 ? <p className="muted">No appointments.</p> : (
              <div className="list">
                {list.map((a) => (
                  <article key={a.id} className={`appt card ${a.status !== 'BOOKED' ? 'dim' : ''}`}>
                    <div className="when"><b>{fmtTime(a.startsAt)}</b><span>to {fmtTime(a.endsAt)}</span></div>
                    <div className="what"><b>{a.patientName}</b><span>{a.reason || 'No reason given'}</span></div>
                    {a.status === 'BOOKED' ? (
                      <div className="actions">
                        {new Date(a.startsAt) <= new Date()
                          ? <button className="btn ghost" onClick={() => act(() => api.complete(session.token, a.id))}>Mark as seen</button>
                          : <button className="btn ghost" onClick={() => act(() => api.cancel(session.token, a.id))}>Cancel</button>}
                      </div>
                    ) : <span className={`badge ${a.status.toLowerCase()}`}>{a.status === 'COMPLETED' ? 'Seen' : 'Cancelled'}</span>}
                  </article>
                ))}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
