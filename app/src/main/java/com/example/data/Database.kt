@file:Suppress("DEPRECATION")
package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawText: String,
    val parsedIntent: String,
    val success: Boolean,
    val feedbackMessage: String
)

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Insert
    suspend fun insertLog(log: CommandLog)

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()
}

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE isCompleted = 0 ORDER BY timestamp DESC")
    fun getActiveTodos(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY timestamp DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Insert
    suspend fun insertTodo(item: TodoItem)

    @Query("UPDATE todo_items SET isCompleted = 1 WHERE id = :id")
    suspend fun completeTodo(id: Long)

    @Query("UPDATE todo_items SET isCompleted = 1 WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%'")
    suspend fun completeTodoByTitle(query: String): Int

    @Query("DELETE FROM todo_items WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%'")
    suspend fun deleteTodoByTitle(query: String): Int

    @Query("DELETE FROM todo_items")
    suspend fun clearAll()
}

@Database(entities = [CommandLog::class, TodoItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandLogDao(): CommandLogDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jarvis_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
