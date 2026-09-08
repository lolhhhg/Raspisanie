package com.yourdomain.scheduleapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LegacyMainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContent { ScheduleTheme { App() } } }
}

private val Orange=Color(0xFFFF6B00)
@Composable fun ScheduleTheme(content: @Composable () -> Unit){ MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme(primary=Orange) else lightColorScheme(primary=Orange),content=content) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm:MainViewModel= hiltViewModel()){
    var page by remember { mutableIntStateOf(0) }; val snackbar=remember{SnackbarHostState()}; val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){ vm.message.collect{ snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={NavigationBar{ listOf("📅 Расписание","📝 Домашнее","🖼 VK Фото").forEachIndexed{i,t->NavigationBarItem(selected=page==i,onClick={page=i},icon={Text(t.take(2))},label={Text(t.substringAfter(" "))})}}}){ pad ->
        Box(Modifier.padding(pad)){ when(page){0->ScheduleScreen(vm);1->HomeworkScreen(vm);else->VkScreen(vm)} }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ScheduleScreen(vm:MainViewModel){
    val day by vm.selectedDay.collectAsState(); val schedule by vm.schedule.collectAsState(); val ctx=LocalContext.current
    val modes by vm.pairModes.collectAsState()
    val allRows by vm.all.collectAsState();val knownSubjects=allRows.flatMap{listOf(it.numeratorSubject,it.denominatorSubject)}.filter{it.isNotBlank()}.distinct().sorted()
    val activePart=currentPart();var showBoth by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importPdf)}
    val createJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportBackup)}
    val importJson=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importBackup)}
    var edit by remember{mutableStateOf<Pair<Int,PartType>?>(null)}; var hw by remember{mutableStateOf<Pair<Int,PartType>?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("РАСПИСАНИЕ")},actions={TextButton(onClick={importJson.launch(arrayOf("application/json"))}){Text("Восст.")};TextButton(onClick={createJson.launch("raspisanie-backup.json")}){Text("Копия")}})},floatingActionButton={FloatingActionButton(onClick={picker.launch(arrayOf("application/pdf"))}){Text("PDF")}}){pad->
        Column(Modifier.padding(pad)){
            ScrollableTabRow(selectedTabIndex=day-1,edgePadding=0.dp){ listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ").forEachIndexed{i,n->Tab(selected=day==i+1,onClick={vm.selectedDay.value=i+1},text={Text(n)})} }
            BellSchedule(day)
            Card(Modifier.fillMaxWidth().padding(horizontal=12.dp),colors=CardDefaults.cardColors(containerColor=Orange.copy(alpha=.14f))){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Сейчас ${if(activePart==PartType.DENOMINATOR)"ЗНАМЕНАТЕЛЬ" else "ЧИСЛИТЕЛЬ"}",fontWeight=FontWeight.Bold,color=Orange);Text("Неделя меняется автоматически по понедельникам",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={showBoth=!showBoth}){Text(if(showBoth)"Текущая" else "Обе")}}}
            if(schedule.pairs.all{it.numerator.subject.isBlank()&&it.denominator.subject.isBlank()}) Text("Загрузите PDF или добавьте пары вручную",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(schedule.pairs){p->val pairMode=modes["$day-${p.pairNumber}"]?:PairMode.SPLIT;PairCard(p,pairMode,day,activePart,showBoth,{vm.setPairMode(day,p.pairNumber,it)},{edit=it},{hw=it})}}
        }
    }
    edit?.let{(n,part)-> val p=schedule.pairs[n-1];val pairMode=modes["$day-$n"]?:PairMode.SPLIT; EditDialog(n,part,if(part==PartType.NUMERATOR)p.numerator else p.denominator,knownSubjects,{edit=null}){s,r,c->vm.edit(n,part,s,r,c||pairMode==PairMode.SAME);edit=null}}
    hw?.let{(n,part)->val p=schedule.pairs[n-1];val d=if(part==PartType.NUMERATOR)p.numerator else p.denominator; HomeworkDialog(d.subject,d.homework,{hw=null}){text,allSame->vm.homeworkSmart(day,n,part,d.subject,text,allSame);hw=null}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PairCard(p:SchedulePair,mode:PairMode,day:Int,active:PartType,showBoth:Boolean,onMode:(PairMode)->Unit,onEdit:(Pair<Int,PartType>)->Unit,onHw:(Pair<Int,PartType>)->Unit){
    val time=bellTimes(day)[p.pairNumber-1]
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(38.dp).background(Orange,CircleShape),contentAlignment=Alignment.Center){Text("${p.pairNumber}",color=Color.White,fontWeight=FontWeight.Bold)};Text(time,style=MaterialTheme.typography.labelSmall)};Spacer(Modifier.weight(1f));SingleChoiceSegmentedButtonRow{SegmentedButton(selected=mode==PairMode.SAME,onClick={onMode(PairMode.SAME)},shape=SegmentedButtonDefaults.itemShape(0,2)){Text("Одна")};SegmentedButton(selected=mode==PairMode.SPLIT,onClick={onMode(PairMode.SPLIT)},shape=SegmentedButtonDefaults.itemShape(1,2)){Text("Ч/З")}}};Spacer(Modifier.height(8.dp));Row{if(mode==PairMode.SAME)PartColumn("ВСЕГДА",p.numerator,{onEdit(p.pairNumber to PartType.NUMERATOR)},{onHw(p.pairNumber to PartType.NUMERATOR)},Modifier.weight(1f))else if(!showBoth){val d=if(active==PartType.NUMERATOR)p.numerator else p.denominator;PartColumn(if(active==PartType.NUMERATOR)"ЧИСЛИТЕЛЬ" else "ЗНАМЕНАТЕЛЬ",d,{onEdit(p.pairNumber to active)},{onHw(p.pairNumber to active)},Modifier.weight(1f))}else{PartColumn("ЧИСЛИТЕЛЬ",p.numerator,{onEdit(p.pairNumber to PartType.NUMERATOR)},{onHw(p.pairNumber to PartType.NUMERATOR)},Modifier.weight(1f));VerticalDivider(Modifier.height(100.dp).padding(horizontal=6.dp));PartColumn("ЗНАМЕНАТЕЛЬ",p.denominator,{onEdit(p.pairNumber to PartType.DENOMINATOR)},{onHw(p.pairNumber to PartType.DENOMINATOR)},Modifier.weight(1f))}}}}
}

private fun bellTimes(day:Int)=if(day==1) listOf("09:00–10:20","10:40–12:00","12:20–13:40","14:00–15:20","15:40–17:00") else listOf("08:00–09:20","09:50–11:10","11:40–13:00","13:30–14:50","15:20–16:40")
@Composable fun BellSchedule(day:Int){val title=if(day==1)"Понедельник · разговоры о важном 08:00–08:45" else "Звонки вторник–пятница";Text(title,Modifier.fillMaxWidth().padding(8.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
@Composable fun PartColumn(title:String,d:PairDetail,edit:()->Unit,hw:()->Unit,modifier:Modifier){Column(modifier){Text(title,style=MaterialTheme.typography.labelSmall,color=Orange);Text(d.subject.ifBlank{"(нет)"},fontWeight=FontWeight.SemiBold,maxLines=2);if(d.room.isNotBlank())Text("Ауд. ${d.room}",style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick=edit,contentPadding=PaddingValues(2.dp)){Text(if(d.subject.isBlank())"+ Добавить" else "✏ Ред.")};if(d.subject.isNotBlank())TextButton(onClick=hw,contentPadding=PaddingValues(2.dp)){Text("📝 ДЗ")}};if(d.homework.isNotBlank())Text(d.homework,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EditDialog(number:Int,initial:PartType,detail:PairDetail,subjects:List<String>,cancel:()->Unit,save:(String,String,Boolean)->Unit){var subject by remember{mutableStateOf(detail.subject)};var room by remember{mutableStateOf(detail.room)};var copy by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=cancel,title={Text("Редактировать пару №$number")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(initial==PartType.NUMERATOR)"Числитель" else "Знаменатель",color=Orange);OutlinedTextField(subject,{subject=it},label={Text("Предмет")});if(subjects.isNotEmpty()){Text("Быстрый выбор",style=MaterialTheme.typography.labelMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){subjects.take(8).forEach{s->AssistChip(onClick={subject=s},label={Text(s,maxLines=1)})}}};OutlinedTextField(room,{room=it},label={Text("Аудитория")});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(copy,{copy=it});Text("Скопировать в другую часть")}}},confirmButton={Button(onClick={save(subject,room,copy)}){Text("Сохранить")}},dismissButton={TextButton(onClick=cancel){Text("Отмена")}})}
@Composable fun HomeworkDialog(subject:String,old:String,cancel:()->Unit,save:(String,Boolean)->Unit){var text by remember{mutableStateOf(old)};var allSame by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=cancel,title={Text("Домашнее задание: $subject")},text={Column{OutlinedTextField(text,{text=it},label={Text("Задание")},minLines=4);if(subject.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically){Checkbox(allSame,{allSame=it});Text("Для всех пар «$subject»",style=MaterialTheme.typography.bodySmall)}}},confirmButton={Button(onClick={save(text,allSame)}){Text("Сохранить")}},dismissButton={Row{TextButton(onClick={save("",allSame)}){Text("Очистить")};TextButton(onClick=cancel){Text("Отмена")}}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeworkScreen(vm:MainViewModel){
    val rows by vm.all.collectAsState()
    val dayNames=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота")
    var editing by remember{mutableStateOf<HwTask?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("ДОМАШНЕЕ ЗАДАНИЕ")})}){pad->
        LazyColumn(Modifier.padding(pad),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            (1..6).forEach{d->
                val dayRows=rows.filter{it.dayOfWeek==d}
                val tasks=dayRows.flatMap{r->listOf(HwTask(d,r.pairNumber,PartType.NUMERATOR,r.numeratorSubject,r.numeratorHomework),HwTask(d,r.pairNumber,PartType.DENOMINATOR,r.denominatorSubject,r.denominatorHomework))}.filter{it.text.isNotBlank()}
                if(tasks.isNotEmpty()){
                    item{Text(dayNames[d-1],fontWeight=FontWeight.Bold,color=Orange)}
                    items(tasks){t->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(t.subject.ifBlank{"Без предмета"},fontWeight=FontWeight.Bold);Text("${t.pair} пара · ${if(t.part==PartType.NUMERATOR)"Числ." else "Знам."}",color=Orange)};Text(t.text);Row{TextButton(onClick={editing=t}){Text("✏ Изменить")};TextButton(onClick={vm.homeworkAt(t.day,t.pair,t.part,"")}){Text("🗑 Удалить")}}}}}
                }
            }
        }
    }
    editing?.let{t->HomeworkDialog(t.subject,t.text,{editing=null}){text,allSame->vm.homeworkSmart(t.day,t.pair,t.part,t.subject,text,allSame);editing=null}}
}

private data class HwTask(val day:Int,val pair:Int,val part:PartType,val subject:String,val text:String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VkScreen(vm:MainViewModel){
    val images by vm.images.collectAsState();var full by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("ЕЖЕДНЕВНОЕ РАСПИСАНИЕ")},actions={TextButton(onClick=vm::refreshVk){Text("↻")};TextButton(onClick=vm::clearVk){Text("🗑")}})}){pad->
        if(images.isEmpty()) Box(Modifier.fillMaxSize().padding(pad),contentAlignment=Alignment.Center){Text("Фото пока нет. Нажмите ↻")}
        else LazyVerticalGrid(GridCells.Fixed(2),Modifier.padding(pad),contentPadding=PaddingValues(8.dp)){items(images){img->Card(Modifier.padding(4.dp).clickable{full=img.url}){Box{AsyncImage(img.url,null,Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Crop);Text(img.postDate,Modifier.align(Alignment.BottomStart).background(Color.Black.copy(.55f)).padding(6.dp),color=Color.White)}}}}
    }
    full?.let{url->androidx.compose.ui.window.Dialog(onDismissRequest={full=null}){AsyncImage(url,null,Modifier.fillMaxSize().clickable{full=null},contentScale=ContentScale.Fit)}}
}
