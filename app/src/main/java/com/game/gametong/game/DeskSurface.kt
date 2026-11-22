package com.game.gametong.game

/**
 * 책상 표면의 상태. 같은 세기로 튕겨도 표면에 따라 미끄러지는 거리·회전이 오래 도는 정도·
 * 튕김이 달라진다.
 *
 * - [frictionMul] 이 클수록 지우개가 빨리 멈춘다(코팅 < 나무 < 합판 < 먼지).
 * - [spinDecayMul] 이 클수록 회전이 빨리 죽는다.
 * - [restitutionMul] 은 표면이 튕겨 주는 정도.
 * - [curveMul] 은 회전이 궤적을 휘게 하는 힘(매끈한 표면에서 크게 휜다).
 */
enum class DeskSurface(
    val label: String,
    val hint: String,
    val frictionMul: Float,
    val spinDecayMul: Float,
    val restitutionMul: Float,
    val curveMul: Float,
) {
    COATED(
        label = "매끈한 코팅 책상",
        hint = "반짝이는 코팅 — 멀리 미끄러지고 회전이 오래 돈다",
        frictionMul = 0.62f,
        spinDecayMul = 0.55f,
        restitutionMul = 1.10f,
        curveMul = 1.45f,
    ),
    WOOD(
        label = "나무 책상",
        hint = "결이 살아 있는 원목 — 무난한 기본",
        frictionMul = 1.0f,
        spinDecayMul = 1.0f,
        restitutionMul = 1.0f,
        curveMul = 1.0f,
    ),
    PLYWOOD(
        label = "거친 합판",
        hint = "결이 거칠어 금방 멈춘다 — 회전도 빨리 죽는다",
        frictionMul = 1.5f,
        spinDecayMul = 1.8f,
        restitutionMul = 0.85f,
        curveMul = 0.7f,
    ),
    DUSTY(
        label = "먼지 쌓인 책상",
        hint = "먼지가 지우개를 붙잡는다 — 미끄러짐 최소",
        frictionMul = 1.95f,
        spinDecayMul = 2.4f,
        restitutionMul = 0.55f,
        curveMul = 0.55f,
    );

    companion object {
        fun byLabel(label: String): DeskSurface =
            entries.firstOrNull { it.label == label } ?: WOOD
    }
}
