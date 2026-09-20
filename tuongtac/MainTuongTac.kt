package com.facebook.engine.tuongtac

fun main() {
    val engine = TuongTacEngine(
        accessToken = "EAAAAU...",
        userId = "100012345678"
    )

    val targetPostId = "10160123456789012"
    val targetUid = "1000987654321"
    val targetPageId = "109876543210"
    val targetGroupId = "554433221100"

    // 1. Comment & Reply
    val resCmt = engine.comment(targetPostId, "Test comment")
    println(resCmt)

    // 2. React (LOVE, HAHA, LIKE...)
    val resReact = engine.react(targetPostId, TuongTacEngine.ReactionType.LOVE)
    println(resReact)

    // 3. Follow UID
    val resFollow = engine.follow(targetUid)
    println(resFollow)

    // 4. Like Page
    val resLikePage = engine.likePage(targetPageId)
    println(resLikePage)

    // 5. Join Group
    val resJoinGroup = engine.joinGroup(targetGroupId)
    println(resJoinGroup)

    // 6. Review 5 sao Page
    val resReview = engine.reviewPage(targetPageId, isPositive = true, reviewText = "Uy tín!")
    println(resReview)
}
