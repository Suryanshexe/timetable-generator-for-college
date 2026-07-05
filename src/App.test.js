import { render, screen } from '@testing-library/react';
import App from './App';

test('renders login screen', () => {
  render(<App />);
  const linkElement = screen.getByText(/Timetable Manager/i);
  expect(linkElement).toBeInTheDocument();
});
