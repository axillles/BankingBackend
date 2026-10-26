# Banking System Application

## Условие задание находиться в папке "requirements"

## Описание

Это приложение представляет собой банковскую систему, позволяющее пользователям управлять счетами, транзакциями и лимитами. Оно использует Spring Boot и Maven для разработки и управления зависимостями.

## Структура проекта

```
t1sheryo-bankingsystemapplication/
├── README.md
├── app/
│   ├── HELP.md
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   └── .mvn/
└── .gitignore
```

## Установка

### Предварительные требования

1. Убедитесь, что у вас установлены следующие инструменты:
   - [Docker](https://www.docker.com/get-started)
   - [MySQL](https://www.mysql.com/)

### Настройка базы данных

1. Проверьте установку Docker и MySQL:
   ```bash
   docker --version
   mysql --version
   ```

2. Убедитесь, что порт 3306 свободен:
   ```bash
   netstat -aon | findstr :3306
   ```

3. Создайте и запустите контейнер MySQL:
   ```bash
   docker run -d --name banking-mysql \
   -p 3306:3306 \
   -e MYSQL_ROOT_PASSWORD=root_password \
   -e MYSQL_DATABASE=banking \
   -e MYSQL_USER=user \
   -e MYSQL_PASSWORD=user_password \
   -v mysql-data:/var/lib/mysql \
   mysql:8.0
   ```

4. Проверьте состояние контейнера:
   ```bash
   docker ps
   ```

5. Подключитесь к серверу базы данных из командной строки:
   ```bash
   mysql -u user -p banking
   ```

6. Команды в командной строке базы данных:
   ```sql
   SHOW TABLES;
   EXIT;
   ```

## Запуск приложения

1. Задайте переменные окружения (шаблон лежит в [`.env.example`](.env.example)):

   | Переменная | Обязательна | Описание |
   |---|---|---|
   | `TWELVEDATA_API_KEY` | да | ключ API [twelvedata.com](https://twelvedata.com) |
   | `DB_PASSWORD` | да | пароль MySQL (в примере выше `user_password`) |
   | `JWT_SECRET` | да | секрет подписи JWT, например `openssl rand -base64 48` |
   | `DB_URL` | нет | по умолчанию `jdbc:mysql://localhost:3306/banking` |
   | `DB_USERNAME` | нет | по умолчанию `user` |
   | `EXCHANGE_RATES_SCHEDULER_ENABLED` | нет | автообновление курсов раз в 6 часов, по умолчанию `true` |

   ```bash
   cp .env.example .env   # заполните значения
   set -a && source .env && set +a
   ```

2. Перейдите в директорию `app` и запустите приложение:
   ```bash
   ./mvnw spring-boot:run
   ```

Миграция `V2__test_data.sql` создает демо-пользователей `Timofey`, `Ivan`, `Fedor` с паролем `password`.

## Использование

Приложение предоставляет следующие функции:

- Получение и обновление курсов валют.
- Управление лимитами для аккаунтов.
- Проведение и отслеживание транзакций.

### Логика лимитов

- Лимит задается в USD отдельно для категорий `PRODUCT` и `SERVICE`. Если клиент его не установил, действует лимит 1000 USD.
- Существующие лимиты не изменяются: каждый новый лимит сохраняется отдельной записью с текущей датой.
- Транзакция помечается `limitExceeded = true`, если расходы за календарный месяц по ее категории (вместе с ней самой, в USD по курсу на день операции) больше действующего лимита.
- Каждая транзакция хранит снимок лимита, по которому она проверялась, поэтому новый лимит не влияет на флаги уже совершенных транзакций.

### Примеры API

Все эндпоинты, кроме `/auth/login`, требуют JWT в заголовке `Authorization: Bearer <token>`.

#### Получение токена

```http
POST /auth/login
Content-Type: application/json

{
  "username": "Ivan",
  "password": "password"
}
```

#### Получение курса валют

```http
GET /bank/exchange-rates?from=USD&to=EUR
```

#### Обновление курса валют

```http
POST /bank/exchange-rates/update?from=USD&to=EUR
```

#### Создание лимита

```http
POST /bank/limits
Content-Type: application/json

{
  "accountId": 1,
  "limit": 1000.00,
  "category": "SERVICE",
  "limitCurrency": "USD"
}
```

## Тестирование

Юнит-тесты (`*Test`), включая сценарии выставления `limit_exceeded` из условия задания:

```bash
./mvnw test
```

Интеграционные тесты (`*IT`) поднимают MySQL через Testcontainers, поэтому нужен запущенный Docker:

```bash
./mvnw -Pintegration-tests verify
```


