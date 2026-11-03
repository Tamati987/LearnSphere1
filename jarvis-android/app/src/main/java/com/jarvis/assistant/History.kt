package com.jarvis.assistant

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Une réplique de conversation : de l'utilisateur, de Jarvis, ou une notification annoncée. */
@Entity(tableName = "exchanges")
data class Exchange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val role: String,
    val text: String,
)

@Dao
interface ExchangeDao {
    @Insert
    fun insert(exchange: Exchange)

    @Query("SELECT * FROM exchanges WHERE text LIKE '%' || :query || '%' AND time BETWEEN :from AND :to ORDER BY time DESC LIMIT :limit")
    fun search(query: String, from: Long, to: Long, limit: Int): List<Exchange>

    @Query("DELETE FROM exchanges WHERE time < :before")
    fun deleteOlderThan(before: Long)
}

@Database(entities = [Exchange::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun exchanges(): ExchangeDao
}

/**
 * Historique complet des conversations, dans une base Room sur le téléphone (un an gardé).
 * Contrairement à la mémoire (résumés et souvenirs), on y retrouve les échanges mot pour mot.
 */
object History {
    @Volatile private var db: HistoryDatabase? = null
    private val writer = Executors.newSingleThreadExecutor()

    private fun dao(context: Context): ExchangeDao = (db ?: synchronized(this) {
        db ?: Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, "historique.db").build().also { db = it }
    }).exchanges()

    /** Enregistre une ligne « Rôle : texte » (en arrière-plan). */
    fun add(context: Context, line: String) {
        val role = line.substringBefore(" : ", "Jarvis")
        val text = line.substringAfter(" : ", line)
        writer.execute {
            runCatching {
                val dao = dao(context)
                dao.insert(Exchange(time = System.currentTimeMillis(), role = role, text = text))
                // Un peu de ménage de temps en temps : on garde un an d'historique.
                if (Math.random() < 0.01) dao.deleteOlderThan(System.currentTimeMillis() - 365L * 24 * 3600_000)
            }
        }
    }

    /** Recherche dans l'historique ([query] vide = tout), entre deux dates (millisecondes). À appeler hors du fil principal. */
    fun search(context: Context, query: String, from: Long, to: Long, limit: Int): String {
        val list = runCatching { dao(context).search(query.trim(), from, to, limit.coerceIn(1, 60)) }.getOrElse {
            return "Échec : historique illisible (${it.message})."
        }
        if (list.isEmpty()) return "Rien dans l'historique pour cette recherche."
        val format = SimpleDateFormat("EEE d MMM HH:mm", Locale.FRANCE)
        // Du plus ancien au plus récent, pour se lire comme une conversation.
        return list.asReversed().joinToString("\n") { "${format.format(Date(it.time))} · ${it.role} : ${it.text.take(400)}" }
    }
}
