import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { COURSES as INIT_COURSES, DEPARTMENTS } from '../utils/data';
import { createItem, deleteItem, getCollection, updateItem } from '../api';

export default function CoursesPage() {
  const [courses, setCourses] = useState(INIT_COURSES);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [search, setSearch] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ id: '', name: '', credits: 4, dept: 'CSE', type: 'Theory', semester: 5, sections: 'A,B', facultyId: '' });

  useEffect(() => {
    getCollection('courses', INIT_COURSES).then(setCourses);
  }, []);

  const filtered = courses.filter(c =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.id.toLowerCase().includes(search.toLowerCase())
  );

  const openAdd = () => {
    setEditing(null);
    setForm({ id: '', name: '', credits: 4, dept: 'CSE', type: 'Theory', semester: 5, sections: 'A', facultyId: '' });
    setShowModal(true);
  };

  const openEdit = (c) => {
    setEditing(c.id);
    setForm({ ...c, sections: c.sections.join(','), facultyId: '' });
    setShowModal(true);
  };

  const handleSave = async () => {
    const newCourse = { ...form, credits: +form.credits, semester: +form.semester, sections: form.sections.split(',').map(s => s.trim()) };
    try {
      const saved = editing
        ? await updateItem('courses', editing, newCourse)
        : await createItem('courses', newCourse);

      setCourses(editing ? courses.map(c => c.id === editing ? saved : c) : [...courses, saved]);
      setNotice('Saved to Java backend.');
    } catch (error) {
      setCourses(editing ? courses.map(c => c.id === editing ? newCourse : c) : [...courses, newCourse]);
      setNotice('Saved locally. Start the Java backend to persist changes.');
    }
    setShowModal(false);
  };

  const handleDelete = async id => {
    if (!window.confirm('Delete this course?')) return;
    try {
      await deleteItem('courses', id);
      setNotice('Deleted from Java backend.');
    } catch (error) {
      setNotice('Deleted locally. Start the Java backend to persist changes.');
    }
    setCourses(courses.filter(c => c.id !== id));
  };

  return (
    <>
      <Navbar title="Courses" />
      <div className="page">
        {notice && <div className="alert alert-success">{notice}</div>}
        <div className="card">
          <div className="card-header">
            <h3>Course Registry ({filtered.length})</h3>
            <div className="flex-gap">
              <input
                className="form-control"
                placeholder="Search courses..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                style={{ width: 200 }}
              />
              <button className="btn btn-primary" onClick={openAdd}>+ Add Course</button>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Course ID</th>
                  <th>Course Name</th>
                  <th>Dept</th>
                  <th>Sem</th>
                  <th>Credits</th>
                  <th>Type</th>
                  <th>Sections</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(c => (
                  <tr key={c.id}>
                    <td><code style={{ background: 'var(--juit-blue-pale)', color: 'var(--juit-blue)', padding: '2px 8px', borderRadius: 4, fontSize: 12, fontFamily: 'monospace' }}>{c.id}</code></td>
                    <td style={{ fontWeight: 600 }}>{c.name}</td>
                    <td>{c.dept}</td>
                    <td style={{ textAlign: 'center' }}>{c.semester}</td>
                    <td style={{ textAlign: 'center' }}>{c.credits}</td>
                    <td>
                      <span className={`badge ${c.type === 'Lab' ? 'badge-amber' : 'badge-blue'}`}>{c.type}</span>
                    </td>
                    <td>{c.sections.join(', ')}</td>
                    <td>
                      <div className="flex-gap">
                        <button className="btn btn-sm btn-ghost" onClick={() => openEdit(c)}>Edit</button>
                        <button className="btn btn-sm" style={{ background: '#fdecea', color: 'var(--juit-red)', border: 'none' }} onClick={() => handleDelete(c.id)}>Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {showModal && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>{editing ? 'Edit Course' : 'Add New Course'}</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Course ID *</label>
                  <input className="form-control" value={form.id} onChange={e => setForm({...form, id: e.target.value})} placeholder="e.g. CS301" />
                </div>
                <div className="form-group">
                  <label className="form-label">Course Name *</label>
                  <input className="form-control" value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="e.g. Algorithms" />
                </div>
                <div className="form-group">
                  <label className="form-label">Department</label>
                  <select className="form-control" value={form.dept} onChange={e => setForm({...form, dept: e.target.value})}>
                    {DEPARTMENTS.map(d => <option key={d}>{d}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Type</label>
                  <select className="form-control" value={form.type} onChange={e => setForm({...form, type: e.target.value})}>
                    <option>Theory</option><option>Lab</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Credits</label>
                  <input className="form-control" type="number" min={1} max={6} value={form.credits} onChange={e => setForm({...form, credits: e.target.value})} />
                </div>
                <div className="form-group">
                  <label className="form-label">Semester</label>
                  <select className="form-control" value={form.semester} onChange={e => setForm({...form, semester: +e.target.value})}>
                    {[1,2,3,4,5,6,7,8].map(s => <option key={s}>{s}</option>)}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Sections (comma separated)</label>
                <input className="form-control" value={form.sections} onChange={e => setForm({...form, sections: e.target.value})} placeholder="A, B" />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave}>{editing ? 'Save Changes' : 'Add Course'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
