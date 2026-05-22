package exposed.r2dbc.examples.production.spring.web

import exposed.r2dbc.examples.production.spring.app.CreateWorkItemRequest
import exposed.r2dbc.examples.production.spring.app.EnqueueOutboundRequest
import exposed.r2dbc.examples.production.spring.app.OutboxEventsView
import exposed.r2dbc.examples.production.spring.app.OutboxEventView
import exposed.r2dbc.examples.production.spring.app.OutboundRequestView
import exposed.r2dbc.examples.production.spring.app.PublishOutboxView
import exposed.r2dbc.examples.production.spring.app.ReadinessView
import exposed.r2dbc.examples.production.spring.app.RegisterAccountRequest
import exposed.r2dbc.examples.production.spring.app.SpringProductionWorkflowService
import exposed.r2dbc.examples.production.spring.app.WorkItemView
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.security.Principal

/**
 * WebFlux controller exposing the chapter 12 production slices.
 */
@RestController
@RequestMapping("/production")
class SpringProductionController(
    private val service: SpringProductionWorkflowService,
    private val realtimeHub: SpringRealtimeHub,
) {
    @PostMapping("/accounts")
    suspend fun registerAccount(@RequestBody request: RegisterAccountRequest) =
        service.registerAccount(request)

    @GetMapping("/profile")
    suspend fun profile(principal: Principal) =
        service.profile(principal.name)

    @GetMapping("/admin")
    suspend fun admin(principal: Principal) =
        service.adminProfile(principal.name)

    @PostMapping("/sessions")
    suspend fun createSession(principal: Principal) =
        service.createSession(principal.name)

    @GetMapping("/sessions")
    suspend fun sessions(principal: Principal) =
        service.sessions(principal.name)

    @PostMapping("/work-items")
    suspend fun createWorkItem(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestBody request: CreateWorkItemRequest,
    ): WorkItemView {
        service.requirePermission(apiKey, "work:create")
        return service.createWorkItem(request)
    }

    @GetMapping(path = ["/realtime"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun realtime(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestParam(defaultValue = "0") after: Long,
    ): Flux<ServerSentEvent<OutboxEventView>> {
        service.requirePermission(apiKey, "work:create")
        return realtimeEvents(after)
    }

    @GetMapping("/outbox")
    suspend fun outbox(@RequestHeader("X-Api-Key") apiKey: String): OutboxEventsView {
        service.requirePermission(apiKey, "work:create")
        return service.outboxEvents()
    }

    @PostMapping("/outbox/publish")
    suspend fun publishOutbox(@RequestHeader("X-Api-Key") apiKey: String): PublishOutboxView {
        service.requirePermission(apiKey, "work:create")
        return service.publishPendingOutbox()
    }

    @PostMapping("/outbound")
    suspend fun enqueueOutbound(
        @RequestHeader("X-Api-Key") apiKey: String,
        @RequestBody request: EnqueueOutboundRequest,
    ): OutboundRequestView {
        service.requirePermission(apiKey, "outbound:create")
        return service.enqueueOutbound(request)
    }

    @GetMapping("/readiness")
    suspend fun readiness(): ReadinessView =
        service.readiness()

    private suspend fun realtimeEvents(after: Long): Flux<ServerSentEvent<OutboxEventView>> {
        val replay = Flux.fromIterable(service.replayEvents(after))
        return Flux.concat(replay, realtimeHub.live())
            .filter { event -> event.sequence > after }
            .map { event ->
                ServerSentEvent.builder(event)
                    .id(event.sequence.toString())
                    .event(event.eventType)
                    .build()
            }
    }
}
