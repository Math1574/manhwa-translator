package app.manhwa.core

/**
 * Onomatopées (bruitages) : on les laisse telles quelles, sans les traduire ni toucher à l'image. Un bruitage est un dessin autant qu'un texte : l'effacer puis le redessiner en
 * police de remplacement l'abîme, et sa « traduction » n'apporte rien. Règle volontairement prudente, pour ne jamais écarter une réplique :
 *  - seuls les textes hors bulle comptent (un « HMPH » ou un « HAHAHA » dans une bulle est une réplique : traduit) ;
 *  - un bloc de trois mots au plus, dont chaque mot est un bruitage connu (« CRACK ! », « BOOOM », « TICK TOCK », « *GULP* ») ;
 *  - ou un gros lettrage de deux mots au plus, dont aucun n'est un mot anglais (bruitages romanisés : « DUDUDUK », « KRAAAK »).
 * Anglais seulement : le japonais, le coréen et le chinois ne sont pas concernés.
 */
object Onomatopoeia {
    private fun squash(w: String): String {
        val sb = StringBuilder()
        for (c in w.lowercase()) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    // bruitages anglais courants ; comparés après réduction des lettres répétées (« BOOOM » = « BOOM »)
    private val sfx: Set<String> = (
        "boom bang bam wham pow bop zap whack thwack smack slap thump thud thunk clunk clonk bonk wallop kapow blam kaboom kablam crash crack krash krak krack snap pop smash slam bash biff thwomp whomp whump " +
        "splash splat plop squelch gurgle glug slosh sploosh sizzle fizz crackle " +
        "swish swoosh whoosh whish whirr whir whiz zoom vroom swoop flap flutter fwoosh whoomp thwip zip zing shing slash slice chop " +
        "click clack clang clank clink jingle jangle ding dong bong gong chime beep boop honk buzz bzzt bzz tick tock knock rattle rumble creak squeak squeal screech scrape scratch rustle crunch munch chomp slurp " +
        "clap stomp thrum throb twang ting tink dink pling plink hum zzt zzz " +
        "hiss growl roar snarl purr meow woof chirp tweet squawk caw hoot howl " +
        "gulp gasp huff puff pant sniff snort sigh tsk"
        ).split(' ').filter { it.isNotBlank() }.map { squash(it) }.toSet()

    private val word = Regex("[A-Za-z]+")

    /**
     * [relativeHeight] : hauteur de ligne du bloc rapportée à la médiane de la page (un gros lettrage dépasse 1,3). [lex] : lexique anglais ; sans lui, seuls les bruitages
     * connus comptent.
     */
    fun isSfx(text: String, kind: BlockKind, relativeHeight: Float, lex: EnglishLexicon?): Boolean {
        if (kind == BlockKind.IN_BUBBLE) return false
        if (ScriptDetector.dominant(text) != "en") return false
        // une apostrophe au milieu d'un mot (« DON'T », « I'LL ») : c'est une réplique, pas un bruitage
        if (Regex("[A-Za-z]['’][A-Za-z]").containsMatchIn(text)) return false
        val toks = word.findAll(text).map { it.value }.toList()
        if (toks.isEmpty() || toks.size > 3) return false
        if (toks.all { squash(it) in sfx }) return true
        // bruitage romanisé en gros lettrage : deux mots au plus, aucun n'est un mot anglais (sans détecteur de bulles, on ne tente pas)
        if (kind == BlockKind.FREE && lex != null && toks.size <= 2 && relativeHeight >= 1.3f && toks.all { it.length >= 3 && !lex.known(it) }) return true
        return false
    }
}
