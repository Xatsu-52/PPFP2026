import java.nio.file.{Files, Path, Paths}

object ProjectPaths {
  // Ищем папку рядом с программой, а если её нет — в папке проекта.
  def findDirectory(name: String, fallbackPaths: List[String]): Path = {
    val currentDirectory = Paths.get("").toAbsolutePath
    val candidates = (name :: fallbackPaths).map(path => currentDirectory.resolve(path))

    candidates.find(path => Files.isDirectory(path)).getOrElse(currentDirectory.resolve(name))
  }
}
