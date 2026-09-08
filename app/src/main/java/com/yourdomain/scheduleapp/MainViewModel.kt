package com.yourdomain.scheduleapp

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val repo:ScheduleRepository,private val parser:PdfScheduleParser,private val vk:VkRepository):ViewModel(){
    val selectedDay=MutableStateFlow(currentDay())
    val schedule=selectedDay.flatMapLatest(repo::day).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DailySchedule(currentDay(),(1..5).map(::SchedulePair)))
    val all=repo.all().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val images=vk.images().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val message=MutableSharedFlow<String>()
    fun edit(number:Int,part:PartType,subject:String,room:String,copy:Boolean=false)=launch { repo.edit(selectedDay.value,number,part,subject,room,copy=copy) }
    fun homework(number:Int,part:PartType,text:String)=launch { repo.homework(selectedDay.value,number,part,text) }
    fun importPdf(uri:Uri)=launch { repo.save(parser.parse(uri)); message.emit("Расписание импортировано") }
    fun refreshVk()=launch { vk.refresh(BuildConfig.VK_TOKEN); message.emit("Фото обновлены") }
    fun clearVk()=launch { vk.clear(); message.emit("Кэш очищен") }
    private fun launch(block:suspend()->Unit)=viewModelScope.launch { try { block() } catch(e:Exception){ message.emit(e.message?:"Ошибка") } }
    companion object { fun currentDay()=java.time.LocalDate.now().dayOfWeek.value.coerceIn(1,6) }
}
