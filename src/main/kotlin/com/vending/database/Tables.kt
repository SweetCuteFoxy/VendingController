package com.vending.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp

object Tables {

    object Roles : Table("roles") {
        val id = integer("id").autoIncrement()
        val code = varchar("code", 20).uniqueIndex()
        val name = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
    }

    object Companies : Table("companies") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255).uniqueIndex()
        val address = text("address").nullable()
        val contactPhone = varchar("contact_phone", 20).nullable()
        val email = varchar("email", 100).nullable()
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Users : Table("users") {
        val id = integer("id").autoIncrement()
        val email = varchar("email", 255).uniqueIndex()
        val phone = varchar("phone", 20).nullable()
        val fullName = varchar("full_name", 255)
        val passwordHash = varchar("password_hash", 255)
        val roleId = integer("role_id").references(Roles.id)
        val companyId = integer("company_id").references(Companies.id).nullable()
        val isActive = bool("is_active").default(true)
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Modems : Table("modems") {
        val id = integer("id").autoIncrement()
        val imei = varchar("imei", 15).uniqueIndex()
        val phoneNumber = varchar("phone_number", 20).nullable()
        val status = varchar("status", 10).default("active")
        override val primaryKey = PrimaryKey(id)
    }

    object VendingMachines : Table("vending_machines") {
        val id = integer("id").autoIncrement()
        val inventoryNumber = varchar("inventory_number", 50).uniqueIndex()
        val serialNumber = varchar("serial_number", 50).uniqueIndex()
        val name = varchar("name", 255)
        val model = varchar("model", 100)
        val type = varchar("type", 10)
        val manufacturer = varchar("manufacturer", 100).nullable()
        val country = varchar("country", 50).nullable()
        val manufactureDate = date("manufacture_date")
        val commissioningDate = date("commissioning_date")
        val lastVerificationDate = date("last_verification_date").nullable()
        val verificationInterval = integer("verification_interval").nullable()
        val lastServiceDate = date("last_service_date").nullable()
        val nextServiceDate = date("next_service_date").nullable()
        val inventoryDate = date("inventory_date").nullable()
        val resourceHours = integer("resource_hours")
        val hoursUsed = integer("hours_used").default(0)
        val serviceDuration = integer("service_duration").nullable()
        val status = varchar("status", 15).default("working")
        val companyId = integer("company_id").references(Companies.id)
        val modemId = integer("modem_id").references(Modems.id).nullable()
        val lastInspectedBy = integer("last_inspected_by").references(Users.id).nullable()
        val lastInspectionDate = date("last_inspection_date").nullable()
        val locationAddress = text("location_address").nullable()
        val latitude = decimal("latitude", 9, 6).nullable()
        val longitude = decimal("longitude", 9, 6).nullable()
        val totalRevenue = decimal("total_revenue", 12, 2).default(java.math.BigDecimal.ZERO)
        val currentCash = decimal("current_cash", 10, 2).default(java.math.BigDecimal.ZERO)
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Products : Table("products") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val price = decimal("price", 10, 2)
        val category = varchar("category", 50).nullable()
        val barcode = varchar("barcode", 20).uniqueIndex().nullable()
        val minStock = integer("min_stock").default(5)
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object MachineProducts : Table("machine_products") {
        val machineId = integer("machine_id").references(VendingMachines.id)
        val productId = integer("product_id").references(Products.id)
        val quantity = integer("quantity").default(0)
        val maxCapacity = integer("max_capacity")
        val minStock = integer("min_stock").default(5)
        val lastRestock = datetime("last_restock").nullable()
        override val primaryKey = PrimaryKey(machineId, productId)
    }

    object Sales : Table("sales") {
        val id = integer("id").autoIncrement()
        val machineId = integer("machine_id").references(VendingMachines.id)
        val productId = integer("product_id").references(Products.id)
        val quantity = integer("quantity")
        val unitPrice = decimal("unit_price", 10, 2)
        val totalAmount = decimal("total_amount", 10, 2).nullable()
        val paymentMethod = varchar("payment_method", 10)
        val saleTime = datetime("sale_time")
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object ServiceOrders : Table("service_orders") {
        val id = integer("id").autoIncrement()
        val orderNumber = varchar("order_number", 20).uniqueIndex()
        val machineId = integer("machine_id").references(VendingMachines.id)
        val type = varchar("type", 15)
        val status = varchar("status", 15).default("new")
        val priority = varchar("priority", 10).default("medium")
        val scheduledDate = date("scheduled_date")
        val estimatedHours = integer("estimated_hours").nullable()
        val engineerId = integer("engineer_id").references(Users.id).nullable()
        val description = text("description").nullable()
        val problems = text("problems").nullable()
        val actions = text("actions").nullable()
        val meterReading = integer("meter_reading").nullable()
        val createdBy = integer("created_by").references(Users.id).nullable()
        val createdAt = datetime("created_at").nullable()
        val completedAt = datetime("completed_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object ServiceHistory : Table("service_history") {
        val id = integer("id").autoIncrement()
        val machineId = integer("machine_id").references(VendingMachines.id)
        val orderId = integer("order_id").references(ServiceOrders.id).nullable()
        val eventType = varchar("event_type", 20)
        val eventDate = date("event_date")
        val description = text("description")
        val engineerId = integer("engineer_id").references(Users.id).nullable()
        val duration = decimal("duration", 5, 2).nullable()
        val cost = decimal("cost", 10, 2).nullable()
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object StatusHistory : Table("status_history") {
        val id = integer("id").autoIncrement()
        val entityType = varchar("entity_type", 20)
        val entityId = integer("entity_id")
        val oldStatus = varchar("old_status", 50).nullable()
        val newStatus = varchar("new_status", 50)
        val changedBy = integer("changed_by").references(Users.id).nullable()
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Notifications : Table("notifications") {
        val id = integer("id").autoIncrement()
        val type = varchar("type", 20) // critical, warning, info
        val title = varchar("title", 255)
        val message = text("message")
        val machineId = integer("machine_id").references(VendingMachines.id).nullable()
        val isRead = bool("is_read").default(false)
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object News : Table("news") {
        val id = integer("id").autoIncrement()
        val title = varchar("title", 255)
        val content = text("content")
        val createdAt = datetime("created_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }
}
