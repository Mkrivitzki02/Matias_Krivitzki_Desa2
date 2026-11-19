package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

@Composable
fun Catalogo(model: ProgramaModel, lista: LazyListState, abrir: (String) -> Unit) {
    // Sin LaunchedEffect: entrar a esta pestaña o volver de una ficha no descarga nada.
    val dia by model.dia.collectAsStateWithLifecycle()
    val tema by model.tema.collectAsStateWithLifecycle()
    val fichas by model.charlas.collectAsStateWithLifecycle()
    val hayEdicion by model.hayEdicion.collectAsStateWithLifecycle()
    val fallo by model.fallo.collectAsStateWithLifecycle()
    val cargando by model.cargando.collectAsStateWithLifecycle()
    // Al cambiar un filtro la lista vuelve arriba; al rotar o recuperar el proceso el filtro
    // guardado coincide con el actual, así que se respeta la posición de lectura.
    var filtroMostrado by rememberSaveable { mutableStateOf("$dia|$tema") }
    LaunchedEffect(dia, tema) {
        if (filtroMostrado != "$dia|$tema") {
            filtroMostrado = "$dia|$tema"
            lista.scrollToItem(0)
        }
    }
    Selector(listOf("Ambos días", "Día 1", "Día 2"), dia, model::elegirDia)
    val temas = listOf("Todos", "Android", "Datos", "Calidad")
    Selector(temas, temas.indexOf(tema)) { model.elegirTema(temas[it]) }
    TextButton(onClick = model::recargar) { Text("Actualizar") }
    if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
    val programa = fichas
    when {
        programa == null || hayEdicion == null -> Unit // todavía leyendo la base local
        hayEdicion == false -> when {
            fallo -> {
                Text("No se pudo descargar el programa y todavía no hay una edición guardada.")
                OutlinedButton(onClick = model::recargar) { Text("Reintentar") }
            }
            cargando -> Text("Descargando el programa…")
            else -> OutlinedButton(onClick = model::recargar) { Text("Descargar programa") }
        }
        programa.isEmpty() -> Text("Sin charlas programadas.")
        else -> {
            val visibles = programa.filter { (dia == 0 || it.dia == dia) && (tema == "Todos" || it.tema == tema) }
            if (visibles.isEmpty()) Text("Ninguna charla coincide con los filtros.")
            Grilla(visibles, lista, abrir)
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, lista: LazyListState = rememberLazyListState(), abrir: (String) -> Unit) {
    LazyColumn(state = lista, verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
