package com.yourdomain.scheduleapp

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.yourdomain.scheduleapp.widget.WidgetUpdater

@HiltViewModel
class MainViewModel @Inject constructor(private val repo:ScheduleRepository,private val parser:PdfScheduleParser,private val vk:VkRepository,@ApplicationContext private val context:Context):ViewModel(){
    private val prefs=context.getSharedPreferences("pair_modes",Context.MODE_PRIVATE)
    val pairModes=MutableStateFlow((1..6).flatMap { day -> (1..5).map { pair -> "$day-$pair" to runCatching { PairMode.valueOf(prefs.getString("pair_${day}_$pair",PairMode.SPLIT.name)!!) }.getOrDefault(PairMode.SPLIT) } }.toMap())
    val selectedDay=MutableStateFlow(currentDay())
    val schedule=selectedDay.flatMapLatest(repo::day).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DailySchedule(currentDay(),(1..5).map(::SchedulePair)))
    val all=repo.all().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val images=vk.images().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val message=MutableSharedFlow<String>()
    fun edit(number:Int,part:PartType,subject:String,room:String,copy:Boolean=false)=launch { repo.edit(selectedDay.value,number,part,subject,room,copy=copy) }
    fun homework(number:Int,part:PartType,text:String)=launch { repo.homework(selectedDay.value,number,part,text) }
    fun homeworkAt(day:Int,number:Int,part:PartType,text:String)=launch { repo.homework(day,number,part,text) }
    fun importPdf(uri:Uri)=launch { repo.save(parser.parse(uri)); message.emit("Расписание импортировано") }
    fun refreshVk()=launch { vk.refresh(BuildConfig.VK_TOKEN); message.emit("Фото обновлены") }
    fun clearVk()=launch { vk.clear(); message.emit("Кэш очищен") }
    fun setPairMode(day:Int,pair:Int,mode:PairMode){ val key="$day-$pair";pairModes.value=pairModes.value+(key to mode);prefs.edit().putString("pair_${day}_$pair",mode.name).apply();WidgetUpdater.saveMode(context,day,pair,mode);WidgetUpdater.updateAll(context) }
    fun homeworkSmart(day:Int,number:Int,part:PartType,subject:String,text:String,allSame:Boolean)=launch{if(allSame)repo.homeworkForSubject(subject,text)else repo.homework(day,number,part,text)}
    fun exportBackup(uri:Uri)=launch { val backup=ScheduleBackup(rows=repo.snapshot(),pairModes=pairModes.value.mapValues{it.value.name});context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{Gson().toJson(backup,it)}?:error("Не удалось создать файл");message.emit("Резервная копия сохранена") }
    fun importBackup(uri:Uri)=launch { val backup=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{Gson().fromJson(it,ScheduleBackup::class.java)}?:error("Файл пустой");val days=(1..6).map{d->DailySchedule(d,(1..5).map{n->val r=backup.rows.firstOrNull{it.dayOfWeek==d&&it.pairNumber==n};SchedulePair(n,PairDetail(r?.numeratorSubject.orEmpty(),r?.numeratorRoom.orEmpty(),r?.numeratorHomework.orEmpty()),PairDetail(r?.denominatorSubject.orEmpty(),r?.denominatorRoom.orEmpty(),r?.denominatorHomework.orEmpty()))})};repo.save(days);val parsed=backup.pairModes.mapValues{runCatching{PairMode.valueOf(it.value)}.getOrDefault(PairMode.SPLIT)};pairModes.value=parsed;prefs.edit().also{e->parsed.forEach{(k,v)->val p=k.split("-");e.putString("pair_${p[0]}_${p[1]}",v.name);WidgetUpdater.saveMode(context,p[0].toInt(),p[1].toInt(),v)}}.apply();WidgetUpdater.updateAll(context);message.emit("Расписание восстановлено") }
    private fun launch(block:suspend()->Unit)=viewModelScope.launch { try { block() } catch(e:Exception){ message.emit(e.message?:"Ошибка") } }
    companion object { fun currentDay()=java.time.LocalDate.now().dayOfWeek.value.coerceIn(1,6) }
}

fun currentPart(date:LocalDate=LocalDate.now()):PartType{val anchor=LocalDate.of(2026,9,7);val weeks=ChronoUnit.WEEKS.between(anchor,date.with(java.time.DayOfWeek.MONDAY));return if(weeks%2L==0L)PartType.DENOMINATOR else PartType.NUMERATOR}
