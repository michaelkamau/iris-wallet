package iris.automate.issue

import arrow.core.Either
import arrow.core.raise.either
import iris.automate.base.Constants
import iris.automate.base.github.GitHubIssueArgs
import iris.automate.base.github.GitHubService
import iris.automate.base.github.model.GitHubIssueNumber
import iris.automate.base.github.model.GitHubUser

sealed interface Action {
    val issueNumber: GitHubIssueNumber

    data class DoNothing(
        override val issueNumber: GitHubIssueNumber
    ) : Action

    data class AssignIssue(
        override val issueNumber: GitHubIssueNumber,
        val user: GitHubUser
    ) : Action

    data class NotApproved(
        val user: GitHubUser,
        override val issueNumber: GitHubIssueNumber
    ) : Action

    data class AlreadyTaken(
        val user: GitHubUser,
        override val issueNumber: GitHubIssueNumber,
        val assignee: GitHubUser,
    ) : Action
}

context(gitHubService: GitHubService)
suspend fun determineAction(args: GitHubIssueArgs): Either<String, Action> = either {
    val issueNumber = args.issueNumber
    val intention = checkCommentsForIntention(issueNumber).bind()
        ?: return@either Action.DoNothing(issueNumber)

    when (intention) {
        is CommentIntention.TakeIssue -> intention.toAction(issueNumber).bind()
        CommentIntention.Unknown -> Action.DoNothing(issueNumber)
    }
}

context(gitHubService: GitHubService)
private suspend fun CommentIntention.TakeIssue.toAction(
    issueNumber: GitHubIssueNumber,
): Either<String, Action> = either {
    val assignee = checkIfIssueIsAssigned(issueNumber).bind()
    if (assignee != null) {
        return@either Action.AlreadyTaken(user, issueNumber, assignee)
    }

    val approved = checkLabelsForApproved(issueNumber).bind()
    if (!approved) {
        return@either Action.NotApproved(user, issueNumber)
    }

    Action.AssignIssue(issueNumber, user)
}

context(gitHubService: GitHubService)
private suspend fun checkCommentsForIntention(
    issueNumber: GitHubIssueNumber
): Either<String, CommentIntention?> = either {
    val comments = gitHubService.fetchIssueComments(issueNumber)
        .mapLeft { "Failed to fetch comments: $it." }
        .bind()

    val lastComment = comments.lastOrNull() ?: return@either null
    if (lastComment.author.username.value == Constants.IRIS_BOT_USERNAME) {
        // Do nothing for Iris BOT comments
        return@either null
    }

    analyzeCommentIntention(lastComment)
}

context(gitHubService: GitHubService)
private suspend fun checkIfIssueIsAssigned(
    issueNumber: GitHubIssueNumber
): Either<String, GitHubUser?> = either {
    val issueInfo = gitHubService.fetchIssue(issueNumber)
        .mapLeft { "Failed to fetch issue: $it." }
        .bind()

    issueInfo.assignee
}

context(gitHubService: GitHubService)
private suspend fun checkLabelsForApproved(
    issueNumber: GitHubIssueNumber
): Either<String, Boolean> = either {
    val labels = gitHubService.fetchIssueLabels(issueNumber)
        .mapLeft { "Failed to fetch labels: $it." }
        .bind()

    val isApproved = labels.any { it.name.value == "approved" }
    isApproved
}
