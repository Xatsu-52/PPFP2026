final case class PasswordCheck(description: String, passed: Boolean)

final case class PasswordCheckResult(policyName: String, checks: List[PasswordCheck]) {
  def passed: Boolean = checks.forall(_.passed)

  def failedChecks: List[PasswordCheck] = checks.filterNot(_.passed)
}

object PasswordService {
  import scala.util.Random

  private val MaximumAttempts = 1000

  def generate(policy: PasswordPolicy, requestedLength: Int): Either[AppError, String] = {
    val requiredCount = policy.allowedGroups.map(_._2).sum
    val availableCount = policy.availableCharacters.length

    if (requestedLength < policy.minimumLength || requestedLength > policy.maximumLength) {
      Left(AppError.LengthNotAllowed(
        policy.name,
        requestedLength,
        policy.minimumLength,
        policy.maximumLength
      ))
    } else if (requiredCount > requestedLength) {
      Left(AppError.RequiredCharactersAboveLength(
        policy.name,
        requiredCount,
        requestedLength
      ))
    } else if (!policy.allowRepeat && availableCount < requestedLength) {
      Left(AppError.NotEnoughCharactersForLength(
        policy.name,
        availableCount,
        requestedLength
      ))
    } else {
      PolicyValidator
        .validate(policy)
        .left
        .map(AppError.InvalidPolicy.apply)
        .flatMap { validPolicy =>
          makePassword(validPolicy, requestedLength).toRight(
            AppError.PasswordGenerationFailed(
              validPolicy.name,
              "не удалось подобрать пароль, удовлетворяющий всем ограничениям"
            )
          )
        }
    }
  }

  def check(policy: PasswordPolicy, password: String): Either[AppError, PasswordCheckResult] = {
    PolicyValidator
      .validate(policy)
      .left
      .map(AppError.InvalidPolicy.apply)
      .map { validPolicy =>
        val allowedCharacters = validPolicy.availableCharacters.toSet

        val lengthCheck = PasswordCheck(
          s"Длина от ${validPolicy.minimumLength} до ${validPolicy.maximumLength}",
          password.length >= validPolicy.minimumLength && password.length <= validPolicy.maximumLength
        )

        val allowedCharactersCheck = PasswordCheck(
          "Используются только разрешённые символы",
          password.forall(character => allowedCharacters.contains(character))
        )

        val groupChecks = validPolicy.allowedGroups.map { case (group, minimum) =>
          val groupCharacters = validPolicy.allowedCharactersFor(group).toSet
          val actual = password.count(character => groupCharacters.contains(character))
          PasswordCheck(
            s"Группа $group: найдено $actual, требуется минимум $minimum",
            actual >= minimum
          )
        }

        val blockedStringsCheck = PasswordCheck(
          "Запрещённые подстроки",
          !validPolicy.blockedStrings.exists(part => part.nonEmpty && password.contains(part))
        )

        val repeatCheck = PasswordCheck(
          "Повторение символов соответствует политике",
          validPolicy.allowRepeat || password.distinct.length == password.length
        )

        val runCheck = PasswordCheck(
          s"Не более ${validPolicy.maximumRepeat} одинаковых символов подряд",
          longestRun(password) <= validPolicy.maximumRepeat
        )

        PasswordCheckResult(
          validPolicy.name,
          List(lengthCheck, allowedCharactersCheck) ++ groupChecks ++
            List(blockedStringsCheck, repeatCheck, runCheck)
        )
      }
  }

  def passwordMatches(policy: PasswordPolicy, password: String): Boolean = {
    check(policy, password).fold(_ => false, result => result.passed)
  }

  private def requiredCharacters(policy: PasswordPolicy): List[Char] = {
    policy.allowedGroups.flatMap { case (group, minimum) =>
      val characters = policy.allowedCharactersFor(group)

      if (characters.isEmpty) {
        Nil
      } else {
        List.fill(minimum)(characters(Random.nextInt(characters.length)))
      }
    }
  }

  private def makePassword(policy: PasswordPolicy, length: Int): Option[String] = {
    val required = requiredCharacters(policy)
    val pool = policy.availableCharacters

    def tryAttempt(attempt: Int): Option[String] = {
      if (attempt >= MaximumAttempts) {
        None
      } else {
        makeCandidate(policy, length, required, pool) match {
          case Some(password) if passwordMatches(policy, password) => Some(password)
          case _ => tryAttempt(attempt + 1)
        }
      }
    }

    tryAttempt(0)
  }

  private def makeCandidate(
    policy: PasswordPolicy,
    length: Int,
    required: List[Char],
    pool: List[Char]
  ): Option[String] = {
    if (required.length > length) {
      None
    } else {
      val extraCount = length - required.length
      val used = required.toSet

      fillCharacters(policy, pool, used, extraCount).map { extra =>
        Random.shuffle(required ++ extra).mkString
      }
    }
  }

  private def fillCharacters(
    policy: PasswordPolicy,
    pool: List[Char],
    alreadyUsed: Set[Char],
    count: Int
  ): Option[List[Char]] = {
    def loop(result: List[Char], used: Set[Char], remaining: Int): Option[List[Char]] = {
      if (remaining == 0) {
        Some(result)
      } else {
        val candidates =
          if (policy.allowRepeat) pool
          else pool.filterNot(used.contains)

        if (candidates.isEmpty) {
          None
        } else {
          val character = candidates(Random.nextInt(candidates.length))
          loop(result :+ character, used + character, remaining - 1)
        }
      }
    }

    loop(Nil, alreadyUsed, count)
  }

  private def longestRun(password: String): Int = {
    var previous: Option[Char] = None
    var currentRun = 0
    var longest = 0

    password.foreach { character =>
      if (previous.contains(character)) {
        currentRun = currentRun + 1
      } else {
        currentRun = 1
      }

      previous = Some(character)
      longest = math.max(longest, currentRun)
    }

    longest
  }
}
