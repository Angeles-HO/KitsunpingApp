package app.kitsunping.data.files

import app.kitsunping.RootFileReader

class ModuleFileGateway(private val moduleRoot: String) {
    private val cacheDir = "$moduleRoot/cache"
    private val routerLastPath = "$cacheDir/router.last"
    private val routerDniPath = "$cacheDir/router.dni"

    fun read(path: String): String {
        return RootFileReader.read(path)?.trim().orEmpty()
    }

    fun readRouterLastView(): String {
        val signature = readRouterSignature()
        if (signature.isBlank()) {
            return "router.last/router.dni is empty or does not exist"
        }

        val targetPath = resolveRouterInfoPath(signature)
        val targetContent = targetPath?.let(::read).orEmpty()

        return buildString {
            append("router.last:\n")
            append(signature)
            append("\n\n")
            if (targetPath == null) {
                append("Could not resolve router_*.info from BSSID")
            } else if (targetContent.isBlank()) {
                append("Related file not found or empty:\n")
                append(targetPath)
            } else {
                append("Related file:\n")
                append(targetPath)
                append("\n\n")
                append(targetContent)
            }
        }
    }

    fun readRouterLastTarget(): String {
        val signature = readRouterSignature()
        if (signature.isBlank()) {
            return "router.last/router.dni is empty or does not exist"
        }

        val targetPath = resolveRouterInfoPath(signature)
            ?: return "Could not resolve router_*.info from router.last\n\n$signature"

        if (!RootFileReader.exists(targetPath)) {
            return "Related file does not exist:\n$targetPath\n\nrouter.last:\n$signature"
        }

        return read(targetPath).ifBlank { "Related file is empty:\n$targetPath" }
    }

    fun listRouterFiles(): List<String> {
        val routerInfos = RootFileReader.list(cacheDir, "router_*.info")
        val files = mutableListOf<String>()
        if (RootFileReader.exists(routerLastPath)) {
            files += routerLastPath
        }
        if (RootFileReader.exists(routerDniPath)) {
            files += routerDniPath
        }
        return (files + routerInfos)
            .map { if (it.startsWith("/")) it else "$cacheDir/$it" }
            .distinct()
    }

    fun readRouterSignatureRaw(): String {
        return readRouterSignature()
    }

    private fun readRouterSignature(): String {
        val last = read(routerLastPath)
        if (last.isNotBlank()) {
            return last
        }
        return read(routerDniPath)
    }

    private fun resolveRouterInfoPath(signature: String): String? {
        val bssid = signature.substringBefore('|').trim()
        if (bssid.isBlank()) return null
        val bssidKey = bssid.replace(":", "")
        if (bssidKey.isBlank()) return null
        return "$cacheDir/router_${bssidKey}.info"
    }
}
