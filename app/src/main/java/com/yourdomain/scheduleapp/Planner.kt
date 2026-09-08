package com.yourdomain.scheduleapp

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Subject(val id:String=UUID.randomUUID().toString(),val name:String="",val room:String="",val teacher:String="",val kind:String="",val color:Long=0xFFFF6B00,val shortName:String="")
data class Assignment(val id:String=UUID.randomUUID().toString(),val subject:String="",val text:String="",val due:String="",val done:Boolean=false,val scope:String="Все пары")
data class Preferences(val anchor:String="2026-09-07",val anchorPart:PartType=PartType.DENOMINATOR,val theme:String="Системная",val reminderMinutes:Int=10,val notifications:Boolean=false)
data class PlannerState(val subjects:List<Subject> = emptyList(),val tasks:List<Assignment> = emptyList(),val modes:Map<String,String> = emptyMap(),val preferences:Preferences=Preferences())
data class Backup(val format:String="raspisanie",val version:Int=2,val created:String=Instant.now().toString(),val rows:List<ScheduleItemEntity> = emptyList(),val state:PlannerState=PlannerState())
data class Lesson(val date:LocalDate,val number:Int,val detail:PairDetail,val part:PartType,val same:Boolean,val start:LocalDateTime,val end:LocalDateTime)

object Planner {
    val days=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")
    fun key(day:Int,pair:Int)="$day-$pair"
    fun part(date:LocalDate,p:Preferences):PartType {
        val a=LocalDate.parse(p.anchor).with(DayOfWeek.MONDAY)
        val weeks=ChronoUnit.WEEKS.between(a,date.with(DayOfWeek.MONDAY))
        return if(Math.floorMod(weeks,2L)==0L)p.anchorPart else if(p.anchorPart==PartType.NUMERATOR)PartType.DENOMINATOR else PartType.NUMERATOR
    }
    fun bells(day:Int)=if(day==1)listOf("09:00" to "10:20","10:40" to "12:00","12:20" to "13:40","14:00" to "15:20","15:40" to "17:00") else listOf("08:00" to "09:20","09:50" to "11:10","11:40" to "13:00","13:30" to "14:50","15:20" to "16:40")
    fun detail(r:ScheduleItemEntity,part:PartType)=if(part==PartType.NUMERATOR)PairDetail(r.numeratorSubject,r.numeratorRoom,r.numeratorHomework) else PairDetail(r.denominatorSubject,r.denominatorRoom,r.denominatorHomework)
    fun lessons(date:LocalDate,b:Backup):List<Lesson> {
        val day=date.dayOfWeek.value
        if(day==7)return emptyList()
        val part=part(date,b.state.preferences)
        return b.rows.filter{it.dayOfWeek==day}.sortedBy{it.pairNumber}.mapNotNull{r->
            val same=b.state.modes[key(day,r.pairNumber)]==PairMode.SAME.name
            val actual=if(same)PartType.NUMERATOR else part
            val d=detail(r,actual)
            if(d.subject.isBlank())null else {val t=bells(day)[r.pairNumber-1];Lesson(date,r.pairNumber,d,actual,same,date.atTime(LocalTime.parse(t.first)),date.atTime(LocalTime.parse(t.second)))}
        }
    }
    fun next(now:LocalDateTime,b:Backup):Lesson?=(0L..14L).asSequence().flatMap{lessons(now.toLocalDate().plusDays(it),b).asSequence()}.firstOrNull{it.end>now}
    fun normalize(s:String)=s.trim().lowercase(java.util.Locale.ROOT)
    fun migrate(rows:List<ScheduleItemEntity>,modes:Map<String,String>):PlannerState {
        val names=rows.flatMap{listOf(it.numeratorSubject to it.numeratorRoom,it.denominatorSubject to it.denominatorRoom)}.filter{it.first.isNotBlank()}.distinctBy{normalize(it.first)}
        val tasks=rows.flatMap{r->listOf(Triple(r.numeratorSubject,r.numeratorHomework,"${days[r.dayOfWeek-1]} · ${r.pairNumber} · Числитель"),Triple(r.denominatorSubject,r.denominatorHomework,"${days[r.dayOfWeek-1]} · ${r.pairNumber} · Знаменатель"))}.filter{it.second.isNotBlank()}.map{Assignment(subject=it.first,text=it.second,scope=it.third)}
        return PlannerState(names.map{Subject(name=it.first,room=it.second)},tasks,modes)
    }
}

object BackupCodec {
    val gson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    fun encode(b:Backup):String {validate(b);return gson.toJson(b)}
    fun decode(text:String):Backup {
        require(text.isNotBlank()){ "Файл пустой. Данные приложения не изменены." }
        val root=JsonParser.parseString(text)
        require(root.isJsonObject){"Это не полная резервная копия приложения"}
        val o=root.asJsonObject
        require(o.has("rows")&&o["rows"].isJsonArray){"В копии отсутствует расписание"}
        val b=if(o.has("state")) {require(o["format"]?.asString=="raspisanie"&&o["version"]?.asInt==2){"Неизвестная версия копии"};gson.fromJson(o,Backup::class.java)} else {
            val old=gson.fromJson(o,ScheduleBackup::class.java)
            Backup(rows=old.rows,state=Planner.migrate(old.rows,old.pairModes?:emptyMap()))
        }
        validate(b);return b
    }
    fun validate(b:Backup){
        require(b.rows.size<=30&&b.rows.all{it.dayOfWeek in 1..6&&it.pairNumber in 1..5}){"Некорректные номера пар"}
        require(b.rows.distinctBy{Planner.key(it.dayOfWeek,it.pairNumber)}.size==b.rows.size){"Повторяющиеся пары"}
        require(b.rows.all{listOf(it.numeratorSubject,it.denominatorSubject,it.numeratorRoom,it.denominatorRoom,it.numeratorHomework,it.denominatorHomework).all{s->s!=null}}){"Повреждённые поля расписания"}
        LocalDate.parse(b.state.preferences.anchor)
        require(b.state.preferences.reminderMinutes in 0..60){"Неверное время напоминания"}
        require(b.state.preferences.anchorPart!=null)
        require(b.state.modes.all{(k,v)->k.matches(Regex("[1-6]-[1-5]"))&&v in listOf("SAME","SPLIT")}){"Неверные режимы пар"}
        require(b.state.subjects.all{it.id.isNotBlank()&&it.name.isNotBlank()}){"Повреждённый справочник"}
        require(b.state.subjects.distinctBy{it.id}.size==b.state.subjects.size)
        require(b.state.tasks.distinctBy{it.id}.size==b.state.tasks.size)
        b.state.tasks.forEach{require(it.id.isNotBlank()&&it.text.isNotBlank());if(it.due.isNotBlank())LocalDate.parse(it.due)}
    }
}
