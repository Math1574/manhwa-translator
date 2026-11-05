package app.manhwa

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import app.manhwa.core.BrowsingLibrary

/**
 * Raccourci « Reprendre la lecture » : un appui long sur l'icône de l'application propose de rouvrir directement la dernière œuvre lue, là où l'on s'était arrêté.
 * Il suit la bibliothèque (dernier chapitre ouvert et avancement) ; sans lecture en cours, il n'y en a pas.
 */
object ResumeShortcut {
    const val EXTRA_URL = "mt_resume_url"
    const val EXTRA_PCT = "mt_resume_pct"
    private const val ID = "resume"
    private var lastPushed = ""

    fun update(ctx: Context, lib: BrowsingLibrary) {
        try {
            val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
            val last = lib.series().firstOrNull()
            if (last == null) { if (lastPushed != "-") { sm.removeAllDynamicShortcuts(); lastPushed = "-" }; return }
            val prog = lib.progressOf(last.lastUrl)
            val pct = if (prog != null && !prog.done && prog.percent in 4..93) prog.percent else 0
            val longLabel = ("Reprendre : " + last.title + (last.chapter?.let { " · $it" } ?: "") + (if (pct > 0) " ($pct %)" else "")).take(48)
            val key = last.lastUrl + "|" + pct + "|" + longLabel
            if (key == lastPushed) return
            val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_URL, last.lastUrl).putExtra(EXTRA_PCT, pct)
            val info = ShortcutInfo.Builder(ctx, ID).setShortLabel("Reprendre").setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher)).setIntent(intent).build()
            sm.setDynamicShortcuts(listOf(info))
            lastPushed = key
        } catch (_: Throwable) {}
    }
}
