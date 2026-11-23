import java.nio.file.Path

object PolicyService {
  def validate(policy: PasswordPolicy): Either[AppError, PasswordPolicy] = {
    PolicyValidator
      .validate(policy)
      .left
      .map(error => AppError.InvalidPolicy(error))
  }

  def create(policy: PasswordPolicy): Either[AppError, Path] = {
    validate(policy).flatMap(validPolicy => PolicyStorage.createPolicy(validPolicy))
  }

  def update(policy: PasswordPolicy, path: Path): Either[AppError, PasswordPolicy] = {
    validate(policy).flatMap { validPolicy =>
      PolicyStorage.savePolicy(validPolicy, path).map { _ =>
        PolicySession.update(path, validPolicy)
        validPolicy
      }
    }
  }

  def reload(): Either[AppError, Int] = {
    PolicyStorage.loadAllPolicies().map { policies =>
      PolicySession.replace(policies)
      policies.length
    }
  }

  def defaultPolicy: Either[AppError, PasswordPolicy] = {
    PolicySession.defaultPolicy.toRight(
      AppError.DefaultPolicyNotLoaded(PolicySession.DefaultPolicyName)
    )
  }
}
