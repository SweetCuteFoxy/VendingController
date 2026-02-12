package com.vending.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.Properties

object DatabaseConfig {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)
    private var isConnected = false

    fun init() {
        if (isConnected) return
        try {
            val props = Properties()
            val stream = DatabaseConfig::class.java.classLoader.getResourceAsStream("database.properties")
            if (stream != null) {
                props.load(stream)
            } else {
                props.setProperty("db.url", "jdbc:postgresql://localhost:5432/vending_db")
                props.setProperty("db.user", "postgres")
                props.setProperty("db.password", "1111")
                props.setProperty("db.driver", "org.postgresql.Driver")
            }
            Database.connect(
                url = props.getProperty("db.url"),
                driver = props.getProperty("db.driver", "org.postgresql.Driver"),
                user = props.getProperty("db.user"),
                password = props.getProperty("db.password")
            )
            isConnected = true
            logger.info("Database connected successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to database", e)
            throw RuntimeException("Database connection failed: ${e.message}", e)
        }
    }

    fun createTablesIfNeeded() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Tables.Roles,
                Tables.Companies,
                Tables.Users,
                Tables.Modems,
                Tables.VendingMachines,
                Tables.Products,
                Tables.MachineProducts,
                Tables.Sales,
                Tables.ServiceOrders,
                Tables.ServiceHistory,
                Tables.StatusHistory,
                Tables.Notifications,
                Tables.News
            )
        }
    }
}
