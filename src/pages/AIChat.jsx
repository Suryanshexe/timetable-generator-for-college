import React, { useState, useEffect, useRef } from 'react';
import Navbar from '../components/Navbar';
import { getTimetable, chatCommand, saveTimetable } from '../api';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  IconButton,
  Paper,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Divider,
  Button
} from '@mui/material';
import {
  Send as SendIcon,
  AutoAwesome as AutoAwesomeIcon,
  SmartToy as SmartToyIcon,
  Person as PersonIcon
} from '@mui/icons-material';


export default function AIChatPage() {
  const [timetable, setTimetable] = useState([]);
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'ai',
      text: "Hello! I am your AI Scheduling Assistant. You can issue schedule-related commands in plain English.\n\nTry commands like:\n- \"Don't schedule DBMS on Friday.\"\n- \"Schedule Dr Sharma in the mornings only.\"\n- \"Move CS301 from Monday 8:00 to Thursday 10:00.\"",
      score: null
    }
  ]);
  const [input, setInput] = useState('');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    getTimetable().then(setTimetable).catch(console.error);
  }, []);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim()) return;

    const userCommand = input.trim();
    setInput('');
    
    // Add user message
    const userMsg = { id: Date.now(), sender: 'user', text: userCommand };
    setMessages(prev => [...prev, userMsg]);
    setLoading(true);

    try {
      // Call AI Chat command API
      const res = await chatCommand(userCommand, timetable);
      
      // Save the updated schedule automatically
      if (res.type === 'update' && res.timetable && res.timetable.length > 0) {
        await saveTimetable(res.timetable);
        setTimetable(res.timetable);
      }

      // Add AI reply message
      let aiReplyText = '';
      if (res.type === 'question') {
        aiReplyText = res.answer || "I'm sorry, I couldn't answer that question.";
      } else {
        aiReplyText = res.conflicts && res.conflicts.length > 0
          ? `Schedule updated, but conflicts were detected:\n${res.conflicts.map(c => `- [${c.type}] ${c.desc}`).join('\n')}`
          : (res.message || `Schedule successfully updated based on your request: "${userCommand}". All constraints verified successfully!`);
      }

      const aiMsg = {
        id: Date.now() + 1,
        sender: 'ai',
        text: aiReplyText,
        score: res.type === 'update' ? res.score : null
      };

      setMessages(prev => [...prev, aiMsg]);
    } catch (err) {
      console.error(err);
      const errorMsg = {
        id: Date.now() + 1,
        sender: 'ai',
        text: `Sorry, I encountered an error updating the schedule: ${err.message}`
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Navbar title="AI Chat Assistant" />
      <Box className="page" sx={{ p: 3, maxWidth: 900, mx: 'auto', display: 'flex', flexDirection: 'column', height: 'calc(100vh - 100px)' }}>
        
        {/* Messages Log */}
        <Box sx={{ flex: 1, overflowY: 'auto', mb: 2, pr: 1 }}>
          <List sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {messages.map(msg => {
              const isAi = msg.sender === 'ai';
              return (
                <ListItem
                  key={msg.id}
                  sx={{
                    alignSelf: isAi ? 'flex-start' : 'flex-end',
                    width: 'fit-content',
                    maxWidth: '80%',
                    p: 0
                  }}
                >
                  <Paper
                    sx={{
                      p: 2,
                      background: isAi 
                        ? 'linear-gradient(135deg, rgba(20, 30, 55, 0.7) 0%, rgba(10, 15, 30, 0.8) 100%)'
                        : 'linear-gradient(135deg, rgba(26, 122, 239, 0.8) 0%, rgba(0, 86, 179, 0.9) 100%)',
                      border: isAi ? '1px solid rgba(255, 255, 255, 0.08)' : 'none',
                      color: '#fff',
                      borderRadius: isAi ? '16px 16px 16px 4px' : '16px 16px 4px 16px',
                      boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.15)',
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                      {isAi ? <SmartToyIcon fontSize="small" color="primary" /> : <PersonIcon fontSize="small" />}
                      <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', opacity: 0.7 }}>
                        {isAi ? 'Scheduling Engine' : 'Administrator'}
                      </Typography>
                    </Box>

                    <Typography variant="body2" sx={{ whiteSpace: 'pre-line', fontSize: 13.5, lineHeight: 1.5 }}>
                      {msg.text}
                    </Typography>

                    {/* Score Card embed */}
                    {isAi && msg.score && (
                      <Box sx={{ mt: 2, pt: 1.5, borderTop: '1px solid rgba(255,255,255,0.1)', display: 'flex', gap: 2, alignItems: 'center' }}>
                        <Box>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>Timetable Score</Typography>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.light' }}>
                            {msg.score.overallScore}%
                          </Typography>
                        </Box>
                        <Box sx={{ width: 1, height: 20, borderLeft: '1px solid rgba(255,255,255,0.1)' }} />
                        <Box>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>Hard Violations</Typography>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: msg.score.hardViolations > 0 ? 'secondary.light' : '#4caf50' }}>
                            {msg.score.hardViolations}
                          </Typography>
                        </Box>
                      </Box>
                    )}
                  </Paper>

                </ListItem>
              );
            })}
            {loading && (
              <ListItem sx={{ alignSelf: 'flex-start' }}>
                <Paper sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2, bgcolor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)' }}>
                  <CircularProgress size={20} />
                  <Typography variant="body2" sx={{ color: 'text.secondary' }}>AI is checking availability and resolving constraints...</Typography>
                </Paper>
              </ListItem>
            )}
            <div ref={messagesEndRef} />
          </List>
        </Box>

        {/* Input area */}
        <Paper
          elevation={4}
          sx={{
            p: 1.5,
            display: 'flex',
            alignItems: 'center',
            background: 'rgba(255, 255, 255, 0.03)',
            backdropFilter: 'blur(10px)',
            border: '1px solid rgba(255, 255, 255, 0.05)',
            borderRadius: 3
          }}
        >
          <TextField
            fullWidth
            variant="transparent"
            placeholder="Type scheduling command here..."
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSend()}
            sx={{
              '& .MuiInputBase-input': { color: 'text.primary', px: 1 },
              '& .MuiOutlinedInput-root': { '& fieldset': { border: 'none' } }
            }}
          />
          <IconButton color="primary" onClick={handleSend} disabled={loading}>
            <SendIcon />
          </IconButton>
        </Paper>

      </Box>
    </>
  );
}
