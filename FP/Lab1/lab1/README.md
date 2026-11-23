# Лабораторная работа №1

Исходное приложение из лабораторной работы №0 переработано с использованием ADT,
`Option` и `Either`.

Подробный разбор изменений, терминов и соответствия заданию:
[LAB1_RAZBOR.md](LAB1_RAZBOR.md).

## Запуск

Из папки `FP/Lab1/lab1`:

```text
sbt run
```

Политики находятся в `PswConfigs`, журнал пишется в `Logs`.

## Структура

- `domain/Commands.scala` — ADT команд меню и действий с политикой;
- `domain/Errors.scala` — ADT ожидаемых ошибок и проблем политики;
- `domain/PasswordPolicy.scala` — модель политики и групп символов;
- `domain/PolicyValidator.scala` — проверка политики, возвращает `Either`;
- `domain/PasswordService.scala` — генерация и проверка пароля, возвращает `Either`;
- `data/PolicyStorage.scala` — чтение и запись файлов политик;
- `data/PolicySession.scala` — текущая загруженная конфигурация (`Option`);
- `data/PolicyService.scala` — операции с политиками;
- `data/EventLogger.scala` — журнал;
- `ui/ConsoleUI.scala` — меню, ввод и вывод;
- `ui/ErrorMessages.scala` — преобразование ошибок в текст.

Бизнес-логика не читает ввод и не печатает в консоль. Ожидаемые ошибки
возвращаются как `Either[AppError, ...]`, а отсутствие значения представляется
через `Option`.
