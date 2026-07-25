package iris.automate.issue.create

import io.kotest.matchers.shouldBe
import iris.automate.base.Constants
import iris.automate.base.github.model.GitHubIssue
import iris.automate.base.github.model.GitHubIssueNumber
import iris.automate.base.github.model.GitHubUser
import iris.automate.base.github.model.GitHubUsername
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CommentTextTest {

    @Test
    fun `the comment text should look good`(): Unit = runTest {
        // given
        val issue = GitHubIssue(
            number = GitHubIssueNumber(value = "223"),
            creator = GitHubUser(GitHubUsername("user1")),
            assignee = null
        )

        // when
        val commentText = commentText(issue)

        // then
        commentText shouldBe """
            Thank you @user1 for raising Issue #223! 🚀
            What's next? Read our **[Contribution Guidelines](${Constants.CONTRIBUTING_URL}) 📚**.
            
            _Tagging @${Constants.IRIS_ADMIN} for review & approval 👀_
        """.trimIndent()
    }
}