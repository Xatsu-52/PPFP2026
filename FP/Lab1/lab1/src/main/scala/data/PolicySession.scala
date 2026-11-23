import java.nio.file.Path

object PolicySession {
  val DefaultPolicyName = "default_politic"

  private var snapshot: Option[List[(Path, PasswordPolicy)]] = None

  def current: Option[List[(Path, PasswordPolicy)]] = snapshot

  def replace(policies: List[(Path, PasswordPolicy)]): Unit = {
    snapshot = Some(policies)
  }

  def update(path: Path, policy: PasswordPolicy): Unit = {
    snapshot = snapshot.map { policies =>
      policies.filterNot { case (savedPath, _) => savedPath == path } :+ (path -> policy)
    }
  }

  def defaultPolicy: Option[PasswordPolicy] = {
    snapshot.flatMap { policies =>
      policies
        .find { case (_, policy) => policy.name == DefaultPolicyName }
        .map { case (_, policy) => policy }
    }
  }

  def findPolicy(name: String): Option[(Path, PasswordPolicy)] = {
    snapshot.flatMap(_.find { case (_, policy) => policy.name == name })
  }
}
