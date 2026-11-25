package com.game.gametong.game

/**
 * 알까기·레슬링 두 경기가 화면에서 같은 방식으로 다뤄지도록 맞춘 공통 인터페이스.
 *
 * 타격은 [flick] 한 번으로 나간다. [strikeOffset] 은 내 지우개의 어느 지점을 때렸는지
 * (-1 왼끝 ~ 0 가운데 ~ 1 오른끝)로, 중심에서 벗어날수록 회전이 붙어 궤적이 휜다.
 */
interface DuelMatch {
    val world: EraserWorld
    val turn: Side
    val phase: MatchPhase
    val winner: Side?
    val isFinished: Boolean

    /** AI가 칠 때 함께 쓰는 젖힘(뒤집기) 정도. 레슬링만 0보다 크다. */
    val aiTip: Float get() = 0f

    /** 가운데를 때린 것으로 본다. */
    fun flick(velocity: Vec2) = flick(velocity, 0f)

    /** 중심에서 벗어난 타격. */
    fun flick(velocity: Vec2, strikeOffset: Float) = flick(velocity, strikeOffset, aiTip)

    /**
     * 가장자리를 눌러 미는 타격.
     * [tip] 은 앞으로 젖혀지는 정도로, 상대 위로 넘어가는(따먹기) 각운동량을 준다.
     */
    fun flick(velocity: Vec2, strikeOffset: Float, tip: Float)

    fun aiVelocity(accuracy: Float = 0.8f): Vec2 = aiVelocity(accuracy, 0f)

    fun aiVelocity(accuracy: Float, strikeOffset: Float): Vec2
}
