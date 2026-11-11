package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.size
import pt.pixellusitano.paragemcerta.ui.tema.iconeModo
import androidx.compose.material3.Icon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.ImageVector
import pt.pixellusitano.paragemcerta.ui.comum.FaixaAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.bordaDoFiltro
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.AjustarBarraDeEstado
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.inicio.pedirPosicaoAtual
import pt.pixellusitano.paragemcerta.ui.inicio.ultimaPosicao
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class Campo { ORIGEM, DESTINO }

/** Os meios de transporte que se podem escolher, nos filtros do ecrã Rotas e nas preferências de rotas do Menu. */
internal val MODOS_FILTRO = listOf(
    Modo.AUTOCARRO to R.string.modo_autocarro, Modo.METRO to R.string.modo_metro, Modo.COMBOIO to R.string.modo_comboio,
    Modo.ELETRICO to R.string.modo_eletrico, Modo.BARCO to R.string.modo_barco,
)

/** Os nomes das linhas em veículo das rotas, sem repetir: o que se pergunta à comunidade para os cartões. */
internal fun linhasDasRotas(itinerarios: List<Itinerario>): List<String> =
    itinerarios.flatMap { it.etapas }.filter { !it.aPe }.mapNotNull { it.linhaNome }.distinct()

/** A lista (com a faixa) e o Escolher lugar pedem ícones claros; o Detalhe, com o mapa por baixo da barra de estado, pede-os escuros. */
@Composable
internal fun BarraDeEstadoDasRotas(aEscolher: Boolean, semRota: Boolean) = AjustarBarraDeEstado(comFaixa = aEscolher || semRota)

@Composable
fun RotasEcra(
    repositorio: Repositorio,
    preferencias: Preferencias,
    comunidade: Comunidade,
    origem: Lugar?,
    destino: Lugar?,
    aoAbrirViagem: () -> Unit,
    /** O interruptor `comunidade` da config: vai até ao começar da viagem (os avisos da comunidade). */
    comunidadeLigada: Boolean = false,
) {
    val contexto = LocalContext.current
    val vm = viewModel {
        RotasViewModel(
            repositorio::planear, repositorio::registarRecente, repositorio::linhasComAlerta,
            preferencias.filtros, preferencias::definirFiltros, { repositorio.proximasPartidas(it) }, origem = origem, destino = destino,
        )
    }
    val pedido by vm.pedido.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val filtros by vm.filtros.collectAsStateWithLifecycle()
    val alertas by vm.alertas.collectAsStateWithLifecycle()
    val aberto by vm.aberto.collectAsStateWithLifecycle()
    val seguida by vm.seguida.collectAsStateWithLifecycle()
    // Os relatos confirmados das linhas das rotas que estão no ecrã: um pedido por lista de rotas (a cache de 2 min evita repetir),
    // só quando a pessoa pede as rotas; com a comunidade desligada a Comunidade não pede nada e a lista fica vazia.
    val linhas = remember(estado) { linhasDasRotas((estado as? EstadoRotas.Prontas)?.itinerarios.orEmpty()) }
    val relatos by produceState(emptyList<RelatoRemoto>(), linhas) {
        value = if (linhas.isEmpty()) emptyList() else comunidade.confirmadosNasLinhas(linhas)
    }
    val locais by remember { repositorio.locais() }.collectAsStateWithLifecycle(emptyList())
    val recentes by remember { repositorio.recentes() }.collectAsStateWithLifecycle(emptyList())
    var escolher by rememberSaveable { mutableStateOf<Campo?>(null) }
    val guardarLocal = rememberGuardarLocal(repositorio)
    // Os "dentro de 3 min" dos cartões andam com o relógio enquanto o ecrã está aberto.
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }

    LaunchedEffect(Unit) {
        ultimaPosicao(contexto)?.let(vm::definirPosicao)
        pedirPosicaoAtual(contexto, vm::definirPosicao)
    }
    BackHandler(enabled = escolher != null) { escolher = null }
    BackHandler(enabled = escolher == null && aberto != null) { vm.abrir(null) }

    val rota = aberto
    BarraDeEstadoDasRotas(aEscolher = escolher != null, semRota = rota == null)
    when (val campo = escolher) {
        null if rota != null -> DetalheRotaEcra(
            rota,
            de = pedido.origem?.nome ?: stringResource(R.string.rotas_minha_posicao),
            para = pedido.destino?.nome.orEmpty(),
            repositorio = repositorio,
            preferencias = preferencias,
            aoComecou = aoAbrirViagem,
            aoVoltar = { vm.abrir(null) },
            seguida = seguida,
            aoSeguir = vm::seguir,
            comunidadeLigada = comunidadeLigada,
        )
        null -> RotasConteudo(
            pedido = pedido, estado = estado, filtros = filtros, alertas = alertas, agora = agora,
            aoEscolherOrigem = { escolher = Campo.ORIGEM }, aoEscolherDestino = { escolher = Campo.DESTINO },
            aoMudarHora = vm::definirHora, aoMudarFiltros = vm::mudarFiltros,
            aoAbrirRota = vm::abrir, aoTentarOutraVez = vm::tentarOutraVez,
            aoMaisCedo = vm::maisCedo, aoMaisTarde = vm::maisTarde,
            relatos = relatos,
        )
        else -> EscolherLugarEcra(
            titulo = stringResource(if (campo == Campo.ORIGEM) R.string.rotas_de else R.string.rotas_para),
            comPosicao = campo == Campo.ORIGEM,
            locais = locais,
            recentes = lugaresRecentes(recentes),
            pesquisar = { t -> repositorio.pesquisarLugares(t, ultimaPosicao(contexto)) },
            aoEscolher = { l ->
                if (campo == Campo.ORIGEM) vm.definirOrigem(l) else if (l != null) vm.definirDestino(l)
                escolher = null
            },
            aoVoltar = { escolher = null },
            aoGuardar = guardarLocal,
        )
    }
}

@Composable
fun RotasConteudo(
    pedido: PedidoRotas,
    estado: EstadoRotas,
    filtros: Filtros,
    alertas: Set<String>,
    agora: Instant,
    aoEscolherOrigem: () -> Unit,
    aoEscolherDestino: () -> Unit,
    aoMudarHora: (TipoHora, LocalTime?) -> Unit,
    aoMudarFiltros: (Filtros) -> Unit,
    aoAbrirRota: (Itinerario) -> Unit,
    aoTentarOutraVez: () -> Unit,
    aoMaisCedo: () -> Unit,
    aoMaisTarde: () -> Unit,
    modifier: Modifier = Modifier,
    /** Os relatos confirmados das linhas das rotas (já sem os ocultos): cada cartão mostra o seu (ver [relatoDoCartao]). */
    relatos: List<RelatoRemoto> = emptyList(),
) {
    var escolherHora by remember { mutableStateOf<TipoHora?>(null) }
    val cores = MaterialTheme.colorScheme
    val lista = rememberLazyListState()
    // Uma página nova (ou uma procura nova) começa de cima, não onde estava o fim da lista anterior.
    LaunchedEffect((estado as? EstadoRotas.Prontas)?.itinerarios) { lista.scrollToItem(0) }

    // As filas de filtros rolam até à borda do ecrã e levam o recuo por dentro; os outros itens recuam por fora.
    val recuo = Modifier.padding(horizontal = 16.dp)
    Column(modifier.fillMaxSize().background(cores.background)) {
        FaixaAzulejo {
            CartaoAzulejo(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                CampoLugar(
                    Icones.Posicao, stringResource(R.string.rotas_de), pedido.origem?.nome ?: stringResource(R.string.rotas_minha_posicao),
                    vazio = false, aoTocar = aoEscolherOrigem,
                )
                HorizontalDivider(color = cores.outlineVariant)
                CampoLugar(
                    Icones.Lugar, stringResource(R.string.rotas_para), pedido.destino?.nome ?: stringResource(R.string.pesquisa_dica),
                    vazio = pedido.destino == null, aoTocar = aoEscolherDestino,
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), lista,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).then(recuo), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val agoraEscolhido = pedido.tipoHora == TipoHora.AGORA
                    FilterChip(
                        selected = agoraEscolhido, onClick = { aoMudarHora(TipoHora.AGORA, null) },
                        label = { Text(stringResource(R.string.rotas_partir_agora)) }, border = bordaDoFiltro(agoraEscolhido),
                    )
                    ChipHora(TipoHora.PARTIR, pedido, agora, R.string.rotas_partir_as, R.string.rotas_partir_quando) { escolherHora = it }
                    ChipHora(TipoHora.CHEGAR, pedido, agora, R.string.rotas_chegar_as, R.string.rotas_chegar_quando) { escolherHora = it }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).then(recuo), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filtros.menosAPe, { aoMudarFiltros(filtros.copy(menosAPe = !filtros.menosAPe)) },
                        label = { Text(stringResource(R.string.rotas_menos_a_pe)) }, border = bordaDoFiltro(filtros.menosAPe))
                    FilterChip(filtros.menosTransbordos, { aoMudarFiltros(filtros.copy(menosTransbordos = !filtros.menosTransbordos)) },
                        label = { Text(stringResource(R.string.rotas_menos_transbordos)) }, border = bordaDoFiltro(filtros.menosTransbordos))
                    FilterChip(filtros.acessivel, { aoMudarFiltros(filtros.copy(acessivel = !filtros.acessivel)) },
                        label = { Text(stringResource(R.string.rotas_acessivel)) }, border = bordaDoFiltro(filtros.acessivel))
                    MODOS_FILTRO.forEach { (modo, texto) ->
                        val escolhido = modo in filtros.modos
                        FilterChip(escolhido, { aoMudarFiltros(filtros.copy(modos = if (escolhido) filtros.modos - modo else filtros.modos + modo)) },
                            leadingIcon = { Icon(iconeModo(modo), null, Modifier.size(FilterChipDefaults.IconSize)) }, label = { Text(stringResource(texto)) },
                            border = bordaDoFiltro(escolhido))
                    }
                }
            }
            when (estado) {
                EstadoRotas.Inicial -> item { Aviso(stringResource(R.string.rotas_dica), recuo) }
                EstadoRotas.ACarregar -> item {
                    Column(recuo, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Aviso(stringResource(R.string.rotas_a_procurar), Modifier)
                    }
                }
                is EstadoRotas.Prontas -> {
                    // As páginas ficam por cima das rotas: chegam-se sem percorrer a lista e não fogem do dedo (a página nova começa de cima).
                    if (estado.anterior != null || estado.seguinte != null || estado.aCarregarPagina || estado.erroPagina) item {
                        Column(Modifier.fillMaxWidth().then(recuo), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (estado.aCarregarPagina) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (estado.anterior != null) TextButton(onClick = aoMaisCedo) { Text(stringResource(R.string.rotas_mais_cedo)) }
                                if (estado.seguinte != null) TextButton(onClick = aoMaisTarde) { Text(stringResource(R.string.rotas_mais_tarde)) }
                            }
                            if (estado.erroPagina) Aviso(stringResource(R.string.rotas_erro_pagina), Modifier)
                        }
                    }
                    itemsIndexed(estado.itinerarios) { i, it ->
                        Box(recuo) {
                            CartaoRota(
                                it, estado.etiquetas.getOrElse(i) { emptySet() },
                                comAlerta = it.etapas.any { e -> !e.aPe && e.linhaNome in alertas },
                                aoAbrir = { aoAbrirRota(it) },
                                partidas = estado.partidas[i], agora = agora,
                                relato = relatoDoCartao(it, relatos),
                            )
                        }
                    }
                }
                is EstadoRotas.Vazias -> item {
                    Aviso(
                        stringResource(
                            when (estado.motivo) {
                                Motivo.SEM_LIGACOES -> R.string.rotas_sem_ligacoes
                                Motivo.MUITO_PERTO -> R.string.rotas_muito_perto
                                Motivo.SEM_ORIGEM -> R.string.rotas_sem_origem
                            },
                        ),
                        recuo,
                    )
                }
                is EstadoRotas.Erro -> item {
                    Column(Modifier.fillMaxWidth().then(recuo), horizontalAlignment = Alignment.CenterHorizontally) {
                        Aviso(stringResource(if (estado.semLigacao) R.string.rotas_erro_rede else R.string.rotas_erro_servico), Modifier)
                        TextButton(onClick = aoTentarOutraVez) { Text(stringResource(R.string.rotas_tentar)) }
                    }
                }
            }
        }
    }

    escolherHora?.let { tipo ->
        SeletorHora(
            inicial = pedido.hora ?: agora.atZone(LISBOA_FUSO).toLocalTime(),
            aoConfirmar = { h ->
                aoMudarHora(tipo, h)
                escolherHora = null
            },
            aoCancelar = { escolherHora = null },
        )
    }
}

@Composable
private fun CampoLugar(icone: ImageVector, rotulo: String, valor: String, vazio: Boolean, aoTocar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = aoTocar).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icone, null, Modifier.size(24.dp), tint = cores.primary)
        Column(Modifier.weight(1f)) {
            Text(rotulo, color = cores.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(
                valor, color = if (vazio) cores.onSurfaceVariant else cores.onSurface,
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** "Partir às" sem hora escolhida; "Partir às 08:00" ou "Partir amanhã às 08:00" quando é este o tipo de hora em uso. */
@Composable
private fun ChipHora(tipo: TipoHora, pedido: PedidoRotas, agora: Instant, semHora: Int, comHora: Int, aoEscolher: (TipoHora) -> Unit) {
    val hora = pedido.hora.takeIf { pedido.tipoHora == tipo }
    val texto = if (hora == null) stringResource(semHora) else {
        val zona = agora.atZone(LISBOA_FUSO)
        val amanha = proximaOcorrencia(hora, zona).toLocalDate() != zona.toLocalDate()
        stringResource(comHora, stringResource(if (amanha) R.string.rotas_amanha_as else R.string.rotas_as, HORA.format(hora)))
    }
    FilterChip(selected = hora != null, onClick = { aoEscolher(tipo) }, label = { Text(texto) }, border = bordaDoFiltro(hora != null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHora(inicial: LocalTime, aoConfirmar: (LocalTime) -> Unit, aoCancelar: () -> Unit) {
    val estado = rememberTimePickerState(inicial.hour, inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = aoCancelar,
        confirmButton = { TextButton(onClick = { aoConfirmar(LocalTime.of(estado.hour, estado.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) } },
        text = { CorpoSeletorHora(estado) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CorpoSeletorHora(estado: TimePickerState) {
    val cores = MaterialTheme.colorScheme
    TimePicker(
        estado,
        colors = TimePickerDefaults.colors(
            clockDialColor = cores.secondaryContainer,
            timeSelectorUnselectedContainerColor = cores.secondaryContainer,
            timeSelectorSelectedContainerColor = cores.primary,
            timeSelectorSelectedContentColor = cores.onPrimary,
        ),
    )
}

@Composable
private fun Aviso(texto: String, modifier: Modifier) {
    Text(
        texto, modifier.fillMaxWidth().padding(vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}
