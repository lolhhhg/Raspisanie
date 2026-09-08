package com.yourdomain.scheduleapp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import com.yourdomain.scheduleapp.widget.WidgetUpdater

class ScheduleRepository @Inject constructor(private val dao: ScheduleDao,@ApplicationContext private val context:Context) {
    fun day(day: Int): Flow<DailySchedule> = dao.observeDay(day).map { rows ->
        DailySchedule(day, (1..5).map { n -> rows.firstOrNull { it.pairNumber == n }.toPair(n) })
    }
    fun all(): Flow<List<ScheduleItemEntity>> = dao.observeAll()
    suspend fun edit(day: Int, number: Int, part: PartType, subject: String, room: String, homework: String? = null, copy: Boolean = false) = try {
        val old = dao.get(day, number) ?: ScheduleItemEntity(dayOfWeek = day, pairNumber = number)
        var next = if (part == PartType.NUMERATOR) old.copy(numeratorSubject=subject, numeratorRoom=room, numeratorHomework=homework ?: old.numeratorHomework)
        else old.copy(denominatorSubject=subject, denominatorRoom=room, denominatorHomework=homework ?: old.denominatorHomework)
        if (copy) next = next.copy(numeratorSubject=subject, numeratorRoom=room, denominatorSubject=subject, denominatorRoom=room)
        dao.insertAll(listOf(next.copy(id=old.id,lastUpdated=System.currentTimeMillis())))
        WidgetUpdater.saveRow(context,next.copy(id=old.id));WidgetUpdater.updateAll(context)
    } catch (e: Exception) { Log.e("ScheduleApp", "edit", e); throw e }
    suspend fun homework(day:Int, number:Int, part:PartType, text:String) {
        val old=dao.get(day,number) ?: ScheduleItemEntity(dayOfWeek=day,pairNumber=number)
        edit(day,number,part,if(part==PartType.NUMERATOR) old.numeratorSubject else old.denominatorSubject,if(part==PartType.NUMERATOR) old.numeratorRoom else old.denominatorRoom,text)
    }
    suspend fun homeworkForSubject(subject:String,text:String){try{
        if(subject.isNotBlank()){val updated=dao.getAllNow().map{r->r.copy(numeratorHomework=if(r.numeratorSubject.equals(subject,true))text else r.numeratorHomework,denominatorHomework=if(r.denominatorSubject.equals(subject,true))text else r.denominatorHomework,lastUpdated=System.currentTimeMillis())};dao.insertAll(updated);WidgetUpdater.saveAll(context,updated);WidgetUpdater.updateAll(context)}
    }catch(e:Exception){Log.e("ScheduleApp","homeworkForSubject",e);throw e}}
    suspend fun save(days: List<DailySchedule>) = try {
        val items=days.flatMap { d -> d.pairs.map { p -> ScheduleItemEntity(dayOfWeek=d.dayOfWeek,pairNumber=p.pairNumber,numeratorSubject=p.numerator.subject,denominatorSubject=p.denominator.subject,numeratorRoom=p.numerator.room,denominatorRoom=p.denominator.room,numeratorHomework=p.numerator.homework,denominatorHomework=p.denominator.homework) } }
        dao.clearAll(); dao.insertAll(items);WidgetUpdater.saveAll(context,items);WidgetUpdater.updateAll(context)
    } catch(e:Exception){ Log.e("ScheduleApp","save",e); throw e }
    private fun ScheduleItemEntity?.toPair(n:Int)=this?.let { SchedulePair(n,PairDetail(it.numeratorSubject,it.numeratorRoom,it.numeratorHomework),PairDetail(it.denominatorSubject,it.denominatorRoom,it.denominatorHomework)) } ?: SchedulePair(n)
    suspend fun snapshot()=dao.getAllNow()
}

class PdfScheduleParser @Inject constructor(@ApplicationContext private val context: Context) {
    fun parse(uri: Uri): List<DailySchedule> = try {
        val text=context.contentResolver.openInputStream(uri)!!.use { PDDocument.load(it).use { doc -> PDFTextStripper().getText(doc) } }
        val names=listOf("понедельник","вторник","среда","четверг","пятница","суббота")
        val buckets=Array(6){ mutableListOf<String>() }; var day=-1
        text.lines().forEach { raw -> val line=raw.trim(); val found=names.indexOfFirst { line.lowercase().contains(it) }; if(found>=0) day=found else if(day>=0 && line.isNotBlank()) buckets[day]+=line }
        buckets.mapIndexed { idx, lines ->
            val pairs=(1..5).map { n ->
                val line=lines.firstOrNull { it.matches(Regex("^$n(?:[.)]|\\s).*")) }?.replaceFirst(Regex("^$n(?:[.)]|\\s)+"),"").orEmpty()
                val parts=line.split(Regex("\\s{2,}|\\||;"),limit=2).map(String::trim)
                SchedulePair(n,PairDetail(parts.getOrElse(0){""}),PairDetail(parts.getOrElse(1){""}))
            }; DailySchedule(idx+1,pairs)
        }
    } catch(e:Exception){ Log.e("ScheduleApp","pdf",e); throw e }
}

class VkRepository @Inject constructor(private val api:VkApiService,private val dao:VkImageDao){
    fun images()=dao.observeAll()
    suspend fun refresh(token:String){
        if(token.isBlank()) error("Добавьте VK-токен в настройки сборки")
        val body=api.comments(token=token); body.error?.let { error(it.error_msg) }
        val fmt=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault())
        val rows=body.response?.items.orEmpty().flatMap { c -> c.attachments.filter { it.type=="photo" }.mapNotNull { a -> a.photo?.sizes?.maxByOrNull { it.width*it.height }?.url?.let { VkPostImageEntity(it,fmt.format(Date(c.date*1000)),c.text) } } }
        dao.clearAll(); dao.insertImages(rows)
    }
    suspend fun clear()=dao.clearAll()
}
