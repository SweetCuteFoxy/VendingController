package com.vending.dao

import com.vending.database.Tables
import com.vending.model.User
import com.vending.model.Role
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

object UserDAO {

    fun findByEmail(email: String): User? = transaction {
        Tables.Users.select { Tables.Users.email eq email }
            .firstOrNull()?.toUser()
    }

    fun findById(id: Int): User? = transaction {
        Tables.Users.select { Tables.Users.id eq id }
            .firstOrNull()?.toUser()
    }

    fun findAll(): List<User> = transaction {
        Tables.Users.selectAll().map { it.toUser() }
    }

    fun findByRole(roleCode: String): List<User> = transaction {
        (Tables.Users innerJoin Tables.Roles)
            .select { Tables.Roles.code eq roleCode }
            .map { it.toUser() }
    }

    fun authenticate(email: String, password: String): User? = transaction {
        val user = Tables.Users.select {
            (Tables.Users.email eq email) and (Tables.Users.isActive eq true)
        }.firstOrNull()?.toUser() ?: return@transaction null

        if (BCrypt.checkpw(password, user.passwordHash)) user else null
    }

    fun create(
        email: String, phone: String?, fullName: String,
        password: String, roleId: Int, companyId: Int?
    ): Int = transaction {
        Tables.Users.insert {
            it[Tables.Users.email] = email
            it[Tables.Users.phone] = phone
            it[Tables.Users.fullName] = fullName
            it[Tables.Users.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[Tables.Users.roleId] = roleId
            it[Tables.Users.companyId] = companyId
        } get Tables.Users.id
    }

    fun update(id: Int, email: String, phone: String?, fullName: String, roleId: Int, companyId: Int?) = transaction {
        Tables.Users.update({ Tables.Users.id eq id }) {
            it[Tables.Users.email] = email
            it[Tables.Users.phone] = phone
            it[Tables.Users.fullName] = fullName
            it[Tables.Users.roleId] = roleId
            it[Tables.Users.companyId] = companyId
        }
    }

    fun delete(id: Int) = transaction {
        Tables.Users.deleteWhere { Op.build { Tables.Users.id eq id } }
    }

    fun toggleActive(id: Int, active: Boolean) = transaction {
        Tables.Users.update({ Tables.Users.id eq id }) {
            it[isActive] = active
        }
    }

    fun getRoleById(roleId: Int): Role? = transaction {
        Tables.Roles.select { Tables.Roles.id eq roleId }
            .firstOrNull()?.let {
                Role(it[Tables.Roles.id], it[Tables.Roles.code], it[Tables.Roles.name])
            }
    }

    fun getAllRoles(): List<Role> = transaction {
        Tables.Roles.selectAll().map {
            Role(it[Tables.Roles.id], it[Tables.Roles.code], it[Tables.Roles.name])
        }
    }

    private fun ResultRow.toUser(): User {
        val roleId = this[Tables.Users.roleId]
        val companyId = this[Tables.Users.companyId]
        val roleName = Tables.Roles.select { Tables.Roles.id eq roleId }
            .firstOrNull()?.get(Tables.Roles.name) ?: ""
        val companyName = if (companyId != null) {
            Tables.Companies.select { Tables.Companies.id eq companyId }
                .firstOrNull()?.get(Tables.Companies.name) ?: ""
        } else ""
        return User(
            id = this[Tables.Users.id],
            email = this[Tables.Users.email],
            phone = this[Tables.Users.phone],
            fullName = this[Tables.Users.fullName],
            passwordHash = this[Tables.Users.passwordHash],
            roleId = roleId,
            companyId = companyId,
            isActive = this[Tables.Users.isActive],
            createdAt = this[Tables.Users.createdAt],
            roleName = roleName,
            companyName = companyName
        )
    }
}
