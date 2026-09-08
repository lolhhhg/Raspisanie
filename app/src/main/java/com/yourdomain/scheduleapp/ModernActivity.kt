@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yourdomain.scheduleapp

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import java.time.*
import java.time.format.DateTimeFormatter

private val Accent=Color(0xFFFF6B35)
private val Palette=listOf(0xFFFF6B35,0xFF5575E7,0xFF159C91,0xFFA262CC,0xFFD95478,0xFFAA7927)
private val DateFormat=DateTimeFormatter.ofPattern("dd.MM.yyyy")
@AndroidEntryPoint class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{PlannerApp()}}
    override fun onResume(){super.onResume();Background.refresh(this)}
}
@Composable fun PlannerApp(vm:PlannerViewModel=hiltViewModel()){
    val b by vm.data.collectAsState();val busy by vm.busy.collectAsState();val now by vm.now.collectAsState()
    val dark=when(b?.state?.preferences?.theme){"Тёмная"->true;"Светлая"->false;else->isSystemInDarkTheme()}
    MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Accent,background=Color(0xFF11141D),surface=Color(0xFF1B2030))else lightColorScheme(primary=Color(0xFFD64B21),background=Color(0xFFF6F5F1),surface=Color.White),shapes=Shapes(medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp))){
        var page by remember{mutableIntStateOf(0)};val snackbar=remember{SnackbarHostState()};val toast by vm.toast.collectAsState();val error by vm.error.collectAsState();val preview by vm.preview.collectAsState()
        LaunchedEffect(toast){toast?.let{snackbar.showSnackbar(it);vm.toast.value=null}}
        Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={NavigationBar{listOf("Сегодня" to Icons.Rounded.Today,"Неделя" to Icons.Rounded.DateRange,"ДЗ" to Icons.Rounded.TaskAlt,"Предметы" to Icons.Rounded.School,"Ещё" to Icons.Rounded.Tune).forEachIndexed{i,(label,icon)->NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icon,label)},label={Text(label,maxLines=1)})}}}){padding->
            Box(Modifier.padding(padding).fillMaxSize()){
                val data=b
                if(data==null)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Text("Загружаем сохранённое расписание…")}
                else when(page){0->TodayPage(data,now,vm);1->WeekPage(data,now,vm);2->TasksPage(data,now,vm);3->SubjectsPage(data,vm);else->SettingsPage(data,vm)}
                if(busy)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.15f)).clickable{},contentAlignment=Alignment.Center){CircularProgressIndicator()}
            }
        }
        error?.let{AlertDialog(onDismissRequest=vm::dismissError,title={Text("Не удалось завершить")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissError){Text("Понятно")}})}
        preview?.let{p->AlertDialog(onDismissRequest={vm.preview.value=null},title={Text("Проверка перед восстановлением")},text={Text("${p.rows.size} ячеек расписания\n${p.state.subjects.size} предметов\n${p.state.tasks.size} заданий\n\nПример: ${p.rows.take(3).joinToString("; "){it.numeratorSubject.ifBlank{it.denominatorSubject}}}\n\nТекущие данные будут заменены. Перед этим сохранится внутренняя копия.")},confirmButton={Button(onClick=vm::restore){Text("Восстановить")}},dismissButton={TextButton(onClick={vm.preview.value=null}){Text("Отмена")}})}
    }
}
@Composable private fun PageTitle(title:String,subtitle:String="",action:(@Composable ()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};action?.invoke()}}
@Composable private fun EmptyState(text:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp)){Icon(Icons.Rounded.AutoAwesome,null,tint=Accent);Spacer(Modifier.height(8.dp));Text(text)}}}
private fun weekLabel(part:PartType)=if(part==PartType.NUMERATOR)"Числитель" else "Знаменатель"
private fun countdown(to:LocalDateTime,now:LocalDateTime):String{val s=Duration.between(now,to).seconds.coerceAtLeast(0);return if(s>=86400)"${s/86400} д" else "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60)}
@Composable private fun TodayPage(b:Backup,now:LocalDateTime,vm:PlannerViewModel){
    val date=now.toLocalDate();val lessons=Planner.lessons(date,b);val next=Planner.next(now,b);var task by remember{mutableStateOf<Assignment?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageTitle("Сегодня",Planner.days[date.dayOfWeek.value-1]+" · "+date.format(DateFormat))}
        item{Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF283B76),Color(0xFF6552A2))),RoundedCornerShape(28.dp)).padding(22.dp)){
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(weekLabel(Planner.part(date,b.state.preferences)).uppercase(),color=Color(0xFFD1D7FF),style=MaterialTheme.typography.labelLarge)
                if(next==null){Text("Впереди свободное время",fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("Добавьте занятия во вкладке «Неделя»",color=Color.White)}else{
                    val ongoing=now>=next.start
                    Text(if(ongoing)"Сейчас · ${next.number} пара" else "Следующая · ${next.date.format(DateTimeFormatter.ofPattern("dd.MM"))} · ${next.number} пара",color=Color.White)
                    Text(next.detail.subject,fontSize=26.sp,color=Color.White,fontWeight=FontWeight.Bold)
                    Text("${next.start.toLocalTime()}–${next.end.toLocalTime()}  ${next.detail.room.takeIf{it.isNotBlank()}?.let{"• ауд. $it"}.orEmpty()}",color=Color.White)
                    Text((if(ongoing)"До конца " else "До начала ")+countdown(if(ongoing)next.end else next.start,now),color=Color.White,fontSize=20.sp)
                }
            }
        }}
        if(date.dayOfWeek==DayOfWeek.MONDAY)item{Text("08:00–08:45 · Разговоры о важном",color=MaterialTheme.colorScheme.primary)}
        if(lessons.isEmpty())item{EmptyState(if(date.dayOfWeek==DayOfWeek.SUNDAY)"Воскресенье — выходной" else "На сегодня пар нет. Заполните расписание во вкладке «Неделя».")}
        items(lessons){l->val s=b.state.subjects.find{Planner.normalize(it.name)==Planner.normalize(l.detail.subject)};Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp)){Column(Modifier.width(64.dp)){Text("${l.number}",color=Color(s?.color?:Palette[0]),fontSize=28.sp,fontWeight=FontWeight.Bold);Text(l.start.toLocalTime().toString(),style=MaterialTheme.typography.labelMedium);Text(l.end.toLocalTime().toString(),style=MaterialTheme.typography.labelMedium)};Column(Modifier.weight(1f)){Text(l.detail.subject,fontWeight=FontWeight.Bold,fontSize=19.sp);if(l.detail.room.isNotBlank())Text("Аудитория ${l.detail.room}");if(!s?.teacher.isNullOrBlank())Text(s!!.teacher,style=MaterialTheme.typography.bodySmall);if(!s?.kind.isNullOrBlank())Text(s!!.kind,style=MaterialTheme.typography.labelSmall);Row{TextButton(onClick={task=Assignment(subject=l.detail.subject,due=date.toString())}){Text("Добавить ДЗ")};IconButton(onClick={vm.calendar(l)}){Icon(Icons.Rounded.EventAvailable,"В календарь")}}}}}}
        val due=b.state.tasks.filter{!it.done&&it.due.isNotBlank()&&it.due<=date.toString()}
        if(due.isNotEmpty())item{Text("Не забудьте · ${due.size} ДЗ",fontWeight=FontWeight.Bold)}
        items(due){t->AssignmentCard(t,date,{vm.saveTask(t.copy(done=!t.done))},{task=t},{vm.deleteTask(t)})}
    }
    task?.let{AssignmentDialog(it,b.state.subjects,{task=null}){vm.saveTask(it);task=null}}
}
@Composable private fun WeekPage(b:Backup,now:LocalDateTime,vm:PlannerViewModel){
    var date by remember{mutableStateOf(now.toLocalDate())};var both by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<Int?>(null)};val monday=date.with(DayOfWeek.MONDAY);val day=date.dayOfWeek.value
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("Моя неделя",weekLabel(Planner.part(date,b.state.preferences)),{TextButton(onClick={date=now.toLocalDate()}){Text("Сегодня")}})}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={date=date.minusWeeks(1)}){Icon(Icons.Rounded.ChevronLeft,"Предыдущая неделя")};Text("${monday.format(DateTimeFormatter.ofPattern("dd.MM"))} — ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM"))}",Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton(onClick={date=date.plusWeeks(1)}){Icon(Icons.Rounded.ChevronRight,"Следующая неделя")}}}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС").forEachIndexed{i,label->FilterChip(day==i+1,{date=monday.plusDays(i.toLong())},label={Text(label)})}}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Показать обе недели",Modifier.weight(1f));Switch(both,{both=it})}}
        if(day==7)item{EmptyState("Воскресенье — выходной")}else{
            if(day==1)item{Text("08:00–08:45 · Разговоры о важном")}
            items((1..5).toList()){n->val row=b.rows.find{it.dayOfWeek==day&&it.pairNumber==n}?:ScheduleItemEntity(dayOfWeek=day,pairNumber=n);val same=b.state.modes[Planner.key(day,n)]=="SAME";val part=Planner.part(date,b.state.preferences);val parts=if(same)listOf(PartType.NUMERATOR)else if(both)PartType.values().toList()else listOf(part);val time=Planner.bells(day)[n-1]
                Card(Modifier.fillMaxWidth().clickable{editing=n}){Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("$n пара · ${time.first}–${time.second}",fontWeight=FontWeight.Bold);Icon(Icons.Rounded.Edit,"Редактировать",Modifier.size(20.dp),tint=Accent)};Spacer(Modifier.height(10.dp));parts.forEach{p->val d=Planner.detail(row,p);Text(if(same)"КАЖДУЮ НЕДЕЛЮ" else weekLabel(p).uppercase(),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall);Text(d.subject.ifBlank{"Нет пары"},fontSize=18.sp,fontWeight=FontWeight.Medium);if(d.room.isNotBlank())Text("Ауд. ${d.room}",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(8.dp))}}}
            }
        }
    }
    editing?.let{n->val row=b.rows.find{it.dayOfWeek==day&&it.pairNumber==n}?:ScheduleItemEntity(dayOfWeek=day,pairNumber=n);EditSlotDialog(row,b.state.modes[Planner.key(day,n)]=="SAME",b.state.subjects,{editing=null}){mode,num,den->vm.savePair(day,n,mode,num,den);editing=null}}
}
@Composable private fun EditSlotDialog(row:ScheduleItemEntity,sameInitially:Boolean,subjects:List<Subject>,close:()->Unit,save:(PairMode,PairDetail,PairDetail)->Unit){
    var same by remember{mutableStateOf(sameInitially)};var num by remember{mutableStateOf(Planner.detail(row,PartType.NUMERATOR))};var den by remember{mutableStateOf(Planner.detail(row,PartType.DENOMINATOR))}
    AlertDialog(onDismissRequest=close,title={Text("${row.pairNumber} пара · ${Planner.days[row.dayOfWeek-1]}")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        SingleChoiceSegmentedButtonRow{SegmentedButton(same,{same=true},SegmentedButtonDefaults.itemShape(0,2)){Text("Одна")};SegmentedButton(!same,{same=false},SegmentedButtonDefaults.itemShape(1,2)){Text("Числ. / Знам.")}}
        Text(if(same)"Предмет каждую неделю. Другая половина сохранится скрытой." else "Пустой предмет означает отсутствие пары.",style=MaterialTheme.typography.bodySmall)
        SubjectFields(if(same)"Каждую неделю" else "Числитель",num,subjects){num=it};if(!same)SubjectFields("Знаменатель",den,subjects){den=it}
    }},confirmButton={Button(onClick={save(if(same)PairMode.SAME else PairMode.SPLIT,num,den)}){Text("Сохранить")}},dismissButton={TextButton(onClick=close){Text("Отмена")}})
}
@Composable private fun SubjectFields(title:String,d:PairDetail,subjects:List<Subject>,change:(PairDetail)->Unit){var pick by remember{mutableStateOf(false)};Text(title,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
    OutlinedTextField(d.subject,{change(d.copy(subject=it))},label={Text("Предмет")},modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={pick=true}){Icon(Icons.Rounded.ArrowDropDown,"Выбрать предмет")}});OutlinedTextField(d.room,{change(d.copy(room=it))},label={Text("Аудитория")},modifier=Modifier.fillMaxWidth());Row{TextButton(onClick={pick=true}){Text("Из справочника")};TextButton(onClick={change(d.copy(subject="",room=""))}){Text("Нет пары")}}
    if(pick)SubjectPicker(subjects,{pick=false}){change(d.copy(subject=it.name,room=it.room));pick=false}
}
@Composable private fun SubjectPicker(subjects:List<Subject>,close:()->Unit,pick:(Subject)->Unit){var search by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("Выбор предмета")},text={Column{OutlinedTextField(search,{search=it},label={Text("Поиск")});LazyColumn(Modifier.heightIn(max=320.dp)){items(subjects.filter{it.name.contains(search,true)},key={it.id}){s->ListItem(headlineContent={Text(s.name)},supportingContent={Text(s.room)},modifier=Modifier.clickable{pick(s)})};if(subjects.isEmpty())item{Text("Добавьте предмет во вкладке «Предметы» или впишите вручную.")}}}},confirmButton={TextButton(onClick=close){Text("Закрыть")}})}
@Composable private fun SubjectsPage(b:Backup,vm:PlannerViewModel){var edit by remember{mutableStateOf<Subject?>(null)};var search by remember{mutableStateOf("")};var deleting by remember{mutableStateOf<Subject?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("Предметы","Один раз добавить — быстро выбирать",{IconButton(onClick={edit=Subject()}){Icon(Icons.Rounded.Add,"Добавить предмет")}})};item{OutlinedTextField(search,{search=it},label={Text("Найти предмет")},modifier=Modifier.fillMaxWidth())}
        items(b.state.subjects.filter{it.name.contains(search,true)},key={it.id}){s->Card(Modifier.fillMaxWidth().clickable{edit=s}){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color(s.color).copy(alpha=.15f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Text(s.shortName.ifBlank{s.name.take(2)},color=Color(s.color),fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(s.name,fontWeight=FontWeight.Bold);Text(listOf(s.room,s.teacher,s.kind).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)};IconButton(onClick={deleting=s}){Icon(Icons.Rounded.DeleteOutline,"Удалить из справочника")}}}};if(b.state.subjects.isEmpty())item{EmptyState("Нажмите +, чтобы создать первый предмет.")}
    }
    edit?.let{SubjectEditDialog(it,{edit=null}){vm.saveSubject(it);edit=null}}
    deleting?.let{s->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить «${s.name}» из справочника?")},text={Text("Заполненные пары и задания сохранятся.")},confirmButton={TextButton(onClick={vm.deleteSubject(s);deleting=null}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
@Composable private fun SubjectEditDialog(initial:Subject,close:()->Unit,save:(Subject)->Unit){var s by remember{mutableStateOf(initial)};AlertDialog(onDismissRequest=close,title={Text("Предмет")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedTextField(s.name,{s=s.copy(name=it)},label={Text("Название")});OutlinedTextField(s.shortName,{s=s.copy(shortName=it.take(5))},label={Text("Короткое название")});OutlinedTextField(s.room,{s=s.copy(room=it)},label={Text("Аудитория по умолчанию")});OutlinedTextField(s.teacher,{s=s.copy(teacher=it)},label={Text("Преподаватель / заметка")});OutlinedTextField(s.kind,{s=s.copy(kind=it)},label={Text("Тип: лекция, практика…")});FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Palette.forEach{c->Box(Modifier.size(34.dp).background(Color(c),RoundedCornerShape(12.dp)).clickable{s=s.copy(color=c)},contentAlignment=Alignment.Center){if(s.color==c)Icon(Icons.Rounded.Check,null,tint=Color.White)}}};Text("Переименование обновит название в расписании и ДЗ.",style=MaterialTheme.typography.bodySmall)
}},confirmButton={Button(enabled=s.name.isNotBlank(),onClick={save(s)}){Text("Сохранить")}},dismissButton={TextButton(onClick=close){Text("Отмена")}})}
@Composable private fun TasksPage(b:Backup,now:LocalDateTime,vm:PlannerViewModel){var edit by remember{mutableStateOf<Assignment?>(null)};var filter by remember{mutableStateOf("Все")};var search by remember{mutableStateOf("")};var deleting by remember{mutableStateOf<Assignment?>(null)};val today=now.toLocalDate()
    val tasks=b.state.tasks.filter{t->(t.text.contains(search,true)||t.subject.contains(search,true))&&when(filter){"Готово"->t.done;"Сегодня"->!t.done&&t.due==today.toString();"Завтра"->!t.done&&t.due==today.plusDays(1).toString();"Неделя"->!t.done&&t.due.isNotBlank()&&t.due<=today.plusDays(7).toString();else->!t.done}}.sortedWith(compareBy<Assignment>{it.due.ifBlank{"9999"}}.thenBy{it.subject})
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("Домашняя работа","${b.state.tasks.count{!it.done}} заданий в работе",{IconButton(onClick={edit=Assignment()}){Icon(Icons.Rounded.Add,"Добавить задание")}})};item{OutlinedTextField(search,{search=it},label={Text("Предмет или текст задания")},modifier=Modifier.fillMaxWidth())};item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Все","Сегодня","Завтра","Неделя","Готово").forEach{f->FilterChip(filter==f,{filter=f},label={Text(f)})}}};if(tasks.isEmpty())item{EmptyState("Здесь пока нет заданий")};items(tasks,key={it.id}){t->AssignmentCard(t,today,{vm.saveTask(t.copy(done=!t.done))},{edit=t},{deleting=t})}
    }
    edit?.let{AssignmentDialog(it,b.state.subjects,{edit=null}){vm.saveTask(it);edit=null}}
    deleting?.let{t->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить задание?")},text={Text(t.text)},confirmButton={TextButton(onClick={vm.deleteTask(t);deleting=null}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
@Composable private fun AssignmentCard(t:Assignment,today:LocalDate,toggle:()->Unit,edit:()->Unit,delete:()->Unit){val overdue=!t.done&&t.due.isNotBlank()&&t.due<today.toString();Card(Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp)){Checkbox(t.done,{toggle()});Column(Modifier.weight(1f)){Text(t.subject,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(t.text);if(t.due.isNotBlank())Text((if(overdue)"Просрочено · " else "До ")+LocalDate.parse(t.due).format(DateFormat),color=if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);Text(t.scope,style=MaterialTheme.typography.labelSmall);Row{TextButton(onClick=edit){Text("Изменить")};IconButton(onClick=delete){Icon(Icons.Rounded.DeleteOutline,"Удалить")}}}}}}
@Composable private fun AssignmentDialog(initial:Assignment,subjects:List<Subject>,close:()->Unit,save:(Assignment)->Unit){var t by remember{mutableStateOf(initial)};var picker by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=close,title={Text("Домашнее задание")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedTextField(t.subject,{t=t.copy(subject=it)},label={Text("Предмет")},trailingIcon={IconButton(onClick={picker=true}){Icon(Icons.Rounded.ArrowDropDown,"Выбрать")}});OutlinedTextField(t.text,{t=t.copy(text=it)},label={Text("Что нужно сделать?")},minLines=3);DateChoice("Срок",t.due){t=t.copy(due=it)};if(t.due.isNotBlank())TextButton(onClick={t=t.copy(due="")}){Text("Без срока")};Text("Одно задание для всех пар предмета — без дублирования.",style=MaterialTheme.typography.bodySmall)
}},confirmButton={Button(enabled=t.subject.isNotBlank()&&t.text.isNotBlank(),onClick={save(t)}){Text("Сохранить")}},dismissButton={TextButton(onClick=close){Text("Отмена")}});if(picker)SubjectPicker(subjects,{picker=false}){t=t.copy(subject=it.name);picker=false}}
@Composable private fun DateChoice(label:String,value:String,change:(String)->Unit){val ctx=LocalContext.current;OutlinedButton(onClick={val d=runCatching{LocalDate.parse(value)}.getOrDefault(LocalDate.now());DatePickerDialog(ctx,{_,y,m,day->change(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()}){Icon(Icons.Rounded.CalendarMonth,null);Text("$label: "+if(value.isBlank())"не выбран" else LocalDate.parse(value).format(DateFormat))}}
@Composable private fun SettingsPage(b:Backup,vm:PlannerViewModel){val ctx=LocalContext.current;val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)};val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importJson)};val pdf=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importPdf)};val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){Background.refresh(ctx)};val p=b.state.preferences;var vk by remember{mutableStateOf(false)}
    val exact=if(Build.VERSION.SDK_INT>=31)ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()else true;val notifications=androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageTitle("Настройки","Расписание · ${BuildConfig.VERSION_NAME}")};item{Text("Резервные копии",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("В базе: ${b.rows.size} ячеек · ${b.state.subjects.size} предметов · ${b.state.tasks.size} ДЗ. Сохраняется до 20 предыдущих состояний.")}
        item{SettingsAction(Icons.Rounded.SaveAlt,"Сохранить и проверить JSON"){export.launch("raspisanie-${LocalDate.now()}.json")};SettingsAction(Icons.Rounded.Share,"Поделиться резервной копией",vm::share);SettingsAction(Icons.Rounded.Restore,"Восстановить из JSON"){import.launch(arrayOf("application/json","text/plain","application/octet-stream"))};SettingsAction(Icons.Rounded.History,"Предыдущее состояние",vm::rollback);SettingsAction(Icons.Rounded.PictureAsPdf,"Импорт PDF с проверкой"){pdf.launch(arrayOf("application/pdf"))};Text("Внутренняя копия не защищает от удаления приложения. Сохраните JSON вне приложения и пришлите его мне для встраивания расписания.",style=MaterialTheme.typography.bodySmall)}
        item{HorizontalDivider();Text("Недели",fontSize=20.sp,fontWeight=FontWeight.Bold);DateChoice("Опорная дата",p.anchor){vm.preferences(p.copy(anchor=it))};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PartType.values().forEach{part->FilterChip(p.anchorPart==part,{vm.preferences(p.copy(anchorPart=part))},label={Text(weekLabel(part))})};Text("Укажите тип недели опорной даты. Смена — в понедельник.",style=MaterialTheme.typography.bodySmall)}
        item{HorizontalDivider();Text("Оформление",fontSize=20.sp,fontWeight=FontWeight.Bold);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Системная","Светлая","Тёмная").forEach{t->FilterChip(p.theme==t,{vm.preferences(p.copy(theme=t))},label={Text(t)})}}}
        item{HorizontalDivider();Row(verticalAlignment=Alignment.CenterVertically){Text("Напоминания",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);Switch(p.notifications,{on->vm.preferences(p.copy(notifications=on));if(on&&Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)})};Text("О паре заранее и о невыполненном ДЗ на сегодня в 18:00.");FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(5,10,15).forEach{n->FilterChip(p.reminderMinutes==n,{vm.preferences(p.copy(reminderMinutes=n))},label={Text("$n мин")})}};Text("Уведомления: ${if(notifications)"разрешены" else "запрещены"}. Точные сигналы: ${if(exact)"разрешены" else "возможна задержка"}.",style=MaterialTheme.typography.bodySmall);if(!notifications)SettingsAction(Icons.Rounded.Notifications,"Разрешения уведомлений"){ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))};if(Build.VERSION.SDK_INT>=31&&!exact)SettingsAction(Icons.Rounded.Alarm,"Разрешить точное время"){ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:${ctx.packageName}")))}}
        item{HorizontalDivider();Text("Виджеты",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Зажмите свободное место на главном экране → Виджеты → Расписание. Следующая пара, звонки, день и домашка. Нажатие обновляет виджет и открывает приложение.")};item{SettingsAction(Icons.Rounded.PhotoLibrary,"Фото расписания VK"){vk=true}}
    }
    if(vk)Dialog(onDismissRequest={vk=false}){Surface(shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(16.dp).fillMaxHeight(.85f)){Row{TextButton(onClick={vk=false}){Text("Назад")};TextButton(onClick=vm::refreshVk){Text("Обновить")};TextButton(onClick=vm::clearVk){Text("Очистить")}};val photos by vm.images.collectAsState();if(photos.isEmpty())Text("Для загрузки фото VK нужен токен. Можно открыть тему в браузере.");TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://vk.ru/topic-191933238_53045814")))}){Text("Открыть тему VK")};LazyColumn{items(photos){p->AsyncImage(p.url,p.caption,Modifier.fillMaxWidth().height(250.dp));Text(p.postDate)}}}}}
}
@Composable private fun SettingsAction(icon:ImageVector,text:String,click:()->Unit){OutlinedButton(onClick=click,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(14.dp)){Icon(icon,null);Spacer(Modifier.width(10.dp));Text(text,Modifier.weight(1f))}}
