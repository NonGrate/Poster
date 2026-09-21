package com.example.poster

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun healthRouteReturnsOk() = testApplication {
        application {
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
