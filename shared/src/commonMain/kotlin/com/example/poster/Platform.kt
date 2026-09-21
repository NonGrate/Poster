package com.example.poster

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform