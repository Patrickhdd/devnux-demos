import { useEffect, useMemo, useState } from 'react';
import { api, addDays, fmtDay, fmtTime, labelForDate, todayInClinic, ApiError, type Doctor, type Session, type Slot } from './api';

const DAYS_SHOWN = 14;

export default function BookPage({ session, onBooked }: { session: Session; onBooked: () => void }) {
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [date, setDate] = useState(todayInClinic());
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [chosen, setChosen] = useState<Slot | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const dates = useMemo(() => Array.from({ length: DAYS_SHOWN }, (_, i) => addDays(todayInClinic(), i)), []);

  useEffect(() => { api.doctors().then((d) => { setDoctors(d); setDoctor((cur) => cur ?? d[0] ?? null); }).catch((e) => setError(e.message)); }, []);

  useEffect(() => {
    if (!doctor) return;
    setSlots(null); setChosen(null);
    api.slots(doctor.id, date).then(setSlots).catch((e) => setError(e.message));
  }, [doctor, date]);

  async function confirm() {
    if (!doctor || !chosen) return;
    setBusy(true); setError('');
    try {
      await api.book(session.token, doctor.id, chosen.startsAt, reason.trim());
      onBooked();
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && e.status === 409) api.slots(doctor.id, date).then(setSlots);
    } finally { setBusy(false); }
  }

  const free = slots?.filter((s) => s.available).length ?? 0;

  return (
    <div className="book">
      <section>
        <h2 className="step"><span>1</span> Choose a doctor</h2>
        <div className="doctors">
          {doctors.map((d) => (
            <button key={d.id} className={`doctor ${doctor?.id === d.id ? 'on' : ''}`} onClick={() => setDoctor(d)} aria-pressed={doctor?.id === d.id}>
              <span className="avatar" aria-hidden="true">{d.name.replace('Dr. ', '').split(' ').map((p) => p[0]).join('')}</span>
              <span className="doc-text">
                <b>{d.name}</b>
                <span className="spec">{d.specialty} · {d.slotMinutes} min visits</span>
                <span className="bio">{d.bio}</span>
              </span>
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2 className="step"><span>2</span> Pick a day and time</h2>
        <div className="dates" role="listbox" aria-label="Day">
          {dates.map((d) => {
            const l = labelForDate(d);
            return (
              <button key={d} role="option" aria-selected={d === date} className={`date ${d === date ? 'on' : ''}`} onClick={() => setDate(d)}>
                <small>{l.weekday}</small><b>{l.day}</b><small>{l.month}</small>
              </button>
            );
          })}
        </div>
        <div className="slots">
          {slots === null && <p className="muted">Loading times...</p>}
          {slots?.length === 0 && <p className="muted">{doctor?.name} doesn't see patients on this day. Try another day.</p>}
          {slots && slots.length > 0 && free === 0 && <p className="muted">This day is fully booked. Try another day.</p>}
          {slots?.map((s) => (
            <button key={s.startsAt} disabled={!s.available} className={`slot ${chosen?.startsAt === s.startsAt ? 'on' : ''}`} onClick={() => setChosen(s)}>
              {fmtTime(s.startsAt)}
            </button>
          ))}
        </div>
      </section>

      <section className="confirm card">
        <h2 className="step"><span>3</span> Confirm</h2>
        {chosen && doctor ? (
          <>
            <p className="summary"><b>{doctor.name}</b><br />{fmtDay(chosen.startsAt)} at {fmtTime(chosen.startsAt)}</p>
            <label>Reason for visit (optional)
              <textarea id="reason" rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="For example: annual check-up" />
            </label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="btn primary" onClick={confirm} disabled={busy}>{busy ? 'Booking...' : 'Book appointment'}</button>
          </>
        ) : (
          <>
            <p className="muted">Choose a time to continue.</p>
            {error && <p className="error" role="alert">{error}</p>}
          </>
        )}
      </section>
    </div>
  );
}
