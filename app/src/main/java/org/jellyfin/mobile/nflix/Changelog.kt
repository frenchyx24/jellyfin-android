package org.jellyfin.mobile.nflix

/** In-app changelog ("Nouveautés de la version"), French, newest first. Pure lookup, unit tested. */
object Changelog {
    data class Entry(val version: String, val title: String, val lines: List<String>)

    val entries: List<Entry> = listOf(
        Entry("5.9.0", "Nflix 5.9.0", listOf(
            "Famille : photos et GIF dans les messages, accusés de lecture, notifications groupées et nuits silencieuses.",
            "Défis, soirées film, lien d'invitation et QR code, groupes d'amis, listes partagées ou publiques, mur des amis.",
            "Recommandez un titre à un ami, avatars générés, vos stats et Nflix Wrapped.",
            "Widget « Reprendre », sauvegarde des réglages, dialogues renforcés, filtres avancés (décennie, note) et « Surprends-moi ».",
            "Pages studio, tri « Plus regardés », couleurs du thème saisonnier partout, démarrage mesuré et plus fluide.",
        )),
        Entry("5.8.2", "Nflix 5.8.2", listOf(
            "Correction du plantage au lancement (épinglage de certificat).",
        )),
        Entry("5.8.0", "Nflix 5.8.0", listOf(
            "Famille : sondages « on regarde quoi ce soir », réactions emoji, réponses et indicateur « écrit… » dans les messages.",
            "Partagez un lien vers n'importe quel titre depuis son menu.",
            "Signalez un bug depuis l'app avec le journal des erreurs.",
            "Nouveautés de la version affichées dans les réglages.",
        )),
        Entry("5.7.0", "Nflix 5.7.0", listOf(
            "« Êtes-vous toujours là ? » après 3 épisodes enchaînés.",
            "Piste audio et sous-titres mémorisées par série ; double appui réglable (10/30 s) ; reprise exacte après un crash.",
            "Filtre « J'ai X minutes » dans la recherche ; badges 4K, HDR, Dolby Vision, VF, VOSTFR sur les affiches.",
            "Thème noir AMOLED, taille du texte et économiseur de données.",
        )),
        Entry("5.6.0", "Nflix 5.6.0", listOf(
            "Thèmes saisonniers entièrement animés : toutes les couches, transitions, intensité réglable, sons discrets, aperçu du calendrier, compte à rebours, thème d'anniversaire, confettis de succès.",
            "Jetons chiffrés au repos, épinglage de certificat sécurisé avec secours, reprise intelligente du réseau et mode hors connexion.",
            "Démarrage plus rapide, R8 full mode, fuites mémoire surveillées en debug.",
        )),
    )

    fun forVersion(version: String): Entry? = entries.firstOrNull { it.version == version }
}

/** Shareable web link of a library item. */
object ShareLink {
    fun forItem(id: String, name: String): String =
        "https://nflix.miracloud.fr/web/index.html#!/details?id=$id — $name sur Nflix"
}
