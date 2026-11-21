package com.game.gametong.game

/**
 * 지우개 종류. 무게·마찰(감속)·탄성·발사 속도가 서로 달라 손맛이 갈린다.
 * - 감속(decel)은 쿨롱 마찰에 가까운 일정 감속(단위/초²)이다.
 */
enum class EraserType(
    val label: String,
    val blurb: String,
    val radius: Float,
    val mass: Float,
    val decel: Float,
    val restitution: Float,
    val speedMul: Float,
) {
    JUMBO(
        label = "소프트 점보",
        blurb = "넓고 무거운 지우개. 잘 밀리지 않지만 멀리 가지는 못한다.",
        radius = 152f,
        mass = 1.7f,
        decel = 1450f,
        restitution = 0.24f,
        speedMul = 0.84f,
    ),
    STANDARD(
        label = "표준 지우개",
        blurb = "무게·마찰·탄성이 고른 기본형. 처음 쓰기 좋다.",
        radius = 122f,
        mass = 1.0f,
        decel = 950f,
        restitution = 0.42f,
        speedMul = 1.0f,
    ),
    NIMBLE(
        label = "날렵한 지우개",
        blurb = "가볍고 잘 미끄러진다. 멀리 가지만 멈추기가 어렵다.",
        radius = 96f,
        mass = 0.62f,
        decel = 780f,
        restitution = 0.58f,
        speedMul = 1.12f,
    );

    companion object {
        fun byLabel(label: String): EraserType =
            entries.firstOrNull { it.label == label } ?: STANDARD
    }
}
