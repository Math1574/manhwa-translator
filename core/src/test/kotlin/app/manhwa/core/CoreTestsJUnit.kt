package app.manhwa.core

import org.junit.Test
import kotlin.test.assertTrue

class CoreTestsJUnit {
    @Test fun allCoreTestsPass() {
        assertTrue(runAll(), "Au moins un test du cœur a échoué (voir la sortie)")
    }
}
