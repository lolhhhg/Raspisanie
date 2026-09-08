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
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContent { ScheduleTheme { App() } } }
}

private val Orange=Color(0xFFFF6B00)
@Composable fun ScheduleTheme(content:@Composable()->Unit){ MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme(primary=Orange) else lightColorScheme(primary=Orange),content=content) }

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
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importPdf)}
    val createJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{ctx.contentResolver.openOutputStream(it)?.bufferedWriter()?.use{out->out.write(toJson(vm.all.value))}}}
    var edit by remember{mutableStateOf<Pair<Int,PartType>?>(null)}; var hw by remember{mutableStateOf<Pair<Int,PartType>?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("РАСПИСАНИЕ")},actions={TextButton(onClick={vm.selectedDay.value=MainViewModel.currentDay()}){Text("Сегодня")};TextButton(onClick={createJson.launch("raspisanie.json")}){Text("JSON")}})},floatingActionButton={FloatingActionButton(onClick={picker.launch(arrayOf("application/pdf"))}){Text("PDF")}}){pad->
        Column(Modifier.padding(pad)){
            ScrollableTabRow(selectedTabIndex=day-1,edgePadding=0.dp){ listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ").forEachIndexed{i,n->Tab(selected=day==i+1,onClick={vm.selectedDay.value=i+1},text={Text(n)})} }
            if(schedule.pairs.all{it.numerator.subject.isBlank()&&it.denominator.subject.isBlank()}) Text("Загрузите PDF или добавьте пары вручную",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(schedule.pairs){p->PairCard(p,{edit=it},{hw=it})}}
        }
    }
    edit?.let{(n,part)-> val p=schedule.pairs[n-1]; EditDialog(n,part,if(part==PartType.NUMERATOR)p.numerator else p.denominator,{edit=null}){s,r,c->vm.edit(n,part,s,r,c);edit=null}}
    hw?.let{(n,part)->val p=schedule.pairs[n-1];val d=if(part==PartType.NUMERATOR)p.numerator else p.denominator; HomeworkDialog(d.subject,d.homework,{hw=null}){vm.homework(n,part,it);hw=null}}
}

@Composable fun PairCard(p:SchedulePair,onEdit:(Pair<Int,PartType>)->Unit,onHw:(Pair<Int,PartType>)->Unit){
    Card(Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).background(Orange,CircleShape),contentAlignment=Alignment.Center){Text("${p.pairNumber}",color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(10.dp));PartColumn("ЧИСЛИТЕЛЬ",p.numerator,{onEdit(p.pairNumber to PartType.NUMERATOR)},{onHw(p.pairNumber to PartType.NUMERATOR)},Modifier.weight(1f));VerticalDivider(Modifier.height(100.dp).padding(horizontal=6.dp));PartColumn("ЗНАМЕНАТЕЛЬ",p.denominator,{onEdit(p.pairNumber to PartType.DENOMINATOR)},{onHw(p.pairNumber to PartType.DENOMINATOR)},Modifier.weight(1f))}}
}
@Composable fun PartColumn(title:String,d:PairDetail,edit:()->Unit,hw:()->Unit,modifier:Modifier){Column(modifier){Text(title,style=MaterialTheme.typography.labelSmall,color=Orange);Text(d.subject.ifBlank{"(нет)"},fontWeight=FontWeight.SemiBold,maxLines=2);if(d.room.isNotBlank())Text("Ауд. ${d.room}",style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick=edit,contentPadding=PaddingValues(2.dp)){Text(if(d.subject.isBlank())"+ Добавить" else "✏ Ред.")};if(d.subject.isNotBlank())TextButton(onClick=hw,contentPadding=PaddingValues(2.dp)){Text("📝 ДЗ")}};if(d.homework.isNotBlank())Text(d.homework,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}

@Composable fun EditDialog(number:Int,initial:PartType,detail:PairDetail,cancel:()->Unit,save:(String,String,Boolean)->Unit){var subject by remember{mutableStateOf(detail.subject)};var room by remember{mutableStateOf(detail.room)};var copy by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=cancel,title={Text("Редактировать пару №$number")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(initial==PartType.NUMERATOR)"Числитель" else "Знаменатель",color=Orange);OutlinedTextField(subject,{subject=it},label={Text("Предмет")});OutlinedTextField(room,{room=it},label={Text("Аудитория")});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(copy,{copy=it});Text("Скопировать в другую часть")}}},confirmButton={Button(onClick={save(subject,room,copy)}){Text("Сохранить")}},dismissButton={TextButton(onClick=cancel){Text("Отмена")}})}
@Composable fun HomeworkDialog(subject:String,old:String,cancel:()->Unit,save:(String)->Unit){var text by remember{mutableStateOf(old)};AlertDialog(onDismissRequest=cancel,title={Text("Домашнее задание: $subject")},text={OutlinedTextField(text,{text=it},label={Text("Задание")},minLines=4)},confirmButton={Button(onClick={save(text)}){Text("Сохранить")}},dismissButton={Row{TextButton(onClick={text=""}){Text("Очистить")};TextButton(onClick=cancel){Text("Отмена")}}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeworkScreen(vm:MainViewModel){val rows by vm.all.collectAsState();Scaffold(topBar={TopAppBar(title={Text("ДОМАШНЕЕ ЗАДАНИЕ")})}){pad->LazyColumn(Modifier.padding(pad),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){val dayNames=listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота");(1..6).forEach{d->val dayRows=rows.filter{it.dayOfWeek==d};val tasks=dayRows.flatMap{r->listOf(Triple(r.numeratorSubject,"Числ.",r.numeratorHomework),Triple(r.denominatorSubject,"Знам.",r.denominatorHomework))}.filter{it.third.isNotBlank()};if(tasks.isNotEmpty()){item{Text(dayNames[d-1],fontWeight=FontWeight.Bold,color=Orange)};items(tasks){t->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(t.first,fontWeight=FontWeight.Bold);Text(t.second,color=Orange);Text(t.third)}}}}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VkScreen(vm:MainViewModel){val images by vm.images.collectAsState();var full by remember{mutableStateOf<String?>(null)};Scaffold(topBar={TopAppBar(title={Text("ЕЖЕДНЕВНОЕ РАСПИСАНИЕ")},actions={TextButton(onClick=vm::refreshVk){Text("↻")};TextButton(onClick=vm::clearVk){Text("🗑")}})}){pad->if(images.isEmpty())Box(Modifier.fillMaxSize().padding(pad),contentAlignment=Alignment.Center){Text("Фото пока нет. Нажмите ↻") } else LazyVerticalGrid(GridCells.Fixed(2),Modifier.padding(pad),contentPadding=PaddingValues(8.dp)){items(images){img->Card(Modifier.padding(4.dp).clickable{full=img.url}){Box{AsyncImage(img.url,null,Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Crop);Text(img.postDate,Modifier.align(Alignment.BottomStart).background(Color.Black.copy(.55f)).padding(6.dp),color=Color.White)}}}}};full?.let{url->androidx.compose.ui.window.Dialog(onDismissRequest={full=null}){AsyncImage(url,null,Modifier.fillMaxSize().clickable{full=null},contentScale=ContentScale.Fit)}}}

private fun toJson(rows:List<ScheduleItemEntity>)=rows.joinToString(",","[\n","\n]"){"  {\"day\":${it.dayOfWeek},\"pair\":${it.pairNumber},\"numerator\":\"${it.numeratorSubject.replace("\"","\\\"")}\",\"denominator\":\"${it.denominatorSubject.replace("\"","\\\"")}\"}"}
