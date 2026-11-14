import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { PrimaryButton } from '../components/PrimaryButton'
import { apiErrorMessage } from '../utils/apiError'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const sessionExpired = (location.state as { sessionExpired?: boolean } | null)?.sessionExpired === true
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login({ email: email.trim(), password })
      navigate('/dashboard', { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'We could not sign you in. Please try again.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <div className="auth-card">
        <span className="eyebrow">WELCOME BACK</span>
        <h2>Sign in to Syncora</h2>
        <p className="auth-subtitle">Pick up where your team left off.</p>
        {sessionExpired && !error && (
          <p className="form-message form-error" role="status">
            Your session expired or is no longer valid. Please sign in again to continue.
          </p>
        )}
        <form className="auth-form" onSubmit={handleSubmit}>
          <FormField
            id="email"
            label="Email address"
            type="email"
            autoComplete="email"
            placeholder="you@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            maxLength={254}
          />
          <FormField
            id="password"
            label="Password"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            maxLength={72}
          />
          {error && <p className="form-message form-error" role="alert">{error}</p>}
          <PrimaryButton type="submit" loading={loading}>Sign in <span aria-hidden="true">→</span></PrimaryButton>
        </form>
        <p className="auth-switch">New to Syncora? <Link to="/register">Create an account</Link></p>
      </div>
    </AuthLayout>
  )
}
