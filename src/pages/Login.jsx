import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login, error } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    login(email, password);
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: 'var(--juit-blue-pale)', margin: '0 auto',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            border: '3px solid var(--juit-blue)'
          }}>
            <span style={{ fontSize: 24, fontWeight: 700, color: 'var(--juit-blue)' }}>JUIT</span>
          </div>
          <h2 style={{ marginTop: 12 }}>Timetable Manager</h2>
          <p>Jaypee University of Information Technology</p>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ marginBottom: 16 }}>
            <span>⚠️</span><span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Email / User ID</label>
            <input
              type="email"
              className="form-control"
              placeholder="yourname@juit.ac.in"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              type="password"
              className="form-control"
              placeholder="Enter password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
            />
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: 8, padding: '12px', fontSize: 16 }}>
            Sign In
          </button>
        </form>

        <div style={{ marginTop: 20, background: 'var(--juit-gray)', borderRadius: 8, padding: 12, fontSize: 12, color: 'var(--juit-muted)' }}>
          <strong style={{ color: 'var(--juit-text)' }}>Demo credentials:</strong><br />
          Admin: admin@juit.ac.in / admin123<br />
          Faculty: priya@juit.ac.in / faculty123<br />
          Student: 211230@juit.ac.in / student123
        </div>
      </div>
    </div>
  );
}
