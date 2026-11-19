package ar.jornadas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Usuarios que actualizan desde la versión 1.2 (base Room versión 1). */
@RunWith(AndroidJUnit4::class)
class MigracionTest {
    private val app: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Crea un archivo con el esquema exacto que generaba Room en la base versión 1. */
    private fun crearBaseV1(nombre: String, conCharlas: Boolean) {
        app.deleteDatabase(nombre)
        val db = SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(nombre), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS `charlas` (`id` TEXT NOT NULL, `titulo` TEXT NOT NULL, " +
            "`tema` TEXT NOT NULL, `dia` INTEGER NOT NULL, `hora` TEXT NOT NULL, `sala` TEXT NOT NULL, " +
            "`ponente` TEXT NOT NULL, `resumen` TEXT NOT NULL, `favorita` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        if (conCharlas) {
            db.execSQL("INSERT INTO charlas VALUES ('11','Compose en producción','Android',1,'09:00','Auditorio','Paz Vega','x',1)")
            db.execSQL("INSERT INTO charlas VALUES ('12','Navegación entre pantallas','Android',1,'14:30','Auditorio','Sol Díaz','x',0)")
        }
        db.version = 1
        db.close()
    }

    private fun abrirV2(nombre: String) =
        Room.databaseBuilder(app, Archivo::class.java, nombre).addMigrations(MIGRACION_1_2).build()

    @Test fun conservaLasMarcasYCuentaComoEdicionPrevia() = runBlocking {
        crearBaseV1("migra1.db", conCharlas = true)
        val db = abrirV2("migra1.db")
        val charlas = db.cartelera().observar().first()
        assertEquals(2, charlas.size)
        assertTrue(charlas.first { it.id == "11" }.favorita)
        assertTrue(db.cartelera().hayEdicion().first())
        db.close()
    }

    @Test fun baseVaciaNoInventaUnaEdicion() = runBlocking {
        crearBaseV1("migra2.db", conCharlas = false)
        val db = abrirV2("migra2.db")
        assertFalse(db.cartelera().hayEdicion().first())
        db.close()
    }
}
