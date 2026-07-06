# JUIT Timetable Manager

Simple React + Java backend for managing timetable courses, faculty, and rooms.

## Run the Java backend

Compile and start the dependency-free Java API:

```powershell
javac backend\SimpleBackend.java
java -cp backend SimpleBackend
```

The backend runs at `http://localhost:8080/api` and stores data in `backend/data`.

Available endpoints:

- `GET/POST /api/courses`
- `GET/PUT/DELETE /api/courses/{id}`
- `GET/POST /api/faculty`
- `GET/PUT/DELETE /api/faculty/{id}`
- `GET/POST /api/rooms`
- `GET/PUT/DELETE /api/rooms/{id}`

## Run the React frontend

In another terminal:

```powershell
npm start
```

Open [http://localhost:3000](http://localhost:3000) to view it in your browser.

If the backend is not running, the management screens still show local demo data, but changes will not persist.

## Demo credentials

- Admin: `admin@juit.ac.in` / `admin123`
- Faculty: `priya@juit.ac.in` / `faculty123`
- Student: `211230@juit.ac.in` / `student123`

## Scripts

### `npm test`

Runs the frontend test suite once.

### `npm run build`

Builds the React app for production into the `build` folder.

Trying to make it more user friendly and easier and more automated...
