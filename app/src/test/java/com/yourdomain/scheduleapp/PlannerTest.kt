package com.yourdomain.scheduleapp
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlannerTest {
    private val r=ScheduleItemEntity(dayOfWeek=2,pairNumber=1,numeratorSubject="",denominatorSubject="ОАП",denominatorRoom="42",denominatorHomework="Кавычки \"и\"\nновая строка")
    @Test fun weekBoundaries(){val p=Preferences();assertEquals(PartType.DENOMINATOR,Planner.part(LocalDate.parse("2026-09-13"),p));assertEquals(PartType.NUMERATOR,Planner.part(LocalDate.parse("2026-09-14"),p));assertEquals(PartType.NUMERATOR,Planner.part(LocalDate.parse("2026-09-06"),p));assertEquals(PartType.DENOMINATOR,Planner.part(LocalDate.parse("2026-09-21"),p))}
    @Test fun splitCanBeEmpty(){val b=Backup(rows=listOf(r));assertEquals("ОАП",Planner.lessons(LocalDate.parse("2026-09-08"),b).single().detail.subject);assertTrue(Planner.lessons(LocalDate.parse("2026-09-15"),b).isEmpty())}
    @Test fun sharedIsBothWeeks(){val b=Backup(rows=listOf(r.copy(numeratorSubject="Физра")),state=PlannerState(modes=mapOf("2-1" to "SAME")));assertEquals("Физра",Planner.lessons(LocalDate.parse("2026-09-08"),b).single().detail.subject);assertEquals("Физра",Planner.lessons(LocalDate.parse("2026-09-15"),b).single().detail.subject)}
    @Test fun skipsEmptyAndSunday(){val b=Backup(rows=listOf(r));val next=Planner.next(LocalDateTime.parse("2026-09-06T12:00:00"),b)!!;assertEquals(LocalDate.parse("2026-09-08"),next.date);assertTrue(Planner.lessons(LocalDate.parse("2026-09-13"),b).isEmpty())}
    @Test fun backupRoundtrip(){val b=Backup(rows=listOf(r),state=Planner.migrate(listOf(r),mapOf("2-1" to "SPLIT")));assertEquals(b,BackupCodec.decode(BackupCodec.encode(b)));assertFalse(BackupCodec.encode(b).isBlank())}
    @Test fun oldBackupMigration(){val text=BackupCodec.gson.toJson(ScheduleBackup(rows=listOf(r),pairModes=mapOf("2-1" to "SPLIT")));val b=BackupCodec.decode(text);assertEquals(r,b.rows.single());assertEquals(r.denominatorHomework,b.state.tasks.single().text)}
    @Test(expected=IllegalArgumentException::class) fun emptyRejected(){BackupCodec.decode("")}
    @Test(expected=IllegalArgumentException::class) fun wrongFileRejected(){BackupCodec.decode("{}")}
    @Test(expected=IllegalArgumentException::class) fun duplicateRejected(){BackupCodec.encode(Backup(rows=listOf(r,r)))}
    @Test fun bells(){assertEquals("09:00",Planner.bells(1)[0].first);assertEquals("16:40",Planner.bells(2)[4].second)}
}
