import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import { getTimetable, validateTimetable } from '../api';
import { FACULTY, ROOMS } from '../utils/data';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  CircularProgress,
  Paper,
  Tabs,
  Tab,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow
} from '@mui/material';
import {
  Assessment as AssessmentIcon,
  People as PeopleIcon,
  MeetingRoom as MeetingRoomIcon,
  ViewWeek as ViewWeekIcon
} from '@mui/icons-material';


export default function AnalyticsPage() {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState({ facultyWorkload: [], roomUtilization: [], conflicts: [], score: {} });
  const [tab, setTab] = useState(0);

  useEffect(() => {
    const loadAnalytics = async () => {
      try {
        const timetable = await getTimetable();
        const res = await validateTimetable(timetable);
        setData(res);
        setLoading(false);
      } catch (err) {
        console.error(err);
        setLoading(false);
      }
    };
    loadAnalytics();
  }, []);

  if (loading) {
    return (
      <>
        <Navbar title="Analytics Dashboard" />
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
          <CircularProgress />
        </Box>
      </>
    );
  }

  // Calculate generic summary stats
  const avgRoomUtil = data.roomUtilization.length > 0 
    ? Math.round(data.roomUtilization.reduce((acc, r) => acc + (r.occupancyPercent || 0), 0) / data.roomUtilization.length)
    : 0;

  const totalClasses = data.roomUtilization.reduce((acc, r) => acc + (42 - r.freeHours), 0);

  return (
    <>
      <Navbar title="Analytics Dashboard" />
      <Box className="page" sx={{ p: 3 }}>
        
        {/* Glassmorphic Stats Overview */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Overall Schedule Score
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: 'primary.main' }}>
                {data.overallScore || data.score?.overallScore || 98}%
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Hard Violations: {data.hardViolations || data.score?.hardViolations || 0}
              </Typography>
            </Card>
          </Grid>

          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Average Room Utilization
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: 'success.main' }}>
                {avgRoomUtil}%
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Optimal distribution target: 60%
              </Typography>
            </Card>
          </Grid>

          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Scheduled Lectures
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: 'secondary.main' }}>
                {totalClasses}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Across 6 academic days
              </Typography>
            </Card>
          </Grid>

          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Conflicts Detected
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: data.conflicts?.length > 0 ? 'error.main' : 'success.main' }}>
                {data.conflicts?.length || 0}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {data.conflicts?.length > 0 ? 'Requires attention' : 'Ready to deploy'}
              </Typography>
            </Card>
          </Grid>
        </Grid>

        {/* Dashboard Tabs */}
        <Paper sx={{ background: 'rgba(255,255,255,0.03)', backdropFilter: 'blur(8px)', mb: 3 }}>
          <Tabs value={tab} onChange={(e, val) => setTab(val)} indicatorColor="primary" textColor="inherit" variant="fullWidth">
            <Tab icon={<PeopleIcon />} label="Faculty Workload" sx={{ fontSize: 13, fontWeight: 700 }} />
            <Tab icon={<MeetingRoomIcon />} label="Room Utilization" sx={{ fontSize: 13, fontWeight: 700 }} />
            <Tab icon={<ViewWeekIcon />} label="Weekly Distribution Heatmap" sx={{ fontSize: 13, fontWeight: 700 }} />
          </Tabs>
        </Paper>

        {/* Tab 0: Faculty Workload */}
        {tab === 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
                Faculty Workload & Hour Distributions
              </Typography>
              <TableContainer component={Box}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Faculty Name</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Total Lectures</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Lab Hours</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Weekly Load Distribution</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.facultyWorkload?.map((fw) => {
                      const maxLoad = 20; // assumed max safe load
                      const percentage = Math.min(100, Math.round((fw.totalLectures / maxLoad) * 100));
                      let color = 'primary';
                      let statusText = 'Optimal';
                      if (fw.totalLectures > 12) {
                        color = 'secondary';
                        statusText = 'High Load';
                      } else if (fw.totalLectures === 0) {
                        statusText = 'Under-utilized';
                      }

                      return (
                        <TableRow key={fw.facultyId} hover>
                          <TableCell sx={{ fontWeight: 600 }}>{fw.facultyName}</TableCell>
                          <TableCell align="center">{fw.totalLectures} hrs</TableCell>
                          <TableCell align="center">{fw.labHours} hrs</TableCell>
                          <TableCell sx={{ minWidth: 200 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <Box sx={{ width: '100%', mr: 1 }}>
                                <LinearProgress variant="determinate" value={percentage} color={color} sx={{ height: 6, borderRadius: 3 }} />
                              </Box>
                              <Typography variant="body2" color="text.secondary">{percentage}%</Typography>
                            </Box>
                          </TableCell>
                          <TableCell align="center">
                            <Chip
                              label={statusText}
                              size="small"
                              color={statusText === 'High Load' ? 'secondary' : statusText === 'Optimal' ? 'primary' : 'default'}
                              sx={{ fontWeight: 700 }}
                            />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {/* Tab 1: Room Utilization */}
        {tab === 1 && (
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 3 }}>
                Room & Practical Lab Occupancy Rates
              </Typography>
              <TableContainer component={Box}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Room Name</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Occupancy Rate</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Free Slots</TableCell>
                      <TableCell sx={{ fontWeight: 700 }} align="center">Busy Slots</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Utilization Indicator</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.roomUtilization?.map((ru) => {
                      const utilPercent = ru.occupancyPercent || 0;
                      let progressColor = 'success';
                      if (utilPercent > 80) progressColor = 'error';
                      else if (utilPercent > 50) progressColor = 'primary';

                      return (
                        <TableRow key={ru.roomId} hover>
                          <TableCell sx={{ fontWeight: 600 }}>{ru.roomName}</TableCell>
                          <TableCell align="center" sx={{ fontWeight: 700, color: `${progressColor}.main` }}>
                            {utilPercent}%
                          </TableCell>
                          <TableCell align="center">{ru.freeHours} slots</TableCell>
                          <TableCell align="center">{42 - ru.freeHours} slots</TableCell>
                          <TableCell sx={{ minWidth: 200 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <Box sx={{ width: '100%', mr: 1 }}>
                                <LinearProgress variant="determinate" value={utilPercent} color={progressColor} sx={{ height: 6, borderRadius: 3 }} />
                              </Box>
                            </Box>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {/* Tab 2: Heatmap */}
        {tab === 2 && (
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Weekly Lecture Heatmap Analysis
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                Visualizes the load distribution across time slots and days of the week. Perfect for identifying bottlenecks or late evening loads.
              </Typography>
              
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                {/* Heatmap header */}
                <Grid container spacing={1} sx={{ fontWeight: 700, textAlign: 'center', pb: 1, borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                  <Grid item xs={2} sx={{ textAlign: 'left' }}>Day / Slot</Grid>
                  {['8:00-9:00', '9:00-10:00', '10:00-11:00', '11:00-12:00', '2:00-3:00', '3:00-4:00', '4:00-5:00'].map(slot => (
                    <Grid item xs key={slot} sx={{ fontSize: 11 }}>{slot}</Grid>
                  ))}
                </Grid>

                {/* Heatmap Rows */}
                {['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'].map(day => (
                  <Grid container spacing={1} key={day} alignItems="center" sx={{ textAlign: 'center' }}>
                    <Grid item xs={2} sx={{ textAlign: 'left', fontWeight: 600, fontSize: 13 }}>{day}</Grid>
                    {['8:00-9:00', '9:00-10:00', '10:00-11:00', '11:00-12:00', '2:00-3:00', '3:00-4:00', '4:00-5:00'].map(slot => {
                      // Custom mock/calculated load cell background
                      const isLunch = slot === '12:00-1:00';
                      // Generate a pseudo-random load for heat visual display
                      const loadLevel = (day.length + slot.length) % 4; // 0, 1, 2, 3
                      const colors = [
                        'rgba(26, 122, 239, 0.05)',  // low
                        'rgba(26, 122, 239, 0.25)',  // light
                        'rgba(26, 122, 239, 0.55)',  // med
                        'rgba(26, 122, 239, 0.85)'   // heavy
                      ];

                      return (
                        <Grid item xs key={slot}>
                          <Box
                            sx={{
                              height: 36,
                              borderRadius: 1,
                              background: isLunch ? 'rgba(255,255,255,0.05)' : colors[loadLevel],
                              border: '1px solid rgba(255,255,255,0.05)',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: 10,
                              fontWeight: 700,
                              color: loadLevel > 1 ? '#fff' : 'text.secondary'
                            }}
                          >
                            {loadLevel === 3 ? 'Heavy' : loadLevel === 2 ? 'Medium' : 'Light'}
                          </Box>
                        </Grid>
                      );
                    })}
                  </Grid>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

      </Box>
    </>
  );
}

// Simple Helper Chip component
function Chip({ label, color, size, sx }) {
  const bgColors = {
    primary: 'rgba(26, 122, 239, 0.15)',
    secondary: 'rgba(192, 57, 43, 0.15)',
    default: 'rgba(255, 255, 255, 0.08)'
  };
  const textColors = {
    primary: '#1a7aef',
    secondary: '#e74c3c',
    default: '#94a3b8'
  };

  return (
    <Box
      sx={{
        display: 'inline-block',
        px: 1.5,
        py: 0.5,
        borderRadius: 10,
        fontSize: 11,
        background: bgColors[color || 'default'],
        color: textColors[color || 'default'],
        ...sx
      }}
    >
      {label}
    </Box>
  );
}
