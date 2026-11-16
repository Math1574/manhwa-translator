package app.manhwa.core

/** Garde du premier usage du GPU : quand couper l'accélération, et surtout quand ne pas la couper. */
fun gpuGuardTests() {
    T.test("garde du GPU : plantage natif pendant le premier usage de cette version : coupé") {
        T.ok(GpuGuard.tripped(GpuGuard.running(206), 206, nativeCrashConfirmed = true), "marque « running » du même build + plantage natif confirmé")
        T.ok(GpuGuard.tripped("running|206\n", 206, nativeCrashConfirmed = true), "espaces et fin de ligne ignorés")
    }
    T.test("garde du GPU : pas de coupure sans plantage natif confirmé") {
        T.ok(!GpuGuard.tripped(GpuGuard.running(206), 206, nativeCrashConfirmed = false), "arrêt par l'utilisateur, manque de mémoire, ancien Android : le GPU reste")
        T.ok(!GpuGuard.tripped(null, 206, nativeCrashConfirmed = true), "aucune marque : rien à couper")
        T.ok(!GpuGuard.tripped("", 206, nativeCrashConfirmed = true), "marque vide : rien à couper")
    }
    T.test("garde du GPU : une marque d'une autre version ne coupe rien") {
        T.ok(!GpuGuard.tripped(GpuGuard.running(205), 206, nativeCrashConfirmed = true), "marque d'un build précédent")
        T.ok(!GpuGuard.tripped(GpuGuard.running(2060), 206, nativeCrashConfirmed = true), "numéro qui commence pareil")
        T.ok(!GpuGuard.tripped(GpuGuard.proven(206), 206, nativeCrashConfirmed = true), "GPU déjà prouvé pour cette version : jamais coupé par la garde")
    }
    T.test("garde du GPU : lancement précédent interrompu pendant un appel GPU de cette version") {
        T.ok(GpuGuard.interrupted(GpuGuard.running(206), 206), "marque « running » du même build")
        T.ok(!GpuGuard.interrupted(GpuGuard.running(205), 206), "marque d'un autre build")
        T.ok(!GpuGuard.interrupted(GpuGuard.proven(206), 206), "preuve : pas une interruption")
        T.ok(!GpuGuard.interrupted(null, 206), "aucune marque")
    }
    T.test("garde du GPU : preuve par version") {
        T.ok(GpuGuard.isProven(GpuGuard.proven(206), 206), "prouvé pour ce build")
        T.ok(!GpuGuard.isProven(GpuGuard.proven(205), 206), "prouvé pour un autre build : la garde reprend")
        T.ok(!GpuGuard.isProven(GpuGuard.running(206), 206), "« running » ne prouve rien")
        T.ok(!GpuGuard.isProven(null, 206), "pas de marque : pas de preuve")
    }
}
