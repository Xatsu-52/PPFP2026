object EventLogger {
  import java.nio.charset.StandardCharsets
  import java.nio.file.{Files, Path, StandardOpenOption}
  import java.time.{LocalDate, LocalDateTime}
  import java.time.format.DateTimeFormatter
  import scala.util.control.NonFatal

  private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private def logFile(date: LocalDate): Path = {
    val logsDirectory = ProjectPaths.findDirectory(
      "Logs",
      List("Lab1/lab1/Logs", "lab1/Logs")
    )
    val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    logsDirectory.resolve(s"password-generator-$dateString.log")
  }

  def log(level: String, message: String): Unit = {
    val timestamp = LocalDateTime.now()
    val entry = s"${timestamp.format(timestampFormat)} $level $message${System.lineSeparator()}"
    val currentLogFile = logFile(timestamp.toLocalDate)

    try {
      Files.createDirectories(currentLogFile.getParent)
      Files.writeString(
        currentLogFile,
        entry,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
      )
    } catch {
      case NonFatal(error) =>
        val reason = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
        System.err.println(s"Не удалось записать журнал: $reason")
    }
  }
}
