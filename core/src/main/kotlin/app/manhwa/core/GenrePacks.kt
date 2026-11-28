package app.manhwa.core

/**
 * Vocabulaire d'usage des traductions françaises par genre (arts martiaux, fantasy de cour, chasseurs et jeux, académies de magie, lycée moderne) : un petit modèle traduit
 * volontiers « Gate » par « porte », « Mount Hua » par « Mount Hua » ou « Beggars' Union » par « Union des Mendicants ». Le genre de l'œuvre se reconnaît aux mots
 * qui reviennent dans ses bulles (voir [SeriesMemory]), à son titre et à son synopsis ; seuls les termes présents dans la page sont rappelés au modèle.
 *
 * Format de chaque terme : `terme anglais | équivalent français` ; plusieurs formes anglaises séparées par « / » (la plus longue est cherchée d'abord).
 */
object GenrePacks {
    class Pack(val id: String, val cues: Set<String>, val terms: List<Pair<List<String>, String>>)

    /** Une entrée retenue pour la page : [source] tel que la page l'écrit (forme de la table), [french] ce qu'il faut écrire. */
    data class Hit(val source: String, val french: String)

    private fun pack(id: String, cues: String, table: String): Pack = Pack(
        id,
        cues.split(Regex("\\s*,\\s*")).filter { it.isNotBlank() }.toSet(),
        table.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { l ->
            val p = l.split('|').map { it.trim() }
            if (p.size < 2) null else p[0].split('/').map { it.trim().lowercase() }.filter { it.isNotEmpty() } to p[1]
        },
    )

    val packs: List<Pack> = listOf(
        pack(
            "murim",
            "sect, sects, murim, martial, qi, dantian, meridian, meridians, cultivation, disciple, disciples, elder, elders, sahyung, sajae, sasuk, sajil, swordsmanship, jianghu, wulin, heavenly demon, sect leader, inner energy, internal energy, sword technique, palm, clan, orthodox, unorthodox, demonic cult, alliance, martial arts, pavilion, sword energy, sword qi",
            """
            mount hua sect / mt. hua sect / hwasan sect | secte du Mont Hua
            mount hua / mt. hua | Mont Hua
            beggars' union / beggars union / beggar's union / beggar sect / beggars' sect | Union des Mendiants
            woodang / wudang | Wudang
            shaolin temple | temple de Shaolin
            heavenly demon sect / heavenly demon cult | secte du Démon Céleste
            heavenly demon | Démon Céleste
            demonic cult / demon cult | culte démoniaque
            murim alliance | Alliance du Murim
            ten great sects / 10 great sects | Dix Grandes Sectes
            nine great sects / 9 great sects | Neuf Grandes Sectes
            five great families / 5 great families | Cinq Grandes Familles
            sect leader | chef de la secte
            grand elder / great elder | grand ancien
            elder | ancien
            patriarch | patriarche
            young master | jeune maître
            senior brother / senior martial brother | frère aîné
            junior brother / junior martial brother | frère cadet
            senior sister / senior martial sister | sœur aînée
            junior sister / junior martial sister | sœur cadette
            martial brother | frère d'armes
            martial artist / martial artists | pratiquant des arts martiaux
            martial arts | arts martiaux
            inner energy / internal energy / internal qi | énergie interne
            sword technique / sword techniques | technique d'épée
            sword art / sword arts | art de l'épée
            sword saint | saint de l'épée
            sword energy / sword qi | énergie d'épée
            sword aura | aura d'épée
            swordsmanship | maîtrise de l'épée
            plum blossom sword | épée de la Fleur de Prunier
            plum blossom | fleur de prunier
            orthodox faction / orthodox sects | factions orthodoxes
            unorthodox faction / unorthodox sects | factions hétérodoxes
            dantian | dantian
            meridians | méridiens
            acupoints / pressure points | points vitaux
            """,
        ),
        pack(
            "noble",
            "duke, duchess, marquis, marquess, count, countess, viscount, baron, baroness, highness, majesty, empire, emperor, empress, imperial, knight, knights, duchy, young lady, crown prince, princess, prince, villainess, heroine, ball, banquet, butler, maid, nobility, noble, nobles, debutante, lady",
            """
            grand duke | grand-duc
            grand duchess | grande-duchesse
            crown prince | prince héritier
            crown princess | princesse héritière
            your majesty | Votre Majesté
            his majesty | Sa Majesté
            her majesty | Sa Majesté
            your highness | Votre Altesse
            his highness / her highness | Son Altesse
            your grace | Votre Grâce
            young lady | jeune demoiselle
            young master | jeune maître
            duke | duc
            duchess | duchesse
            marquis / marquess | marquis
            marchioness | marquise
            viscount | vicomte
            viscountess | vicomtesse
            countess | comtesse
            baroness | baronne
            imperial palace | palais impérial
            imperial knights | chevaliers impériaux
            holy knight / holy knights | chevalier sacré
            head maid | gouvernante
            butler | majordome
            debutante | débutante
            villainess | méchante
            male lead / male leads | personnage masculin principal
            female lead | héroïne
            affection meter / affection score | jauge d'affection
            """,
        ),
        pack(
            "hunter",
            "hunter, hunters, gate, gates, dungeon, dungeons, awakening, awakened, guild, s-rank, rank, level up, skill, skills, status window, quest, system, raid, stats, mana, inventory, player, players, ranker, tutorial, stage clear, boss, summon, summoned, gacha, heroes, exp, constellation, constellations, scenario, dokkaebi, monster, monsters",
            """
            hunter association | Association des chasseurs
            hunter license | licence de chasseur
            hunter / hunters | chasseur
            dungeon break | débordement de donjon
            gate / gates | portail
            dungeon | donjon
            awakening | éveil
            awakened / awakener / awakeners | éveillé
            guild master | maître de guilde
            guild | guilde
            status window / status screen | fenêtre de statut
            main quest | quête principale
            sub quest / side quest | quête secondaire
            hidden quest | quête cachée
            quest | quête
            skill / skills | compétence
            level up / leveled up / levelled up | niveau supérieur
            stage clear / stage cleared | niveau terminé
            drag and drop | glisser-déposer
            drop rate | taux d'obtention
            summon card / summon ticket | ticket d'invocation
            summon | invocation (verb: invoquer)
            ranker / rankers | joueur du classement
            mana stone / magic stone | pierre de mana
            cooldown | temps de recharge
            constellation / constellations | Constellation
            dokkaebi | dokkaebi
            main scenario | scénario principal
            scenario | scénario
            """,
        ),
        pack(
            "magic",
            "mage, mages, magic, spell, spells, circle, wizard, wizards, academy, professor, archmage, magic tower, mana, sorcery, sorcerer, enchant, enchantment, incantation, magician",
            """
            archmage / arch mage | archimage
            magic tower / mage tower / tower of magic | tour des mages
            magic circle | cercle magique
            mage / mages | mage
            spell / spells | sort
            headmaster | directeur
            familiar | familier
            """,
        ),
        pack(
            "school",
            "school, class, teacher, homeroom, transfer, student, students, hyung, noona, oppa, unnie, sunbae, sunbaenim, gang, punk, bully, bullied, freshman, classroom",
            """
            homeroom teacher | professeur principal
            transfer student | nouvel élève
            vocational school | lycée professionnel
            high school / high schooler | lycée
            middle school | collège
            class president | délégué de classe
            hyung | hyung
            noona | noona
            oppa | oppa
            unnie | unnie
            sunbae / sunbaenim | sunbae
            """,
        ),
    )

    private val wordRe = Regex("[a-z][a-z'’.-]*")

    /** Mots indicateurs de genre présents dans une bulle (une fois chacun). */
    fun cueWords(text: String): Set<String> {
        val low = " " + text.lowercase().replace('’', '\'').replace(Regex("[^a-z'.-]+"), " ") + " "
        val out = HashSet<String>()
        for (p in packs) for (c in p.cues) if (low.contains(" $c ")) out.add(c)
        return out
    }

    /**
     * Genres de l'œuvre : un pack convient quand au moins trois de ses mots indicateurs reviennent dans les bulles (chacun dans deux bulles au moins), le titre et le synopsis
     * comptant comme des bulles de plus.
     */
    fun detect(words: Map<String, Int>, title: String?, premise: String?): Set<String> {
        val extra = (cueWords(title ?: "") + cueWords(premise ?: "")).associateWith { 2 }
        val out = HashSet<String>()
        for (p in packs) {
            val n = p.cues.count { c -> (words[c] ?: 0) + (extra[c] ?: 0) >= 2 }
            if (n >= 3) out.add(p.id)
        }
        return out
    }

    /** Termes des packs [ids] présents dans les lignes de la page (forme la plus longue d'abord ; un terme contenu dans un autre déjà trouvé est écarté). */
    fun find(ids: Set<String>, texts: List<String>, max: Int = 12): List<Hit> {
        if (ids.isEmpty()) return emptyList()
        val low = " " + texts.joinToString(" ¶ ").lowercase().replace('’', '\'').replace(Regex("[^a-z0-9'.¶-]+"), " ") + " "
        val found = ArrayList<Pair<String, String>>()
        val taken = ArrayList<IntRange>()
        val all = packs.filter { it.id in ids }.flatMap { p -> p.terms.flatMap { (forms, fr) -> forms.map { it to fr } } }.sortedByDescending { it.first.length }
        for ((form, fr) in all) {
            var i = low.indexOf(" $form ")
            if (i < 0 && !form.endsWith("s")) i = low.indexOf(" ${form}s ")
            if (i < 0) continue
            val r = i..(i + form.length + 1)
            if (taken.any { it.first <= r.last && r.first <= it.last }) continue
            taken.add(r)
            if (found.none { it.second == fr }) found.add(form to fr)
            if (found.size >= max) break
        }
        return found.map { Hit(it.first, it.second) }
    }
}
