package org.jellyfin.mobile.nflix.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import org.jellyfin.mobile.nflix.data.Item
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius

/**
 * Ambient motion drawn over title pages. Franchises get a signature scene (X-wings, warp, magic,
 * embers, bats...), and every other title falls back to an ambience picked from its genres, so
 * nearly the whole catalogue gets one. Detection is name/genre based: no extra metadata needed.
 */
/** Renderers; each [Ambience] picks one and may recolour it. */
enum class SagaFxKind { XWing, Warp, Magic, Embers, Bats, Lightning, Fog, Snow, Rain, Bubbles, Hearts, Petals, Confetti, Notes, Dust, Code, Leaves, Fireflies, Sparks }

/** One ambience: a renderer, an optional palette, and the title/genre words that select it. */
class Ambience(val name: String, val style: SagaFxKind, val palette: List<Color>?, val titles: List<String>, val genres: List<String>)

object SagaFx {
    val all: List<Ambience> = listOf(
        Ambience("StarWars", SagaFxKind.XWing, null, listOf("star wars", "guerre des étoiles", "mandalorian", "andor", "ahsoka", "obi-wan", "kenobi", "boba fett", "clone wars", "bad batch", "acolyte", "skeleton crew", "rogue one", "solo: a", "jedi", "sith"), emptyList()),
        Ambience("StarTrek", SagaFxKind.Warp, null, listOf("star trek", "picard", "strange new worlds", "lower decks", "discovery", "voyager", "deep space nine"), emptyList()),
        Ambience("Dune", SagaFxKind.Dust, listOf(Color(0xFFFFD08A), Color(0xFFE8A24A), Color(0xFFC9782E)), listOf("dune", "arrakis"), emptyList()),
        Ambience("Avatar", SagaFxKind.Fireflies, listOf(Color(0xFF5CF2FF), Color(0xFF7CFFCB), Color(0xFFB48CFF)), listOf("avatar", "pandora", "na'vi"), emptyList()),
        Ambience("Matrix", SagaFxKind.Code, listOf(Color(0xFF3DFF7A)), listOf("matrix"), emptyList()),
        Ambience("Tron", SagaFxKind.Code, listOf(Color(0xFF4DE9FF), Color(0xFFFF8A3D)), listOf("tron", "cyberpunk", "ghost in the shell", "ready player", "altered carbon"), emptyList()),
        Ambience("Hacker", SagaFxKind.Code, listOf(Color(0xFF5AA9FF)), listOf("mr. robot", "mr robot", "hacker", "black mirror", "the social network", "silicon valley", "westworld", "severance"), emptyList()),
        Ambience("Space", SagaFxKind.Warp, null, listOf("interstellar", "gravity", "the martian", "seul sur mars", "ad astra", "passengers", "apollo", "2001", "alien", "prometheus", "the expanse", "foundation", "battlestar", "stargate", "guardians of the galaxy", "gardiens de la galaxie", "the 100", "lost in space", "moonfall", "armageddon", "space", "galax", "cosmos", "planète", "planet"), listOf("science fiction", "science-fiction", "sci-fi")),
        Ambience("HarryPotter", SagaFxKind.Magic, listOf(Color(0xFFFFE08A), Color(0xFFFFC94D), Color(0xFFFFF4C2)), listOf("harry potter", "fantastic beasts", "animaux fantastiques", "poudlard", "hogwarts"), emptyList()),
        Ambience("Strange", SagaFxKind.Magic, listOf(Color(0xFFFF9A3D), Color(0xFFFFC46B), Color(0xFFFF6A00)), listOf("doctor strange", "docteur strange", "mystic"), emptyList()),
        Ambience("Witcher", SagaFxKind.Magic, listOf(Color(0xFFB48CFF), Color(0xFF7C5CFF), Color(0xFFE0D0FF)), listOf("witcher", "shadow and bone", "sorcier", "wizard", "merlin", "percy jackson", "wicked", "oz", "narnia", "wheel of time", "la roue du temps"), emptyList()),
        Ambience("Disney", SagaFxKind.Magic, listOf(Color(0xFFFFD1F0), Color(0xFFBDE0FE), Color(0xFFFFF3B0)), listOf("disney", "aladdin", "cendrillon", "cinderella", "raiponce", "tangled", "vaiana", "moana", "maléfique", "maleficent", "encanto", "wish", "enchant", "princesse", "princess", "fée", "fairy", "magic", "magie"), listOf("fantasy", "fantastique")),
        Ambience("Frozen", SagaFxKind.Snow, listOf(Color(0xFFDDF4FF), Color(0xFFA9DEFF), Color(0xFFFFFFFF)), listOf("frozen", "reine des neiges", "olaf"), emptyList()),
        Ambience("Christmas", SagaFxKind.Snow, listOf(Color(0xFFFFFFFF), Color(0xFFFFE3E3)), listOf("christmas", "noël", "noel", "grinch", "home alone", "maman j'ai raté", "polar express", "klaus", "elf", "santa", "père noël", "love actually"), emptyList()),
        Ambience("Ice", SagaFxKind.Snow, listOf(Color(0xFFEAF6FF), Color(0xFFC9E6FF)), listOf("ice age", "l'âge de glace", "everest", "the thing", "snowpiercer", "fargo", "the revenant", "winter", "hiver", "the day after tomorrow"), emptyList()),
        Ambience("War", SagaFxKind.Snow, listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD), Color(0xFF757575)), listOf("saving private ryan", "il faut sauver", "dunkirk", "1917", "band of brothers", "the pacific", "hacksaw", "fury", "full metal", "apocalypse now", "platoon", "black hawk", "world war", "guerre mondiale"), listOf("war", "guerre", "war & politics")),
        Ambience("LOTR", SagaFxKind.Embers, listOf(Color(0xFFFFC94D), Color(0xFFFF9A3D), Color(0xFFFFE7A3)), listOf("lord of the rings", "seigneur des anneaux", "hobbit", "rings of power", "anneaux de pouvoir", "silmarillion"), emptyList()),
        Ambience("Dragons", SagaFxKind.Embers, listOf(Color(0xFFFF3D00), Color(0xFFFF7A1A), Color(0xFFFFB347)), listOf("game of thrones", "house of the dragon", "dragon", "how to train", "eragon", "reign of fire"), emptyList()),
        Ambience("Fire", SagaFxKind.Embers, listOf(Color(0xFFFF6A00), Color(0xFFFFB347), Color(0xFFFFD54F)), listOf("hunger games", "mad max", "hellboy", "ghost rider", "pompéi", "pompeii", "volcan", "inferno", "backdraft", "fire", "feu"), emptyList()),
        Ambience("Gladiator", SagaFxKind.Dust, listOf(Color(0xFFFFD180), Color(0xFFE6B35C)), listOf("gladiator", "troy", "troie", "300", "spartacus", "ben-hur", "rome", "napoleon", "alexander", "kingdom of heaven"), emptyList()),
        Ambience("Vikings", SagaFxKind.Snow, listOf(Color(0xFFCFD8DC), Color(0xFFB0BEC5)), listOf("vikings", "the last kingdom", "northman", "valhalla", "beowulf"), emptyList()),
        Ambience("Batman", SagaFxKind.Bats, null, listOf("batman", "dark knight", "gotham", "joker", "the penguin"), emptyList()),
        Ambience("Vampires", SagaFxKind.Bats, listOf(Color(0xFF4A0010)), listOf("dracula", "vampire", "twilight", "true blood", "nosferatu", "underworld", "blade", "interview with the vampire", "what we do in the shadows"), emptyList()),
        Ambience("Halloween", SagaFxKind.Bats, listOf(Color(0xFF2B1400)), listOf("halloween", "beetlejuice", "hocus pocus", "addams", "mercredi", "wednesday", "castlevania", "coraline", "nightmare before"), emptyList()),
        Ambience("Marvel", SagaFxKind.Lightning, listOf(Color(0xFFFF4D4D), Color(0xFFFFD54F)), listOf("marvel", "avengers", "iron man", "captain america", "black panther", "ant-man", "hulk", "loki", "wandavision", "eternals", "shang-chi", "captain marvel", "black widow", "daredevil", "fantastic four", "4 fantastiques", "venom", "moon knight", "echo"), listOf("superhero", "super-héros", "super héros")),
        Ambience("Thor", SagaFxKind.Lightning, listOf(Color(0xFF9FD3FF), Color(0xFFFFFFFF)), listOf("thor", "storm", "tempête", "shazam", "the flash", "flash"), emptyList()),
        Ambience("Spider", SagaFxKind.Sparks, listOf(Color(0xFFFF3B3B), Color(0xFF3B6BFF), Color(0xFFFFFFFF)), listOf("spider-man", "spiderman", "spider-verse", "miles morales", "spider"), emptyList()),
        Ambience("DC", SagaFxKind.Lightning, listOf(Color(0xFF5AA9FF), Color(0xFFFF3B3B)), listOf("superman", "man of steel", "justice league", "wonder woman", "aquaman", "the boys", "invincible", "peacemaker", "suicide squad", "black adam", "green lantern", "titans", "doom patrol"), emptyList()),
        Ambience("Mutants", SagaFxKind.Sparks, listOf(Color(0xFFB388FF), Color(0xFFFFD54F)), listOf("x-men", "deadpool", "wolverine", "logan", "the gifted", "legion", "the umbrella academy"), emptyList()),
        Ambience("Mecha", SagaFxKind.Sparks, listOf(Color(0xFF4DE9FF), Color(0xFFFFD54F)), listOf("transformers", "pacific rim", "gundam", "evangelion", "terminator", "robocop", "i, robot"), emptyList()),
        Ambience("Pirates", SagaFxKind.Bubbles, listOf(Color(0xFF7FE3D6), Color(0xFFB3FFF1)), listOf("pirates", "caribbean", "caraïbes", "one piece", "black sails", "our flag"), emptyList()),
        Ambience("Ocean", SagaFxKind.Bubbles, listOf(Color(0xFFBDEBFF), Color(0xFF8AD4FF)), listOf("nemo", "dory", "little mermaid", "petite sirène", "luca", "ponyo", "ocean", "océan", "bob l'éponge", "spongebob", "the way of water", "la voie de l'eau", "sea", "mer", "aquarium"), emptyList()),
        Ambience("Abyss", SagaFxKind.Bubbles, listOf(Color(0xFF6FA8DC), Color(0xFF3D85C6)), listOf("titanic", "the abyss", "jaws", "les dents de la mer", "the meg", "underwater", "sous la mer", "20 000 lieues", "deep"), emptyList()),
        Ambience("Jurassic", SagaFxKind.Leaves, listOf(Color(0xFF7BC86C), Color(0xFF4E9F3D), Color(0xFFC9A23F)), listOf("jurassic", "dinosaur", "dinosaure", "land before time", "walking with"), emptyList()),
        Ambience("Jungle", SagaFxKind.Leaves, listOf(Color(0xFF5DBB63), Color(0xFF2E7D32), Color(0xFFA5D6A7)), listOf("jungle", "tarzan", "king kong", "kong", "godzilla", "lion king", "roi lion", "mowgli", "zootopia", "zootopie", "planet of the apes", "planète des singes", "robin hood", "into the wild"), listOf("nature", "animals", "animaux")),
        Ambience("Ghibli", SagaFxKind.Fireflies, listOf(Color(0xFFFFF3A0), Color(0xFFC8FFB0)), listOf("totoro", "spirited away", "chihiro", "ghibli", "mononoke", "howl", "château ambulant", "kiki", "porco", "nausicaä", "arrietty"), emptyList()),
        Ambience("Anime", SagaFxKind.Petals, listOf(Color(0xFFFFB7C9), Color(0xFFFFD1DC)), listOf("naruto", "demon slayer", "kimetsu", "attack on titan", "l'attaque des titans", "dragon ball", "jujutsu", "my hero academia", "death note", "bleach", "hunter x hunter", "your name", "fullmetal", "cowboy bebop", "sailor moon", "pokémon", "pokemon", "chainsaw man", "spy x family", "solo leveling"), listOf("anime", "animation japonaise")),
        Ambience("Pixar", SagaFxKind.Confetti, null, listOf("toy story", "pixar", "minions", "shrek", "despicable", "moi, moche", "inside out", "vice-versa", "les indestructibles", "incredibles", "monsters", "monstres", "ratatouille", "wall-e", "up", "là-haut", "kung fu panda", "madagascar", "the lego", "lego", "cars", "turning red", "elemental"), listOf("animation", "family", "familial", "kids", "enfants")),
        Ambience("Kids", SagaFxKind.Bubbles, listOf(Color(0xFFFFB3D9), Color(0xFFB3E5FF), Color(0xFFFFF5B3), Color(0xFFC5FFB3)), listOf("peppa", "paw patrol", "pat' patrouille", "bluey", "cocomelon", "barbapapa", "trotro", "t'choupi", "dora", "oui-oui", "cartoon"), emptyList()),
        Ambience("Music", SagaFxKind.Notes, null, listOf("mamma mia", "la la land", "bohemian rhapsody", "rocketman", "elvis", "whiplash", "glee", "high school musical", "sing", "tous en scène", "pitch perfect", "the greatest showman", "grease", "a star is born", "trolls", "soul", "concert", "music", "musique", "tour"), listOf("music", "musique", "musical", "comédie musicale")),
        Ambience("Rock", SagaFxKind.Sparks, listOf(Color(0xFFFF5252), Color(0xFFFFD740)), listOf("rock", "metal", "school of rock", "spinal tap", "guitar", "tenacious", "green day", "metallica", "ac/dc"), emptyList()),
        Ambience("Noir", SagaFxKind.Rain, null, listOf("blade runner", "seven", "se7en", "the batman", "sin city", "the crow", "zodiac", "true detective", "mindhunter", "peaky blinders", "noir", "rain", "pluie", "the departed", "les infiltrés", "heat", "drive", "the killer"), listOf("thriller", "crime", "policier", "film noir", "polar")),
        Ambience("Spy", SagaFxKind.Sparks, listOf(Color(0xFFFFD54F), Color(0xFFFFFFFF)), listOf("james bond", "007", "mission: impossible", "mission impossible", "kingsman", "jason bourne", "the bourne", "tenet", "the night agent", "slow horses", "the bureau", "le bureau des légendes", "homeland"), emptyList()),
        Ambience("Action", SagaFxKind.Sparks, null, listOf("john wick", "fast & furious", "fast and furious", "rapides et dangereux", "top gun", "die hard", "piège de cristal", "rambo", "expendables", "equalizer", "taken", "the raid", "extraction", "kill bill", "speed", "reacher", "nobody", "bullet train", "the gray man"), listOf("action", "adventure", "aventure", "action & adventure")),
        Ambience("Racing", SagaFxKind.Sparks, listOf(Color(0xFFFF8A00), Color(0xFFFFD54F), Color(0xFFFFFFFF)), listOf("f1", "formula 1", "rush", "le mans", "gran turismo", "need for speed", "days of thunder", "drive to survive"), emptyList()),
        Ambience("Sport", SagaFxKind.Confetti, listOf(Color(0xFFFFD54F), Color(0xFF4CAF50), Color(0xFFFFFFFF)), listOf("rocky", "creed", "million dollar", "moneyball", "coach carter", "the blind side", "invictus", "space jam", "ted lasso", "friday night lights", "the last dance", "football", "basket", "boxe"), listOf("sport", "sports")),
        Ambience("Western", SagaFxKind.Dust, listOf(Color(0xFFD7B98E), Color(0xFFBC9A6C)), listOf("western", "django", "the good, the bad", "le bon, la brute", "unforgiven", "impitoyable", "yellowstone", "1883", "1923", "red dead", "true grit", "tombstone", "no country", "there will be blood", "deadwood", "westworld"), listOf("western")),
        Ambience("Egypt", SagaFxKind.Dust, listOf(Color(0xFFFFD54F), Color(0xFFFFE082)), listOf("the mummy", "la momie", "egypt", "égypte", "indiana jones", "tomb raider", "prince of persia", "lawrence of arabia", "uncharted", "sahara", "desert", "désert", "moon knight", "stargate"), emptyList()),
        Ambience("Ghost", SagaFxKind.Fog, listOf(Color(0xFFCFD8DC)), listOf("ghost", "fantôme", "conjuring", "insidious", "haunting", "hantise", "annabelle", "paranormal activity", "the ring", "le cercle", "the others", "poltergeist", "exorcist", "exorciste", "the nun", "la nonne", "sinister"), listOf("horror", "horreur", "épouvante", "paranormal")),
        Ambience("Zombies", SagaFxKind.Fog, listOf(Color(0xFFA5D6A7)), listOf("walking dead", "zombie", "the last of us", "resident evil", "28 days", "28 jours", "world war z", "train to busan", "dawn of the dead", "army of the dead", "kingdom"), emptyList()),
        Ambience("Stranger", SagaFxKind.Fog, listOf(Color(0xFFFF8A80)), listOf("stranger things", "upside down", "it", "ça", "dark", "the haunting of", "midnight mass", "stephen king", "the shining", "shining"), emptyList()),
        Ambience("Mystery", SagaFxKind.Fog, listOf(Color(0xFFB39DDB)), listOf("sherlock", "agatha christie", "knives out", "à couteaux tirés", "murder", "meurtre", "poirot", "lupin", "columbo", "only murders", "twin peaks", "lost"), listOf("mystery", "mystère", "suspense")),
        Ambience("Romance", SagaFxKind.Hearts, null, listOf("titanic", "the notebook", "n'oubliez jamais", "pride and prejudice", "orgueil et préjugés", "bridgerton", "la chronique des bridgerton", "me before you", "avant toi", "500 days", "dirty dancing", "pretty woman", "notting hill", "crazy rich", "emily in paris", "outlander", "love", "amour"), listOf("romance", "romantique", "romantic")),
        Ambience("Wedding", SagaFxKind.Petals, listOf(Color(0xFFFF8FA3), Color(0xFFFFC2D1)), listOf("wedding", "mariage", "mamma mia", "bride", "mariée", "sex and the city", "gossip girl"), emptyList()),
        Ambience("Comedy", SagaFxKind.Confetti, null, listOf("friends", "the office", "brooklyn nine", "how i met", "big bang theory", "modern family", "parks and rec", "seinfeld", "the simpsons", "les simpson", "futurama", "family guy", "south park", "kaamelott", "astérix", "asterix", "les visiteurs", "bienvenue chez", "intouchables", "camping"), listOf("comedy", "comédie", "sitcom", "stand-up", "reality", "téléréalité", "talk", "game show")),
        Ambience("History", SagaFxKind.Dust, listOf(Color(0xFFE8D5B0), Color(0xFFCFB98F)), listOf("the crown", "chernobyl", "oppenheimer", "napoleon", "the last samurai", "braveheart", "schindler", "band of", "the tudors", "versailles", "rome", "marie antoinette", "downton abbey"), listOf("history", "histoire", "historique", "biography", "biographie")),
        Ambience("Documentary", SagaFxKind.Dust, listOf(Color(0xFFFFFFFF), Color(0xFFE8D5B0)), listOf("planet earth", "planète terre", "our planet", "notre planète", "cosmos", "blue planet", "free solo", "march of the penguins", "la marche de l'empereur", "documentary", "documentaire"), listOf("documentary", "documentaire")),
        Ambience("Nature", SagaFxKind.Leaves, listOf(Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFC107)), listOf("national geographic", "nat geo", "wild", "sauvage", "nature", "forêt", "forest"), emptyList()),
        Ambience("Drama", SagaFxKind.Petals, listOf(Color(0xFFFFE0B2), Color(0xFFFFCCBC)), emptyList(), listOf("drama", "drame", "soap")),
        Ambience("Calm", SagaFxKind.Fireflies, null, emptyList(), emptyList()),
    )
    private val calm = all.last()
    private val musicAmbience = all.first { it.name == "Music" }
    private val bookAmbience = all.first { it.name == "History" }

    fun detect(item: Item): Ambience? = detect(item.name, item.seriesName, item.genres, item.type)

    fun detect(name: String, seriesName: String? = null, genres: List<String> = emptyList(), type: String? = null): Ambience? {
        val title = " " + listOfNotNull(name, seriesName).joinToString(" ").lowercase() + " "
        all.firstOrNull { a -> a.titles.any { matches(title, it) } }?.let { return it }
        val genreText = " " + genres.joinToString(" | ") { it.lowercase() } + " "
        all.firstOrNull { a -> a.genres.any { matches(genreText, it) } }?.let { return it }
        // Everything else still gets a gentle ambience; music and books stay calm.
        return when (type) {
            "Movie", "Series", "Season", "Episode", "BoxSet" -> calm
            "MusicAlbum", "Audio", "MusicArtist", "Playlist" -> musicAmbience
            "Book", "AudioBook" -> bookAmbience
            else -> null
        }
    }

    /** Whole-word match for short keys ("oz", "300", "sea") so they do not fire inside other words. */
    private fun matches(text: String, key: String): Boolean {
        if (key.length > 5) return text.contains(key)
        var from = 0
        while (true) {
            val i = text.indexOf(key, from)
            if (i < 0) return false
            val before = text.getOrNull(i - 1) ?: ' '
            val after = text.getOrNull(i + key.length) ?: ' '
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = i + 1
        }
    }
}

private data class Flyby(val baseY: Float, val delay: Float, val span: Float, val scale: Float, val down: Boolean)

/** One ambient particle: normalised start position, speed, size, phase. */
private data class Mote(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float, val hue: Int)

@Composable
fun SagaMotion(item: Item, modifier: Modifier = Modifier) {
    val ambience = remember(item.id, item.name, item.seriesName, item.genres, item.type) { SagaFx.detect(item) }
    SagaMotionCanvas(ambience, modifier)
}

/** Name-based entry point for saga landing pages, which have no [Item] at hand. */
@Composable
fun SagaMotion(name: String, modifier: Modifier = Modifier) {
    val ambience = remember(name) { SagaFx.detect(name, type = "Movie") }
    SagaMotionCanvas(ambience, modifier)
}

@Composable
private fun SagaMotionCanvas(ambience: Ambience?, modifier: Modifier = Modifier) {
    if (ambience == null) return
    val kind = ambience.style
    val palette = ambience.palette
    // No animation frames at all when the system asks for reduced motion.
    if (!org.jellyfin.mobile.nflix.ui.screens.rememberMotionEnabled()) return
    val transition = rememberInfiniteTransition(label = "saga-motion")
    val progress by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(if (kind == SagaFxKind.XWing) 18000 else 22000, easing = LinearEasing), RepeatMode.Restart),
        label = "saga-progress",
    )
    val ships = remember(ambience) {
        val random = Random(ambience.name.hashCode() + 31)
        List(3) { index ->
            Flyby(
                baseY = 0.18f + index * 0.26f + random.nextFloat() * 0.06f,
                delay = index * 0.29f,
                span = 0.42f,
                scale = 0.75f + random.nextFloat() * 0.55f,
                down = index % 2 == 1,
            )
        }
    }
    val stars = remember(ambience) { List(70) { Random(ambience.name.hashCode() * 97 + it).nextFloat() to Random(ambience.name.hashCode() * 31 + it * 7).nextFloat() } }
    val motes = remember(ambience) {
        val r = Random(ambience.name.hashCode() * 7919 + 3)
        List(if (kind == SagaFxKind.Rain || kind == SagaFxKind.Code) 70 else 34) {
            Mote(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.9f, 0.6f + r.nextFloat() * 0.8f, r.nextFloat(), r.nextInt(6))
        }
    }
    Canvas(modifier) {
        when (kind) {
            SagaFxKind.XWing -> {
                drawXWingScene(progress, ships)
                drawLaserBolts(progress)
            }
            SagaFxKind.Warp -> drawWarpScene(progress, stars)
            SagaFxKind.Bats -> drawBats(progress, motes, palette)
            SagaFxKind.Lightning -> drawLightning(progress, motes, palette)
            SagaFxKind.Fog -> drawFog(progress, motes, palette)
            else -> drawMotes(kind, progress, motes, palette)
        }
    }
}

/** Generic particle field: direction, shape and palette depend on the ambience. */
private fun DrawScope.drawMotes(kind: SagaFxKind, progress: Float, motes: List<Mote>, palette: List<Color>? = null) {
    fun pick(m: Mote, fallback: Color): Color = palette?.let { it[m.hue % it.size].copy(alpha = fallback.alpha) } ?: fallback
    val w = size.width
    val h = size.height
    val unit = w / 400f
    motes.forEachIndexed { i, m ->
        val t = (progress * m.speed * 3f + m.phase) % 1f
        val sway = sin((t * 2f + m.phase) * 2f * PI.toFloat())
        when (kind) {
            SagaFxKind.Magic -> {
                val x = w * m.x + sway * 18f * unit
                val y = h * (1f - t)
                val a = sin(t * PI.toFloat()) * 0.9f
                val c = pick(m, listOf(Color(0xFFFFE08A), Color(0xFFB9A6FF), Color(0xFF8AE8FF))[m.hue % 3])
                drawSparkle(Offset(x, y), 3.2f * m.size * unit * (0.6f + 0.4f * sin(t * 12f + i)), c.copy(alpha = a))
            }
            SagaFxKind.Embers, SagaFxKind.Sparks -> {
                val x = w * m.x + sway * 26f * unit
                val y = h * (1.05f - t * 1.1f)
                val a = (1f - t) * 0.85f
                val c = if (kind == SagaFxKind.Embers) listOf(Color(0xFFFF7A1A), Color(0xFFFFB347), Color(0xFFFF3D00))[m.hue % 3] else listOf(Color(0xFFFFD54F), Color(0xFFFF8A00), Color.White)[m.hue % 3].let { pick(m, it) }
                drawCircle(c.copy(alpha = a * 0.35f), 5.5f * m.size * unit, Offset(x, y))
                drawCircle(c.copy(alpha = a), 1.8f * m.size * unit, Offset(x, y))
            }
            SagaFxKind.Snow -> {
                val x = w * m.x + sway * 22f * unit
                val y = h * t
                drawCircle(pick(m, Color.White).copy(alpha = 0.75f * m.size.coerceAtMost(1f)), 2.4f * m.size * unit, Offset(x, y))
            }
            SagaFxKind.Rain -> {
                val x = w * ((m.x + t * 0.08f) % 1f)
                val y = h * ((t * 1.6f + m.y) % 1f)
                drawLine(pick(m, Color(0xFFB0C4DE)).copy(alpha = 0.32f), Offset(x, y), Offset(x - 5f * unit, y + 22f * unit * m.size), strokeWidth = 1.2f * unit)
            }
            SagaFxKind.Code -> {
                val col = (m.x * 26).toInt() / 26f
                val y = h * ((t * 1.4f + m.y) % 1f)
                for (k in 0 until 6) drawRect(
                    pick(m, Color(0xFF3DFF7A)).copy(alpha = (0.55f - k * 0.09f).coerceAtLeast(0f)),
                    Offset(w * col, y - k * 11f * unit), Size(4.5f * unit, 7f * unit),
                )
            }
            SagaFxKind.Bubbles -> {
                val x = w * m.x + sway * 14f * unit
                val y = h * (1.05f - t * 1.1f)
                drawCircle(pick(m, Color(0xFFBDEBFF)).copy(alpha = 0.45f * (1f - t * 0.6f)), 5f * m.size * unit, Offset(x, y), style = Stroke(1.3f * unit))
                drawCircle(Color.White.copy(alpha = 0.4f), 1.2f * unit, Offset(x - 1.6f * unit * m.size, y - 1.6f * unit * m.size))
            }
            SagaFxKind.Hearts -> {
                val x = w * m.x + sway * 16f * unit
                val y = h * (1.05f - t * 1.1f)
                drawHeart(Offset(x, y), 7f * m.size * unit, pick(m, Color(0xFFFF5C8A)).copy(alpha = 0.55f * sin(t * PI.toFloat())))
            }
            SagaFxKind.Petals, SagaFxKind.Leaves -> {
                val x = w * ((m.x + t * 0.25f) % 1f) + sway * 20f * unit
                val y = h * t
                val c = if (kind == SagaFxKind.Petals) listOf(Color(0xFFFFB7C9), Color(0xFFFFD1DC))[m.hue % 2] else listOf(Color(0xFF7BC86C), Color(0xFFC9A23F), Color(0xFF4E9F3D))[m.hue % 3]
                    .let { pick(m, it) }
                rotate(t * 540f + m.phase * 360f, Offset(x, y)) {
                    drawOval(c.copy(alpha = 0.7f), Offset(x - 5f * unit * m.size, y - 2.5f * unit * m.size), Size(10f * unit * m.size, 5f * unit * m.size))
                }
            }
            SagaFxKind.Confetti -> {
                val x = w * m.x + sway * 24f * unit
                val y = h * t
                val c = listOf(Color(0xFFFF4D6D), Color(0xFFFFD60A), Color(0xFF4CC9F0), Color(0xFF80ED99), Color(0xFFB388FF), Color(0xFFFF9F1C))[m.hue].let { pick(m, it) }
                rotate(t * 720f + i * 40f, Offset(x, y)) {
                    drawRect(c.copy(alpha = 0.75f), Offset(x - 3f * unit, y - 1.5f * unit), Size(6f * unit * m.size, 3f * unit))
                }
            }
            SagaFxKind.Notes -> {
                val x = w * m.x + sway * 20f * unit
                val y = h * (1.05f - t * 1.1f)
                drawNote(Offset(x, y), 6f * m.size * unit, pick(m, Color(0xFFE5E5FF)).copy(alpha = 0.6f * sin(t * PI.toFloat())))
            }
            SagaFxKind.Dust -> {
                val x = w * ((m.x + t * 0.3f) % 1f)
                val y = h * m.y + sway * 10f * unit
                drawCircle(pick(m, Color(0xFFE8D5B0)).copy(alpha = 0.35f * sin(t * PI.toFloat())), 1.6f * m.size * unit, Offset(x, y))
            }
            SagaFxKind.Fireflies -> {
                val x = w * m.x + sway * 30f * unit
                val y = h * (0.35f + 0.6f * m.y) + cos((t + m.phase) * 2f * PI.toFloat()) * 16f * unit
                val glow = (0.5f + 0.5f * sin((t * 6f + m.phase * 10f))).coerceIn(0f, 1f) * 0.8f
                drawCircle(pick(m, Color(0xFFFFF3A0)).copy(alpha = glow * 0.25f), 7f * m.size * unit, Offset(x, y))
                drawCircle(pick(m, Color(0xFFFFF7C2)).copy(alpha = glow), 1.7f * m.size * unit, Offset(x, y))
            }
            else -> Unit
        }
    }
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r / 3f, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = r / 3f, cap = StrokeCap.Round)
    drawCircle(color.copy(alpha = color.alpha * 0.4f), r * 0.9f, c)
}

private fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y - s * 0.1f, c.x - s * 0.6f, c.y - s * 1.1f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.6f, c.y - s * 1.1f, c.x + s * 1.4f, c.y - s * 0.1f, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawNote(c: Offset, s: Float, color: Color) {
    drawOval(color, Offset(c.x - s * 0.7f, c.y - s * 0.4f), Size(s * 1.3f, s * 0.9f))
    drawLine(color, Offset(c.x + s * 0.55f, c.y), Offset(c.x + s * 0.55f, c.y - s * 2.4f), strokeWidth = s * 0.2f)
    drawLine(color, Offset(c.x + s * 0.55f, c.y - s * 2.4f), Offset(c.x + s * 1.3f, c.y - s * 1.8f), strokeWidth = s * 0.25f, cap = StrokeCap.Round)
}

/** Bat silhouettes flapping across the frame. */
private fun DrawScope.drawBats(progress: Float, motes: List<Mote>, palette: List<Color>? = null) {
    val w = size.width
    val h = size.height
    val unit = w / 400f
    motes.take(9).forEachIndexed { i, m ->
        val t = (progress * (0.8f + m.speed) + m.phase) % 1f
        val x = w * (-0.1f + t * 1.2f)
        val y = h * (0.12f + m.y * 0.5f) + sin(t * 14f + i) * 10f * unit
        val flap = sin(progress * 220f + i * 3f)
        val s = 9f * m.size * unit
        val path = Path().apply {
            moveTo(x, y)
            quadraticBezierTo(x - s, y - s * (0.6f + flap * 0.6f), x - s * 2f, y - s * 0.1f * flap)
            quadraticBezierTo(x - s * 1.2f, y + s * 0.2f, x, y + s * 0.35f)
            quadraticBezierTo(x + s * 1.2f, y + s * 0.2f, x + s * 2f, y - s * 0.1f * flap)
            quadraticBezierTo(x + s, y - s * (0.6f + flap * 0.6f), x, y)
            close()
        }
        drawPath(path, (palette?.first() ?: Color(0xFF0B0B12)).copy(alpha = 0.8f))
    }
}

/** Comic-book energy: brief lightning forks plus drifting sparks. */
private fun DrawScope.drawLightning(progress: Float, motes: List<Mote>, palette: List<Color>? = null) {
    val w = size.width
    val h = size.height
    val unit = w / 400f
    val cycle = (progress * 6f) % 1f
    if (cycle < 0.12f) {
        val seed = (progress * 6f).toInt()
        val r = Random(seed)
        var x = w * (0.15f + r.nextFloat() * 0.7f)
        var y = 0f
        val alpha = (1f - cycle / 0.12f) * 0.85f
        drawRect(Color(0xFF9FD3FF).copy(alpha = alpha * 0.12f))
        while (y < h * 0.6f) {
            val nx = x + (r.nextFloat() - 0.5f) * 40f * unit
            val ny = y + (18f + r.nextFloat() * 22f) * unit
            drawLine(Color(0xFFDDEEFF).copy(alpha = alpha), Offset(x, y), Offset(nx, ny), strokeWidth = 2.4f * unit, cap = StrokeCap.Round)
            drawLine((palette?.first() ?: Color(0xFF6FB6FF)).copy(alpha = alpha * 0.4f), Offset(x, y), Offset(nx, ny), strokeWidth = 7f * unit, cap = StrokeCap.Round)
            x = nx; y = ny
        }
    }
    drawMotes(SagaFxKind.Sparks, progress, motes.take(16), palette)
}

/** Slow drifting fog banks and a faint flicker. */
private fun DrawScope.drawFog(progress: Float, motes: List<Mote>, palette: List<Color>? = null) {
    val w = size.width
    val h = size.height
    motes.take(8).forEachIndexed { i, m ->
        val x = w * (((m.x + progress * (0.2f + m.speed * 0.15f)) % 1.4f) - 0.2f)
        val y = h * (0.45f + m.y * 0.5f)
        drawOval((palette?.first() ?: Color(0xFFCCD3DD)).copy(alpha = 0.08f), Offset(x - w * 0.3f, y - h * 0.06f), Size(w * 0.6f, h * 0.12f))
    }
    val flicker = sin(progress * 90f) * sin(progress * 37f)
    if (flicker > 0.92f) drawRect(Color.Black.copy(alpha = 0.18f))
}

private fun DrawScope.drawXWingScene(progress: Float, ships: List<Flyby>) {
    // Faint star field drifting left, sells the depth behind the ships.
    val starAlpha = 0.22f
    repeat(48) { index ->
        val seed = index * 17
        val x = ((seed * 0.0137f) - progress * 0.25f).mod(1f)
        val y = ((seed * 0.0713f) + sin((progress * 2.0 * PI + index).toFloat()) * 0.004f).mod(1f)
        drawCircle(Color.White.copy(alpha = starAlpha), radius = if (index % 7 == 0) 2.2f else 1.2f, center = Offset(x * size.width, y * size.height))
    }
    ships.forEach { ship ->
        val local = ((progress - ship.delay) % 1f + 1f) % 1f
        val travel = local / ship.span
        if (travel > 1f) return@forEach
        val startX = -0.28f * size.width
        val endX = 1.28f * size.width
        val x = startX + (endX - startX) * travel
        val y = (ship.baseY + (if (ship.down) travel * 0.16f else -travel * 0.12f)) * size.height
        val angle = if (ship.down) 8f else -7f
        val alpha = (1f - ((travel - 0.82f) / 0.18f).coerceIn(0f, 1f) * 0.55f).coerceIn(0f, 1f)
        drawEngineTrail(x, y, angle, ship.scale, alpha)
        withTransform({
            translate(x, y)
            rotate(angle)
            scale(ship.scale, ship.scale, pivot = Offset.Zero)
        }) {
            drawXWing(alpha)
        }
    }
}

private fun DrawScope.drawEngineTrail(x: Float, y: Float, angle: Float, scale: Float, alpha: Float) {
    val length = 90f * scale
    val rad = (angle * PI / 180.0).toFloat()
    val tail = Offset(x - cos(rad) * length, y - sin(rad) * length)
    val paint = androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(Color.Transparent, Color(0xFFFF7A45).copy(alpha = 0.32f * alpha), Color(0xFFFFB26B).copy(alpha = 0.55f * alpha)),
        startX = tail.x,
        endX = x,
    )
    drawLine(
        brush = paint,
        start = tail,
        end = Offset(x - cos(rad) * 12f, y - sin(rad) * 12f),
        strokeWidth = 3.2f * scale,
    )
}

private fun DrawScope.drawXWing(alpha: Float) {
    val hull = Color(0xFFD8DEE9).copy(alpha = 0.82f * alpha)
    val shade = Color(0xFF8B93A1).copy(alpha = 0.75f * alpha)
    val engine = Color(0xFFFF6B4A).copy(alpha = 0.95f * alpha)

    // Fuselage with nose cone, nose pointing right (+X).
    val body = Path().apply {
        moveTo(-16f, -3.4f)
        lineTo(6f, -4.6f)
        lineTo(19f, -1.4f)
        lineTo(21f, 0f)
        lineTo(19f, 1.4f)
        lineTo(6f, 4.6f)
        lineTo(-16f, 3.4f)
        close()
    }
    drawPath(body, hull)

    // Wings swept back, both sides.
    val wings = Path().apply {
        moveTo(-4f, -3.2f)
        lineTo(-19f, -16f)
        lineTo(-23f, -16.6f)
        lineTo(-13f, -2.6f)
        close()
        moveTo(-4f, 3.2f)
        lineTo(-19f, 16f)
        lineTo(-23f, 16.6f)
        lineTo(-13f, 2.6f)
        close()
    }
    drawPath(wings, shade)

    // Wing-tip laser cannons plus the four engine glows.
    drawLine(hull, Offset(-18f, -16f), Offset(-6f, -15.2f), strokeWidth = 1.6f)
    drawLine(hull, Offset(-18f, 16f), Offset(-6f, 15.2f), strokeWidth = 1.6f)
    listOf(Offset(-14f, -5.4f), Offset(-14f, 5.4f), Offset(-6f, -2.6f), Offset(-6f, 2.6f)).forEach { point ->
        drawCircle(engine, radius = 3.1f, center = point)
        drawCircle(Color(0xFFFFE3B0).copy(alpha = 0.55f * alpha), radius = 1.4f, center = point)
    }
}

private fun DrawScope.drawLaserBolts(progress: Float) {
    repeat(4) { index ->
        val phase = ((progress * 3.0f + index * 0.37f) % 1f)
        val visible = phase < 0.12f
        if (!visible) return@repeat
        val t = phase / 0.12f
        val y = (0.22f + index * 0.19f) * size.height
        val x = (0.1f + t * 0.9f) * size.width
        val color = if (index % 2 == 0) Color(0xFFFF4C4C) else Color(0xFF63E6A6)
        drawLine(
            color = color.copy(alpha = 0.75f * (1f - t)),
            start = Offset(x - 46f, y),
            end = Offset(x, y),
            strokeWidth = 3f,
        )
    }
}

private fun DrawScope.drawWarpScene(progress: Float, stars: List<Pair<Float, Float>>) {
    val center = Offset(size.width * 0.5f, size.height * 0.42f)
    stars.forEachIndexed { index, (sx, sy) ->
        val angle = sx * 2f * PI.toFloat()
        val radius = (sy + progress * (0.4f + sy * 0.6f)).mod(1f)
        val distance = radius * size.width * 0.9f
        val from = Offset(center.x + cos(angle) * distance * 0.72f, center.y + sin(angle) * distance * 0.72f)
        val to = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
        val color = if (index % 6 == 0) Color(0xFF7FD1FF) else Color.White
        drawLine(
            color = color.copy(alpha = 0.35f + 0.4f * (1f - radius)),
            start = from,
            end = to,
            strokeWidth = 1.2f + (1f - radius) * 2.4f,
        )
    }
}
