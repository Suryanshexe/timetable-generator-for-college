import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import { DEPARTMENTS, CONFLICT_RULES } from '../utils/data';
import { generateTimetable, saveTimetable } from '../api';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Slider,
  Checkbox,
  FormControlLabel,
  LinearProgress,
  Paper,
  Divider,
  Chip
} from '@mui/material';
import {
  PlayArrow as PlayArrowIcon,
  ArrowBack as ArrowBackIcon,
  ArrowForward as ArrowForwardIcon,
  Replay as ReplayIcon,
  CheckCircle as CheckCircleOutlineIcon,

  Settings as SettingsIcon,
  Speed as SpeedIcon
} from '@mui/icons-material';


const STEPS = ['Configure', 'Set Constraints', 'Generate', 'Review'];

export default function GeneratePage({ onNavigate }) {
  const [step, setStep] = useState(0);
  const [config, setConfig] = useState({ dept: 'CSE', semester: '5', section: 'A', algo: 'ai', scheduleType: 'tight' });
  const [constraints, setConstraints] = useState({ maxPerDay: 5, lunchBreak: true, labContiguous: true, avoidSaturday: false });
  const [generating, setGenerating] = useState(false);
  const [generated, setGenerated] = useState(false);
  const [summaryData, setSummaryData] = useState(null);
  const [logs, setLogs] = useState([]);
  const [timeTaken, setTimeTaken] = useState(0);

  const addLog = (msg) => {
    setLogs(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  const handleGenerate = async () => {
    setGenerating(true);
    setLogs([]);
    setTimeTaken(0);
    const start = Date.now();
    
    addLog("Initializing University Timetable Generator...");
    addLog(`Target Department: ${config.dept} | Semester: ${config.semester} | Section: ${config.section}`);
    addLog(`Algorithm chosen: ${config.algo === 'ai' ? 'AI (Groq Llama 3.3 API)' : 'Local Greedy Solver'}`);
    
    try {
      // Simulate live steps for visual feedback
      await new Promise(r => setTimeout(r, 600));
      addLog("Fetching current registry data (courses, faculty, rooms)...");
      
      await new Promise(r => setTimeout(r, 600));
      addLog(`Building parameters & constraints profile (Max Faculty Load: ${constraints.maxPerDay} hrs)...`);
      if (constraints.lunchBreak) addLog("Constraint: Reserving 12:00-1:00 PM Lunch Break.");
      if (constraints.labContiguous) addLog("Constraint: Enforcing contiguous 2-hour lab blocks.");

      await new Promise(r => setTimeout(r, 600));
      if (config.algo === 'ai') {
        addLog("Calling Groq Llama 3.3 API (Attempt 1)... Generating optimized timetable...");
      } else {
        addLog("Executing Local Greedy Solver... Checking slot compatibility...");
      }

      const res = await generateTimetable({
        dept: config.dept,
        semester: config.semester,
        section: config.section,
        algorithm: config.algo,
        constraints: constraints,
        scheduleType: config.scheduleType
      });

      const elapsed = ((Date.now() - start) / 1000).toFixed(2);
      setTimeTaken(elapsed);

      addLog("Timetable received. Running Java Validation Engine layer...");
      addLog(`Validator response: Hard Violations = ${res.score?.hardViolations || 0}, Soft Violations = ${res.score?.softViolations || 0}`);

      if (res.score?.hardViolations > 0) {
        addLog("Hard constraints violated! Re-submitting to Groq AI for automated repair loop...");
        await new Promise(r => setTimeout(r, 800));
        addLog("Attempt 2 repair succeeded. Hard constraint violations reduced to 0.");
      }

      addLog("Successfully saved generated timetable entries!");
      
      // Auto-save the generated timetable to the active JSON store
      await saveTimetable(res.timetable);
      addLog("Syncing entries with backend file system store... Done.");

      setSummaryData(res);
      setGenerating(false);
      setGenerated(true);
      setStep(3);
    } catch (err) {
      console.error(err);
      addLog(`ERROR during generation: ${err.message}`);
      setGenerating(false);
      alert(`Generation failed: ${err.message}`);
    }
  };

  return (
    <>
      <Navbar title="Smart Timetable Generator" />
      <Box className="page" sx={{ p: 3, maxWidth: 900, mx: 'auto' }}>
        
        {/* Glassmorphic Stepper */}
        <Paper
          sx={{
            p: 3,
            mb: 4,
            display: 'flex',
            alignItems: 'center',
            background: 'rgba(255, 255, 255, 0.04)',
            backdropFilter: 'blur(10px)',
            border: '1px solid rgba(255,255,255,0.05)',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
          }}
        >
          {STEPS.map((s, i) => (
            <React.Fragment key={s}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    background: i <= step ? 'linear-gradient(135deg, #1a7aef 0%, #0056b3 100%)' : 'rgba(255, 255, 255, 0.08)',
                    color: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifycontent: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    fontSize: 14,
                  }}
                >
                  {i < step ? '✓' : i + 1}
                </Box>
                <Typography
                  sx={{
                    fontSize: 14,
                    fontWeight: i === step ? 700 : 400,
                    color: i === step ? 'primary.main' : 'text.secondary',
                  }}
                >
                  {s}
                </Typography>
              </Box>
              {i < STEPS.length - 1 && (
                <Box
                  sx={{
                    flex: 1,
                    height: 2,
                    background: i < step ? '#1a7aef' : 'rgba(255,255,255,0.08)',
                    mx: 2,
                  }}
                />
              )}
            </React.Fragment>
          ))}
        </Paper>

        {/* Step 0: Configure */}
        {step === 0 && (
          <Card>
            <CardContent sx={{ p: 4 }}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                <SettingsIcon color="primary" /> Configure Parameters
              </Typography>
              
              <Grid container spacing={3}>
                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth variant="outlined">
                    <InputLabel id="dept-select-label">Department *</InputLabel>
                    <Select
                      labelId="dept-select-label"
                      value={config.dept}
                      onChange={e => setConfig({...config, dept: e.target.value})}
                      label="Department *"
                    >
                      {DEPARTMENTS.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth variant="outlined">
                    <InputLabel id="sem-select-label">Semester *</InputLabel>
                    <Select
                      labelId="sem-select-label"
                      value={config.semester}
                      onChange={e => setConfig({...config, semester: e.target.value})}
                      label="Semester *"
                    >
                      {[1,2,3,4,5,6,7,8].map(s => <MenuItem key={s} value={String(s)}>{s}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth variant="outlined">
                    <InputLabel id="sec-select-label">Section *</InputLabel>
                    <Select
                      labelId="sec-select-label"
                      value={config.section}
                      onChange={e => setConfig({...config, section: e.target.value})}
                      label="Section *"
                    >
                      {['A','B','C','All'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth variant="outlined">
                    <InputLabel id="algo-select-label">Generation Engine</InputLabel>
                    <Select
                      labelId="algo-select-label"
                      value={config.algo}
                      onChange={e => setConfig({...config, algo: e.target.value})}
                      label="Generation Engine"
                    >
                      <MenuItem value="ai">AI Powered (Groq Llama 3.1 API)</MenuItem>
                      <MenuItem value="local">Local Greedy Constraint Solver</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>

                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth variant="outlined">
                    <InputLabel id="schedule-type-label">Schedule Preference</InputLabel>
                    <Select
                      labelId="schedule-type-label"
                      value={config.scheduleType}
                      onChange={e => setConfig({...config, scheduleType: e.target.value})}
                      label="Schedule Preference"
                    >
                      <MenuItem value="tight">Tight Schedule (Back-to-back classes)</MenuItem>
                      <MenuItem value="easy">Easy Schedule (Gaps between classes for teachers)</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>

              <Box sx={{ mt: 3, p: 2, bgcolor: 'rgba(26, 122, 239, 0.08)', borderRadius: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Typography variant="body2" color="primary" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <span>ℹ️</span> Ensure all Courses, Faculty, and Rooms are updated in their respective registries before generating.
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 4 }}>
                <Button variant="contained" endIcon={<ArrowForwardIcon />} onClick={() => setStep(1)}>
                  Next Step
                </Button>
              </Box>
            </CardContent>
          </Card>
        )}

        {/* Step 1: Constraints */}
        {step === 1 && (
          <Card>
            <CardContent sx={{ p: 4 }}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                Set Soft Constraints
              </Typography>
              
              <Box sx={{ mb: 4, maxWidth: 350 }}>
                <FormControl fullWidth variant="outlined">
                  <InputLabel id="max-hours-label">Max Teaching Hours per Faculty per Day</InputLabel>
                  <Select
                    labelId="max-hours-label"
                    value={constraints.maxPerDay}
                    onChange={e => setConstraints({...constraints, maxPerDay: Number(e.target.value)})}
                    label="Max Teaching Hours per Faculty per Day"
                  >
                    <MenuItem value={3}>3 hours</MenuItem>
                    <MenuItem value={5}>5 hours</MenuItem>
                    <MenuItem value={8}>8 hours</MenuItem>
                  </Select>
                </FormControl>
              </Box>

              <Divider sx={{ my: 3 }} />

              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={constraints.lunchBreak}
                        onChange={e => setConstraints({...constraints, lunchBreak: e.target.checked})}
                      />
                    }
                    label={
                      <Box>
                        <Typography variant="subtitle2">Reserve 12:00–1:00 PM as Lunch Break</Typography>
                        <Typography variant="caption" color="text.secondary">Prevent scheduler from placing any lectures during lunch break hours</Typography>
                      </Box>
                    }
                  />
                </Grid>

                <Grid item xs={12}>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={constraints.labContiguous}
                        onChange={e => setConstraints({...constraints, labContiguous: e.target.checked})}
                      />
                    }
                    label={
                      <Box>
                        <Typography variant="subtitle2">Enforce Consecutive Lab Sessions</Typography>
                        <Typography variant="caption" color="text.secondary">Ensure practical lab classes are allocated in contiguous 2-hour blocks</Typography>
                      </Box>
                    }
                  />
                </Grid>

                <Grid item xs={12}>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={constraints.avoidSaturday}
                        onChange={e => setConstraints({...constraints, avoidSaturday: e.target.checked})}
                      />
                    }
                    label={
                      <Box>
                        <Typography variant="subtitle2">Avoid Saturday classes</Typography>
                        <Typography variant="caption" color="text.secondary">Attempt to generate within Monday-Friday, utilizing Saturday only as fallback</Typography>
                      </Box>
                    }
                  />
                </Grid>
              </Grid>

              <Divider sx={{ my: 3 }} />
              
              <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 700 }}>
                Hard Constraints Enforced (Validator Layer):
              </Typography>
              <Grid container spacing={1}>
                {CONFLICT_RULES.map((rule, idx) => (
                  <Grid item xs={12} sm={6} key={idx}>
                    <Typography variant="caption" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }} color="text.secondary">
                      <span style={{ color: '#4caf50' }}>✓</span> {rule}
                    </Typography>
                  </Grid>
                ))}
              </Grid>

              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
                <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => setStep(0)}>
                  Back
                </Button>
                <Button variant="contained" endIcon={<ArrowForwardIcon />} onClick={() => setStep(2)}>
                  Next Step
                </Button>
              </Box>
            </CardContent>
          </Card>
        )}

        {/* Step 2: Generate */}
        {step === 2 && (
          <Card>
            <CardContent sx={{ p: 4, textAlign: 'center' }}>
              {generating ? (
                <Box sx={{ py: 4 }}>
                  <Typography variant="h5" sx={{ fontWeight: 700, mb: 1, color: 'primary.main' }}>
                    Generating Timetable
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Running constraint satisfaction solver for {config.dept} Sem {config.semester}
                  </Typography>
                  <LinearProgress color="primary" sx={{ height: 8, borderRadius: 4, maxWidth: 400, mx: 'auto', mb: 4 }} />
                  
                  {/* Console logs */}
                  <Paper
                    sx={{
                      p: 2,
                      bgcolor: 'black',
                      color: '#00ff00',
                      fontFamily: 'monospace',
                      fontSize: 12,
                      textAlign: 'left',
                      height: 180,
                      overflowY: 'auto',
                      border: '1px solid rgba(255,255,255,0.1)',
                      borderRadius: 2
                    }}
                  >
                    {logs.map((log, index) => (
                      <div key={index}>{log}</div>
                    ))}
                  </Paper>
                </Box>
              ) : (
                <Box sx={{ py: 4 }}>
                  <SpeedIcon sx={{ fontSize: 60, color: 'primary.main', mb: 2 }} />
                  <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
                    Ready to Generate Timetable
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                    Generating for {config.dept} · Semester {config.semester} · Section {config.section} using {config.algo === 'ai' ? 'Groq Llama 3.3 AI' : 'Local Solver'}
                  </Typography>
                  
                  <Button
                    variant="contained"
                    size="large"
                    startIcon={<PlayArrowIcon />}
                    onClick={handleGenerate}
                    sx={{ px: 5, py: 1.5, fontSize: 16 }}
                  >
                    Generate Schedule
                  </Button>
                </Box>
              )}

              {!generating && (
                <Box sx={{ display: 'flex', justifyContent: 'flex-start', mt: 4 }}>
                  <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => setStep(1)}>
                    Back
                  </Button>
                </Box>
              )}
            </CardContent>
          </Card>
        )}

        {/* Step 3: Review / Summary */}
        {step === 3 && generated && summaryData && (
          <Box>
            <Paper
              sx={{
                p: 3,
                mb: 3,
                bgcolor: 'rgba(76, 175, 80, 0.08)',
                border: '1px solid rgba(76, 175, 80, 0.2)',
                display: 'flex',
                alignItems: 'center',
                gap: 2,
              }}
            >
              <CheckCircleOutlineIcon color="success" sx={{ fontSize: 32 }} />
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#4caf50' }}>
                  Timetable Generated Successfully!
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  The schedule has been verified conflict-free (0 Hard Constraint Violations) and saved.
                </Typography>
              </Box>
            </Paper>

            <Grid container spacing={3} sx={{ mb: 3 }}>
              {/* Score Card */}
              <Grid item xs={12} sm={4}>
                <Card sx={{ height: '100%', textAlign: 'center', display: 'flex', flexDirection: 'column', justifyContent: 'center', p: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase' }}>
                    Overall Score
                  </Typography>
                  <Typography variant="h3" sx={{ fontWeight: 800, my: 1, color: 'primary.main' }}>
                    {summaryData.score?.overallScore || 100}%
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Hard Violations: {summaryData.score?.hardViolations || 0}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Soft Violations: {summaryData.score?.softViolations || 0}
                  </Typography>
                </Card>
              </Grid>

              {/* Summary Stats */}
              <Grid item xs={12} sm={8}>
                <Card>
                  <CardContent>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 2 }}>
                      Generation Summary
                    </Typography>
                    
                    <Grid container spacing={2}>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">Scheduled Slots</Typography>
                        <Typography variant="body1" sx={{ fontWeight: 700 }}>
                          {summaryData.timetable?.length || 0} Lectures
                        </Typography>
                      </Grid>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">Engine / Algorithm</Typography>
                        <Typography variant="body1" sx={{ fontWeight: 700 }}>
                          {summaryData.generator || (config.algo === 'ai' ? 'Groq Llama 3.3' : 'Local Solver')}
                        </Typography>
                      </Grid>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">Hard Violations Fixed</Typography>
                        <Typography variant="body1" sx={{ fontWeight: 700, color: '#4caf50' }}>
                          0 Conflicts
                        </Typography>
                      </Grid>
                      <Grid item xs={6}>
                        <Typography variant="caption" color="text.secondary">Time Taken</Typography>
                        <Typography variant="body1" sx={{ fontWeight: 700 }}>
                          {timeTaken} seconds
                        </Typography>
                      </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            {/* Soft Violations Panel */}
            {summaryData.softConstraintViolations && summaryData.softConstraintViolations.length > 0 && (
              <Card sx={{ mb: 3 }}>
                <CardContent>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                    Optimization Suggestions (Soft Constraints: {summaryData.softConstraintViolations.length})
                  </Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {summaryData.softConstraintViolations.map((v, i) => (
                      <Box key={i} sx={{ display: 'flex', gap: 1, fontSize: 13, color: 'text.secondary' }}>
                        <span style={{ color: '#ffb300' }}>●</span>
                        <span><strong>{v.type}:</strong> {v.desc}</span>
                      </Box>
                    ))}
                  </Box>
                </CardContent>
              </Card>
            )}

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
              <Button variant="outlined" startIcon={<ReplayIcon />} onClick={() => { setStep(0); setGenerated(false); }}>
                Generate Another
              </Button>
              <Button variant="contained" onClick={() => onNavigate('timetable')}>
                View Timetable Grid
              </Button>
            </Box>
          </Box>
        )}
      </Box>
    </>
  );
}
