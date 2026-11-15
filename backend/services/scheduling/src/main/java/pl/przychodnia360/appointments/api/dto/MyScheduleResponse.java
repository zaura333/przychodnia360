package pl.przychodnia360.appointments.api.dto;

import java.util.List;
import pl.przychodnia360.appointments.api.AppointmentViews.StaffAppointmentView;

public record MyScheduleResponse(List<IntervalResponse> intervals, List<StaffAppointmentView> appointments) {
}
