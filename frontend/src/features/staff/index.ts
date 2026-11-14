// Publiczny interfejs funkcji "staff". Import z innych funkcji wyłącznie przez ten plik.
export { DoctorsPage } from './pages/DoctorsPage';
export { MySchedulePage } from './pages/MySchedulePage';
export { useDoctors, useDoctorSchedule, useMySchedule, weekEnd } from './hooks';
export { gridHours, toWorkingIntervals, weeklySummary } from './lib';
