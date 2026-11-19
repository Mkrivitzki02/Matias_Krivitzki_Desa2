package ar.jornadas.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pedido 42 y parte del 41, con las cuatro ediciones reales de assets. */
@RunWith(AndroidJUnit4::class)
class CarteleraDaoTest {
    private lateinit var db: Archivo
    private lateinit var dao: CarteleraDao
    private val app: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun abrir() {
        db = Room.inMemoryDatabaseBuilder(app, Archivo::class.java).build()
        dao = db.cartelera()
    }

    @After fun cerrar() = db.close()

    private fun edicion(n: Int): List<Charla> {
        val json = app.assets.open("programa-$n.json").bufferedReader().use { it.readText() }
        return Gson().fromJson(json, Array<FichaRemota>::class.java).map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
    }

    private suspend fun aplicar(n: Int) = dao.reemplazarPrograma(edicion(n), 0L)
    private suspend fun programa() = dao.observar().first()
    private suspend fun charla(id: String) = programa().find { it.id == id }
    private suspend fun marcadas() = programa().filter { it.favorita }.map { it.id }.toSet()

    @Test fun sinDescargaNoHayEdicion() = runBlocking {
        assertFalse(dao.hayEdicion().first())
    }

    @Test fun v1GuardaDoceCharlasSinMarcar() = runBlocking {
        aplicar(1)
        assertEquals(12, programa().size)
        assertTrue(marcadas().isEmpty())
        assertTrue(dao.hayEdicion().first())
    }

    @Test fun v2ConservaLaMarcaYActualizaTituloYSala() = runBlocking {
        aplicar(1)
        dao.marcar("11", true)
        aplicar(2)
        val c = charla("11")!!
        assertTrue(c.favorita)
        assertEquals("Compose en producción avanzada", c.titulo)
        assertEquals("Sala Puerto", c.sala)
    }

    @Test fun v3RetiraLa11AgregaLa90YConservaLasVigentes() = runBlocking {
        aplicar(1); dao.marcar("11", true); dao.marcar("23", true)
        aplicar(2)
        aplicar(3)
        assertNull(charla("11"))
        assertFalse(charla("90")!!.favorita)
        assertEquals(setOf("23"), marcadas())
        assertEquals(12, programa().size)
    }

    @Test fun unaCharlaQueRegresaVuelveSinMarcar() = runBlocking {
        aplicar(2); dao.marcar("11", true)
        aplicar(3)
        aplicar(2)
        assertFalse(charla("11")!!.favorita)
    }

    @Test fun marcasHechasDuranteLaPeticionSeRespetan() = runBlocking {
        aplicar(1); dao.marcar("12", true)
        val respuesta = edicion(2)      // la petición "sale" acá
        dao.marcar("21", true)          // el usuario marca mientras espera
        dao.marcar("12", false)         // y desmarca otra
        dao.reemplazarPrograma(respuesta, 0L) // llega la respuesta
        assertEquals(setOf("21"), marcadas())
    }

    @Test fun v4VaciaEsUnaEdicionValida() = runBlocking {
        aplicar(1); dao.marcar("11", true)
        aplicar(4)
        assertTrue(programa().isEmpty())
        assertTrue(dao.hayEdicion().first())
    }
}
