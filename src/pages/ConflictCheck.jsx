import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { getTimetable, validateTimetable, getSuggestions, saveTimetable } from '../api';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Chip,
  Paper,
  Divider
} from '@mui/material';
import {
  WarningAmber as WarningAmberIcon,
  CheckCircle as CheckCircleOutlineIcon,
  Lightbulb as LightbulbIcon
} from '@mui/icons-material';



export default function ConflictsPage() {
  const [loading, setLoading] = useState(true);
  const [conflicts, setConflicts] = useState([]);
  const [timetable, setTimetable] = useState([]);
  
  // Resolution Dialog State
  const [resolutionTarget, setResolutionTarget] = useState(null); // conflict object
  const [suggestions, setSuggestions] = useState([]);
  const [suggestLoading, setSuggestLoading] = useState(false);
  const [resolvedIds, setResolvedIds] = useState([]);

  useEffect(() => {
    loadConflicts();
  }, []);

  const loadConflicts = async () => {
    try {
      const tt = await getTimetable();
      setTimetable(tt);
      const res = await validateTimetable(tt);
      setConflicts(res.conflicts || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenResolve = async (conflict) => {
    setResolutionTarget(conflict);
    setSuggestions([]);
    setSuggestLoading(true);

    try {
      // Find the specific scheduled entry that corresponds to the clashed course in this conflict
      const entry = timetable.find(e => 
        e.day === conflict.day && 
        e.slot === conflict.slot && 
        conflict.affectedCourses.includes(e.course)
      );

      if (!entry) {
        // Fallback search by course ID
        const fallbackCourse = conflict.affectedCourses[0];
        const fallbackEntry = timetable.find(e => e.course === fallbackCourse);
        if (fallbackEntry) {
          const res = await getSuggestions({ entry: fallbackEntry, currentTimetable: timetable });
          setSuggestions(res.suggestions || []);
        }
      } else {
        const res = await getSuggestions({ entry, currentTimetable: timetable });
        setSuggestions(res.suggestions || []);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setSuggestLoading(false);
    }
  };

  const applyResolution = async (sug) => {
    if (!resolutionTarget) return;
    const courseId = resolutionTarget.affectedCourses[0];
    
    // Find and update the slot in timetable entries
    const updatedTimetable = timetable.map(item => {
      if (item.course === courseId && item.day === resolutionTarget.day && item.slot === resolutionTarget.slot) {
        return { ...item, day: sug.newDay, slot: sug.newSlot, room: sug.newRoom };
      }
      return item;
    });

    try {
      setLoading(true);
      await saveTimetable(updatedTimetable);
      setResolvedIds(prev => [...prev, resolutionTarget.id]);
      setResolutionTarget(null);
      await loadConflicts(); // re-verify conflicts
    } catch (err) {
      alert(`Failed to save resolution: ${err.message}`);
      setLoading(false);
    }
  };

  const openConflicts = conflicts.filter(c => !resolvedIds.includes(c.id));
  const resolvedConflicts = conflicts.filter(c => resolvedIds.includes(c.id));

  const severityColor = { high: 'error', medium: 'warning', low: 'default' };

  if (loading) {
    return (
      <>
        <Navbar title="Conflict Detection" />
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
          <CircularProgress />
        </Box>
      </>
    );
  }

  return (
    <>
      <Navbar title="Conflict Detection & Resolution" />
      <Box className="page" sx={{ p: 3, maxWidth: 900, mx: 'auto' }}>
        
        {/* Summary Card */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} sm={4}>
            <Card sx={{ p: 1, textAlign: 'center', borderColor: openConflicts.length > 0 ? 'error.main' : 'success.main' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Open Conflicts
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: openConflicts.length > 0 ? 'error.main' : 'success.main' }}>
                {openConflicts.length}
              </Typography>
            </Card>
          </Grid>
          
          <Grid item xs={12} sm={4}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Resolved Clashes
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: 'success.main' }}>
                {resolvedConflicts.length}
              </Typography>
            </Card>
          </Grid>

          <Grid item xs={12} sm={4}>
            <Card sx={{ p: 1, textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                Total Checked
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 800, my: 1 }}>
                {conflicts.length}
              </Typography>
            </Card>
          </Grid>
        </Grid>

        {openConflicts.length > 0 ? (
          <Paper
            sx={{
              p: 2,
              mb: 3,
              bgcolor: 'rgba(239, 83, 80, 0.08)',
              border: '1px solid rgba(239, 83, 80, 0.2)',
              display: 'flex',
              alignItems: 'center',
              gap: 2
            }}
          >
            <WarningAmberIcon color="error" />
            <Typography variant="body2" sx={{ color: 'error.main', fontWeight: 700 }}>
              {openConflicts.length} conflict(s) require attention before the timetable can be published.
            </Typography>
          </Paper>
        ) : (
          <Paper
            sx={{
              p: 2,
              mb: 3,
              bgcolor: 'rgba(76, 175, 80, 0.08)',
              border: '1px solid rgba(76, 175, 80, 0.2)',
              display: 'flex',
              alignItems: 'center',
              gap: 2
            }}
          >
            <CheckCircleOutlineIcon color="success" />
            <Typography variant="body2" sx={{ color: 'success.main', fontWeight: 700 }}>
              All constraints satisfied! The timetable is conflict-free and ready for publishing.
            </Typography>
          </Paper>
        )}

        {/* Open Conflicts List */}
        {openConflicts.length > 0 && (
          <Card sx={{ mb: 4 }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Active Issues
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {openConflicts.map(c => (
                  <Paper
                    key={c.id}
                    variant="outlined"
                    sx={{
                      p: 2,
                      borderColor: 'rgba(255,255,255,0.08)',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: 2
                    }}
                  >
                    <Box sx={{ flex: 1 }}>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 1 }}>
                        <Chip label={c.severity.toUpperCase()} color={severityColor[c.severity]} size="small" sx={{ fontWeight: 700 }} />
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {c.type}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                        {c.desc}
                      </Typography>
                      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                        <Typography variant="caption" color="text.secondary">
                          📅 {c.day} · {c.slot}
                        </Typography>
                        {c.affectedCourses.map(course => (
                          <Chip key={course} label={course} size="small" variant="outlined" sx={{ height: 20, fontSize: 10 }} />
                        ))}
                      </Box>
                    </Box>

                    <Button variant="contained" color="primary" onClick={() => handleOpenResolve(c)}>
                      Resolve Conflict
                    </Button>
                  </Paper>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

        {/* Resolved Conflicts List */}
        {resolvedConflicts.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Resolved Conflicts ({resolvedConflicts.length})
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                {resolvedConflicts.map(c => (
                  <Paper
                    key={c.id}
                    variant="outlined"
                    sx={{
                      p: 2,
                      bgcolor: 'rgba(255,255,255,0.01)',
                      borderColor: 'rgba(255,255,255,0.04)',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center'
                    }}
                  >
                    <Box>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 0.5 }}>
                        <Chip label="RESOLVED" color="success" size="small" sx={{ fontWeight: 700 }} />
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, textDecoration: 'line-through', opacity: 0.7 }}>
                          {c.type}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ opacity: 0.6 }}>
                        {c.desc}
                      </Typography>
                    </Box>
                    <CheckCircleOutlineIcon color="success" />
                  </Paper>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

      </Box>

      {/* Resolution Assistant Dialog */}
      <Dialog open={!!resolutionTarget} onClose={() => setResolutionTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
          <LightbulbIcon color="primary" /> AI Conflict Resolution Panel
        </DialogTitle>
        <DialogContent>
          {resolutionTarget && (
            <Box sx={{ my: 1 }}>
              <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 700 }}>
                Issue: {resolutionTarget.type}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                {resolutionTarget.desc}
              </Typography>
              
              <Divider sx={{ my: 2 }} />
              
              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700 }}>
                Recommended Resolutions:
              </Typography>

              {suggestLoading ? (
                <Box sx={{ textAlign: 'center', py: 3 }}><CircularProgress /></Box>
              ) : suggestions.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No automated resolutions found. You may need to manually re-schedule classes.
                </Typography>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  {suggestions.map((sug, idx) => (
                    <Paper
                      key={idx}
                      variant="outlined"
                      sx={{
                        p: 2,
                        cursor: 'pointer',
                        borderColor: 'rgba(255,255,255,0.08)',
                        '&:hover': { background: 'rgba(26, 122, 239, 0.08)', borderColor: 'primary.main' }
                      }}
                      onClick={() => applyResolution(sug)}
                    >
                      <Typography variant="subtitle2" color="primary.main" sx={{ fontWeight: 700, mb: 0.5 }}>
                        {sug.action || `Move to ${sug.newDay} ${sug.newSlot}`}
                      </Typography>
                      <Typography variant="body2" sx={{ fontSize: 13 }}>
                        <strong>Reason:</strong> {sug.reason}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        New Room: {sug.newRoom}
                      </Typography>
                    </Paper>
                  ))}
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResolutionTarget(null)}>Cancel</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}