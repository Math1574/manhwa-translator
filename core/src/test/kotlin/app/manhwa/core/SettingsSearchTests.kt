package app.manhwa.core

private val sample = listOf(
    SearchEntry("reread", "Relecture des textes difficiles (Gemma)", "Reconnaissance du texte › Relecture", "vision lettrage manuscrit tester"),
    SearchEntry("size", "Taille du texte traduit", "Lecture › Affichage", "police agrandir réduire échelle"),
    SearchEntry("gpu", "Accélération GPU (expérimental)", "Avancé › Performance", "processeur cpu tester"),
    SearchEntry("ads", "Bloquer les publicités", "Navigation › Publicités", "pub pop-up redirections adblock"),
    SearchEntry("mascot", "Mascotte", "Apparence › Mascotte", "chat hibou dragon nuage esprit-feuille personnage"),
    SearchEntry("motion", "Animations réduites", "Apparence › Mouvement", "rebond flottement"),
    SearchEntry("oeuvre", "Cœur du glossaire", "Glossaire", "œuvre personnages noms"),
)

private fun ids(q: String, limit: Int = 20) = SettingsSearch.find(q, sample, limit).map { it.id }

fun settingsSearchTests() {
    T.test("recherche des réglages: un mot du titre trouve le réglage") {
        T.eq(listOf("gpu"), ids("gpu")); T.eq(listOf("gpu"), ids("GPU"))
    }
    T.test("recherche des réglages: sans accents, avec ou sans majuscules, début de mot suffisant") {
        T.eq(listOf("gpu"), ids("acceleration")); T.eq(listOf("gpu"), ids("ACCÉLÉRATION")); T.eq(listOf("gpu"), ids("accel"))
        T.eq(listOf("ads"), ids("publicites")); T.eq(listOf("ads"), ids("pub"))
    }
    T.test("recherche des réglages: les petits mots sont ignorés et tous les autres mots doivent correspondre") {
        T.eq(listOf("size"), ids("taille du texte")); T.eq(listOf("size"), ids("la taille de texte"))
        T.eq(emptyList<String>(), ids("gpu mascotte"))
    }
    T.test("recherche des réglages: les mots supplémentaires et le chemin mènent aussi au réglage") {
        T.eq(listOf("mascot"), ids("hibou")); T.eq(listOf("mascot"), ids("esprit-feuille")); T.eq(listOf("mascot"), ids("esprit feuille"))
        T.eq(listOf("gpu"), ids("processeur")); T.eq(listOf("ads"), ids("pop up"))
        T.eq(listOf("mascot", "motion"), ids("apparence"))      // chemin : même score, ordre de la liste
    }
    T.test("recherche des réglages: le titre passe avant les mots supplémentaires, le mot entier avant le début de mot") {
        // « texte » est un mot entier du titre de « size », seulement le début de « textes » dans « reread », même si « reread » est listé en premier
        T.eq(listOf("size", "reread"), ids("texte"))
        // « tester » : mots supplémentaires de deux réglages, ordre de la liste
        T.eq(listOf("reread", "gpu"), ids("tester"))
    }
    T.test("recherche des réglages: ligatures, apostrophes et chiffres") {
        T.eq(listOf("oeuvre"), ids("coeur")); T.eq(listOf("oeuvre"), ids("oeuvre")); T.eq(listOf("oeuvre"), ids("Œuvre"))
        T.eq("l oeuvre de l ete 2", SettingsSearch.normalize("L’œuvre de l'été 2"))
    }
    T.test("recherche des réglages: vide, introuvable, limite") {
        T.eq(emptyList<String>(), ids("")); T.eq(emptyList<String>(), ids("   ")); T.eq(emptyList<String>(), ids("zzzz"))
        T.eq(2, ids("e", limit = 2).size)
    }
    T.test("recherche des réglages: un petit mot seul ne vide pas la recherche") {
        T.ok(ids("des").isNotEmpty(), "« des » seul doit encore proposer les titres qui le contiennent")
    }
}
