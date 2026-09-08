package com.yourdomain.scheduleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.yourdomain.scheduleapp.*
import java.time.LocalDate
import java.time.LocalTime

object WidgetUpdater {
    private const val PREFS="widget_schedule"
    fun saveRow(context:Context,row:ScheduleItemEntity){context.getSharedPreferences(PREFS,0).edit().putString("${row.dayOfWeek}-${row.pairNumber}",Gson().toJson(row)).apply()}
    fun saveAll(context:Context,rows:List<ScheduleItemEntity>){val e=context.getSharedPreferences(PREFS,0).edit();rows.forEach{e.putString("${it.dayOfWeek}-${it.pairNumber}",Gson().toJson(it))};e.apply()}
    fun row(context:Context,day:Int,pair:Int):ScheduleItemEntity?=context.getSharedPreferences(PREFS,0).getString("$day-$pair",null)?.let{runCatching{Gson().fromJson(it,ScheduleItemEntity::class.java)}.getOrNull()}
    fun saveMode(context:Context,day:Int,pair:Int,mode:PairMode){context.getSharedPreferences(PREFS,0).edit().putString("mode-$day-$pair",mode.name).apply()}
    fun mode(context:Context,day:Int,pair:Int)=runCatching{PairMode.valueOf(context.getSharedPreferences(PREFS,0).getString("mode-$day-$pair",PairMode.SPLIT.name)!!)}.getOrDefault(PairMode.SPLIT)
    fun updateAll(context:Context){val m=AppWidgetManager.getInstance(context);listOf(NextPairWidget::class.java,BellsWidget::class.java).forEach{c->val ids=m.getAppWidgetIds(ComponentName(context,c));if(c==NextPairWidget::class.java)NextPairWidget.update(context,m,ids)else BellsWidget.update(context,m,ids)}}
}

private fun times(day:Int)=if(day==1)listOf("09:00" to "10:20","10:40" to "12:00","12:20" to "13:40","14:00" to "15:20","15:40" to "17:00")else listOf("08:00" to "09:20","09:50" to "11:10","11:40" to "13:00","13:30" to "14:50","15:20" to "16:40")
private fun click(context:Context)=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

class NextPairWidget:AppWidgetProvider(){
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Background.refresh(c)}
    companion object { fun update(c:Context,m:AppWidgetManager,ids:IntArray){
        val date=LocalDate.now();val day=date.dayOfWeek.value;val now=LocalTime.now();val slots=if(day<=6)times(day)else emptyList()
        val index=slots.indexOfFirst{now<LocalTime.parse(it.second)}
        val row=if(index>=0)WidgetUpdater.row(c,day,index+1)else null;val active=currentPart(date)
        val same=index>=0&&WidgetUpdater.mode(c,day,index+1)==PairMode.SAME
        val subject=row?.let{if(same||active==PartType.NUMERATOR)it.numeratorSubject else it.denominatorSubject}.orEmpty().ifBlank{"Нет пары"}
        val room=row?.let{if(same||active==PartType.NUMERATOR)it.numeratorRoom else it.denominatorRoom}.orEmpty()
        ids.forEach{id->val v=RemoteViews(c.packageName,R.layout.widget_next_pair);v.setTextViewText(R.id.widget_title,if(index>=0)"${index+1} ПАРА · ${slots[index].first}–${slots[index].second}" else "ПАРЫ ЗАКОНЧИЛИСЬ");v.setTextViewText(R.id.widget_subject,subject);v.setTextViewText(R.id.widget_room,if(room.isBlank())if(active==PartType.NUMERATOR)"Числитель" else "Знаменатель" else "Ауд. $room · ${if(active==PartType.NUMERATOR)"числитель" else "знаменатель"}");v.setOnClickPendingIntent(R.id.widget_root,click(c));m.updateAppWidget(id,v)}
    }}
}

class BellsWidget:AppWidgetProvider(){
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Background.refresh(c)}
    companion object { fun update(c:Context,m:AppWidgetManager,ids:IntArray){val day=LocalDate.now().dayOfWeek.value;val lines=if(day<=6)times(day).mapIndexed{i,t->"${i+1}  ${t.first}–${t.second}"}.joinToString("   ")else "Сегодня выходной";ids.forEach{id->val v=RemoteViews(c.packageName,R.layout.widget_bells);v.setTextViewText(R.id.widget_bells_title,if(day==1)"ПОНЕДЕЛЬНИК · ЗВОНКИ" else "ЗВОНКИ СЕГОДНЯ");v.setTextViewText(R.id.widget_bells_text,lines);v.setOnClickPendingIntent(R.id.widget_bells_root,click(c));m.updateAppWidget(id,v)}}}
}
