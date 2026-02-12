package com.vending.model

import javafx.beans.property.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// ========= Data classes =========

data class Role(val id: Int, val code: String, val name: String)

data class Company(
    val id: Int,
    val name: String,
    val address: String? = null,
    val contactPhone: String? = null,
    val email: String? = null,
    val createdAt: LocalDateTime? = null
)

data class User(
    val id: Int,
    val email: String,
    val phone: String? = null,
    val fullName: String,
    val passwordHash: String,
    val roleId: Int,
    val companyId: Int? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
    var roleName: String = "",
    var companyName: String = ""
)

data class Modem(
    val id: Int,
    val imei: String,
    val phoneNumber: String? = null,
    val status: String = "active"
)

data class VendingMachine(
    val id: Int,
    val inventoryNumber: String,
    val serialNumber: String,
    val name: String,
    val model: String,
    val type: String,
    val manufacturer: String? = null,
    val country: String? = null,
    val manufactureDate: LocalDate,
    val commissioningDate: LocalDate,
    val lastVerificationDate: LocalDate? = null,
    val verificationInterval: Int? = null,
    val lastServiceDate: LocalDate? = null,
    val nextServiceDate: LocalDate? = null,
    val inventoryDate: LocalDate? = null,
    val resourceHours: Int,
    val hoursUsed: Int = 0,
    val serviceDuration: Int? = null,
    val status: String = "working",
    val companyId: Int,
    val modemId: Int? = null,
    val lastInspectedBy: Int? = null,
    val lastInspectionDate: LocalDate? = null,
    val locationAddress: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val totalRevenue: BigDecimal = BigDecimal.ZERO,
    val currentCash: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime? = null,
    // Resolved names for display
    var companyName: String = "",
    var modemImei: String = "",
    var inspectorName: String = ""
) {
    val statusDisplay: String get() = when(status) {
        "working" -> "Работает"
        "broken" -> "Не работает"
        "maintenance" -> "На обслуживании"
        "offline" -> "Офлайн"
        else -> status
    }

    val typeDisplay: String get() = when(type) {
        "cash" -> "Наличные"
        "card" -> "Карта"
        "both" -> "Наличные + Карта"
        else -> type
    }

    val nextVerificationDate: LocalDate? get() {
        if (lastVerificationDate == null || verificationInterval == null) return null
        return lastVerificationDate.plusMonths(verificationInterval.toLong())
    }

    val resourceUsagePercent: Double get() {
        if (resourceHours <= 0) return 0.0
        return (hoursUsed.toDouble() / resourceHours.toDouble()) * 100.0
    }
}

data class Product(
    val id: Int,
    val name: String,
    val price: BigDecimal,
    val category: String? = null,
    val barcode: String? = null,
    val minStock: Int = 5,
    val createdAt: LocalDateTime? = null
)

data class MachineProduct(
    val machineId: Int,
    val productId: Int,
    val quantity: Int,
    val maxCapacity: Int,
    val minStock: Int = 5,
    val lastRestock: LocalDateTime? = null,
    var productName: String = "",
    var machineName: String = ""
)

data class Sale(
    val id: Int,
    val machineId: Int,
    val productId: Int,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val paymentMethod: String,
    val saleTime: LocalDateTime,
    var machineName: String = "",
    var productName: String = ""
)

data class ServiceOrder(
    val id: Int,
    val orderNumber: String,
    val machineId: Int,
    val type: String,
    val status: String = "new",
    val priority: String = "medium",
    val scheduledDate: LocalDate,
    val estimatedHours: Int? = null,
    val engineerId: Int? = null,
    val description: String? = null,
    val problems: String? = null,
    val actions: String? = null,
    val meterReading: Int? = null,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
    var machineName: String = "",
    var engineerName: String = ""
)

data class ServiceHistoryEntry(
    val id: Int,
    val machineId: Int,
    val orderId: Int? = null,
    val eventType: String,
    val eventDate: LocalDate,
    val description: String,
    val engineerId: Int? = null,
    val duration: BigDecimal? = null,
    val cost: BigDecimal? = null,
    var machineName: String = "",
    var engineerName: String = ""
)

data class Notification(
    val id: Int = 0,
    val type: String, // critical, warning, info
    val title: String,
    val message: String,
    val machineId: Int? = null,
    val isRead: Boolean = false,
    val createdAt: LocalDateTime? = null
)

data class NewsItem(
    val id: Int = 0,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime? = null
)

// ========= Dashboard aggregation models =========

data class DashboardStats(
    val totalMachines: Int = 0,
    val workingMachines: Int = 0,
    val brokenMachines: Int = 0,
    val maintenanceMachines: Int = 0,
    val offlineMachines: Int = 0,
    val totalRevenue: BigDecimal = BigDecimal.ZERO,
    val totalCash: BigDecimal = BigDecimal.ZERO,
    val totalSalesToday: BigDecimal = BigDecimal.ZERO,
    val totalSalesCount: Int = 0,
    val pendingServiceOrders: Int = 0,
    val completedServiceOrders: Int = 0
) {
    val efficiencyPercent: Double get() {
        if (totalMachines == 0) return 0.0
        return (workingMachines.toDouble() / totalMachines.toDouble()) * 100.0
    }
}

data class SalesByDay(
    val date: LocalDate,
    val totalAmount: BigDecimal,
    val totalCount: Int
)

// ========= Session =========

data class SessionInfo(
    var currentUser: User? = null,
    var token: String? = null,
    var role: Role? = null
)
