package com.vending.dao

import com.vending.database.Tables
import com.vending.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

object CompanyDAO {
    fun findAll(): List<Company> = transaction {
        Tables.Companies.selectAll().map { it.toCompany() }
    }

    fun findById(id: Int): Company? = transaction {
        Tables.Companies.select { Tables.Companies.id eq id }
            .firstOrNull()?.toCompany()
    }

    fun create(name: String, address: String?, phone: String?, email: String?): Int = transaction {
        Tables.Companies.insert {
            it[Tables.Companies.name] = name
            it[Tables.Companies.address] = address
            it[Tables.Companies.contactPhone] = phone
            it[Tables.Companies.email] = email
        } get Tables.Companies.id
    }

    fun update(id: Int, name: String, address: String?, phone: String?, email: String?) = transaction {
        Tables.Companies.update({ Tables.Companies.id eq id }) {
            it[Tables.Companies.name] = name
            it[Tables.Companies.address] = address
            it[Tables.Companies.contactPhone] = phone
            it[Tables.Companies.email] = email
        }
    }

    fun delete(id: Int) = transaction {
        Tables.Companies.deleteWhere { Op.build { Tables.Companies.id eq id } }
    }

    private fun ResultRow.toCompany() = Company(
        id = this[Tables.Companies.id],
        name = this[Tables.Companies.name],
        address = this[Tables.Companies.address],
        contactPhone = this[Tables.Companies.contactPhone],
        email = this[Tables.Companies.email],
        createdAt = this[Tables.Companies.createdAt]
    )
}

object ModemDAO {
    fun findAll(): List<Modem> = transaction {
        Tables.Modems.selectAll().map { it.toModem() }
    }

    fun findById(id: Int): Modem? = transaction {
        Tables.Modems.select { Tables.Modems.id eq id }
            .firstOrNull()?.toModem()
    }

    fun findUnbound(): List<Modem> = transaction {
        val boundModems = Tables.VendingMachines
            .slice(Tables.VendingMachines.modemId)
            .selectAll()
            .mapNotNull { it[Tables.VendingMachines.modemId] }
        Tables.Modems.select { Tables.Modems.id notInList boundModems }
            .map { it.toModem() }
    }

    fun create(imei: String, phoneNumber: String?, status: String = "active"): Int = transaction {
        Tables.Modems.insert {
            it[Tables.Modems.imei] = imei
            it[Tables.Modems.phoneNumber] = phoneNumber
            it[Tables.Modems.status] = status
        } get Tables.Modems.id
    }

    fun update(id: Int, imei: String, phoneNumber: String?, status: String) = transaction {
        Tables.Modems.update({ Tables.Modems.id eq id }) {
            it[Tables.Modems.imei] = imei
            it[Tables.Modems.phoneNumber] = phoneNumber
            it[Tables.Modems.status] = status
        }
    }

    fun delete(id: Int) = transaction {
        Tables.Modems.deleteWhere { Op.build { Tables.Modems.id eq id } }
    }

    private fun ResultRow.toModem() = Modem(
        id = this[Tables.Modems.id],
        imei = this[Tables.Modems.imei],
        phoneNumber = this[Tables.Modems.phoneNumber],
        status = this[Tables.Modems.status]
    )
}

object SaleDAO {
    fun findAll(): List<Sale> = transaction {
        Tables.Sales.selectAll().orderBy(Tables.Sales.saleTime, SortOrder.DESC).map { it.toSale() }
    }

    fun create(machineId: Int, productId: Int, quantity: Int, unitPrice: BigDecimal, paymentMethod: String): Int = transaction {
        Tables.Sales.insert {
            it[Tables.Sales.machineId] = machineId
            it[Tables.Sales.productId] = productId
            it[Tables.Sales.quantity] = quantity
            it[Tables.Sales.unitPrice] = unitPrice
            it[Tables.Sales.totalAmount] = unitPrice * quantity.toBigDecimal()
            it[Tables.Sales.paymentMethod] = paymentMethod
            it[Tables.Sales.saleTime] = LocalDateTime.now()
        } get Tables.Sales.id
    }

    fun getSalesLast10Days(): List<SalesByDay> = transaction {
        val startDate = LocalDate.now().minusDays(10).atStartOfDay()
        val rows = Tables.Sales.select { Tables.Sales.saleTime greaterEq startDate }
            .orderBy(Tables.Sales.saleTime, SortOrder.ASC)
            .toList()

        rows.groupBy { it[Tables.Sales.saleTime].toLocalDate() }
            .map { (date, salesRows) ->
                SalesByDay(
                    date = date,
                    totalAmount = salesRows.sumOf {
                        it[Tables.Sales.unitPrice] * it[Tables.Sales.quantity].toBigDecimal()
                    },
                    totalCount = salesRows.sumOf { it[Tables.Sales.quantity] }
                )
            }
            .sortedBy { it.date }
    }

    private fun ResultRow.toSale(): Sale {
        val mId = this[Tables.Sales.machineId]
        val pId = this[Tables.Sales.productId]
        val mName = Tables.VendingMachines.select { Tables.VendingMachines.id eq mId }
            .firstOrNull()?.get(Tables.VendingMachines.name) ?: ""
        val pName = Tables.Products.select { Tables.Products.id eq pId }
            .firstOrNull()?.get(Tables.Products.name) ?: ""
        val qty = this[Tables.Sales.quantity]
        val price = this[Tables.Sales.unitPrice]
        return Sale(
            id = this[Tables.Sales.id],
            machineId = mId,
            productId = pId,
            quantity = qty,
            unitPrice = price,
            totalAmount = this[Tables.Sales.totalAmount] ?: (price * qty.toBigDecimal()),
            paymentMethod = this[Tables.Sales.paymentMethod],
            saleTime = this[Tables.Sales.saleTime],
            machineName = mName,
            productName = pName
        )
    }
}

object ServiceDAO {
    fun findAllOrders(): List<ServiceOrder> = transaction {
        Tables.ServiceOrders.selectAll()
            .orderBy(Tables.ServiceOrders.scheduledDate, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun findOrdersByStatus(status: String): List<ServiceOrder> = transaction {
        Tables.ServiceOrders.select { Tables.ServiceOrders.status eq status }
            .orderBy(Tables.ServiceOrders.scheduledDate, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun findHistory(): List<ServiceHistoryEntry> = transaction {
        Tables.ServiceHistory.selectAll()
            .orderBy(Tables.ServiceHistory.eventDate, SortOrder.DESC)
            .map { it.toHistory() }
    }

    fun createOrder(
        orderNumber: String, machineId: Int, type: String, priority: String,
        scheduledDate: LocalDate, engineerId: Int?, description: String?
    ): Int = transaction {
        Tables.ServiceOrders.insert {
            it[Tables.ServiceOrders.orderNumber] = orderNumber
            it[Tables.ServiceOrders.machineId] = machineId
            it[Tables.ServiceOrders.type] = type
            it[Tables.ServiceOrders.status] = "new"
            it[Tables.ServiceOrders.priority] = priority
            it[Tables.ServiceOrders.scheduledDate] = scheduledDate
            it[Tables.ServiceOrders.engineerId] = engineerId
            it[Tables.ServiceOrders.description] = description
            it[Tables.ServiceOrders.createdAt] = LocalDateTime.now()
        } get Tables.ServiceOrders.id
    }

    fun updateOrder(
        id: Int, type: String, status: String, priority: String,
        scheduledDate: LocalDate, engineerId: Int?, description: String?
    ) = transaction {
        Tables.ServiceOrders.update({ Tables.ServiceOrders.id eq id }) {
            it[Tables.ServiceOrders.type] = type
            it[Tables.ServiceOrders.status] = status
            it[Tables.ServiceOrders.priority] = priority
            it[Tables.ServiceOrders.scheduledDate] = scheduledDate
            it[Tables.ServiceOrders.engineerId] = engineerId
            it[Tables.ServiceOrders.description] = description
            if (status == "completed") {
                it[Tables.ServiceOrders.completedAt] = LocalDateTime.now()
            }
        }
    }

    fun deleteOrder(id: Int) = transaction {
        Tables.ServiceOrders.deleteWhere { Tables.ServiceOrders.id eq id }
    }

    private fun ResultRow.toOrder(): ServiceOrder {
        val mId = this[Tables.ServiceOrders.machineId]
        val eId = this[Tables.ServiceOrders.engineerId]
        val mName = Tables.VendingMachines.select { Tables.VendingMachines.id eq mId }
            .firstOrNull()?.get(Tables.VendingMachines.name) ?: ""
        val eName = if (eId != null) {
            Tables.Users.select { Tables.Users.id eq eId }
                .firstOrNull()?.get(Tables.Users.fullName) ?: ""
        } else ""
        return ServiceOrder(
            id = this[Tables.ServiceOrders.id],
            orderNumber = this[Tables.ServiceOrders.orderNumber],
            machineId = mId,
            type = this[Tables.ServiceOrders.type],
            status = this[Tables.ServiceOrders.status],
            priority = this[Tables.ServiceOrders.priority],
            scheduledDate = this[Tables.ServiceOrders.scheduledDate],
            estimatedHours = this[Tables.ServiceOrders.estimatedHours],
            engineerId = eId,
            description = this[Tables.ServiceOrders.description],
            problems = this[Tables.ServiceOrders.problems],
            actions = this[Tables.ServiceOrders.actions],
            meterReading = this[Tables.ServiceOrders.meterReading],
            createdBy = this[Tables.ServiceOrders.createdBy],
            createdAt = this[Tables.ServiceOrders.createdAt],
            completedAt = this[Tables.ServiceOrders.completedAt],
            machineName = mName,
            engineerName = eName
        )
    }

    private fun ResultRow.toHistory(): ServiceHistoryEntry {
        val mId = this[Tables.ServiceHistory.machineId]
        val eId = this[Tables.ServiceHistory.engineerId]
        val mName = Tables.VendingMachines.select { Tables.VendingMachines.id eq mId }
            .firstOrNull()?.get(Tables.VendingMachines.name) ?: ""
        val eName = if (eId != null) {
            Tables.Users.select { Tables.Users.id eq eId }
                .firstOrNull()?.get(Tables.Users.fullName) ?: ""
        } else ""
        return ServiceHistoryEntry(
            id = this[Tables.ServiceHistory.id],
            machineId = mId,
            orderId = this[Tables.ServiceHistory.orderId],
            eventType = this[Tables.ServiceHistory.eventType],
            eventDate = this[Tables.ServiceHistory.eventDate],
            description = this[Tables.ServiceHistory.description],
            engineerId = eId,
            duration = this[Tables.ServiceHistory.duration],
            cost = this[Tables.ServiceHistory.cost],
            machineName = mName,
            engineerName = eName
        )
    }
}

object NotificationDAO {
    fun findAll(): List<Notification> = transaction {
        Tables.Notifications.selectAll()
            .orderBy(Tables.Notifications.createdAt, SortOrder.DESC)
            .map { it.toNotification() }
    }

    fun findUnread(): List<Notification> = transaction {
        Tables.Notifications.select { Tables.Notifications.isRead eq false }
            .orderBy(Tables.Notifications.createdAt, SortOrder.DESC)
            .map { it.toNotification() }
    }

    fun create(type: String, title: String, message: String, machineId: Int? = null): Int = transaction {
        Tables.Notifications.insert {
            it[Tables.Notifications.type] = type
            it[Tables.Notifications.title] = title
            it[Tables.Notifications.message] = message
            it[Tables.Notifications.machineId] = machineId
            it[Tables.Notifications.createdAt] = java.time.LocalDateTime.now()
        } get Tables.Notifications.id
    }

    fun markRead(id: Int) = transaction {
        Tables.Notifications.update({ Tables.Notifications.id eq id }) {
            it[isRead] = true
        }
    }

    private fun ResultRow.toNotification() = Notification(
        id = this[Tables.Notifications.id],
        type = this[Tables.Notifications.type],
        title = this[Tables.Notifications.title],
        message = this[Tables.Notifications.message],
        machineId = this[Tables.Notifications.machineId],
        isRead = this[Tables.Notifications.isRead],
        createdAt = this[Tables.Notifications.createdAt]
    )
}

object NewsDAO {
    fun findAll(): List<NewsItem> = transaction {
        Tables.News.selectAll()
            .orderBy(Tables.News.createdAt, SortOrder.DESC)
            .map {
                NewsItem(
                    id = it[Tables.News.id],
                    title = it[Tables.News.title],
                    content = it[Tables.News.content],
                    createdAt = it[Tables.News.createdAt]
                )
            }
    }

    fun create(title: String, content: String): Int = transaction {
        Tables.News.insert {
            it[Tables.News.title] = title
            it[Tables.News.content] = content
            it[Tables.News.createdAt] = java.time.LocalDateTime.now()
        } get Tables.News.id
    }
}
