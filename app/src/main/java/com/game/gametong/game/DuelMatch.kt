package com.game.gametong.game

/** 알까기·레슬링 두 경기가 화면에서 같은 방식으로 다뤄지도록 맞춘 공통 인터페이스. */
interface DuelMatch {
    val world: EraserWorld
    val turn: Side
    val phase: MatchPhase
    val winner: Side?
    val isFinished: Boolean

    fun flick(velocity: Vec2)
    fun aiVelocity(accuracy: Float = 0.8f): Vec2
}
