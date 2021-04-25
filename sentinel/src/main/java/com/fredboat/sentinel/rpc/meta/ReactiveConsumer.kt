package com.fredboat.sentinel.rpc.meta

import com.fredboat.sentinel.util.Rabbit
import org.reflections.Reflections
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Mono
import reactor.rabbitmq.AcknowledgableDelivery
import reactor.rabbitmq.OutboundMessage

class ReactiveConsumer<T : Annotation>(
    private val rabbit: Rabbit,
    spring: ApplicationContext,
    annotation: Class<T>
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ReactiveConsumer::class.java)
    }

    private val handlers: Map<Class<*>, (Any) -> Any>

    init {
        val reflections = Reflections("com.fredboat.sentinel.rpc")
        handlers = reflections.getTypesAnnotatedWith(annotation)
            .flatMap { it.declaredMethods.toList() }
            .filter { it.isAnnotationPresent(annotation) }
            .associate { method ->
                val clazz = method.declaringClass
                val bean = spring.getBean(clazz)

                if (method.parameters.size != 1) {
                    throw IllegalStateException("$method must have exactly one parameter")
                }

                method.parameters.first().type to { input: Any ->
                    method.invoke(bean, input)
                }
            }
        log.info("Found {} listening methods annotated with {}", handlers.size, annotation.simpleName)
    }

    fun handleIncoming(delivery: AcknowledgableDelivery) = try {
        handleIncoming0(delivery)
    } catch (t: Throwable) {
        handleFailure(delivery, t)
    }

    private fun handleIncoming0(delivery: AcknowledgableDelivery) {
        val clazz = rabbit.getType(delivery)
        val message = rabbit.fromJson(delivery, clazz)


        val handler = handlers[clazz]
        if (handler == null) {
            log.warn("Unhandled type {}!", clazz)
            delivery.nack(false)
            return
        }

        when(val reply: Any? = handler(message)) {
            is Unit, null -> {
                if (delivery.properties.replyTo != null) {
                    log.warn("Sender with {} message expected reply, but we have none!", clazz)
                    delivery.nack(false)
                } else {
                    // Empty response
                    delivery.ack()
                }
            }
            is Mono<*> -> reply.doOnError { handleFailure(delivery, it) }
                .subscribe{ sendReply(delivery, it); delivery.ack() }
            else -> {
                sendReply(delivery, reply)
                delivery.ack()
            }
        }
    }

    private fun handleFailure(incoming: AcknowledgableDelivery, throwable: Throwable) {
        log.error("Got exception while consuming message", throwable)
        incoming.nack(false)
    }

    private fun sendReply(incoming: AcknowledgableDelivery, reply: Any) {
        val (body, builder) = rabbit.toJson(reply)

        // Replies are always sent via the default exchange
        rabbit.send(OutboundMessage(
            "",
            incoming.properties.replyTo,
            builder.correlationId(incoming.properties.correlationId).build(),
            body
        ))
    }
}