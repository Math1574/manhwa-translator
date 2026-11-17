package app.manhwa

import android.content.Context
import app.manhwa.core.EnglishLexicon

/** Chargement paresseux du dictionnaire anglais embarqué (ENABLE, domaine public ; 10 000 mots les plus fréquents, rangs de fréquence ; vocabulaire du genre : arts martiaux, fantasy, cour, cris et rires). */
object Lexicon {
    @Volatile private var cached: EnglishLexicon? = null

    /** Le dictionnaire s'il est déjà chargé (la lecture d'une page le charge toujours avant son affichage), sans le charger. */
    fun peek(): EnglishLexicon? = cached

    fun get(ctx: Context): EnglishLexicon = cached ?: synchronized(this) {
        cached ?: run {
            fun lines(name: String) = ctx.assets.open("lexicon/$name").bufferedReader().use { it.readLines() }
            EnglishLexicon(lines("enable1.txt"), lines("g10k.txt"), lines("genre.txt")).also { cached = it }
        }
    }
}
