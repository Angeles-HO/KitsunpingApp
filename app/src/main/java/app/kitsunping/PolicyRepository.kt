package app.kitsunping

import app.kitsunping.data.files.ModuleFileGateway
import app.kitsunping.data.root.RootCommandExecutor

class PolicyRepository(
    private val modelStore: TargetPolicyModelStore,
    private val moduleFileGateway: ModuleFileGateway,
    private val rootCommandExecutor: RootCommandExecutor
) {
    private val targetPropCacheFiles = listOf(
        "$MODULE_CACHE_DIR/target.prop.cache",
        "$MODULE_CACHE_DIR/target.prop.hash"
    )

    fun loadPolicies(): Map<String, TargetPolicyRule> {
        val saved = modelStore.loadModel()
        if (saved.isNotEmpty()) return saved

        val runtimeTargetProp = moduleFileGateway.read(MODULE_TARGET_PROP_PATH)
        val imported = modelStore.importFromTargetProp(runtimeTargetProp)
        if (imported.isNotEmpty()) {
            modelStore.saveModel(imported)
        }
        return imported
    }

    fun savePolicies(model: Map<String, TargetPolicyRule>) {
        modelStore.saveModel(model)
    }

    fun syncToRuntime(model: Map<String, TargetPolicyRule>) {
        val normalized = modelStore.loadModel().ifEmpty { model }
        if (normalized.isEmpty()) return

        val modelJson = modelStore.modelToJsonText(normalized)
        val targetProp = modelStore.renderTargetProp(normalized)

        val currentModelJson = moduleFileGateway.read(MODULE_TARGET_MODEL_JSON_PATH)
        val currentTargetProp = moduleFileGateway.read(MODULE_TARGET_PROP_PATH)
        if (currentModelJson.trim() == modelJson.trim() && currentTargetProp.trim() == targetProp.trim()) {
            return
        }

        val command = buildString {
            append("mkdir -p ").append(shQuote(MODULE_CACHE_DIR)).append(" && ")
            append("cat > ").append(shQuote(MODULE_TARGET_MODEL_JSON_PATH)).append(" <<'KPMODEL'\n")
            append(modelJson).append("\nKPMODEL\n")
            append("cat > ").append(shQuote(MODULE_TARGET_PROP_PATH)).append(" <<'KPTARGET'\n")
            append(targetProp)
            if (!targetProp.endsWith("\n")) {
                append('\n')
            }
            append("KPTARGET\n")
            append("rm -f ")
            append(targetPropCacheFiles.joinToString(" ") { shQuote(it) })
            append(" || true; ")
            append("chmod 600 ").append(shQuote(MODULE_TARGET_MODEL_JSON_PATH)).append(" || true; ")
            append("chmod 644 ").append(shQuote(MODULE_TARGET_PROP_PATH)).append(" || true")
        }

        rootCommandExecutor.runCapture(command)
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    companion object {
        private const val MODULE_ROOT = "/data/adb/modules/Kitsunping"
        private const val MODULE_CACHE_DIR = "$MODULE_ROOT/cache"
        private const val MODULE_TARGET_PROP_PATH = "$MODULE_ROOT/target.prop"
        private const val MODULE_TARGET_MODEL_JSON_PATH = "$MODULE_CACHE_DIR/target.model.json"
    }
}
