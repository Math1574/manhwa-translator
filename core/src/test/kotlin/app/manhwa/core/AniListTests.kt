package app.manhwa.core

private val WORKS_JSON = """{"data":{"Page":{"media":[
 {"id":105398,"title":{"romaji":"Na Honjaman Level-Up","english":"Solo Leveling","native":"나 혼자만 레벨업"},"format":"MANGA","countryOfOrigin":"KR","startDate":{"year":2018},"isAdult":false},
 {"id":103047,"title":{"romaji":"Na Honjaman Level Up","english":null,"native":"나 혼자만 레벨업"},"format":"NOVEL","countryOfOrigin":"KR","startDate":{"year":2016},"isAdult":false},
 {"id":7,"title":{"romaji":"Sample Adult","english":"sample adult","native":null},"format":"MANGA","countryOfOrigin":"JP","startDate":{"year":null},"isAdult":true},
 {"id":8,"title":{"romaji":null,"english":null,"native":null},"format":"MANGA","countryOfOrigin":"CN","startDate":{"year":2020},"isAdult":false},
 {"title":{"romaji":"Sans numéro"}}
]}}}"""

private val CHARS_JSON = """{"data":{"Media":{"characters":{"pageInfo":{"hasNextPage":true},"edges":[
 {"role":"SUPPORTING","node":{"name":{"first":"Eren","middle":null,"last":"Yeager","full":"Eren Yeager","native":"エレン","alternative":[]}}},
 {"role":"MAIN","node":{"name":{"first":"Jinwoo","middle":null,"last":"Sung","full":"Jinwoo Sung","native":"성진우","alternative":["Jin-Woo","Shadow Monarch","Sung Jin Woo"]}}},
 {"role":"BACKGROUND","node":{"name":{"first":"Kai","middle":null,"last":null,"full":"Kai","native":null,"alternative":null}}},
 {"role":"MAIN","node":{"name":{"first":"Cha Hae-In","middle":null,"last":null,"full":"Cha Hae-In","native":null,"alternative":["Haein"]}}},
 {"role":"SUPPORTING","node":{"name":{"first":null,"middle":null,"last":null,"full":null,"native":null,"alternative":[]}}},
 {"role":"SUPPORTING","node":{"name":{"first":"Léonie","middle":null,"last":"D'Arc","full":"Léonie D'Arc","native":null,"alternative":["성진우"]}}}
]}}}}"""

private fun aniLex() = EnglishLexicon(listOf("sung", "shadow", "monarch", "light", "sing"), listOf("weren", "television", "the", "and", "alarming"))

/** Suggestions d'après AniList : requêtes, lecture des réponses, noms proposés (jamais d'ajout sans accord : ce sont des propositions). */
fun aniListTests() {
    T.test("AniList : la requête de recherche est un JSON valide, titre échappé") {
        val req = AniList.searchRequest("  He said \"hi\" \\ ok\n", true)
        val m = MiniJson.parse(req) as Map<*, *>
        val v = m["variables"] as Map<*, *>
        T.eq("He said \"hi\" \\ ok", v["s"], "titre tel quel, sans espaces autour")
        T.eq(AniList.WORKS.toDouble(), v["n"])
        val q = m["query"] as String
        T.ok(q.contains("search:\$s") && q.contains("type:MANGA") && q.contains("countryOfOrigin"), "requête complète")
        val small = MiniJson.parse(AniList.searchRequest("x", false)) as Map<*, *>
        T.ok(!(small["query"] as String).contains("countryOfOrigin"), "version réduite sans les champs facultatifs")
    }

    T.test("AniList : la requête des personnages porte l'identifiant et la page, tri facultatif") {
        val m = MiniJson.parse(AniList.charactersRequest(105398, 2)) as Map<*, *>
        val v = m["variables"] as Map<*, *>
        T.eq(105398.0, v["id"]); T.eq(2.0, v["p"]); T.eq(AniList.PER_PAGE.toDouble(), v["n"])
        T.ok((m["query"] as String).contains("sort:[ROLE,FAVOURITES_DESC]"), "principaux d'abord")
        T.ok(!((MiniJson.parse(AniList.charactersRequest(1, 1, false)) as Map<*, *>)["query"] as String).contains("sort:"), "repli sans tri")
    }

    T.test("AniList : œuvres lues — titre, autre titre, année, genre, 18+") {
        val w = AniList.parseWorks(WORKS_JSON)
        T.eq(3, w.size, "les œuvres sans titre ou sans numéro sont laissées de côté")
        T.eq("Solo Leveling", w[0].title); T.eq("Na Honjaman Level-Up", w[0].altTitle); T.eq(2018, w[0].year); T.eq("Manhwa", w[0].kind); T.eq("Manhwa · 2018", w[0].details())
        T.eq("Na Honjaman Level Up", w[1].title, "sans titre anglais : le romaji"); T.eq("나 혼자만 레벨업", w[1].altTitle); T.eq("Roman", w[1].kind)
        T.eq(null, w[2].altTitle, "romaji et anglais identiques à la casse près : pas d'autre titre"); T.ok(w[2].adult, "18+"); T.eq("Manga · 18+", w[2].details(), "sans année")
        T.eq(emptyList<AniListWork>(), AniList.parseWorks("pas du json"))
        T.eq(emptyList<AniListWork>(), AniList.parseWorks("""{"errors":[{"message":"Not Found","status":404}],"data":null}"""))
        T.eq(emptyList<AniListWork>(), AniList.parseWorks("""{"data":{"Page":{"media":[""" ), "réponse coupée")
    }

    T.test("AniList : personnages lus, sans noms à spoiler, figurants sans nom écartés") {
        val p = AniList.parseCharacters(CHARS_JSON)
        T.ok(p.hasNext, "d'autres pages")
        T.eq(5, p.characters.size, "le personnage sans aucun nom est laissé de côté")
        val jin = p.characters.first { it.full == "Jinwoo Sung" }
        T.eq("MAIN", jin.role); T.eq("Sung", jin.last); T.eq(listOf("Jin-Woo", "Shadow Monarch", "Sung Jin Woo"), jin.alternatives)
        T.eq(emptyList<String>(), p.characters.first { it.full == "Kai" }.alternatives, "alternative: null")
        T.eq(0, AniList.parseCharacters("{}").characters.size); T.ok(!AniList.parseCharacters("[").hasNext, "réponse coupée : pas de suite")
    }

    T.test("AniList : noms proposés — morceaux de nom, principaux d'abord, mots courants écartés") {
        val chars = AniList.parseCharacters(CHARS_JSON).characters
        val r = AniList.proposals(chars, emptySet(), aniLex())
        T.eq(listOf("Jinwoo", "Jin-Woo", "Hae-In", "Haein", "Yeager", "Léonie", "D'Arc"), r.candidates.map { it.name }, "ordre : principaux, secondaires, figurants ; une seule fois chacun")
        T.eq("Jinwoo Sung", r.candidates[0].owner); T.eq("MAIN", r.candidates[0].role); T.eq("Jinwoo Sung", r.candidates[1].owner)
        T.eq("Cha Hae-In", r.candidates[2].owner); T.eq("Eren Yeager", r.candidates[4].owner); T.eq("SUPPORTING", r.candidates[4].role)
        T.ok("Sung" in r.skipped && "Shadow" in r.skipped && "Monarch" in r.skipped, "mots anglais écartés")
        T.ok("Eren" in r.skipped, "« eren » est dans « weren't » : jamais proposé")
        T.ok("Kai" in r.skipped && "Cha" in r.skipped && "Jin" in r.skipped, "moins de 4 lettres : écarté, signalé")
        T.ok(r.candidates.none { it.name.any { c -> c.code > 0x24F } }, "pas de caractères non latins")
        T.eq(0, r.already)
    }

    T.test("AniList : un nom déjà au glossaire n'est pas reproposé, une casse différente non plus") {
        val chars = AniList.parseCharacters(CHARS_JSON).characters
        val r = AniList.proposals(chars, setOf("jinwoo", "yeager"), aniLex())
        T.eq(2, r.already)
        T.ok(r.candidates.none { it.name.equals("Jinwoo", true) || it.name.equals("Yeager", true) }, "déjà connus")
        T.ok(r.candidates.any { it.name == "Jin-Woo" }, "les autres graphies restent proposées")
    }

    T.test("AniList : fragments de mots courants écartés (« levi » dans « television »), sans lexique seule la longueur compte") {
        val chars = listOf(AniListCharacter("MAIN", "Levi Ackerman", "Levi", "", "Ackerman", "", emptyList()), AniListCharacter("MAIN", "Armin", "Armin", "", "", "", listOf("Sunny")))
        val lex = EnglishLexicon(listOf("sunny"), listOf("television", "alarming"))
        val r = AniList.proposals(chars, emptySet(), lex)
        T.eq(listOf("Ackerman"), r.candidates.map { it.name }, "levi (television), armin (alarming), sunny (mot anglais)")
        T.ok(r.skipped.containsAll(listOf("Levi", "Armin", "Sunny")), "signalés")
        T.eq(listOf("Levi", "Ackerman", "Armin", "Sunny"), AniList.proposals(chars, emptySet(), null).candidates.map { it.name }, "sans lexique : seule la longueur est contrôlée")
    }

    T.test("AniList : au plus 80 noms proposés") {
        val many = ('a'..'z').flatMap { a -> ('a'..'z').map { b -> "Zok$a$b" } }.take(200).map { AniListCharacter("BACKGROUND", it, "", "", "", "", emptyList()) }
        T.eq(AniList.MAX_NAMES, AniList.proposals(many, emptySet(), null).candidates.size, "plafond")
        T.eq(3, AniList.proposals(many, emptySet(), null, 3).candidates.size, "plafond choisi")
    }
}
