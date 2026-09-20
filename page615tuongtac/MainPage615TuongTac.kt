package com.facebook.engine.page615.interaction

fun main() {
    val engine = Page615TuongTacEngine(
        pageToken = "EAABxxxxxxxxxxxx",
        pageId615 = "615xxxxxxxxxxx"
    )

    val resLike = engine.reactPost(
        postId = "123456789_987654321",
        reactionType = Page615TuongTacEngine.ReactionType.LOVE
    )
    println("React Result: ${resLike.isSuccess} | ${resLike.message}")

    val resCmt = engine.commentPost(
        postId = "123456789_987654321",
        message = "Page 615 tuong tac tu dong"
    )
    println("Comment Result: ${resCmt.isSuccess} | ID: ${resCmt.resultId}")

    val resFollow = engine.followTarget(
        targetId = "1000xxxxxxxxxxx"
    )
    println("Follow Result: ${resFollow.isSuccess}")

    val resLikePage = engine.likeOtherPage(
        targetPageId = "999999999999"
    )
    println("Like Other Page: ${resLikePage.isSuccess}")

    val resGroup = engine.joinGroup(
        groupId = "888888888888"
    )
    println("Join Group Result: ${resGroup.isSuccess}")
}
