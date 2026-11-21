package com.game.gametong.game

/**
 * 드래그 → 발사 변환. 화면에서 손가락을 뒤로 당긴 벡터의 반대 방향으로 지우개가 날아간다.
 * 당긴 거리가 길수록 세게, 종류별 보정(speedMul)이 곱해진다.
 */
object Flick {
    const val MIN_DRAG = 12f

    fun powerOf(dragVector: Vec2, maxDrag: Float = Board.MAX_DRAG): Float =
        (dragVector.length() / maxDrag).coerceIn(0f, 1f)

    fun velocityFromDrag(
        dragVector: Vec2,
        type: EraserType,
        maxDrag: Float = Board.MAX_DRAG,
        maxSpeed: Float = Board.MAX_FLICK_SPEED,
    ): Vec2 {
        if (dragVector.length() < MIN_DRAG) return Vec2.ZERO
        val power = powerOf(dragVector, maxDrag)
        return dragVector.normalized() * (maxSpeed * power * type.speedMul)
    }
}
