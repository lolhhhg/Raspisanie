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

@HiltViewModel
class MainViewModel @Inject constructor(private val repo:ScheduleRepository,private val parser:PdfScheduleParser,private val vk:VkRepository,@ApplicationContext context:Context):ViewModel(){
    private val prefs=context.getSharedPreferences("pair_modes",Context.MODE_PRIVATE)
    val pairModes=MutableStateFlow((1..6).flatMap { day -> (1..5).map { pair -> "$day-$pair" to runCatching { PairMode.valueOf(prefs.getString("pair_${day}_$pair",PairMode.SPLIT.name)!!) }.getOrDefault(PairMode.SPLIT) } }.toMap())
    val selectedDay=MutableStateFlow(currentDay())
    val schedule=selectedDay.flatMapLatest(repo::day).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DailySchedule(currentDay(),(1..5).map(::SchedulePair)))
    val all=repo.all().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val images=vk.images().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val message=MutableSharedFlow<String>()
    fun edit(number:Int,part:PartType,subject:String,room:String,copy:Boolean=false)=launch { repo.edit(selectedDay.value,number,part,subject,room,copy=copy) }
    fun homework(number:Int,part:PartType,text:String)=launch { repo.homework(selectedDay.value,number,part,text) }
    fun homeworkAt(day:Int,number:Int,part:PartType,text:String)=launch { repo.homework(day,number,part,text) }
    fun importPdf(uri:Uri)=launch { repo.save(parser.parse(uri)); message.emit("Расписание импортировано") }
    fun refreshVk()=launch { vk.refresh(BuildConfig.VK_TOKEN); message.emit("Фото обновлены") }
    fun clearVk()=launch { vk.clear(); message.emit("Кэш очищен") }
    fun setPairMode(day:Int,pair:Int,mode:PairMode){ val key="$day-$pair";pairModes.value=pairModes.value+(key to mode);prefs.edit().putString("pair_${day}_$pair",mode.name).apply() }
    private fun launch(block:suspend()->Unit)=viewModelScope.launch { try { block() } catch(e:Exception){ message.emit(e.message?:"Ошибка") } }
    companion object { fun currentDay()=java.time.LocalDate.now().dayOfWeek.value.coerceIn(1,6) }
}
