package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/** Fila única que indica que ya hubo al menos una descarga exitosa (aunque viniera vacía). */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val descargadaEn: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun hayEdicion(): Flow<Boolean>

    @Query("UPDATE charlas SET favorita = :valor WHERE id = :id")
    abstract suspend fun marcar(id: String, valor: Boolean)

    /**
     * Aplica una instantánea completa del programa en una sola transacción:
     * borra las charlas retiradas, agrega las nuevas sin marcar y actualiza los datos
     * de las vigentes SIN tocar la columna favorita. Así se respetan las marcas hechas
     * mientras la petición estaba en curso y una charla que regresa vuelve sin marcar.
     */
    @Transaction
    open suspend fun reemplazarPrograma(fichas: List<Charla>, ahora: Long) {
        if (fichas.isEmpty()) borrarTodas() else borrarRetiradas(fichas.map { it.id })
        insertarNuevas(fichas)
        fichas.forEach {
            actualizarDatos(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        }
        registrarEdicion(Edicion(descargadaEn = ahora))
    }

    @Query("DELETE FROM charlas")
    abstract suspend fun borrarTodas()

    @Query("DELETE FROM charlas WHERE id NOT IN (:vigentes)")
    abstract suspend fun borrarRetiradas(vigentes: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertarNuevas(fichas: List<Charla>)

    @Query("""UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora,
        sala = :sala, ponente = :ponente, resumen = :resumen WHERE id = :id""")
    abstract suspend fun actualizarDatos(id: String, titulo: String, tema: String, dia: Int,
        hora: String, sala: String, ponente: String, resumen: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun registrarEdicion(edicion: Edicion)
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/**
 * v1 → v2: agrega la tabla edicion sin tocar charlas (se conservan las marcas).
 * Quien ya tenía charlas descargadas con la versión anterior cuenta como "con edición previa".
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, " +
            "`descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}
