object EventLogger {
  import java.nio.charset.StandardCharsets
  import java.nio.file.{Files, Path, Paths, StandardOpenOption}
  import java.time.LocalDateTime
  import java.time.format.DateTimeFormatter
  import scala.util.control.NonFatal

  private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private lazy val logFile: Path = {
    val workingDirectory = Paths.get("").toAbsolutePath
    val localLogs = workingDirectory.resolve("Logs")
    if (Files.isDirectory(localLogs)) localLogs.resolve("password-generator.log")
    else workingDirectory.resolve(Paths.get("Lab0", "lab0", "Logs", "password-generator.log"))
  }

  def log(level: String, message: String): Unit = {
    val entry = s"${LocalDateTime.now().format(timestampFormat)} $level $message${System.lineSeparator()}"
    try {
      Files.createDirectories(logFile.getParent)
      Files.writeString(
        logFile,
        entry,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
      )
    } catch {
      case NonFatal(error) =>
        System.err.println(s"Не удалось записать журнал: ${error.getMessage}")
    }
  }
}

object PolicySession {
  private var snapshot = List.empty[(java.nio.file.Path, PswrdPolitic)]
  private var defaultPolicyName = Option.empty[String]
  private var initialized = false

  def current: List[(java.nio.file.Path, PswrdPolitic)] = snapshot
  def currentDefault: Option[String] = defaultPolicyName
  def hasSnapshot: Boolean = initialized

  def replace(policies: List[(java.nio.file.Path, PswrdPolitic)], defaultName: String): Unit = {
    snapshot = policies
    defaultPolicyName = Some(defaultName)
    initialized = true
  }

  def update(path: java.nio.file.Path, policy: PswrdPolitic): Unit = {
    if (initialized) {
      snapshot = snapshot.filterNot(_._1 == path) :+ (path -> policy)
    }
  }
}

enum SymbolsGroup(val x : Array[Char]) {
  case LowercaseLatinLetters extends SymbolsGroup(('a' to 'z').toArray)
  case UppercaseLatinLetters extends SymbolsGroup(('A' to 'Z').toArray)
  case Numbers extends SymbolsGroup(('0' to '9').toArray)
  case SpecialSymbols extends SymbolsGroup(
    Array(
      '!','@','#','$','%','^','&','*','(',')','_','-','+','='
    )
  )
}

class PswrdPolitic(
  var name : String,
  var description : String,
  var MAX_LENGTH : Int,
  var MIN_LENGTH : Int,
  var DEFAULT_LENGTH : Int,
  var AllowedSymbolGroups : Array[(SymbolsGroup, Int)], //Разрешенная группа символов и минимальное количество символов каждой группы
  var BlockedSymbols : Array[Char],
  var AllowRepeat : Boolean,
  var MaxRepeatSymVal : Int,
  var AllowSpace : Boolean,
  var BlockedStrings : Array[String],
)
{
  def GeneratePassword(requestedLength: Int = DEFAULT_LENGTH): String = {
    import scala.util.Random
    require(MIN_LENGTH >= 1 && MAX_LENGTH >= MIN_LENGTH, "Некорректные границы длины")
    require(requestedLength >= MIN_LENGTH && requestedLength <= MAX_LENGTH,
      s"Длина $requestedLength не разрешена политикой '$name'. Допустимо от $MIN_LENGTH до $MAX_LENGTH.")
    require(MaxRepeatSymVal >= 1, "MaxRepeatSymVal должен быть не меньше 1")
    require(AllowedSymbolGroups.nonEmpty, "Не задано ни одной группы символов")
    val length = requestedLength
    val blocked = BlockedSymbols.toSet
    val groups = AllowedSymbolGroups.toList.map { case (group, minimum) =>
      require(minimum >= 0, s"Отрицательный минимум для группы $group")
      val available = group.x.filterNot(ch => blocked.contains(ch)).distinct.toList
      require(minimum == 0 || available.nonEmpty,
      s"В группе $group не осталось разрешённых символов")
      (available, minimum)
      }

    val pool = (groups.flatMap(_._1) ++ (if (AllowSpace && !blocked.contains(' ')) List(' ') else Nil)).distinct
    val requiredCount = groups.iterator.map(_._2.toLong).sum
    require(pool.nonEmpty, "Нет доступных символов для генерации")
    require(requiredCount <= length, s"Обязательных символов больше, чем длина пароля по политике '$name'")
    require(AllowRepeat || pool.length >= length,
    "При запрещённых повторах недостаточно доступных символов")
    def isValid(password: String): Boolean = {
      val groupRequirementsMet = groups.forall { case (chars, minimum) =>
        password.count(ch => chars.contains(ch)) >= minimum
      }

      val noForbiddenSubstring =
      !BlockedStrings.exists(part => part.nonEmpty && password.contains(part))

      val repeatsAllowed =
      AllowRepeat || password.distinct.length == password.length

      val consecutiveLimitMet = MaxRepeatSymVal >= password.length ||
        !password.sliding(MaxRepeatSymVal + 1).exists { window =>
          window.length == MaxRepeatSymVal + 1 && window.forall(_ == window.head)
        }

        groupRequirementsMet &&
          noForbiddenSubstring &&
          repeatsAllowed &&
          consecutiveLimitMet
      }

      val groupCharacterSets = groups.map(_._1.toSet).toArray
      val minimumCounts = groups.map(_._2).toArray
      val currentCounts = Array.fill(groups.length)(0)

      def search(
        prefix: String,
        usedCharacters: Set[Char],
        previousCharacter: Option[Char],
        currentRunLength: Int
      ): Option[String] = {
        if (prefix.length == length) {
          if (currentCounts.indices.forall(index => currentCounts(index) >= minimumCounts(index)) && isValid(prefix)) {
            Some(prefix)
          } else None
        } else {
          val remaining = length - prefix.length
          val requiredRemaining = currentCounts.indices.iterator.map { index =>
            (minimumCounts(index) - currentCounts(index)).max(0).toLong
          }.sum

          if (requiredRemaining > remaining) None
          else {
            val choices = Random.shuffle(pool).iterator
            var result: Option[String] = None
            while (choices.hasNext && result.isEmpty) {
              val character = choices.next()
              val repeatsTooMany = previousCharacter.contains(character) && currentRunLength >= MaxRepeatSymVal
              val alreadyUsed = !AllowRepeat && usedCharacters.contains(character)
              val nextPrefix = prefix + character
              val containsForbiddenSubstring = BlockedStrings.exists { blockedString =>
                blockedString.nonEmpty && nextPrefix.contains(blockedString)
              }

              if (!repeatsTooMany && !alreadyUsed && !containsForbiddenSubstring) {
                val matchingGroups = groupCharacterSets.indices.filter(groupIndex =>
                  groupCharacterSets(groupIndex).contains(character)
                )
                matchingGroups.foreach(groupIndex => currentCounts(groupIndex) += 1)

                val nextRunLength = if (previousCharacter.contains(character)) currentRunLength + 1 else 1
                result = search(
                  nextPrefix,
                  if (AllowRepeat) usedCharacters else usedCharacters + character,
                  Some(character),
                  nextRunLength
                )

                matchingGroups.foreach(groupIndex => currentCounts(groupIndex) -= 1)
              }
            }
            result
          }
        }
      }

      search("", Set.empty, None, 0).getOrElse(
        throw new IllegalStateException(
          s"Невозможно сгенерировать пароль по политике '$name' с длиной $length. Проверьте ограничения."
        )
      )
    }
}

case class MenuItem(
  title : String, action : Option[() => Unit] = None,
  children : List[MenuItem] = Nil
)

object Menu {
  def show(items: List[MenuItem], path: List[String] = Nil): Unit =  {
    if(path.nonEmpty) {
      println(s"\n Путь: ${path.mkString(" > ")}")
    }
    println("\n Меню ")
    items.zipWithIndex.foreach { case (item, i) => println(s"${i + 1}. ${item.title}")}
    println("0. Назад")
    println("\nВаш выбор: ")
    val input = scala.io.StdIn.readLine().trim
    input match {
      case "0" => ()
      case _ => input.toIntOption match {
        case Some(idx) if idx >= 1 && idx <= items.size => val selected = items(idx-1)
        if(selected.children.nonEmpty) {
          show(selected.children, path :+ selected.title)
        }
        else {
          selected.action.foreach(_())
          println("\nНажмите Enter чтобы продолжить")
          scala.io.StdIn.readLine()
        }
        show(items, path)
        case _ =>
          println("Неверный ввод")
          Thread.sleep(1000)
          show(items, path)
      }
    }
  }
}
  def readValue(prompt: String): String = {
    print(prompt)
    Option(scala.io.StdIn.readLine()).getOrElse("")
  }

  def readInt(prompt: String, minimum: Int, maximum: Int): Int = {
    var result: Option[Int] = None
    while (result.isEmpty) {
      readValue(prompt).trim.toIntOption match {
        case Some(value) if value >= minimum && value <= maximum => result = Some(value)
        case _ =>
          EventLogger.log("WARN", "Введён некорректный числовой параметр")
          println(s"Введите целое число от $minimum до $maximum.")
      }
    }
    result.get
  }

  def readPasswordLength(policy: PswrdPolitic): Int = {
    while (true) {
      val input = readValue(s"Длина пароля (Enter = ${policy.DEFAULT_LENGTH}): ").trim
      if (input.isEmpty) return policy.DEFAULT_LENGTH
      input.toIntOption match {
        case Some(length) if length >= policy.MIN_LENGTH && length <= policy.MAX_LENGTH => return length
        case _ =>
          EventLogger.log("WARN", s"Некорректная длина генерации: policy=${policy.name}")
          println(s"Введите целое число от ${policy.MIN_LENGTH} до ${policy.MAX_LENGTH}.")
      }
    }
    policy.DEFAULT_LENGTH
  }

  def readBoolean(prompt: String): Boolean = {
    while (true) {
      readValue(prompt).trim.toLowerCase match {
        case "true" => return true
        case "false" => return false
        case _ =>
          EventLogger.log("WARN", "Введён некорректный логический параметр")
          println("Введите true или false.")
      }
    }
    false
  }

  def readAllowedGroups(maxLength: Int): Array[(SymbolsGroup, Int)] = {
    val availableGroups = SymbolsGroup.values
    println("Доступные группы символов:")
    availableGroups.zipWithIndex.foreach { case (group, index) =>
      println(s"${index + 1}. $group")
    }

    var selectedGroupIndexes = Set.empty[Int]
    while (selectedGroupIndexes.isEmpty) {
      val input = readValue("Введите номера нужных групп через пробел: ").trim
      val indexes = input.split("\\s+").toList.map(_.toIntOption)
      if (input.nonEmpty && indexes.forall(_.exists(index => index >= 1 && index <= availableGroups.length))) {
        selectedGroupIndexes = indexes.flatten.map(_ - 1).toSet
      } else {
        println(s"Введите номера групп от 1 до ${availableGroups.length}, разделяя их пробелами.")
      }
    }

    val selectedGroups = availableGroups.zipWithIndex.collect {
      case (group, index) if selectedGroupIndexes.contains(index) => group
    }
    selectedGroups.map { group =>
      val minimum = readInt(s"Минимальное количество символов группы $group (0 = необязательно): ", 0, maxLength)
      group -> minimum
    }
  }

  def policyConfigDirectory: java.nio.file.Path = {
    import java.nio.file.{Files, Paths}

    val currentDirectory = Paths.get("")
    val localConfigDirectory = currentDirectory.resolve("PswConfigs")
    if (Files.isDirectory(localConfigDirectory)) localConfigDirectory
    else currentDirectory.resolve(Paths.get("Lab0", "lab0", "PswConfigs"))
  }

  private def applicationConfigFile: java.nio.file.Path =
    policyConfigDirectory.resolve("application.properties")

  private def readDefaultPolicyName(): String = {
    import java.nio.file.Files
    import java.util.Properties

    if (!Files.isRegularFile(applicationConfigFile)) {
      throw new IllegalArgumentException("В application.properties отсутствует параметр defaultPolicy.")
    }
    val properties = new Properties()
    val input = Files.newInputStream(applicationConfigFile)
    try properties.load(input)
    finally input.close()
    Option(properties.getProperty("defaultPolicy")).map(_.trim).filter(_.nonEmpty)
      .getOrElse(throw new IllegalArgumentException("Параметр defaultPolicy отсутствует или пуст."))
  }

  def setDefaultPolicy(policy: PswrdPolitic, policyFile: java.nio.file.Path): Unit = {
    import java.nio.file.{Files, StandardOpenOption}
    import java.util.Properties

    validatePolicy(policy)
    if (!Files.isRegularFile(policyFile) || loadPolitic(policyFile).name != policy.name) {
      throw new IllegalArgumentException(s"Нельзя назначить default отсутствующую политику '${policy.name}'.")
    }

    val properties = new Properties()
    properties.setProperty("defaultPolicy", policy.name)
    Files.createDirectories(policyConfigDirectory)
    val output = Files.newOutputStream(
      applicationConfigFile,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    try properties.store(output, "Application settings")
    finally output.close()
    EventLogger.log("INFO", s"Назначена политика по умолчанию: policy=${policy.name}")
  }

  def CreatePolitic(): PswrdPolitic = {
    import java.nio.file.{Files, StandardOpenOption}
    import java.util.Properties

    val name = readValue("Название политики (латинские буквы, цифры, _ или -): ").trim
    require(name.matches("[A-Za-z0-9_-]+"), "Имя политики может содержать только латинские буквы, цифры, _ и -")
    val description = readValue("Описание политики: ").trim
    val minLength = readInt("Минимальная длина: ", 1, Int.MaxValue)
    val maxLength = readInt("Максимальная длина: ", minLength, Int.MaxValue)
    val defaultLength = readInt("Длина по умолчанию: ", minLength, maxLength)

    val allowedGroups = readAllowedGroups(maxLength)

    val blockedSymbols = readValue("Запрещённые символы (оставьте пустым, если их нет): ").toArray
    val allowRepeat = readBoolean("AllowRepeat (true/false): ")
    val maxRepeat = readInt("Максимум одинаковых символов подряд: ", 1, Int.MaxValue)
    val allowSpace = readBoolean("AllowSpace (true/false): ")
    val blockedStrings = readValue("Запрещённые подстроки через запятую (или пусто): ")
      .split(",", -1).iterator.map(_.trim).filter(_.nonEmpty).toArray

    val blockedSet = blockedSymbols.toSet
    allowedGroups.foreach { case (group, minimum) =>
      val available = group.x.distinct.count(ch => !blockedSet.contains(ch))
      require(available > 0, s"В выбранной группе $group не осталось разрешённых символов")
      require(available >= minimum, s"Для группы $group требуется $minimum символов, но доступно только $available")
    }
    require(allowedGroups.map(_._2).sum <= maxLength,
      "Сумма обязательных количеств символов превышает максимальную длину")

    val availablePool = (allowedGroups.flatMap { case (group, _) =>
      group.x.filterNot(blockedSet.contains)
    } ++ (if (allowSpace && !blockedSet.contains(' ')) Array(' ') else Array.emptyCharArray)).distinct
    require(availablePool.nonEmpty, "После исключения запрещённых символов не осталось доступных символов")
    require(allowRepeat || availablePool.length >= defaultLength,
      "При запрещённых повторах недостаточно доступных символов для длины по умолчанию")

    val policy = new PswrdPolitic(
      name, description, maxLength, minLength, defaultLength, allowedGroups,
      blockedSymbols, allowRepeat, maxRepeat, allowSpace, blockedStrings
    )
    validatePolicy(policy)

    val configDirectory = policyConfigDirectory
    Files.createDirectories(configDirectory)
    val configFile = configDirectory.resolve(s"$name.properties")
    require(!Files.exists(configFile), s"Политика '$name' уже существует: $configFile")

    val properties = new Properties()
    properties.setProperty("name", name)
    properties.setProperty("description", description)
    properties.setProperty("minimumLength", minLength.toString)
    properties.setProperty("maximumLength", maxLength.toString)
    properties.setProperty("defaultLength", defaultLength.toString)
    properties.setProperty("allowedGroupCount", allowedGroups.length.toString)
    allowedGroups.zipWithIndex.foreach { case ((group, minimum), index) =>
      properties.setProperty(s"allowedGroup.$index.name", group.toString)
      properties.setProperty(s"allowedGroup.$index.minimum", minimum.toString)
      properties.setProperty(s"allowedGroup.$index.enabled", "true")
    }
    properties.setProperty("blockedSymbols", blockedSymbols.mkString)
    properties.setProperty("allowRepeat", allowRepeat.toString)
    properties.setProperty("maxRepeatSymVal", maxRepeat.toString)
    properties.setProperty("allowSpace", allowSpace.toString)
    properties.setProperty("blockedStringCount", blockedStrings.length.toString)
    blockedStrings.zipWithIndex.foreach { case (value, index) =>
      properties.setProperty(s"blockedString.$index", value)
    }

    val output = Files.newOutputStream(configFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    try properties.store(output, s"Password policy: $name")
    finally output.close()

    if (!Files.exists(applicationConfigFile)) setDefaultPolicy(policy, configFile)
    EventLogger.log("INFO", s"Политика создана и сохранена: policy=$name")
    reloadConfiguration()
    println(s"Политика '$name' сохранена: $configFile")
    policy
  }

  def loadPolitic(configFile: java.nio.file.Path): PswrdPolitic = {
    import java.nio.file.Files
    import java.util.Properties

    val properties = new Properties()
    val input = Files.newInputStream(configFile)
    try properties.load(input)
    finally input.close()

    val policyName = Option(properties.getProperty("name"))
      .getOrElse(configFile.getFileName.toString.stripSuffix(".properties"))

    def invalidConfig(parameter: String, reason: String): Nothing =
      throw new IllegalArgumentException(
        s"Ошибка в политике '$policyName'. Параметр '$parameter': $reason"
      )

    def requiredProperty(key: String): String =
      Option(properties.getProperty(key)).getOrElse(
        invalidConfig(key, s"значение отсутствует в ${configFile.getFileName}.")
      )

    def intProperty(key: String): Int =
      requiredProperty(key).toIntOption.getOrElse(
        invalidConfig(key, "значение должно быть целым числом.")
      )

    def booleanProperty(key: String): Boolean = requiredProperty(key).toLowerCase match {
      case "true" => true
      case "false" => false
      case _ => invalidConfig(key, "значение должно быть true или false.")
    }

    val groupCount = intProperty("allowedGroupCount")
    if (groupCount < 0) invalidConfig("allowedGroupCount", "значение не может быть отрицательным.")
    val allowedGroups = (0 until groupCount).flatMap { index =>
      val groupName = requiredProperty(s"allowedGroup.$index.name")
      val group = SymbolsGroup.values.find(_.toString == groupName).getOrElse(
        invalidConfig(s"allowedGroup.$index.name", s"неизвестная группа '$groupName'.")
      )
      val minimum = intProperty(s"allowedGroup.$index.minimum")
      val enabled = Option(properties.getProperty(s"allowedGroup.$index.enabled"))
        .map(_.toLowerCase match {
          case "true" => true
          case "false" => false
          case _ => invalidConfig(s"allowedGroup.$index.enabled", "значение должно быть true или false.")
        })
        .getOrElse(true)
      if (minimum < 0) invalidConfig(s"allowedGroup.$index.minimum", "значение не может быть отрицательным.")
      if (!enabled && minimum > 0) {
        invalidConfig(s"allowedGroup.$index.minimum", "минимум задан для выключенной группы.")
      }
      if (enabled) Some(group -> minimum) else None
    }.toArray

    val blockedStringCount = intProperty("blockedStringCount")
    if (blockedStringCount < 0) invalidConfig("blockedStringCount", "значение не может быть отрицательным.")
    val blockedStrings = Array.tabulate(blockedStringCount) { index =>
      requiredProperty(s"blockedString.$index")
    }

    val policy = new PswrdPolitic(
      requiredProperty("name"),
      requiredProperty("description"),
      intProperty("maximumLength"),
      intProperty("minimumLength"),
      intProperty("defaultLength"),
      allowedGroups,
      requiredProperty("blockedSymbols").toArray,
      booleanProperty("allowRepeat"),
      intProperty("maxRepeatSymVal"),
      booleanProperty("allowSpace"),
      blockedStrings
    )
    validatePolicy(policy)
    EventLogger.log("INFO", s"Конфигурация загружена и проверена: policy=${policy.name}")
    policy
  }

  def listSavedPolicies(): List[java.nio.file.Path] = {
    import java.nio.file.{Files, Path}
    val configDirectory = policyConfigDirectory
    if (!Files.isDirectory(configDirectory)) return Nil

    val stream = Files.list(configDirectory)
    try {
      val iterator = stream.iterator()
      val files = List.newBuilder[Path]
      while (iterator.hasNext) {
        val path = iterator.next()
        if (Files.isRegularFile(path) &&
            path.getFileName.toString.endsWith(".properties") &&
            path.getFileName.toString != "application.properties") {
          files += path
        }
      }
      files.result().sortBy(_.getFileName.toString)
    } finally stream.close()
  }

  def reloadConfiguration(): Boolean = {
    import scala.util.control.NonFatal

    try {
      val configFiles = listSavedPolicies()
      if (configFiles.isEmpty) throw new IllegalArgumentException("В папке PswConfigs нет политик.")

      val candidate = configFiles.map(path => path -> loadPolitic(path))
      val defaultName = readDefaultPolicyName()
      if (!candidate.exists(_._2.name == defaultName)) {
        throw new IllegalArgumentException(s"defaultPolicy '$defaultName' не соответствует ни одному файлу политики.")
      }

      PolicySession.replace(candidate, defaultName)
      EventLogger.log("INFO", s"Конфигурация успешно перезагружена: policies=${candidate.size}")
      println(s"Конфигурация перезагружена. Загружено политик: ${candidate.size}.")
      true
    } catch {
      case NonFatal(error) =>
        EventLogger.log("ERROR", s"Ошибка перезагрузки конфигурации: ${error.getMessage}")
        println(s"Не удалось перезагрузить конфигурацию: ${error.getMessage}")
        false
    }
  }

  private def invalidPolicy(policy: PswrdPolitic, parameter: String, reason: String, fix: String): Nothing =
    throw new IllegalArgumentException(
      s"Ошибка в политике '${policy.name}'. Параметр '$parameter': $reason $fix"
    )

  def validatePolicy(policy: PswrdPolitic): Unit = {
    if (policy.MIN_LENGTH < 1)
      invalidPolicy(policy, "minimumLength", "значение меньше 1.", "Укажите минимум 1.")
    if (policy.MAX_LENGTH < policy.MIN_LENGTH)
      invalidPolicy(policy, "maximumLength", "меньше минимальной длины.", "Увеличьте maximumLength или уменьшите minimumLength.")
    if (policy.DEFAULT_LENGTH < policy.MIN_LENGTH || policy.DEFAULT_LENGTH > policy.MAX_LENGTH)
      invalidPolicy(policy, "defaultLength", "значение вне допустимого диапазона.",
        "Выберите длину между minimumLength и maximumLength.")
    if (policy.AllowedSymbolGroups.isEmpty)
      invalidPolicy(policy, "characterGroups", "не включена ни одна группа символов.",
        "Добавьте хотя бы одну разрешённую группу.")
    if (policy.AllowedSymbolGroups.map(_._1).distinct.length != policy.AllowedSymbolGroups.length)
      invalidPolicy(policy, "characterGroups", "группа символов указана несколько раз.",
        "Оставьте каждую группу только один раз.")
    if (policy.MaxRepeatSymVal < 1)
      invalidPolicy(policy, "maximumConsecutiveIdenticalCharacters", "значение меньше 1.", "Укажите минимум 1.")

    val blocked = policy.BlockedSymbols.toSet
    val groupMinimumTotal = policy.AllowedSymbolGroups.iterator.map(_._2.toLong).sum
    if (groupMinimumTotal > policy.MAX_LENGTH.toLong)
      invalidPolicy(policy, "characterGroups.minimumCount",
        s"Сумма обязательных количеств ($groupMinimumTotal) больше maximumLength (${policy.MAX_LENGTH}).",
        "Уменьшите минимумы групп или увеличьте maximumLength.")
    if (groupMinimumTotal > policy.DEFAULT_LENGTH.toLong)
      invalidPolicy(policy, "characterGroups.minimumCount",
        s"Сумма обязательных количеств ($groupMinimumTotal) больше defaultLength (${policy.DEFAULT_LENGTH}).",
        "Уменьшите минимумы групп или увеличьте defaultLength.")

    val allGroupCharacters = policy.AllowedSymbolGroups.iterator.flatMap(_._1.x).toSet
    val conflicts = allGroupCharacters.intersect(blocked)
    if (conflicts.nonEmpty)
      invalidPolicy(policy, "forbiddenCharacters",
        s"разрешённые символы одновременно запрещены: ${conflicts.mkString("'", "', '", "'")}.",
        "Уберите их из forbiddenCharacters или исключите соответствующую группу.")
    if (policy.AllowSpace && blocked.contains(' '))
      invalidPolicy(policy, "forbiddenCharacters", "пробел одновременно разрешён и запрещён.",
        "Уберите пробел из списка запрещённых символов или отключите allowSpaces.")

    policy.AllowedSymbolGroups.foreach { case (group, minimum) =>
      if (minimum < 0)
        invalidPolicy(policy, s"characterGroups.$group.minimumCount", "значение отрицательное.", "Укажите минимум 0.")
      val available = group.x.distinct.count(ch => !blocked.contains(ch))
      if (available == 0)
        invalidPolicy(policy, s"characterGroups.$group", "после исключения запрещённых символов группа пуста.",
          "Измените forbiddenCharacters или удалите группу из разрешённых.")
      if (minimum > available)
        invalidPolicy(policy, s"characterGroups.$group.minimumCount",
          s"запрошено $minimum символов, доступно только $available.",
          "Уменьшите минимум или разрешите больше символов группы.")
    }

    val pool = (policy.AllowedSymbolGroups.iterator.flatMap { case (group, _) =>
      group.x.iterator.filterNot(blocked.contains)
    } ++ (if (policy.AllowSpace) Iterator(' ') else Iterator.empty)).toSet
    if (pool.isEmpty)
      invalidPolicy(policy, "characterGroups", "после фильтрации не осталось доступных символов.",
        "Включите непустую группу символов.")
    if (!policy.AllowRepeat && pool.size < policy.DEFAULT_LENGTH)
      invalidPolicy(policy, "allowRepeatedCharacters",
        s"уникальных символов (${pool.size}) меньше defaultLength (${policy.DEFAULT_LENGTH}).",
        "Разрешите повторы или уменьшите defaultLength.")
  }

  def savePolitic(policy: PswrdPolitic, configFile: java.nio.file.Path): Unit = {
    import java.nio.file.{Files, StandardOpenOption}
    import java.util.Properties

    val properties = new Properties()
    properties.setProperty("name", policy.name)
    properties.setProperty("description", policy.description)
    properties.setProperty("minimumLength", policy.MIN_LENGTH.toString)
    properties.setProperty("maximumLength", policy.MAX_LENGTH.toString)
    properties.setProperty("defaultLength", policy.DEFAULT_LENGTH.toString)
    properties.setProperty("allowedGroupCount", policy.AllowedSymbolGroups.length.toString)
    policy.AllowedSymbolGroups.zipWithIndex.foreach { case ((group, minimum), index) =>
      properties.setProperty(s"allowedGroup.$index.name", group.toString)
      properties.setProperty(s"allowedGroup.$index.minimum", minimum.toString)
      properties.setProperty(s"allowedGroup.$index.enabled", "true")
    }
    properties.setProperty("blockedSymbols", policy.BlockedSymbols.mkString)
    properties.setProperty("allowRepeat", policy.AllowRepeat.toString)
    properties.setProperty("maxRepeatSymVal", policy.MaxRepeatSymVal.toString)
    properties.setProperty("allowSpace", policy.AllowSpace.toString)
    properties.setProperty("blockedStringCount", policy.BlockedStrings.length.toString)
    policy.BlockedStrings.zipWithIndex.foreach { case (value, index) =>
      properties.setProperty(s"blockedString.$index", value)
    }

    val output = Files.newOutputStream(
      configFile,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    try properties.store(output, s"Password policy: ${policy.name}")
    finally output.close()
    EventLogger.log("INFO", s"Политика сохранена: policy=${policy.name}")
  }

  def showPoliticInfo(policy: PswrdPolitic): Unit = {
    println(s"\nПолитика: ${policy.name}")
    println(s"Описание: ${policy.description}")
    println(s"Длина: ${policy.MIN_LENGTH} - ${policy.MAX_LENGTH} (по умолчанию ${policy.DEFAULT_LENGTH})")
    println("Группы символов:")
    policy.AllowedSymbolGroups.foreach { case (group, minimum) =>
      val requirement = if (minimum == 0) "необязательные" else s"минимум $minimum"
      println(s"- $group: $requirement")
    }
    println(s"Запрещённые символы: ${if (policy.BlockedSymbols.isEmpty) "нет" else policy.BlockedSymbols.mkString}")
    println(s"Повторение символов разрешено: ${policy.AllowRepeat}")
    println(s"Максимум одинаковых подряд: ${policy.MaxRepeatSymVal}")
    println(s"Пробелы разрешены: ${policy.AllowSpace}")
    println(s"Запрещённые подстроки: ${if (policy.BlockedStrings.isEmpty) "нет" else policy.BlockedStrings.mkString(", ")}")
  }

  def checkPassword(policy: PswrdPolitic, password: String): Boolean = {
    val blockedCharacters = policy.BlockedSymbols.toSet
    val allowedCharacters = policy.AllowedSymbolGroups.iterator
      .flatMap { case (group, _) => group.x.iterator }
      .filterNot(blockedCharacters.contains)
      .toSet ++ (if (policy.AllowSpace && !blockedCharacters.contains(' ')) Set(' ') else Set.empty[Char])

    val groupChecks = policy.AllowedSymbolGroups.map { case (group, minimum) =>
      val groupCharacters = group.x.toSet -- blockedCharacters
      val actual = password.count(groupCharacters.contains)
      (s"Группа $group: найдено $actual, требуется минимум $minimum", actual >= minimum)
    }.toList

    var previousCharacter: Option[Char] = None
    var currentRunLength = 0
    var longestRunLength = 0
    password.foreach { character =>
      if (previousCharacter.contains(character)) currentRunLength += 1
      else currentRunLength = 1
      previousCharacter = Some(character)
      longestRunLength = longestRunLength.max(currentRunLength)
    }

    val checks = List(
      (s"Длина от ${policy.MIN_LENGTH} до ${policy.MAX_LENGTH}",
        password.length >= policy.MIN_LENGTH && password.length <= policy.MAX_LENGTH),
      ("Используются только разрешённые символы", password.forall(ch => allowedCharacters.contains(ch)))
    ) ++ groupChecks ++ List(
      ("Запрещенные подстроки", !policy.BlockedStrings.exists(part => part.nonEmpty && password.contains(part))),
      ("Повторение символов не соответствует политике",
        policy.AllowRepeat || password.distinct.length == password.length),
      (s"Более ${policy.MaxRepeatSymVal} одинаковых символов подряд",
        longestRunLength <= policy.MaxRepeatSymVal)
    )

    val failedChecks = checks.collect { case (description, false) => description }
    val matchesPolicy = failedChecks.isEmpty
    EventLogger.log("INFO", s"Выполнена проверка: policy=${policy.name}, result=${if (matchesPolicy) "passed" else "failed"}")
    if (matchesPolicy) {
      println("Пароль соответствует политике.")
    } else {
      println("Пароль не соответствует политике:")
      failedChecks.foreach(description => println(s"- $description"))
    }
    matchesPolicy
  }

  def editPolitic(policy: PswrdPolitic, configFile: java.nio.file.Path): PswrdPolitic = {
    val edited = new PswrdPolitic(
      policy.name,
      policy.description,
      policy.MAX_LENGTH,
      policy.MIN_LENGTH,
      policy.DEFAULT_LENGTH,
      policy.AllowedSymbolGroups.clone(),
      policy.BlockedSymbols.clone(),
      policy.AllowRepeat,
      policy.MaxRepeatSymVal,
      policy.AllowSpace,
      policy.BlockedStrings.clone()
    )

    println("\nКакое свойство изменить?")
    println("1. Описание")
    println("2. Минимальная длина")
    println("3. Максимальная длина")
    println("4. Длина по умолчанию")
    println("5. Разрешённые группы и их минимумы")
    println("6. Запрещённые символы")
    println("7. AllowRepeat")
    println("8. Максимум одинаковых символов подряд")
    println("9. AllowSpace")
    println("10. Запрещённые подстроки")
    println("0. Назад")

    val choice = readInt("Номер свойства: ", 0, 10)
    if (choice == 0) return policy

    choice match {
      case 1 => edited.description = readValue("Новое описание: ").trim
      case 2 => edited.MIN_LENGTH = readInt("Новая минимальная длина: ", 1, edited.MAX_LENGTH)
      case 3 => edited.MAX_LENGTH = readInt("Новая максимальная длина: ", edited.MIN_LENGTH, Int.MaxValue)
      case 4 => edited.DEFAULT_LENGTH = readInt("Новая длина по умолчанию: ", edited.MIN_LENGTH, edited.MAX_LENGTH)
      case 5 => edited.AllowedSymbolGroups = readAllowedGroups(edited.MAX_LENGTH)
      case 6 => edited.BlockedSymbols = readValue("Новый список запрещённых символов: ").toArray
      case 7 => edited.AllowRepeat = readBoolean("AllowRepeat (true/false): ")
      case 8 => edited.MaxRepeatSymVal = readInt("Новый максимум повторов подряд: ", 1, edited.MAX_LENGTH)
      case 9 => edited.AllowSpace = readBoolean("AllowSpace (true/false): ")
      case 10 => edited.BlockedStrings = readValue("Новые запрещённые подстроки через запятую: ")
        .split(",", -1).iterator.map(_.trim).filter(_.nonEmpty).toArray
      case _ => ()
    }

    validatePolicy(edited)
    savePolitic(edited, configFile)
    println(s"Политика '${edited.name}' обновлена и сохранена.")
    edited
  }

  def showSelectedPolitic(
    configFile: java.nio.file.Path,
    cachedPolicy: Option[PswrdPolitic] = None
  ): Unit = {
    var policy = cachedPolicy.getOrElse(loadPolitic(configFile))
    var selected = true
    while (selected) {
      println(s"\nПолитика: ${policy.name}")
      println("1. Использовать политику")
      println("2. Показать информацию")
      println("3. Редактировать политику")
      println("4. Проверить пароль по этой политике")
      println("5. Назначить политикой по умолчанию")
      println("0. Назад")
      readValue("Выберите действие: ").trim match {
        case "1" =>
          EventLogger.log("INFO", s"Запрошена генерация: policy=${policy.name}")
          try {
            val length = readPasswordLength(policy)
            val password = policy.GeneratePassword(length)
            EventLogger.log("INFO", s"Выполнена генерация: policy=${policy.name}, length=${password.length}")
            println(s"Сгенерированный пароль: $password")
          } catch {
            case scala.util.control.NonFatal(error) =>
              EventLogger.log("ERROR", s"Ошибка генерации: policy=${policy.name}, error=${error.getMessage}")
              println(s"Ошибка генерации: ${error.getMessage}")
          }
        case "2" => showPoliticInfo(policy)
        case "3" =>
          try {
            policy = editPolitic(policy, configFile)
            PolicySession.update(configFile, policy)
          } catch {
            case scala.util.control.NonFatal(error) =>
              EventLogger.log("ERROR", s"Ошибка редактирования политики: policy=${policy.name}, error=${error.getMessage}")
              println(s"Изменение не сохранено: ${error.getMessage}")
          }
        case "4" =>
          EventLogger.log("INFO", s"Запрошена проверка пароля: policy=${policy.name}")
          val password = readValue("Введите пароль для проверки: ")
          checkPassword(policy, password)
        case "5" =>
          try {
            setDefaultPolicy(policy, configFile)
            reloadConfiguration()
          } catch {
            case scala.util.control.NonFatal(error) =>
              EventLogger.log("ERROR", s"Ошибка назначения defaultPolicy: ${error.getMessage}")
              println(s"Не удалось назначить политику по умолчанию: ${error.getMessage}")
          }
        case "0" => selected = false
        case _ =>
          EventLogger.log("WARN", "Введён неизвестный пункт меню политики")
          println("Неверный ввод. Введите 1, 2, 3, 4, 5 или 0.")
      }
    }
  }

  def generateDefaultPassword(): Unit = {
    if (!PolicySession.hasSnapshot) {
      println("Нет загруженной корректной конфигурации. Исправьте файлы и выполните перезагрузку.")
      return
    }

    val defaultName = PolicySession.currentDefault.get
    PolicySession.current.find(_._2.name == defaultName) match {
      case Some((_, policy)) =>
        EventLogger.log("INFO", s"Запрошена генерация по умолчанию: policy=${policy.name}")
        try {
          val length = readPasswordLength(policy)
          val password = policy.GeneratePassword(length)
          EventLogger.log("INFO", s"Выполнена генерация: policy=${policy.name}, length=${password.length}")
          println(s"Сгенерированный пароль: $password")
        } catch {
          case scala.util.control.NonFatal(error) =>
            EventLogger.log("ERROR", s"Ошибка генерации: policy=${policy.name}, error=${error.getMessage}")
            println(s"Ошибка генерации: ${error.getMessage}")
        }
      case None =>
        EventLogger.log("ERROR", s"Политика по умолчанию '$defaultName' отсутствует в активной конфигурации")
        println(s"Политика по умолчанию '$defaultName' не загружена.")
    }
  }

  def showSavedPolitics(): Unit = {
    val policies: List[(java.nio.file.Path, Option[PswrdPolitic])] = if (PolicySession.hasSnapshot) {
      PolicySession.current.map { case (path, policy) => (path, Some(policy)) }
    } else {
      listSavedPolicies().map(path => (path, None))
    }
    if (policies.isEmpty) {
      println("Сохранённых политик нет. Сначала создайте политику.")
      return
    }

    var browsing = true
    while (browsing) {
      println("\nСохранённые политики:")
      policies.zipWithIndex.foreach { indexedPolicy =>
        val path = indexedPolicy._1._1
        val cachedPolicy = indexedPolicy._1._2
        val index = indexedPolicy._2
        val policyName = cachedPolicy.map(_.name).getOrElse(path.getFileName.toString.stripSuffix(".properties"))
        println(s"${index + 1}. $policyName")
      }
      println("0. Назад")
      val choice = readInt("Выберите политику: ", 0, policies.length)
      if (choice == 0) browsing = false
      else {
        val (path, cachedPolicy) = policies(choice - 1)
        try showSelectedPolitic(path, cachedPolicy)
        catch {
          case scala.util.control.NonFatal(error) =>
            EventLogger.log("ERROR", s"Ошибка загрузки или валидации политики: ${error.getMessage}")
            println(s"Не удалось открыть политику: ${error.getMessage}")
        }
      }
    }
  }

  def runApplication(): Unit = {
    EventLogger.log("INFO", "Приложение запущено")
    reloadConfiguration()
    var running = true
    try {
      while (running) {
        println("\n1. Показать политики")
        println("2. Создать новую политику")
        println("3. Сгенерировать пароль по политике по умолчанию")
        println("4. Перезагрузить конфигурацию")
        println("0. Выход")
        readValue("Выберите действие: ").trim match {
          case "1" => showSavedPolitics()
          case "2" =>
            try CreatePolitic()
            catch {
              case scala.util.control.NonFatal(error) =>
                EventLogger.log("ERROR", s"Ошибка создания или валидации политики: ${error.getMessage}")
                println(s"Политику не удалось создать: ${error.getMessage}")
            }
          case "3" => generateDefaultPassword()
          case "4" => reloadConfiguration()
          case "0" => running = false
          case _ =>
            EventLogger.log("WARN", "Введён неизвестный пункт главного меню")
            println("Неверный ввод. Введите 1, 2, 3, 4 или 0.")
        }
      }
    } catch {
      case scala.util.control.NonFatal(error) =>
        EventLogger.log("ERROR", s"Внутренняя ошибка приложения: ${error.getMessage}")
        println(s"Внутренняя ошибка: ${error.getMessage}")
    } finally {
      EventLogger.log("INFO", "Приложение завершено")
    }
  }

object Main extends App {
  runApplication()
}