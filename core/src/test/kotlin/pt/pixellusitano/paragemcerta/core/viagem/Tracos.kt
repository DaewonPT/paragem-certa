package pt.pixellusitano.paragemcerta.core.viagem

import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/** Uma posição de uma viagem de teste, s segundos depois do início; precisao em metros. */
data class Posicao(val s: Double, val lat: Double, val lon: Double, val precisao: Float = 8f)

/** Um passo do motor: a entrada, o estado antes e depois, e os eventos que deu. */
data class Passo(val entrada: Entrada, val antes: EstadoViagem, val depois: EstadoViagem, val eventos: List<Evento>)

fun List<Posicao>.mais(s: Double) = map { it.copy(s = it.s + s) }

/** Viagens de teste: geradas pelo código (as únicas que entram no repositório) ou lidas das gravações reais do autor. */
object Tracos {
    private const val RAIO_TERRA = 6_371_000.0
    private const val INTERVALO_GPS = 2.0
    private const val INTERVALO_RELOGIO = 5L
    private const val SALTO_M = 300.0

    /** As paragens da etapa; numa etapa a pé, a origem e o destino dela. */
    fun paragensDe(etapa: Etapa): List<ParagemEtapa> = etapa.paragens.ifEmpty {
        listOf(
            ParagemEtapa(null, etapa.de.nome, etapa.de.lat, etapa.de.lon, etapa.partida),
            ParagemEtapa(null, etapa.para.nome, etapa.para.lat, etapa.para.lon, etapa.chegada),
        )
    }

    /**
     * Posições a cada intervaloS (2 s) de quem faz a etapa a vMs, parado paragemS em cada paragem intermédia; s = 0 na primeira paragem.
     * ruidoM é o desvio padrão (por eixo) do erro; saltos a fração de posições atiradas 300 m ao lado; buraco os
     * segundos sem posição (túnel). A precisão anunciada acompanha o ruído.
     */
    fun sintetico(
        etapa: Etapa, vMs: Double = 7.0, paragemS: Int = 20, ruidoM: Double = 0.0, saltos: Double = 0.0,
        buraco: IntRange? = null, semente: Int = 1, intervaloS: Double = INTERVALO_GPS,
    ): List<Posicao> {
        val paragens = paragensDe(etapa)
        val linha = etapa.desenho.ifEmpty { paragens.map { it.lat to it.lon } }
        val a = Percurso(linha, paragens).noPercurso
        // (segundos, metros) onde o andamento muda: chega a cada paragem e, nas intermédias, parte paragemS depois.
        val nos = mutableListOf(0.0 to a[0])
        for (k in 1 until a.size) {
            val chega = nos.last().first + (a[k] - a[k - 1]) / vMs
            nos += chega to a[k]
            if (k < a.lastIndex) nos += (chega + paragemS) to a[k]
        }
        // O mesmo plano local do Percurso, para as posições caírem onde ele as mede.
        val lat0 = linha.first().first
        val lon0 = linha.first().second
        val mLat = Math.toRadians(1.0) * RAIO_TERRA
        val mLon = mLat * cos(Math.toRadians(lat0))
        val xs = linha.map { (it.second - lon0) * mLon }
        val ys = linha.map { (it.first - lat0) * mLat }
        val acumulado = DoubleArray(linha.size).also { for (i in 1 until it.size) it[i] = it[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) }
        val rnd = Random(semente.toLong())
        val precisao = maxOf(8.0, ruidoM * 1.5).toFloat()
        return generateSequence(0.0) { it + intervaloS }.takeWhile { it <= nos.last().first }.mapNotNull { s ->
            val m = entre(nos, s)
            val i = (1 until linha.size).firstOrNull { m <= acumulado[it] } ?: linha.lastIndex
            val vao = acumulado[i] - acumulado[i - 1]
            val t = if (vao > 0) ((m - acumulado[i - 1]) / vao).coerceIn(0.0, 1.0) else 0.0
            var x = xs[i - 1] + (xs[i] - xs[i - 1]) * t + rnd.nextGaussian() * ruidoM
            var y = ys[i - 1] + (ys[i] - ys[i - 1]) * t + rnd.nextGaussian() * ruidoM
            if (rnd.nextDouble() < saltos) {
                val angulo = rnd.nextDouble() * 2 * PI
                x += SALTO_M * cos(angulo)
                y += SALTO_M * sin(angulo)
            }
            if (buraco != null && s.toInt() in buraco) null else Posicao(s, lat0 + y / mLat, lon0 + x / mLon, precisao)
        }.toList()
    }

    /** Parado em (lat, lon), uma posição a cada 2 s em [de, ate[. */
    fun parado(lat: Double, lon: Double, de: Double, ate: Double): List<Posicao> =
        generateSequence(de) { it + INTERVALO_GPS }.takeWhile { it < ate }.map { Posicao(it, lat, lon) }.toList()

    /**
     * Corre a viagem desde inicio com as posições, as entradas extra e um Relogio a cada 5 s, até ela terminar
     * (no máximo até à guarda de chegada + 30 min, ou até à última posição ou entrada extra, se for depois).
     */
    fun correr(
        viagem: Viagem, inicio: Instant, posicoes: List<Posicao>, extras: List<Entrada> = emptyList(),
        estado: EstadoViagem = viagem.inicio(inicio),
    ): List<Passo> {
        val gps = posicoes.map { Entrada.Gps(it.lat, it.lon, it.precisao, inicio.plusMillis((it.s * 1000).roundToLong())) }
        val fim = ((gps + extras).map { it.hora } + viagem.plano.chegada.plus(Duration.ofMinutes(31))).max()
        val relogio = generateSequence(inicio) { it.plusSeconds(INTERVALO_RELOGIO) }.takeWhile { it <= fim }.map { Entrada.Relogio(it) }
        var e = estado
        val passos = mutableListOf<Passo>()
        for (x in (gps + extras + relogio).sortedBy { it.hora }) {
            if (e.terminada) break
            val (depois, eventos) = viagem.passo(e, x)
            passos += Passo(x, e, depois, eventos)
            e = depois
        }
        return passos
    }

    /**
     * Uma gravação real: `s,lat,lon,acc` por linha, com s em segundos desde 1970 (a hora da posição) e `#` nos
     * comentários. Devolve a hora da primeira posição e as posições contadas a partir dela.
     */
    fun ler(csv: File): Pair<Instant, List<Posicao>> {
        val posicoes = csv.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { linha ->
            val c = linha.split(',').map { it.trim() }
            val s = c.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            if (c.size < 4) null else Posicao(s, c[1].toDouble(), c[2].toDouble(), c[3].toFloat())
        }
        require(posicoes.isNotEmpty()) { "${csv.name} não tem posições" }
        val zero = posicoes.first().s
        return Instant.ofEpochMilli((zero * 1000).roundToLong()) to posicoes.mais(-zero)
    }

    private fun entre(nos: List<Pair<Double, Double>>, s: Double): Double {
        val k = (1 until nos.size).firstOrNull { s <= nos[it].first } ?: return nos.last().second
        val vao = nos[k].first - nos[k - 1].first
        if (vao <= 0.0) return nos[k].second
        return nos[k - 1].second + (nos[k].second - nos[k - 1].second) * ((s - nos[k - 1].first) / vao)
    }
}
