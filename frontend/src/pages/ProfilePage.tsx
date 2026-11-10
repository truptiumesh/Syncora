import { useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { FormField } from '../components/FormField'
import { PrimaryButton } from '../components/PrimaryButton'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { authApi } from '../services/authService'
import type { UserResponse } from '../types/auth'
import { apiErrorMessage } from '../utils/apiError'

export function ProfilePage() {
  const { user, setUser, logout } = useAuth()
  const navigate = useNavigate()
  const [pageLoading, setPageLoading] = useState(true)
  const [editing, setEditing] = useState(false)
  const [fullName, setFullName] = useState('')
  const [bio, setBio] = useState('')
  const [profileImageUrl, setProfileImageUrl] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [profileError, setProfileError] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [notice, setNotice] = useState('')
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)

  useEffect(() => {
    let active = true
    authApi.getCurrentUser()
      .then((profile) => {
        if (active) setUser(profile)
      })
      .catch((error: unknown) => {
        if (!active) return
        setProfileError(apiErrorMessage(error, 'Your profile could not be loaded.'))
        if (axios.isAxiosError(error) && error.response?.status === 401) {
          logout()
          navigate('/login', { replace: true })
        }
      })
      .finally(() => {
        if (active) setPageLoading(false)
      })
    return () => {
      active = false
    }
  }, [logout, navigate, setUser])

  useEffect(() => {
    if (!user) return
    setFullName(user.fullName)
    setBio(user.bio ?? '')
    setProfileImageUrl(user.profileImageUrl ?? '')
  }, [user])

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProfileError('')
    setNotice('')
    setSavingProfile(true)
    try {
      const updated = await authApi.updateProfile({ fullName: fullName.trim(), bio, profileImageUrl })
      setUser(updated)
      setEditing(false)
      setNotice('Your profile has been updated.')
    } catch (error) {
      setProfileError(apiErrorMessage(error, 'Your profile could not be updated.'))
    } finally {
      setSavingProfile(false)
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPasswordError('')
    setNotice('')
    if (newPassword !== confirmPassword) {
      setPasswordError('Your new passwords do not match.')
      return
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
      setPasswordError('Choose a password with no more than 72 bytes.')
      return
    }
    setSavingPassword(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setNotice('Your password has been changed.')
    } catch (error) {
      setPasswordError(apiErrorMessage(error, 'Your password could not be changed.'))
    } finally {
      setSavingPassword(false)
    }
  }

  if (pageLoading) {
    return <main className="workspace-page"><WorkspaceHeader /><div className="workspace-loading" role="status"><span className="spinner" />Loading your profile...</div></main>
  }

  return (
    <main className="profile-page">
      <WorkspaceHeader />
      <section className="profile-content">
        <div className="profile-heading">
          <span className="eyebrow">YOUR ACCOUNT</span>
          <h1>Profile & settings</h1>
          <p>Manage your personal information and account security.</p>
        </div>
        {notice && <p className="form-message form-success" role="status">{notice}</p>}
        <div className="profile-grid">
          <section className="settings-card profile-card">
            <div className="card-heading">
              <div>
                <h2>Personal information</h2>
                <p>Your profile is how your teammates know you.</p>
              </div>
              {!editing && <button className="button-outline" type="button" onClick={() => setEditing(true)}>Edit profile</button>}
            </div>
            <div className="identity-row">
              <Avatar user={user} />
              <div>
                <h3>{user?.fullName}</h3>
                <p>{user?.email}</p>
              </div>
              <span className="role-pill">{user?.role}</span>
            </div>
            {profileError && <p className="form-message form-error" role="alert">{profileError}</p>}
            {editing ? (
              <form className="settings-form" onSubmit={saveProfile}>
                <FormField id="profileName" label="Full name" value={fullName} onChange={(event) => setFullName(event.target.value)} required minLength={2} maxLength={100} />
                <div className="field">
                  <label htmlFor="bio">Bio</label>
                  <textarea id="bio" rows={3} maxLength={500} placeholder="A little about you" value={bio} onChange={(event) => setBio(event.target.value)} />
                </div>
                <FormField id="profileImageUrl" label="Profile image URL" type="url" placeholder="https://example.com/photo.jpg" value={profileImageUrl} onChange={(event) => setProfileImageUrl(event.target.value)} maxLength={2048} />
                <div className="form-actions">
                  <button className="button-quiet" type="button" onClick={() => setEditing(false)}>Cancel</button>
                  <PrimaryButton type="submit" loading={savingProfile}>Save changes</PrimaryButton>
                </div>
              </form>
            ) : (
              <div className="profile-details">
                <div><span>About</span><p>{user?.bio || 'Add a short bio to tell your team about yourself.'}</p></div>
                <div><span>Email address</span><p>{user?.email}</p></div>
                <div><span>Member since</span><p>{user?.createdAt ? new Date(user.createdAt).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }) : '—'}</p></div>
              </div>
            )}
          </section>
          <section className="settings-card password-card">
            <div className="card-heading">
              <div>
                <span className="card-icon" aria-hidden="true">⌑</span>
                <h2>Password & security</h2>
                <p>Keep your account protected with a strong password.</p>
              </div>
            </div>
            <form className="settings-form" onSubmit={changePassword}>
              <FormField id="currentPassword" label="Current password" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required maxLength={72} />
              <FormField id="newPassword" label="New password" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required minLength={12} maxLength={72} hint="Use at least 12 characters." />
              <FormField id="confirmNewPassword" label="Confirm new password" type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required minLength={12} maxLength={72} />
              {passwordError && <p className="form-message form-error" role="alert">{passwordError}</p>}
              <PrimaryButton type="submit" loading={savingPassword}>Update password</PrimaryButton>
            </form>
          </section>
        </div>
        <p className="profile-footnote"><span className="status-dot" />Your account is active and secure.</p>
      </section>
    </main>
  )
}

function Avatar({ user }: { user: UserResponse | null }) {
  if (user?.profileImageUrl) {
    return <img className="avatar" src={user.profileImageUrl} alt="" />
  }
  const initials = user?.fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() ?? '?'
  return <span className="avatar avatar-fallback" aria-hidden="true">{initials}</span>
}
