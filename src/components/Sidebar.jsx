import React from 'react';
import { useAuth, ROLES } from '../context/AuthContext';

const NAV_ITEMS = {
  [ROLES.ADMIN]: [
    { section: 'Overview', items: [
      { label: 'Dashboard', icon: '⬛', page: 'dashboard' },
      { label: 'Analytics', icon: '📊', page: 'analytics' },
    ]},
    { section: 'Manage', items: [
      { label: 'Courses', icon: '📚', page: 'courses' },
      { label: 'Faculty', icon: '👤', page: 'faculty' },
      { label: 'Rooms', icon: '🏛', page: 'rooms' },
    ]},
    { section: 'Timetable', items: [
      { label: 'Generate', icon: '⚙️', page: 'generate' },
      { label: 'View Timetable', icon: '📅', page: 'timetable' },
      { label: 'Conflict Check', icon: '⚠️', page: 'conflicts' },
    ]},
  ],

  [ROLES.FACULTY]: [
    { section: 'Overview', items: [
      { label: 'Dashboard', icon: '⬛', page: 'dashboard' },
      { label: 'My Schedule', icon: '📅', page: 'timetable' },
    ]},
  ],
  [ROLES.STUDENT]: [
    { section: 'Overview', items: [
      { label: 'Dashboard', icon: '⬛', page: 'dashboard' },
      { label: 'My Timetable', icon: '📅', page: 'timetable' },
    ]},
  ],
};

export default function Sidebar({ currentPage, onNavigate }) {
  const { user, logout } = useAuth();
  if (!user) return null;

  const sections = NAV_ITEMS[user.role] || NAV_ITEMS[ROLES.STUDENT];
  const initials = user.name.split(' ').map(n => n[0]).slice(0, 2).join('');

  return (
    <div className="sidebar">
      {/* Logo */}
      <div className="sidebar-logo">
        <div className="sidebar-logo-icon">JUIT</div>
        <div className="sidebar-logo-text">
          <h3>Timetable</h3>
          <span>Manager v1.0</span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {sections.map(section => (
          <div key={section.section}>
            <div className="nav-section-label">{section.section}</div>
            {section.items.map(item => (
              <div
                key={item.page}
                className={`nav-item ${currentPage === item.page ? 'active' : ''}`}
                onClick={() => onNavigate(item.page)}
              >
                <span className="nav-icon">{item.icon}</span>
                {item.label}
              </div>
            ))}
          </div>
        ))}
      </nav>

      {/* User footer */}
      <div className="sidebar-footer">
        <div className="sidebar-user">
          <div className="user-avatar">{initials}</div>
          <div className="user-info">
            <p>{user.name.split(' ').slice(0,2).join(' ')}</p>
            <span>{user.role}</span>
          </div>
          <button className="logout-btn" onClick={logout} title="Logout">✕</button>
        </div>
      </div>
    </div>
  );
}