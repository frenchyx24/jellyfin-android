package org.jellyfin.mobile.nflix.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import org.jellyfin.mobile.nflix.data.BookFile
import org.jellyfin.mobile.nflix.ui.EmptyState
import org.jellyfin.mobile.nflix.ui.Nflix
import java.util.Locale

/** Page colours of the reader. [dark] also drives the PDF/comic night filter and the chrome. */
internal enum class ReaderTheme(val label: String, val background: Color, val text: Color, val dark: Boolean) {
    Day("Blanc", Color(0xFFFFFFFF), Color(0xFF1A1A1A), false),
    Sepia("Sépia", Color(0xFFF4ECD8), Color(0xFF5B4636), false),
    Grey("Gris", Color(0xFF4A4A4F), Color(0xFFEDEDED), true),
    Night("Nuit", Color(0xFF14141A), Color(0xFFDADAE0), true),
    Black("Noir", Color(0xFF000000), Color(0xFFBFBFBF), true),
}

internal enum class ReaderFont(val label: String, val css: String, val compose: FontFamily) {
    Serif("Classique", "Georgia, 'Noto Serif', serif", FontFamily.Serif),
    Sans("Moderne", "'Roboto', 'Noto Sans', sans-serif", FontFamily.SansSerif),
    Mono("Machine", "'Roboto Mono', monospace", FontFamily.Monospace),
    Book("Livre", "'Book Antiqua', Palatino, 'Noto Serif', serif", FontFamily.Serif),
}

/** Optional text tint over the theme default; null keeps the theme's colour. */
internal val ReaderTextColors = listOf<Color?>(null, Color(0xFF1A1A1A), Color(0xFFFFFFFF), Color(0xFFF5DEB3), Color(0xFFB8D8FF), Color(0xFFA8E6B0), Color(0xFFFFB3B3))

internal data class ReaderPrefs(
    val theme: ReaderTheme = ReaderTheme.Night,
    val textColor: Color? = null,
    val font: ReaderFont = ReaderFont.Serif,
    val size: Int = 110,
    val lineHeight: Float = 1.6f,
    val margin: Int = 22,
    val justify: Boolean = true,
) {
    val text: Color get() = textColor ?: theme.text

    /** Stylesheet forced over the book's own CSS, so no publisher colour can make text unreadable. */
    fun css(): String {
        val bg = hex(theme.background)
        val fg = hex(text)
        val link = if (theme.dark) "#ff6b75" else "#b20710"
        return "html,body{background:$bg!important;color:$fg!important;font-family:${font.css}!important;" +
            "font-size:$size%!important;line-height:$lineHeight!important;}" +
            "body{padding:72px ${margin}px 120px ${margin}px!important;margin:0 auto!important;max-width:760px;" +
            "text-align:${if (justify) "justify" else "left"}!important;hyphens:auto;-webkit-hyphens:auto;}" +
            "*{color:$fg!important;background-color:transparent!important;border-color:${fg}33!important;}" +
            "p,div,span,li{font-family:inherit!important;line-height:inherit!important;}" +
            "a,a *{color:$link!important;}" +
            "img,svg,video{max-width:100%!important;height:auto!important;}" +
            "h1,h2,h3{line-height:1.25!important;text-align:left!important;}"
    }

    companion object {
        private const val PREFS = "nflix_reading"
        fun load(context: Context): ReaderPrefs {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ReaderPrefs(
                theme = ReaderTheme.entries.getOrNull(p.getInt("theme", ReaderTheme.Night.ordinal)) ?: ReaderTheme.Night,
                textColor = p.getLong("text", 0L).takeIf { it != 0L }?.let { Color(it.toULong()) },
                font = ReaderFont.entries.getOrNull(p.getInt("family", 0)) ?: ReaderFont.Serif,
                size = p.getInt("font", 110).coerceIn(70, 220),
                lineHeight = p.getFloat("line", 1.6f).coerceIn(1.2f, 2.2f),
                margin = p.getInt("margin", 22).coerceIn(8, 56),
                justify = p.getBoolean("justify", true),
            )
        }
    }

    fun save(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putInt("theme", theme.ordinal)
        putLong("text", textColor?.value?.toLong() ?: 0L)
        putInt("family", font.ordinal)
        putInt("font", size)
        putFloat("line", lineHeight)
        putInt("margin", margin)
        putBoolean("justify", justify)
    }
}

private fun hex(color: Color) = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

/** Scroll position inside the current chapter, so reopening a book lands on the same paragraph. */
private object ChapterScroll {
    private fun prefs(context: Context) = context.getSharedPreferences("nflix_reading", Context.MODE_PRIVATE)
    fun get(context: Context, id: String) = prefs(context).getFloat("scroll-$id", 0f)
    fun set(context: Context, id: String, value: Float) = prefs(context).edit { putFloat("scroll-$id", value) }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubReader(id: String, book: BookFile.Epub, prefs: ReaderPrefs, onPrefs: (ReaderPrefs) -> Unit, chrome: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    if (book.chapters.isEmpty()) {
        EmptyState(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.FormatListBulleted, "Livre vide", null)
        return
    }
    var chapter by remember { mutableIntStateOf(ReadingProgress.position(context, id).coerceIn(0, book.chapters.lastIndex)) }
    var inChapter by remember { mutableFloatStateOf(ChapterScroll.get(context, id)) }
    var restore by remember { mutableFloatStateOf(inChapter) }
    var showSettings by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    val currentChapter by rememberUpdatedState(chapter)
    val tap by rememberUpdatedState(onTap)

    LaunchedEffect(chapter, inChapter) {
        val overall = (chapter + inChapter.coerceIn(0f, 1f)) / book.chapters.size
        ReadingProgress.save(context, id, chapter, overall)
        ChapterScroll.set(context, id, inChapter)
    }
    val css = prefs.css()

    Box(Modifier.fillMaxSize().background(prefs.theme.background)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(prefs.theme.background.toArgb())
                    settings.allowFileAccess = true
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = false
                    isVerticalScrollBarEnabled = false
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun scrolled(fraction: Float) { post { inChapter = fraction.coerceIn(0f, 1f) } }
                        },
                        "NflixReader",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val target = restore
                            restore = 0f
                            view.evaluateJavascript(
                                "(function(){var s=document.getElementById('nflix-css')||document.createElement('style');s.id='nflix-css';" +
                                    "s.innerHTML=${org.json.JSONObject.quote((view.tag as? String).orEmpty())};(document.head||document.documentElement).appendChild(s);" +
                                    "var max=function(){return Math.max(1,document.documentElement.scrollHeight-window.innerHeight)};" +
                                    "setTimeout(function(){window.scrollTo(0,max()*$target)},60);" +
                                    "window.addEventListener('scroll',function(){NflixReader.scrolled(window.scrollY/max())},{passive:true});})();",
                                null,
                            )
                        }
                    }
                    // Tap zones: left third = previous page, right third = next page, centre = menus.
                    val detector = GestureDetector(
                        ctx,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val third = width / 3f
                                when {
                                    e.x < third -> turnPage(this@apply, forward = false) { if (currentChapter > 0) { restore = 1f; chapter = currentChapter - 1 } }
                                    e.x > width - third -> turnPage(this@apply, forward = true) { if (currentChapter < book.chapters.lastIndex) { restore = 0f; chapter = currentChapter + 1 } }
                                    else -> tap()
                                }
                                return true
                            }
                        },
                    )
                    setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
                }
            },
            update = { view ->
                view.tag = css
                view.setBackgroundColor(prefs.theme.background.toArgb())
                val url = "file://" + book.chapters[chapter].absolutePath
                if (view.url != url) {
                    view.loadUrl(url)
                } else {
                    view.evaluateJavascript("(function(){var s=document.getElementById('nflix-css');if(s){s.innerHTML=${org.json.JSONObject.quote(css)};}})();", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            chrome, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val panel = if (prefs.theme.dark) Color(0xF01C1C22) else Color(0xF0FFFFFF)
            val ink = if (prefs.theme.dark) Color.White else Color(0xFF1A1A1A)
            Column(
                Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(panel).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val overall = (chapter + inChapter.coerceIn(0f, 1f)) / book.chapters.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapitre ${chapter + 1} sur ${book.chapters.size}", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    Text("${(overall * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
                }
                Slider(
                    value = chapter.toFloat(),
                    onValueChange = { restore = 0f; chapter = it.toInt().coerceIn(0, book.chapters.lastIndex) },
                    valueRange = 0f..book.chapters.lastIndex.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Nflix.Red, activeTrackColor = Nflix.Red, inactiveTrackColor = ink.copy(alpha = 0.15f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (chapter > 0) { restore = 0f; chapter-- } }, enabled = chapter > 0) { Icon(Icons.AutoMirrored.Rounded.NavigateBefore, "Chapitre précédent", tint = ink) }
                    IconButton(onClick = { contents = true }) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "Chapitres", tint = ink) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.TextFields, "Affichage", tint = ink) }
                    IconButton(onClick = { if (chapter < book.chapters.lastIndex) { restore = 0f; chapter++ } }, enabled = chapter < book.chapters.lastIndex) { Icon(Icons.AutoMirrored.Rounded.NavigateNext, "Chapitre suivant", tint = ink) }
                }
            }
        }

        if (showSettings) ReaderSettingsSheet(prefs, onPrefs) { showSettings = false }
        if (contents) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { contents = false }, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (chapter - 3).coerceAtLeast(0))
                Text("Chapitres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                LazyColumn(state = list, modifier = Modifier.navigationBarsPadding().height(420.dp)) {
                    itemsIndexed(book.chapters) { index, file ->
                        Row(
                            Modifier.fillMaxWidth().clickable { restore = 0f; chapter = index; contents = false }
                                .background(if (index == chapter) Nflix.Red.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", color = if (index == chapter) Nflix.Red else Nflix.Muted, modifier = Modifier.width(36.dp))
                            Text(chapterLabel(file.nameWithoutExtension, index), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (index < chapter) Icon(Icons.Rounded.Check, "Lu", tint = Nflix.Success, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** File names such as "Tome 3_split_004" or "Section0004" become "Partie 4". */
private fun chapterLabel(name: String, index: Int): String {
    val cleaned = name.replace('_', ' ').replace('-', ' ').trim()
    return if (cleaned.isEmpty() || Regex("(?i)(split|section|chapter|part|text|index)\\s*\\d+$").containsMatchIn(cleaned) || cleaned.length > 60) "Partie ${index + 1}" else cleaned
}

/** Scrolls one screen; at a chapter edge, moves to the neighbouring chapter instead. */
private fun turnPage(view: WebView, forward: Boolean, atEdge: () -> Unit) {
    val step = (view.height * 0.92f).toInt()
    val js = if (forward) {
        "(function(){var m=document.documentElement.scrollHeight-window.innerHeight;if(window.scrollY>=m-4){return 'edge'}window.scrollBy({top:$step,behavior:'smooth'});return 'ok'})()"
    } else {
        "(function(){if(window.scrollY<=4){return 'edge'}window.scrollBy({top:-$step,behavior:'smooth'});return 'ok'})()"
    }
    view.evaluateJavascript(js) { result -> if (result?.contains("edge") == true) atEdge() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(prefs: ReaderPrefs, onPrefs: (ReaderPrefs) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun update(next: ReaderPrefs) { next.save(context); onPrefs(next) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Nflix.SurfaceHigh) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Live preview of the current settings.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(prefs.theme.background).padding(16.dp)) {
                Text(
                    "Il était une fois, au bord d'une rivière tranquille, un lecteur qui choisissait ses couleurs.",
                    color = prefs.text, fontFamily = prefs.font.compose, fontSize = (15 * prefs.size / 100f).sp,
                    lineHeight = (15 * prefs.size / 100f * prefs.lineHeight).sp,
                )
            }
            Label("Fond")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ReaderTheme.entries) { theme ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(theme.background)
                                .border(if (theme == prefs.theme) 3.dp else 1.dp, if (theme == prefs.theme) Nflix.Red else Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { update(prefs.copy(theme = theme)) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Aa", color = theme.text, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp))
                        Text(theme.label, style = MaterialTheme.typography.labelSmall, color = Nflix.Muted)
                    }
                }
            }
            Label("Couleur du texte")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ReaderTextColors) { color ->
                    val selected = color == prefs.textColor
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(color ?: prefs.theme.background)
                            .border(if (selected) 3.dp else 1.dp, if (selected) Nflix.Red else Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { update(prefs.copy(textColor = color)) },
                        contentAlignment = Alignment.Center,
                    ) { if (color == null) Text("Auto", fontSize = 9.sp, color = prefs.theme.text, fontWeight = FontWeight.Bold) }
                }
            }
            Label("Police")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReaderFont.entries) { font ->
                    val selected = font == prefs.font
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Nflix.Red else Nflix.Surface).clickable { update(prefs.copy(font = font)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(font.label, fontFamily = font.compose, color = Color.White) }
                }
            }
            Stepper("Taille du texte", "${prefs.size} %", { update(prefs.copy(size = (prefs.size - 10).coerceAtLeast(70))) }, { update(prefs.copy(size = (prefs.size + 10).coerceAtMost(220))) })
            Stepper(
                "Interligne", String.format(Locale.FRANCE, "%.1f", prefs.lineHeight),
                { update(prefs.copy(lineHeight = (prefs.lineHeight - 0.1f).coerceAtLeast(1.2f))) },
                { update(prefs.copy(lineHeight = (prefs.lineHeight + 0.1f).coerceAtMost(2.2f))) },
            )
            Stepper("Marges", "${prefs.margin} px", { update(prefs.copy(margin = (prefs.margin - 6).coerceAtLeast(8))) }, { update(prefs.copy(margin = (prefs.margin + 6).coerceAtMost(56))) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alignement", modifier = Modifier.weight(1f))
                IconButton(onClick = { update(prefs.copy(justify = false)) }) { Icon(Icons.Rounded.FormatAlignLeft, "Aligné à gauche", tint = if (!prefs.justify) Nflix.Red else Nflix.Muted) }
                IconButton(onClick = { update(prefs.copy(justify = true)) }) { Icon(Icons.Rounded.FormatAlignJustify, "Justifié", tint = if (prefs.justify) Nflix.Red else Nflix.Muted) }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = Nflix.Muted)

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "Diminuer") }
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "Augmenter") }
    }
}
