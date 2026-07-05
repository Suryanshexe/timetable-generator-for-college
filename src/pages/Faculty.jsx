import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { FACULTY as INIT_FACULTY, DEPARTMENTS, COURSES } from '../utils/data';
import { createItem, deleteItem, getCollection, updateItem } from '../api';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

export default function FacultyPage() {
  const [faculty, setFaculty] = useState(INIT_FACULTY);
  const [courses, setCourses] = useState(COURSES);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [availModal, setAvailModal] = useState(null);
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ id: '', name: '', dept: 'CSE', designation: 'Assistant Professor', courses: [] });

  const designations = ['Professor', 'Associate Professor', 'Assistant Professor', 'Lecturer'];

  useEffect(() => {
    getCollection('faculty', INIT_FACULTY).then(setFaculty);
    getCollection('courses', COURSES).then(setCourses);
  }, []);

  const openAdd = () => {
    setEditing(null);
    setForm({ id: '', name: '', dept: 'CSE', designation: 'Assistant Professor', courses: [] });
    setShowModal(true);
  };

  const openEdit = (f) => {
    setEditing(f.id);
    setForm({ ...f });
    setShowModal(true);
  };

  const handleSave = async () => {
    try {
      const saved = editing
        ? await updateItem('faculty', editing, form)
        : await createItem('faculty', form);

      setFaculty(editing ? faculty.map(f => f.id === editing ? saved : f) : [...faculty, saved]);
      setNotice('Saved to Java backend.');
    } catch (error) {
      setFaculty(editing ? faculty.map(f => f.id === editing ? { ...form } : f) : [...faculty, form]);
      setNotice('Saved locally. Start the Java backend to persist changes.');
    }
    setShowModal(false);
  };

  const handleDelete = async id => {
    if (!window.confirm('Remove this faculty member?')) return;
    try {
      await deleteItem('faculty', id);
      setNotice('Deleted from Java backend.');
    } catch (error) {
      setNotice('Deleted locally. Start the Java backend to persist changes.');
    }
    setFaculty(faculty.filter(f => f.id !== id));
  };

  return (
    <>
      <Navbar title="Faculty" />
      <div className="page">
        {notice && <div className="alert alert-success">{notice}</div>}
        <div className="card">
          <div className="card-header">
            <h3>Faculty Registry ({faculty.length})</h3>
            <button className="btn btn-primary" onClick={openAdd}>+ Add Faculty</button>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Faculty ID</th>
                  <th>Name</th>
                  <th>Department</th>
                  <th>Designation</th>
                  <th>Courses Assigned</th>
                  <th>Availability</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {faculty.map(f => (
                  <tr key={f.id}>
                    <td><code style={{ background: 'var(--juit-blue-pale)', color: 'var(--juit-blue)', padding: '2px 8px', borderRadius: 4, fontSize: 12 }}>{f.id}</code></td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{
                          width: 32, height: 32, borderRadius: '50%', background: 'var(--juit-blue)', color: '#fff',
                          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0
                        }}>
                          {f.name.split(' ').filter(n => n !== 'Dr.' && n !== 'Prof.').map(n => n[0]).slice(0,2).join('')}
                        </div>
                        <span style={{ fontWeight: 600 }}>{f.name}</span>
                      </div>
                    </td>
                    <td>{f.dept}</td>
                    <td><span className="badge badge-blue" style={{ fontSize: 11 }}>{f.designation}</span></td>
                    <td>
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                        {f.courses.map(c => (
                          <span key={c} className="badge badge-gray" style={{ fontSize: 11 }}>{c}</span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <button className="btn btn-sm btn-outline" onClick={() => setAvailModal(f)}>
                        Set Availability
                      </button>
                    </td>
                    <td>
                      <div className="flex-gap">
                        <button className="btn btn-sm btn-ghost" onClick={() => openEdit(f)}>Edit</button>
                        <button className="btn btn-sm" style={{ background: '#fdecea', color: 'var(--juit-red)', border: 'none' }} onClick={() => handleDelete(f.id)}>Remove</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>{editing ? 'Edit Faculty' : 'Add Faculty'}</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Faculty ID</label>
                  <input className="form-control" value={form.id} onChange={e => setForm({...form, id: e.target.value})} placeholder="e.g. F07" />
                </div>
                <div className="form-group">
                  <label className="form-label">Full Name</label>
                  <input className="form-control" value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="Dr. Name Surname" />
                </div>
                <div className="form-group">
                  <label className="form-label">Department</label>
                  <select className="form-control" value={form.dept} onChange={e => setForm({...form, dept: e.target.value})}>
                    {DEPARTMENTS.map(d => <option key={d}>{d}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Designation</label>
                  <select className="form-control" value={form.designation} onChange={e => setForm({...form, designation: e.target.value})}>
                    {designations.map(d => <option key={d}>{d}</option>)}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Assign Courses</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {courses.filter(c => c.dept === form.dept).map(c => (
                    <label key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer', fontSize: 13 }}>
                      <input
                        type="checkbox"
                        checked={form.courses?.includes(c.id)}
                        onChange={e => {
                          const updated = e.target.checked
                            ? [...(form.courses||[]), c.id]
                            : form.courses.filter(x => x !== c.id);
                          setForm({...form, courses: updated});
                        }}
                      />
                      {c.id}
                    </label>
                  ))}
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave}>{editing ? 'Save' : 'Add Faculty'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Availability Modal */}
      {availModal && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h3>Availability — {availModal.name}</h3>
              <button className="modal-close" onClick={() => setAvailModal(null)}>✕</button>
            </div>
            <div className="modal-body">
              <p style={{ fontSize: 13, color: 'var(--juit-muted)', marginBottom: 16 }}>
                Select days when the faculty member is unavailable for scheduling.
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {DAYS.map(day => {
                  const isChecked = availModal.unavailableSlots?.includes(day);
                  return (
                    <label key={day} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
                      <input
                        type="checkbox"
                        checked={!!isChecked}
                        onChange={async (e) => {
                          const list = availModal.unavailableSlots || [];
                          const updatedSlots = e.target.checked
                            ? [...list, day]
                            : list.filter(d => d !== day);
                          
                          const updatedFaculty = { ...availModal, unavailableSlots: updatedSlots };
                          setAvailModal(updatedFaculty);
                        }}
                      />
                      <span>{day}</span>
                      <span style={{ color: 'var(--juit-muted)', fontSize: 12 }}>— Mark as unavailable</span>
                    </label>
                  );
                })}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setAvailModal(null)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  try {
                    const saved = await updateItem('faculty', availModal.id, availModal);
                    setFaculty(faculty.map(f => f.id === availModal.id ? saved : f));
                    setNotice(`Availability updated for ${availModal.name}.`);
                  } catch (err) {
                    setFaculty(faculty.map(f => f.id === availModal.id ? availModal : f));
                    setNotice('Availability updated locally.');
                  }
                  setAvailModal(null);
                }}
              >
                Save Availability
              </button>
            </div>
          </div>
        </div>
      )}

    </>
  );
}
