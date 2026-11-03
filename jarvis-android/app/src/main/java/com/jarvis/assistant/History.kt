package com.jarvis.assistant

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Un message de conversation : auteur (Utilisateur, Jarvis, Notification…), date et heure, texte. */
@Entity(tableName = "exchanges", indices = [Index(value = ["time", "role", "text"], unique = true)])
data class Exchange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val role: String,
    val text: String,
)

/** Souvenir à long terme : un fait sur l'utilisateur ou une consigne. */
@Entity(tableName = "memories")
data class MemoryRow(@PrimaryKey val id: Int, val type: String, val text: String, val date: String)

/** Résumé d'une conversation terminée. */
@Entity(tableName = "summaries")
data class SummaryRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val text: String)

/** Conversation en cours, pas encore résumée (ligne par ligne, dans l'ordre). */
@Entity(tableName = "transcript")
data class TranscriptRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val line: String)

/** Réveil de Jarvis ; jours au format « 2,3,4 » (jours de Calendar). */
@Entity(tableName = "alarms")
data class AlarmRow(
    @PrimaryKey val id: Int, val hour: Int, val minute: Int, val days: String, val text: String,
    val agenda: Boolean, val music: String?, val weather: Boolean,
)

/** Routine programmée ; jours au format « 2,3,4 ». */
@Entity(tableName = "routines")
data class RoutineRow(@PrimaryKey val id: Int, val hour: Int, val minute: Int, val days: String, val instruction: String)

/** Petites valeurs internes (début de la conversation en cours…). */
@Entity(tableName = "kv")
data class ValueRow(@PrimaryKey val key: String, val value: String)

@Dao
interface JarvisDao {
    // Historique
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertExchange(exchange: Exchange)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertExchanges(exchanges: List<Exchange>)

    @Query("SELECT * FROM exchanges WHERE text LIKE '%' || :query || '%' AND time BETWEEN :from AND :to ORDER BY time DESC LIMIT :limit")
    fun searchExchanges(query: String, from: Long, to: Long, limit: Int): List<Exchange>

    @Query("SELECT * FROM exchanges ORDER BY time")
    fun allExchanges(): List<Exchange>

    @Query("DELETE FROM exchanges WHERE time < :before")
    fun deleteExchangesBefore(before: Long)

    @Query("DELETE FROM exchanges")
    fun clearExchanges()

    // Mémoire
    @Query("SELECT * FROM memories ORDER BY id")
    fun memories(): List<MemoryRow>

    @Query("SELECT * FROM summaries ORDER BY id")
    fun summaries(): List<SummaryRow>

    @Query("SELECT * FROM transcript ORDER BY id")
    fun transcript(): List<TranscriptRow>

    @Query("DELETE FROM memories") fun clearMemories()
    @Query("DELETE FROM summaries") fun clearSummaries()
    @Query("DELETE FROM transcript") fun clearTranscript()
    @Insert fun insertMemories(rows: List<MemoryRow>)
    @Insert fun insertSummaries(rows: List<SummaryRow>)
    @Insert fun insertTranscript(rows: List<TranscriptRow>)

    @Transaction
    fun replaceMemory(memories: List<MemoryRow>, summaries: List<SummaryRow>, transcript: List<TranscriptRow>) {
        clearMemories(); clearSummaries(); clearTranscript()
        insertMemories(memories); insertSummaries(summaries); insertTranscript(transcript)
    }

    // Réveils et routines
    @Query("SELECT * FROM alarms") fun alarms(): List<AlarmRow>
    @Query("DELETE FROM alarms") fun clearAlarms()
    @Insert fun insertAlarms(rows: List<AlarmRow>)

    @Transaction
    fun replaceAlarms(rows: List<AlarmRow>) { clearAlarms(); insertAlarms(rows) }

    @Query("SELECT * FROM routines") fun routines(): List<RoutineRow>
    @Query("DELETE FROM routines") fun clearRoutines()
    @Insert fun insertRoutines(rows: List<RoutineRow>)

    @Transaction
    fun replaceRoutines(rows: List<RoutineRow>) { clearRoutines(); insertRoutines(rows) }

    // Valeurs
    @Query("SELECT value FROM kv WHERE `key` = :key") fun value(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun setValue(row: ValueRow)
}

@Database(
    entities = [Exchange::class, MemoryRow::class, SummaryRow::class, TranscriptRow::class, AlarmRow::class, RoutineRow::class, ValueRow::class],
    version = 1,
    exportSchema = false,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun dao(): JarvisDao
}

/**
 * La base de données de Jarvis (« jarvis.db », dans la mémoire interne de l'appli) : historique
 * des conversations, mémoire à long terme et consignes, résumés, réveils et routines. Les tables
 * sont petites : on s'autorise à les lire depuis le fil principal.
 */
object JarvisDb {
    @Volatile private var db: JarvisDatabase? = null

    fun dao(context: Context): JarvisDao = (db ?: synchronized(this) {
        db ?: Room.databaseBuilder(context.applicationContext, JarvisDatabase::class.java, NAME)
            .allowMainThreadQueries()
            // Un seul fichier (pas de journal WAL à côté) : la sauvegarde d'Android le copie en entier.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build().also { db = it }
    }).dao()

    const val NAME = "jarvis.db"

    fun days(set: Set<Int>) = set.sorted().joinToString(",")
    fun days(text: String): Set<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
}

/**
 * Historique complet des conversations : chaque message avec son auteur, sa date et son heure,
 * gardé un an. Contrairement à la mémoire (résumés et souvenirs), on y retrouve tout mot pour mot.
 */
object History {
    private val writer = Executors.newSingleThreadExecutor()

    /** Enregistre une ligne « Auteur : texte » (en arrière-plan). */
    fun add(context: Context, line: String) {
        val role = line.substringBefore(" : ", "Jarvis")
        val text = line.substringAfter(" : ", line)
        val time = System.currentTimeMillis()
        writer.execute {
            runCatching {
                val dao = JarvisDb.dao(context)
                dao.insertExchange(Exchange(time = time, role = role, text = text))
                // Un peu de ménage de temps en temps : on garde un an d'historique.
                if (Math.random() < 0.01) dao.deleteExchangesBefore(System.currentTimeMillis() - 365L * 24 * 3600_000)
            }
        }
    }

    fun clear(context: Context) = runCatching { JarvisDb.dao(context).clearExchanges() }.isSuccess

    /** Recherche dans l'historique ([query] vide = tout), entre deux dates (millisecondes). */
    fun search(context: Context, query: String, from: Long, to: Long, limit: Int): String {
        val list = runCatching { JarvisDb.dao(context).searchExchanges(query.trim(), from, to, limit.coerceIn(1, 60)) }.getOrElse {
            return "Échec : historique illisible (${it.message})."
        }
        if (list.isEmpty()) return "Rien dans l'historique pour cette recherche."
        val format = SimpleDateFormat("EEE d MMM HH:mm", Locale.FRANCE)
        // Du plus ancien au plus récent, pour se lire comme une conversation.
        return list.asReversed().joinToString("\n") { "${format.format(Date(it.time))} · ${it.role} : ${it.text.take(400)}" }
    }

    /** Derniers messages, pour donner du contexte au modèle hors ligne. */
    fun recent(context: Context, count: Int): List<Exchange> =
        runCatching { JarvisDb.dao(context).searchExchanges("", 0, Long.MAX_VALUE, count).asReversed() }.getOrDefault(emptyList())
}
