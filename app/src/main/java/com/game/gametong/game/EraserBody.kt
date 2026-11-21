package com.game.gametong.game

/** 책상 위 지우개 하나. 위치·속도·회전(연출)과 상태(떨어짐/올라탐)를 가진다. */
data class EraserBody(
    val side: Side,
    val type: EraserType,
    var pos: Vec2,
    val home: Vec2,
    var vel: Vec2 = Vec2.ZERO,
    var spin: Float = 0f,
    var spinSpeed: Float = 0f,
    var fallen: Boolean = false,
    var climbing: Boolean = false,
    var contactCooldown: Float = 0f,
) {
    val radius: Float get() = type.radius
    val mass: Float get() = type.mass
    val speed: Float get() = vel.length()

    fun resetHome() {
        pos = home
        vel = Vec2.ZERO
        spin = 0f
        spinSpeed = 0f
        fallen = false
        climbing = false
        contactCooldown = 0f
    }

    fun isResting(): Boolean = fallen || vel.length() <= 1e-3f
}
