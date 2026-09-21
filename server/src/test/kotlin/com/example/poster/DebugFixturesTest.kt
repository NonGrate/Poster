package com.example.poster

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugFixturesTest {
    @Test
    fun fixtureRouteIsNotRegisteredOutsideDevelopmentMode() = testApplication {
        application {
            routing {
                debugFixtureRoutes(enabled = false) {}
            }
        }

        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/debug/fixtures/integration").status,
        )
    }

    @Test
    fun fixtureRouteAppliesFixturesInDevelopmentMode() = testApplication {
        var applicationCount = 0
        application {
            routing {
                debugFixtureRoutes(enabled = true) { applicationCount++ }
            }
        }

        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/debug/fixtures/integration").status,
        )
        assertEquals(1, applicationCount)
    }

    /** The routes read a JSON body, which needs the same plugin the app installs. */
    private fun Application.jsonBodies() = install(ContentNegotiation) { json() }

    /**
     * Seeding needs a group and the public API will not make one — creating
     * a group belongs to the admin panel, because it carries the invite
     * code people join with.
     */
    @Test
    fun theGroupRouteCreatesOne() = testApplication {
        val created = mutableListOf<Triple<String, String, String>>()
        application {
            jsonBodies()
            routing {
                debugFixtureRoutes(
                    enabled = true,
                    createGroup = { id, name, invite -> created += Triple(id, name, invite) },
                ) {}
            }
        }

        val response = client.post("/debug/fixtures/group") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"c-1","name":"Our group","inviteCode":"HOME"}""")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(listOf(Triple("c-1", "Our group", "HOME")), created)
    }

    /** A group with no id or no name is not a group. */
    @Test
    fun theGroupRouteRefusesAnEmptyName() = testApplication {
        var calls = 0
        application {
            jsonBodies()
            routing {
                debugFixtureRoutes(enabled = true, createGroup = { _, _, _ -> calls++ }) {}
            }
        }

        val response = client.post("/debug/fixtures/group") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"c-1","name":"","inviteCode":"HOME"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(0, calls)
    }

    @Test
    fun theGroupRouteIsNotRegisteredOutsideDevelopmentMode() = testApplication {
        application {
            routing {
                debugFixtureRoutes(enabled = false) {}
            }
        }

        assertEquals(HttpStatusCode.NotFound, client.post("/debug/fixtures/group").status)
    }
}
