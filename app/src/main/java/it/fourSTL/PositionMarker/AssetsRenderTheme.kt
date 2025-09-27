package it.fourSTL.PositionMarker

import android.content.Context
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import java.io.IOException
import java.io.InputStream

class AssetsRenderTheme(private val context: Context) : XmlRenderTheme {

    private var menuCallback: XmlRenderThemeMenuCallback? = null
    private var resourceProvider: XmlThemeResourceProvider? = null

    override fun getMenuCallback(): XmlRenderThemeMenuCallback? = menuCallback

    override fun setMenuCallback(menuCallback: XmlRenderThemeMenuCallback?) {
        this.menuCallback = menuCallback
    }

    override fun getResourceProvider(): XmlThemeResourceProvider? = resourceProvider

    override fun setResourceProvider(resourceProvider: XmlThemeResourceProvider?) {
        this.resourceProvider = resourceProvider
    }

    override fun getRelativePathPrefix(): String = "" // nessun prefisso richiesto

    override fun getRenderThemeAsStream(): InputStream {
        return try {
            context.assets.open("renderTheme.xml")
        } catch (e: IOException) {
            throw RuntimeException("Errore durante il caricamento di renderTheme.xml", e)
        }
    }
}
