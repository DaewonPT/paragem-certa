package pt.pixellusitano.paragemcerta.atualizar

// Serializable: o aviso de versão nova guarda-a com rememberSaveable, para rodar o ecrã não a perder.
data class NovaVersao(val tag: String, val urlApk: String, val notas: String) : java.io.Serializable
