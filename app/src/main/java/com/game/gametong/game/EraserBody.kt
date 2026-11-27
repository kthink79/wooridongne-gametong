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

    /**
     * 넘어간 각을 0~180 으로 접은 자세.
     * 0 = 넓은 면이 책상에 닿은 눕힌 자세, 90 = 가장자리로 세워진 자세.
     */
    val tumblePose: Float get() = ((tumble % 180f) + 180f) % 180f

    /**
     * 가장자리로 **세워져** 있는가(넓은 면이 수직).
     *
     * 지우개는 두께 4cm 남짓한 좁은 받침으로 세로로 서면 오래 버티지 못한다.
     * 세워진 채로는 상대가 윗면에 올라탈 수도 없다([EraserWorld.handleClimb]).
     */
    val standing: Boolean get() = tumblePose > 45f && tumblePose < 135f

    /** 세워졌을 때의 높이(무대 단위). 눕혀 있으면 두께. */
    val standingHeight: Float get() = if (standing) blockDepth else blockHeight

    /** 바닥에 닿는 받침의 짧은 폭(무대 단위) — 설 수 있는 면. */
    val footprintWidth: Float get() = if (standing) blockHeight else blockDepth

    /** 놓인 면이 반듯한가(180도 배수 근처). 멈췄다는 판정에 쓴다. */
    val faceFlat: Boolean get() = tumblePose < 0.5f || tumblePose > 179.5f

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

    fun isResting(): Boolean {
        if (fallen) return true
        val slow = vel.length() <= 1e-3f || (perched && vel.length() <= Board.REST_SPEED)
        if (!slow) return false
        // 상대 위에 걸쳐 있으면 높이 떠 있어도 멈춘 것이다.
        if (perched) return true
        // 세워진 지우개·아직 내려오는 중인 지우개는 멈춘 것이 아니다(넘어지거나 내려와야 끝난다).
        return !standing && faceFlat && z < 1f
    }

    /** 바닥에 눕는 자세(넓은 면이 책상에 닿는 180도 배수 각). */
    fun settledTumble(): Float = (tumble / 180f).roundToInt() * 180f

    /**
     * 세워진 채 멈추려 할 때 **넘어가던 쪽으로 계속 넘어져** 눕는 각.
     * 90도에서 멈추면 그대로 서 있는 것이 아니라 180도(눕는 자세)까지 넘어진다.
     */
    fun toppleTumble(): Float = kotlin.math.ceil(tumble / 180f) * 180f

    /** 멈출 때 바닥에 놓인 면이 반듯해지도록 텀블 각을 180도 배수로 맞춘다. */
    fun snapTumble() {
        tumble = if (standing) toppleTumble() else settledTumble()
    }
}
