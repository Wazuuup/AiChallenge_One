package ru.sber.cb.aichallenge_one

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform