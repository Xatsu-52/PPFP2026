// Типизированные команды, которые получаются после разбора ввода пользователя.

sealed trait MainCommand

object MainCommand {
  case object ShowPolicies extends MainCommand
  case object CreatePolicy extends MainCommand
  case object GenerateDefaultPassword extends MainCommand
  case object ReloadConfiguration extends MainCommand
  case object Exit extends MainCommand
}

sealed trait PolicyCommand

object PolicyCommand {
  case object GeneratePassword extends PolicyCommand
  case object ShowInformation extends PolicyCommand
  case object EditPolicy extends PolicyCommand
  case object CheckPassword extends PolicyCommand
  case object Back extends PolicyCommand
}

sealed trait PolicyListCommand

object PolicyListCommand {
  final case class OpenPolicy(index: Int) extends PolicyListCommand
  case object Back extends PolicyListCommand
}

sealed trait PolicyEditCommand

object PolicyEditCommand {
  case object EditDescription extends PolicyEditCommand
  case object EditMinimumLength extends PolicyEditCommand
  case object EditMaximumLength extends PolicyEditCommand
  case object EditDefaultLength extends PolicyEditCommand
  case object EditGroups extends PolicyEditCommand
  case object EditBlockedSymbols extends PolicyEditCommand
  case object EditAllowRepeat extends PolicyEditCommand
  case object EditMaximumRepeat extends PolicyEditCommand
  case object EditAllowSpace extends PolicyEditCommand
  case object EditBlockedStrings extends PolicyEditCommand
  case object Back extends PolicyEditCommand
}

// Элемент меню хранит уже готовую команду, а не строку.
final case class MenuItem[A](title: String, command: A)

final case class Menu[A](
  title: String,
  items: List[MenuItem[A]],
  backTitle: String,
  backCommand: A
)
