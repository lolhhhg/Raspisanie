package com.yourdomain.scheduleapp

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerStore @Inject constructor(private val db:AppDatabase,@ApplicationContext private val context:Context){
    private val lock=Mutex()
    val data=MutableStateFlow<Backup?>(null)
    val folder=File(context.filesDir,"backups").apply{mkdirs()}
    private fun writeAtomic(file:File,text:String){val a=AtomicFile(file);val out=a.startWrite();try{out.write(text.toByteArray(Charsets.UTF_8));a.finishWrite(out)}catch(t:Throwable){a.failWrite(out);throw t}}
    suspend fun initialize()=withContext(Dispatchers.IO){lock.withLock{
        val rows=db.scheduleDao().getAllNow()
        val saved=db.stateDao().get()
        val state=if(saved!=null)BackupCodec.gson.fromJson(saved.json,PlannerState::class.java)else{
            val prefs=context.getSharedPreferences("pair_modes",0)
            val modes=(1..6).flatMap{d->(1..5).map{n->Planner.key(d,n) to (prefs.getString("pair_${d}_$n","SPLIT")?:"SPLIT")}}.toMap()
            Planner.migrate(rows,modes)
        }
        val b=Backup(rows=rows,state=state);BackupCodec.validate(b)
        if(saved==null)db.stateDao().put(StateEntity(json=BackupCodec.gson.toJson(state)))
        data.value=b
        mirror(b)
    }}
    private fun mirror(b:Backup){writeAtomic(File(context.filesDir,"widget-state.json"),BackupCodec.encode(b))}
    suspend fun snapshot():Backup=withContext(Dispatchers.IO){lock.withLock{db.withTransaction{
        val state=db.stateDao().get()?:error("База ещё загружается")
        Backup(rows=db.scheduleDao().getAllNow(),state=BackupCodec.gson.fromJson(state.json,PlannerState::class.java)).also{BackupCodec.validate(it)}
    }}}
    suspend fun change(transform:(Backup)->Backup)=withContext(Dispatchers.IO){lock.withLock{
        val old=data.value?:error("Подождите загрузки расписания")
        val next=transform(old).copy(created=java.time.Instant.now().toString())
        BackupCodec.validate(next)
        val checkpoint=File(folder,"before-${System.currentTimeMillis()}.json")
        writeAtomic(checkpoint,BackupCodec.encode(old))
        db.withTransaction{db.scheduleDao().clearAll();db.scheduleDao().insertAll(next.rows);db.stateDao().put(StateEntity(json=BackupCodec.gson.toJson(next.state)))}
        data.value=next;mirror(next)
        writeAtomic(File(folder,"latest.json"),BackupCodec.encode(next))
        folder.listFiles()?.filter{it.name.startsWith("before-")&&it.extension=="json"}?.sortedByDescending{it.name}?.drop(20)?.forEach{it.delete()}
    }; Background.refresh(context)}
    suspend fun export(uri:Uri):Pair<Int,Int> = withContext(Dispatchers.IO){
        val b=snapshot();val bytes=BackupCodec.encode(b).toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(bytes);it.flush()}?:error("Не удалось открыть файл для записи")
        val read=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Файл записан, но провайдер не разрешил его проверить. Используйте «Поделиться».")
        require(read.contentEquals(bytes)){"Проверка записи не прошла. Выберите папку «Загрузки» или «Поделиться»."}
        BackupCodec.decode(String(read,Charsets.UTF_8));b.rows.size to read.size
    }
    suspend fun share():File=withContext(Dispatchers.IO){val f=File(context.cacheDir,"shared/raspisanie-backup.json");f.parentFile!!.mkdirs();writeAtomic(f,BackupCodec.encode(snapshot()));f}
    suspend fun read(uri:Uri):Backup=withContext(Dispatchers.IO){val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Не удалось прочитать файл");BackupCodec.decode(text)}
    suspend fun restore(b:Backup)=change{b}
    suspend fun rollback():Backup=withContext(Dispatchers.IO){val f=folder.listFiles()?.filter{it.name.startsWith("before-")&&it.extension=="json"}?.maxByOrNull{it.name}?:error("Предыдущих версий пока нет");BackupCodec.decode(f.readText())}
}
