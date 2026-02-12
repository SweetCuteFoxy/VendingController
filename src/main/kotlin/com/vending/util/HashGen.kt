package com.vending.util

import org.mindrot.jbcrypt.BCrypt

fun main() {
    val hash = BCrypt.hashpw("password123", BCrypt.gensalt(10))
    println("BCRYPT_HASH=")
}
