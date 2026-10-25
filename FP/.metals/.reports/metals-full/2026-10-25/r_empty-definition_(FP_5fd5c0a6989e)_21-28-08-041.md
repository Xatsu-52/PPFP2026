error id: file:///C:/Users/fedos/Documents/GItHub/PPFP2026/FP/Lab0/lab0/src/main/scala/Main.scala:scala/Int#
file:///C:/Users/fedos/Documents/GItHub/PPFP2026/FP/Lab0/lab0/src/main/scala/Main.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol scala/Int#
empty definition using fallback
non-local guesses:

offset: 590
uri: file:///C:/Users/fedos/Documents/GItHub/PPFP2026/FP/Lab0/lab0/src/main/scala/Main.scala
text:
```scala
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
  var AllowedSymbolGroups : Array[Tuple[SymbolsGroup, I@@nt]], //Разрешенная группа символов и минимальное количество символов каждой группы
  var BlockedSymbols : Array[Char],
  var AllowRepeat : Boolean,
  var MaxRepeatSymVal : Int,
  var AllowSpace : Boolean,
  var BlockedStrings : Array[String],
)
{
  
}

object Main extends App {
  PswrdPolitic("A", "B", 4, 2, 4, (Array(SymbolsGroup.LowercaseLatinLetters)), Array(',', '.'), true, 2, false, "");

}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 