package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.example.poster.model.Group
import io.ktor.http.isSuccess
import com.example.poster.model.GroupMember
import com.example.poster.model.GroupInvite
import com.example.poster.network.GroupApi
import com.example.poster.network.JoinResult

class KtorGroupApi(private val httpClient: HttpClient) : GroupApi {
    // Group CRUD (aligned with server routes in Application.kt)
    override suspend fun getAllGroups(): List<Group> {
        return httpClient.get("groups") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    override suspend fun getGroupById(id: String): Group? {
        return try {
            httpClient.get("groups/byId/$id") {
                contentType(ContentType.Application.Json)
            }.body()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getGroupByInviteCode(inviteCode: String): Group? {
        return try {
            httpClient.get("groups/byInvite/$inviteCode") {
                contentType(ContentType.Application.Json)
            }.body()
        } catch (e: Exception) {
            null
        }
    }



    override suspend fun joinWithInvite(userId: String, code: String): JoinResult = try {
        val status = httpClient.post("groups/join") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("inviteCode" to code.trim().uppercase()))
        }.status
        when {
            status.isSuccess() -> JoinResult.JOINED
            status == HttpStatusCode.Forbidden -> JoinResult.WRONG_ADDRESS
            else -> JoinResult.INVALID
        }
    } catch (e: Exception) {
        JoinResult.INVALID
    }

    override suspend fun getMembers(groupId: String): List<GroupMember> = try {
        httpClient.get("groups/$groupId/members").body()
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun removeMember(groupId: String, memberId: String): Boolean = try {
        httpClient.delete("groups/$groupId/members/$memberId").status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun setMemberRole(groupId: String, memberId: String, role: String): Boolean = try {
        httpClient.post("groups/$groupId/members/$memberId/role") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("role" to role))
        }.status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun getInvites(groupId: String): List<GroupInvite> = try {
        httpClient.get("groups/$groupId/invites").body()
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun createInvite(groupId: String): String? = try {
        httpClient.post("groups/$groupId/invites")
            .body<Map<String, String>>()["code"]
    } catch (e: Exception) {
        null
    }

    override suspend fun revokeInvite(groupId: String, code: String): Boolean = try {
        httpClient.post("groups/$groupId/invites/$code/revoke").status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun inviteByEmail(groupId: String, email: String): Boolean = try {
        httpClient.post("groups/$groupId/invites/email") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email.trim()))
        }.status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun removeGroup(groupId: String): Boolean = try {
        httpClient.delete("groups/$groupId").status.isSuccess()
    } catch (e: Exception) {
        false
    }

    override suspend fun createGroup(name: String): Group? = try {
        httpClient.post("groups/create") {
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name))
        }.body()
    } catch (e: Exception) {
        // A refusal is an answer, not a crash: the name was rejected or this
        // account has created as many as it may. The screen says so.
        null
    }

    // User-Group membership
    override suspend fun addUserToGroup(userId: String, groupId: String) {
        // Use the server's join endpoint with groupId
        httpClient.post("groups/join") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId, "groupId" to groupId))
        }
    }

    override suspend fun removeUserFromGroup(userId: String, groupId: String) {
        // Server expects DELETE /groups/leave with body { userId, groupId }
        httpClient.delete("groups/leave") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("userId" to userId, "groupId" to groupId))
        }
    }

    override suspend fun getUserGroups(userId: String): List<Group> {
        return httpClient.get("groups/user/$userId") {
            contentType(ContentType.Application.Json)
        }.body()
    }


}

/** The whole of what creating a group takes. Everything else the server decides. */
@kotlinx.serialization.Serializable
private data class CreateGroupRequest(val name: String)
