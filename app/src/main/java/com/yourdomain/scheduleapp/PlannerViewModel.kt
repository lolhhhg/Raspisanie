package com.yourdomain.scheduleapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.Inject

@HiltViewModel
class PlannerViewModel @Inject constructor(private val store:PlannerStore,private val pdf:PdfScheduleParser,private val vk:VkRepository,@ApplicationContext private val context:Context):ViewModel(){
    val data=store.data
    val now=MutableStateFlow(LocalDateTime.now())
    val busy=MutableStateFlow(false)
    val error=MutableStateFlow<String?>(null)
    val toast=MutableStateFlow<String?>(null)
    val preview=MutableStateFlow<Backup?>(null)
    val images=vk.images().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init{run{store.initialize();Background.refresh(context)};viewModelScope.launch{while(isActive){now.value=LocalDateTime.now();delay(1000)}}}
    private fun run(action:suspend()->Unit){if(busy.value)return;busy.value=true;viewModelScope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message?:"Ошибка операции"}finally{busy.value=false}}}
    fun dismissError(){error.value=null}
    fun savePair(day:Int,n:Int,mode:PairMode,num:PairDetail,den:PairDetail)=run{store.change{b->
        val old=b.rows.firstOrNull{it.dayOfWeek==day&&it.pairNumber==n}?:ScheduleItemEntity(dayOfWeek=day,pairNumber=n)
        // Keep the hidden denominator when choosing one shared lesson: switching back is lossless.
        val row=old.copy(numeratorSubject=num.subject.trim(),numeratorRoom=num.room.trim(),denominatorSubject=den.subject.trim(),denominatorRoom=den.room.trim(),lastUpdated=System.currentTimeMillis())
        var subjects=b.state.subjects
        listOf(num,den).filter{it.subject.isNotBlank()}.forEach{d->if(subjects.none{Planner.normalize(it.name)==Planner.normalize(d.subject)})subjects=subjects+Subject(name=d.subject.trim(),room=d.room.trim())}
        b.copy(rows=b.rows.filterNot{it.dayOfWeek==day&&it.pairNumber==n}+row,state=b.state.copy(subjects=subjects,modes=b.state.modes+(Planner.key(day,n) to mode.name)))
    }}
    fun saveSubject(s:Subject)=run{require(s.name.isNotBlank()){"Введите название предмета"};store.change{b->
        require(b.state.subjects.none{it.id!=s.id&&Planner.normalize(it.name)==Planner.normalize(s.name)}){"Предмет уже есть"}
        val old=b.state.subjects.find{it.id==s.id};val renamed=s.copy(name=s.name.trim())
        val rows=if(old==null)b.rows else b.rows.map{r->r.copy(numeratorSubject=if(Planner.normalize(r.numeratorSubject)==Planner.normalize(old.name))renamed.name else r.numeratorSubject,denominatorSubject=if(Planner.normalize(r.denominatorSubject)==Planner.normalize(old.name))renamed.name else r.denominatorSubject)}
        val tasks=if(old==null)b.state.tasks else b.state.tasks.map{if(Planner.normalize(it.subject)==Planner.normalize(old.name))it.copy(subject=renamed.name)else it}
        b.copy(rows=rows,state=b.state.copy(subjects=b.state.subjects.filterNot{it.id==s.id}+renamed,tasks=tasks))
    }}
    fun deleteSubject(s:Subject)=run{store.change{b->b.copy(state=b.state.copy(subjects=b.state.subjects.filterNot{it.id==s.id}))};toast.value="Удалён из справочника. Пары и задания сохранены."}
    fun saveTask(t:Assignment)=run{require(t.subject.isNotBlank()&&t.text.isNotBlank()){"Выберите предмет и введите задание"};if(t.due.isNotBlank())LocalDate.parse(t.due);store.change{b->b.copy(state=b.state.copy(tasks=b.state.tasks.filterNot{it.id==t.id}+t))}}
    fun deleteTask(t:Assignment)=run{store.change{b->b.copy(state=b.state.copy(tasks=b.state.tasks.filterNot{it.id==t.id}))}}
    fun preferences(p:Preferences)=run{LocalDate.parse(p.anchor);store.change{it.copy(state=it.state.copy(preferences=p))}}
    fun export(uri:Uri)=run{val (count,size)=store.export(uri);toast.value="Проверено: $count ячеек, $size байт. Копия сохранена."}
    fun share()=run{val f=store.share();val uri=FileProvider.getUriForFile(context,context.packageName+".files",f);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Резервная копия").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    fun importJson(uri:Uri)=run{preview.value=store.read(uri)}
    fun restore()=run{val b=preview.value?:return@run;store.restore(b);preview.value=null;toast.value="Копия восстановлена. Предыдущее состояние сохранено."}
    fun rollback()=run{preview.value=store.rollback()}
    fun importPdf(uri:Uri)=run{val parsed=withContext(Dispatchers.IO){pdf.parse(uri)};val filled=parsed.flatMap{d->d.pairs.filter{it.numerator.subject.isNotBlank()||it.denominator.subject.isNotBlank()}.map{p->ScheduleItemEntity(dayOfWeek=d.dayOfWeek,pairNumber=p.pairNumber,numeratorSubject=p.numerator.subject,numeratorRoom=p.numerator.room,denominatorSubject=p.denominator.subject,denominatorRoom=p.denominator.room)}};require(filled.isNotEmpty()){"В PDF не найдено расписание. Скан без текста нужно заполнить вручную; текущее расписание не изменено."};val b=store.snapshot();preview.value=b.copy(rows=b.rows.filterNot{old->filled.any{it.dayOfWeek==old.dayOfWeek&&it.pairNumber==old.pairNumber}}+filled)}
    fun refreshVk()=run{vk.refresh(BuildConfig.VK_TOKEN)}
    fun clearVk()=run{vk.clear()}
    fun calendar(lesson:Lesson){try{context.startActivity(Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.Events.TITLE,lesson.detail.subject).putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION,lesson.detail.room).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,lesson.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,lesson.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}catch(e:Exception){error.value="На устройстве нет приложения календаря"}}
}
