/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel

import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import java.util.function.Supplier

@SpringBootApplication
@ComponentScan(basePackages = ["com.fredboat"])
class Launcher : ApplicationContextAware {

    lateinit var springContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        ApplicationState.integrationCallback(applicationContext)
        springContext = applicationContext
    }

    @Bean
    fun springContextSupplier() = Supplier { this.springContext }
}

fun main(args: Array<String>) {
    System.setProperty("spring.config.name", "sentinel")
    System.setProperty("spring.config.title", "sentinel")
    val app = SpringApplication(Launcher::class.java)
    app.webApplicationType = WebApplicationType.SERVLET
    app.run(*args)
}

object ApplicationState {
    /** Used for integration testing */
    var integrationCallback: (ApplicationContext) -> Unit = {}
    var isTesting = false
}