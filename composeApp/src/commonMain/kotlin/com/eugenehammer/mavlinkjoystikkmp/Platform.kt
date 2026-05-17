package com.eugenehammer.mavlinkjoystikkmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform