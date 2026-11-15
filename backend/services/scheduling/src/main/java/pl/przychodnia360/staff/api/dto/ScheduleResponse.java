package pl.przychodnia360.staff.api.dto;

import java.util.List;
import pl.przychodnia360.staff.application.ScheduleService.ScheduleView;

public record ScheduleResponse(List<WeeklyEntryResponse> weekly, List<ExceptionResponse> exceptions,
        List<IntervalResponse> intervals) {

    public static ScheduleResponse of(ScheduleView view) {
        return new ScheduleResponse(view.weekly().stream().map(WeeklyEntryResponse::of).toList(),
                view.exceptions().stream().map(ExceptionResponse::of).toList(),
                view.intervals().stream().map(i -> new IntervalResponse(i.start(), i.end())).toList());
    }
}
