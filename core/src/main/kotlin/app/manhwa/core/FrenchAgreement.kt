package app.manhwa.core

/**
 * Accords du français qu'un petit modèle rate d'une ligne à l'autre : « tu » ou « vous » (le « you » anglais ne dit ni combien de personnes ni quel respect)
 * et genre du narrateur (« je suis humain » / « humaine »). Les règles sont prudentes : un contrôle ne signale que les cas sans ambiguïté, appuyés sur le texte
 * anglais de la ligne ou sur ce que le chapitre a déjà établi, et la ligne n'est refaite qu'une fois (voir [TranslationChecks]).
 */
object FrenchAgreement {
    enum class Register { TU, VOUS }
    enum class Gender { M, F }

    /** Nombre de formes de tutoiement et de vouvoiement d'une ligne. */
    class Forms(val tu: Int, val vous: Int) {
        /** Le registre de la ligne, ou null si elle n'en a aucun ou mélange les deux. */
        val register: Register? get() = if (tu > 0 && vous == 0) Register.TU else if (vous > 0 && tu == 0) Register.VOUS else null
    }

    // ---------- tu / vous dans la traduction ----------

    private val tuRe = Regex("(?<![\\p{L}])(?:tu|toi|tes|ta|te)(?![\\p{L}])|(?<![\\p{L}])t['’](?=\\p{L})", RegexOption.IGNORE_CASE)
    private val tonRe = Regex("(?<![\\p{L}])ton(?![\\p{L}])", RegexOption.IGNORE_CASE)
    /** « le ton », « d'un ton », « sur un ton » : le nom, pas le possessif. */
    private val beforeToneNoun = setOf("le", "un", "ce", "du", "au", "sur", "même", "d'un", "d’un", "ton")
    private val vousRe = Regex("(?<![\\p{L}])(?:vous|votre|vos|vôtres?)(?![\\p{L}])", RegexOption.IGNORE_CASE)

    fun forms(tr: String): Forms {
        var tu = tuRe.findAll(tr).count()
        for (m in tonRe.findAll(tr)) {
            val prev = tr.substring(0, m.range.first).trimEnd().takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.lowercase()
            if (prev !in beforeToneNoun) tu++
        }
        return Forms(tu, vousRe.findAll(tr).count())
    }

    // ---------- ce que dit l'anglais ----------

    private const val PLURAL_NOUNS = "fools|idiots|bastards|cowards|traitors|monsters|demons|villagers|thieves|murderers|liars|brats|dogs|pigs|rats|bandits|mercenaries|soldiers|men|women|people|guys|ladies|gentlemen|brothers|sisters|kids|children|boys|girls|humans|devils|wretches|scoundrels|morons|imbeciles|fiends|ingrates|rascals|disciples"

    /** Plusieurs personnes sont interpellées : « you all », « everyone » en apostrophe (« TOUT LE MONDE ! »), « yourselves », « you idiots »… Un « everyone » complément (« you killed everyone ») n'en est pas un. */
    private val pluralCue = Regex(
        "(?i)(?<![\\p{L}'’])(?:you\\s+all|all\\s+of\\s+you|both\\s+of\\s+you|each\\s+of\\s+you|every\\s+one\\s+of\\s+you|every\\s+single\\s+one\\s+of\\s+you|you\\s+guys|you\\s+people|you\\s+lot|you\\s+two|you\\s+three|you\\s+both|y['’]all|yourselves|" +
            "you\\s+(?:[\\p{L}'’]+\\s+){0,2}?(?:$PLURAL_NOUNS))(?![\\p{L}'’])"
    )
    /** « EVERYONE » seul, en début de phrase ou après une virgule, suivi d'un signe : c'est l'apostrophe (« LISTEN UP, EVERYONE! »). */
    private val everyoneVocative = Regex("(?i)(?:^|[.!?…]\\s*|,\\s*)(?:everyone|everybody|all\\s+of\\s+us\\s+here)\\s*(?:[,!?…]|$)")

    fun pluralCue(src: String): String? {
        pluralCue.find(src)?.let { return it.value.trim() }
        everyoneVocative.find(src)?.let { return "everyone" }
        return null
    }

    /** Titres de respect : « vous » est alors légitime même dans un chapitre où l'on se tutoie. */
    private val honorificCue = Regex("(?i)(?<![\\p{L}'’])(?:sir|sire|my\\s+lord|lord|lady|majesty|highness|excellency|your\\s+grace|master|mistress|elder|elders|senior|ma['’]am|madam|miss|your\\s+honou?r|lordship|ladyship|patriarch|grandmaster|emperor|empress|king|queen|prince|princess|duke|duchess|general|commander|chief|captain|boss)(?![\\p{L}'’])")
    fun honorificCue(src: String): Boolean = honorificCue.containsMatchIn(src)

    /**
     * Le nom (parmi [names], en minuscules) auquel la ligne s'adresse : en tête de ligne (« GAF, WHAT… », « G-GAF, PLEASE… »), en fin de ligne après une virgule
     * (« …, GAF! »), ou entre virgules. Un nom cité sans adresse (« GAF IS RIGHT ») n'en est pas un.
     */
    fun addressee(src: String, names: Collection<String>): String? {
        val s = src.trim()
        for (n in names) {
            val name = n.lowercase()
            if (name.length < 3 || name.any { !it.isLetter() }) continue
            val q = Regex.escape(name)
            val rx = Regex("(?i)(?:^\\s*(?:\\p{L}-){0,2}$q(?![\\p{L}'’])\\s*[,!?.…]|[,!?.…]\\s*(?:\\p{L}-)?$q\\s*[!?.…]*\\s*$|,\\s*$q\\s*[,!?.…])")
            if (rx.containsMatchIn(s)) return name
        }
        return null
    }

    // ---------- genre du narrateur ----------

    private const val ADVERBS = "(?:pas|plus|jamais|toujours|déjà|enfin|vraiment|très|trop|si|tellement|bien|presque|complètement|totalement|simplement|juste|même|encore|aussi|maintenant|seulement|plutôt|assez|peu|un\\s+peu|à\\s+moitié|à\\s+demi|tout\\s+à\\s+fait|réellement|sérieusement|vraiment|donc)"
    private val firstPerson = Regex(
        "(?i)(?<![\\p{L}])(?:je|j['’])\\s*(?:ne\\s+|n['’]\\s*)?(?:suis|étais|serai|serais|resterai|reste|restais|deviens|devenais|me\\s+sens|me\\s+sentais|me\\s+suis|m['’]étais)\\s+(?:$ADVERBS\\s+)*([\\p{L}'’-]+)"
    )
    private val invertedFirstPerson = Regex("(?i)(?<![\\p{L}])(?:suis|étais|serais|resterai)-je\\s+(?:$ADVERBS\\s+)*([\\p{L}'’-]+)")
    private val notAdjective = setOf(
        "un", "une", "le", "la", "les", "l", "ce", "cet", "cette", "ces", "mon", "ma", "mes", "en", "dans", "à", "au", "aux", "de", "d", "du", "des", "avec", "chez", "sur", "sous", "par", "pour", "ici", "là", "loin", "près", "ainsi", "aussi",
        "ni", "oui", "non", "qui", "que", "qu", "ça", "cela", "quoi", "où", "comme", "lui", "moi", "toi", "elle", "il", "ils", "elles", "nous", "vous", "souvent", "jamais", "désormais", "mais", "puis", "bien", "rien", "avant", "devant",
        "pendant", "maintenant", "pourtant", "cependant", "tant", "autant", "dedans", "dehors", "midi", "merci", "souci", "aujourd'hui", "aujourd’hui", "tout", "toute", "sa", "son", "ses", "notre", "votre", "leur", "quelqu'un", "quelqu’un",
    )
    private val feminine = Regex("(?:ée|ées|euse|euses|trice|trices|ette|ettes|enne|ennes|aine|aines|ive|ives|ète|ètes|ante|antes|ente|entes|inte|intes|eule|eules|elle|elles|olle|olles|orte|ortes|ande|andes|ite|ites|ise|ises|ue|ues|ie|ies|ouse|ouses|aise|aises|prête|prêtes|bonne|bonnes|anche|aîche|èche)$")
    private val masculine = Regex("(?:é|i|u|is|it|eux|oux|if|ain|ien|[eê]t|ant|ent|ort|and|ond|fou)$")

    /** Genre d'un adjectif ou participe qui s'accorde avec « je » ; null si l'on ne peut pas le dire (invariable, mot-outil, adverbe). */
    fun genderOf(word: String): Gender? {
        val w = word.lowercase().trim('\'', '’', '-')
        if (w.length < 3 && w != "né") return null
        if (w in notAdjective || w.endsWith("ment") || w.any { it == '\'' || it == '’' }) return null   // « d'accord », « d'avis » : locutions sans genre
        if (feminine.containsMatchIn(w)) return Gender.F
        if (masculine.containsMatchIn(w)) return Gender.M
        return null
    }

    /** Les genres que la ligne [tr] donne au narrateur (« je suis humaine » → F). */
    fun narratorGenders(tr: String): List<Pair<Gender, String>> =
        (firstPerson.findAll(tr) + invertedFirstPerson.findAll(tr)).mapNotNull { m -> val w = m.groupValues[1]; genderOf(w)?.let { it to w } }.toList()

    // ---------- contrôles ----------

    /**
     * Problèmes d'accord de la ligne [tr] (traduction de [src]) : plusieurs personnes tutoyées, « tu » et « vous » mêlés, interlocuteur nommé tutoyé puis vouvoyé,
     * chapitre qui tutoie et ligne qui vouvoie. [style] : ce que le chapitre a déjà établi (null : seulement l'anglais de la ligne).
     * Le genre du narrateur n'est pas contrôlé : on ne sait pas qui parle, et refaire une réplique de femme au masculin serait pire que l'incohérence ; il n'est que rappelé au modèle.
     */
    fun problems(src: String, tr: String, names: Collection<String>, style: StyleMemory?): List<TranslationChecks.Problem> {
        val out = ArrayList<TranslationChecks.Problem>()
        val f = forms(tr)
        val plural = pluralCue(src)
        if (plural != null && f.tu > 0) {
            out.add(TranslationChecks.Problem("accord", "plusieurs personnes (« $plural ») : tout vouvoyer (« vous », « votre », « vos », impératif en -ez), jamais « tu », « toi », « ton », « te »"))
        } else if (plural == null && f.tu > 0 && f.vous > 0) {
            val usual = style?.usualRegister()
            val how = when (usual) { Register.TU -> " ; dans ce chapitre on tutoie : « tu »"; Register.VOUS -> " ; dans ce chapitre on vouvoie : « vous »"; null -> "" }
            out.add(TranslationChecks.Problem("accord", "« tu » et « vous » mélangés dans la même ligne : une seule forme pour une même personne$how"))
        }
        if (style != null && plural == null) {
            val reg = f.register
            val who = addressee(src, names)
            val known = who?.let { style.registerWith(it) }
            if (reg != null && known != null && reg != known) {
                out.add(TranslationChecks.Problem("accord", "dans ce chapitre on ${if (known == Register.TU) "tutoie" else "vouvoie"} « ${who!!.replaceFirstChar { it.uppercase() }} » : ${if (known == Register.TU) "« tu », « ton », « te »" else "« vous », « votre »"}"))
            } else if (reg == Register.VOUS && who == null && !honorificCue(src) && style.usualRegister() == Register.TU && style.strongTu()) {
                out.add(TranslationChecks.Problem("accord", "ce chapitre tutoie et personne de respecté n'est interpellé : « tu », « ton », « te » pour une seule personne"))
            }
        }
        return out
    }
}

/**
 * Ce que le chapitre a établi en traduisant : tutoiement ou vouvoiement (en général et par interlocuteur nommé) et genre du narrateur. Alimentée par chaque ligne
 * traduite ; sert à signaler une ligne dont le tutoiement contredit ce qui a été fixé, et à rappeler au modèle ce qui est net (tutoiement, genre du narrateur).
 */
class StyleMemory {
    private val recent = ArrayDeque<FrenchAgreement.Register>()
    private val byName = HashMap<String, IntArray>()
    private val narrator = ArrayDeque<FrenchAgreement.Gender>()

    fun copy(): StyleMemory {
        val c = StyleMemory()
        c.recent.addAll(recent); c.narrator.addAll(narrator)
        for ((k, v) in byName) c.byName[k] = v.copyOf()
        return c
    }

    fun reset() { recent.clear(); byName.clear(); narrator.clear() }

    /** Prend en compte une ligne traduite. [names] : noms du chapitre (pour repérer à qui l'on parle). */
    fun observe(src: String, tr: String, names: Collection<String>) {
        val f = FrenchAgreement.forms(tr)
        val reg = f.register
        // une foule ou un supérieur qu'on vouvoie n'apprend rien sur la façon dont on parle à un seul interlocuteur ordinaire
        if (reg != null && FrenchAgreement.pluralCue(src) == null) {
            if (!(reg == FrenchAgreement.Register.VOUS && FrenchAgreement.honorificCue(src))) {
                recent.addLast(reg); while (recent.size > WINDOW) recent.removeFirst()
            }
            FrenchAgreement.addressee(src, names)?.let { n -> byName.getOrPut(n) { IntArray(2) }[if (reg == FrenchAgreement.Register.TU) 0 else 1]++ }
        }
        for ((g, _) in FrenchAgreement.narratorGenders(tr)) { narrator.addLast(g); while (narrator.size > WINDOW) narrator.removeFirst() }
    }

    private fun share(): Float? = if (recent.size < MIN_LINES) null else recent.count { it == FrenchAgreement.Register.TU }.toFloat() / recent.size

    /** Le registre qui domine nettement (au moins 8 lignes sur 10) ; null s'il n'y a pas assez de lignes ou si c'est partagé. */
    fun usualRegister(): FrenchAgreement.Register? {
        val s = share() ?: return null
        return if (s >= 0.8f) FrenchAgreement.Register.TU else if (s <= 0.2f) FrenchAgreement.Register.VOUS else null
    }

    /** Tutoiement très net : au moins 10 lignes et 90 % de « tu ». */
    fun strongTu(): Boolean = recent.size >= STRONG_LINES && (share() ?: 0f) >= 0.9f

    /** Registre employé avec [name] (en minuscules) s'il est constant : au moins deux lignes et aucune de l'autre forme. */
    fun registerWith(name: String): FrenchAgreement.Register? {
        val c = byName[name] ?: return null
        return when { c[0] >= 3 && c[1] == 0 -> FrenchAgreement.Register.TU; c[1] >= 3 && c[0] == 0 -> FrenchAgreement.Register.VOUS; else -> null }
    }

    /** Genre du narrateur s'il est constant : au moins 4 accords d'un genre et aucun de l'autre. */
    fun narratorGender(): FrenchAgreement.Gender? {
        val m = narrator.count { it == FrenchAgreement.Gender.M }; val f = narrator.size - m
        return when { m >= 4 && f == 0 -> FrenchAgreement.Gender.M; f >= 4 && m == 0 -> FrenchAgreement.Gender.F; else -> null }
    }

    /** Rappels (en anglais, pour le modèle) de ce qui est établi, quand c'est net ; vide sinon. */
    fun hints(): List<String> {
        val out = ArrayList<String>()
        when (usualRegister()) {
            FrenchAgreement.Register.TU -> out.add("Register so far: one person is addressed with \"tu\" (tu, toi, ton, ta, tes, te). Keep \"tu\" for one person; use \"vous\" only for several people at once, or for a lord, elder or other superior addressed formally.")
            FrenchAgreement.Register.VOUS -> out.add("Register so far: one person is addressed with \"vous\" (vous, votre, vos). Keep \"vous\" for one person unless the speakers are clearly close friends or family.")
            null -> {}
        }
        val named = byName.entries.mapNotNull { (n, _) -> registerWith(n)?.let { n.replaceFirstChar { c -> c.uppercase() } + " -> " + it.name.lowercase() } }.take(4)
        if (named.isNotEmpty()) out.add("Established with these characters (keep it): " + named.joinToString(", "))
        when (narratorGender()) {
            FrenchAgreement.Gender.M -> out.add("The speaker \"I\" has been male so far (masculine agreement: \"je suis prêt\", \"fatigué\", \"humain\"). Keep the masculine for \"je\" unless a line is clearly spoken by a woman.")
            FrenchAgreement.Gender.F -> out.add("The speaker \"I\" has been female so far (feminine agreement: \"je suis prête\", \"fatiguée\", \"humaine\"). Keep the feminine for \"je\" unless a line is clearly spoken by a man.")
            null -> {}
        }
        return out
    }

    private companion object {
        const val WINDOW = 40
        const val MIN_LINES = 6
        const val STRONG_LINES = 10
    }
}
