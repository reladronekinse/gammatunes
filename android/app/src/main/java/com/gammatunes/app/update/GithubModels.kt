package com.gammatunes.app.update

data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L,
)

data class GithubRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    val apkAsset: GithubAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}
