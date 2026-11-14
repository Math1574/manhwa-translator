package app.manhwa.core

private val TRACE = """
----- pid 4242 at 2026-11-15 00:31:07 -----
Cmd line: app.manhwa

"main" prio=5 tid=1 Blocked
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x72c0a000 self=0xb400007e5a4a0010
  | sysTid=4242 nice=-10 cgrp=top-app sched=0/0 handle=0x7e5c8b04f8
  | state=S schedstat=( 4337441890 2154689329 7347 ) utm=337 stm=96 core=3 HZ=100
  at app.manhwa.GemmaTranslator.resetContext(Llm.kt:421)
  - waiting to lock <0x0a1b2c3d> (a app.manhwa.GemmaTranslator) held by thread 45
  at app.manhwa.TranslationController.resetSession(Controller.kt:1175)
  at app.manhwa.TranslationController.onPageStarted(Controller.kt:549)

"traduction-groupee" prio=5 tid=45 Native
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x12c0a000 self=0xb400007e5a4a1010
  at com.google.ai.edge.litertlm.Conversation.nativeSendMessage(Native method)
  at app.manhwa.GemmaTranslator.translateWithGlossary(Llm.kt:129)

"Binder:4242_1" prio=5 tid=7 Native
  at android.os.BinderProxy.transactNative(Native method)
"""

/** Trace d'un blocage : seuls les fils utiles au diagnostic sont gardés, sans le détail système. */
fun crashTraceTests() {
    T.test("trace de blocage : le fil principal et celui de la traduction, sans les lignes de détail du système") {
        val e = CrashTrace.excerpt(TRACE, listOf("main", "traduction-groupee"))
        T.ok(e.contains("\"main\" prio=5 tid=1 Blocked") && e.contains("resetContext(Llm.kt:421)"), "le fil principal et sa pile")
        T.ok(e.contains("waiting to lock <0x0a1b2c3d> (a app.manhwa.GemmaTranslator) held by thread 45"), "ce qu'il attendait")
        T.ok(e.contains("\"traduction-groupee\" prio=5 tid=45 Native") && e.contains("nativeSendMessage"), "le fil de la traduction")
        T.ok(!e.contains("sysTid") && !e.contains("schedstat") && !e.contains("Binder"), "ni détail système ni autres fils")
    }
    T.test("trace de blocage : limites de taille") {
        val long = "\"main\" prio=5 tid=1 Blocked\n" + (1..200).joinToString("\n") { "  at pkg.Cls.m$it(F.kt:$it)" } + "\n\n\"other\" prio=5 tid=2 X\n  at a.b(C.kt:1)\n"
        val e = CrashTrace.excerpt(long, listOf("main"), maxLines = 10)
        T.eq(10, e.lines().size, "dix lignes au plus par fil")
        T.ok(CrashTrace.excerpt(long, listOf("main"), maxChars = 100).length <= 100, "taille totale bornée")
        T.eq("", CrashTrace.excerpt(long, listOf("absent")), "aucun fil demandé : rien")
        T.eq("", CrashTrace.excerpt("", listOf("main")) + CrashTrace.excerpt("n'importe quoi\nsans fil", listOf("main")), "trace vide ou illisible : rien")
    }
}
