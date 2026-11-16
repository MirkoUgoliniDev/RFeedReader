package it.mirkougolinidev.rfeedreader.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PodcastEntity::class, EpisodeEntity::class], version = 3, exportSchema = false)
abstract class RFeedDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    companion object {
        @Volatile private var instance: RFeedDatabase? = null

        /** v2: token di proprietà dei download (vedi [EpisodeEntity.downloadToken]). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN downloadToken TEXT")
            }
        }

        /** v3: dimensione reale del download e motivo dell'ultimo errore. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN downloadTotalBytes INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN downloadError TEXT")
            }
        }

        fun get(context: Context): RFeedDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RFeedDatabase::class.java, "rfeedreader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
