package pt.pixellusitano.paragemcerta.ui.paragem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.AoVivo
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.FaixaEstado
import pt.pixellusitano.paragemcerta.ui.comum.LinhaChegada
import pt.pixellusitano.paragemcerta.ui.comunidade.CartaoRelato
import pt.pixellusitano.paragemcerta.ui.comunidade.CorrigirNaParagem
import pt.pixellusitano.paragemcerta.ui.comunidade.FotosDaParagem
import pt.pixellusitano.paragemcerta.ui.comunidade.RelatarNaParagem
import pt.pixellusitano.paragemcerta.ui.comunidade.aplicarCorrecoes
import pt.pixellusitano.paragemcerta.ui.comunidade.fotoDeRuaVisivel
import pt.pixellusitano.paragemcerta.ui.comunidade.rememberRelatosDaParagem
import pt.pixellusitano.paragemcerta.ui.tema.CoresGravidade
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Instant

/**
 * Ecrã da paragem ligado aos dados. [fotoDeRua] é a foto do Panoramax que o ficheiro do servidor tem para esta paragem (null se não
 * tem ou ainda não chegou) e [aoAbrirPagina] abre a página dela.
 */
@Composable
fun ParagemEcra(
    ref: RefParagem,
    repositorio: Repositorio,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
    carregarFoto: suspend (String) -> ByteArray?,
    aoEncontrar: (Paragem) -> Unit,
    aoAbrirConta: () -> Unit,
    aoVoltar: () -> Unit,
    fotoDeRua: FotoDeRua? = null,
    aoAbrirPagina: (String) -> Unit = {},
) {
    val contexto = LocalContext.current
    val vm = viewModel(key = ref.chave) { ParagemViewModel(repositorio.chegadas(ref)) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val filtro by vm.filtro.collectAsStateWithLifecycle()
    val paragem by produceState<Paragem?>(null, ref) { value = repositorio.paragem(ref) }
    var favorita by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val linhas by produceState(emptyMap<String, Linha>()) { value = repositorio.linhasCm() }
    val presentes = estado?.let(vm::linhasPresentes).orEmpty()
    val alertas by produceState(emptyList<Alerta>(), presentes) {
        favorita = repositorio.eFavorita(ref)
        value = repositorio.alertasDaParagem(ref, presentes.map { it.first }.toSet())
    }
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }
    val vigiadas by ServicoAvisoChegada.paragensVigiadas.collectAsStateWithLifecycle()
    val vigiada = ref.chave in vigiadas
    var avisaMe by rememberSaveable { mutableStateOf(false) }
    val aoVivo = rememberAoVivoCarris(repositorio, ref.id, remember(vm) { vm.estado.filterNotNull().map { it.chegadas } })
    val relatos = rememberRelatosDaParagem(ref.chave, paragem, comunidade, configuracoes, preferencias, aoAbrirConta)
    val rua = fotoDeRuaVisivel(fotoDeRua, preferencias)
    // A paragem como os passageiros a corrigiram (as correções aprovadas): é a que se mostra e a que "Encontrar a paragem" procura.
    val corrigida = paragem?.let { aplicarCorrecoes(it, relatos.correcoes) }
    val vista = corrigida?.first
    val nome = vista?.nome ?: ref.id
    ParagemConteudo(
        nome = nome,
        codigo = ref.id.substringAfterLast('_'),
        acessivel = paragem?.acessivel,
        favorita = favorita,
        vigiada = vigiada,
        estado = estado?.let(vm::aplicarFiltro),
        filtro = filtro,
        linhasPresentes = presentes,
        alertas = alertas,
        linhas = linhas,
        agora = agora,
        aoFiltrar = vm::escolherFiltro,
        aoFavoritar = {
            val p = paragem ?: return@ParagemConteudo
            escopo.launch {
                repositorio.alternarFavorita(p)
                favorita = repositorio.eFavorita(ref)
            }
        },
        aoAvisaMe = {
            if (vigiada) {
                contexto.startService(ServicoAvisoChegada.cancelar(contexto, ref))
            } else {
                avisaMe = true
            }
        },
        aoReportar = { runCatching { contexto.startActivity(intentReportarErro(BuildConfig.VERSION_NAME, ref.chave, nome)) } },
        aoVoltar = aoVoltar,
        modifier = Modifier.fillMaxSize(),
        aoEncontrar = vista?.let { p -> { aoEncontrar(p) } },
        relatar = { RelatarNaParagem(paragem, presentes.map { it.second }.distinct(), comunidade, configuracoes, aoAbrirConta) },
        relatos = relatos.lista,
        aoVotar = relatos.aoVotar,
        aoDenunciar = relatos.aoDenunciar,
        aoOcultar = relatos.aoOcultar,
        fotos = {
            FotosDaParagem(
                ref.chave, relatos, comunidade, configuracoes, carregarFoto, aoAbrirConta, rua = rua, aoAbrirPagina = aoAbrirPagina,
                aoOcultarRua = { rua?.let { escopo.launch { preferencias.ocultar("foto:${it.id}") } } },
            )
        },
        jaNaoExiste = corrigida?.second == true,
        corrigir = { CorrigirNaParagem(vista, comunidade, configuracoes, aoAbrirConta) },
        aoVivo = aoVivo,
    )
    // O serviço vigia as linhas pelo nome que a pessoa vê (o do Transitous não é o id).
    if (avisaMe) AvisaMe(ref, nome, presentes.map { it.second }.distinct(), aoFechar = { avisaMe = false })
}

/**
 * O "ao vivo, a N paragens" das [chegadas] da paragem [paragemId], pelo `idViagem` de cada uma (ver [Repositorio.aoVivoCarris]); vazio
 * enquanto não há (outro operador, interruptor desligado, falha). Só lê com o ecrã à vista (STARTED), como as chegadas.
 */
@Composable
fun rememberAoVivoCarris(repositorio: Repositorio, paragemId: String?, chegadas: Flow<List<Chegada>>): Map<String, AoVivo> {
    val fluxo = remember(paragemId) { if (paragemId == null) emptyFlow() else repositorio.aoVivoCarris(paragemId, chegadas) }
    return fluxo.collectAsStateWithLifecycle(emptyMap()).value
}

/** Conteúdo puro do ecrã da paragem (testado com capturas). */
@Composable
fun ParagemConteudo(
    nome: String,
    codigo: String,
    acessivel: Boolean?,
    favorita: Boolean,
    vigiada: Boolean,
    estado: EstadoChegadas?,
    filtro: String?,
    linhasPresentes: List<Pair<String, String>>,
    alertas: List<Alerta>,
    linhas: Map<String, Linha>,
    agora: Instant,
    aoFiltrar: (String?) -> Unit,
    aoFavoritar: () -> Unit,
    aoAvisaMe: () -> Unit,
    aoReportar: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    aoEncontrar: (() -> Unit)? = null,
    relatar: @Composable () -> Unit = {},
    relatos: List<RelatoRemoto> = emptyList(),
    aoVotar: (RelatoRemoto, aindaEsta: Boolean) -> Unit = { _, _ -> },
    aoDenunciar: (RelatoRemoto) -> Unit = {},
    aoOcultar: (RelatoRemoto) -> Unit = {},
    fotos: @Composable () -> Unit = {},
    jaNaoExiste: Boolean = false,
    corrigir: @Composable () -> Unit = {},
    aoVivo: Map<String, AoVivo> = emptyMap(),
) {
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = aoVoltar) { Text("← " + stringResource(R.string.voltar), color = MaterialTheme.colorScheme.onPrimary) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    val descricao = stringResource(if (favorita) R.string.paragem_favorita else R.string.paragem_nao_favorita)
                    Text(
                        if (favorita) "★" else "☆",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = aoFavoritar)
                            .semantics { contentDescription = descricao }
                            .padding(10.dp),
                    )
                }
            }
            Text(nome, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp))
            Text(
                stringResource(R.string.paragem_codigo, codigo) + if (acessivel == true) "  ·  ♿ " + stringResource(R.string.acessivel) else "",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        FrisoAzulejo(Modifier.fillMaxWidth())
        Column(Modifier.padding(16.dp)) {
            if (jaNaoExiste) AvisoJaNaoExiste()
            estado?.let { FaixaEstado(it, agora, Modifier.padding(bottom = 8.dp)) }
            if (linhasPresentes.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filtro == null, onClick = { aoFiltrar(null) }, label = { Text(stringResource(R.string.paragem_todas)) })
                    linhasPresentes.forEach { (_, nome) ->
                        FilterChip(selected = filtro == nome, onClick = { aoFiltrar(nome) }, label = { Text(nome) })
                    }
                }
            }
            alertas.forEach { CartaoAlerta(it) }
            // Com o relógio do ecrã: um relato que acabou desde a última leitura sai já, sem esperar por outro pedido.
            relatos.filter { it.expiraEm > agora.toEpochMilli() }.forEach { r ->
                // O relato guarda a linha em maiúsculas; mostra-se o nome que a pessoa vê na paragem.
                val nomeLinha = linhasPresentes.firstOrNull { chaveLinha(it.second) == r.linha }?.second ?: r.linha.ifEmpty { null }
                CartaoRelato(r, nomeLinha, agora, aoVotar = { aoVotar(r, it) }, aoDenunciar = { aoDenunciar(r) }, aoOcultar = { aoOcultar(r) })
            }
            // Filtra outra vez com o relógio do ecrã: sem rede, a próxima resposta pode demorar minutos.
            val visiveis = estado?.chegadas?.filter { aindaPorChegar(it, agora) }
            when {
                estado == null -> Text(stringResource(R.string.a_carregar), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp))
                visiveis.isNullOrEmpty() && !estado.semLigacao && !estado.tempoRealIndisponivel ->
                    Text(stringResource(R.string.sem_chegadas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp))
                else -> visiveis.orEmpty().forEach { LinhaChegada(it, linhas[it.linhaId], agora, aoVivo = aoVivo[it.idViagem]) }
            }
            // Sem linhas à vista não há o que escolher; se a paragem já está vigiada, "Parar" serve sempre.
            Button(onClick = aoAvisaMe, enabled = vigiada || linhasPresentes.isNotEmpty(), modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Text(stringResource(if (vigiada) R.string.avisame_vigiar_parar else R.string.avisame))
            }
            relatar()
            fotos()
            // Precisa das coordenadas da paragem: enquanto ela não carrega, não há bússola.
            aoEncontrar?.let {
                OutlinedButton(onClick = it, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.bussola_encontrar))
                }
            }
            corrigir()
            OutlinedButton(onClick = aoReportar, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.paragem_reportar))
            }
        }
    }
}

/** O que dizem os passageiros, depois de o autor aprovar a correção: a paragem continua à vista, mas com este aviso (o estilo dos alertas de aviso). */
@Composable
private fun AvisoJaNaoExiste() {
    val estilo = estiloDaGravidade(Gravidade.AVISO, LocalEscuro.current)
    Text(
        stringResource(R.string.paragem_ja_nao_existe), color = estilo.texto, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().background(estilo.fundo, RoundedCornerShape(10.dp)).padding(10.dp),
    )
}

@Composable
private fun CartaoAlerta(a: Alerta) {
    var aberto by rememberSaveable(a.id) { mutableStateOf(false) }
    val (fundo, texto, rotulo, icone) = estiloDaGravidade(a.gravidade, LocalEscuro.current)
    Column(
        Modifier.padding(vertical = 6.dp).fillMaxWidth().background(fundo, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { aberto = !aberto }.padding(10.dp),
    ) {
        Text("$icone ${stringResource(rotulo)} · ${a.titulo}", color = texto, style = MaterialTheme.typography.labelLarge)
        if (aberto) Text(a.texto, color = texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        else Text(stringResource(R.string.mostrar_mais), color = texto, style = MaterialTheme.typography.labelSmall)
    }
}

/** As cores e o ícone de cada gravidade; os cartões dos relatos usam os mesmos (por confirmar = INFO, confirmado = AVISO). */
internal fun estiloDaGravidade(g: Gravidade, escuro: Boolean): EstiloAlerta {
    val (rotulo, icone) = when (g) {
        Gravidade.GRAVE -> R.string.gravidade_grave to "⛔"
        Gravidade.AVISO -> R.string.gravidade_aviso to "⚠"
        Gravidade.INFO -> R.string.gravidade_info to "ⓘ"
    }
    return EstiloAlerta(CoresGravidade.fundo(g, escuro), CoresGravidade.texto(g, escuro), rotulo, icone)
}

internal data class EstiloAlerta(val fundo: Color, val texto: Color, val rotulo: Int, val icone: String)
