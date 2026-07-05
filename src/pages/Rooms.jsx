import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { ROOMS as INIT_ROOMS } from '../utils/data';
import { createItem, deleteItem, getCollection, updateItem } from '../api';

export default function RoomsPage() {
  const [rooms, setRooms] = useState(INIT_ROOMS);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ id: '', name: '', type: 'Lecture', capacity: 60, block: 'A' });

  useEffect(() => {
    getCollection('rooms', INIT_ROOMS).then(setRooms);
  }, []);

  const openAdd = () => {
    setEditing(null);
    setForm({ id: '', name: '', type: 'Lecture', capacity: 60, block: 'A' });
    setShowModal(true);
  };

  const openEdit = r => {
    setEditing(r.id);
    setForm({ ...r });
    setShowModal(true);
  };

  const handleSave = async () => {
    const room = { ...form, capacity: +form.capacity };
    try {
      const saved = editing
        ? await updateItem('rooms', editing, room)
        : await createItem('rooms', room);

      setRooms(editing ? rooms.map(r => r.id === editing ? saved : r) : [...rooms, saved]);
      setNotice('Saved to Java backend.');
    } catch (error) {
      setRooms(editing ? rooms.map(r => r.id === editing ? room : r) : [...rooms, room]);
      setNotice('Saved locally. Start the Java backend to persist changes.');
    }
    setShowModal(false);
  };

  const handleDelete = async id => {
    if (!window.confirm('Delete this room?')) return;
    try {
      await deleteItem('rooms', id);
      setNotice('Deleted from Java backend.');
    } catch (error) {
      setNotice('Deleted locally. Start the Java backend to persist changes.');
    }
    setRooms(rooms.filter(x => x.id !== id));
  };

  const typeColor = { Lecture: 'badge-blue', Lab: 'badge-amber', Seminar: 'badge-green' };

  return (
    <>
      <Navbar title="Rooms & Labs" />
      <div className="page">
        {notice && <div className="alert alert-success">{notice}</div>}
        {/* Summary */}
        <div className="stats-grid mb-6">
          {[
            { type: 'Lecture', count: rooms.filter(r => r.type === 'Lecture').length, icon: '🏫', cls: 'blue' },
            { type: 'Lab', count: rooms.filter(r => r.type === 'Lab').length, icon: '🔬', cls: 'amber' },
            { type: 'Seminar', count: rooms.filter(r => r.type === 'Seminar').length, icon: '🎓', cls: 'green' },
            { type: 'Total Capacity', count: rooms.reduce((a, r) => a + r.capacity, 0), icon: '👥', cls: 'red' },
          ].map(s => (
            <div className="stat-card" key={s.type}>
              <div className={`stat-icon ${s.cls}`}>{s.icon}</div>
              <div className="stat-info">
                <div className="value">{s.count}</div>
                <div className="label">{s.type}</div>
              </div>
            </div>
          ))}
        </div>

        <div className="card">
          <div className="card-header">
            <h3>Room Registry ({rooms.length})</h3>
            <button className="btn btn-primary" onClick={openAdd}>+ Add Room</button>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Room ID</th>
                  <th>Room Name</th>
                  <th>Block</th>
                  <th>Type</th>
                  <th>Capacity</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rooms.map(r => (
                  <tr key={r.id}>
                    <td><code style={{ background: 'var(--juit-blue-pale)', color: 'var(--juit-blue)', padding: '2px 8px', borderRadius: 4, fontSize: 12 }}>{r.id}</code></td>
                    <td style={{ fontWeight: 600 }}>{r.name}</td>
                    <td style={{ textAlign: 'center' }}><span className="badge badge-gray">Block {r.block}</span></td>
                    <td><span className={`badge ${typeColor[r.type]}`}>{r.type}</span></td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{
                          height: 6, width: 80, background: 'var(--juit-border)', borderRadius: 3, overflow: 'hidden'
                        }}>
                          <div style={{ height: '100%', width: `${Math.min(100, (r.capacity / 150) * 100)}%`, background: 'var(--juit-blue)', borderRadius: 3 }} />
                        </div>
                        <span style={{ fontSize: 13, fontWeight: 600 }}>{r.capacity}</span>
                      </div>
                    </td>
                    <td>
                      <div className="flex-gap">
                        <button className="btn btn-sm btn-ghost" onClick={() => openEdit(r)}>Edit</button>
                        <button className="btn btn-sm" style={{ background: '#fdecea', color: 'var(--juit-red)', border: 'none' }}
                          onClick={() => handleDelete(r.id)}>
                          Delete
                        </button>
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
              <h3>{editing ? 'Edit Room' : 'Add Room'}</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Room ID</label>
                  <input className="form-control" value={form.id} onChange={e => setForm({...form, id: e.target.value})} placeholder="e.g. R301" />
                </div>
                <div className="form-group">
                  <label className="form-label">Room Name</label>
                  <input className="form-control" value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="e.g. Room 301" />
                </div>
                <div className="form-group">
                  <label className="form-label">Type</label>
                  <select className="form-control" value={form.type} onChange={e => setForm({...form, type: e.target.value})}>
                    <option>Lecture</option><option>Lab</option><option>Seminar</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Block</label>
                  <select className="form-control" value={form.block} onChange={e => setForm({...form, block: e.target.value})}>
                    {['A','B','C','D','E'].map(b => <option key={b}>{b}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Capacity</label>
                  <input className="form-control" type="number" min={10} max={500} value={form.capacity} onChange={e => setForm({...form, capacity: e.target.value})} />
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave}>{editing ? 'Save' : 'Add Room'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
