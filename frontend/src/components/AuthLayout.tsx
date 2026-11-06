import type { ReactNode } from 'react'

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth-page">
      <section className="auth-story" aria-label="About Syncora">
        <a className="brand brand-light" href="/login" aria-label="Syncora home">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
          <span>syncora</span>
        </a>
        <div className="story-copy">
          <span className="eyebrow">WORK BETTER, TOGETHER</span>
          <h1>Make space for your best work.</h1>
          <p>One calm place to bring your people, ideas, and projects into sync.</p>
        </div>
        <div className="story-note">
          <span className="note-spark" aria-hidden="true">✳</span>
          <p>Good work happens when everyone is moving in the same direction.</p>
        </div>
        <div className="story-orbit orbit-one" />
        <div className="story-orbit orbit-two" />
        <span className="story-dot dot-one" />
        <span className="story-dot dot-two" />
      </section>
      <section className="auth-main">
        <a className="brand brand-mobile" href="/login" aria-label="Syncora home">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
          <span>syncora</span>
        </a>
        {children}
        <p className="auth-legal">By continuing, you agree to our <span>Terms</span> and <span>Privacy Policy</span>.</p>
      </section>
    </main>
  )
}
