import java.awt.AlphaComposite
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Generates the Nflix branding resource overlay at build time.
 *
 * The overlay is registered as a product flavor resource directory so it overrides
 * the upstream Jellyfin resources without touching them:
 * - launcher icons (legacy + adaptive), notification icon, splash and logo
 * - brand colors
 * - every string containing "Jellyfin" in every locale is rewritten to "Nflix"
 */
object NflixBranding {
    private const val BRAND = "Nflix"
    private val densities = linkedMapOf(
        "mdpi" to 1f,
        "hdpi" to 1.5f,
        "xhdpi" to 2f,
        "xxhdpi" to 3f,
        "xxxhdpi" to 4f,
    )

    fun generate(logoBase64: File, mainRes: File, out: File) {
        out.deleteRecursively()
        out.mkdirs()
        val logo = ImageIO.read(Base64.getMimeDecoder().decode(logoBase64.readText().trim()).inputStream())
            ?: error("Invalid Nflix logo")

        writePng(logo, File(out, "drawable-nodpi/nflix_logo.png"))
        for ((name, scale) in densities) {
            writePng(legacyIcon(logo, (48 * scale).roundToInt(), round = false), File(out, "mipmap-$name/ic_launcher.png"))
            writePng(legacyIcon(logo, (48 * scale).roundToInt(), round = true), File(out, "mipmap-$name/ic_launcher_round.png"))
            writePng(adaptiveForeground(logo, (108 * scale).roundToInt()), File(out, "mipmap-$name/nflix_launcher_fg.png"))
            writePng(notificationIcon((24 * scale).roundToInt()), File(out, "drawable-$name/ic_notification.png"))
        }
        // ldpi notification icon exists upstream, override it too
        writePng(notificationIcon(18), File(out, "drawable-ldpi/ic_notification.png"))

        val adaptive = """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/nflix_launcher_bg" />
                <foreground android:drawable="@mipmap/nflix_launcher_fg" />
                <monochrome android:drawable="@drawable/nflix_monochrome" />
            </adaptive-icon>
        """.trimIndent()
        write(File(out, "mipmap-anydpi-v26/ic_launcher.xml"), adaptive)
        write(File(out, "mipmap-anydpi-v26/ic_launcher_round.xml"), adaptive)

        write(
            File(out, "drawable/nflix_launcher_bg.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                <gradient android:type="radial" android:gradientRadius="80dp" android:centerX="0.5" android:centerY="0.42"
                    android:startColor="#FF3A0A12" android:centerColor="#FF14060A" android:endColor="#FF050507" />
            </shape>
            """.trimIndent(),
        )
        write(
            File(out, "drawable/nflix_monochrome.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
                <path android:fillColor="#FFFFFFFF" android:pathData="${nPath(108f)}" />
            </vector>
            """.trimIndent(),
        )
        // Upstream foreground vector is still referenced by Android Auto metadata
        write(
            File(out, "drawable/ic_launcher_foreground.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
                <path android:fillColor="#FFE50914" android:pathData="${nPath(108f)}" />
            </vector>
            """.trimIndent(),
        )
        write(
            File(out, "drawable/app_logo.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
                android:src="@drawable/nflix_logo" android:gravity="center" android:antialias="true" />
            """.trimIndent(),
        )
        write(
            File(out, "drawable/ic_splash.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:gravity="center" android:width="160dp" android:height="70dp" android:drawable="@drawable/nflix_logo" />
            </layer-list>
            """.trimIndent(),
        )
        write(
            File(out, "values/nflix_colors.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="jellyfin_primary">#FF121216</color>
                <color name="jellyfin_primary_dark">#FF07070A</color>
                <color name="jellyfin_accent">#FFE50914</color>
                <color name="ic_launcher_background">#FF07070A</color>
                <color name="splash_fill">#FFE50914</color>
                <color name="theme_background">#FF07070A</color>
            </resources>
            """.trimIndent(),
        )

        rebrandStrings(mainRes, out)
    }

    /** Copies every strings file that mentions Jellyfin with the brand replaced. */
    private fun rebrandStrings(mainRes: File, out: File) {
        mainRes.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty().forEach { dir ->
            dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().forEach { file ->
                val text = file.readText()
                if ("Jellyfin" in text) {
                    // Only the visible brand word; identifiers, URLs (lowercase) stay intact
                    write(File(out, "${dir.name}/${file.name}"), text.replace("Jellyfin", BRAND))
                }
            }
        }
    }

    private fun legacyIcon(logo: BufferedImage, size: Int, round: Boolean): BufferedImage {
        val image = canvas(size)
        val g = image.createGraphics().apply { quality() }
        val inset = size * 0.04f
        val shape = if (round) {
            Ellipse2D.Float(inset, inset, size - 2 * inset, size - 2 * inset)
        } else {
            RoundRectangle2D.Float(inset, inset, size - 2 * inset, size - 2 * inset, size * 0.36f, size * 0.36f)
        }
        g.paint = GradientPaint(0f, 0f, Color(0x3A, 0x0A, 0x12), 0f, size.toFloat(), Color(0x05, 0x05, 0x07))
        g.fill(shape)
        g.clip = shape
        drawCentered(g, logo, size, size * 0.78f)
        g.dispose()
        return image
    }

    private fun adaptiveForeground(logo: BufferedImage, size: Int): BufferedImage {
        val image = canvas(size)
        val g = image.createGraphics().apply { quality() }
        // Safe zone is a 66dp circle in a 108dp canvas; the wordmark diagonal stays inside it
        drawCentered(g, logo, size, size * (60f / 108f))
        g.dispose()
        return image
    }

    private fun notificationIcon(size: Int): BufferedImage {
        val image = canvas(size)
        val g = image.createGraphics().apply { quality() }
        g.color = Color.WHITE
        val s = size / 24f
        val p = Polygon()
        nPoints(24f).forEach { (x, y) -> p.addPoint((x * s).roundToInt(), (y * s).roundToInt()) }
        g.fillPolygon(p)
        g.dispose()
        return image
    }

    /** Bold geometric "N", expressed in a square viewport of [size] units. */
    private fun nPoints(size: Float): List<Pair<Float, Float>> {
        val u = size / 24f
        return listOf(
            5f to 4f, 9.6f to 4f, 15f to 14.2f, 15f to 4f, 19f to 4f,
            19f to 20f, 14.4f to 20f, 9f to 9.8f, 9f to 20f, 5f to 20f,
        ).map { (x, y) -> x * u to y * u }
    }

    private fun nPath(size: Float): String {
        // Keep the glyph inside the 66dp adaptive safe zone
        val scale = 0.55f
        val offset = size * (1 - scale) / 2
        return nPoints(size).mapIndexed { i, (x, y) ->
            val cmd = if (i == 0) "M" else "L"
            "$cmd${"%.2f".format(java.util.Locale.ROOT, offset + x * scale)},${"%.2f".format(java.util.Locale.ROOT, offset + y * scale)}"
        }.joinToString(" ") + " Z"
    }

    private fun drawCentered(g: java.awt.Graphics2D, logo: BufferedImage, size: Int, width: Float) {
        val w = width.roundToInt()
        val h = (logo.height * width / logo.width).roundToInt()
        g.composite = AlphaComposite.SrcOver
        g.drawImage(scaled(logo, w, h), (size - w) / 2, (size - h) / 2, null)
    }

    /** Progressive halving keeps downscaled logos crisp. */
    private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
        var current = source
        var w = source.width
        var h = source.height
        while (w / 2 >= width && h / 2 >= height) {
            w /= 2
            h /= 2
            current = resize(current, w, h)
        }
        return resize(current, width, height)
    }

    private fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = canvas(width, height)
        val g = target.createGraphics().apply { quality() }
        g.drawImage(source, 0, 0, width, height, null)
        g.dispose()
        return target
    }

    private fun canvas(width: Int, height: Int = width) = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    private fun java.awt.Graphics2D.quality() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private fun writePng(image: BufferedImage, file: File) {
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun write(file: File, text: String) {
        file.parentFile.mkdirs()
        file.writeText(text + "\n")
    }
}
