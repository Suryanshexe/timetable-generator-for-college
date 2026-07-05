import React, { createContext, useContext, useState, useEffect } from 'react';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

const ThemeContext = createContext();

export function useThemeMode() {
  return useContext(ThemeContext);
}

export function ThemeModeProvider({ children }) {
  const darkMode = false;

  useEffect(() => {
    localStorage.setItem('themeMode', 'light');
    document.body.style.background = '#f4f6f9';
    document.body.style.color = '#1e2d45';
    document.documentElement.setAttribute('data-theme', 'light');
  }, []);

  const toggleTheme = () => {};

  const theme = createTheme({
    palette: {
      mode: darkMode ? 'dark' : 'light',
      primary: {
        main: '#1a7aef',
        light: '#539eff',
        dark: '#0056b3',
      },
      secondary: {
        main: '#c0392b',
        light: '#e74c3c',
        dark: '#962d22',
      },
      background: {
        default: darkMode ? '#090d16' : '#f4f6f9',
        paper: darkMode ? 'rgba(20, 25, 40, 0.7)' : 'rgba(255, 255, 255, 0.75)',
      },
      text: {
        primary: darkMode ? '#e2e8f0' : '#1e2d45',
        secondary: darkMode ? '#94a3b8' : '#6b7a93',
      },
    },
    shape: {
      borderRadius: 12,
    },
    components: {
      MuiCard: {
        styleOverrides: {
          root: {
            backdropFilter: 'blur(16px)',
            background: darkMode
              ? 'linear-gradient(135deg, rgba(25, 32, 52, 0.6) 0%, rgba(15, 20, 32, 0.7) 100%)'
              : 'linear-gradient(135deg, rgba(255, 255, 255, 0.7) 0%, rgba(240, 244, 250, 0.8) 100%)',
            border: darkMode
              ? '1px solid rgba(255, 255, 255, 0.08)'
              : '1px solid rgba(26, 74, 138, 0.12)',
            boxShadow: darkMode
              ? '0 8px 32px 0 rgba(0, 0, 0, 0.3)'
              : '0 8px 32px 0 rgba(31, 38, 135, 0.06)',
            borderRadius: 16,
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 600,
            borderRadius: 10,
          },
        },
      },
    },
  });

  return (
    <ThemeContext.Provider value={{ darkMode, toggleTheme }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeContext.Provider>
  );
}
