package app.manhwa.core

import java.io.File

/** Mode Ninja : le cache des pages et le journal du bloqueur ne gardent rien. */
fun ninjaTests() {
    fun tmpDir(): File = java.nio.file.Files.createTempDirectory("ninja").toFile()
    val key = CacheKey("abc", "ja", "fr", "eng1")
    val res = PageResult(100, 200, listOf(TranslatedBlock(0, RectF(1f, 2f, 3f, 4f), "texte", "traduction", false, 12f, listOf(RectF(1f, 2f, 3f, 3f)), listOf("ligne"), "texte", "IN_BUBBLE", null, null)))

    T.test("mode Ninja : le cache des pages ne touche pas au disque, ne voit pas le cache habituel, et s'oublie à la sortie") {
        val d = tmpDir(); val c = ResultCache(d)
        val other = CacheKey("zzz", "ja", "fr", "eng1")
        c.put(other, res)
        val before = d.listFiles()!!.map { it.name }.sorted()
        c.beginIsolation()
        T.eq(null, c.get(other))                       // le cache habituel n'est pas consulté
        c.put(key, res)
        T.eq(res, c.get(key))                          // mais la page reste disponible pendant la session
        T.eq(before, d.listFiles()!!.map { it.name }.sorted())     // rien d'écrit, rien de modifié sur le disque
        c.endIsolation()
        T.eq(null, c.get(key))
        T.eq(res, c.get(other))                        // le cache habituel est intact
        T.ok(!File(d, key.id + ".page").exists(), "page écrite sur le disque")
    }
    T.test("mode Ninja : le journal du bloqueur reste vide") {
        val log = BlockLog()
        log.silent = true
        log.add("image", "https://exemple.test/a.png", null, "https://exemple.test/")
        T.eq(0, log.total()); T.ok(log.recent().isEmpty(), "entrée notée")
        log.silent = false
        log.add("image", "https://exemple.test/a.png", null, null)
        T.eq(1, log.total())
    }
}
