package com.yourdomain.scheduleapp
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageTest {
    @Test fun migratesFilledVersionOneWithoutLoss()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>();val name="migration-test.db";c.deleteDatabase(name);val path=c.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use{old->
            old.execSQL("CREATE TABLE schedule (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,dayOfWeek INTEGER NOT NULL,pairNumber INTEGER NOT NULL,numeratorSubject TEXT NOT NULL,denominatorSubject TEXT NOT NULL,numeratorRoom TEXT NOT NULL,denominatorRoom TEXT NOT NULL,numeratorHomework TEXT NOT NULL,denominatorHomework TEXT NOT NULL,lastUpdated INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_schedule_dayOfWeek_pairNumber ON schedule(dayOfWeek,pairNumber)")
            old.execSQL("CREATE TABLE vk_images (url TEXT NOT NULL PRIMARY KEY,postDate TEXT NOT NULL,caption TEXT NOT NULL,downloadedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO schedule VALUES(1,2,1,'','ОАП','','105','','Не потерять ДЗ',123)");old.version=1
        }
        val db=Room.databaseBuilder(c,AppDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
        try{val store=PlannerStore(db,c);store.initialize();val before=store.snapshot();assertEquals("ОАП",before.rows.single().denominatorSubject);assertEquals("Не потерять ДЗ",before.state.tasks.single().text)
            val target=File(c.cacheDir,"storage-export.json");val result=store.export(Uri.fromFile(target));assertTrue(result.second>100);val read=store.read(Uri.fromFile(target));assertEquals(before.rows,read.rows)
            store.change{it.copy(rows=it.rows.map{r->r.copy(denominatorRoom="303")})};assertEquals("303",store.snapshot().rows.single().denominatorRoom)
            store.restore(read);assertEquals("105",store.snapshot().rows.single().denominatorRoom)
            val count=store.snapshot().rows.size;try{BackupCodec.decode("{}");fail()}catch(_:Exception){};assertEquals(count,store.snapshot().rows.size)
        }finally{db.close();c.deleteDatabase(name)}
    }
}
