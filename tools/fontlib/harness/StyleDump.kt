import app.manhwa.core.*
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Écrit un CSV d'échantillons dégradés : id, classe, puis le descripteur. Usage : -Dk=nb d'échantillons par police, -Dseed=graine, -Dout=fichier */
fun main() {
    val k = (System.getProperty("k") ?: "40").toInt(); val seed = (System.getProperty("seed") ?: "5").toInt(); val out = File(System.getProperty("out") ?: "/tmp/demo/samples.csv")
    val rnd = Random(seed)
    val sb = StringBuilder()
    val halo = (System.getProperty("halo") ?: "0").toInt()
    for (s in FontLibrary.specs) {
        var got = 0; var tries = 0
        while (got < k && tries < k * 3) {
            tries++
            val cap = 16f + rnd.nextFloat() * 56f
            val blurS = rnd.nextFloat() * 1.1f; val noise = rnd.nextFloat() * 9f; val q = 0.45f + rnd.nextFloat() * 0.5f
            val mixed = rnd.nextFloat() < 0.4f
            val off = (rnd.nextFloat() * 40 - 20).toInt(); val dil = rnd.nextFloat() < 0.2f
            val lines = (0 until 3).map { val t = SENTENCES[rnd.nextInt(SENTENCES.size)]; if (mixed) t.lowercase().replaceFirstChar { c -> c.uppercase() }.replace(" i ", " I ") else t }
            val vs = lines.mapNotNull { t ->
                var im = renderLine(s.id, t, cap); im = blur(im, blurS); im = noisy(im, noise, rnd); im = jpeg(im, q)
                toInk(im, t, off, dil)?.let { StyleFeatures.of(it) }
            }
            val v = StyleFeatures.mean(vs) ?: continue
            sb.append(s.id).append(',').append(s.cls).append(',').append((cap).toInt()).append(',').append(v.v.joinToString(",") { "%.5f".format(it) }).append('\n'); got++
        }
    }
    out.writeText(sb.toString()); println("échantillons écrits : ${sb.lines().size - 1} dans $out")
}
