# Разбор лабораторной работы №1

## Где находится решение

```text
FP/Lab1/lab1
```

Лабораторная работа №0 не изменялась. Для ЛР №1 сделана отдельная копия проекта,
внутри которой переписана архитектура.

Запуск из папки `FP/Lab1/lab1`:

```powershell
& "$env:TEMP\opencode\sbt\sbt\bin\sbt.bat" run
```

Если sbt добавлен в `PATH`, то обычно:

```text
sbt run
```

---

## Главная идея работы

В ЛР №0 программа работала, но внутри неё было много мест, где:

- ошибки обозначались исключениями (`require`, `throw`);
- отсутствие значения фактически обозначалось пустым списком или флагом;
- функции сами печатали текст в консоль;
- меню хранило не команду, а функцию `() => Unit`, которую потом выполняло.

В ЛР №1 нужно было отделить **данные** от **действий**, а ожидаемые ошибки
сделать частью результата функции.

Проще говоря:

```text
Было:  функция или сразу печатает результат, или падает с исключением.
Стало: функция возвращает данные, отсутствие значения или типизированную ошибку,
       а печатает и спрашивает пользователя отдельный UI-слой.
```

---

## Термины, которые используются в коде

### ADT — алгебраический тип данных

ADT — это тип, у которого есть несколько вариантов, и каждый вариант описан
отдельно.

В Scala для этого обычно используют:

- `sealed trait` — общий тип;
- `case object` — вариант без данных;
- `case class` — вариант с данными;
- `enum` — удобная запись набора вариантов.

Пример из `domain/Commands.scala`:

```scala
sealed trait MainCommand

object MainCommand {
  case object ShowPolicies extends MainCommand
  case object CreatePolicy extends MainCommand
  case object GenerateDefaultPassword extends MainCommand
  case object ReloadConfiguration extends MainCommand
  case object Exit extends MainCommand
}
```

Здесь `MainCommand` — это тип команды. Конкретная команда — один из вариантов.
Компилятор знает все варианты, поэтому в `match` можно проверить их все.

### `sealed`

`sealed` означает, что новые варианты нельзя добавить в другом файле. Это полезно:
компилятор может проверить, что `match` обработал все варианты.

### `case class`

`case class` — класс с данными, для которого автоматически есть:

- сравнение по содержимому;
- удобное создание;
- метод `copy` для создания изменённой копии.

Например, политика:

```scala
final case class PasswordPolicy(
  name: String,
  description: String,
  minimumLength: Int,
  ...
)
```

В ЛР №0 политика была классом с `var`-полями. В ЛР №1 политика неизменяемая:
при редактировании создаётся копия через `policy.copy(...)`.

### `case object`

`case object` — вариант ADT без полей. Например, `Exit` или `Back`.
Он существует в одном экземпляре, и это нормально: у команды «выход» нет
дополнительных данных.

### `Option`

`Option[A]` — контейнер, в котором значения может не быть.

```scala
Some(value) // значение есть
None        // значения нет
```

В коде `Option` используется там, где отсутствие значения — нормальная ситуация:

- конфигурация ещё не загружена: `PolicySession.current` возвращает `Option`;
- политика по умолчанию не найдена: `Option[PasswordPolicy]`;
- в списке политик для файла ещё нет загруженной политики:
  `Option[PasswordPolicy]`;
- группа символов ищется по имени: `SymbolsGroup.fromName` возвращает `Option`.

Важно: `None` — это не `null`, не `-1` и не пустая строка. Это отдельный
вариант типа, который нельзя случайно перепутать с обычным значением.

### `Either`

`Either[AppError, A]` — результат операции, которая может завершиться ошибкой.

```scala
Right(value) // успех
Left(error)  // ожидаемая ошибка
```

Например:

```scala
def generate(
  policy: PasswordPolicy,
  requestedLength: Int
): Either[AppError, String]
```

Это значит: функция либо вернёт пароль строкой (`Right`), либо вернёт
описание ошибки (`Left`).

Важно: `Left` — это не исключение. Это обычное значение, которое вызывающий
код обязан обработать.

### `map`

`map` преобразует успешное значение внутри контейнера.

```scala
PolicyStorage.loadAllPolicies().map { policies =>
  PolicySession.replace(policies)
  policies.length
}
```

Если загрузка успешна, мы получаем список политик и превращаем его в количество.
Если загрузка вернула `Left`, `map` ничего не делает с ошибкой.

### `flatMap`

`flatMap` нужен, когда следующая операция тоже может вернуть ошибку.

```scala
def create(policy: PasswordPolicy): Either[AppError, Path] = {
  validate(policy).flatMap(validPolicy =>
    PolicyStorage.createPolicy(validPolicy)
  )
}
```

Сначала проверяем политику. Если проверка вернула `Left`, сохранение уже не
выполняется. Если `Right`, переходим к сохранению.

### `for`-comprehension

`for` в Scala — это удобная запись цепочки `flatMap`/`map`.

Пример из `PolicyStorage.loadPolicy`:

```scala
for {
  name <- requiredProperty(properties, fileName, "name")
  description <- requiredProperty(properties, fileName, "description")
  minimumLength <- integerProperty(properties, fileName, "minimumLength")
  ...
  validPolicy <- PolicyValidator.validate(policy).left.map(AppError.InvalidPolicy.apply)
} yield validPolicy
```

Читается так:

1. прочитать имя;
2. если имя не прочиталось — дальше не идти;
3. прочитать описание;
4. ...
5. проверить политику;
6. вернуть готовую политику.

Если на любом шаге получился `Left`, весь `for` сразу возвращает этот `Left`.

### `left.map`

Обычный `map` работает с правой частью `Either` — с успехом.
`left.map` работает с левой частью — с ошибкой.

Пример:

```scala
PolicyValidator
  .validate(policy)
  .left
  .map(error => AppError.InvalidPolicy(error))
```

`PolicyValidator` возвращает `PolicyValidationError`, а наружу нужно вернуть
общий `AppError`. `left.map` переводит один тип ошибки в другой.

### Pattern matching

`match` — разбор значения по вариантам.

```scala
error match {
  case AppError.UnknownMenuChoice(input) => ...
  case AppError.NotAnInteger(input, min, max) => ...
  case AppError.InvalidPolicy(validationError) => ...
}
```

Для `sealed`-типов компилятор может предупредить, если какой-то вариант забыт.

### Чистая функция и побочный эффект

Чистая функция зависит только от аргументов и не меняет внешний мир.
Побочные эффекты — это ввод/вывод, запись файлов, изменение глобального состояния.

В ЛР №1 доменные функции стараются быть чистыми:

- `PolicyValidator.validate` только проверяет политику;
- `PasswordService.generate` только возвращает пароль или ошибку;
- `PasswordService.check` только возвращает результат проверки;
- `ConsoleUI` занимается вводом и выводом.

Файловые операции и журнал — это инфраструктура, они находятся в `data`.

---

## Структура проекта

```text
src/main/scala/
├── Main.scala
├── domain/
│   ├── Commands.scala
│   ├── Errors.scala
│   ├── PasswordPolicy.scala
│   ├── PolicyValidator.scala
│   └── PasswordService.scala
├── data/
│   ├── EventLogger.scala
│   ├── PolicyService.scala
│   ├── PolicySession.scala
│   ├── PolicyStorage.scala
│   └── ProjectPaths.scala
└── ui/
    ├── ConsoleUI.scala
    └── ErrorMessages.scala
```

### `domain/Commands.scala`

Типизированные команды:

- `MainCommand` — команды главного меню;
- `PolicyCommand` — действия с выбранной политикой;
- `PolicyListCommand` — выбор политики из списка;
- `PolicyEditCommand` — выбор свойства для редактирования.

Также здесь есть меню как данные:

```scala
final case class MenuItem[A](title: String, command: A)
final case class Menu[A](title: String, items: List[MenuItem[A]], ...)
```

`MenuItem` хранит **команду**, а не функцию, которая сразу что-то делает.

### `domain/Errors.scala`

Два ADT ошибок:

- `AppError` — ошибки приложения;
- `PolicyProblem` — конкретные причины, почему политика некорректна.

`PolicyValidationError` связывает имя политики и найденную проблему.

### `domain/PasswordPolicy.scala`

Модель политики и `enum SymbolsGroup` для групп символов.
Политика больше не меняет свои поля через `var`, а создаётся как неизменяемая
`case class`.

Здесь же есть вспомогательные методы:

- `allowedCharactersFor(group)` — какие символы группы разрешены с учётом
  запрещённых;
- `availableCharacters` — все доступные символы.

### `domain/PolicyValidator.scala`

Проверяет политику и возвращает:

```scala
Either[PolicyValidationError, PasswordPolicy]
```

Проверяются ограничения из задания:

- минимальная длина;
- максимальная длина;
- длина по умолчанию;
- наличие групп;
- дубликаты групп;
- отрицательные минимумы;
- сумма обязательных символов;
- конфликт разрешённых и запрещённых символов;
- пробел;
- пустая группа после запретов;
- минимум больше доступного количества;
- отсутствие доступных символов;
- недостаток символов при запрете повторов;
- максимум одинаковых символов подряд.

### `domain/PasswordService.scala`

Генерация и проверка пароля.

Возвращает `Either[AppError, ...]` и ничего не печатает.

### `data/PolicyStorage.scala`

Чтение и запись `.properties`-файлов политик.

Здесь находятся `try/catch` вокруг Java NIO и `Properties`, потому что это
внешние операции. Исключения преобразуются в `AppError.IoFailure` или
`AppError.InvalidConfiguration`.

### `data/PolicySession.scala`

Хранит текущую загруженную конфигурацию:

```scala
private var snapshot: Option[List[(Path, PasswordPolicy)]] = None
```

Если конфигурация ещё не загружена — `None`. Если загружена — `Some(список)`.

`defaultPolicy` возвращает `Option[PasswordPolicy]`, а не `null` и не пустую
строку.

### `data/PolicyService.scala`

Связывает проверку, сохранение, перезагрузку и сессию.

Например, перезагрузка:

```scala
PolicyStorage.loadAllPolicies().map { policies =>
  PolicySession.replace(policies)
  policies.length
}
```

Если загрузка не удалась, `PolicySession.replace` не вызывается.
Поэтому старая корректная конфигурация остаётся активной.

### `ui/ConsoleUI.scala`

Весь ввод и вывод:

- печать меню;
- чтение строк;
- чтение чисел и boolean;
- вывод результата;
- обработка команд.

### `ui/ErrorMessages.scala`

Превращает `AppError` в понятный текст.
Доменный код не знает, как именно ошибка будет показана пользователю.

---

## Что конкретно поменялось

### 1. Меню теперь возвращает команду, а не выполняет действие

Было в ЛР №0:

```scala
case class MenuItem(
  title: String,
  action: Option[() => Unit] = None,
  children: List[MenuItem] = Nil
)
```

Меню само вызывало `action`, то есть UI и действие были перемешаны.

Стало:

```scala
final case class MenuItem[A](title: String, command: A)
```

`readMenu` разбирает номер и возвращает, например:

```scala
Right(MainCommand.CreatePolicy)
```

Дальше `runMainLoop` делает `match` по команде. Строка ввода больше не
анализируется повторно.

### 2. Ошибки стали отдельными вариантами ADT

Было:

```scala
require(requestedLength >= MIN_LENGTH, "Длина не разрешена")
```

или:

```scala
throw new IllegalArgumentException("Ошибка в политике ...")
```

Стало:

```scala
if (requestedLength < policy.minimumLength || requestedLength > policy.maximumLength) {
  Left(AppError.LengthNotAllowed(
    policy.name,
    requestedLength,
    policy.minimumLength,
    policy.maximumLength
  ))
}
```

Теперь различаются, например:

- `UnknownMenuChoice` — неверный пункт меню;
- `NotAnInteger` — введено не число;
- `IntegerOutOfRange` — число вне диапазона;
- `NotABoolean` — введено не `true`/`false`;
- `InvalidPolicy` — политика не прошла проверку;
- `PolicyAlreadyExists` — политика уже есть;
- `PolicyNotFound` — политика не найдена;
- `DefaultPolicyNotLoaded` — нет политики по умолчанию;
- `LengthNotAllowed` — длина не разрешена политикой;
- `RequiredCharactersAboveLength` — обязательных символов больше длины;
- `NotEnoughCharactersForLength` — при запрете повторов не хватает символов;
- `ConfigurationDirectoryMissing` — нет папки конфигураций;
- `ConfigurationFileMissing` — нет файла;
- `NoPoliciesFound` — в папке нет политик;
- `InvalidConfiguration` — ошибка в файле;
- `IoFailure` — ошибка файловой операции;
- `Unexpected` — непредвиденная внутренняя ошибка.

Почему это лучше строки `"error"`:

- по варианту ошибки можно понять причину;
- UI может показать разный текст;
- журнал получает нормальное описание;
- компилятор проверяет обработку вариантов.

### 3. Ожидаемые ошибки возвращаются через `Either`, а не через исключения

Основные примеры:

```scala
PolicyValidator.validate(policy)          // Either[PolicyValidationError, PasswordPolicy]
PolicyStorage.loadPolicy(path)            // Either[AppError, PasswordPolicy]
PolicyStorage.savePolicy(policy, path)    // Either[AppError, Unit]
PolicyService.reload()                    // Either[AppError, Int]
PasswordService.generate(policy, length)  // Either[AppError, String]
PasswordService.check(policy, password)   // Either[AppError, PasswordCheckResult]
```

Исключения остались только там, где работают внешние библиотеки:

```scala
try {
  ...
} catch {
  case NonFatal(error) =>
    Left(AppError.IoFailure("чтение списка политик", messageOf(error)))
}
```

То есть исключение Java не летит в бизнес-логику, а превращается в `AppError`.

### 4. Отсутствие значения обозначается через `Option`

Было:

```scala
private var initialized = false
private var snapshot = List.empty[...]
```

Стало:

```scala
private var snapshot: Option[List[(Path, PasswordPolicy)]] = None
```

Было бы плохо обозначать отсутствие так:

```scala
null
""
-1
false
```

Вместо этого:

```scala
PolicySession.current       // Option[список политик]
PolicySession.defaultPolicy // Option[PasswordPolicy]
SymbolsGroup.fromName(...)  // Option[SymbolsGroup]
Option(properties.getProperty(key))
```

`Option` явно говорит: значения может не быть, и это нужно обработать.

### 5. Бизнес-логика больше не общается с консолью

В `domain` и `data` нет `println` и `readLine` в доменных функциях.

Например, проверка пароля раньше сама печатала:

```text
Пароль соответствует политике.
```

Теперь `PasswordService.check` возвращает:

```scala
PasswordCheckResult(
  policyName = "...",
  checks = List(
    PasswordCheck("Длина ...", true),
    PasswordCheck("Группа ...", false),
    ...
  )
)
```

А `ConsoleUI` уже решает, как это напечатать:

```text
[+] Длина от 4 до 16
[-] Группа UppercaseLatinLetters: найдено 0, требуется минимум 1
```

Это и есть разделение бизнес-логики и интерфейса.

### 6. Коллекции и контейнеры обрабатываются через `map`/`flatMap`

Примеры:

- `PolicyStorage.sequence` собирает список `Either` в `Either` со списком;
- `ConsoleUI.showPolicies` через `map` превращает список файлов в список
  `(файл, политика)`;
- `PasswordService.check` через `map` строит список проверок;
- `PasswordService.passwordMatches` через `fold` превращает `Either` в `Boolean`;
- `PolicyValidator` через `flatMap` по списку групп собирает найденные проблемы.

При этом обычные циклы остались там, где они понятнее:

- печать меню;
- ожидание корректного ввода;
- случайное заполнение пароля.

Использовать `map` ради самого `map` не нужно: если цикл читается проще,
цикл допустим.

### 7. Генерация пароля стала проще

В ЛР №0 был рекурсивный перебор с `search`, `require` и исключением в конце.

В ЛР №1 алгоритм проще:

1. Взять обязательные символы для каждой группы.
2. Добавить случайные символы до нужной длины.
3. Перемешать.
4. Проверить пароль по всем правилам.
5. Если не подошёл — попробовать снова.
6. После 1000 неудачных попыток вернуть `AppError.PasswordGenerationFailed`.

Это не «идеальный» алгоритм, но он понятный, соответствует политике при успехе
и не использует исключения для ожидаемой ошибки.

Дополнительно проверяется:

- `RequiredCharactersAboveLength` — обязательных символов больше, чем длина;
- `NotEnoughCharactersForLength` — при запрете повторов не хватает символов.

---

## Как выглядит путь команды

Пример: пользователь выбрал «Сгенерировать пароль по политике по умолчанию».

```text
Пользователь вводит 3
        ↓
ConsoleUI.readMenu
        ↓
MainCommand.GenerateDefaultPassword
        ↓
ConsoleUI.generateDefaultPassword
        ↓
PolicyService.defaultPolicy
        ↓
PasswordService.generate
        ↓
Right(пароль) или Left(AppError)
        ↓
ConsoleUI печатает пароль или текст ошибки
```

Ключевая мысль: после разбора меню дальше идёт **команда-значение**, а не строка
`"3"`.

Пример с ошибкой:

```text
Пользователь вводит abc в главном меню
        ↓
readMenu возвращает Left(AppError.UnknownMenuChoice("abc"))
        ↓
showError печатает:
  Неверный ввод: 'abc'.
  Введите номер пункта из меню.
        ↓
пишется запись WARN в журнал
```

---

## Что соответствует требованиям задания

| Требование | Где выполнено |
|---|---|
| 1. Команды представлены ADT | `domain/Commands.scala`: `MainCommand`, `PolicyCommand`, `PolicyListCommand`, `PolicyEditCommand` |
| 2. Ожидаемые ошибки — отдельный ADT | `domain/Errors.scala`: `AppError`, `PolicyProblem`, `PolicyValidationError` |
| 3. Ошибки не через исключения, а через `Either` | `PolicyValidator`, `PasswordService`, `PolicyStorage`, `PolicyService` |
| 4. Отсутствие значения через `Option` | `PolicySession`, `defaultPolicy`, `cachedPolicy`, `SymbolsGroup.fromName`, чтение properties |
| 5. Бизнес-логика не работает с UI | `domain` и `data` не читают ввод и не печатают результаты; всё общение с пользователем в `ui/ConsoleUI.scala` |
| 6. `map`/`flatMap` для контейнеров и коллекций | `for`-comprehension в `PolicyStorage`, `map` в `PolicyService`/`ConsoleUI`, `fold` в `PasswordService`, `flatMap` в `PolicyValidator` |
| 7. Функциональность ЛР №0 сохранена | список политик, информация, генерация, проверка, создание, редактирование, перезагрузка, журнал, политика по умолчанию |

---

## Что сохранено из ЛР №0

- формат конфигурации `.properties`;
- папка `PswConfigs`;
- политика по умолчанию `default_politic`;
- меню и подменю;
- просмотр списка политик;
- просмотр параметров политики;
- генерация по выбранной политике;
- генерация по политике по умолчанию;
- проверка пароля по каждому требованию;
- создание политики;
- редактирование политики;
- перезагрузка конфигурации;
- журнал в `Logs`;
- запрет записи паролей в журнал.

---

## Что стоит понимать на защите

### Почему `Either`, а не исключения?

Потому что ошибка — это ожидаемый результат операции. `Either` заставляет
вызывающий код явно обработать и успех, и ошибку. Исключения оставлены только
для внешних библиотек и файловых операций.

### Почему `Option`, а не `null`?

`Option` явно говорит, что значения может не быть. Его нельзя случайно
использовать как обычное значение, и компилятор помогает не забыть про `None`.

### Зачем `sealed trait`?

Чтобы все варианты ADT были известны в одном месте и `match` можно было
проверить на полноту.

### Почему политика теперь `case class`, а не класс с `var`?

Так политика не меняется незаметно из другого места. При редактировании
создаётся новая копия через `copy`, а старая остаётся неизменной.

### Где находится бизнес-логика?

В `domain`:

- проверка политики;
- генерация пароля;
- проверка пароля.

Она не знает про консоль. `ConsoleUI` только спрашивает данные и показывает
результат.

### Что делает `PolicySession`?

Хранит последнюю **успешно загруженную** конфигурацию. Если новая конфигурация
сломана, `replace` не вызывается, и продолжает использоваться старая.

### Почему в журнале нет паролей?

Потому что журнал пишет только имена политик, длины и результат проверки
`passed`/`failed`. Сам пароль в `EventLogger` не передаётся.

---

## Возможные улучшения, но не обязательные для ЛР

- убрать `var` из `PolicySession`, передавать состояние явно;
- добавить unit-тесты для `PolicyValidator` и `PasswordService`;
- вынести работу с `java.util.Properties` в отдельный адаптер;
- сделать более умную генерацию без случайных повторных попыток;
- добавить больше групп символов.

Эти пункты не нужны для выполнения задания, но показывают направления развития.
