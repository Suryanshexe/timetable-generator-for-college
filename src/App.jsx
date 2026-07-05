import React, { useState } from 'react';
import { AuthProvider } from './context/AuthContext';
import { ThemeModeProvider } from './context/ThemeContext';
import ProtectedRoute from './components/ProtectedRoute';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import TimetableView from './pages/TimetableView';
import CoursesPage from './pages/Courses';
import FacultyPage from './pages/Faculty';
import RoomsPage from './pages/Rooms';
import GeneratePage from './pages/Generate';
import ConflictsPage from './pages/ConflictCheck';
import AnalyticsPage from './pages/Analytics';
import AIChatPage from './pages/AIChat';

function AppShell() {
  const [page, setPage] = useState('dashboard');

  const pages = {
    dashboard: <Dashboard onNavigate={setPage} />,
    timetable: <TimetableView />,
    courses: <CoursesPage />,
    faculty: <FacultyPage />,
    rooms: <RoomsPage />,
    generate: <GeneratePage onNavigate={setPage} />,
    conflicts: <ConflictsPage />,
    analytics: <AnalyticsPage />,
    chat: <AIChatPage />,
  };

  return (
    <div className="app-layout">
      <Sidebar currentPage={page} onNavigate={setPage} />
      <main className="main-content">
        {pages[page] || pages.dashboard}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <ThemeModeProvider>
      <AuthProvider>
        <ProtectedRoute>
          <AppShell />
        </ProtectedRoute>
      </AuthProvider>
    </ThemeModeProvider>
  );
}