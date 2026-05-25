package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scoreboard")
data class ScoreRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String = "Sahabat Qur'an",
    val score: Int,
    val surahCompleted: String = "Semua Juz 30",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_verses")
data class CustomVerse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val suraNumber: Int,
    val suraName: String,
    val ayahNumber: Int,
    val textArabic: String
)

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scoreboard ORDER BY score DESC, timestamp DESC LIMIT 20")
    fun getAllScores(): Flow<List<ScoreRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(record: ScoreRecord)

    @Query("DELETE FROM scoreboard")
    suspend fun clearAllScores()

    // Custom Verses DAO methods
    @Query("SELECT * FROM custom_verses ORDER BY id DESC")
    fun getAllCustomVerses(): Flow<List<CustomVerse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomVerse(verse: CustomVerse)

    @Query("DELETE FROM custom_verses")
    suspend fun clearCustomVerses()
}

@Database(entities = [ScoreRecord::class, CustomVerse::class], version = 2, exportSchema = false)
abstract class ScoreDatabase : RoomDatabase() {
    abstract val scoreDao: ScoreDao

    companion object {
        @Volatile
        private var INSTANCE: ScoreDatabase? = null

        fun getInstance(context: Context): ScoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScoreDatabase::class.java,
                    "score_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ScoreRepository(private val scoreDao: ScoreDao) {
    val allScores: Flow<List<ScoreRecord>> = scoreDao.getAllScores()
    val allCustomVerses: Flow<List<CustomVerse>> = scoreDao.getAllCustomVerses()

    suspend fun insert(record: ScoreRecord) {
        scoreDao.insertScore(record)
    }

    suspend fun clear() {
        scoreDao.clearAllScores()
    }

    suspend fun insertCustomVerse(verse: CustomVerse) {
        scoreDao.insertCustomVerse(verse)
    }

    suspend fun clearCustomVerses() {
        scoreDao.clearCustomVerses()
    }
}
