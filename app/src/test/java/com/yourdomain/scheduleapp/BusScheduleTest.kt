package com.yourdomain.scheduleapp

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BusScheduleTest {
    @Test fun nextDepartureHandlesBoundariesAndTomorrow() {
        fun check(now: String, expected: String) = assertEquals(LocalDateTime.parse(expected), BusSchedule.next(LocalDateTime.parse(now)))
        check("2026-09-09T06:00:00", "2026-09-09T06:25:00")
        check("2026-09-09T09:35:00", "2026-09-09T09:35:00")
        check("2026-09-09T09:35:01", "2026-09-09T10:50:00")
        check("2026-09-09T21:15:01", "2026-09-10T06:25:00")
        check("2026-12-31T23:59:59", "2027-01-01T06:25:00")
        assertEquals(26, BusSchedule.inbound.size)
        assertEquals(25, BusSchedule.outbound.size)
        assertEquals(LocalDateTime.parse("2026-09-10T06:05:00"), BusSchedule.next(LocalDateTime.parse("2026-09-09T20:00:00"), false))
    }
}
