import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, body } from '../api';
import { ErrorBox, Field, Loading, Success } from '../ui';
interface Preferences {
  enabled: boolean;
  timeZone: string;
  hour: number;
}
export default function Reminders() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['learning-reminders'],
    queryFn: () => api<Preferences>('/enrollments/learning/reminders'),
  });
  const save = useMutation({
    mutationFn: (input: Preferences) =>
      api('/enrollments/learning/reminders', { method: 'PUT', body: body(input) }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['learning-reminders'] });
    },
  });
  if (query.isPending) return <Loading />;
  if (query.error) return <ErrorBox error={query.error} />;
  return (
    <section className="panel">
      <h2>Study reminders</h2>
      <p>
        Receive one daily reminder when you have upcoming tasks or courses you have not visited
        recently. Email delivery follows your account preferences.
      </p>
      <form
        key={JSON.stringify(query.data)}
        onSubmit={(e) => {
          e.preventDefault();
          const form = new FormData(e.currentTarget);
          save.mutate({
            enabled: form.has('enabled'),
            timeZone: String(form.get('zone')),
            hour: Number(form.get('hour')),
          });
        }}
      >
        <label>
          <input type="checkbox" name="enabled" defaultChecked={query.data!.enabled} /> Enable study
          reminders
        </label>
        <Field label="Time zone">
          <select
            name="zone"
            defaultValue={
              query.data!.timeZone === 'UTC'
                ? Intl.DateTimeFormat().resolvedOptions().timeZone
                : query.data!.timeZone
            }
          >
            {Array.from(
              new Set([
                'UTC',
                query.data!.timeZone,
                Intl.DateTimeFormat().resolvedOptions().timeZone,
                ...Intl.supportedValuesOf('timeZone'),
              ]),
            ).map((zone) => (
              <option key={zone}>{zone}</option>
            ))}
          </select>
        </Field>
        <Field label="Reminder time">
          <select name="hour" defaultValue={query.data!.hour}>
            {Array.from({ length: 24 }, (_, hour) => (
              <option key={hour} value={hour}>
                {String(hour).padStart(2, '0')}:00
              </option>
            ))}
          </select>
        </Field>
        <button className="button primary" disabled={save.isPending}>
          Save reminder preferences
        </button>
        {save.error && <ErrorBox error={save.error} />}{' '}
        {save.isSuccess && <Success>Reminder preferences saved.</Success>}
      </form>
    </section>
  );
}
