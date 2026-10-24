import type { AssignmentPolicy } from '../types';
import { Field } from '../ui';
export default function AssignmentPolicyEditor({
  value,
  onChange,
}: {
  value?: AssignmentPolicy;
  onChange: (value: AssignmentPolicy) => void;
}) {
  const policy = value || { revision: 0, allowLate: true, rubric: [] };
  return (
    <section>
      <h3>Assignment deadline and grading rubric</h3>
      <Field label="Submission deadline">
        <input
          type="datetime-local"
          value={
            policy.dueAt
              ? new Date(
                  new Date(policy.dueAt).getTime() -
                    new Date(policy.dueAt).getTimezoneOffset() * 60000,
                )
                  .toISOString()
                  .slice(0, 16)
              : ''
          }
          onChange={(e) =>
            onChange({
              ...policy,
              dueAt: e.target.value ? new Date(e.target.value).toISOString() : undefined,
            })
          }
        />
      </Field>
      <label>
        <input
          type="checkbox"
          checked={policy.allowLate}
          onChange={(e) => onChange({ ...policy, allowLate: e.target.checked })}
        />{' '}
        Allow late submissions
      </label>
      <p>The final percentage is calculated from the points awarded for each criterion.</p>
      {policy.rubric.map((criterion, index) => (
        <div className="form-grid" key={criterion.id}>
          <Field label={`Criterion ${index + 1}`}>
            <input
              maxLength={200}
              value={criterion.label}
              onChange={(e) =>
                onChange({
                  ...policy,
                  rubric: policy.rubric.map((c) =>
                    c.id === criterion.id ? { ...c, label: e.target.value } : c,
                  ),
                })
              }
            />
          </Field>
          <Field label={`Maximum points for criterion ${index + 1}`}>
            <input
              type="number"
              min={1}
              max={100}
              value={criterion.maxPoints}
              onChange={(e) =>
                onChange({
                  ...policy,
                  rubric: policy.rubric.map((c) =>
                    c.id === criterion.id ? { ...c, maxPoints: Number(e.target.value) } : c,
                  ),
                })
              }
            />
          </Field>
          <button
            type="button"
            onClick={() =>
              onChange({ ...policy, rubric: policy.rubric.filter((c) => c.id !== criterion.id) })
            }
          >
            Remove criterion
          </button>
        </div>
      ))}
      <button
        type="button"
        className="button secondary"
        disabled={policy.rubric.length >= 20}
        onClick={() =>
          onChange({
            ...policy,
            rubric: [
              ...policy.rubric,
              { id: crypto.randomUUID(), label: 'New criterion', maxPoints: 10 },
            ],
          })
        }
      >
        Add grading criterion
      </button>
    </section>
  );
}
