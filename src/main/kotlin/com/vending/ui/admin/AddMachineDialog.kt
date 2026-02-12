package com.vending.ui.admin

import com.vending.dao.CompanyDAO
import com.vending.dao.ModemDAO
import com.vending.dao.VendingMachineDAO
import com.vending.model.VendingMachine
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.Modality
import javafx.stage.Stage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddMachineDialog(
    private val owner: Stage,
    private val editMachine: VendingMachine?,
    private val onSaved: () -> Unit
) {
    private val dialog = Dialog<VendingMachine>()
    private val isEdit = editMachine != null

    private val nameField = TextField()
    private val modelField = TextField()
    private val serialField = TextField()
    private val inventoryField = TextField()
    private val manufacturerField = TextField()
    private val typeBox = ComboBox(FXCollections.observableArrayList("cash", "card", "both"))
    private val countryBox = ComboBox(FXCollections.observableArrayList(
        "Россия", "Китай", "Германия", "Япония", "США", "Италия", "Турция", "Другая"
    ))
    private val manufactureDatePicker = DatePicker()
    private val commissioningDatePicker = DatePicker()
    private val lastVerificationPicker = DatePicker()
    private val verificationIntervalField = TextField()
    private val resourceHoursField = TextField()
    private val hoursUsedField = TextField()
    private val serviceDurationField = TextField()
    private val nextServicePicker = DatePicker()
    private val inventoryDatePicker = DatePicker()
    private val statusBox = ComboBox(FXCollections.observableArrayList("working", "broken", "maintenance", "offline"))
    private val companyBox = ComboBox<String>()
    private val modemBox = ComboBox<String>()
    private val addressField = TextField()
    private val latField = TextField()
    private val lonField = TextField()

    private var companies = listOf<com.vending.model.Company>()
    private var modems = listOf<com.vending.model.Modem>()

    fun show() {
        dialog.initOwner(owner)
        dialog.initModality(Modality.APPLICATION_MODAL)
        dialog.title = if (isEdit) "Редактирование ТА" else "Добавление ТА"
        dialog.headerText = if (isEdit) "Редактирование «${editMachine!!.name}»" else "Новый торговый автомат"

        loadReferenceData()
        buildForm()

        if (isEdit) fillFields(editMachine!!)

        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        dialog.dialogPane.lookupButton(ButtonType.OK).apply {
            (this as Button).text = "Сохранить"
        }

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK) {
                if (!validateForm()) return@setResultConverter null
                buildVendingMachine()
            } else null
        }

        dialog.showAndWait().ifPresent { vm ->
            Thread {
                try {
                    if (isEdit) {
                        VendingMachineDAO.update(vm)
                    } else {
                        VendingMachineDAO.create(vm)
                    }
                    Platform.runLater { onSaved() }
                } catch (e: Exception) {
                    Platform.runLater {
                        Alert(Alert.AlertType.ERROR, "Ошибка сохранения: ${e.message}").showAndWait()
                    }
                }
            }.start()
        }
    }

    private fun loadReferenceData() {
        try {
            companies = CompanyDAO.findAll()
            modems = ModemDAO.findAll()
        } catch (_: Exception) {}
        companyBox.items = FXCollections.observableArrayList(companies.map { "${it.id}: ${it.name}" })
        val modemItems = mutableListOf("— Нет —")
        modemItems.addAll(modems.map { "${it.id}: ${it.imei}" })
        modemBox.items = FXCollections.observableArrayList(modemItems)
        modemBox.selectionModel.selectFirst()
    }

    private fun buildForm() {
        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 8.0
            padding = Insets(16.0)
        }
        var row = 0

        fun addField(label: String, node: javafx.scene.Node) {
            grid.add(Label(label), 0, row)
            grid.add(node, 1, row)
            row++
        }

        nameField.promptText = "Название автомата"
        modelField.promptText = "Модель"
        serialField.promptText = "Серийный номер"
        inventoryField.promptText = "Инвентарный номер"
        manufacturerField.promptText = "Производитель"
        verificationIntervalField.promptText = "мес."
        resourceHoursField.promptText = "часов"
        hoursUsedField.promptText = "часов"
        serviceDurationField.promptText = "1-20 часов"
        addressField.promptText = "Адрес установки"
        latField.promptText = "Широта"
        lonField.promptText = "Долгота"

        typeBox.selectionModel.selectFirst()
        statusBox.selectionModel.selectFirst()

        addField("Название *", nameField)
        addField("Модель *", modelField)
        addField("Серийный номер *", serialField)
        addField("Инвент. номер *", inventoryField)
        addField("Производитель", manufacturerField)
        addField("Тип *", typeBox)
        addField("Страна", countryBox)
        addField("Дата изготовления *", manufactureDatePicker)
        addField("Дата ввода в экспл. *", commissioningDatePicker)
        addField("Дата посл. поверки", lastVerificationPicker)
        addField("Межповер. интервал (мес.)", verificationIntervalField)
        addField("Ресурс (часы) *", resourceHoursField)
        addField("Наработка (часы)", hoursUsedField)
        addField("Время ТО (часы)", serviceDurationField)
        addField("Дата след. ТО", nextServicePicker)
        addField("Дата инвентаризации", inventoryDatePicker)
        addField("Статус *", statusBox)
        addField("Компания *", companyBox)
        addField("Модем", modemBox)
        addField("Адрес", addressField)
        addField("Широта", latField)
        addField("Долгота", lonField)

        val scroll = ScrollPane(grid).apply {
            isFitToWidth = true
            prefViewportHeight = 500.0
            prefViewportWidth = 450.0
        }
        dialog.dialogPane.content = scroll
    }

    private fun fillFields(vm: VendingMachine) {
        nameField.text = vm.name
        modelField.text = vm.model
        serialField.text = vm.serialNumber
        inventoryField.text = vm.inventoryNumber
        manufacturerField.text = vm.manufacturer ?: ""
        typeBox.value = vm.type
        countryBox.value = vm.country
        manufactureDatePicker.value = vm.manufactureDate
        commissioningDatePicker.value = vm.commissioningDate
        lastVerificationPicker.value = vm.lastVerificationDate
        verificationIntervalField.text = vm.verificationInterval?.toString() ?: ""
        resourceHoursField.text = vm.resourceHours.toString()
        hoursUsedField.text = vm.hoursUsed.toString()
        serviceDurationField.text = vm.serviceDuration?.toString() ?: ""
        nextServicePicker.value = vm.nextServiceDate
        inventoryDatePicker.value = vm.inventoryDate
        statusBox.value = vm.status
        addressField.text = vm.locationAddress ?: ""
        latField.text = vm.latitude?.toPlainString() ?: ""
        lonField.text = vm.longitude?.toPlainString() ?: ""

        companies.find { it.id == vm.companyId }?.let {
            companyBox.value = "${it.id}: ${it.name}"
        }
        if (vm.modemId != null) {
            modems.find { it.id == vm.modemId }?.let {
                modemBox.value = "${it.id}: ${it.imei}"
            }
        }
    }

    private fun validateForm(): Boolean {
        val errors = mutableListOf<String>()
        if (nameField.text.isBlank()) errors.add("Введите название")
        if (modelField.text.isBlank()) errors.add("Введите модель")
        if (serialField.text.isBlank()) errors.add("Введите серийный номер")
        if (inventoryField.text.isBlank()) errors.add("Введите инвентарный номер")
        if (manufactureDatePicker.value == null) errors.add("Укажите дату изготовления")
        if (commissioningDatePicker.value == null) errors.add("Укажите дату ввода в эксплуатацию")
        if (resourceHoursField.text.isBlank()) errors.add("Укажите ресурс в часах")
        if (companyBox.value == null) errors.add("Выберите компанию")

        // Serial number uniqueness
        val excludeId = editMachine?.id
        if (serialField.text.isNotBlank()) {
            try {
                if (VendingMachineDAO.serialNumberExists(serialField.text, excludeId)) {
                    errors.add("ТА с таким серийным номером уже существует")
                }
            } catch (_: Exception) {}
        }
        if (inventoryField.text.isNotBlank()) {
            try {
                if (VendingMachineDAO.inventoryNumberExists(inventoryField.text, excludeId)) {
                    errors.add("ТА с таким инвентарным номером уже существует")
                }
            } catch (_: Exception) {}
        }

        // Date validations
        val mfDate = manufactureDatePicker.value
        val comDate = commissioningDatePicker.value
        if (mfDate != null && comDate != null) {
            if (comDate.isBefore(mfDate)) {
                errors.add("Дата ввода в эксплуатацию не может быть раньше даты изготовления")
            }
            if (comDate.isAfter(LocalDate.now())) {
                errors.add("Дата ввода в эксплуатацию не может быть позже текущей даты")
            }
        }
        val lvDate = lastVerificationPicker.value
        if (lvDate != null && mfDate != null) {
            if (lvDate.isBefore(mfDate)) errors.add("Дата поверки не может быть раньше даты изготовления")
            if (lvDate.isAfter(LocalDate.now())) errors.add("Дата поверки не может быть позже текущей даты")
        }
        val invDate = inventoryDatePicker.value
        if (invDate != null && mfDate != null) {
            if (invDate.isBefore(mfDate)) errors.add("Дата инвентаризации не может быть раньше даты изготовления")
            if (invDate.isAfter(LocalDate.now())) errors.add("Дата инвентаризации не может быть позже текущей даты")
        }

        // Resource hours
        val resH = resourceHoursField.text.toIntOrNull()
        if (resH != null && resH <= 0) errors.add("Ресурс ТА должен быть положительным числом")
        val servD = serviceDurationField.text.toIntOrNull()
        if (servD != null && (servD < 1 || servD > 20)) errors.add("Время обслуживания от 1 до 20 часов")

        if (errors.isNotEmpty()) {
            Alert(Alert.AlertType.WARNING).apply {
                title = "Ошибка валидации"
                headerText = "Проверьте данные"
                contentText = errors.joinToString("\n")
            }.showAndWait()
            return false
        }
        return true
    }

    private fun buildVendingMachine(): VendingMachine {
        val compId = companyBox.value?.split(":")?.firstOrNull()?.trim()?.toIntOrNull() ?: 1
        val modemVal = modemBox.value
        val modId = if (modemVal == null || modemVal.startsWith("—")) null
        else modemVal.split(":").firstOrNull()?.trim()?.toIntOrNull()

        return VendingMachine(
            id = editMachine?.id ?: 0,
            inventoryNumber = inventoryField.text.trim(),
            serialNumber = serialField.text.trim(),
            name = nameField.text.trim(),
            model = modelField.text.trim(),
            type = typeBox.value ?: "both",
            manufacturer = manufacturerField.text.trim().ifBlank { null },
            country = countryBox.value,
            manufactureDate = manufactureDatePicker.value!!,
            commissioningDate = commissioningDatePicker.value!!,
            lastVerificationDate = lastVerificationPicker.value,
            verificationInterval = verificationIntervalField.text.toIntOrNull(),
            lastServiceDate = editMachine?.lastServiceDate,
            nextServiceDate = nextServicePicker.value,
            inventoryDate = inventoryDatePicker.value,
            resourceHours = resourceHoursField.text.toIntOrNull() ?: 10000,
            hoursUsed = hoursUsedField.text.toIntOrNull() ?: 0,
            serviceDuration = serviceDurationField.text.toIntOrNull(),
            status = statusBox.value ?: "working",
            companyId = compId,
            modemId = modId,
            locationAddress = addressField.text.trim().ifBlank { null },
            latitude = latField.text.trim().toBigDecimalOrNull(),
            longitude = lonField.text.trim().toBigDecimalOrNull()
        )
    }
}
