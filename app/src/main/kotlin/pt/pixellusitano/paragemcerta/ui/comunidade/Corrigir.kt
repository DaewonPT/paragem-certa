package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.Correcao
import pt.pixellusitano.paragemcerta.core.fontes.servidor.CorrecaoRemota
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.ui.comum.BotaoPrincipal
import pt.pixellusitano.paragemcerta.ui.comum.BotaoSecundario
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro

// O limite do servidor (worker/src/correcoes.ts): o nome tem 1 a 80 caracteres depois de cortado, contados por pontos de código.
private const val MAX_NOME = 80

private val CONTROLO = Regex("\\p{Cc}")

/**
 * A paragem como os passageiros a corrigiram (correções aprovadas pelo autor, as da paragem [p]): o nome e a posição novos.
 * O segundo valor diz que dizem que a paragem já não existe; os dados da paragem ficam como estão.
 */
fun aplicarCorrecoes(p: Paragem, correcoes: List<CorrecaoRemota>): Pair<Paragem, Boolean> {
    var vista = p
    var jaNaoExiste = false
    for (c in correcoes) {
        if (c.paragem != p.ref.chave) continue
        val lat = c.lat
        val lon = c.lon
        val nome = c.nome
        when {
            c.tipo == "posicao" && lat != null && lon != null -> vista = vista.copy(lat = lat, lon = lon)
            c.tipo == "nome" && !nome.isNullOrBlank() -> vista = vista.copy(nome = nome)
            c.tipo == "ja_nao_existe" -> jaNaoExiste = true
        }
    }
    return vista to jaNaoExiste
}

/** O nome certo como o servidor o aceita (cortado, 1 a 80 caracteres), ou null. Conta pontos de código, não unidades UTF-16: um emoji é 1. */
internal fun nomeDaCorrecao(texto: String): String? {
    val nome = texto.trim()
    return nome.takeIf { it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_NOME }
}

// O campo do nome é de uma só linha: quebras de linha coladas viram espaços, e corta-se aos 80 pontos de código (sem partir um a meio).
private fun String.paraOCampoDoNome(): String {
    val linha = replace(CONTROLO, " ")
    return if (linha.codePointCount(0, linha.length) <= MAX_NOME) linha else linha.substring(0, linha.offsetByCodePoints(0, MAX_NOME))
}

/** As três coisas que se podem corrigir numa paragem. */
internal enum class OpcaoCorrigir(@StringRes val texto: Int, val icone: ImageVector) {
    POSICAO(R.string.corrigir_posicao, Icones.Posicao),
    NOME(R.string.corrigir_nome, Icones.Lapis),
    JA_NAO_EXISTE(R.string.corrigir_ja_nao_existe, Icones.Fechar),
}

/** O erro de uma correção que falhou: o limite diário tem texto próprio, o resto é o de toda a comunidade (a pausa primeiro). */
@StringRes
private fun erroDaCorrecao(f: Resultado.Falha): Int = if (f.erro == "muitos_pedidos") R.string.corrigir_limite else erroDaComunidade(f)

/**
 * O "Corrigir dados" do ecrã da paragem: o botão (só com os interruptores `comunidade` e `correcoes` ligados), a folha e o envio.
 * Uma correção fica em revisão até o autor a aprovar; só então muda o que se vê (ver [aplicarCorrecoes]). Falhando o envio a folha
 * fica aberta, com o que a pessoa escreveu. Um 403 `desligado` (a config da app é a antiga) esconde o botão até a config mudar.
 * [mapa] desenha o mapa da folha: por omissão o verdadeiro, que um teste troca por um falso.
 */
@Composable
fun CorrigirNaParagem(
    paragem: Paragem?,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    aoAbrirConta: () -> Unit,
    mapa: @Composable (centro: LatLng, aoMover: (LatLng) -> Unit) -> Unit = { centro, aoMover -> MapaDaCorrecao(centro, aoMover) },
) {
    val contexto = LocalContext.current
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    var desligada by remember(config) { mutableStateOf(false) }
    val publicar = rememberPublicar(comunidade, aoAbrirConta, R.string.corrigir_entrar_texto)
    var folha by rememberSaveable { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    if (!(config.interruptores.comunidade && config.interruptores.correcoes) || desligada) return

    // Precisa das coordenadas da paragem (o mapa abre nelas): enquanto ela não carrega, não se corrige.
    BotaoSecundario({ folha = true }, Modifier.padding(top = 16.dp).fillMaxWidth(), ativo = paragem != null) {
        Text(stringResource(R.string.corrigir_dados))
    }
    if (folha && paragem != null) {
        FolhaCorrigir(paragem, ocupado = ocupado, mapa = mapa, aoFechar = { folha = false }, aoEnviar = { correcao ->
            // "Ocupado" vale só enquanto o pedido corre: se a resposta for um diálogo (entrar, termos), a folha volta a ficar livre.
            publicar.correr({
                ocupado = true
                try {
                    comunidade.corrigir(paragem.ref.chave, correcao)
                } finally {
                    ocupado = false
                }
            }) { r ->
                when {
                    r is Resultado.Ok -> {
                        avisar(contexto, R.string.corrigir_enviada)
                        folha = false
                    }
                    r is Resultado.Falha && r.erro == "desligado" -> {
                        desligada = true
                        folha = false
                        avisar(contexto, R.string.corrigir_desligada)
                    }
                    r is Resultado.Falha -> avisar(contexto, erroDaCorrecao(r))
                }
            }
        })
    }
}

/**
 * A folha "Corrigir dados": escolhe-se o que está errado (a posição, o nome, ou que a paragem já não existe) e envia-se. [aoEnviar]
 * recebe a correção; enquanto [ocupado] (um envio a correr) não deixa enviar outra vez. [mapa] recebe o centro com que abre (a
 * posição que os dados dão à paragem) e avisa por [aoMover] cada vez que a pessoa o desloca; o ponto fixo ao centro é desenhado aqui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaCorrigir(
    paragem: Paragem,
    aoEnviar: (Correcao) -> Unit,
    aoFechar: () -> Unit,
    mapa: @Composable (centro: LatLng, aoMover: (LatLng) -> Unit) -> Unit,
    ocupado: Boolean = false,
) {
    // Largura própria em vez da do sistema: com campos de texto, a janela com a largura do sistema nunca fica quieta nos testes do
    // Robolectric (como no DialogoDenunciar). A margem do ecrã põe-se aqui; o cartão tem no máximo 560 dp, como o AlertDialog.
    BasicAlertDialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FolhaCorrigirConteudo(paragem, aoEnviar, aoFechar, ocupado, mapa, Modifier.padding(horizontal = 24.dp))
    }
}

/** O cartão da folha, à parte da janela (as capturas de ecrã não apanham janelas); [opcaoInicial] abre já numa das três. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolhaCorrigirConteudo(
    paragem: Paragem,
    aoEnviar: (Correcao) -> Unit,
    aoFechar: () -> Unit,
    ocupado: Boolean,
    mapa: @Composable (centro: LatLng, aoMover: (LatLng) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    opcaoInicial: OpcaoCorrigir? = null,
) {
    var opcao by rememberSaveable { mutableStateOf(opcaoInicial) }
    var nome by rememberSaveable { mutableStateOf("") }
    var centro by rememberSaveable { mutableStateOf(LatLng(paragem.lat, paragem.lon)) }
    val escolhida = opcao
    val nomeCerto = nomeDaCorrecao(nome)
    Surface(
        modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            Text(stringResource(escolhida?.texto ?: R.string.corrigir_titulo), style = MaterialTheme.typography.headlineSmall)
            if (escolhida == OpcaoCorrigir.POSICAO) {
                // O mapa tem de ficar fora de uma coluna que rola: arrastá-lo na vertical faria rolar a folha em vez de o deslocar.
                // Ocupa o que sobra (até 260 dp), para os botões ficarem sempre à vista.
                Text(stringResource(R.string.corrigir_mapa_texto), Modifier.padding(top = 16.dp))
                // O contorno só se desenha: o mapa é uma SurfaceView, que ignora o recorte.
                Box(
                    Modifier.fillMaxWidth().padding(top = 12.dp).weight(1f, fill = false).heightIn(max = 260.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    mapa(centro) { centro = it }
                    PontoFixo(Modifier.align(Alignment.Center))
                }
            } else {
                // Com letra grande o conteúdo rola e os botões de baixo ficam sempre à vista.
                Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                    when (escolhida) {
                        null -> OpcaoCorrigir.entries.forEach { o ->
                            BotaoSecundario({ opcao = o }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Icon(o.icone, null, Modifier.padding(end = 8.dp))
                                Text(stringResource(o.texto), Modifier.weight(1f))
                            }
                        }
                        OpcaoCorrigir.NOME -> OutlinedTextField(
                            nome, { nome = it.paraOCampoDoNome() }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.corrigir_nome_campo)) }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        OpcaoCorrigir.JA_NAO_EXISTE -> Text(stringResource(R.string.corrigir_ja_nao_existe_texto))
                        OpcaoCorrigir.POSICAO -> Unit
                    }
                }
            }
            // Com letra grande os botões quebram em vez de um sair do ecrã.
            FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
                if (escolhida != null) {
                    BotaoPrincipal(
                        aoTocar = {
                            when (escolhida) {
                                OpcaoCorrigir.POSICAO -> aoEnviar(Correcao.Posicao(centro.latitude, centro.longitude))
                                OpcaoCorrigir.NOME -> nomeCerto?.let { aoEnviar(Correcao.Nome(it)) }
                                OpcaoCorrigir.JA_NAO_EXISTE -> aoEnviar(Correcao.JaNaoExiste)
                            }
                        },
                        ativo = !ocupado && (escolhida != OpcaoCorrigir.NOME || nomeCerto != null),
                    ) { Text(stringResource(R.string.corrigir_enviar)) }
                }
            }
        }
    }
}

/** O ponto fixo ao centro do mapa: é o sítio que a pessoa arrasta até à paragem. Desenhado aqui (um "⌖" em texto depende da letra do telemóvel). */
@Composable
private fun PontoFixo(modifier: Modifier = Modifier) {
    val descricao = stringResource(R.string.corrigir_ponto)
    // As cores da paragem de saída no mapa (Mapa.kt), para se perceber que é o ponto a pôr no sítio.
    Box(
        modifier.size(24.dp).background(Azulejo.Eletrico, CircleShape)
            .border(3.dp, Azulejo.Tinta, CircleShape)
            .semantics { contentDescription = descricao },
    )
}

/**
 * O mapa verdadeiro da folha. O centro vem do meio da zona à vista (o que o [Mapa] dá ao parar a câmara); a esta escala a
 * diferença para o centro da câmara é de milímetros.
 */
@Composable
private fun MapaDaCorrecao(centro: LatLng, aoMover: (LatLng) -> Unit) {
    Mapa(
        paragens = emptyList(), enquadrar = emptyList(), veiculos = emptyList(), coresLinhas = emptyMap(), escuro = LocalEscuro.current,
        centroInicial = centro, camaraInicial = null, centrarEm = null,
        aoMoverCamara = { minLat, minLon, maxLat, maxLon, _ -> aoMover(LatLng((minLat + maxLat) / 2, (minLon + maxLon) / 2)) },
        aoTocarParagem = {}, aoTocarVeiculo = {}, modifier = Modifier.fillMaxSize(),
    )
}
