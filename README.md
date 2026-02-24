# VendingController

Десктопное приложение для управления сетью вендинговых автоматов (модуль франчайзора).  
Kotlin + JavaFX 21 + PostgreSQL + Exposed ORM.

## Требования

- **JDK 17+** (рекомендуется 21)  
- **PostgreSQL 17** (или совместимая версия)  
- Maven устанавливать **не нужно** — в проекте есть Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Быстрый старт

### 1. Клонирование

```bash
git clone https://github.com/SweetCuteFoxy/VendingController.git
cd VendingController
```

### 2. Настройка базы данных

Создайте базу `vending_db` в PostgreSQL и выполните скрипт создания таблиц:

```sql
CREATE DATABASE vending_db;
```

Затем импортируйте структуру из `/DB/SQLCreate.sql` и данные из `/DB/import_data.sql`.

Настройки подключения в `src/main/resources/database.properties`:

```properties
db.url=jdbc:postgresql://localhost:5432/vending_db
db.user=postgres
db.password=1111
```

### 3. Сборка

**Windows:**
```cmd
mvnw.cmd compile
```

**Linux / macOS:**
```bash
chmod +x mvnw
./mvnw compile
```

> Если у вас JDK не в `JAVA_HOME`, установите переменную перед сборкой:
> ```cmd
> set JAVA_HOME=C:\path\to\jdk-21
> mvnw.cmd compile
> ```

### 4. Запуск

```cmd
mvnw.cmd javafx:run
```

Или через IDE — запустите класс `com.vending.MainKt`.

## Зависимости

Все зависимости подтягиваются автоматически через Maven при первой сборке:

| Библиотека | Версия | Назначение |
|---|---|---|
| Kotlin | 2.1.0 | Язык |
| JavaFX (base, controls, graphics) | 21.0.2 | UI фреймворк |
| PostgreSQL JDBC | 42.7.1 | Драйвер БД |
| Exposed (core, jdbc, java-time) | 0.46.0 | ORM |
| jBCrypt | 0.4 | Хэширование паролей |
| java-jwt (Auth0) | 4.4.0 | JWT токены |
| iTextPDF | 5.5.13.3 | Генерация PDF |
| Logback + SLF4J | 1.4.14 / 2.0.9 | Логирование |

## Структура проекта

```
src/main/kotlin/com/vending/
├── Main.kt                  # Точка входа
├── dao/                     # Data Access Objects
├── database/                # Подключение к БД
├── model/                   # Модели данных
├── ui/                      # Основные экраны
│   ├── VendingApp.kt        # Application класс
│   ├── MainView.kt          # Навигация + sidebar
│   ├── DashboardView.kt     # Главная панель
│   └── admin/               # Администрирование
└── util/                    # Утилиты
```

## Лицензия

Проект для учебных целей.
