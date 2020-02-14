/*
 * Copyright © 2018 Frederik Mikkelsen <fred at frederikam.com>
 * FredBoat microservice for handling JDA and Lavalink over RabbitMQ.
 *
 * This program is licensed under GNU AGPLv3 under no warranty.
 */

package com.fredboat.sentinel.entities

/**
 * This is a copy of JDA's JDA.Status enum.
 * We don't want to pull all of JDA or even the JDA into our projects.
 */
enum class ShardStatus {
    /**JDA is currently setting up supporting systems like the AudioSystem. */
    INITIALIZING,
    /**JDA has finished setting up supporting systems and is ready to log in. */
    INITIALIZED,
    /**JDA is currently attempting to log in. */
    LOGGING_IN,
    /**JDA is currently attempting to connect it's websocket to Discord. */
    CONNECTING_TO_WEBSOCKET,
    /**JDA has successfully connected it's websocket to Discord and is sending authentication */
    IDENTIFYING_SESSION,
    /**JDA has sent authentication to discord and is awaiting confirmation */
    AWAITING_LOGIN_CONFIRMATION,
    /**JDA is populating internal objects.
     * This process often takes the longest of all Statuses (besides CONNECTED) */
    LOADING_SUBSYSTEMS,
    /**JDA has finished loading everything, is receiving information from Discord and is firing jda. */
    CONNECTED,
    /**JDA's main websocket has been disconnected. This **DOES NOT** mean JDA has shutdown permanently.
     * This is an in-between status. Most likely ATTEMPTING_TO_RECONNECT or SHUTTING_DOWN/SHUTDOWN will soon follow. */
    DISCONNECTED,
    /** JDA session has been added to SessionController
     * and is awaiting to be dequeued for reconnecting. */
    RECONNECT_QUEUED,
    /**When trying to reconnect to Discord JDA encountered an issue, most likely related to a lack of internet connection,
     * and is waiting to try reconnecting again. */
    WAITING_TO_RECONNECT,
    /**JDA has been disconnected from Discord and is currently trying to reestablish the connection. */
    ATTEMPTING_TO_RECONNECT,
    /**JDA has received a shutdown request or has been disconnected from Discord and reconnect is disabled, thus,
     * JDA is in the process of shutting down */
    SHUTTING_DOWN,
    /**JDA has finished shutting down and this instance can no longer be used to communicate with the Discord servers. */
    SHUTDOWN,
    /**While attempting to authenticate, Discord reported that the provided authentication information was invalid. */
    FAILED_TO_LOGIN;
}