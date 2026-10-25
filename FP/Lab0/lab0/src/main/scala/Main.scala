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
  def GeneratePassword(): String = {
    import scala.util.Random
    require(MIN_LENGTH >= 1 && MAX_LENGTH >= MIN_LENGTH, "Некорректные границы длины")
    require(DEFAULT_LENGTH >= MIN_LENGTH && DEFAULT_LENGTH <= MAX_LENGTH,
    "DEFAULT_LENGTH должен быть в допустимых пределах")
    require(MaxRepeatSymVal >= 1, "MaxRepeatSymVal должен быть не меньше 1")
    require(AllowedSymbolGroups.nonEmpty, "Не задано ни одной группы символов")
    val length = DEFAULT_LENGTH
    val blocked = BlockedSymbols.toSet
    val groups = AllowedSymbolGroups.toList.map { case (group, minimum) =>
      require(minimum >= 0, s"Отрицательный минимум для группы $group")
      val available = group.x.filterNot(ch => blocked.contains(ch)).distinct.toList
      require(minimum == 0 || available.nonEmpty,
      s"В группе $group не осталось разрешённых символов")
      (available, minimum)
      }

    val pool = (groups.flatMap(_._1) ++ (if (AllowSpace && !blocked.contains(' ')) List(' ') else Nil)).distinct
    val requiredCount = groups.map(_._2).sum
    require(pool.nonEmpty, "Нет доступных символов для генерации")
    require(requiredCount <= length, "Обязательных символов больше, чем длина пароля")
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

      val consecutiveLimitMet =
      !password.sliding(MaxRepeatSymVal + 1).exists { window =>
      window.length == MaxRepeatSymVal + 1 && window.forall(_ == window.head)
      }

        groupRequirementsMet &&
          noForbiddenSubstring &&
          repeatsAllowed &&
          consecutiveLimitMet
      }

      var attempts = 0
      while (attempts < 10000) {
        val required = groups.flatMap { case (chars, minimum) =>
          List.fill(minimum)(chars(Random.nextInt(chars.length)))
        }

        val uniqueRequired = AllowRepeat || required.distinct.length == required.length
        val remainingPool =
          if (AllowRepeat) pool
          else pool.filterNot(required.contains)

        val fillerCount = length - required.length
        val filler =
          if (AllowRepeat) List.fill(fillerCount)(pool(Random.nextInt(pool.length)))
          else Random.shuffle(remainingPool).take(fillerCount)

        if (uniqueRequired && filler.length == fillerCount) {
          val candidate = Random.shuffle(required ++ filler).mkString
          if (isValid(candidate)) return candidate
        }

        attempts += 1
      }

      throw new IllegalStateException(
        s"Не удалось сгенерировать пароль по политике '$name'; проверьте её ограничения"
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
        case _ => println(s"Введите целое число от $minimum до $maximum.")
      }
    }
    result.get
  }

  def readBoolean(prompt: String): Boolean = {
    while (true) {
      readValue(prompt).trim.toLowerCase match {
        case "true" => return true
        case "false" => return false
        case _ => println("Введите true или false.")
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

    def requiredProperty(key: String): String =
      Option(properties.getProperty(key)).getOrElse(
        throw new IllegalArgumentException(s"В файле ${configFile.getFileName} отсутствует параметр '$key'")
      )

    def intProperty(key: String): Int =
      requiredProperty(key).toIntOption.getOrElse(
        throw new IllegalArgumentException(s"Параметр '$key' в ${configFile.getFileName} должен быть целым числом")
      )

    def booleanProperty(key: String): Boolean = requiredProperty(key).toLowerCase match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException(s"Параметр '$key' должен быть true или false")
    }

    val groupCount = intProperty("allowedGroupCount")
    require(groupCount > 0, "Политика должна содержать хотя бы одну группу символов")
    val allowedGroups = Array.tabulate(groupCount) { index =>
      val groupName = requiredProperty(s"allowedGroup.$index.name")
      val group = SymbolsGroup.values.find(_.toString == groupName).getOrElse(
        throw new IllegalArgumentException(s"Неизвестная группа символов '$groupName'")
      )
      group -> intProperty(s"allowedGroup.$index.minimum")
    }

    val blockedStringCount = intProperty("blockedStringCount")
    require(blockedStringCount >= 0, "Количество запрещённых подстрок не может быть отрицательным")
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
        if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".properties")) {
          files += path
        }
      }
      files.result().sortBy(_.getFileName.toString)
    } finally stream.close()
  }

  def validatePolicy(policy: PswrdPolitic): Unit = {
    require(policy.MIN_LENGTH >= 1, "Минимальная длина должна быть не меньше 1")
    require(policy.MAX_LENGTH >= policy.MIN_LENGTH, "Максимальная длина меньше минимальной")
    require(policy.DEFAULT_LENGTH >= policy.MIN_LENGTH && policy.DEFAULT_LENGTH <= policy.MAX_LENGTH,
      "Длина по умолчанию должна находиться в допустимых пределах")
    require(policy.AllowedSymbolGroups.nonEmpty, "Нужно выбрать хотя бы одну группу символов")
    require(policy.AllowedSymbolGroups.map(_._1).distinct.length == policy.AllowedSymbolGroups.length,
      "Группа символов указана несколько раз")
    require(policy.MaxRepeatSymVal >= 1 && policy.MaxRepeatSymVal <= policy.MAX_LENGTH,
      "Максимум одинаковых символов подряд должен быть от 1 до максимальной длины")

    val blocked = policy.BlockedSymbols.toSet
    policy.AllowedSymbolGroups.foreach { case (group, minimum) =>
      require(minimum >= 0, s"Минимум для группы $group не может быть отрицательным")
      val available = group.x.distinct.count(ch => !blocked.contains(ch))
      require(available > 0, s"В группе $group не осталось разрешённых символов")
      require(minimum <= available, s"Для группы $group требуется $minimum символов, но доступно только $available")
    }
    require(policy.AllowedSymbolGroups.map(_._2).sum <= policy.DEFAULT_LENGTH,
      "Сумма обязательных символов превышает длину по умолчанию")

    val pool = (policy.AllowedSymbolGroups.flatMap { case (group, _) =>
      group.x.filterNot(blocked.contains)
    } ++ (if (policy.AllowSpace && !blocked.contains(' ')) Array(' ') else Array.emptyCharArray)).distinct
    require(pool.nonEmpty, "После исключения запрещённых символов не осталось доступных символов")
    require(policy.AllowRepeat || pool.length >= policy.DEFAULT_LENGTH,
      "При запрещённых повторах недостаточно символов для длины по умолчанию")
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
  }

  def showPoliticInfo(policy: PswrdPolitic): Unit = {
    println(s"\nПолитика: ${policy.name}")
    println(s"Описание: ${policy.description}")
    println(s"Длина: ${policy.MIN_LENGTH}–${policy.MAX_LENGTH} (по умолчанию ${policy.DEFAULT_LENGTH})")
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

  def showSelectedPolitic(configFile: java.nio.file.Path): Unit = {
    var policy = loadPolitic(configFile)
    var selected = true
    while (selected) {
      println(s"\nПолитика: ${policy.name}")
      println("1. Использовать политику")
      println("2. Показать информацию")
      println("3. Редактировать политику")
      println("0. Назад")
      readValue("Выберите действие: ").trim match {
        case "1" =>
          try println(s"Сгенерированный пароль: ${policy.GeneratePassword()}")
          catch { case error: IllegalArgumentException => println(s"Ошибка генерации: ${error.getMessage}") }
        case "2" => showPoliticInfo(policy)
        case "3" =>
          try policy = editPolitic(policy, configFile)
          catch { case error: IllegalArgumentException => println(s"Изменение не сохранено: ${error.getMessage}") }
        case "0" => selected = false
        case _ => println("Неверный ввод. Введите 1, 2, 3 или 0.")
      }
    }
  }

  def showSavedPolitics(): Unit = {
    val configFiles = listSavedPolicies()
    if (configFiles.isEmpty) {
      println("Сохранённых политик нет. Сначала создайте политику.")
      return
    }

    var browsing = true
    while (browsing) {
      println("\nСохранённые политики:")
      configFiles.zipWithIndex.foreach { case (path, index) =>
        println(s"${index + 1}. ${path.getFileName.toString.stripSuffix(".properties")}")
      }
      println("0. Назад")
      val choice = readInt("Выберите политику: ", 0, configFiles.length)
      if (choice == 0) browsing = false
      else {
        try showSelectedPolitic(configFiles(choice - 1))
        catch { case error: IllegalArgumentException => println(s"Не удалось открыть политику: ${error.getMessage}") }
      }
    }
  }

  def runApplication(): Unit = {
    var running = true
    while (running) {
      println("\n1. Show politics")
      println("2. Создать новую политику")
      println("0. Выход")
      readValue("Выберите действие: ").trim match {
        case "1" => showSavedPolitics()
        case "2" =>
          try CreatePolitic()
          catch { case error: IllegalArgumentException => println(s"Политику не удалось создать: ${error.getMessage}") }
        case "0" => running = false
        case _ => println("Неверный ввод. Введите 1, 2 или 0.")
      }
    }
  }

object Main extends App {
  runApplication()
}