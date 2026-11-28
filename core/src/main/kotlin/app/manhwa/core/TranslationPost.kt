package app.manhwa.core

/** Retouches sûres d'une traduction, faites après coup sur chaque ligne (le modèle se trompe de façon régulière sur ces points). */
object TranslationPost {
    /** Bégaiement de l'original : « B-BUT », « W-wait », « Th-the », « WH- WHAT » (une ou deux lettres, répétées). */
    private val sourceStutter = Regex("(?i)(?<![\\p{L}'’-])(\\p{L}{1,2})-\\s?\\1\\p{L}*")
    private val sourceStartStutter = Regex("(?i)^[^\\p{L}]*(\\p{L}{1,2})-\\s?\\1")
    /** Bégaiement déjà présent dans la traduction (« M-MAIS », « DÉ-DÉMON »). */
    private val frenchStutter = Regex("(?i)(?<![\\p{L}'’-])(\\p{L}{1,2})-\\1\\p{L}*")
    /** Une lettre suivie d'un trait d'union et d'un mot d'au moins deux lettres : « E-ATTENDEZ ». « a-t-il », « peut-être » n'y répondent pas. */
    private val singleLetterStutter = Regex("(?<![\\p{L}'’-])(\\p{L})-(\\p{L}[\\p{L}'’]+)(?![\\p{L}-])")
    private val firstWord = Regex("^([^\\p{L}]*)(\\p{L}[\\p{L}'’]*)")
    /** « S-Rank », « X-Ray » : une lettre, un trait d'union et un mot qui n'a rien d'un bégaiement. */
    private val codeWords = Regex("(?i)^(rank|class|grade|level|type|ray|tier|series|shirt)$")

    /** Une réplique qui n'est qu'un bégaiement (« Y-YOU… », « B-BUT! ») : ponctuation, une lettre, trait d'union, mot. */
    private val loneStutter = Regex("^([^\\p{L}]*)(\\p{L})-\\s?(\\p{L}+)([^\\p{L}]*)$")
    /** Mots que l'on bégaie le plus et leur équivalent français (la lettre répétée est la première du mot français). */
    private val stutterWords = mapOf(
        "you" to "toi", "but" to "mais", "what" to "quoi", "wait" to "attends", "why" to "pourquoi", "who" to "qui", "how" to "comment",
        "no" to "non", "stop" to "arrête", "me" to "moi", "he" to "lui", "she" to "elle", "we" to "nous", "they" to "eux",
        "help" to "à l'aide", "sorry" to "désolé", "okay" to "d'accord", "please" to "s'il te plaît", "yes" to "oui", "hey" to "hé", "hold" to "attends", "run" to "cours",
    )

    /**
     * Filet de sécurité : le modèle recopie parfois « Y-YOU… » tel quel (un seul « mot » pour lui, qu'il juge intraduisible). Quand la traduction est identique
     * à l'original et que celui-ci n'est qu'un bégaiement d'un mot courant, la réplique est écrite directement : « T-TOI… ». Tout autre cas est laissé tel quel.
     */
    fun rescueStutter(src: String, tr: String): String {
        val m = loneStutter.matchEntire(src.trim()) ?: return tr
        val key = m.groupValues[3]
        val fr = stutterWords[key.lowercase()] ?: return tr
        if (!m.groupValues[2].equals(key.take(1), ignoreCase = true)) return tr
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        if (norm(src) != norm(tr)) return tr
        val upper = key.length >= 2 && key.all { it.isUpperCase() }
        val word = if (upper) fr.uppercase() else if (key[0].isUpperCase()) fr.replaceFirstChar { it.uppercase() } else fr
        val letter = word.first().toString()
        val tail = m.groupValues[4]
        return m.groupValues[1] + letter + "-" + word + (if (tail.isNotEmpty() && tail[0] in "!?:;") " $tail" else tail)       // espace avant « ! », « ? » à la française
    }

    /**
     * Bégaiement : l'original « B-BUT » doit donner « M-MAIS », pas « P-MAIS » (le modèle recopie ou invente la lettre) ni « MAIS » (le bégaiement est perdu).
     * La lettre répétée est toujours la première du mot français qui suit. Sans bégaiement dans l'original, rien n'est changé.
     */
    fun stutter(src: String, tr: String): String = dottedStutter(src, hyphenStutter(src, tr))

    /** Bégaiement à points de l'original (« S.. SORRY », « M... MOM ») : dans la traduction, la lettre bégayée est celle du mot français (« D... DÉSOLÉ », pas « S... DÉSOLÉ »). */
    private val sourceDotStutter = Regex("(?i)(?<![\\p{L}'’])(\\p{L}{1,2})(?:\\.{2,3}|…)\\s?\\1\\p{L}*")
    private val dotStutter = Regex("(?<![\\p{L}'’])(\\p{L}{1,2})(\\.{2,3}|…)(\\s?)(\\p{L}[\\p{L}'’]*)")
    private fun bare(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    private fun dottedStutter(src: String, tr: String): String {
        val n = sourceDotStutter.findAll(src).count()
        if (n == 0) return tr
        var seen = 0
        return dotStutter.replace(tr) { m ->
            if (seen >= n) return@replace m.value
            val letters = m.groupValues[1]; val word = m.groupValues[4]
            if (word.length <= letters.length) return@replace m.value
            seen++
            if (bare(word).startsWith(bare(letters))) m.value
            else {
                val head = word.take(letters.length)
                (if (letters.all { it.isUpperCase() }) head.uppercase() else head) + m.groupValues[2] + m.groupValues[3] + word
            }
        }
    }

    private fun hyphenStutter(src: String, tr: String): String {
        val n = sourceStutter.findAll(src).count()
        if (n == 0) return tr
        var seen = 0
        // 1) bégaiement français dont la lettre n'est pas celle du mot (au plus autant que dans l'original)
        var out = singleLetterStutter.replace(tr) { m ->
            val letter = m.groupValues[1]; val word = m.groupValues[2]
            if (seen >= n || codeWords.matches(word)) return@replace m.value
            seen++
            val first = word[0]
            if (letter[0].equals(first, ignoreCase = true)) m.value
            else (if (letter[0].isUpperCase()) first.uppercaseChar() else first.lowercaseChar()).toString() + "-" + word
        }
        // 2) bégaiement perdu en tête de ligne : ajouté devant le premier mot
        if (seen == 0 && sourceStartStutter.containsMatchIn(src) && !frenchStutter.containsMatchIn(out)) {
            val m = firstWord.find(out)
            if (m != null && m.groupValues[2].length >= 2) {
                val w = m.groupValues[2]
                out = m.groupValues[1] + w[0] + "-" + w + out.substring(m.range.last + 1)
            }
        }
        return out
    }

    // ---------- grammaire sûre : élision, subjonctif, « faire » + infinitif ----------

    /** Mot qui commence par une voyelle et devant lequel on élide (« l'ancien », « d'un »), ou le pronom « y » (« j'y vais ») ; jamais « onze », « oui », « ouate », un nom en Y (« de Yoo ») ni un mot en h (h muet ou aspiré : on ne sait pas). */
    private const val ELIDABLE = "(?=[aeiouéèêëàâîïôûùæœAEIOUÉÈÊËÀÂÎÏÔÛÙÆŒ]\\p{L}|[yY](?![\\p{L}]))(?!(?i:onz|oui(?![\\p{L}])|ouat|ouf(?![\\p{L}])|ululer))"
    /** « le ancien », « de un », « que il », « je ai », « ne est », « ce est » : l'article, la préposition ou le pronom s'élide devant une voyelle. « fais-le encore » (impératif) n'est pas touché. */
    private val missingElision = Regex("(?<![\\p{L}'’-])(le|la|de|je|me|te|se|ne|que|jusque|lorsque|puisque|quoique)\\s+$ELIDABLE(?=\\p{L})", RegexOption.IGNORE_CASE)
    private val nextWord = Regex("^[\\p{L}'’]+")
    /** Pronoms et articles : devant un mot recopié de l'original (titre anglais « PICK ME UP », nom, charabia), on ne touche à rien. */
    private val pronounLike = setOf("le", "la", "je", "me", "te", "se", "ne")
    private val ceEst = Regex("(?<![\\p{L}'’-])(ce)\\s+(?=(?:est|était|étaient|eût)(?![\\p{L}]))", RegexOption.IGNORE_CASE)
    private val siIl = Regex("(?<![\\p{L}'’-])(si)\\s+(?=ils?(?![\\p{L}'’]))", RegexOption.IGNORE_CASE)
    /** « du ancien » → « de l'ancien », « au enfant » → « à l'enfant » ; jamais après « avoir » (« j'aurais du aller » : c'est « dû »). */
    private val duVowel = Regex("(?<![\\p{L}'’-])(du|au)\\s+$ELIDABLE(?=\\p{L})", RegexOption.IGNORE_CASE)
    private val avoirBefore = Regex("(?i)(?:ai|as|a|avons|avez|ont|avais|avait|avions|aviez|avaient|aurai|auras|aura|aurais|aurait|aurions|auriez|auraient|eu|aie|ait)\\s*$")

    private fun caseLike(model: String, out: String): String = when {
        model.length >= 2 && model.all { !it.isLetter() || it.isUpperCase() } -> out.uppercase()
        model.isNotEmpty() && model[0].isUpperCase() -> out.replaceFirstChar { it.uppercase() }
        else -> out
    }

    /**
     * Élisions oubliées par la génération (« LE ÉPILOGUE », « DU ANCIEN VAGABOND », « SI IL ») ; le reste de la ligne est intact. [src] : la ligne d'origine — un mot qui en vient
     * (nom, titre anglais, charabia recopié) n'est élidé qu'après « de », « que » (« d'Eugene ») ; « du » devant un nom devient « d' » (« la fille d'Uihyeok »), « la une » reste.
     */
    fun elision(src: String, tr: String): String {
        val srcWords = Regex("[\\p{L}'’]+").findAll(src.lowercase()).map { it.value }.toSet()
        val srcLow = " " + src.lowercase().replace(Regex("[^\\p{L}'’]+"), " ") + " "
        fun next(text: String, from: Int) = nextWord.find(text.substring(from))?.value?.lowercase() ?: ""
        var out = missingElision.replace(tr) { m ->
            val w = m.groupValues[1]; val wl = w.lowercase()
            val n = next(tr, m.range.last + 1)
            if (srcLow.contains(" $wl $n ")) return@replace m.value              // recopié de l'original (« PICK ME UP »)
            if (n in srcWords && wl in pronounLike) return@replace m.value          // pronom devant un mot anglais ou un nom : on ne sait pas
            if (wl == "la" && n == "une") return@replace m.value                    // « à la une »
            val el = when (wl) { "que" -> "qu'"; "jusque" -> "jusqu'"; "lorsque" -> "lorsqu'"; "puisque" -> "puisqu'"; "quoique" -> "quoiqu'"; else -> wl.take(1) + "'" }
            caseLike(w, el)
        }
        out = ceEst.replace(out) { m -> caseLike(m.groupValues[1], "c'") }
        out = siIl.replace(out) { m -> caseLike(m.groupValues[1], "s'") }
        val cur = out
        out = duVowel.replace(cur) { m ->
            val w = m.groupValues[1]; val n = next(cur, m.range.last + 1)
            if (avoirBefore.containsMatchIn(cur.substring(0, m.range.first)) || srcLow.contains(" ${w.lowercase()} $n ")) m.value
            else if (n in srcWords) caseLike(w, if (w.equals("du", ignoreCase = true)) "d'" else "à ")   // devant un nom : « d'Uihyeok », « à Ilay »
            else caseLike(w, if (w.equals("du", ignoreCase = true)) "de l'" else "à l'")
        }
        return out
    }

    /** Présent de l'indicatif → subjonctif, pour les verbes que le modèle laisse à l'indicatif après « bien que », « pour que »… */
    private val subjunctive: Map<String, String> = mapOf(
        "suis" to "sois", "es" to "sois", "est" to "soit", "sommes" to "soyons", "êtes" to "soyez", "sont" to "soient",
        "ai" to "aie", "as" to "aies", "a" to "ait", "avons" to "ayons", "avez" to "ayez", "ont" to "aient",
        "peux" to "puisse", "peut" to "puisse", "pouvons" to "puissions", "pouvez" to "puissiez", "peuvent" to "puissent",
        "fais" to "fasse", "fait" to "fasse", "faisons" to "fassions", "faites" to "fassiez", "font" to "fassent",
        "vais" to "aille", "vas" to "ailles", "va" to "aille", "allons" to "allions", "allez" to "alliez", "vont" to "aillent",
        "sais" to "sache", "sait" to "sache", "savons" to "sachions", "savez" to "sachiez", "savent" to "sachent",
        "veux" to "veuille", "veut" to "veuille", "veulent" to "veuillent",
        "viens" to "vienne", "vient" to "vienne", "venons" to "venions", "venez" to "veniez",
        "dois" to "doive", "doit" to "doive", "devons" to "devions", "devez" to "deviez",
        "prends" to "prenne", "prend" to "prenne", "prenons" to "prenions", "prenez" to "preniez",
    )
    /** Conjonction qui exige le subjonctif, sujet, pronoms compléments éventuels, verbe au présent de l'indicatif. */
    private val subjunctiveSlot = Regex(
        "(?iu)(?<![\\p{L}])((?:bien|pour|avant|sans|afin|pourvu|jusqu['’]à ce|à moins|il faut|faut|il faudrait|faudrait|il fallait|veux|voulais|voudrais|veut|voulait|voudrait)\\s+qu(?:e\\s+|['’]))" +
            "(je\\s+|j['’]|tu\\s+|il\\s+|elle\\s+|on\\s+|ils\\s+|elles\\s+|nous\\s+|vous\\s+|ça\\s+|cela\\s+|ce\\s+)" +
            "((?:(?:ne|n['’])\\s*)?(?:(?:le|la|les|lui|leur|me|te|se|nous|vous|y|en)\\s+|[lmtsn]['’]){0,2})" +
            "(\\p{L}+)(?![\\p{L}'’-])"
    )

    /** « bien qu'il est » → « bien qu'il soit », « pour que tu peux » → « pour que tu puisses » : le subjonctif est obligatoire après ces conjonctions. */
    fun subjunctive(tr: String): String = subjunctiveSlot.replace(tr) { m ->
        val verb = m.groupValues[4]
        var sub = subjunctive[verb.lowercase()] ?: return@replace m.value
        val subject = m.groupValues[2].trim().lowercase().removeSuffix("'").removeSuffix("’")
        // personne : « que tu puisses », « que j'aie », « qu'ils sachent » (le pluriel des tableaux ci-dessus est déjà juste)
        if (subject == "tu" && !sub.endsWith("s") && !sub.endsWith("z")) sub += "s"
        if (subject == "j" && verb.lowercase() == "ai") sub = "aie"
        if ((subject == "ils" || subject == "elles") && verb.lowercase() in setOf("viennent", "doivent", "prennent")) return@replace m.value
        m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + caseLike(verb, sub)
    }

    /** « m'a fait stationné » → « m'a fait stationner » : après « faire » causatif (précédé d'un pronom complément), le verbe est à l'infinitif. */
    private val causativeParticiple = Regex("(?iu)((?:[mtls]['’]|nous\\s+|vous\\s+|les\\s+|me\\s+|te\\s+|se\\s+)(?:(?:a|as|ai|avez|avons|ont|avait|avais|avaient|aura|aurait|est|suis|es|sont|était|étais)\\s+)?fait\\s+)(\\p{L}{2,}é)(?![\\p{L}'’-])")
    fun causative(tr: String): String = causativeParticiple.replace(tr) { m ->
        val p = m.groupValues[2]
        m.groupValues[1] + p.dropLast(1) + (if (p.last().isUpperCase()) "ER" else "er")
    }

    /** « T'PRÊTER », « N'SAIS » : une élision devant une consonne (français parlé) s'écrit en entier. « L' », « D' », « QU' » ne sont pas concernés. */
    private val badElision = Regex("(?<![\\p{L}'’])([jmtsnJMTSN])['’](?=[bcdfgjklmnpqrstvwxzçBCDFGJKLMNPQRSTVWXZÇ])")
    fun consonantElision(tr: String): String = badElision.replace(tr) { m ->
        val c = m.groupValues[1]
        (if (c[0].isUpperCase()) c + "E" else c + "e") + " "
    }

    /** « POSSIBLE ? !!! » → « POSSIBLE ?!!! » : pas d'espace à l'intérieur d'un groupe de « ? » et de « ! ». */
    private val splitMarks = Regex("([?!])\\s+(?=[?!])")
    fun punctuation(tr: String): String = splitMarks.replace(tr, "$1")

    /** Les retouches de grammaire sûres, dans l'ordre. */
    fun grammar(src: String, tr: String): String = punctuation(consonantElision(causative(subjunctive(elision(src, tr)))))

    // ---------- défauts de génération : chiffres et mots doublés ----------

    /** Un nombre, séparateurs de milliers compris (« 3,149 », « 3 149 », « 1.200 ») : ses chiffres seuls servent à comparer. */
    private val numberRe = Regex("(?<![\\d])\\d+(?:[ ,.\\u00A0\\u202F]\\d{3}(?!\\d))*")

    /**
     * Nombres abîmés à la génération (journal du 27/11 : « 26 YEARS » → « 266 ANS », « $50 » → « 500 DOLLARS », « 06:50 » → « 06:500 », « 24/7 » → « 2/7 ») : un nombre de la traduction
     * absent de l'original, qui ne diffère que d'un chiffre (en trop ou en moins) d'un nombre de l'original absent de la traduction, est remis tel qu'à l'original.
     * Un nombre écrit en lettres (« dix », « cent ») n'est pas concerné ; un nombre que l'original n'explique pas d'une seule façon est laissé.
     */
    fun numbers(src: String, tr: String): String {
        val srcNums = numberRe.findAll(src).map { it.value.filter(Char::isDigit) }.toList()
        if (srcNums.isEmpty()) return tr
        if (clockRe.containsMatchIn(src)) return tr   // « 1 PM » → « 13 h » : l'heure change légitimement de chiffres
        val trMatches = numberRe.findAll(tr).toList()
        if (trMatches.isEmpty()) return tr
        val trNums = trMatches.map { it.value.filter(Char::isDigit) }
        val missing = srcNums.filter { it !in trNums }.distinct()
        if (missing.isEmpty()) return tr
        val sb = StringBuilder(tr)
        val used = HashSet<String>()
        for (m in trMatches.reversed()) {
            val t = m.value.filter(Char::isDigit)
            if (t in srcNums) continue
            val cands = missing.filter { it !in used && oneDigitApart(t, it) }
            if (cands.size != 1) continue
            val s = cands[0]; used.add(s)
            val grouped = m.value.any { !it.isDigit() } && s.length > 3
            sb.replace(m.range.first, m.range.last + 1, if (grouped) groupFrench(s) else s)
        }
        return sb.toString()
    }

    private val clockRe = Regex("(?i)\\d\\s*(?:a\\.?m\\b|p\\.?m\\b)|o['’]clock")

    /** Suite d'au moins cinq chiffres identiques (« 1111111… ») absente de l'original : bégaiement du modèle sur un numéro de ligne, collé à la traduction ; retirée. */
    private val digitRunRe = Regex("\\s*(\\d)\\1{4,}")
    fun digitRuns(src: String, tr: String): String {
        if (!digitRunRe.containsMatchIn(tr)) return tr
        val out = digitRunRe.replace(tr) { m -> if (src.contains(m.value.trim())) m.value else "" }.trim()
        return out.ifEmpty { tr }
    }

    private fun oneDigitApart(a: String, b: String): Boolean {
        if (a.length == b.length + 1) return a.indices.any { a.removeRange(it, it + 1) == b }
        if (b.length == a.length + 1) return b.indices.any { b.removeRange(it, it + 1) == a }
        return false
    }

    private fun groupFrench(d: String): String = d.reversed().chunked(3).joinToString(" ").reversed()

    /** Mots-outils qui ne se répètent jamais d'affilée en français écrit (« à à », « que que », « de de ») ; « vous vous », « nous nous » sont réfléchis, donc permis (trois de suite ne l'est pas). */
    private val doubledWord = Regex("(?iu)(?<![\\p{L}'’])(à|a|de|du|des|que|qui|le|la|les|un|une|et|en|ne|pour|par|sur|dans|avec|je|tu|il|elle|on|ils|elles|ce|se|me|te|mon|ma|mes|ton|ta|tes|son|sa|ses|au|aux|y)\\s+\\1(?![\\p{L}'’])")
    private val tripledReflexive = Regex("(?iu)(?<![\\p{L}'’])((vous|nous)\\s+\\2)\\s+\\2(?![\\p{L}'’])")
    private val srcRepeat = Regex("(?iu)(?<![\\p{L}'’])(\\p{L}+)[\\s,.…-]+\\1(?![\\p{L}'’])")

    /** Mot-outil doublé par la génération (« QUELLE QUE QUE RAISON », « À À QUEL POINT ») : le doublon est retiré, sauf si l'original répète lui-même un mot (bégaiement, insistance). */
    fun doubledWords(src: String, tr: String): String {
        if (srcRepeat.containsMatchIn(src)) return tr
        var out = doubledWord.replace(tr) { m -> m.value.substring(0, m.groupValues[1].length) }
        out = tripledReflexive.replace(out) { m -> m.groupValues[1] }
        return out
    }
}

