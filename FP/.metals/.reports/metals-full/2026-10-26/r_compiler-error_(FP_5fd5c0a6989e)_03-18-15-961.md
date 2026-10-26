error id: AC93B7226940AC9E7503E73370A137AD
file:///C:/Users/fedos/Documents/GItHub/PPFP2026/FP/Lab0/lab0/src/main/scala/Main.scala
### java.lang.UnsupportedOperationException: openForMutations

occurred in the presentation compiler.



action parameters:
offset: 1594
uri: file:///C:/Users/fedos/Documents/GItHub/PPFP2026/FP/Lab0/lab0/src/main/scala/Main.scala
text:
```scala
object EventLogger {
  import java.nio.charset.StandardCharsets
  import java. nio.file.{Files, Path, Paths, StandardOpenOption}
  import java.time.LocalDateTime
  import java.time.format.DateTimeFormatter
  import scala.util.control.NonFatal

  private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private lazy val logFile : Path = {
    val workingDirectory = Paths.get("").toAbsolutePath
    val localLogs = workingDirectory.resolve("Logs")
    if(Files.isDirectory(localLogs)) localLogs.resolve("password-generator.log")
    else workingDirectory.resolve(Paths.get("Lab0", "lab0", "Logs", "password-generator.log"))
  }
  def log(level: String, messege: String) : Unit = {
    val entry = s"${LocalDateTime.now().format(timestampFormat)} $level $messege${System.lineSeparator()}"
    try {
      Files.createDirectories(logFile.getParent)
      Files.writeString(
        logFile, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
      )
    }
    catch {
      case NonFatal(error) => System.err.println(s"Не удалось записать журнал: ${error.getMessage}")
    } 
  }
}

object PolicySession {
  private var shapshot = List.empty[(java.nio.file.Path, PswrdPolitic)]
}

enum SymbolsGroup(val x: Array[Char]) {
  case LowercaseLatinLetters extends SymbolsGroup(('a' to 'z').toArray)
  case UppercaseLatinLetters extends SymbolsGroup(('A' to 'Z').toArray)
  case Numbers extends SymbolsGroup(('0' to '9').toArray)
  case SpecialSymbols extends SymbolsGroup(
    A@@
  )
}

class PswrdPolitic(
  var name: String,
  var description : String,
  var MAX_LENGTH : Int,
  var MIN_LENGTH : Int,
  var DEFAULT_LENGTH : Int,
  var AllowedSymbolsGroups : Array[(Sy)]
)
```


presentation compiler configuration:
Scala version: 3.8.3-bin-nonbootstrapped
Classpath:
<WORKSPACE>\.scala-build\FP_d5c0a6989e\classes\main [exists ], <HOME>\AppData\Local\Coursier\cache\v1\https\repo1.maven.org\maven2\org\scala-lang\scala3-library_3\3.8.3\scala3-library_3-3.8.3.jar [exists ], <HOME>\AppData\Local\Coursier\cache\v1\https\repo1.maven.org\maven2\org\scala-lang\scala-library\3.8.3\scala-library-3.8.3.jar [exists ], <HOME>\AppData\Local\Coursier\cache\v1\https\repo1.maven.org\maven2\com\sourcegraph\semanticdb-javac\0.10.0\semanticdb-javac-0.10.0.jar [exists ], <WORKSPACE>\.scala-build\FP_d5c0a6989e\classes\main\META-INF\best-effort [missing ]
Options:
-Xsemanticdb -sourceroot <WORKSPACE> -Ywith-best-effort-tasty




#### Error stacktrace:

```
dotty.tools.package$.unsupported(package.scala:10)
	dotty.tools.dotc.core.Scopes$Scope.openForMutations(Scopes.scala:187)
	dotty.tools.dotc.core.SymDenotations$ClassDenotation.enter(SymDenotations.scala:2121)
	dotty.tools.dotc.core.Definitions.withSpecMethods$$anonfun$1$$anonfun$1(Definitions.scala:1853)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.BitmapIndexedSetNode.foreach(HashSet.scala:955)
	scala.collection.immutable.HashSet.foreach(HashSet.scala:187)
	dotty.tools.dotc.core.Definitions.withSpecMethods$$anonfun$1(Definitions.scala:1852)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:15)
	scala.runtime.function.JProcedure1.apply(JProcedure1.java:10)
	scala.collection.immutable.List.foreach(List.scala:327)
	dotty.tools.dotc.core.Definitions.withSpecMethods(Definitions.scala:1852)
	dotty.tools.dotc.core.Definitions.Tuple2(Definitions.scala:1857)
	dotty.tools.pc.completions.SingletonCompletions$.contribute$$anonfun$1(SingletonCompletions.scala:33)
	scala.collection.immutable.List.flatMap(List.scala:287)
	dotty.tools.pc.completions.SingletonCompletions$.contribute(SingletonCompletions.scala:25)
	dotty.tools.pc.completions.Completions.$anonfun$12(Completions.scala:551)
	scala.Option.map(Option.scala:244)
	dotty.tools.pc.completions.Completions.advancedCompletions(Completions.scala:551)
	dotty.tools.pc.completions.Completions.completions(Completions.scala:138)
	dotty.tools.pc.completions.CompletionProvider.completions(CompletionProvider.scala:148)
	dotty.tools.pc.ScalaPresentationCompiler.complete$$anonfun$1(ScalaPresentationCompiler.scala:194)
	scala.meta.internal.pc.CompilerAccess.withSharedCompiler(CompilerAccess.scala:149)
	scala.meta.internal.pc.CompilerAccess.$anonfun$1(CompilerAccess.scala:93)
	scala.meta.internal.pc.CompilerAccess.onCompilerJobQueue$$anonfun$1(CompilerAccess.scala:210)
	scala.meta.internal.pc.CompilerJobQueue$Job.run(CompilerJobQueue.scala:153)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:842)
```
#### Short summary: 

java.lang.UnsupportedOperationException: openForMutations