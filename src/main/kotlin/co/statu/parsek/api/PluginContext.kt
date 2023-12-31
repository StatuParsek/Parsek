package co.statu.parsek.api

import co.statu.parsek.Main
import co.statu.parsek.PluginEventManager
import co.statu.parsek.ReleaseStage
import io.vertx.core.Vertx

class PluginContext(
    val pluginId: String,
    val vertx: Vertx,
    val pluginEventManager: PluginEventManager,
    val environmentType: Main.Companion.EnvironmentType,
    val releaseStage: ReleaseStage
)