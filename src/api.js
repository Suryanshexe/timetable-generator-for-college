const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with status ${response.status}`);
  }

  return response.status === 204 ? null : response.json();
}

export async function getCollection(collection, fallback = []) {
  try {
    return await request(`/${collection}`);
  } catch (error) {
    console.warn(`Using local ${collection} data because backend is unavailable.`, error);
    return fallback;
  }
}

export function createItem(collection, item) {
  return request(`/${collection}`, {
    method: 'POST',
    body: JSON.stringify(item),
  });
}

export function updateItem(collection, id, item) {
  return request(`/${collection}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(item),
  });
}

export function deleteItem(collection, id) {
  return request(`/${collection}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

export function getTimetable() {
  return request('/timetable');
}

export function saveTimetable(timetable) {
  return request('/timetable', {
    method: 'POST',
    body: JSON.stringify(timetable),
  });
}

export function generateTimetable(params) {
  return request('/timetable/generate', {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

export function validateTimetable(timetable) {
  return request('/timetable/validate', {
    method: 'POST',
    body: JSON.stringify(timetable),
  });
}

export function getSuggestions(body) {
  return request('/timetable/suggest', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function clearTimetable(semester, dept = 'All') {
  return request('/timetable/clear', {
    method: 'POST',
    body: JSON.stringify({ semester, dept }),
  });
}

export function autoFixTimetable(currentTimetable, dept, semester) {
  return request('/timetable/auto-fix', {
    method: 'POST',
    body: JSON.stringify({ currentTimetable, dept, semester }),
  });
}

export function chatCommand(command, currentTimetable) {
  return request('/timetable/chat', {
    method: 'POST',
    body: JSON.stringify({ command, currentTimetable }),
  });
}

export function importCollection(collection, csvContent) {
  return request(`/import/${collection}`, {
    method: 'POST',
    body: csvContent,
    headers: { 'Content-Type': 'text/plain' },
  });
}

