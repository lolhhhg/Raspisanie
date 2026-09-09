package com.yourdomain.scheduleapp

import java.time.LocalDateTime
import java.time.LocalTime

/** Timetable transcribed from the two supplied photographs; no live tracking. */
object BusSchedule {
    val inbound = times("06:25 06:50 07:20 07:45 08:15 09:00 09:35 10:50 11:20 11:50 12:30 13:00 13:30 13:50 14:30 14:55 15:30 16:00 16:25 17:00 17:30 18:00 18:35 19:15 20:20 21:15")
    val outbound = times("06:05 06:30 07:00 07:40 08:15 08:45 09:20 09:55 10:35 11:10 12:10 12:35 13:10 13:45 14:15 14:45 15:10 15:45 16:10 16:45 17:15 17:40 18:15 19:05 19:45")
    private fun times(text: String) = text.split(" ").map(LocalTime::parse)
    fun next(now: LocalDateTime, inboundDirection: Boolean = true): LocalDateTime {
        val times = if (inboundDirection) inbound else outbound
        return times.map { now.toLocalDate().atTime(it) }.firstOrNull { !it.isBefore(now) }
            ?: now.toLocalDate().plusDays(1).atTime(times.first())
    }
}
