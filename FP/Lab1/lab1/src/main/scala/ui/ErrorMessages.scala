object ErrorMessages {
  def lines(error: AppError): List[String] = error match {
    case AppError.InvalidPolicy(PolicyValidationError(policyName, problem)) =>
      val (parameter, reason, fix) = problemDetails(problem)
      List(
        s"Ошибка в политике '$policyName'.",
        s"Параметр '$parameter': $reason",
        s"Как исправить: $fix"
      )

    case AppError.InvalidPolicyName(name) =>
      List(
        s"Имя политики '$name' некорректно.",
        "Используйте латинские буквы, цифры, символ _ или -."
      )

    case AppError.PolicyAlreadyExists(name, file) =>
      List(
        s"Политика '$name' уже существует.",
        s"Файл: $file"
      )

    case AppError.PolicyNotFound(name) =>
      List(s"Политика '$name' не найдена.")

    case AppError.DefaultPolicyNotLoaded(name) =>
      List(s"Политика по умолчанию '$name' не загружена.")

    case AppError.LengthNotAllowed(policyName, length, minimum, maximum) =>
      List(
        s"Длина $length не разрешена политикой '$policyName'.",
        s"Допустимая длина: от $minimum до $maximum."
      )

    case AppError.RequiredCharactersAboveLength(policyName, required, length) =>
      List(
        s"Политика '$policyName' требует минимум $required символов.",
        s"Запрошенная длина: $length."
      )

    case AppError.NotEnoughCharactersForLength(policyName, available, length) =>
      List(
        s"Политика '$policyName' запрещает повторение символов.",
        s"Доступно символов: $available, запрошенная длина: $length."
      )

    case AppError.PasswordGenerationFailed(policyName, reason) =>
      List(
        s"Не удалось сгенерировать пароль по политике '$policyName'.",
        s"Причина: $reason"
      )

    case AppError.ConfigurationDirectoryMissing(path) =>
      List(
        "Не найдена папка с конфигурациями.",
        s"Путь: $path"
      )

    case AppError.ConfigurationFileMissing(path) =>
      List(
        "Не найден файл конфигурации.",
        s"Путь: $path"
      )

    case AppError.NoPoliciesFound(path) =>
      List(
        "В папке нет файлов политик.",
        s"Путь: $path"
      )

    case AppError.InvalidConfiguration(file, parameter, reason) =>
      List(
        s"Ошибка в файле '$file'.",
        s"Параметр '$parameter': $reason"
      )

    case AppError.IoFailure(operation, reason) =>
      List(
        s"Ошибка при выполнении операции: $operation.",
        s"Причина: $reason"
      )

    case AppError.UnknownMenuChoice(input) =>
      List(
        s"Неверный ввод: '$input'.",
        "Введите номер пункта из меню."
      )

    case AppError.NotAnInteger(input, minimum, maximum) =>
      List(
        s"Значение '$input' не является целым числом.",
        s"Введите целое число от $minimum до $maximum."
      )

    case AppError.IntegerOutOfRange(value, minimum, maximum) =>
      List(
        s"Число $value вне допустимого диапазона.",
        s"Введите целое число от $minimum до $maximum."
      )

    case AppError.NotABoolean(input) =>
      List(
        s"Значение '$input' не является логическим.",
        "Введите true или false."
      )

    case AppError.InvalidGroupSelection(input, maximum) =>
      List(
        s"Неверный список групп: '$input'.",
        s"Введите номера групп от 1 до $maximum, разделяя их пробелами."
      )

    case AppError.EmptyGroupSelection =>
      List("Не выбрано ни одной группы символов.")

    case AppError.Unexpected(reason) =>
      List(
        "Внутренняя ошибка приложения.",
        s"Причина: $reason"
      )
  }

  def logText(error: AppError): String = lines(error).mkString(" | ")

  private def problemDetails(problem: PolicyProblem): (String, String, String) = {
    problem match {
      case PolicyProblem.MinimumLengthLessThanOne(value) =>
        ("minimumLength", s"значение $value меньше 1.", "Укажите минимум 1.")

      case PolicyProblem.MaximumLengthLessThanMinimum(maximum, minimum) =>
        (
          "maximumLength",
          s"значение $maximum меньше минимальной длины $minimum.",
          "Увеличьте maximumLength или уменьшите minimumLength."
        )

      case PolicyProblem.DefaultLengthOutsideRange(value, minimum, maximum) =>
        (
          "defaultLength",
          s"значение $value вне диапазона от $minimum до $maximum.",
          "Выберите длину между minimumLength и maximumLength."
        )

      case PolicyProblem.NoCharacterGroups =>
        (
          "characterGroups",
          "не включена ни одна группа символов.",
          "Добавьте хотя бы одну разрешённую группу."
        )

      case PolicyProblem.DuplicateGroup(group) =>
        (
          "characterGroups",
          s"группа $group указана несколько раз.",
          "Оставьте каждую группу только один раз."
        )

      case PolicyProblem.NegativeGroupMinimum(group, minimum) =>
        (
          s"characterGroups.$group.minimumCount",
          s"значение $minimum отрицательное.",
          "Укажите минимум 0 или больше."
        )

      case PolicyProblem.MandatoryCountAboveMaximum(total, maximum) =>
        (
          "characterGroups.minimumCount",
          s"сумма обязательных символов ($total) больше maximumLength ($maximum).",
          "Уменьшите минимумы групп или увеличьте maximumLength."
        )

      case PolicyProblem.MandatoryCountAboveDefault(total, defaultLength) =>
        (
          "characterGroups.minimumCount",
          s"сумма обязательных символов ($total) больше defaultLength ($defaultLength).",
          "Уменьшите минимумы групп или увеличьте defaultLength."
        )

      case PolicyProblem.AllowedCharacterBlocked(characters) =>
        (
          "forbiddenCharacters",
          s"разрешённые символы одновременно запрещены: ${characters.mkString("'", "', '", "'")}.",
          "Уберите их из списка запрещённых символов."
        )

      case PolicyProblem.SpaceAllowedAndBlocked =>
        (
          "forbiddenCharacters",
          "пробел одновременно разрешён и запрещён.",
          "Уберите пробел из запрещённых символов или отключите allowSpace."
        )

      case PolicyProblem.EmptyGroupAfterBlocking(group) =>
        (
          s"characterGroups.$group",
          "после исключения запрещённых символов группа пуста.",
          "Измените запрещённые символы или удалите группу из разрешённых."
        )

      case PolicyProblem.GroupMinimumAboveAvailable(group, minimum, available) =>
        (
          s"characterGroups.$group.minimumCount",
          s"запрошено $minimum символов, доступно только $available.",
          "Уменьшите минимум или разрешите больше символов группы."
        )

      case PolicyProblem.NoAvailableCharacters =>
        (
          "characterGroups",
          "после фильтрации не осталось доступных символов.",
          "Включите непустую группу символов."
        )

      case PolicyProblem.NotEnoughCharactersForNoRepeat(available, defaultLength) =>
        (
          "allowRepeat",
          s"уникальных символов ($available) меньше defaultLength ($defaultLength).",
          "Разрешите повторы или уменьшите defaultLength."
        )

      case PolicyProblem.MaximumRepeatLessThanOne(value) =>
        (
          "maxRepeatSymVal",
          s"значение $value меньше 1.",
          "Укажите минимум 1."
        )
    }
  }
}
