package com.vending.dao

import com.vending.database.Tables
import com.vending.model.Product
import com.vending.model.MachineProduct
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object ProductDAO {
    fun findAll(): List<Product> = transaction {
        Tables.Products.selectAll().map { it.toProduct() }
    }

    fun findById(id: Int): Product? = transaction {
        Tables.Products.select { Tables.Products.id eq id }
            .firstOrNull()?.toProduct()
    }

    fun create(name: String, price: java.math.BigDecimal, category: String?, barcode: String?, minStock: Int): Int = transaction {
        Tables.Products.insert {
            it[Tables.Products.name] = name
            it[Tables.Products.price] = price
            it[Tables.Products.category] = category
            it[Tables.Products.barcode] = barcode
            it[Tables.Products.minStock] = minStock
            it[Tables.Products.createdAt] = LocalDateTime.now()
        } get Tables.Products.id
    }

    fun update(id: Int, name: String, price: java.math.BigDecimal, category: String?, barcode: String?, minStock: Int) = transaction {
        Tables.Products.update({ Tables.Products.id eq id }) {
            it[Tables.Products.name] = name
            it[Tables.Products.price] = price
            it[Tables.Products.category] = category
            it[Tables.Products.barcode] = barcode
            it[Tables.Products.minStock] = minStock
        }
    }

    fun delete(id: Int) = transaction {
        Tables.Products.deleteWhere { Op.build { Tables.Products.id eq id } }
    }

    private fun ResultRow.toProduct() = Product(
        id = this[Tables.Products.id],
        name = this[Tables.Products.name],
        price = this[Tables.Products.price],
        category = this[Tables.Products.category],
        barcode = this[Tables.Products.barcode],
        minStock = this[Tables.Products.minStock],
        createdAt = this[Tables.Products.createdAt]
    )
}

object MachineProductDAO {
    fun findAll(): List<MachineProduct> = transaction {
        Tables.MachineProducts.selectAll().map { it.toMP() }
    }

    fun findByMachine(machineId: Int): List<MachineProduct> = transaction {
        Tables.MachineProducts.select { Tables.MachineProducts.machineId eq machineId }
            .map { it.toMP() }
    }

    fun findLowStock(): List<MachineProduct> = transaction {
        Tables.MachineProducts.selectAll().mapNotNull { row ->
            val mp = row.toMP()
            if (mp.quantity <= mp.minStock) mp else null
        }
    }

    fun upsert(machineId: Int, productId: Int, quantity: Int, maxCapacity: Int, minStock: Int) = transaction {
        val exists = Tables.MachineProducts.select {
            (Tables.MachineProducts.machineId eq machineId) and
                    (Tables.MachineProducts.productId eq productId)
        }.count() > 0

        if (exists) {
            Tables.MachineProducts.update({
                (Tables.MachineProducts.machineId eq machineId) and
                        (Tables.MachineProducts.productId eq productId)
            }) {
                it[Tables.MachineProducts.quantity] = quantity
                it[Tables.MachineProducts.maxCapacity] = maxCapacity
                it[Tables.MachineProducts.minStock] = minStock
                it[Tables.MachineProducts.lastRestock] = LocalDateTime.now()
            }
        } else {
            Tables.MachineProducts.insert {
                it[Tables.MachineProducts.machineId] = machineId
                it[Tables.MachineProducts.productId] = productId
                it[Tables.MachineProducts.quantity] = quantity
                it[Tables.MachineProducts.maxCapacity] = maxCapacity
                it[Tables.MachineProducts.minStock] = minStock
                it[Tables.MachineProducts.lastRestock] = LocalDateTime.now()
            }
        }
    }

    fun delete(machineId: Int, productId: Int) = transaction {
        Tables.MachineProducts.deleteWhere {
            Op.build {
                (Tables.MachineProducts.machineId eq machineId) and
                        (Tables.MachineProducts.productId eq productId)
            }
        }
    }

    private fun ResultRow.toMP(): MachineProduct {
        val mId = this[Tables.MachineProducts.machineId]
        val pId = this[Tables.MachineProducts.productId]
        val mName = Tables.VendingMachines.select { Tables.VendingMachines.id eq mId }
            .firstOrNull()?.get(Tables.VendingMachines.name) ?: ""
        val pName = Tables.Products.select { Tables.Products.id eq pId }
            .firstOrNull()?.get(Tables.Products.name) ?: ""
        return MachineProduct(
            machineId = mId,
            productId = pId,
            quantity = this[Tables.MachineProducts.quantity],
            maxCapacity = this[Tables.MachineProducts.maxCapacity],
            minStock = this[Tables.MachineProducts.minStock],
            lastRestock = this[Tables.MachineProducts.lastRestock],
            productName = pName,
            machineName = mName
        )
    }
}
