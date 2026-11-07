package app.manhwa.core

/** Décision pour une navigation de page entière demandée par la page affichée (clic, script, redirection). */
sealed class NavDecision {
    object Allow : NavDecision()
    /** [reason] : explication affichée à l'utilisateur ; [rule] : règle de liste responsable, s'il y en a une. */
    class Block(val reason: String, val rule: String? = null) : NavDecision()
}

/**
 * Politique de navigation : c'est elle qui arrête les pop-ups déguisés en redirection, les « click-under » et les superpositions invisibles qui emmènent sur un autre site
 * au premier appui. Une page lue n'a presque jamais de raison d'envoyer le lecteur ailleurs : tout changement de site que la page déclenche elle-même est donc refusé
 * (avec possibilité d'ouvrir quand même), sauf :
 *  - la suite d'une redirection du serveur (un site qui change de nom de domaine), tant que la destination n'est pas une régie publicitaire connue ;
 *  - un vrai lien visible sur lequel l'utilisateur vient d'appuyer ([trustedLink]) ;
 *  - un site que l'utilisateur a déjà autorisé ([allowedTargets]).
 */
object NavigationPolicy {
    private val internalSchemes = setOf("about", "blob", "data", "javascript", "file", "content")

    fun decide(
        target: String, pageUrl: String?, isMainFrame: Boolean, isRedirect: Boolean, trustedLink: Boolean,
        allowedTargets: Set<String>, blockerOn: Boolean, engine: FilterEngine?,
    ): NavDecision {
        val scheme = target.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") {
            // intent:, market:, tg:, whatsapp:… : un site n'a pas à lancer d'autres applications
            return if (scheme in internalSchemes) NavDecision.Allow else NavDecision.Block("lien vers une autre application")
        }
        if (!blockerOn || !isMainFrame) return NavDecision.Allow
        val targetHost = Sites.hostOf(target) ?: return NavDecision.Allow
        val pageHost = Sites.hostOf(pageUrl) ?: return NavDecision.Allow
        if (Sites.sameSite(targetHost, pageHost)) return NavDecision.Allow
        if (Sites.registrable(targetHost) in allowedTargets) return NavDecision.Allow
        val listed = engine?.isDocumentBlocked(target, pageUrl)
        if (listed != null && listed.blocked) return NavDecision.Block("régie publicitaire", listed.rule)
        if (isRedirect) return NavDecision.Allow
        if (trustedLink) return NavDecision.Allow
        return NavDecision.Block("changement de site sans votre demande")
    }
}
