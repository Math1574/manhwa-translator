pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ManhwaTranslator"
include(":core")
// Le module :app (Android, Compose, WebView, ML Kit) n'est PAS encore écrit : voir docs/STATUS.md
