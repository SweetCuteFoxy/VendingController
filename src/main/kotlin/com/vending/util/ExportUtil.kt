package com.vending.util

import com.itextpdf.text.*
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.vending.model.VendingMachine
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

object ExportUtil {
    private val logger = LoggerFactory.getLogger(ExportUtil::class.java)

    /** Generic CSV export — works for any data type */
    fun <T> exportGenericCSV(
        data: List<T>,
        headers: List<String>,
        rowExtractor: (T) -> List<String>,
        fileName: String,
        stage: Stage
    ) {
        val chooser = FileChooser().apply {
            title = "Сохранить CSV"
            extensionFilters.add(FileChooser.ExtensionFilter("CSV files", "*.csv"))
            initialFileName = fileName
        }
        val file = chooser.showSaveDialog(stage) ?: return
        try {
            java.io.FileWriter(file, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // BOM for Excel
                writer.write(headers.joinToString(";") + "\n")
                data.forEach { item ->
                    writer.write(rowExtractor(item).joinToString(";") { it.replace(";", ",") } + "\n")
                }
            }
            logger.info("Exported ${data.size} rows to $fileName")
            javafx.application.Platform.runLater {
                javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION).apply {
                    title = "Экспорт завершён"
                    headerText = null
                    contentText = "Файл сохранён: ${file.name} (${data.size} строк)"
                }.show()
            }
        } catch (e: Exception) {
            logger.error("CSV export failed", e)
        }
    }

    fun exportToCSV(machines: List<VendingMachine>, stage: Stage) {
        val chooser = FileChooser().apply {
            title = "Сохранить CSV"
            extensionFilters.add(FileChooser.ExtensionFilter("CSV files", "*.csv"))
            initialFileName = "vending_machines.csv"
        }
        val file = chooser.showSaveDialog(stage) ?: return
        try {
            FileWriter(file, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // BOM for Excel
                writer.write("ID;Название;Модель;Компания;Адрес;Статус;Серийный номер;Инвентарный номер;Доход\n")
                machines.forEach { vm ->
                    writer.write("${vm.id};${vm.name};${vm.model};${vm.companyName};${vm.locationAddress ?: ""};${vm.statusDisplay};${vm.serialNumber};${vm.inventoryNumber};${vm.totalRevenue}\n")
                }
            }
            logger.info("Exported ${machines.size} machines to CSV: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("CSV export failed", e)
        }
    }

    fun exportToPDF(machines: List<VendingMachine>, stage: Stage) {
        val chooser = FileChooser().apply {
            title = "Сохранить PDF"
            extensionFilters.add(FileChooser.ExtensionFilter("PDF files", "*.pdf"))
            initialFileName = "vending_machines.pdf"
        }
        val file = chooser.showSaveDialog(stage) ?: return
        try {
            val document = Document(PageSize.A4.rotate())
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // Try to use a font that supports Cyrillic
            val baseFont = try {
                BaseFont.createFont("c:/windows/fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
            } catch (e: Exception) {
                BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
            }
            val font = Font(baseFont, 9f)
            val headerFont = Font(baseFont, 10f, Font.BOLD)
            val titleFont = Font(baseFont, 16f, Font.BOLD)

            document.add(Paragraph("Торговые автоматы — отчёт", titleFont).apply {
                spacingAfter = 12f
            })

            val table = PdfPTable(7).apply {
                widthPercentage = 100f
                setWidths(floatArrayOf(1f, 3f, 2f, 3f, 4f, 2f, 2f))
            }

            val headers = listOf("ID", "Название", "Модель", "Компания", "Адрес", "Статус", "Доход")
            headers.forEach { h ->
                table.addCell(PdfPCell(Phrase(h, headerFont)).apply {
                    backgroundColor = BaseColor(54, 153, 255)
                    horizontalAlignment = Element.ALIGN_CENTER
                    paddingBottom = 5f
                    paddingTop = 5f
                })
            }

            machines.forEach { vm ->
                listOf(
                    vm.id.toString(), vm.name, vm.model, vm.companyName,
                    vm.locationAddress ?: "", vm.statusDisplay, vm.totalRevenue.toPlainString()
                ).forEach { cell ->
                    table.addCell(PdfPCell(Phrase(cell, font)).apply { paddingBottom = 3f; paddingTop = 3f })
                }
            }

            document.add(table)
            document.close()
            logger.info("Exported ${machines.size} machines to PDF: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("PDF export failed", e)
        }
    }

    fun exportToHTML(machines: List<VendingMachine>, stage: Stage) {
        val chooser = FileChooser().apply {
            title = "Сохранить HTML"
            extensionFilters.add(FileChooser.ExtensionFilter("HTML files", "*.html"))
            initialFileName = "vending_machines.html"
        }
        val file = chooser.showSaveDialog(stage) ?: return
        try {
            FileWriter(file, Charsets.UTF_8).use { writer ->
                writer.write("""
<!DOCTYPE html>
<html lang="ru"><head><meta charset="UTF-8">
<title>Торговые автоматы</title>
<style>
body{font-family:Arial,sans-serif;margin:20px;background:#f5f8fa;}
h1{color:#1e1e2d;}
table{border-collapse:collapse;width:100%;box-shadow:0 2px 8px rgba(0,0,0,0.1);}
th{background:#3699ff;color:white;padding:10px 8px;text-align:left;}
td{padding:8px;border-bottom:1px solid #eee;}
tr:nth-child(odd){background:#f9f9f9;}
tr:hover{background:#e8f4ff;}
.working{color:#50cd89;font-weight:bold;}
.broken{color:#f1416c;font-weight:bold;}
.maintenance{color:#ffc700;font-weight:bold;}
</style></head><body>
<h1>Торговые автоматы — отчёт</h1>
<table><thead><tr>
<th>ID</th><th>Название</th><th>Модель</th><th>Компания</th>
<th>Адрес</th><th>Статус</th><th>Доход</th></tr></thead><tbody>
""".trimIndent())
                machines.forEach { vm ->
                    val statusClass = when (vm.status) {
                        "working" -> "working"
                        "broken" -> "broken"
                        "maintenance" -> "maintenance"
                        else -> ""
                    }
                    writer.write("<tr><td>${vm.id}</td><td>${vm.name}</td><td>${vm.model}</td>")
                    writer.write("<td>${vm.companyName}</td><td>${vm.locationAddress ?: ""}</td>")
                    writer.write("<td class=\"$statusClass\">${vm.statusDisplay}</td>")
                    writer.write("<td>${vm.totalRevenue}</td></tr>\n")
                }
                writer.write("</tbody></table></body></html>")
            }
            logger.info("Exported ${machines.size} machines to HTML: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("HTML export failed", e)
        }
    }
}
