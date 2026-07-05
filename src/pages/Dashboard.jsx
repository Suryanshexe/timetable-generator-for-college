import React from 'react';
import Navbar from '../components/Navbar';
import { useAuth, ROLES } from '../context/AuthContext';
import { COURSES, FACULTY, ROOMS, SAMPLE_TIMETABLE } from '../utils/data';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  Paper,
  Divider,
  Chip
} from '@mui/material';
import {
  AutoAwesome as AutoAwesomeIcon,
  CalendarMonth as CalendarMonthIcon,
  LibraryBooks as LibraryBooksIcon,
  PersonPin as PersonPinIcon,
  Equalizer as EqualizerIcon,
  Chat as ChatIcon
} from '@mui/icons-material';



export default function Dashboard({ onNavigate }) {
  const { user } = useAuth();

  const recentActivity = [
    { action: 'Timetable generated', detail: 'CSE Sem 5 Section A', time: '2 hours ago', type: 'success' },
    { action: 'Conflict detected', detail: 'Dr. Gupta double-booked on Tuesday 10:00', time: '3 hours ago', type: 'warning' },
    { action: 'Room added', detail: 'CS Lab 3 added to Room Registry', time: '1 day ago', type: 'blue' },
    { action: 'Faculty availability updated', detail: 'Dr. Sharma marked unavailable Saturday', time: '2 days ago', type: 'gray' },
  ];

  const typeColor = { success: 'success.main', warning: 'warning.main', blue: 'primary.main', gray: 'text.secondary' };

  return (
    <>
      <Navbar title="Dashboard Overview" />
      <Box className="page" sx={{ p: 3 }}>
        
        {/* Welcome Glassmorphic Banner */}
        <Paper
          sx={{
            p: 4,
            mb: 4,
            background: 'linear-gradient(135deg, #102a54 0%, #1a7aef 100%)',
            color: '#fff',
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 8px 32px 0 rgba(26, 122, 239, 0.2)',
            border: '1px solid rgba(255,255,255,0.08)'
          }}
        >
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, mb: 1, color: '#fff' }}>
              Welcome back, {user?.name?.split(' ')[0]} 👋
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.85 }}>
              {user?.role === ROLES.ADMIN
                ? 'Automate course scheduling and analyze faculty workloads with AI.'
                : user?.role === ROLES.FACULTY
                ? 'Check your active lecture timetable and student section assignments.'
                : `${user?.dept} · Semester ${user?.semester} · Section ${user?.section}`}
            </Typography>
          </Box>
          <Paper
            sx={{
              p: 2,
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(8px)',
              border: '1px solid rgba(255,255,255,0.15)',
              textAlign: 'center',
              color: '#fff'
            }}
          >
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Sem 5</Typography>
            <Typography variant="caption" sx={{ display: 'block', opacity: 0.8 }}>2026–27</Typography>
          </Paper>
        </Paper>

        {/* Stats Grid */}
        {user?.role === ROLES.ADMIN && (
          <Grid container spacing={3} sx={{ mb: 4 }}>
            <Grid item xs={12} sm={3}>
              <Card>
                <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(26, 122, 239, 0.12)', color: 'primary.main' }}>
                    <LibraryBooksIcon />
                  </Box>
                  <Box>
                    <Typography variant="h4" sx={{ fontWeight: 800 }}>{COURSES.length}</Typography>
                    <Typography variant="caption" color="text.secondary">Total Courses</Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={3}>
              <Card>
                <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(192, 57, 43, 0.12)', color: 'secondary.main' }}>
                    <PersonPinIcon />
                  </Box>
                  <Box>
                    <Typography variant="h4" sx={{ fontWeight: 800 }}>{FACULTY.length}</Typography>
                    <Typography variant="caption" color="text.secondary">Faculty Members</Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={3}>
              <Card>
                <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(76, 175, 80, 0.12)', color: '#4caf50' }}>
                    <CalendarMonthIcon />
                  </Box>
                  <Box>
                    <Typography variant="h4" sx={{ fontWeight: 800 }}>{ROOMS.length}</Typography>
                    <Typography variant="caption" color="text.secondary">Rooms / Labs</Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={3}>
              <Card>
                <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(255, 179, 0, 0.12)', color: '#ffb300' }}>
                    <AutoAwesomeIcon />
                  </Box>
                  <Box>
                    <Typography variant="h4" sx={{ fontWeight: 800 }}>{SAMPLE_TIMETABLE.entries.length}</Typography>
                    <Typography variant="caption" color="text.secondary">Scheduled Slots</Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}

        <Grid container spacing={3}>
          {/* Quick Actions Panel */}
          <Grid item xs={12} md={6}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
                  AI Quick Operations
                </Typography>
                
                {user?.role === ROLES.ADMIN ? (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <Button
                      variant="contained"
                      size="large"
                      startIcon={<AutoAwesomeIcon />}
                      onClick={() => onNavigate('generate')}
                      sx={{ py: 1.5, justifyContent: 'flex-start', px: 3 }}
                    >
                      Generate New AI Timetable
                    </Button>
                    <Button
                      variant="outlined"
                      size="large"
                      startIcon={<ChatIcon />}
                      onClick={() => onNavigate('chat')}
                      sx={{ py: 1.5, justifyContent: 'flex-start', px: 3 }}
                    >
                      Natural Language AI Scheduler
                    </Button>
                    <Button
                      variant="outlined"
                      size="large"
                      startIcon={<EqualizerIcon />}
                      onClick={() => onNavigate('analytics')}
                      sx={{ py: 1.5, justifyContent: 'flex-start', px: 3 }}
                    >
                      View Workload Analytics
                    </Button>
                    <Button
                      variant="text"
                      size="large"
                      startIcon={<CalendarMonthIcon />}
                      onClick={() => onNavigate('timetable')}
                      sx={{ py: 1.2, justifyContent: 'flex-start', px: 3 }}
                    >
                      Open Grid Timetable View
                    </Button>
                  </Box>
                ) : (
                  <Button
                    variant="contained"
                    size="large"
                    fullWidth
                    startIcon={<CalendarMonthIcon />}
                    onClick={() => onNavigate('timetable')}
                    sx={{ py: 1.5 }}
                  >
                    View My Timetable
                  </Button>
                )}
              </CardContent>
            </Card>
          </Grid>

          {/* Activity Feed */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                  System Notification Logs
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {recentActivity.map((a, i) => (
                    <Box key={i}>
                      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: typeColor[a.type], mt: 0.8 }} />
                        <Box sx={{ flex: 1 }}>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700, fontSize: 13 }}>
                            {a.action}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            {a.detail}
                          </Typography>
                          <Typography variant="caption" sx={{ fontSize: 10, opacity: 0.6 }}>
                            {a.time}
                          </Typography>
                        </Box>
                      </Box>
                      {i < recentActivity.length - 1 && <Divider sx={{ mt: 1.5 }} />}
                    </Box>
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

      </Box>
    </>
  );
}
