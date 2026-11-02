package pt.pixellusitano.paragemcerta.atualizar

enum class Decisao { NADA, ESPERAR, INSTALAR }

/**
 * Só instala com a opção ligada e uma versão nova; com a app em uso espera, para a instalação não a matar a meio (ao abrir a app,
 * ESPERAR quer dizer "descarrega já, instala quando saíres"). O mesmo com uma viagem por acabar ([emViagem]): instalar mata o
 * processo :trip e com ele o aviso da paragem; e com um aviso de chegada a correr ([comAviso]), que a instalação também mataria.
 * Se essa versão já está descarregada à espera de confirmação ([pendente]), não repete a transferência nem abre outra sessão.
 */
fun decidir(
    nova: NovaVersao?, ligado: Boolean, emPrimeiroPlano: Boolean, pendente: String? = null, emViagem: Boolean = false, comAviso: Boolean = false,
): Decisao = when {
    !ligado || nova == null || nova.tag == pendente -> Decisao.NADA
    emPrimeiroPlano || emViagem || comAviso -> Decisao.ESPERAR
    else -> Decisao.INSTALAR
}
