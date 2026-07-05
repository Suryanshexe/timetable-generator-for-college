import React from 'react';
import { useAuth } from '../context/AuthContext';
import Login from '../pages/Login';

export default function ProtectedRoute({ children }) {
  const { user } = useAuth();
  return user ? children : <Login />;
}