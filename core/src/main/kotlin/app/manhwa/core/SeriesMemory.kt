package app.manhwa.core

/**
 * Confusions de lettres propres au lettrage d'une œuvre : certaines polices font lire à l'OCR un « D » comme un « P » (« PUKE » pour « DUKE », « ANP » pour « AND »),
 * un « U » comme un « L » (« BECALSE »), un « O » comme un « D » (« WHD »)… Une faute isolée ne prouve rien ; la même substitution qui, dans des dizaines de mots
 * différents de la même œuvre, change un mot inconnu en mot courant, si. Elle est alors appliquée aux mots inconnus de cette œuvre (et signalée au modèle de traduction
 * pour les mots qui en restent ambigus, comme « PAY » pour « DAY »).
 *
 * Clé d'une confusion : la lettre lue puis la lettre voulue (« PD » : lu P, voulu D).
 */
class LetterConfusions {
    private val evidence = HashMap<String, LinkedHashSet<String>>()
    /** Confusions effacées par l'utilisateur (« PD ») : jamais appliquées, même si de nouveaux mots les appuient. */
    val rejected = LinkedHashSet<String>()
    /** Mots inconnus déjà examinés (pour ne compter chaque mot qu'une fois). */
    private val examined = LinkedHashSet<String>()

    /** Examine le texte lu d'une bulle. [protect] : noms connus de l'œuvre (minuscules), jamais pris pour des fautes. */
    fun observe(text: String, lex: EnglishLexicon, protect: Set<String> = emptySet()) {
        for (m in wordRe.findAll(text)) {
            val w = m.value.uppercase()
            if (w.length < 3 || w in examined) continue
            val l = w.lowercase()
            if (lex.known(l) || l in protect || JunkFilter.isInterjectionToken(l)) continue
            examined.add(w)
            if (examined.size > MAX_EXAMINED) examined.remove(examined.first())
            for (a in w.toSet()) for (b in 'A'..'Z') {
                if (a == b) continue
                val c = w.replace(a, b).lowercase()
                if (lex.isCommon(c) || (c.length >= 5 && lex.known(c))) {
                    val set = evidence.getOrPut("$a$b") { LinkedHashSet() }
                    if (set.size < MAX_EVIDENCE) set.add(w)
                }
            }
        }
    }

    /**
     * Confusions établies : pour une lettre lue, la lettre voulue qui explique au moins [MIN_WORDS] mots différents, et au moins deux fois plus que toute autre lettre voulue
     * pour la même lettre lue (« P » lu pour « D » : 102 mots ; pour « R » : 28, simple effet de bord des terminaisons -ED).
     */
    fun active(): List<Pair<Char, Char>> {
        val out = ArrayList<Pair<Char, Char>>()
        for ((a, group) in evidence.entries.filter { it.key !in rejected }.groupBy { it.key[0] }) {
            val sorted = group.sortedByDescending { it.value.size }
            val best = sorted[0]; val second = sorted.getOrNull(1)?.value?.size ?: 0
            if (best.value.size >= MIN_WORDS && best.value.size >= 2 * second) out.add(a to best.key[1])
        }
        return out.sortedByDescending { evidence["${it.first}${it.second}"]?.size ?: 0 }.take(3)
    }

    /** Confusion très nette (au moins [STRONG_WORDS] mots) : un mot valide mais rare peut alors aussi être une faute (« HARP » pour « HARD »). */
    private fun strong(p: Pair<Char, Char>): Boolean = (evidence["${p.first}${p.second}"]?.size ?: 0) >= STRONG_WORDS

    /** Exemples de mots corrigés par une confusion (lu → voulu), pour le modèle de traduction. */
    fun examples(p: Pair<Char, Char>, lex: EnglishLexicon? = null, n: Int = 3): List<Pair<String, String>> =
        (evidence["${p.first}${p.second}"] ?: emptySet<String>()).asSequence()
            .map { it to it.replace(p.first, p.second) }
            .filter { (_, c) -> lex == null || lex.isCommon(c.lowercase()) }
            .sortedBy { (_, c) -> lex?.rank(c.lowercase()) ?: 0 }
            .take(n).toList()

    /**
     * Corrige un mot lu (casse d'origine gardée) : un mot inconnu devient le mot connu le plus courant qu'une confusion établie explique (toutes ou une partie des lettres en cause ;
     * « PREPAREP » → « PREPARED », pas « DREDARED ») ; un mot valide mais rare ne change qu'avec une confusion très nette et pour un mot courant (« HARP » → « HARD »).
     * Null : rien à corriger. [protect] : noms connus (minuscules).
     */
    fun fix(word: String, lex: EnglishLexicon, protect: Set<String> = emptySet(), pairs: List<Pair<Char, Char>> = active()): String? {
        if (word.length < 2 || word.any { !it.isLetter() && it != '\'' && it != '’' } || word.count { it.isLetter() } < 2) return null
        val up = word.uppercase().replace('’', '\''); val l = up.lowercase()
        if (l in protect || JunkFilter.isInterjectionToken(l)) return null
        if (pairs.isEmpty()) return null
        // mot de deux lettres (« DF » pour « OF », « TD » pour « TO ») : seulement vers un petit mot très courant, et jamais depuis l'un d'eux
        if (up.count { it.isLetter() } == 2) {
            if (l in shortCommon) return null
            for (p in pairs) {
                val c = l.replace(p.first.lowercaseChar(), p.second.lowercaseChar())
                if (c != l && c in shortCommon) return recaseLike(word, c)
            }
            return null
        }
        val known = lex.known(l)
        if (known && lex.isCommon(l)) return null
        var best: String? = null; var bestRank = Int.MAX_VALUE
        for (p in pairs) {
            if (known && !strong(p)) continue
            val idx = up.indices.filter { up[it] == p.first }
            if (idx.isEmpty() || idx.size > 5) continue
            for (mask in 1 until (1 shl idx.size)) {
                val ch = up.toCharArray()
                for ((k, i) in idx.withIndex()) if (mask and (1 shl k) != 0) ch[i] = p.second
                val c = String(ch).lowercase()
                // un mot rare n'est proposé que s'il est long (« PERSECUTEP » → « PERSECUTED ») : « CLUME » ne devient pas « GLUME »
                val ok = if (known) lex.isCommon(c) && lex.rank(c) < 8_000 else (lex.isCommon(c) || (c.length >= 7 && lex.known(c)))
                if (!ok) continue
                val r = lex.rank(c)
                if (r < bestRank) { best = c; bestRank = r }
            }
        }
        return recaseLike(word, best ?: return null)
    }

    /** Casse du mot lu : majuscules si la plupart de ses lettres en sont (« GOpDAMN » → « GODDAMN »), majuscule initiale, ou minuscules. */
    private fun recaseLike(word: String, b: String): String {
        val letters = word.filter { it.isLetter() }
        return when {
            letters.count { it.isUpperCase() } * 2 >= letters.length -> b.uppercase()
            word[0].isUpperCase() -> b.replaceFirstChar { it.uppercase() }
            else -> b
        }
    }

    fun isEmpty(): Boolean = evidence.isEmpty()

    /** Nombre de mots différents qui appuient la confusion [key] (« PD »). */
    fun support(key: String): Int = evidence[key]?.size ?: 0

    /** Efface une confusion et ne l'applique plus. */
    fun reject(key: String) { evidence.remove(key); rejected.add(key) }

    /** Oublie tout ce qui a été observé (les confusions effacées le restent). */
    fun clear() { evidence.clear(); examined.clear() }

    fun serialize(sb: StringBuilder) {
        for ((k, v) in evidence.toSortedMap()) if (v.size >= 2) sb.append("C\t").append(k).append('\t').append(v.joinToString(",")).append('\n')
    }

    fun load(key: String, words: String) {
        if (key.length != 2) return
        evidence.getOrPut(key) { LinkedHashSet() }.addAll(words.split(',').filter { it.isNotBlank() }.take(MAX_EVIDENCE))
        examined.addAll(words.split(',').filter { it.isNotBlank() })
    }

    companion object {
        private val wordRe = Regex("(?<![A-Za-z0-9'’])[A-Za-z]{2,}(?![A-Za-z0-9])")
        const val MIN_WORDS = 8
        const val STRONG_WORDS = 25
        private const val MAX_EVIDENCE = 60
        private const val MAX_EXAMINED = 4000
        /** Mots de deux lettres qu'une confusion peut produire (« DF » → « OF », « TD » → « TO »). */
        private val shortCommon = setOf("of", "to", "do", "go", "no", "so", "on", "or", "in", "it", "is", "up", "us", "we", "me", "my", "by", "an", "at", "as", "if", "he", "be", "am")
    }
}

/**
 * Ce que l'application sait d'une œuvre, d'un chapitre à l'autre (la mémoire du chapitre, elle, repart de zéro à chaque chapitre) : personnages (nom tel qu'il s'écrit,
 * genre, tutoiement ou vouvoiement quand on leur parle), genre du narrateur, vocabulaire qui dit le genre de l'œuvre (arts martiaux, fantasy, chasseurs…), confusions de
 * lettres de son lettrage. Alimentée par chaque page traduite ; rappelée au modèle de traduction (voir [promptNotes]) ; gardée sur le téléphone, une par œuvre.
 */
class SeriesMemory(val key: String) {
    /** Une réplique déjà traduite qui contient un terme de l'œuvre, et le chapitre où elle l'a été (« ? » : enregistrée avant le build 269, chapitre inconnu). */
    class Example(val src: String, val tr: String, val chapter: String)

    class Name(var display: String) {
        var seen = 0; var male = 0; var female = 0; var tu = 0; var vous = 0
        val gender: FrenchAgreement.Gender? get() = when {
            male >= 3 && male >= 3 * female -> FrenchAgreement.Gender.M
            female >= 3 && female >= 3 * male -> FrenchAgreement.Gender.F
            else -> null
        }
        val register: FrenchAgreement.Register? get() = when {
            tu >= 3 && tu >= 4 * vous -> FrenchAgreement.Register.TU
            vous >= 3 && vous >= 4 * tu -> FrenchAgreement.Register.VOUS
            else -> null
        }
    }

    val confusions = LetterConfusions()
    private val names = LinkedHashMap<String, Name>()
    private var narratorM = 0
    private var narratorF = 0
    /** Mots qui disent le genre de l'œuvre : nombre de bulles où chacun apparaît (voir [GenrePacks]). */
    private val genreWords = HashMap<String, Int>()
    /**
     * Termes propres à l'œuvre (« Northern Heavenly Sect », « boarding house », « great sasuk ») : suites de deux à quatre mots qui reviennent d'un chapitre à l'autre, avec le nombre
     * de bulles où chacune est apparue et la plus courte réplique déjà traduite qui la contient. Quand un terme revient, cette réplique est montrée au modèle, qui garde la même traduction.
     */
    private val termCounts = LinkedHashMap<String, Int>()
    /**
     * Répliques déjà traduites qui contiennent un terme : au plus une par chapitre (la plus courte), pour [MAX_PER_TERM] chapitres. Un exemple ne sert de référence au modèle
     * (prompt 6) que si deux chapitres différents ont traduit le terme de façon concordante (voir [agree]) : une traduction ratée une fois ne devient pas la règle de l'œuvre.
     */
    private val termExamples = HashMap<String, ArrayList<Example>>()
    // ce que l'utilisateur a effacé dans la bibliothèque : plus jamais appris pour cette œuvre
    private val rejectedNames = LinkedHashSet<String>()
    private val rejectedTerms = LinkedHashSet<String>()
    private var narratorRejected = false
    /** Faux pendant un préchauffage avec les traductions d'une ancienne version (banc d'essai) : les termes sont comptés, leurs exemples ne sont pas retenus. */
    @Volatile var learnExamples = true
    /** Changé depuis le dernier enregistrement. */
    @Volatile var dirty = false

    @Synchronized fun nameCount(): Int = names.size

    /** Noms de l'œuvre vus au moins [min] fois, les plus fréquents d'abord (minuscules). */
    @Synchronized fun knownNames(min: Int = 3, lex: EnglishLexicon? = null): Set<String> {
        val pairs = if (lex != null) confusions.active() else emptyList()
        // un « nom » que les confusions de l'œuvre expliquent (« PUKE » pour « DUKE ») n'en est pas un : il n'est plus protégé de la correction
        return names.filter { it.value.seen >= min && (pairs.isEmpty() || confusions.fix(it.key, lex!!, emptySet(), pairs) == null) }.keys.toSet()
    }

    @Synchronized fun name(lower: String): Name? = names[lower.lowercase()]

    /**
     * Noms de l'œuvre (vus au moins trois fois, d'un chapitre à l'autre) que les lignes [texts] contiennent, tels qu'ils s'écrivent : ils rejoignent les noms à garder tels quels
     * dès la première page d'un nouveau chapitre (la mémoire du chapitre, elle, repart de zéro).
     */
    @Synchronized fun namesOnPage(texts: List<String>, lex: EnglishLexicon?): List<String> {
        val page = texts.joinToString(" ").lowercase()
        return knownNames(3, lex).filter { Regex("(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])").containsMatchIn(page) }
            .sortedByDescending { names[it]?.seen ?: 0 }.mapNotNull { names[it]?.display?.let { d -> if (d.any { c -> c.isUpperCase() }) d else d.replaceFirstChar { c -> c.uppercase() } } }
    }

    // ---------- apprentissage ----------

    /** Le texte lu d'une bulle (avant réparation) : confusions de lettres du lettrage. */
    @Synchronized fun observeReading(text: String, lex: EnglishLexicon) {
        confusions.observe(text, lex, knownNames(2, lex))
        dirty = true
    }

    /**
     * Une page traduite : [chapterNames] sont les noms retenus par la mémoire du chapitre (gardés tels quels dans les traductions). Chaque nom de la page compte une fois de plus ;
     * son genre se lit dans l'anglais (« he », « his » sans « she » dans la même bulle, et l'inverse), son registre dans la traduction quand la bulle lui parle.
     */
    /**
     * [trusted] (prompt 6) : lignes réussies du premier coup (ni refaites, ni corrigées après contrôle). Ce que la mémoire apprend de la traduction elle-même (tutoiement ou
     * vouvoiement d'un personnage, genre du narrateur, répliques d'exemple des termes) ne vient que d'elles ; ce qu'elle apprend de l'anglais (noms, genre par les pronoms, termes,
     * genre de l'œuvre) vient de toutes. [chapter] : le chapitre de la page (voir [Example]). [outside] : lignes hors bulle (narration, pensées) ; quand on le sait, seules elles
     * apprennent le genre du narrateur (dans une bulle, « je » est celui qui parle, pas forcément le narrateur).
     */
    @Synchronized fun observePage(sources: List<String>, translations: List<String>, chapterNames: Collection<String>, lex: EnglishLexicon? = null,
                                  trusted: List<Boolean>? = null, chapter: String = "", outside: List<Boolean>? = null) {
        // ni une interjection (« HEHE », « HAHA ») ni un mot mal lu que les confusions de l'œuvre expliquent (« LGH » pour « UGH ») : la mémoire du chapitre les garde tels quels, ce ne sont pas des personnages
        val pairs = confusions.active()
        val chapterLow = chapterNames.map { it.lowercase() }.filter { n ->
            n.length >= 3 && !JunkFilter.isInterjectionToken(n) && (lex == null || pairs.isEmpty() || confusions.fix(n, lex, emptySet(), pairs) == null)
        }
        for ((i, src) in sources.withIndex()) {
            val tr = translations.getOrNull(i) ?: continue
            val ok = trusted?.getOrNull(i) ?: true
            val low = src.lowercase()
            // à qui la bulle parle : ses « he », « she » visent alors quelqu'un d'autre (« GAF, DID YOU SEE HIM? »)
            val who = if (FrenchAgreement.pluralCue(src) == null) FrenchAgreement.addressee(src, chapterLow) else null
            val titled = (titleMale.findAll(low) + titleFemale.findAll(low)).map { it.groupValues[1] }.toSet()
            // un mot anglais (« NORMAL », « MODE », « SCORE ») que la mémoire du chapitre garde tel quel n'est un nom que si on l'interpelle ou qu'un titre le précède (« PARK, … », « MRS. PARK »)
            val present = chapterLow.filter { n ->
                n !in rejectedNames && Regex("(?<![\\p{L}])" + Regex.escape(n) + "(?![\\p{L}])").containsMatchIn(low) &&
                    (lex == null || !lex.known(n) || n == who || n in titled || names.containsKey(n))
            }
            for (n in present) {
                val e = names.getOrPut(n) { Name(display(n, chapterNames)) }
                e.seen++
                // un pronom ne renseigne que s'il n'y a qu'un seul nom dans la bulle et qu'on ne lui parle pas
                if (present.size == 1 && n != who) {
                    val he = maleRe.containsMatchIn(low); val she = femaleRe.containsMatchIn(low)
                    if (he && !she) e.male++ else if (she && !he) e.female++
                }
                // un titre de civilité vaut un pronom : un nom de famille seul peut désigner plusieurs personnes (« MRS. PARK », la mère de Daniel Park)
                for (m in titleMale.findAll(low)) if (m.groupValues[1] == n) e.male++
                for (m in titleFemale.findAll(low)) if (m.groupValues[1] == n) e.female++
            }
            // registre : la bulle s'adresse à un nom connu et la traduction tutoie ou vouvoie (une foule ou un titre de respect n'apprennent rien sur la personne)
            if (ok && who != null && who !in rejectedNames && (lex == null || !lex.known(who) || names.containsKey(who) || who in present)) {
                val reg = FrenchAgreement.forms(tr).register
                if (reg != null) names.getOrPut(who) { Name(display(who, chapterNames)) }.let { if (reg == FrenchAgreement.Register.TU) it.tu++ else it.vous++ }
            }
            if (ok && !narratorRejected && (outside == null || outside.getOrNull(i) == true))
                for ((g, _) in FrenchAgreement.narratorGenders(tr)) if (g == FrenchAgreement.Gender.M) narratorM++ else narratorF++
            for (w in GenrePacks.cueWords(src)) genreWords[w] = (genreWords[w] ?: 0) + 1
            if (lex != null) for (t in termsOf(src, lex, chapterLow)) {
                if (t in rejectedTerms) continue
                termCounts[t] = (termCounts[t] ?: 0) + 1
                val s = src.trim(); val f = tr.trim()
                if (learnExamples && ok && s.length in 6..MAX_EXAMPLE && f.isNotEmpty() && f != s) addExample(t, Example(s, f, chapter))
            }
        }
        if (termCounts.size > MAX_TERMS) {
            // les termes vus une seule fois partent d'abord (les plus anciens), puis les moins fréquents
            val drop = termCounts.entries.sortedBy { it.value }.take(termCounts.size - MAX_TERMS * 3 / 4).map { it.key }
            drop.forEach { termCounts.remove(it); termExamples.remove(it) }
        }
        if (termExamples.size > MAX_EXAMPLES) termExamples.keys.sortedBy { termCounts[it] ?: 0 }.take(termExamples.size - MAX_EXAMPLES).forEach { termExamples.remove(it) }
        if (termExamples.values.sumOf { it.size } > MAX_EXAMPLE_LINES) termExamples.keys.sortedBy { termCounts[it] ?: 0 }.take(termExamples.size / 4).forEach { termExamples.remove(it) }
        if (names.size > MAX_NAMES) names.entries.sortedBy { it.value.seen }.take(names.size - MAX_NAMES).forEach { names.remove(it.key) }
        dirty = true
    }

    /**
     * Répliques déjà traduites de l'œuvre qui contiennent un terme de la page (vu au moins trois fois) : au plus [max], les termes les plus longs puis les plus fréquents d'abord ;
     * jamais une ligne de la page elle-même.
     */
    @Synchronized fun termExamples(texts: List<String>, lex: EnglishLexicon?, max: Int = 3, glossary: List<GlossaryEntry> = emptyList(), agreed: Boolean = false): List<Pair<String, String>> {
        if (lex == null || termExamples.isEmpty()) return emptyList()
        val page = texts.map { it.trim() }.toSet()
        val found = texts.flatMap { termsOf(it, lex, emptyList()) }.distinct()
            .filter { (termCounts[it] ?: 0) >= 3 && termExamples.containsKey(it) && it !in rejectedTerms }
            .sortedWith(compareByDescending<String> { it.count { c -> c == ' ' } }.thenByDescending { termCounts[it] ?: 0 })
        val out = ArrayList<Pair<String, String>>()
        val covered = HashSet<String>()
        for (t in found) {
            if (out.size >= max) break
            if (covered.any { it.contains(t) }) continue                       // « heavenly sect » est déjà montré avec « northern heavenly sect »
            // le glossaire de l'utilisateur fixe déjà ce terme (ou une partie) : il l'emporte, pas d'exemple tiré des traductions
            if (glossary.any { g -> val gs = g.source.lowercase().trim(); gs.length >= 3 && (t.contains(gs) || gs.contains(t)) }) continue
            val ex = (if (agreed) reference(t, glossary) else termExamples[t]?.minByOrNull { it.src.length }) ?: continue
            if (contradicts(ex, glossary)) continue
            if (ex.src in page || out.any { it.first == ex.src }) continue
            out.add(ex.src to ex.tr); covered.add(t)
        }
        return out
    }

    private fun addExample(t: String, e: Example) {
        val list = termExamples.getOrPut(t) { ArrayList() }
        val i = list.indexOfFirst { it.chapter == e.chapter }
        if (i >= 0) { if (e.src.length < list[i].src.length) list[i] = e; return }
        list.add(e)
        while (list.size > MAX_PER_TERM) list.removeAt(0)                      // le plus ancien chapitre laisse sa place
    }

    /** L'exemple qui fait référence pour [t] : deux chapitres différents l'ont traduit de façon concordante (sans contredire le glossaire) ; le plus court des deux. Null sinon. */
    private fun reference(t: String, glossary: List<GlossaryEntry> = emptyList()): Example? {
        val list = termExamples[t]?.filter { !contradicts(it, glossary) } ?: return null
        var best: Example? = null
        for (i in list.indices) for (j in i + 1 until list.size) {
            val a = list[i]; val b = list[j]
            if (a.chapter == b.chapter || !agree(a, b, t)) continue
            for (e in listOf(a, b)) { val cur = best; if (cur == null || e.src.length < cur.src.length) best = e }
        }
        return best
    }

    /** Une réplique d'exemple qui contredit le glossaire (le terme anglais y figure, sa traduction fixée non) n'est jamais montrée. */
    private fun contradicts(e: Example, glossary: List<GlossaryEntry>): Boolean = glossary.any { g ->
        g.source.isNotBlank() && g.target.isNotBlank() &&
            Regex("(?i)(?<![\\p{L}])" + Regex.escape(g.source.trim()) + "(?![\\p{L}])").containsMatchIn(e.src) && !e.tr.contains(g.target.trim(), ignoreCase = true)
    }

    private fun display(n: String, chapterNames: Collection<String>): String =
        chapterNames.firstOrNull { it.lowercase() == n }?.takeIf { it.any { c -> c.isLowerCase() } } ?: n.replaceFirstChar { it.uppercase() }

    // ---------- ce qui est établi ----------

    /** Genre du narrateur (« je ») s'il est net sur toute l'œuvre : au moins 6 accords et quatre fois plus d'un genre que de l'autre. */
    @Synchronized fun narratorGender(): FrenchAgreement.Gender? = if (narratorRejected) null else when {
        narratorM >= 6 && narratorM >= 4 * narratorF -> FrenchAgreement.Gender.M
        narratorF >= 6 && narratorF >= 4 * narratorM -> FrenchAgreement.Gender.F
        else -> null
    }

    /** Les packs de vocabulaire qui conviennent à l'œuvre (voir [GenrePacks.detect]) ; [title] et [premise] y aident. */
    @Synchronized fun genres(title: String?, premise: String?): Set<String> = GenrePacks.detect(genreWords, title, premise)

    /**
     * Rappels pour le modèle (en anglais, comme le reste de la consigne) : personnages de la page et de l'œuvre avec leur genre et la façon de leur parler, genre du narrateur,
     * confusions de lettres. Seulement ce qui est net ; [texts] : les lignes de la page (les personnages qu'elles nomment passent en premier).
     */
    @Synchronized fun promptNotes(texts: List<String>, lex: EnglishLexicon?, premise: String? = null): List<String> {
        val out = ArrayList<String>()
        val page = texts.joinToString(" ").lowercase()
        val ranked = names.entries.filter { it.value.seen >= 3 }.sortedWith(compareByDescending<Map.Entry<String, Name>> { Regex("(?<![\\p{L}])" + Regex.escape(it.key) + "(?![\\p{L}])").containsMatchIn(page) }.thenByDescending { it.value.seen })
        val described = ranked.mapNotNull { (_, n) ->
            val bits = ArrayList<String>()
            when (n.gender) { FrenchAgreement.Gender.M -> bits.add("male"); FrenchAgreement.Gender.F -> bits.add("female"); null -> {} }
            when (n.register) { FrenchAgreement.Register.TU -> bits.add("addressed with \"tu\""); FrenchAgreement.Register.VOUS -> bits.add("addressed with \"vous\""); null -> {} }
            if (bits.isEmpty()) null else "${n.display} (${bits.joinToString(", ")})"
        }.take(6)
        if (described.isNotEmpty()) out.add("Characters of this series: " + described.joinToString("; ") + ".")
        // le synopsis dit le genre du héros ; à défaut, les accords déjà faits dans l'œuvre (s'ils ne le contredisent pas)
        val hero = if (narratorRejected) null else protagonistGender(premise)
        when (hero ?: narratorGender()) {
            FrenchAgreement.Gender.M -> out.add("The protagonist of this series is a man: his narration and inner thoughts (\"je\") take masculine agreement (\"je suis prêt\", \"seul\"); other speakers keep their own gender.")
            FrenchAgreement.Gender.F -> out.add("The protagonist of this series is a woman: her narration and inner thoughts (\"je\") take feminine agreement (\"je suis prête\", \"seule\"); other speakers keep their own gender.")
            null -> {}
        }
        for (p in confusions.active()) {
            val ex = confusions.examples(p, lex, 3)
            if (ex.isNotEmpty()) out.add("Reading note: in this series the lettering makes the text recognition read \"${p.second}\" as \"${p.first}\" (" + ex.joinToString(", ") { "${it.first} = ${it.second}" } + "); read through such mistakes.")
        }
        return out
    }

    /** Résumé pour le journal : personnages connus, genre du narrateur, confusions de lettres actives. */
    @Synchronized fun summary(): String =
        "noms=${names.count { it.value.seen >= 3 }}" + (narratorGender()?.let { ",narrateur=$it" } ?: "") +
            confusions.active().takeIf { it.isNotEmpty() }?.let { a -> ",lettres=" + a.joinToString("/") { "${it.first}→${it.second}" } }.orEmpty()

    // ---------- ce que l'utilisateur voit et efface (bibliothèque) ----------

    class CharacterView(val key: String, val display: String, val seen: Int, val gender: FrenchAgreement.Gender?, val register: FrenchAgreement.Register?)
    /** [reference] : deux chapitres l'ont traduit de la même façon, l'exemple est montré au modèle ; sinon il attend la confirmation d'un autre chapitre. */
    class TermView(val term: String, val seen: Int, val src: String, val tr: String, val chapters: Int, val reference: Boolean)
    class ConfusionView(val key: String, val read: Char, val meant: Char, val words: Int, val examples: List<Pair<String, String>>)
    /** Élément effacé : sorte (« nom », « terme », « lettres », « narrateur ») et clé. */
    class RejectedView(val kind: String, val key: String)
    class View(
        val characters: List<CharacterView>, val narrator: FrenchAgreement.Gender?, val narratorFromPremise: Boolean,
        val terms: List<TermView>, val confusions: List<ConfusionView>, val rejected: List<RejectedView>,
    ) {
        val isEmpty: Boolean get() = characters.isEmpty() && narrator == null && terms.isEmpty() && confusions.isEmpty()
    }

    /** Ce que la mémoire de l'œuvre a retenu et qui sert à la traduction : personnages (vus au moins trois fois), narrateur, termes avec leur réplique d'exemple, lettres mal lues. */
    @Synchronized fun view(premise: String?, lex: EnglishLexicon?): View {
        val characters = names.entries.filter { it.value.seen >= 3 }.sortedByDescending { it.value.seen }
            .map { (k, n) -> CharacterView(k, n.display, n.seen, n.gender, n.register) }
        val hero = if (narratorRejected) null else protagonistGender(premise)
        val terms = termExamples.entries.filter { (termCounts[it.key] ?: 0) >= 3 && it.value.isNotEmpty() }.sortedByDescending { termCounts[it.key] ?: 0 }.map { (t, list) ->
            val ref = reference(t)
            val shown = ref ?: list.minByOrNull { it.src.length }!!
            TermView(t, termCounts[t] ?: 0, shown.src, shown.tr, list.map { it.chapter }.distinct().size, ref != null)
        }
        val letters = confusions.active().map { p -> ConfusionView("${p.first}${p.second}", p.first, p.second, confusions.support("${p.first}${p.second}"), confusions.examples(p, lex, 3)) }
        val rejected = rejectedNames.map { RejectedView("nom", it) } + rejectedTerms.map { RejectedView("terme", it) } +
            confusions.rejected.map { RejectedView("lettres", it) } + (if (narratorRejected) listOf(RejectedView("narrateur", "narrateur")) else emptyList())
        return View(characters, hero ?: narratorGender(), hero != null, terms, letters, rejected)
    }

    /** Efface un personnage (clé en minuscules) : il n'est plus rappelé au modèle ni réappris pour cette œuvre. */
    @Synchronized fun forgetName(key: String) { val k = key.lowercase(); names.remove(k); rejectedNames.add(k); dirty = true }
    /** Efface un terme et ses répliques d'exemple : plus jamais montré ni réappris. */
    @Synchronized fun forgetTerm(term: String) { termExamples.remove(term); termCounts.remove(term); rejectedTerms.add(term); dirty = true }
    /** Efface une confusion de lettres (« PD ») : plus jamais appliquée. */
    @Synchronized fun forgetConfusion(key: String) { confusions.reject(key); dirty = true }
    /** Efface le genre du narrateur (appris ou tiré du synopsis) : plus rappelé au modèle ni réappris. */
    @Synchronized fun forgetNarrator() { narratorM = 0; narratorF = 0; narratorRejected = true; dirty = true }

    /** Rétablit un élément effacé : il peut de nouveau être appris. */
    @Synchronized fun restore(kind: String, key: String) {
        when (kind) {
            "nom" -> rejectedNames.remove(key)
            "terme" -> rejectedTerms.remove(key)
            "lettres" -> confusions.rejected.remove(key)
            "narrateur" -> narratorRejected = false
        }
        dirty = true
    }

    /** Oublie tout ce qui a été appris sur l'œuvre ; les éléments effacés un par un le restent. */
    @Synchronized fun clearLearned() {
        names.clear(); narratorM = 0; narratorF = 0; genreWords.clear(); termCounts.clear(); termExamples.clear(); confusions.clear(); dirty = true
    }

    // ---------- enregistrement ----------

    @Synchronized fun serialize(): String = buildString {
        append("V\t1\n")
        append("N\t").append(narratorM).append('\t').append(narratorF).append('\n')
        for ((k, n) in names) append("P\t").append(esc(k)).append('\t').append(esc(n.display)).append('\t').append(n.seen).append('\t').append(n.male).append('\t').append(n.female).append('\t').append(n.tu).append('\t').append(n.vous).append('\n')
        for ((w, c) in genreWords.toSortedMap()) append("G\t").append(esc(w)).append('\t').append(c).append('\n')
        for ((t, c) in termCounts.toSortedMap()) if (c >= 2) append("T\t").append(esc(t)).append('\t').append(c).append('\n')
        for ((t, list) in termExamples.toSortedMap()) for (e in list) append("F\t").append(esc(t)).append('\t').append(esc(e.chapter)).append('\t').append(esc(e.src)).append('\t').append(esc(e.tr)).append('\n')
        val sb = StringBuilder(); confusions.serialize(sb); append(sb)
        for (k in rejectedNames) append("XN\t").append(esc(k)).append('\n')
        for (k in rejectedTerms) append("XT\t").append(esc(k)).append('\n')
        for (k in confusions.rejected) append("XC\t").append(k).append('\n')
        if (narratorRejected) append("XR\n")
    }

    companion object {
        private const val MAX_NAMES = 80
        private const val MAX_TERMS = 8000
        private const val MAX_EXAMPLES = 300
        private const val MAX_EXAMPLE = 120
        private const val MAX_PER_TERM = 4
        private const val MAX_EXAMPLE_LINES = 900
        private val termWord = Regex("[a-z]+(?:'[a-z]+)?")
        /** Mots qui ne commencent ni ne finissent un terme. */
        private val termStop = (
            "the a an of to in on at and or but nor is are was were be been being am i you he she it we they me him her us them my your his its our their mine yours this that these those " +
                "what who whom why how when where which with from for by as not no yes do did does done so if then than just all some any there here i'm you're he's she's it's we're they're " +
                "that's there's what's don't can't won't didn't isn't aren't wasn't weren't i'll you'll i've you've i'd you'd let's also too very really even still only into onto out up down over about " +
                "again ever never now more most much many such own same other another each every both either neither have has had will would can could shall should may might must get got"
            ).split(' ').toSet()
        private val slang = setOf("wanna", "gonna", "gotta", "kinda", "sorta", "lemme", "gimme", "dunno", "ain't", "y'all", "ya", "yeah", "nope", "yep", "okay", "ok")
        private val credits = Regex("(?i)(?<![\\p{L}])(?:story|art|original|studio|translat\\w*|proofread\\w*|typeset\\w*|letter(?:ing|er)|colou?r(?:ing|ist|s)?|storyboard|editor|editing|qc|clean(?:er|ing)|redraw\\w*|author|illustrat\\w*|adapt\\w*|webtoon|scans?|staff|shading|grading)(?![\\p{L}])")
        /** Termes des tables de vocabulaire par genre : leur traduction y est déjà fixée. */
        private val packForms: Set<String> by lazy { GenrePacks.packs.flatMap { p -> p.terms.flatMap { it.first } }.toSet() }

        /**
         * Termes candidats d'une réplique : suites de 2 à 4 mots sans mot-outil aux bords, avec au moins un mot anglais peu courant (ou inconnu) et au moins un mot anglais connu
         * (une suite de noms propres, comme « Chung Myung », est gardée telle quelle par ailleurs) ; ni nom du chapitre seul, ni terme des tables par genre, ni interjection.
         */
        internal fun termsOf(src: String, lex: EnglishLexicon, chapterNames: Collection<String>): Set<String> {
            if (credits.findAll(src).count() >= 2) return emptySet()          // page de crédits (« Story Art Studio … Original Story … ») : pas des termes de l'histoire
            val w = termWord.findAll(src.lowercase().replace('’', '\'')).map { it.value }.toList()
            val out = LinkedHashSet<String>()
            for (n in 2..4) for (i in 0..w.size - n) {
                val g = w.subList(i, i + n)
                if (g.first() in termStop || g.last() in termStop || g.any { JunkFilter.isInterjectionToken(it) || it in slang } || g.toSet().size == 1) continue
                if (g.none { lex.rank(it) >= 4000 }) continue
                if (g.none { lex.known(it) && it !in chapterNames }) continue
                val t = g.joinToString(" ")
                if (packForms.any { f -> t == f || t.contains(f) && f.contains(' ') }) continue
                out.add(t)
            }
            return out
        }
        /**
         * Deux répliques de chapitres différents traduisent-elles un terme de la même façon ? Sans dictionnaire, on compte les mots français qu'elles ont en commun, hors mots-outils
         * et hors mots recopiés de l'anglais (un nom comme « Hua » ne dit rien de la traduction) : il en faut au moins deux (« Démon Céleste » des deux côtés pour « heavenly demon » ;
         * « Secte Céleste » contre « Secte du Paradis » pour « heavenly sect » : « secte » seul, désaccord). Prudent : un terme rendu par un seul mot (« boarding house » → « pension »)
         * n'est jamais confirmé, il reste sans réplique d'exemple, comme avant le prompt 5.
         */
        internal fun agree(a: Example, b: Example, term: String): Boolean {
            val copied = words(a.src) + words(b.src)
            val shared = (words(a.tr) - copied - frStop).intersect(words(b.tr) - copied - frStop)
            return shared.size >= 2
        }
        private val wordAny = Regex("\\p{L}{3,}")
        private fun words(s: String): Set<String> = wordAny.findAll(s.lowercase()).map { it.value }.toSet()
        private val frStop = (
            "les des une que qui quoi pour avec dans sur par pas plus mais donc car ici tout tous toute toutes cette ces son ses leur leurs mon mes ton tes nous vous ils elles elle lui eux " +
                "moi toi est sont été être avoir ont avait était sera fait faire comme sans sous vers chez aussi bien très trop peu encore déjà alors puis quand comment pourquoi même autre " +
                "autres rien personne quelque chose cela ceci celui celle ceux celles dont non oui juste vraiment"
            ).split(' ').toSet()
        private val maleRe = Regex("(?<![\\p{L}'’])(?:he|him|his|himself)(?![\\p{L}'’])")
        private val femaleRe = Regex("(?<![\\p{L}'’])(?:she|her|hers|herself)(?![\\p{L}'’])")
        private val titleMale = Regex("(?<![\\p{L}])(?:mr\\.?|sir|lord|brother|uncle|grandpa|young master|prince|king|duke|count|father)\\s+([\\p{L}]+)")
        private val titleFemale = Regex("(?<![\\p{L}])(?:ms\\.?|mrs\\.?|miss|lady|sister|aunt|grandma|young lady|princess|queen|duchess|countess|mother|madam)\\s+([\\p{L}]+)")

        private val heRe = Regex("(?i)(?<![\\p{L}'’])(?:he|him|his|himself)(?![\\p{L}'’])")
        private val sheRe = Regex("(?i)(?<![\\p{L}'’])(?:she|her|hers|herself)(?![\\p{L}'’])")
        /** Ce que le synopsis dit du héros lui-même (« une étudiante », « la méchante », « a young man ») ; pas les liens de parenté (« her husband », « sa mère »). */
        private val maleNoun = Regex("(?iu)(?<![\\p{L}'’])(?:man|boy|prince|hero|emperor|king|duke|étudiant|lycéen|homme|jeune homme|garçon|héros|prince|empereur|roi|duc|fils)(?![\\p{L}'’])")
        private val femaleNoun = Regex("(?iu)(?<![\\p{L}'’])(?:woman|girl|lady|villainess|princess|daughter|heroine|empress|queen|duchess|étudiante|lycéenne|femme|jeune femme|fille|héroïne|méchante|princesse|impératrice|reine|duchesse)(?![\\p{L}'’])")
        /** « il » personnel seulement : « il y a », « il faut », « il s'agit », « il était une fois » ne désignent personne. */
        private val ilRe = Regex("(?i)(?<![\\p{L}'’])(?:il(?!\\s+(?:y\\b|faut|fallait|faudra|semble|semblait|paraît|s['’]agit|était une fois|est temps|reste))|lui-même)(?![\\p{L}'’])")
        private val elleRe = Regex("(?i)(?<![\\p{L}'’])(?:elle|elle-même)(?![\\p{L}'’])")

        /**
         * Genre du héros d'après le synopsis (anglais ou français) : les pronoms qui le désignent dominent nettement (au moins trois, trois fois plus que l'autre genre).
         * Null si le synopsis est court, mêle plusieurs personnages des deux genres ou n'en dit rien.
         */
        fun protagonistGender(premise: String?): FrenchAgreement.Gender? {
            if (premise.isNullOrBlank()) return null
            val m = heRe.findAll(premise).count() + ilRe.findAll(premise).count() + maleNoun.findAll(premise).count()
            val f = sheRe.findAll(premise).count() + elleRe.findAll(premise).count() + femaleNoun.findAll(premise).count()
            return when { m >= 3 && m >= 3 * f -> FrenchAgreement.Gender.M; f >= 3 && f >= 3 * m -> FrenchAgreement.Gender.F; else -> null }
        }

        /** Noms propres du synopsis (« Penelope », « Eckhart », « Dokja ») : mots à majuscule de quatre lettres au moins qui ne sont pas des mots anglais. Minuscules. */
        fun premiseNames(premise: String?, lex: EnglishLexicon?): Set<String> {
            if (premise.isNullOrBlank()) return emptySet()
            return Regex("(?<![\\p{L}'’])\\p{Lu}\\p{Ll}{3,}(?![\\p{L}])").findAll(premise).map { it.value.lowercase() }.filter { w -> lex == null || !lex.known(w) }.toSet()
        }

        fun parse(key: String, data: String): SeriesMemory {
            val m = SeriesMemory(key)
            for (line in data.lines()) {
                val p = line.split('\t')
                try {
                    when (p[0]) {
                        "N" -> { m.narratorM = p[1].toInt(); m.narratorF = p[2].toInt() }
                        "P" -> if (p.size >= 8) m.names[unesc(p[1])] = Name(unesc(p[2])).apply { seen = p[3].toInt(); male = p[4].toInt(); female = p[5].toInt(); tu = p[6].toInt(); vous = p[7].toInt() }
                        "G" -> if (p.size >= 3) m.genreWords[unesc(p[1])] = p[2].toInt()
                        "T" -> if (p.size >= 3) m.termCounts[unesc(p[1])] = p[2].toInt()
                        // avant le build 269 : un exemple par terme, chapitre inconnu (« ? ») ; il ne fait référence qu'une fois confirmé par un chapitre traduit depuis
                        "E" -> if (p.size >= 4) m.addExample(unesc(p[1]), Example(unesc(p[2]), unesc(p[3]), "?"))
                        "F" -> if (p.size >= 5) m.addExample(unesc(p[1]), Example(unesc(p[3]), unesc(p[4]), unesc(p[2])))
                        "XN" -> if (p.size >= 2) m.rejectedNames.add(unesc(p[1]))
                        "XT" -> if (p.size >= 2) m.rejectedTerms.add(unesc(p[1]))
                        "XC" -> if (p.size >= 2) m.confusions.rejected.add(p[1])
                        "XR" -> m.narratorRejected = true
                        "C" -> if (p.size >= 3) m.confusions.load(p[1], p[2])
                    }
                } catch (_: Exception) {}
            }
            return m
        }
    }
}
