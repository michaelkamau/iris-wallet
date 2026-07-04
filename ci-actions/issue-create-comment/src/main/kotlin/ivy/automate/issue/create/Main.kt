package ivy.automate.issue.create

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import ivy.automate.base.Constants
import ivy.automate.base.IvyError
import ivy.automate.base.github.GitHubIssueArgs
import ivy.automate.base.github.GitHubService
import ivy.automate.base.github.GitHubServiceImpl
import ivy.automate.base.github.model.GitHubIssue
import ivy.automate.base.github.model.NotBlankTrimmedString
import ivy.automate.base.github.parseArgs
import ivy.automate.base.ktor.ktorClientScope
import kotlinx.coroutines.runBlocking

data class Context(
    val gitHubService: GitHubService,
) : GitHubService by gitHubService

fun main(args: Array<String>): Unit = runBlocking {
    ktorClientScope {
        val context = Context(
            gitHubService = GitHubServiceImpl(
                ktorClient = ktorClient,
            ),
        )
        with(context) {
            val result = execute(args).fold(
                ifLeft = { throw IvyError("TASK FAILED: $it") },
                ifRight = { "TASK SUCCESSFUL: $it" }
            )
            println("[ISSUE-ASSIGN] $result")
        }
    }
}

context(gitHubService: GitHubService)
private suspend fun execute(argsArr: Array<String>): Either<String, String> = either {
    val args = parseArgs(argsArr.toList()).bind()

    val issue = gitHubService.fetchIssue(args.issueNumber).mapLeft {
        "Failed to fetch Issue #${args.issueNumber.value}"
    }.bind()
    comment(args, commentText(issue))
}

fun commentText(
    issue: GitHubIssue
): String = buildString {
    append("Thank you @${issue.creator.username.value} for raising Issue #${issue.number.value}! \uD83D\uDE80")
    append("\n")
    val guidelinesUrl = "**[Contribution Guidelines](${Constants.CONTRIBUTING_URL}) \uD83D\uDCDA**"
    append("What's next? Read our $guidelinesUrl.")
    append("\n\n")
    append("_Tagging @${Constants.IVY_ADMIN} for review & approval \uD83D\uDC40_")
}

context(raise: Raise<String>, gitHubService: GitHubService)
private suspend fun comment(
    args: GitHubIssueArgs,
    text: String
): String = with(raise) {
    gitHubService.commentIssue(
        pat = args.pat,
        issueNumber = args.issueNumber,
        text = NotBlankTrimmedString(text)
    ).mapLeft {
        "Failed to comment: $it"
    }.map {
        "Commented on Issue #${args.issueNumber.value}"
    }.bind()
}