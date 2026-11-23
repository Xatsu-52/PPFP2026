object PolicyValidator {
  def validate(policy: PasswordPolicy): Either[PolicyValidationError, PasswordPolicy] = {
    findProblem(policy) match {
      case Some(problem) => Left(PolicyValidationError(policy.name, problem))
      case None => Right(policy)
    }
  }

  private def findProblem(policy: PasswordPolicy): Option[PolicyProblem] = {
    if (policy.minimumLength < 1) {
      Some(PolicyProblem.MinimumLengthLessThanOne(policy.minimumLength))
    } else if (policy.maximumLength < policy.minimumLength) {
      Some(PolicyProblem.MaximumLengthLessThanMinimum(policy.maximumLength, policy.minimumLength))
    } else if (policy.defaultLength < policy.minimumLength || policy.defaultLength > policy.maximumLength) {
      Some(PolicyProblem.DefaultLengthOutsideRange(
        policy.defaultLength,
        policy.minimumLength,
        policy.maximumLength
      ))
    } else if (policy.allowedGroups.isEmpty) {
      Some(PolicyProblem.NoCharacterGroups)
    } else if (policy.allowedGroups.map(_._1).distinct.length != policy.allowedGroups.length) {
      val duplicate = policy.allowedGroups
        .groupBy(_._1)
        .find { case (_, groups) => groups.length > 1 }
        .map(_._1)

      duplicate.map(PolicyProblem.DuplicateGroup.apply)
    } else if (policy.maximumRepeat < 1) {
      Some(PolicyProblem.MaximumRepeatLessThanOne(policy.maximumRepeat))
    } else {
      findGroupsProblem(policy)
    }
  }

  private def findGroupsProblem(policy: PasswordPolicy): Option[PolicyProblem] = {
    val blocked = policy.blockedSymbols.toSet
    val groupMinimums = policy.allowedGroups.map(_._2)
    val totalMinimum = groupMinimums.map(_.toLong).sum

    val negativeMinimum = policy.allowedGroups.flatMap { case (group, minimum) =>
      if (minimum < 0) List(PolicyProblem.NegativeGroupMinimum(group, minimum))
      else Nil
    }.headOption

    val groupProblem = policy.allowedGroups.flatMap { case (group, minimum) =>
      val available = group.characters.distinct.count(character => !blocked.contains(character))

      if (available == 0) {
        List(PolicyProblem.EmptyGroupAfterBlocking(group))
      } else if (minimum > available) {
        List(PolicyProblem.GroupMinimumAboveAvailable(group, minimum, available))
      } else {
        Nil
      }
    }.headOption

    val allGroupCharacters = policy.allowedGroups.flatMap(_._1.characters).toSet
    val blockedAllowedCharacters = allGroupCharacters.intersect(blocked).toList.sorted
    val pool = policy.availableCharacters

    if (negativeMinimum.nonEmpty) {
      negativeMinimum
    } else if (totalMinimum > policy.maximumLength.toLong) {
      Some(PolicyProblem.MandatoryCountAboveMaximum(totalMinimum, policy.maximumLength))
    } else if (totalMinimum > policy.defaultLength.toLong) {
      Some(PolicyProblem.MandatoryCountAboveDefault(totalMinimum, policy.defaultLength))
    } else if (blockedAllowedCharacters.nonEmpty) {
      Some(PolicyProblem.AllowedCharacterBlocked(blockedAllowedCharacters))
    } else if (policy.allowSpace && blocked.contains(' ')) {
      Some(PolicyProblem.SpaceAllowedAndBlocked)
    } else if (groupProblem.nonEmpty) {
      groupProblem
    } else if (pool.isEmpty) {
      Some(PolicyProblem.NoAvailableCharacters)
    } else if (!policy.allowRepeat && pool.length < policy.defaultLength) {
      Some(PolicyProblem.NotEnoughCharactersForNoRepeat(pool.length, policy.defaultLength))
    } else {
      None
    }
  }
}
