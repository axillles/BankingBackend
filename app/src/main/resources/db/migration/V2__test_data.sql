-- Демо-пользователи. Пароль у всех: password (в БД хранится BCrypt-хеш)
INSERT INTO accounts (id, user_username, user_password, user_role)
VALUES
    (1, 'Timofey', '$2a$10$7mpJ7M7l94ai97QGsuIgsOkUDD1C6kmRWTAhneXatzUoz0Gf063Hm', 'ADMIN'),
    (2, 'Ivan',    '$2a$10$hBSP09.UaAgCMWdm6w0hSODoTUNDbc5sojlzsNef1NJDThv23fxV6', 'USER'),
    (3, 'Fedor',   '$2a$10$iZ8vgDYi/19Jcq.UxRzthOOItb7Gl89oC7d7tUM4nTty1lDGZcF8K', 'ADMIN');

INSERT INTO limits (limit_sum, expense_category, limit_datetime, limit_currency_shortname, limit_remainder, account_id)
VALUES
    (1000.00, 'SERVICE', '2025-04-04 00:00:00', 'USD', 1000.00, 1),
    (2000.00, 'PRODUCT', '2025-04-04 00:00:00', 'USD', 2000.00, 1),
    (2000.00, 'SERVICE', '2025-04-04 00:00:00', 'USD', 2000.00, 2),
    (1000.00, 'PRODUCT', '2025-04-04 00:00:00', 'USD', 1000.00, 2);
