// Publiczny interfejs funkcji "staff". Import z innych funkcji wyłącznie przez ten plik.
export { DoctorsPage } from './pages/DoctorsPage';
export { MySchedulePage } from './pages/MySchedulePage';
export { AdminEmployeesPage } from './pages/AdminEmployeesPage';
export { EmployeeSchedulePage } from './pages/EmployeeSchedulePage';
export { useDoctors, useDoctorSchedule, useDoctorSchedules, useMySchedule, weekEnd } from './hooks';
export { gridHours, toWorkingIntervals, weeklySummary } from './lib';
