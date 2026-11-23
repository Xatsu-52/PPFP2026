import java.nio.file.Path

// Подробные причины, по которым политика может быть некорректной.
sealed trait PolicyProblem

object PolicyProblem {
  final case class MinimumLengthLessThanOne(value: Int) extends PolicyProblem
  final case class MaximumLengthLessThanMinimum(maximum: Int, minimum: Int) extends PolicyProblem
  final case class DefaultLengthOutsideRange(value: Int, minimum: Int, maximum: Int) extends PolicyProblem
  case object NoCharacterGroups extends PolicyProblem
  final case class DuplicateGroup(group: SymbolsGroup) extends PolicyProblem
  final case class NegativeGroupMinimum(group: SymbolsGroup, minimum: Int) extends PolicyProblem
  final case class MandatoryCountAboveMaximum(total: Long, maximum: Int) extends PolicyProblem
  final case class MandatoryCountAboveDefault(total: Long, defaultLength: Int) extends PolicyProblem
  final case class AllowedCharacterBlocked(characters: List[Char]) extends PolicyProblem
  case object SpaceAllowedAndBlocked extends PolicyProblem
  final case class EmptyGroupAfterBlocking(group: SymbolsGroup) extends PolicyProblem
  final case class GroupMinimumAboveAvailable(group: SymbolsGroup, minimum: Int, available: Int) extends PolicyProblem
  case object NoAvailableCharacters extends PolicyProblem
  final case class NotEnoughCharactersForNoRepeat(available: Int, defaultLength: Int) extends PolicyProblem
  final case class MaximumRepeatLessThanOne(value: Int) extends PolicyProblem
}

final case class PolicyValidationError(policyName: String, problem: PolicyProblem)

// Все ожидаемые ошибки приложения. У каждой причины есть свой вариант типа.
sealed trait AppError

object AppError {
  final case class InvalidPolicy(error: PolicyValidationError) extends AppError
  final case class InvalidPolicyName(name: String) extends AppError
  final case class PolicyAlreadyExists(name: String, file: Path) extends AppError
  final case class PolicyNotFound(name: String) extends AppError
  final case class DefaultPolicyNotLoaded(name: String) extends AppError
  final case class LengthNotAllowed(policyName: String, length: Int, minimum: Int, maximum: Int) extends AppError
  final case class RequiredCharactersAboveLength(policyName: String, required: Int, length: Int) extends AppError
  final case class NotEnoughCharactersForLength(policyName: String, available: Int, length: Int) extends AppError
  final case class PasswordGenerationFailed(policyName: String, reason: String) extends AppError

  final case class ConfigurationDirectoryMissing(path: Path) extends AppError
  final case class ConfigurationFileMissing(path: Path) extends AppError
  final case class NoPoliciesFound(path: Path) extends AppError
  final case class InvalidConfiguration(file: String, parameter: String, reason: String) extends AppError
  final case class IoFailure(operation: String, reason: String) extends AppError

  final case class UnknownMenuChoice(input: String) extends AppError
  final case class NotAnInteger(input: String, minimum: Int, maximum: Int) extends AppError
  final case class IntegerOutOfRange(value: Int, minimum: Int, maximum: Int) extends AppError
  final case class NotABoolean(input: String) extends AppError
  final case class InvalidGroupSelection(input: String, maximum: Int) extends AppError
  case object EmptyGroupSelection extends AppError

  final case class Unexpected(reason: String) extends AppError
}
