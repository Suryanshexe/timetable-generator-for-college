import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import { useAuth, ROLES } from '../context/AuthContext';
import { getCollection, createItem, updateItem, deleteItem } from '../api';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  TextField,
  Chip,
  Paper,
  Divider
} from '@mui/material';
import {
  Send as SendIcon,
  Delete as DeleteIcon,
  CheckCircle as ResolveIcon,
  ReportProblem as ReportIcon
} from '@mui/icons-material';

export default function IssuesPage() {
  const { user } = useAuth();
  const [issues, setIssues] = useState([]);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [replyText, setReplyText] = useState({});

  useEffect(() => {
    loadIssues();
  }, []);

  const loadIssues = async () => {
    try {
      const data = await getCollection('issues', []);
      setIssues(data);
    } catch (e) {
      console.error(e);
    }
  };

  const handleSubmitIssue = async () => {
    if (!title.trim() || !message.trim()) return;
    const newIssue = {
      id: `issue_${Date.now()}`,
      title,
      message,
      facultyId: user.email,
      facultyName: user.name,
      status: 'Pending',
      reply: '',
      date: new Date().toISOString()
    };
    await createItem('issues', newIssue);
    setTitle('');
    setMessage('');
    loadIssues();
  };

  const handleReply = async (id) => {
    const issue = issues.find(i => i.id === id);
    const reply = replyText[id] || '';
    if (!reply.trim()) return;
    await updateItem('issues', id, { ...issue, reply, status: 'Resolved' });
    setReplyText({ ...replyText, [id]: '' });
    loadIssues();
  };

  const handleResolve = async (id) => {
    const issue = issues.find(i => i.id === id);
    await updateItem('issues', id, { ...issue, status: 'Resolved' });
    loadIssues();
  };

  const handleDelete = async (id) => {
    if (window.confirm('Delete this issue?')) {
      await deleteItem('issues', id);
      loadIssues();
    }
  };

  // Faculty sees only their issues; Admin sees all
  const visibleIssues = user?.role === ROLES.ADMIN 
    ? issues 
    : issues.filter(i => i.facultyId === user?.email);

  return (
    <>
      <Navbar title={user?.role === ROLES.ADMIN ? 'Manage Issues & Requests' : 'My Issues & Requests'} />
      <Box className="page" sx={{ p: 3 }}>
        <Grid container spacing={4}>
          
          {user?.role === ROLES.FACULTY && (
            <Grid item xs={12} md={5}>
              <Card sx={{ position: 'sticky', top: 20 }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                    <ReportIcon color="primary" /> Submit New Request
                  </Typography>
                  <TextField
                    fullWidth
                    label="Issue Title"
                    variant="outlined"
                    size="small"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    sx={{ mb: 2 }}
                  />
                  <TextField
                    fullWidth
                    label="Message Details"
                    variant="outlined"
                    multiline
                    rows={4}
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    sx={{ mb: 3 }}
                  />
                  <Button 
                    variant="contained" 
                    fullWidth 
                    size="large" 
                    endIcon={<SendIcon />}
                    onClick={handleSubmitIssue}
                    disabled={!title.trim() || !message.trim()}
                  >
                    Send to Admin
                  </Button>
                </CardContent>
              </Card>
            </Grid>
          )}

          <Grid item xs={12} md={user?.role === ROLES.FACULTY ? 7 : 12}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {visibleIssues.length === 0 ? (
                <Paper sx={{ p: 5, textAlign: 'center', bgcolor: 'transparent', border: '1px dashed rgba(0,0,0,0.1)' }}>
                  <Typography color="text.secondary">No issues found.</Typography>
                </Paper>
              ) : (
                visibleIssues.slice().reverse().map(issue => (
                  <Card key={issue.id} sx={{ overflow: 'visible' }}>
                    <CardContent>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                        <Box>
                          <Typography variant="h6" sx={{ fontWeight: 700 }}>
                            {issue.title}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                            Submitted by <strong>{issue.facultyName}</strong> on {new Date(issue.date).toLocaleDateString()}
                          </Typography>
                        </Box>
                        <Chip 
                          label={issue.status} 
                          size="small" 
                          color={issue.status === 'Resolved' ? 'success' : 'warning'} 
                          sx={{ fontWeight: 600 }}
                        />
                      </Box>
                      
                      <Typography variant="body2" sx={{ mb: 3, p: 2, bgcolor: 'rgba(0,0,0,0.02)', borderRadius: 2 }}>
                        {issue.message}
                      </Typography>

                      {issue.reply && (
                        <Box sx={{ mb: 2, p: 2, bgcolor: 'rgba(26, 122, 239, 0.05)', borderRadius: 2, borderLeft: '4px solid #1a7aef' }}>
                          <Typography variant="caption" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5, display: 'block' }}>
                            Admin Reply
                          </Typography>
                          <Typography variant="body2">
                            {issue.reply}
                          </Typography>
                        </Box>
                      )}

                      {user?.role === ROLES.ADMIN && (
                        <>
                          <Divider sx={{ my: 2 }} />
                          {issue.status !== 'Resolved' && (
                            <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                              <TextField
                                fullWidth
                                size="small"
                                placeholder="Type a reply..."
                                value={replyText[issue.id] || ''}
                                onChange={(e) => setReplyText({ ...replyText, [issue.id]: e.target.value })}
                              />
                              <Button 
                                variant="contained" 
                                disableElevation
                                onClick={() => handleReply(issue.id)}
                              >
                                Reply
                              </Button>
                            </Box>
                          )}
                          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                            {issue.status !== 'Resolved' && (
                              <Button 
                                size="small" 
                                startIcon={<ResolveIcon />}
                                color="success"
                                onClick={() => handleResolve(issue.id)}
                              >
                                Mark Resolved
                              </Button>
                            )}
                            <Button 
                              size="small" 
                              startIcon={<DeleteIcon />}
                              color="error"
                              onClick={() => handleDelete(issue.id)}
                            >
                              Delete
                            </Button>
                          </Box>
                        </>
                      )}
                    </CardContent>
                  </Card>
                ))
              )}
            </Box>
          </Grid>
        </Grid>
      </Box>
    </>
  );
}
