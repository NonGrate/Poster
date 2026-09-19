package com.example.poster.preview

import com.example.poster.model.User
import com.example.poster.network.UserApi
import com.example.poster.db.Groups

class FakeUserApi: UserApi {
    private val users = mutableListOf<User>(
        User("AAA-AAA", "Ada", "Lovelace", "AAA-AAA@example.com", "dummy_hash", null),
        User("BBB-BBB", "Gardener", "Mary", "BBB-BBB@example.com", "dummy_hash", null),
        User("CCC-CCC", "Teacher", "David", "CCC-CCC@example.com", "dummy_hash", null)
    )

    private var currentLoggedInUser: User? = null


    override suspend fun getUserById(id: String): User? = users.find { it.guid == id }


    override suspend fun updateUser(user: User) {
        val index = users.indexOfFirst { it.guid == user.guid }
        if (index != -1) {
            users[index] = user
        }
    }


    override suspend fun logIn(user: String): User? {
        val foundUser = users.find { it.guid == user }
        currentLoggedInUser = foundUser
        return foundUser
    }


    override suspend fun currentUser(): User? = currentLoggedInUser

    override suspend fun authenticateUser(email: String, password: String): User? {
        val user = users.find { it.email == email }
        currentLoggedInUser = user
        return user
    }

    override suspend fun createUser(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String?,
        languages: List<String>,
        defaultLanguage: String?,
        showName: Boolean,
    ): User {
        val guid = "${name.uppercase()}-${surname.uppercase()}"
        val newUser = User(
            guid = guid,
            name = name,
            surname = surname,
            email = email,
            passwordHash = "dummy_hash_for_$guid",
            photo = null
        )

        users.add(newUser)
        return newUser
    }

    // The offline backend has no email and nothing to verify against: it exists
    // so the app can run without a server at all.
    override suspend fun verifyEmail(token: String): Boolean = false

    override suspend fun resendVerification() = Unit

    override suspend fun requestPasswordReset(email: String) = Unit

    override suspend fun resetPassword(token: String, newPassword: String): Boolean = false
    override suspend fun logout() {
        currentLoggedInUser = null
    }
}
