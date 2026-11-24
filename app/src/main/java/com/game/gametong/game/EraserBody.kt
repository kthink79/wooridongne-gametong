package com.game.gametong.game

import com.game.gametong.game.physics3d.EraserDynamics
import kotlin.math.roundToInt

/**
 * 책상 위 지우개 하나.
 *
 * 위치·속도·회전과 함께 **3D 자세**를 가진다.
 * - [tumble]: 앞으로 넘어가며 구른 각(도). 90도마다 한 면이 바닥에 닿는다(넘기면서 전진).
 * - [tumbleAxis]: 구르는 회전축(월드 수평 단위벡터). 진행 방향의 수직이다.
 * - [z]: 바닥에서 들린 높이. 상대 지우개에 걸쳐 올라가면 상대 두께만큼 올라간다.
 * - [lean]: 옆으로 기운 각(도). 상대 위에 걸칠 때·중심에서 벗어난 타격에서 생긴다.
 * - [perchedOn]·[overlap]: 상대 위에 **걸쳐 있는** 상태와 걸침 비율.
 */
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
    var tumble: Float = 0f,
    var tumbleAxis: Vec2 = Vec2(1f, 0f),
    var z: Float = 0f,
    var lean: Float = 0f,
    var perchedOn: Side? = null,
    var overlap: Float = 0f,
) {
    /**
     * 3D 기울기 동역학. 관성 모멘트·각운동량으로 모서리 걸림과 넘어짐을 계산한다.
     * (규칙 판정은 기존 [lean] 을 쓰고, 이 값은 자세·연출에 더해진다.)
     */
    val dyn: EraserDynamics = EraserDynamics(
        halfWidth = type.radius,
        halfHeight = type.thickness * 0.5f,
        halfDepth = type.radius * 0.78f,
        mass = type.mass,
    )

    /** 강체가 앞뒤로 기운 각(도). */
    val pitchDeg: Float get() = dyn.pitch

    /** 강체가 옆으로 기운 각(도). */
    val rollDeg: Float get() = dyn.roll

    val radius: Float get() = type.radius
    val mass: Float get() = type.mass
    val speed: Float get() = vel.length()

    /** 블록의 가로(무대 단위). */
    val blockWidth: Float get() = type.radius * 2f

    /** 블록의 앞뒤 길이(무대 단위). */
    val blockDepth: Float get() = type.radius * 1.56f

    /** 블록의 높이(무대 단위). */
    val blockHeight: Float get() = type.thickness

    /** 지금 공중에 떠 있는가. */
    val airborne: Boolean get() = z > 0.5f

    /** 상대 위에 걸쳐 있는가. */
    val perched: Boolean get() = perchedOn != null

    fun resetHome() {
        pos = home
        vel = Vec2.ZERO
        spin = 0f
        spinSpeed = 0f
        fallen = false
        climbing = false
        contactCooldown = 0f
        tumble = 0f
        tumbleAxis = Vec2(1f, 0f)
        z = 0f
        lean = 0f
        perchedOn = null
        overlap = 0f
        dyn.reset()
    }

    fun isResting(): Boolean = fallen || vel.length() <= 1e-3f || (perched && vel.length() <= Board.REST_SPEED)

    /** 바닥에 놓인 면이 정확히 90도 배수가 되도록 정리한 각. */
    fun settledTumble(): Float = (tumble / 90f).roundToInt() * 90f

    /** 멈출 때 바닥에 놓인 면이 반듯해지도록 텀블 각을 90도 배수로 맞춘다. */
    fun snapTumble() {
        tumble = settledTumble()
    }
}
