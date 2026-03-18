package app.kitsunping.ui.screens.router

fun detectGatewayIp(daemonState: Map<String, String>, fallback: String): String {
    val candidates = listOf(
        daemonState["router.ip"],
        daemonState["router_ip"],
        daemonState["gateway"],
        daemonState["gw"],
        daemonState["wifi.gateway"],
        daemonState["wifi.gw"],
        daemonState["mobile.gateway"],
        daemonState["mobile.gw"],
        fallback
    )

    return candidates
        .map { it.orEmpty().trim() }
        .firstOrNull { it.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) }
        .orEmpty()
}
