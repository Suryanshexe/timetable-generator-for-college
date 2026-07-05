import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useThemeMode } from '../context/ThemeContext';
import { Box, Typography, IconButton, Chip } from '@mui/material';
import {
  Brightness4 as Brightness4Icon,
  Brightness7 as Brightness7Icon
} from '@mui/icons-material';


export default function Navbar({ title }) {
  const { user } = useAuth();
  const { darkMode, toggleTheme } = useThemeMode();

  return (
    <Box
      className="topbar"
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        px: 3,
        height: 64,
        background: darkMode ? 'rgba(9, 13, 22, 0.7)' : 'rgba(255, 255, 255, 0.75)',
        backdropFilter: 'blur(12px)',
        borderBottom: darkMode ? '1px solid rgba(255, 255, 255, 0.08)' : '1px solid rgba(26, 74, 138, 0.12)',
        position: 'sticky',
        top: 0,
        zIndex: 50
      }}
    >
      <Typography variant="h6" sx={{ fontWeight: 800, color: 'text.primary' }}>
        {title}
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        {user && (
          <>
            <Chip
              label={`${user.dept} · ${user.role?.toUpperCase()}`}
              color="primary"
              variant="outlined"
              size="small"
              sx={{ fontWeight: 700 }}
            />
            <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.secondary', display: { xs: 'none', sm: 'block' } }}>
              {user.name}
            </Typography>
          </>
        )}
      </Box>
    </Box>
  );
}