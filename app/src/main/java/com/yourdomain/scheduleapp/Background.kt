package com.yourdomain.scheduleapp

import android.app.*
import android.appwidget.*
import android.content.*
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourdomain.scheduleapp.widget.NextPairWidget
import com.yourdomain.scheduleapp.widget.BellsWidget
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class DayWidget:AppWidgetProvider(){override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Background.refresh(c)}}
class HomeworkWidget:AppWidgetProvider(){override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Background.refresh(c)}}
class PlannerReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val pending=goAsync();Background.executor.execute{try{Background.handle(c,i)}finally{pending.finish()}}}}
object Background{
    val executor=Executors.newSingleThreadExecutor()
    fun refresh(context:Context){val c=context.applicationContext;executor.execute{runCatching{update(c)}}}
    private fun read(c:Context):Backup?=runCatching{BackupCodec.decode(File(c.filesDir,"widget-state.json").readText())}.getOrNull()
    private fun open(c:Context)=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun millis(t:LocalDateTime)=t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun handle(c:Context,i:Intent){
        if(i.action=="com.yourdomain.scheduleapp.TICK"){
            val scheduled=i.getLongExtra("at",0);val b=read(c)
            if(b?.state?.preferences?.notifications==true&&System.currentTimeMillis()-scheduled in 0..3600000){
                val title=i.getStringExtra("title");val text=i.getStringExtra("text");val key=i.getStringExtra("key")
                val prefs=c.getSharedPreferences("notified",0)
                if(title!=null&&text!=null&&key!=null&&prefs.getString("last",null)!=key){
                    val manager=c.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("lessons","Пары и домашняя работа",NotificationManager.IMPORTANCE_DEFAULT))
                    if(NotificationManagerCompat.from(c).areNotificationsEnabled())try{manager.notify(700,NotificationCompat.Builder(c,"lessons").setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open(c)).setAutoCancel(true).build());prefs.edit().putString("last",key).apply()}catch(_:SecurityException){}
                }
            }
        }
        update(c)
    }
    private fun update(c:Context){
        val b=read(c)?:return;val now=LocalDateTime.now();val date=now.toLocalDate();val next=Planner.next(now,b);val today=Planner.lessons(date,b);val m=AppWidgetManager.getInstance(c)
        val types=listOf(NextPairWidget::class.java,BellsWidget::class.java,DayWidget::class.java,HomeworkWidget::class.java)
        types.forEachIndexed{type,clazz->m.getAppWidgetIds(ComponentName(c,clazz)).forEach{id->
            val v=RemoteViews(c.packageName,R.layout.widget_planner)
            val title=when(type){0->if(next!=null&&now>=next.start)"СЕЙЧАС · ${next.number} ПАРА" else "СЛЕДУЮЩАЯ ПАРА";1->"ЗВОНКИ · ${Planner.days[date.dayOfWeek.value-1]}";2->"РАСПИСАНИЕ СЕГОДНЯ";else->"ДОМАШНЯЯ РАБОТА"}
            val text=when(type){0->next?.let{"${it.detail.subject}\n${it.date.format(DateTimeFormatter.ofPattern("dd.MM"))} · ${it.start.toLocalTime()}–${it.end.toLocalTime()}${if(it.detail.room.isBlank())"" else " · ауд. ${it.detail.room}"}"}?:"На ближайшие две недели занятий нет";1->if(date.dayOfWeek==DayOfWeek.SUNDAY)"Выходной" else (if(date.dayOfWeek==DayOfWeek.MONDAY)"08:00–08:45 · Разговоры о важном\n" else "")+Planner.bells(date.dayOfWeek.value).mapIndexed{i,t->"${i+1}   ${t.first} – ${t.second}"}.joinToString("\n");2->today.joinToString("\n"){"${it.start.toLocalTime()}   ${it.detail.subject}"}.ifBlank{"Сегодня пар нет"};else->b.state.tasks.filter{!it.done&&(it.due.isBlank()||it.due<=date.toString())}.take(5).joinToString("\n"){"${it.subject}: ${it.text.take(65)}"}.ifBlank{"Все задания на сегодня выполнены"}}
            v.setTextViewText(R.id.planner_widget_title,title);v.setTextViewText(R.id.planner_widget_text,text)
            v.setTextViewText(R.id.planner_widget_week,if(Planner.part(date,b.state.preferences)==PartType.NUMERATOR)"ЧИСЛИТЕЛЬ" else "ЗНАМЕНАТЕЛЬ")
            val countdown=type==0&&next!=null&&next.date==date
            v.setViewVisibility(R.id.planner_widget_timer,if(countdown)android.view.View.VISIBLE else android.view.View.GONE)
            if(countdown){val until=if(now>=next!!.start)next.end else next.start;v.setChronometer(R.id.planner_widget_timer,SystemClock.elapsedRealtime()+Duration.between(now,until).toMillis(),if(now>=next.start)"До конца %s" else "До начала %s",true);v.setChronometerCountDown(R.id.planner_widget_timer,true)}
            v.setOnClickPendingIntent(R.id.planner_widget_root,open(c));m.updateAppWidget(id,v)
        }}
        schedule(c,b,now)
    }
    private data class Event(val at:LocalDateTime,val title:String?=null,val text:String?=null,val key:String?=null)
    private fun schedule(c:Context,b:Backup,now:LocalDateTime){
        val events=mutableListOf(Event(now.toLocalDate().plusDays(1).atStartOfDay()))
        (0L..14L).forEach{d->val date=now.toLocalDate().plusDays(d);Planner.lessons(date,b).forEach{l->events+=Event(l.start);events+=Event(l.end);if(b.state.preferences.notifications)events+=Event(l.start.minusMinutes(b.state.preferences.reminderMinutes.toLong()),"Скоро ${l.detail.subject}","${l.start.toLocalTime()} · ${l.number} пара · ${l.detail.room}","${l.date}-${l.number}")}}
        if(b.state.preferences.notifications){val due=b.state.tasks.filter{!it.done&&it.due.isNotBlank()&&it.due<=now.toLocalDate().toString()};if(due.isNotEmpty())events+=Event(now.toLocalDate().atTime(18,0),"Домашняя работа · ${due.size}",due.take(4).joinToString("; "){it.subject},"hw-${now.toLocalDate()}")}
        val e=events.filter{it.at>now.plusSeconds(1)}.minByOrNull{it.at}?:return
        val intent=Intent(c,PlannerReceiver::class.java).setAction("com.yourdomain.scheduleapp.TICK").putExtra("at",millis(e.at)).putExtra("title",e.title).putExtra("text",e.text).putExtra("key",e.key)
        val pi=PendingIntent.getBroadcast(c,901,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm=c.getSystemService(AlarmManager::class.java)
        if(Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms())alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis(e.at),pi)else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis(e.at),pi)
    }
}
