import java.nio.file.Path
import scala.io.StdIn
import scala.util.control.NonFatal

object ConsoleUI {
  private val mainMenu = Menu(
    "Главное меню",
    List(
      MenuItem("Показать политики", MainCommand.ShowPolicies),
      MenuItem("Создать новую политику", MainCommand.CreatePolicy),
      MenuItem("Сгенерировать пароль по политике по умолчанию", MainCommand.GenerateDefaultPassword),
      MenuItem("Перезагрузить конфигурацию", MainCommand.ReloadConfiguration)
    ),
    "Выход",
    MainCommand.Exit
  )

  private val policyMenu = Menu(
    "Действия с политикой",
    List(
      MenuItem("Использовать политику", PolicyCommand.GeneratePassword),
      MenuItem("Показать информацию", PolicyCommand.ShowInformation),
      MenuItem("Редактировать политику", PolicyCommand.EditPolicy),
      MenuItem("Проверить пароль по этой политике", PolicyCommand.CheckPassword)
    ),
    "Назад",
    PolicyCommand.Back
  )

  private val editMenu = Menu(
    "Какое свойство изменить?",
    List(
      MenuItem("Описание", PolicyEditCommand.EditDescription),
      MenuItem("Минимальная длина", PolicyEditCommand.EditMinimumLength),
      MenuItem("Максимальная длина", PolicyEditCommand.EditMaximumLength),
      MenuItem("Длина по умолчанию", PolicyEditCommand.EditDefaultLength),
      MenuItem("Разрешённые группы и их минимумы", PolicyEditCommand.EditGroups),
      MenuItem("Запрещённые символы", PolicyEditCommand.EditBlockedSymbols),
      MenuItem("AllowRepeat", PolicyEditCommand.EditAllowRepeat),
      MenuItem("Максимум одинаковых символов подряд", PolicyEditCommand.EditMaximumRepeat),
      MenuItem("AllowSpace", PolicyEditCommand.EditAllowSpace),
      MenuItem("Запрещённые подстроки", PolicyEditCommand.EditBlockedStrings)
    ),
    "Назад",
    PolicyEditCommand.Back
  )

  def run(): Unit = {
    EventLogger.log("INFO", "Приложение запущено")

    try {
      reloadConfiguration()
      runMainLoop()
    } catch {
      case NonFatal(error) =>
        showError(AppError.Unexpected(messageOf(error)))
    } finally {
      EventLogger.log("INFO", "Приложение завершено")
    }
  }

  private def runMainLoop(): Unit = {
    var running = true

    while (running) {
      readMenu(mainMenu) match {
        case Left(error) =>
          showError(error)

        case Right(MainCommand.ShowPolicies) =>
          showPolicies()

        case Right(MainCommand.CreatePolicy) =>
          createPolicy()

        case Right(MainCommand.GenerateDefaultPassword) =>
          generateDefaultPassword()

        case Right(MainCommand.ReloadConfiguration) =>
          reloadConfiguration()

        case Right(MainCommand.Exit) =>
          running = false
      }
    }
  }

  private def reloadConfiguration(): Unit = {
    PolicyService.reload() match {
      case Right(count) =>
        EventLogger.log("INFO", s"Конфигурация успешно перезагружена: policies=$count")
        println(s"Конфигурация перезагружена. Загружено политик: $count.")

      case Left(error) =>
        showError(error)
    }
  }

  private def showPolicies(): Unit = {
    val policiesResult: Either[AppError, List[(Path, Option[PasswordPolicy])]] =
      PolicySession.current match {
        case Some(policies) =>
          Right(policies.map { case (path, policy) => (path, Some(policy)) })

        case None =>
          PolicyStorage.listPolicyFiles().map { paths =>
            paths.map(path => (path, Option.empty[PasswordPolicy]))
          }
      }

    policiesResult match {
      case Left(error) =>
        showError(error)

      case Right(policies) if policies.isEmpty =>
        println("Сохранённых политик нет. Сначала создайте политику.")

      case Right(policies) =>
        browsePolicies(policies)
    }
  }

  private def browsePolicies(policies: List[(Path, Option[PasswordPolicy])]): Unit = {
    var browsing = true

    while (browsing) {
      val items: List[MenuItem[PolicyListCommand]] =
        policies.zipWithIndex.map { case ((path, cachedPolicy), index) =>
          MenuItem(policyName(path, cachedPolicy), PolicyListCommand.OpenPolicy(index))
        }

      val menu = Menu(
        "Сохранённые политики",
        items,
        "Назад",
        PolicyListCommand.Back
      )

      readMenu(menu) match {
        case Left(error) =>
          showError(error)

        case Right(PolicyListCommand.Back) =>
          browsing = false

        case Right(PolicyListCommand.OpenPolicy(index)) =>
          val (path, cachedPolicy) = policies(index)
          openPolicy(path, cachedPolicy)
      }
    }
  }

  private def openPolicy(path: Path, cachedPolicy: Option[PasswordPolicy]): Unit = {
    val policyResult: Either[AppError, PasswordPolicy] = cachedPolicy match {
      case Some(policy) => Right(policy)
      case None => PolicyStorage.loadPolicy(path)
    }

    policyResult match {
      case Left(error) => showError(error)
      case Right(policy) => showPolicyMenu(path, policy)
    }
  }

  private def showPolicyMenu(path: Path, initialPolicy: PasswordPolicy): Unit = {
    var policy = initialPolicy
    var selected = true

    while (selected) {
      readMenu(policyMenu) match {
        case Left(error) =>
          showError(error)

        case Right(PolicyCommand.GeneratePassword) =>
          generatePassword(policy)

        case Right(PolicyCommand.ShowInformation) =>
          showPolicyInfo(policy)

        case Right(PolicyCommand.EditPolicy) =>
          editPolicy(policy, path) match {
            case Left(error) =>
              showError(error)

            case Right(Some(updatedPolicy)) =>
              policy = updatedPolicy
              println(s"Политика '${policy.name}' обновлена и сохранена.")

            case Right(None) =>
              ()
          }

        case Right(PolicyCommand.CheckPassword) =>
          checkPassword(policy)

        case Right(PolicyCommand.Back) =>
          selected = false
      }
    }
  }

  private def generateDefaultPassword(): Unit = {
    PolicyService.defaultPolicy match {
      case Right(policy) =>
        generatePassword(policy)

      case Left(error) =>
        showError(error)
    }
  }

  private def generatePassword(policy: PasswordPolicy): Unit = {
    EventLogger.log("INFO", s"Запрошена генерация: policy=${policy.name}")
    val length = askPasswordLength(policy)

    PasswordService.generate(policy, length) match {
      case Right(password) =>
        EventLogger.log(
          "INFO",
          s"Выполнена генерация: policy=${policy.name}, length=${password.length}"
        )
        println(s"Сгенерированный пароль: $password")

      case Left(error) =>
        showError(error)
    }
  }

  private def checkPassword(policy: PasswordPolicy): Unit = {
    EventLogger.log("INFO", s"Запрошена проверка пароля: policy=${policy.name}")
    val password = readValue("Введите пароль для проверки: ")

    PasswordService.check(policy, password) match {
      case Right(result) =>
        showCheckResult(result)
        val status = if (result.passed) "passed" else "failed"
        EventLogger.log(
          "INFO",
          s"Выполнена проверка: policy=${result.policyName}, result=$status"
        )

      case Left(error) =>
        showError(error)
    }
  }

  private def showCheckResult(result: PasswordCheckResult): Unit = {
    println(s"\nРезультат проверки по политике ${result.policyName}:")

    result.checks.foreach { check =>
      val mark = if (check.passed) "[+]" else "[-]"
      println(s"$mark ${check.description}")
    }

    if (result.passed) {
      println(s"\nПароль соответствует политике ${result.policyName}.")
    } else {
      println(s"\nПароль не соответствует политике ${result.policyName}.")
    }
  }

  private def createPolicy(): Unit = {
    println("\nСоздание новой политики")

    val name = askPolicyName()
    val description = readValue("Описание политики: ").trim
    val minimumLength = askInt("Минимальная длина: ", 1, Int.MaxValue)
    val maximumLength = askInt("Максимальная длина: ", minimumLength, Int.MaxValue)
    val defaultLength = askInt("Длина по умолчанию: ", minimumLength, maximumLength)
    val groups = askAllowedGroups(maximumLength)

    val blockedSymbols = readValue(
      "Запрещённые символы (оставьте пустым, если их нет): "
    ).toList

    val allowRepeat = askBoolean("AllowRepeat (true/false): ")
    val maximumRepeat = askInt("Максимум одинаковых символов подряд: ", 1, Int.MaxValue)
    val allowSpace = askBoolean("AllowSpace (true/false): ")
    val blockedStrings = parseBlockedStrings(
      readValue("Запрещённые подстроки через запятую (или пусто): ")
    )

    val policy = PasswordPolicy(
      name,
      description,
      minimumLength,
      maximumLength,
      defaultLength,
      groups,
      blockedSymbols,
      allowRepeat,
      maximumRepeat,
      allowSpace,
      blockedStrings
    )

    PolicyService.create(policy) match {
      case Right(path) =>
        println(s"Политика '$name' сохранена: $path")
        reloadConfiguration()

      case Left(error) =>
        showError(error)
    }
  }

  private def editPolicy(
    policy: PasswordPolicy,
    path: Path
  ): Either[AppError, Option[PasswordPolicy]] = {
    readMenu(editMenu) match {
      case Left(error) =>
        Left(error)

      case Right(PolicyEditCommand.Back) =>
        Right(None)

      case Right(command) =>
        val updatedPolicy = applyEditCommand(policy, command)
        PolicyService.update(updatedPolicy, path).map(savedPolicy => Some(savedPolicy))
    }
  }

  private def applyEditCommand(
    policy: PasswordPolicy,
    command: PolicyEditCommand
  ): PasswordPolicy = {
    command match {
      case PolicyEditCommand.EditDescription =>
        policy.copy(description = readValue("Новое описание: ").trim)

      case PolicyEditCommand.EditMinimumLength =>
        policy.copy(
          minimumLength = askInt("Новая минимальная длина: ", 1, policy.maximumLength)
        )

      case PolicyEditCommand.EditMaximumLength =>
        policy.copy(
          maximumLength = askInt(
            "Новая максимальная длина: ",
            policy.minimumLength,
            Int.MaxValue
          )
        )

      case PolicyEditCommand.EditDefaultLength =>
        policy.copy(
          defaultLength = askInt(
            "Новая длина по умолчанию: ",
            policy.minimumLength,
            policy.maximumLength
          )
        )

      case PolicyEditCommand.EditGroups =>
        policy.copy(allowedGroups = askAllowedGroups(policy.maximumLength))

      case PolicyEditCommand.EditBlockedSymbols =>
        policy.copy(
          blockedSymbols = readValue("Новый список запрещённых символов: ").toList
        )

      case PolicyEditCommand.EditAllowRepeat =>
        policy.copy(allowRepeat = askBoolean("AllowRepeat (true/false): "))

      case PolicyEditCommand.EditMaximumRepeat =>
        policy.copy(
          maximumRepeat = askInt(
            "Новый максимум повторов подряд: ",
            1,
            policy.maximumLength
          )
        )

      case PolicyEditCommand.EditAllowSpace =>
        policy.copy(allowSpace = askBoolean("AllowSpace (true/false): "))

      case PolicyEditCommand.EditBlockedStrings =>
        policy.copy(
          blockedStrings = parseBlockedStrings(
            readValue("Новые запрещённые подстроки через запятую: ")
          )
        )

      case PolicyEditCommand.Back =>
        policy
    }
  }

  private def showPolicyInfo(policy: PasswordPolicy): Unit = {
    println(s"\nПолитика: ${policy.name}")
    println(s"Описание: ${policy.description}")
    println(
      s"Длина: ${policy.minimumLength} - ${policy.maximumLength} " +
        s"(по умолчанию ${policy.defaultLength})"
    )

    println("Группы символов:")
    policy.allowedGroups.foreach { case (group, minimum) =>
      val requirement =
        if (minimum == 0) "необязательные"
        else s"минимум $minimum"

      println(s"- ${group.title} ($group): $requirement")
    }

    val blockedSymbolsText =
      if (policy.blockedSymbols.isEmpty) "нет"
      else policy.blockedSymbols.mkString(" ")

    val blockedStringsText =
      if (policy.blockedStrings.isEmpty) "нет"
      else policy.blockedStrings.mkString(", ")

    println(s"Запрещённые символы: $blockedSymbolsText")
    println(s"Повторение символов разрешено: ${policy.allowRepeat}")
    println(s"Максимум одинаковых подряд: ${policy.maximumRepeat}")
    println(s"Пробелы разрешены: ${policy.allowSpace}")
    println(s"Запрещённые подстроки: $blockedStringsText")
  }

  private def askAllowedGroups(maximumLength: Int): List[(SymbolsGroup, Int)] = {
    val groups = SymbolsGroup.values.toList

    println("Доступные группы символов:")
    groups.zipWithIndex.foreach { case (group, index) =>
      println(s"${index + 1}. $group (${group.title})")
    }

    def readGroupIndexes(): List[Int] = {
      val input = readValue("Введите номера нужных групп через пробел: ").trim
      val parts = input.split("\\s+").filter(_.nonEmpty).toList
      val numbers = parts.map(_.toIntOption)
      val allNumbersValid = parts.nonEmpty && numbers.forall { number =>
        number.exists(value => value >= 1 && value <= groups.length)
      }

      if (allNumbersValid) {
        numbers.flatMap(_.toList).distinct.map(_ - 1)
      } else {
        val error =
          if (parts.isEmpty) AppError.EmptyGroupSelection
          else AppError.InvalidGroupSelection(input, groups.length)

        showError(error)
        readGroupIndexes()
      }
    }

    val selectedIndexes = readGroupIndexes()

    groups.zipWithIndex.collect {
      case (group, index) if selectedIndexes.contains(index) =>
        val minimum = askInt(
          s"Минимальное количество символов группы $group (0 = необязательно): ",
          0,
          maximumLength
        )
        group -> minimum
    }
  }

  private def askPolicyName(): String = {
    val name = readValue(
      "Название политики (латинские буквы, цифры, _ или -): "
    ).trim

    if (name.matches("[A-Za-z0-9_-]+")) {
      name
    } else {
      showError(AppError.InvalidPolicyName(name))
      askPolicyName()
    }
  }

  private def askPasswordLength(policy: PasswordPolicy): Int = {
    val input = readValue(
      s"Длина пароля (например, ${policy.defaultLength}): "
    ).trim

    if (input.isEmpty) {
      policy.defaultLength
    } else {
      input.toIntOption match {
        case Some(length)
            if length >= policy.minimumLength && length <= policy.maximumLength =>
          length

        case Some(length) =>
          showError(AppError.IntegerOutOfRange(
            length,
            policy.minimumLength,
            policy.maximumLength
          ))
          askPasswordLength(policy)

        case None =>
          showError(AppError.NotAnInteger(
            input,
            policy.minimumLength,
            policy.maximumLength
          ))
          askPasswordLength(policy)
      }
    }
  }

  private def askInt(prompt: String, minimum: Int, maximum: Int): Int = {
    readInt(prompt, minimum, maximum) match {
      case Right(value) =>
        value

      case Left(error) =>
        showError(error)
        askInt(prompt, minimum, maximum)
    }
  }

  private def readInt(
    prompt: String,
    minimum: Int,
    maximum: Int
  ): Either[AppError, Int] = {
    val input = readValue(prompt).trim

    input.toIntOption match {
      case Some(value) if value >= minimum && value <= maximum =>
        Right(value)

      case Some(value) =>
        Left(AppError.IntegerOutOfRange(value, minimum, maximum))

      case None =>
        Left(AppError.NotAnInteger(input, minimum, maximum))
    }
  }

  private def askBoolean(prompt: String): Boolean = {
    readBoolean(prompt) match {
      case Right(value) =>
        value

      case Left(error) =>
        showError(error)
        askBoolean(prompt)
    }
  }

  private def readBoolean(prompt: String): Either[AppError, Boolean] = {
    readValue(prompt).trim.toLowerCase match {
      case "true" => Right(true)
      case "false" => Right(false)
      case other => Left(AppError.NotABoolean(other))
    }
  }

  private def parseBlockedStrings(text: String): List[String] = {
    text
      .split(",", -1)
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
  }

  private def policyName(path: Path, cachedPolicy: Option[PasswordPolicy]): String = {
    cachedPolicy
      .map(_.name)
      .getOrElse(path.getFileName.toString.stripSuffix(".properties"))
  }

  private def readMenu[A](menu: Menu[A]): Either[AppError, A] = {
    println(s"\n ${menu.title}")
    menu.items.zipWithIndex.foreach { case (item, index) =>
      println(s"${index + 1}. ${item.title}")
    }
    println(s"0. ${menu.backTitle}")

    val input = readValue("Ваш выбор: ").trim

    if (input == "0") {
      Right(menu.backCommand)
    } else {
      input.toIntOption match {
        case Some(number) if number >= 1 && number <= menu.items.length =>
          Right(menu.items(number - 1).command)

        case _ =>
          Left(AppError.UnknownMenuChoice(input))
      }
    }
  }

  private def readValue(prompt: String): String = {
    print(prompt)
    Option(StdIn.readLine()).getOrElse("")
  }

  private def showError(error: AppError): Unit = {
    EventLogger.log(errorLevel(error), ErrorMessages.logText(error))
    ErrorMessages.lines(error).foreach(line => println(line))
  }

  private def errorLevel(error: AppError): String = {
    error match {
      case AppError.UnknownMenuChoice(_) =>
        "WARN"

      case AppError.NotAnInteger(_, _, _) =>
        "WARN"

      case AppError.IntegerOutOfRange(_, _, _) =>
        "WARN"

      case AppError.NotABoolean(_) =>
        "WARN"

      case AppError.InvalidGroupSelection(_, _) =>
        "WARN"

      case AppError.EmptyGroupSelection =>
        "WARN"

      case AppError.InvalidPolicyName(_) =>
        "WARN"

      case _ =>
        "ERROR"
    }
  }

  private def messageOf(error: Throwable): String = {
    Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
  }
}
