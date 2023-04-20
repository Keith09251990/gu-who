/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lib

import scala.util.{Success, Try}
import Implicits._
import com.madgag.scalagithub.model.{Org, User}

object AccountRequirements {

  val All = Seq(FullNameRequirement, TwoFactorAuthRequirement, SponsorRequirement)

  val RequirementsByLabel = All.map(r => r.issueLabel -> r).toMap

}


trait AccountRequirement {

  trait UserEvaluator {
    val requirement = AccountRequirement.this
    def isSatisfiedBy(user: User): Boolean
    def appliesTo(user: User): Boolean
  }

  val issueLabel: String
  def fixSummary(implicit org: Org): String
  def userEvaluatorFor(orgSnapshot: OrgSnapshot): Try[UserEvaluator]
}


object FullNameRequirement extends AccountRequirement {
  override val issueLabel = "FullName"
  override def fixSummary(implicit org: Org) =
    "Enter a full name in your [GitHub profile](https://github.com/settings/profile)."

  private val userEvaluator = Success(new UserEvaluator {
    def appliesTo(user: User) = true
    def isSatisfiedBy(user: User): Boolean = user.name.exists(_.length > 1)
  })

  def userEvaluatorFor(orgSnapshot: OrgSnapshot): Try[UserEvaluator] = userEvaluator
}

// requires a 'users.txt' file in the people repo
object SponsorRequirement extends AccountRequirement {

  override val issueLabel = "Sponsor"

  override def fixSummary(implicit org: Org) =
    "Get a pull request opened to add your username to our " +
      s"[users.txt](https://github.com/${org.login}/people/blob/master/users.txt) file " +
      s"_- ideally, a Tech Lead or Dev Manager at ${org.displayName} should open this request for you_."

  def userEvaluatorFor(orgSnapshot: OrgSnapshot): Try[UserEvaluator] = Success(new UserEvaluator {
    def isSatisfiedBy(user: User) = orgSnapshot.hasSponsorFor(user)
    def appliesTo(user: User) = true
  })
}

// requires Owner permissions
object TwoFactorAuthRequirement extends AccountRequirement {

  override val issueLabel = "TwoFactorAuth"

  override def fixSummary(implicit org: Org) =
    "Enable [two-factor authentication](https://help.github.com/articles/about-two-factor-authentication) " +
      "in your [GitHub Account Security settings](https://github.com/settings/security)."

  def userEvaluatorFor(orgSnapshot: OrgSnapshot): Try[UserEvaluator] =
    for (tfaDisabledUsers <- orgSnapshot.twoFactorAuthDisabledUserLogins) yield
      new UserEvaluator {
        def isSatisfiedBy(user: User) = !tfaDisabledUsers.contains(user)
        def appliesTo(user: User): Boolean = !orgSnapshot.botUsers.contains(user)
      }
}