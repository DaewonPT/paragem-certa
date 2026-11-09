package pt.pixellusitano.paragemcerta.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import pt.pixellusitano.paragemcerta.R
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** A posição e as ligações do widget ficam só no telemóvel: o ficheiro "widgets" sai das cópias de segurança. */
@RunWith(RobolectricTestRunner::class)
class CopiaDeSegurancaTest {
    private val contexto = RuntimeEnvironment.getApplication()

    /** As exclusões de um XML de regras, cada uma com a secção onde está (a etiqueta de cima). */
    private fun exclusoes(xml: Int): List<Triple<String, String?, String?>> {
        val p = contexto.resources.getXml(xml)
        val pilha = ArrayDeque<String>()
        val achadas = mutableListOf<Triple<String, String?, String?>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    if (p.name == "exclude") achadas += Triple(pilha.last(), p.getAttributeValue(null, "domain"), p.getAttributeValue(null, "path"))
                    pilha.addLast(p.name)
                }
                XmlPullParser.END_TAG -> pilha.removeLast()
            }
        }
        return achadas
    }

    @Test fun copiaAteAoAndroid11DeixaOsWidgetsDeFora() {
        assertEquals(listOf(Triple("full-backup-content", "sharedpref", "widgets.xml")), exclusoes(R.xml.regras_copia))
    }

    @Test fun copiaETransferenciaDoAndroid12DeixamOsWidgetsDeFora() {
        assertEquals(
            listOf(Triple("cloud-backup", "sharedpref", "widgets.xml"), Triple("device-transfer", "sharedpref", "widgets.xml")),
            exclusoes(R.xml.regras_extracao),
        )
    }

    // O ApplicationInfo do Robolectric não preenche o dataExtractionRules: lê-se o manifesto.
    @Test fun oManifestoLigaAsDuasRegras() {
        val config = Properties().apply {
            CopiaDeSegurancaTest::class.java.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")?.use(::load)
        }
        val fundido = config.getProperty("android_merged_manifest")?.let(::File)?.takeIf { it.isFile }
        val manifesto = fundido ?: File("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifesto)
        val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("@xml/regras_copia", app.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/regras_extracao", app.getAttributeNS(android, "dataExtractionRules"))
    }
}
