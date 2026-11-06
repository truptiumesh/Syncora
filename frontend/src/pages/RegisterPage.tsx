import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { PrimaryButton } from '../components/PrimaryButton'
import { apiErrorMessage } from '../utils/apiError'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (password !== confirmPassword) {
      setError('Your passwords do not match.')
      return
    }
    if (new TextEncoder().encode(password).length > 72) {
      setError('Choose a password with no more than 72 bytes.')
      return
    }
    setLoading(true)
    try {
      await register({ fullName: fullName.trim(), email: email.trim(), password })
      navigate('/profile', { replace: true })
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'We could not create your account. Please try again.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <div className="auth-card">
        <span className="eyebrow">A BETTER WAY TO WORK</span>
        <h2>Create your account</h2>
        <p className="auth-subtitle">Start bringing your team into sync.</p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <FormField
            id="fullName"
            label="Full name"
            type="text"
            autoComplete="name"
            placeholder="Your name"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            required
            minLength={2}
            maxLength={100}
          />
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
            autoComplete="new-password"
            placeholder="At least 12 characters"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={12}
            maxLength={72}
            hint="Use at least 12 characters."
          />
          <FormField
            id="confirmPassword"
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            placeholder="Enter your password again"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            required
            minLength={12}
            maxLength={72}
          />
          {error && <p className="form-message form-error" role="alert">{error}</p>}
          <PrimaryButton type="submit" loading={loading}>Create account <span aria-hidden="true">→</span></PrimaryButton>
        </form>
        <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
      </div>
    </AuthLayout>
  )
}
