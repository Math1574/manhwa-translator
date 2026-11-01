package app.manhwa

import android.content.Context
import app.manhwa.core.EnglishLexicon

/** Chargement paresseux du dictionnaire anglais embarqué (ENABLE, domaine public ; 10 000 mots les plus fréquents, rangs de fréquence). */
object Lexicon {
    @Volatile private var cached: EnglishLexicon? = null

    fun get(ctx: Context): EnglishLexicon = cached ?: synchronized(this) {
        cached ?: run {
            fun lines(name: String) = ctx.assets.open("lexicon/$name").bufferedReader().use { it.readLines() }
            EnglishLexicon(lines("enable1.txt"), lines("g10k.txt")).also { cached = it }
        }
    }
}
