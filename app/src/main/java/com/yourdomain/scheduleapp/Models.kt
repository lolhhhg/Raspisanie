package com.yourdomain.scheduleapp

enum class PartType { NUMERATOR, DENOMINATOR }
data class PairDetail(val subject: String = "", val room: String = "", val homework: String = "")
data class SchedulePair(val pairNumber: Int, val numerator: PairDetail = PairDetail(), val denominator: PairDetail = PairDetail())
data class DailySchedule(val dayOfWeek: Int, val pairs: List<SchedulePair>)
