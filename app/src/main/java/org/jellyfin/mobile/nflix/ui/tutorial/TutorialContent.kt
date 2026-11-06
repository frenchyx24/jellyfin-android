package org.jellyfin.mobile.nflix.ui.tutorial

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Visual theme of a tutorial page; the overlay maps it to an icon. */
enum class TutorialTopic { Welcome, Home, Videos, Detail, Player, QuickMenu, Music, Books, Horus, Search, Profile, Admin, Done }

data class TutorialPage(val topic: TutorialTopic, val title: String, val body: String, val tips: List<String>)

/** Pages of the first-run tutorial. Every feature described here exists in the native app. */
object TutorialPages {
    private val before = listOf(
        TutorialPage(
            TutorialTopic.Welcome,
            "Bienvenue sur Nflix",
            "Films, séries, musique, livres et demandes : tout est réuni dans une seule app. Voici le tour du propriétaire en une minute.",
            listOf(
                "La barre du bas mène à Accueil, Vidéos, Musique, Livres et Horus.",
                "Vous pouvez passer ce guide à tout moment et le revoir depuis votre Profil.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Home,
            "L'accueil",
            "Le carrousel met en avant les titres à la une : faites-le défiler du doigt.",
            listOf(
                "Reprendre et À suivre : continuez exactement là où vous vous étiez arrêté.",
                "Les nouveautés de chaque bibliothèque s'affichent en rangées.",
                "En haut à droite : la loupe pour Rechercher, la cloche pour les Annonces, votre avatar pour le Profil.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Videos,
            "Les vidéos",
            "Films, séries et animés sont rangés par bibliothèque, en rangées ou en grilles d'affiches.",
            listOf(
                "Triez par ajouts récents, titre, année, note, vu récemment ou au hasard.",
                "Filtrez : non vus, favoris ou en cours, et choisissez un genre.",
                "Ma liste rassemble les titres que vous avez mis de côté.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Detail,
            "La fiche d'un titre",
            "Touchez une affiche pour ouvrir sa fiche.",
            listOf(
                "Séries : choisissez la saison puis l'épisode.",
                "Découvrez la distribution et regardez la bande-annonce.",
                "Reprendre ou Du début, Ma liste, Marquer vu.",
                "Télécharger un film, un épisode ou une saison entière.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Player,
            "La lecture",
            "Le lecteur vidéo est intégré à l'app.",
            listOf(
                "Changez la piste audio et les sous-titres pendant la lecture.",
                "Un bouton apparaît pour passer l'intro ou le générique quand ils sont repérés.",
                "Hors ligne, retrouvez vos téléchargements dans Profil › Téléchargements.",
                "Pour diffuser sur un Chromecast, passez par Profil › Version web.",
            ),
        ),
        TutorialPage(
            TutorialTopic.QuickMenu,
            "L'appui long",
            "Restez appuyé sur une affiche, un album ou un titre pour ouvrir le menu rapide.",
            listOf(
                "Ajouter à Ma liste ou aimer un morceau.",
                "Marquer comme vu ou non vu, retirer de « Reprendre ».",
                "Lire ensuite, ajouter à la file d'attente ou à une playlist.",
                "Télécharger, voir la fiche, aller à la série, à l'album ou à l'artiste.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Music,
            "La musique",
            "Albums, artistes, playlists et titres likés, avec des mix et des suggestions.",
            listOf(
                "Le lecteur plein écran affiche les paroles synchronisées et la file d'attente.",
                "Le mini-lecteur reste en bas de l'écran pendant que vous naviguez.",
                "La musique continue en arrière-plan et sur l'écran verrouillé.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Books,
            "Les livres",
            "Lisez vos PDF, EPUB et bandes dessinées CBZ directement dans l'app.",
            listOf(
                "Continuer la lecture vous ramène à votre dernière page.",
                "Parcourez par auteur ou par saga.",
                "Les livres audio s'écoutent dans le lecteur musique, chapitre par chapitre.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Horus,
            "Horus",
            "Il manque un titre ? Horus vous permet de le demander.",
            listOf(
                "Découvrez les films, séries, albums et livres populaires ou récents.",
                "Demandez un film, une série complète, un album, un livre numérique ou un livre audio.",
                "Suivez l'avancement dans Mes demandes, puis regardez ou écoutez sur Nflix dès que c'est disponible.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Search,
            "La recherche",
            "Une seule recherche pour tout Nflix et Horus.",
            listOf(
                "Films, séries, animés, musique, livres, personnes et collections.",
                "Filtrez les résultats par type et retrouvez vos recherches récentes.",
                "Un titre n'est pas encore sur Nflix ? Demandez-le directement depuis les résultats.",
            ),
        ),
        TutorialPage(
            TutorialTopic.Profile,
            "Votre profil",
            "Touchez votre avatar en haut de l'accueil.",
            listOf(
                "Mes succès : badges, rang et progression.",
                "Téléchargements et Mes demandes Horus.",
                "Annonces de Nflix et de Mira.",
                "Thèmes saisonniers, réglages Lecture et lecteur.",
                "Mises à jour (via Mira Connect quand il est installé) et Version web.",
                "Didacticiel : revoyez ce guide quand vous voulez.",
            ),
        ),
    )

    private val admin = TutorialPage(
        TutorialTopic.Admin,
        "Administration",
        "Vous êtes administrateur : un espace réservé vous attend dans le Profil.",
        listOf(
            "Distributeurs et logos affichés sur l'accueil.",
            "Demandes Horus à approuver ou refuser.",
            "Activité en direct et analyse des bibliothèques.",
        ),
    )

    private val done = TutorialPage(
        TutorialTopic.Done,
        "C'est parti !",
        "Vous savez tout. Bon visionnage, bonne écoute et bonne lecture sur Nflix.",
        emptyList(),
    )

    fun pages(isAdmin: Boolean): List<TutorialPage> = before + listOfNotNull(admin.takeIf { isAdmin }) + done

    /**
     * The administrator check may answer while the tutorial is already open. The page is only added
     * when it lands after the page on screen, so the current page never changes under the user.
     */
    fun canInsertAdmin(currentPage: Int): Boolean = currentPage < before.size
}

/** Pager rules shared by the buttons and the system back gesture. */
object TutorialFlow {
    fun isLast(index: Int, count: Int): Boolean = index >= count - 1

    fun next(index: Int, count: Int): Int = (index + 1).coerceIn(0, (count - 1).coerceAtLeast(0))

    fun previous(index: Int): Int = (index - 1).coerceAtLeast(0)

    fun primaryLabel(index: Int, count: Int): String = if (isLast(index, count)) "C'est parti" else "Suivant"

    /** Back goes to the previous page; on the first page it closes the tutorial. */
    fun backCloses(index: Int): Boolean = index <= 0

    fun pageDescription(index: Int, count: Int): String = "Page ${index + 1} sur $count"
}

interface TutorialStorage {
    fun seenVersion(): Int
    fun saveSeenVersion(version: Int)
}

/** Dedicated preferences, never cleared on sign-out: the tutorial shows once per device. */
class SharedPrefsTutorialStorage(private val prefs: SharedPreferences) : TutorialStorage {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    override fun seenVersion(): Int = prefs.getInt(KEY_SEEN_VERSION, 0)

    override fun saveSeenVersion(version: Int) = prefs.edit { putInt(KEY_SEEN_VERSION, version) }

    companion object {
        const val PREFS = "nflix_tutorial"
        const val KEY_SEEN_VERSION = "seen_version"
    }
}

class TutorialProgress(private val storage: TutorialStorage) {
    /** True until the current tutorial version has been finished or skipped. */
    fun shouldShow(): Boolean = storage.seenVersion() < VERSION

    fun markSeen() {
        if (storage.seenVersion() < VERSION) storage.saveSeenVersion(VERSION)
    }

    companion object {
        /** Bump to show a reworked tutorial again to everyone. */
        const val VERSION = 1
    }
}
