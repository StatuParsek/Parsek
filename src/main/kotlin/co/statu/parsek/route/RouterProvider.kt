package co.statu.parsek.route

import co.statu.parsek.PluginEventManager
import co.statu.parsek.annotation.Endpoint
import co.statu.parsek.api.event.RouterEventListener
import co.statu.parsek.config.ConfigManager
import co.statu.parsek.model.Api
import co.statu.parsek.model.Route
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.json.schema.SchemaParser
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class RouterProvider private constructor(
    vertx: Vertx,
    applicationContext: AnnotationConfigApplicationContext,
    schemaParser: SchemaParser,
    configManager: ConfigManager
) {
    companion object {
        fun create(
            vertx: Vertx,
            applicationContext: AnnotationConfigApplicationContext,
            schemaParser: SchemaParser,
            configManager: ConfigManager
        ) =
            RouterProvider(vertx, applicationContext, schemaParser, configManager)

        private var isInitialized = false

        fun getIsInitialized() = isInitialized
    }

    private val router by lazy {
        Router.router(vertx)
    }

    private val allowedHeaders = setOf(
        "x-requested-with",
        "Access-Control-Allow-Origin",
        "origin",
        "Content-Type",
        "accept",
        "X-PINGARUNER",
        "x-csrf-token"
    )

    private val allowedMethods = setOf<HttpMethod>(
        HttpMethod.GET,
        HttpMethod.POST,
        HttpMethod.OPTIONS,
        HttpMethod.DELETE,
        HttpMethod.PATCH,
        HttpMethod.PUT
    )

    init {
        val routerConfig = configManager.getConfig().getJsonObject("router")

        val beans = applicationContext.getBeansWithAnnotation(Endpoint::class.java)

        val routeList = beans.map { it.value as Route }.toMutableList()

        val routerEventHandlers = PluginEventManager.getEventHandlers<RouterEventListener>()

        routerEventHandlers.forEach { eventHandler ->
            eventHandler.onInitRouteList(routeList)
        }

        router.route()
            .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
            .handler(
                CorsHandler.create(".*.")
                    .allowCredentials(true)
                    .allowedHeaders(allowedHeaders)
                    .allowedMethods(allowedMethods)
            )
            .handler(
                BodyHandler.create()
                    .setDeleteUploadedFilesOnEnd(true)
                    .setUploadsDirectory(configManager.getConfig().getString("file-uploads-folder") + "/temp")
            )

        routeList.forEach { route ->
            route.paths.forEach { path ->
                var url = path.url
                val httpMethod = path.routeType.vertxHttpMethod

                if (route is Api) {
                    val apiPrefix = routerConfig.getString("api-prefix")

                    if (!url.startsWith(apiPrefix)) {
                        url = apiPrefix + url
                    }
                }

                val routedRoute = if (httpMethod != null) {
                    router.route(httpMethod, url)
                } else {
                    router.route(url)
                }

                routedRoute
                    .order(route.order)

                val validationHandler = route.getValidationHandler(schemaParser)

                if (validationHandler != null) {
                    routedRoute
                        .handler(validationHandler)
                }

                routedRoute
                    .handler(route.getHandler())
                    .failureHandler(route.getFailureHandler())
            }
        }

        isInitialized = true
    }

    fun provide(): Router = router
}