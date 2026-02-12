package com.vending.dao

import com.vending.database.Tables
import com.vending.model.VendingMachine
import com.vending.model.DashboardStats
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

object VendingMachineDAO {

    fun findAll(): List<VendingMachine> = transaction {
        Tables.VendingMachines.selectAll().map { it.toVM() }
    }

    fun findById(id: Int): VendingMachine? = transaction {
        Tables.VendingMachines.select { Tables.VendingMachines.id eq id }
            .firstOrNull()?.toVM()
    }

    fun findByCompany(companyId: Int): List<VendingMachine> = transaction {
        Tables.VendingMachines.select { Tables.VendingMachines.companyId eq companyId }
            .map { it.toVM() }
    }

    fun findByStatus(status: String): List<VendingMachine> = transaction {
        Tables.VendingMachines.select { Tables.VendingMachines.status eq status }
            .map { it.toVM() }
    }

    fun findByNameFilter(filter: String): List<VendingMachine> = transaction {
        Tables.VendingMachines.select {
            Tables.VendingMachines.name.lowerCase() like "%${filter.lowercase()}%"
        }.map { it.toVM() }
    }

    fun serialNumberExists(sn: String, excludeId: Int? = null): Boolean = transaction {
        val query = Tables.VendingMachines.select { Tables.VendingMachines.serialNumber eq sn }
        if (excludeId != null) {
            query.andWhere { Tables.VendingMachines.id neq excludeId }
        }
        query.count() > 0
    }

    fun inventoryNumberExists(inv: String, excludeId: Int? = null): Boolean = transaction {
        val query = Tables.VendingMachines.select { Tables.VendingMachines.inventoryNumber eq inv }
        if (excludeId != null) {
            query.andWhere { Tables.VendingMachines.id neq excludeId }
        }
        query.count() > 0
    }

    fun create(vm: VendingMachine): Int = transaction {
        Tables.VendingMachines.insert {
            it[inventoryNumber] = vm.inventoryNumber
            it[serialNumber] = vm.serialNumber
            it[name] = vm.name
            it[model] = vm.model
            it[type] = vm.type
            it[manufacturer] = vm.manufacturer
            it[country] = vm.country
            it[manufactureDate] = vm.manufactureDate
            it[commissioningDate] = vm.commissioningDate
            it[lastVerificationDate] = vm.lastVerificationDate
            it[verificationInterval] = vm.verificationInterval
            it[lastServiceDate] = vm.lastServiceDate
            it[nextServiceDate] = vm.nextServiceDate
            it[inventoryDate] = vm.inventoryDate
            it[resourceHours] = vm.resourceHours
            it[hoursUsed] = vm.hoursUsed
            it[serviceDuration] = vm.serviceDuration
            it[status] = vm.status
            it[companyId] = vm.companyId
            it[modemId] = vm.modemId
            it[locationAddress] = vm.locationAddress
            it[latitude] = vm.latitude
            it[longitude] = vm.longitude
        } get Tables.VendingMachines.id
    }

    fun update(vm: VendingMachine) = transaction {
        Tables.VendingMachines.update({ Tables.VendingMachines.id eq vm.id }) {
            it[inventoryNumber] = vm.inventoryNumber
            it[serialNumber] = vm.serialNumber
            it[name] = vm.name
            it[model] = vm.model
            it[type] = vm.type
            it[manufacturer] = vm.manufacturer
            it[country] = vm.country
            it[manufactureDate] = vm.manufactureDate
            it[commissioningDate] = vm.commissioningDate
            it[lastVerificationDate] = vm.lastVerificationDate
            it[verificationInterval] = vm.verificationInterval
            it[lastServiceDate] = vm.lastServiceDate
            it[nextServiceDate] = vm.nextServiceDate
            it[inventoryDate] = vm.inventoryDate
            it[resourceHours] = vm.resourceHours
            it[hoursUsed] = vm.hoursUsed
            it[serviceDuration] = vm.serviceDuration
            it[status] = vm.status
            it[companyId] = vm.companyId
            it[modemId] = vm.modemId
            it[locationAddress] = vm.locationAddress
            it[latitude] = vm.latitude
            it[longitude] = vm.longitude
        }
    }

    fun delete(id: Int) = transaction {
        Tables.VendingMachines.deleteWhere { Op.build { Tables.VendingMachines.id eq id } }
    }

    fun unbindModem(machineId: Int) = transaction {
        Tables.VendingMachines.update({ Tables.VendingMachines.id eq machineId }) {
            it[modemId] = null
        }
    }

    fun updateStatus(machineId: Int, newStatus: String) = transaction {
        Tables.VendingMachines.update({ Tables.VendingMachines.id eq machineId }) {
            it[status] = newStatus
        }
    }

    fun getDashboardStats(): DashboardStats = transaction {
        val all = Tables.VendingMachines.selectAll().toList()
        val totalMachines = all.size
        val working = all.count { it[Tables.VendingMachines.status] == "working" }
        val broken = all.count { it[Tables.VendingMachines.status] == "broken" }
        val maintenance = all.count { it[Tables.VendingMachines.status] == "maintenance" }
        val offline = all.count { it[Tables.VendingMachines.status] == "offline" }
        val totalRevenue = all.sumOf { it[Tables.VendingMachines.totalRevenue] }
        val totalCash = all.sumOf { it[Tables.VendingMachines.currentCash] }

        val today = LocalDate.now()
        val todayStart = today.atStartOfDay()
        val todayEnd = today.plusDays(1).atStartOfDay()
        val salesToday = Tables.Sales.select {
            Tables.Sales.saleTime greaterEq todayStart and
                    (Tables.Sales.saleTime less todayEnd)
        }.toList()
        val totalSalesAmount = salesToday.sumOf {
            it[Tables.Sales.unitPrice] * it[Tables.Sales.quantity].toBigDecimal()
        }
        val totalSalesCount = salesToday.sumOf { it[Tables.Sales.quantity] }

        val pendingOrders = Tables.ServiceOrders.select {
            Tables.ServiceOrders.status inList listOf("new", "assigned", "progress")
        }.count().toInt()
        val completedOrders = Tables.ServiceOrders.select {
            Tables.ServiceOrders.status eq "completed"
        }.count().toInt()

        DashboardStats(
            totalMachines = totalMachines,
            workingMachines = working,
            brokenMachines = broken,
            maintenanceMachines = maintenance,
            offlineMachines = offline,
            totalRevenue = totalRevenue,
            totalCash = totalCash,
            totalSalesToday = totalSalesAmount,
            totalSalesCount = totalSalesCount,
            pendingServiceOrders = pendingOrders,
            completedServiceOrders = completedOrders
        )
    }

    fun totalCount(): Long = transaction {
        Tables.VendingMachines.selectAll().count()
    }

    fun findPaginated(offset: Int, limit: Int, nameFilter: String? = null): List<VendingMachine> = transaction {
        var query = Tables.VendingMachines.selectAll()
        if (!nameFilter.isNullOrBlank()) {
            query = Tables.VendingMachines.select {
                Tables.VendingMachines.name.lowerCase() like "%${nameFilter.lowercase()}%"
            }
        }
        query.limit(limit, offset.toLong()).map { it.toVM() }
    }

    fun countFiltered(nameFilter: String? = null): Long = transaction {
        if (!nameFilter.isNullOrBlank()) {
            Tables.VendingMachines.select {
                Tables.VendingMachines.name.lowerCase() like "%${nameFilter.lowercase()}%"
            }.count()
        } else {
            Tables.VendingMachines.selectAll().count()
        }
    }

    private fun ResultRow.toVM(): VendingMachine {
        val compId = this[Tables.VendingMachines.companyId]
        val modId = this[Tables.VendingMachines.modemId]
        val inspBy = this[Tables.VendingMachines.lastInspectedBy]

        val companyName = Tables.Companies.select { Tables.Companies.id eq compId }
            .firstOrNull()?.get(Tables.Companies.name) ?: ""
        val modemImei = if (modId != null) {
            Tables.Modems.select { Tables.Modems.id eq modId }
                .firstOrNull()?.get(Tables.Modems.imei) ?: ""
        } else ""
        val inspName = if (inspBy != null) {
            Tables.Users.select { Tables.Users.id eq inspBy }
                .firstOrNull()?.get(Tables.Users.fullName) ?: ""
        } else ""

        return VendingMachine(
            id = this[Tables.VendingMachines.id],
            inventoryNumber = this[Tables.VendingMachines.inventoryNumber],
            serialNumber = this[Tables.VendingMachines.serialNumber],
            name = this[Tables.VendingMachines.name],
            model = this[Tables.VendingMachines.model],
            type = this[Tables.VendingMachines.type],
            manufacturer = this[Tables.VendingMachines.manufacturer],
            country = this[Tables.VendingMachines.country],
            manufactureDate = this[Tables.VendingMachines.manufactureDate],
            commissioningDate = this[Tables.VendingMachines.commissioningDate],
            lastVerificationDate = this[Tables.VendingMachines.lastVerificationDate],
            verificationInterval = this[Tables.VendingMachines.verificationInterval],
            lastServiceDate = this[Tables.VendingMachines.lastServiceDate],
            nextServiceDate = this[Tables.VendingMachines.nextServiceDate],
            inventoryDate = this[Tables.VendingMachines.inventoryDate],
            resourceHours = this[Tables.VendingMachines.resourceHours],
            hoursUsed = this[Tables.VendingMachines.hoursUsed],
            serviceDuration = this[Tables.VendingMachines.serviceDuration],
            status = this[Tables.VendingMachines.status],
            companyId = compId,
            modemId = modId,
            lastInspectedBy = inspBy,
            lastInspectionDate = this[Tables.VendingMachines.lastInspectionDate],
            locationAddress = this[Tables.VendingMachines.locationAddress],
            latitude = this[Tables.VendingMachines.latitude],
            longitude = this[Tables.VendingMachines.longitude],
            totalRevenue = this[Tables.VendingMachines.totalRevenue],
            currentCash = this[Tables.VendingMachines.currentCash],
            createdAt = this[Tables.VendingMachines.createdAt],
            companyName = companyName,
            modemImei = modemImei,
            inspectorName = inspName
        )
    }
}
