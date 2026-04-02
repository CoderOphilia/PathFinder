package com.pathfinder.mentee.dto;



import java.util.List;

public record CalenderDay(
        String dayName,
        List<CalendarEvent> events
) {
}
