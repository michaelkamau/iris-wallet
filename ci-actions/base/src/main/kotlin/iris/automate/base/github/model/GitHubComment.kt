package iris.automate.base.github.model

data class GitHubComment(
    val author: GitHubUser,
    val text: String,
)
