enum SymbolsGroup(val characters: List[Char], val title: String) {
  case LowercaseLatinLetters
    extends SymbolsGroup(('a' to 'z').toList, "строчные латинские буквы")

  case UppercaseLatinLetters
    extends SymbolsGroup(('A' to 'Z').toList, "прописные латинские буквы")

  case Numbers
    extends SymbolsGroup(('0' to '9').toList, "цифры")

  case SpecialSymbols
    extends SymbolsGroup(
      List('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '-', '+', '='),
      "специальные символы"
    )
}

object SymbolsGroup {
  def fromName(name: String): Option[SymbolsGroup] = {
    values.find(group => group.toString == name)
  }
}

// Политика не меняется после создания: при редактировании создаётся копия.
final case class PasswordPolicy(
  name: String,
  description: String,
  minimumLength: Int,
  maximumLength: Int,
  defaultLength: Int,
  allowedGroups: List[(SymbolsGroup, Int)],
  blockedSymbols: List[Char],
  allowRepeat: Boolean,
  maximumRepeat: Int,
  allowSpace: Boolean,
  blockedStrings: List[String]
) {
  def allowedCharactersFor(group: SymbolsGroup): List[Char] = {
    group.characters
      .filterNot(character => blockedSymbols.contains(character))
      .distinct
  }

  def availableCharacters: List[Char] = {
    val groupCharacters = allowedGroups.flatMap { case (group, _) =>
      allowedCharactersFor(group)
    }
    val spaceCharacters =
      if (allowSpace && !blockedSymbols.contains(' ')) List(' ')
      else Nil

    (groupCharacters ++ spaceCharacters).distinct
  }
}
