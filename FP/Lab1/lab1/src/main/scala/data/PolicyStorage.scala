import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.Properties
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object PolicyStorage {
  def configDirectory: Path = {
    ProjectPaths.findDirectory(
      "PswConfigs",
      List("Lab1/lab1/PswConfigs", "lab1/PswConfigs")
    )
  }

  def listPolicyFiles(): Either[AppError, List[Path]] = {
    val directory = configDirectory

    if (!Files.isDirectory(directory)) {
      Left(AppError.ConfigurationDirectoryMissing(directory))
    } else {
      try {
        val stream = Files.list(directory)
        try {
          val files = stream
            .iterator()
            .asScala
            .toList
            .filter(path => Files.isRegularFile(path))
            .filter(path => path.getFileName.toString.endsWith(".properties"))
            .filter(path => path.getFileName.toString != "application.properties")
            .sortBy(path => path.getFileName.toString)

          Right(files)
        } finally {
          stream.close()
        }
      } catch {
        case NonFatal(error) =>
          Left(AppError.IoFailure("чтение списка политик", messageOf(error)))
      }
    }
  }

  def loadPolicy(path: Path): Either[AppError, PasswordPolicy] = {
    if (!Files.isRegularFile(path)) {
      Left(AppError.ConfigurationFileMissing(path))
    } else {
      loadProperties(path).flatMap { properties =>
        val fileName = path.getFileName.toString

        for {
          name <- requiredProperty(properties, fileName, "name")
          description <- requiredProperty(properties, fileName, "description")
          minimumLength <- integerProperty(properties, fileName, "minimumLength")
          maximumLength <- integerProperty(properties, fileName, "maximumLength")
          defaultLength <- integerProperty(properties, fileName, "defaultLength")
          groupCount <- integerProperty(properties, fileName, "allowedGroupCount")
          _ <- if (groupCount < 0) {
            Left(AppError.InvalidConfiguration(
              fileName,
              "allowedGroupCount",
              "значение не может быть отрицательным."
            ))
          } else {
            Right(())
          }
          groups <- loadGroups(properties, fileName, groupCount)
          blockedSymbolsText <- requiredProperty(properties, fileName, "blockedSymbols")
          allowRepeat <- booleanProperty(properties, fileName, "allowRepeat")
          maximumRepeat <- integerProperty(properties, fileName, "maxRepeatSymVal")
          allowSpace <- booleanProperty(properties, fileName, "allowSpace")
          blockedStringCount <- integerProperty(properties, fileName, "blockedStringCount")
          _ <- if (blockedStringCount < 0) {
            Left(AppError.InvalidConfiguration(
              fileName,
              "blockedStringCount",
              "значение не может быть отрицательным."
            ))
          } else {
            Right(())
          }
          blockedStrings <- loadBlockedStrings(properties, fileName, blockedStringCount)
          policy = PasswordPolicy(
            name,
            description,
            minimumLength,
            maximumLength,
            defaultLength,
            groups,
            blockedSymbolsText.toList,
            allowRepeat,
            maximumRepeat,
            allowSpace,
            blockedStrings
          )
          validPolicy <- PolicyValidator
            .validate(policy)
            .left
            .map(error => AppError.InvalidPolicy(error))
        } yield {
          EventLogger.log("INFO", s"Конфигурация загружена и проверена: policy=${validPolicy.name}")
          validPolicy
        }
      }
    }
  }

  def loadAllPolicies(): Either[AppError, List[(Path, PasswordPolicy)]] = {
    for {
      files <- listPolicyFiles()
      _ <- if (files.isEmpty) {
        Left(AppError.NoPoliciesFound(configDirectory))
      } else {
        Right(())
      }
      policies <- sequence(files.map(loadPolicy))
      _ <- if (policies.exists(_.name == PolicySession.DefaultPolicyName)) {
        Right(())
      } else {
        Left(AppError.DefaultPolicyNotLoaded(PolicySession.DefaultPolicyName))
      }
    } yield files.zip(policies)
  }

  def createPolicy(policy: PasswordPolicy): Either[AppError, Path] = {
    try {
      val path = configDirectory.resolve(s"${policy.name}.properties")

      if (Files.exists(path)) {
        Left(AppError.PolicyAlreadyExists(policy.name, path))
      } else {
        savePolicy(policy, path).map(_ => path)
      }
    } catch {
      case NonFatal(error) =>
        Left(AppError.IoFailure("создание политики", messageOf(error)))
    }
  }

  def savePolicy(policy: PasswordPolicy, path: Path): Either[AppError, Unit] = {
    try {
      Files.createDirectories(path.getParent)
      val output = Files.newOutputStream(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
      )

      try {
        toProperties(policy).store(output, s"Password policy: ${policy.name}")
      } finally {
        output.close()
      }

      EventLogger.log("INFO", s"Политика сохранена: policy=${policy.name}")
      Right(())
    } catch {
      case NonFatal(error) =>
        Left(AppError.IoFailure("сохранение политики", messageOf(error)))
    }
  }

  private def loadProperties(path: Path): Either[AppError, Properties] = {
    try {
      val properties = new Properties()
      val input = Files.newInputStream(path)

      try {
        properties.load(input)
      } finally {
        input.close()
      }

      Right(properties)
    } catch {
      case NonFatal(error) =>
        Left(AppError.IoFailure(s"чтение файла ${path.getFileName}", messageOf(error)))
    }
  }

  private def loadGroups(
    properties: Properties,
    fileName: String,
    count: Int
  ): Either[AppError, List[(SymbolsGroup, Int)]] = {
    val results = (0 until count).toList.map { index =>
      val nameKey = s"allowedGroup.$index.name"
      val minimumKey = s"allowedGroup.$index.minimum"
      val enabledKey = s"allowedGroup.$index.enabled"

      for {
        groupName <- requiredProperty(properties, fileName, nameKey)
        group <- SymbolsGroup
          .fromName(groupName)
          .toRight(AppError.InvalidConfiguration(
            fileName,
            nameKey,
            s"неизвестная группа '$groupName'."
          ))
        minimum <- integerProperty(properties, fileName, minimumKey)
        _ <- if (minimum < 0) {
          Left(AppError.InvalidConfiguration(
            fileName,
            minimumKey,
            "значение не может быть отрицательным."
          ))
        } else {
          Right(())
        }
        enabled <- optionalBooleanProperty(properties, fileName, enabledKey, default = true)
        _ <- if (!enabled && minimum > 0) {
          Left(AppError.InvalidConfiguration(
            fileName,
            minimumKey,
            "минимум задан для выключенной группы."
          ))
        } else {
          Right(())
        }
      } yield if (enabled) Some(group -> minimum) else None
    }

    sequence(results).map(_.flatten)
  }

  private def loadBlockedStrings(
    properties: Properties,
    fileName: String,
    count: Int
  ): Either[AppError, List[String]] = {
    val results = (0 until count).toList.map { index =>
      requiredProperty(properties, fileName, s"blockedString.$index")
    }

    sequence(results)
  }

  private def requiredProperty(
    properties: Properties,
    fileName: String,
    key: String
  ): Either[AppError, String] = {
    Option(properties.getProperty(key)).toRight(
      AppError.InvalidConfiguration(fileName, key, "значение отсутствует в файле.")
    )
  }

  private def integerProperty(
    properties: Properties,
    fileName: String,
    key: String
  ): Either[AppError, Int] = {
    requiredProperty(properties, fileName, key).flatMap { value =>
      value.toIntOption match {
        case Some(number) => Right(number)
        case None =>
          Left(AppError.InvalidConfiguration(
            fileName,
            key,
            "значение должно быть целым числом."
          ))
      }
    }
  }

  private def booleanProperty(
    properties: Properties,
    fileName: String,
    key: String
  ): Either[AppError, Boolean] = {
    requiredProperty(properties, fileName, key).flatMap { value =>
      parseBoolean(value, fileName, key)
    }
  }

  private def optionalBooleanProperty(
    properties: Properties,
    fileName: String,
    key: String,
    default: Boolean
  ): Either[AppError, Boolean] = {
    Option(properties.getProperty(key)) match {
      case Some(value) => parseBoolean(value, fileName, key)
      case None => Right(default)
    }
  }

  private def parseBoolean(
    value: String,
    fileName: String,
    key: String
  ): Either[AppError, Boolean] = {
    value.toLowerCase match {
      case "true" => Right(true)
      case "false" => Right(false)
      case _ =>
        Left(AppError.InvalidConfiguration(
          fileName,
          key,
          "значение должно быть true или false."
        ))
    }
  }

  private def toProperties(policy: PasswordPolicy): Properties = {
    val properties = new Properties()
    properties.setProperty("name", policy.name)
    properties.setProperty("description", policy.description)
    properties.setProperty("minimumLength", policy.minimumLength.toString)
    properties.setProperty("maximumLength", policy.maximumLength.toString)
    properties.setProperty("defaultLength", policy.defaultLength.toString)
    properties.setProperty("allowedGroupCount", policy.allowedGroups.length.toString)

    policy.allowedGroups.zipWithIndex.foreach { case ((group, minimum), index) =>
      properties.setProperty(s"allowedGroup.$index.name", group.toString)
      properties.setProperty(s"allowedGroup.$index.minimum", minimum.toString)
      properties.setProperty(s"allowedGroup.$index.enabled", "true")
    }

    properties.setProperty("blockedSymbols", policy.blockedSymbols.mkString)
    properties.setProperty("allowRepeat", policy.allowRepeat.toString)
    properties.setProperty("maxRepeatSymVal", policy.maximumRepeat.toString)
    properties.setProperty("allowSpace", policy.allowSpace.toString)
    properties.setProperty("blockedStringCount", policy.blockedStrings.length.toString)

    policy.blockedStrings.zipWithIndex.foreach { case (value, index) =>
      properties.setProperty(s"blockedString.$index", value)
    }

    properties
  }

  private def sequence[A](items: List[Either[AppError, A]]): Either[AppError, List[A]] = {
    items.foldLeft[Either[AppError, List[A]]](Right(Nil)) { (accumulator, item) =>
      for {
        values <- accumulator
        value <- item
      } yield values :+ value
    }
  }

  private def messageOf(error: Throwable): String = {
    Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
  }
}
