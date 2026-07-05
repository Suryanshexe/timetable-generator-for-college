import React, { createContext, useContext, useState } from 'react';

const AuthContext = createContext(null);

export const ROLES = { ADMIN: 'admin', FACULTY: 'faculty', STUDENT: 'student' };

// Demo users (replace with real API calls in production)
const DEMO_USERS = [
  { id: 1, name: 'Dr. Admin Singh', email: 'admin@juit.ac.in', password: 'admin123', role: ROLES.ADMIN, dept: 'Administration' },
  { id: 2, name: 'Dr. Priya Sharma', email: 'priya@juit.ac.in', password: 'faculty123', role: ROLES.FACULTY, dept: 'CSE' },
  { id: 3, name: 'Rahul Verma', email: '211230@juit.ac.in', password: 'student123', role: ROLES.STUDENT, dept: 'CSE', section: 'A', semester: 5 },
];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');

  const login = (email, password) => {
    const found = DEMO_USERS.find(u => u.email === email && u.password === password);
    if (found) {
      setUser(found);
      setError('');
      return true;
    }
    setError('Invalid credentials. Try admin@juit.ac.in / admin123');
    return false;
  };

  const logout = () => setUser(null);

  return (
    <AuthContext.Provider value={{ user, login, logout, error }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);