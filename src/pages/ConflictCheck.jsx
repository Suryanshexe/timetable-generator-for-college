import React, { useEffect, useState, useCallback } from 'react';
import Navbar from '../components/Navbar';
import { getTimetable, validateTimetable, getSuggestions, saveTimetable, resolveConflicts, getAnalytics } from '../api';
import {
  Box, Card, CardContent, Typography, Grid, Button, CircularProgress,
  Dialog, DialogTitle, DialogContent, DialogActions, Chip, Paper,
  Divider, LinearProgress, Collapse, Alert, Tooltip, IconButton
} from '@mui/material';
import {
  WarningAmber as WarningAmberIcon,
  CheckCircle as CheckCircleOutlineIcon,
  Lightbulb as LightbulbIcon,
  AutoFixHigh as AutoFixHighIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  TrendingUp as TrendingUpIcon,
  Timeline as TimelineIcon,
  Shield as ShieldIcon,
  PeopleAlt as PeopleAltIcon
} from '@mui/icons-material';

// ── Score Gauge ──────────────────────────────────────────────────────────────
function ScoreGauge({ score, label, size = 'large' }) {
  const color = score >= 90 ? '#4caf50' : score >= 70 ? '#ff9800' : '#f44336';
  const h = size === 'large' ? 72 : 48;
  const fontSize = size === 'large' ? 28 : 20;
  return (
    <Box sx={{ textAlign: 'center' }}>
      <Box sx={{
        width: h, height: h, borderRadius: '50%', mx: 'auto', mb: 0.5,
        background: `conic-gradient(${color} ${score * 3.6}deg, rgba(255,255,255,0.08) 0deg)`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative'
      }}>
        <Box sx={{
          width: h - 14, height: h - 14, borderRadius: '50%',
          bgcolor: 'background.paper', display: 'flex',
          alignItems: 'center', justifyContent: 'center'
        }}>
          <Typography sx={{ fontWeight: 800, fontSize, color }}>{score}%</Typography>
        </Box>
      </Box>
      {label && <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>{label}</Typography>}
    </Box>
  );
}

// ── Stat Card ────────────────────────────────────────────────────────────────
function StatCard({ value, label, color, icon, before, after, animate }) {
  const improved = before !== undefined && after !== undefined && after < before;
  const worsened = before !== undefined && after !== undefined && after > before;
  return (
    <Card sx={{
      p: 1, textAlign: 'center',
      border: improved ? '1.5px solid #4caf50' : worsened ? '1.5px solid #f44336' : '1px solid rgba(255,255,255,0.08)',
      transition: 'all 0.4s ease',
      transform: animate ? 'scale(1.03)' : 'scale(1)'
    }}>
      {icon && <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1, color }}>{icon}</Box>}
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase', display: 'block', mt: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color }}>
        {value}
      </Typography>
      {improved && (
        <Chip label={`↓ ${before - after} fixed`} color="success" size="small" sx={{ mb: 1, fontWeight: 700 }} />
      )}
    </Card>
  );
}

export default function ConflictsPage() {
  const [loading, setLoading]           = useState(true);
  const [resolving, setResolving]       = useState(false);
  const [conflicts, setConflicts]       = useState([]);
  const [softViolations, setSoft]       = useState([]);
  const [staffingAlerts, setStaffing]   = useState([]);
  const [timetable, setTimetable]       = useState([]);
  const [score, setScore]               = useState(100);
  const [hardCount, setHardCount]       = useState(0);
  const [softCount, setSoftCount]       = useState(0);
  const [resolveResult, setResult]      = useState(null);
  const [animateStats, setAnimate]      = useState(false);
  const [showMoveLog, setShowLog]       = useState(false);
  const [resolvedIds, setResolvedIds]   = useState([]);

  // Resolution Dialog State
  const [resolutionTarget, setResTarget]  = useState(null);
  const [suggestions, setSuggestions]     = useState([]);
  const [suggestLoading, setSuggestLoad]  = useState(false);

  const loadConflicts = useCallback(async () => {
    setLoading(true);
    try {
      const [tt, analytics] = await Promise.all([getTimetable(), getAnalytics()]);
      setTimetable(Array.isArray(tt) ? tt : []);
      setConflicts(analytics.conflicts || []);
      setSoft(analytics.softConstraintViolations || []);
      setStaffing(analytics.staffingAlerts || []);
      setScore(analytics.overallScore ?? 100);
      setHardCount(analytics.hardViolations ?? 0);
      setSoftCount(analytics.softViolations ?? 0);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadConflicts(); }, [loadConflicts]);

  // ── Auto-Resolve All via ConflictResolver ─────────────────────────────────
  const handleAutoResolve = async () => {
    setResolving(true);
    setResult(null);
    try {
      const res = await resolveConflicts({});
      setResult(res);
      // Update live stats
      const after = res.after || {};
      setConflicts(res.conflicts || []);
      setSoft(res.softConstraintViolations || []);
      setStaffing(res.staffingAlerts || []);
      setHardCount(after.hardViolations ?? res.hardViolations ?? 0);
      setSoftCount(after.softViolations ?? res.softViolations ?? 0);
      setScore(after.overallScore ?? res.overallScore ?? 100);
      setTimetable(res.timetable || timetable);
      // Trigger animation
      setAnimate(true);
      setTimeout(() => setAnimate(false), 1200);
    } catch (err) {
      alert('Conflict resolution failed: ' + err.message);
    } finally {
      setResolving(false);
    }
  };

  // ── Manual single-conflict resolve ───────────────────────────────────────
  const handleOpenResolve = async (conflict) => {
    setResTarget(conflict);
    setSuggestions([]);
    setSuggestLoad(true);
    try {
      const entry = timetable.find(e =>
        e.day === conflict.day && e.slot === conflict.slot &&
        conflict.affectedCourses.includes(e.course)
      ) || timetable.find(e => e.course === conflict.affectedCourses[0]);
      if (entry) {
        const res = await getSuggestions({ entry, currentTimetable: timetable });
        setSuggestions(res.suggestions || []);
      }
    } catch (err) { console.error(err); }
    finally { setSuggestLoad(false); }
  };

  const applyResolution = async (sug) => {
    if (!resolutionTarget) return;
    const courseId = resolutionTarget.affectedCourses[0];
    const updated = timetable.map(item =>
      item.course === courseId && item.day === resolutionTarget.day && item.slot === resolutionTarget.slot
        ? { ...item, day: sug.newDay, slot: sug.newSlot, room: sug.newRoom }
        : item
    );
    try {
      setLoading(true);
      await saveTimetable(updated);
      setResolvedIds(prev => [...prev, resolutionTarget.id]);
      setResTarget(null);
      await loadConflicts();
    } catch (err) {
      alert(`Failed to apply: ${err.message}`);
      setLoading(false);
    }
  };

  const openConflicts    = conflicts.filter(c => !resolvedIds.includes(c.id));
  const resolvedConflicts= conflicts.filter(c =>  resolvedIds.includes(c.id));
  const severityColor    = { high: 'error', medium: 'warning', low: 'default' };
  const beforeScore      = resolveResult?.before?.overallScore;
  const beforeHard       = resolveResult?.before?.hardViolations;
  const beforeSoft       = resolveResult?.before?.softViolations;

  if (loading) return (
    <>
      <Navbar title="Conflict Detection" />
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
        <CircularProgress />
      </Box>
    </>
  );

  return (
    <>
      <Navbar title="Conflict Detection & Resolution" />
      <Box className="page" sx={{ p: 3, maxWidth: 1000, mx: 'auto' }}>

        {/* ── Hero Score Panel ────────────────────────────────────────── */}
        <Paper sx={{
          p: 3, mb: 3,
          background: score >= 90
            ? 'linear-gradient(135deg, #0a2e0a 0%, #1a4a1a 100%)'
            : score >= 70
            ? 'linear-gradient(135deg, #2e1e00 0%, #4a3200 100%)'
            : 'linear-gradient(135deg, #2e0a0a 0%, #4a1a1a 100%)',
          border: `1px solid ${score >= 90 ? 'rgba(76,175,80,0.3)' : score >= 70 ? 'rgba(255,152,0,0.3)' : 'rgba(244,67,54,0.3)'}`,
          borderRadius: 3,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2
        }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <ScoreGauge score={score} label="Overall Score" />
            {resolveResult && beforeScore !== undefined && (
              <>
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <TrendingUpIcon sx={{ color: '#4caf50', fontSize: 28 }} />
                  <Typography variant="caption" sx={{ color: '#4caf50', fontWeight: 700 }}>
                    +{score - beforeScore}%
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">Before</Typography>
                  <ScoreGauge score={beforeScore} size="small" />
                </Box>
              </>
            )}
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 0.5 }}>
                {score === 100 ? '✅ Conflict-Free Timetable' :
                 score >= 90  ? '⚡ Near-Optimal Schedule' :
                 score >= 70  ? '⚠ Minor Issues Detected' :
                                '🔴 Significant Conflicts Found'}
              </Typography>
              <Typography variant="body2" sx={{ opacity: 0.75, color: '#fff' }}>
                {hardCount} hard violation{hardCount !== 1 ? 's' : ''} · {softCount} soft violation{softCount !== 1 ? 's' : ''}
              </Typography>
              {resolveResult && (
                <Typography variant="caption" sx={{ color: '#4caf50', display: 'block', mt: 0.5 }}>
                  {resolveResult.message}
                </Typography>
              )}
            </Box>
          </Box>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, minWidth: 200 }}>
            <Button
              variant="contained"
              size="large"
              startIcon={resolving ? <CircularProgress size={18} color="inherit" /> : <AutoFixHighIcon />}
              onClick={handleAutoResolve}
              disabled={resolving || (hardCount === 0 && softCount === 0)}
              sx={{
                py: 1.5, fontWeight: 700, fontSize: 15,
                background: 'linear-gradient(90deg, #1a7aef, #4caf50)',
                '&:hover': { background: 'linear-gradient(90deg, #1564c0, #388e3c)' },
                '&:disabled': { opacity: 0.5 }
              }}
            >
              {resolving ? 'Resolving…' : hardCount === 0 && softCount === 0 ? '✅ All Clear' : '⚡ Auto-Resolve All'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={loadConflicts}
              sx={{ fontWeight: 600 }}
            >
              Refresh Analysis
            </Button>
          </Box>
        </Paper>

        {/* ── Stats Row ───────────────────────────────────────────────── */}
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} sm={3}>
            <StatCard
              value={hardCount}
              label="Hard Violations"
              color={hardCount > 0 ? 'error.main' : 'success.main'}
              icon={<WarningAmberIcon />}
              before={beforeHard}
              after={hardCount}
              animate={animateStats}
            />
          </Grid>
          <Grid item xs={6} sm={3}>
            <StatCard
              value={softCount}
              label="Soft Violations"
              color={softCount > 0 ? 'warning.main' : 'success.main'}
              icon={<TimelineIcon />}
              before={beforeSoft}
              after={softCount}
              animate={animateStats}
            />
          </Grid>
          <Grid item xs={6} sm={3}>
            <StatCard
              value={resolvedConflicts.length + (resolveResult?.movesApplied || 0)}
              label="Entries Rescheduled"
              color="success.main"
              icon={<CheckCircleOutlineIcon />}
              animate={animateStats}
            />
          </Grid>
          <Grid item xs={6} sm={3}>
            <StatCard
              value={staffingAlerts.length}
              label="Staffing Alerts"
              color={staffingAlerts.length > 0 ? 'warning.main' : 'text.secondary'}
              icon={<PeopleAltIcon />}
              animate={animateStats}
            />
          </Grid>
        </Grid>

        {/* ── Resolve Result Banner ─────────────────────────────────── */}
        {resolveResult && (
          <Alert
            severity={hardCount === 0 ? 'success' : 'warning'}
            sx={{ mb: 2, fontWeight: 600 }}
            action={
              <Button size="small" onClick={() => setShowLog(!showMoveLog)}>
                {showMoveLog ? 'Hide' : 'Show'} Move Log ({resolveResult.moveLog?.length || 0})
              </Button>
            }
          >
            {resolveResult.message}
          </Alert>
        )}

        {/* ── Move Log ─────────────────────────────────────────────── */}
        {resolveResult && showMoveLog && (
          <Card sx={{ mb: 3, bgcolor: 'rgba(255,255,255,0.02)' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <AutoFixHighIcon fontSize="small" color="primary" />
                What Changed ({resolveResult.moveLog?.length} actions, {resolveResult.movesApplied} moves applied)
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                {(resolveResult.moveLog || []).map((log, i) => (
                  <Typography key={i} variant="body2" sx={{ fontSize: 12.5, py: 0.3,
                    color: log.startsWith('✅') ? 'success.main' :
                           log.startsWith('⚠') ? 'warning.main' :
                           log.startsWith('🔀') ? 'primary.main' :
                           log.startsWith('📦') ? '#4caf50' : 'text.secondary' }}>
                    {log}
                  </Typography>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

        {/* ── Staffing Alerts ──────────────────────────────────────── */}
        {staffingAlerts.length > 0 && (
          <Card sx={{ mb: 3, border: '1.5px solid rgba(255,152,0,0.4)' }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                <PeopleAltIcon color="warning" /> Staffing Recommendations
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {staffingAlerts.map((alert, i) => (
                  <Paper key={i} variant="outlined" sx={{ p: 2, borderColor: 'rgba(255,152,0,0.3)', bgcolor: 'rgba(255,152,0,0.04)' }}>
                    <Box sx={{ display: 'flex', gap: 1, mb: 0.5 }}>
                      <Chip label="OVERLOADED" color="warning" size="small" sx={{ fontWeight: 700 }} />
                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{alert.facultyName}</Typography>
                      <Typography variant="caption" color="text.secondary">({alert.dept})</Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      {alert.currentWeeklyHours} hrs/week (max {alert.maxAllowedWeeklyHours} hrs) — overloaded by {alert.overloadHours} hrs
                    </Typography>
                    <Typography variant="body2" sx={{ color: 'warning.main', fontWeight: 600, fontSize: 12.5 }}>
                      {alert.recommendation}
                    </Typography>
                  </Paper>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

        {/* ── Status Banner ────────────────────────────────────────── */}
        <Paper sx={{
          p: 2, mb: 3,
          bgcolor: openConflicts.length > 0 ? 'rgba(239,83,80,0.08)' : 'rgba(76,175,80,0.08)',
          border: `1px solid ${openConflicts.length > 0 ? 'rgba(239,83,80,0.2)' : 'rgba(76,175,80,0.2)'}`,
          display: 'flex', alignItems: 'center', gap: 2
        }}>
          {openConflicts.length > 0
            ? <WarningAmberIcon color="error" />
            : <CheckCircleOutlineIcon color="success" />}
          <Typography variant="body2" sx={{
            color: openConflicts.length > 0 ? 'error.main' : 'success.main', fontWeight: 700
          }}>
            {openConflicts.length > 0
              ? `${openConflicts.length} conflict(s) require attention. Click "Auto-Resolve All" to fix automatically.`
              : 'All constraints satisfied! The timetable is conflict-free and ready for publishing.'}
          </Typography>
        </Paper>

        {/* ── Active Conflicts List ────────────────────────────────── */}
        {openConflicts.length > 0 && (
          <Card sx={{ mb: 4 }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Active Conflicts ({openConflicts.length})
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {openConflicts.map(c => (
                  <Paper key={c.id} variant="outlined" sx={{
                    p: 2, borderColor: 'rgba(255,255,255,0.08)',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    flexWrap: 'wrap', gap: 2
                  }}>
                    <Box sx={{ flex: 1 }}>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 1 }}>
                        <Chip label={c.severity?.toUpperCase() || 'HIGH'} color={severityColor[c.severity] || 'error'} size="small" sx={{ fontWeight: 700 }} />
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{c.type}</Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{c.desc}</Typography>
                      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                        <Typography variant="caption" color="text.secondary">📅 {c.day} · {c.slot}</Typography>
                        {(c.affectedCourses || []).map(course => (
                          <Chip key={course} label={course} size="small" variant="outlined" sx={{ height: 20, fontSize: 10 }} />
                        ))}
                      </Box>
                    </Box>
                    <Button variant="outlined" color="primary" size="small" startIcon={<LightbulbIcon />}
                      onClick={() => handleOpenResolve(c)}>
                      Manual Fix
                    </Button>
                  </Paper>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

        {/* ── Soft Violations ─────────────────────────────────────── */}
        {softViolations.length > 0 && (
          <Card sx={{ mb: 4, border: '1px solid rgba(255,152,0,0.2)' }}>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Soft Constraint Warnings ({softViolations.length})
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                {softViolations.map((v, i) => (
                  <Paper key={i} variant="outlined" sx={{ p: 1.5, borderColor: 'rgba(255,152,0,0.2)' }}>
                    <Box sx={{ display: 'flex', gap: 1, mb: 0.5 }}>
                      <Chip label={v.type} color="warning" size="small" variant="outlined" />
                    </Box>
                    <Typography variant="body2" color="text.secondary">{v.desc}</Typography>
                  </Paper>
                ))}
              </Box>
            </CardContent>
          </Card>
        )}

        {/* ── Resolved List ────────────────────────────────────────── */}
        {resolvedConflicts.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Resolved ({resolvedConflicts.length})
              </Typography>
              {resolvedConflicts.map(c => (
                <Paper key={c.id} variant="outlined" sx={{
                  p: 2, mb: 1, bgcolor: 'rgba(255,255,255,0.01)', borderColor: 'rgba(255,255,255,0.04)',
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center'
                }}>
                  <Box>
                    <Box sx={{ display: 'flex', gap: 1, mb: 0.5 }}>
                      <Chip label="RESOLVED" color="success" size="small" sx={{ fontWeight: 700 }} />
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, textDecoration: 'line-through', opacity: 0.7 }}>{c.type}</Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" sx={{ opacity: 0.6 }}>{c.desc}</Typography>
                  </Box>
                  <CheckCircleOutlineIcon color="success" />
                </Paper>
              ))}
            </CardContent>
          </Card>
        )}
      </Box>

      {/* ── Manual Resolution Dialog ─────────────────────────────────── */}
      <Dialog open={!!resolutionTarget} onClose={() => setResTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
          <LightbulbIcon color="primary" /> Manual Conflict Fix
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
              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700 }}>Suggested Slots:</Typography>
              {suggestLoading ? (
                <Box sx={{ textAlign: 'center', py: 3 }}><CircularProgress /></Box>
              ) : suggestions.length === 0 ? (
                <Alert severity="info">
                  No automated slot suggestions available for this entry. Use "Auto-Resolve All" for a complete fix.
                </Alert>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  {suggestions.map((sug, idx) => (
                    <Paper key={idx} variant="outlined" sx={{
                      p: 2, cursor: 'pointer', borderColor: 'rgba(255,255,255,0.08)',
                      '&:hover': { background: 'rgba(26,122,239,0.08)', borderColor: 'primary.main' }
                    }} onClick={() => applyResolution(sug)}>
                      <Typography variant="subtitle2" color="primary.main" sx={{ fontWeight: 700, mb: 0.5 }}>
                        {sug.action || `Move to ${sug.newDay} ${sug.newSlot}`}
                      </Typography>
                      <Typography variant="body2"><strong>Reason:</strong> {sug.reason}</Typography>
                      <Typography variant="caption" color="text.secondary">New Room: {sug.newRoom}</Typography>
                    </Paper>
                  ))}
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResTarget(null)}>Cancel</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}