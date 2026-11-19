package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    /** null mientras todavía no se leyó la base (distinto de "programa vacío"). */
    val charlas: StateFlow<List<Charla>?> = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hayEdicion: StateFlow<Boolean?> = repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Los filtros viven en el estado guardado: sobreviven a rotar y a la muerte del proceso,
    // pero una tarea nueva arranca sin filtros.
    val dia: StateFlow<Int> = estado.getStateFlow(CLAVE_DIA, 0)
    val tema: StateFlow<String> = estado.getStateFlow(CLAVE_TEMA, "Todos")

    private val _fallo = MutableStateFlow(false)
    val fallo: StateFlow<Boolean> = _fallo.asStateFlow()
    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    private var descarga: Job? = null

    fun elegirDia(valor: Int) { estado[CLAVE_DIA] = valor }
    fun elegirTema(valor: String) { estado[CLAVE_TEMA] = valor }

    /** Carga automática: una sola vez por proceso, aunque se recree la Activity o el ViewModel. */
    fun cargaInicial() {
        if (servicios.reclamarCargaAutomatica()) recargar()
    }

    /** Actualización explícita. Si ya hay una petición en curso, el toque se ignora. */
    fun recargar() {
        if (descarga?.isActive == true) return
        descarga = viewModelScope.launch {
            _cargando.value = true
            _fallo.value = false
            try {
                repo.sincronizar()
            } catch (e: IOException) {
                _fallo.value = true
            } finally {
                _cargando.value = false
            }
        }
    }

    fun marcar(id: String, valor: Boolean) { viewModelScope.launch { repo.marcar(id, valor) } }

    private companion object {
        const val CLAVE_DIA = "dia"
        const val CLAVE_TEMA = "tema"
    }
}
