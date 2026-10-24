import { useMemo, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { ArrowLeft, LockKeyhole } from 'lucide-react';
import { api, body, money } from '../api';
import type { Course, Payment, PaymentIntent } from '../types';
import { Cover, ErrorBox, Field, Loading, PageTitle, Submit, Success } from '../ui';

function PaymentForm({ courseId, paymentId }: { courseId: string; paymentId: string }) {
  const stripe = useStripe();
  const navigate = useNavigate();
  const elements = useElements();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!stripe || !elements) return;
    setBusy(true);
    setError(null);
    try {
      const result = await stripe.confirmPayment({
        elements,
        confirmParams: {
          return_url: `${window.location.origin}/checkout/${courseId}?payment=${encodeURIComponent(paymentId)}`,
        },
        redirect: 'if_required',
      });
      if (result.error)
        throw new Error(result.error.message || 'The payment could not be completed.');
      navigate(`/checkout/${courseId}?payment=${encodeURIComponent(paymentId)}`, { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      <PaymentElement />
      {!!error && <ErrorBox error={error} />}
      <div className="form-actions">
        <Submit busy={busy || !stripe}>Complete secure payment</Submit>
      </div>
    </form>
  );
}
function PaymentStatus({ paymentId }: { paymentId: string }) {
  const payment = useQuery({
    queryKey: ['payment', paymentId],
    queryFn: () => api<Payment>(`/payments/${paymentId}`),
    refetchInterval: (q) =>
      ['PENDING', 'PROCESSING'].includes(q.state.data?.status || '') ? 5000 : false,
  });
  if (payment.isPending) return <Loading />;
  if (payment.error) return <ErrorBox error={payment.error} retry={() => void payment.refetch()} />;
  if (payment.data?.status === 'COMPLETED')
    return (
      <>
        <Success>Your payment has been verified. Your enrollment is being prepared.</Success>
        <p>Your course will appear in your learning area shortly.</p>
        <Link className="button primary" to="/my-learning">
          Go to my learning
        </Link>
      </>
    );
  if (['FAILED', 'CANCELLED'].includes(payment.data?.status || ''))
    return (
      <ErrorBox
        error={
          new Error('The payment could not be completed. Check its status on the payments page.')
        }
      />
    );
  return (
    <>
      <div className="notice warning">
        Waiting for payment confirmation. Enrollment will be created after your bank confirms the
        payment.
      </div>
      <Link className="button secondary" to="/payments">
        View my payments
      </Link>
    </>
  );
}
export default function Checkout() {
  const { courseId } = useParams();
  const [params] = useSearchParams();
  const paymentId = params.get('payment');
  const [intent, setIntent] = useState<PaymentIntent>();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const key = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY;
  const stripe = useMemo(() => (key ? loadStripe(key) : null), [key]);
  const course = useQuery({
    queryKey: ['course', courseId],
    queryFn: () => api<Course>(`/courses/${courseId}`),
  });
  async function begin(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const f = new FormData(e.currentTarget);
    try {
      const result = await api<PaymentIntent>('/payments/intent', {
        method: 'POST',
        body: body({ courseId, couponCode: String(f.get('couponCode') || '').trim() || undefined }),
      });
      if (!result.clientSecret)
        throw new Error('Payment details could not be created. Check the payments page.');
      setIntent(result);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (course.isPending) return <Loading />;
  if (course.error) return <ErrorBox error={course.error} />;
  const c = course.data!;
  return (
    <>
      <Link className="back-link" to={`/courses/${courseId}`}>
        <ArrowLeft size={16} /> Back to course
      </Link>
      <PageTitle
        eyebrow="ONE STEP CLOSER"
        title="Invest in your learning."
        description="Your course will appear in your learning area after payment is confirmed."
      />
      <div className="checkout-layout">
        <section className="panel">
          <h2>
            <LockKeyhole size={20} /> Secure payment
          </h2>
          {paymentId ? (
            <PaymentStatus paymentId={paymentId} />
          ) : !key ? (
            <div className="notice warning">
              Card payments are currently unavailable. Try again later or contact the training
              provider.
            </div>
          ) : intent?.clientSecret && stripe ? (
            <Elements
              stripe={stripe}
              options={{
                clientSecret: intent.clientSecret,
                locale: 'tr',
                appearance: {
                  theme: 'stripe',
                  variables: { colorPrimary: '#173e35', borderRadius: '10px' },
                },
              }}
            >
              <PaymentForm courseId={courseId!} paymentId={intent.paymentId} />
            </Elements>
          ) : (
            <form onSubmit={begin}>
              {c.price === 0 && (
                <p className="notice warning">
                  This course is listed as free. Enrollment depends on the provider's payment and
                  registration checks.
                </p>
              )}
              <Field label="Coupon code (optional)">
                <input name="couponCode" placeholder="Enter a coupon code" />
              </Field>
              {!!error && <ErrorBox error={error} />}
              <Submit busy={busy}>Continue to payment</Submit>
            </form>
          )}
        </section>
        <aside className="panel order-summary">
          <Cover course={c} />
          <h3>{c.title}</h3>
          <p>{c.instructorName}</p>
          <div className="order-total">
            <span>Total</span>
            <strong>
              {intent ? money(intent.amount, intent.currency) : money(c.discountPrice ?? c.price)}
            </strong>
          </div>
          <small>Your card details are processed securely by Stripe.</small>
        </aside>
      </div>
    </>
  );
}
