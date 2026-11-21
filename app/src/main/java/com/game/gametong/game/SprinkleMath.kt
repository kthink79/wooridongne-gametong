package com.game.gametong.game

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 뿌리기 명성 계산(v0.1 공식의 앱 구현).
 * 명성 = 30 + 6 × min(수령자, 20) + 4 × (희귀 + 2 × 전설), 일일 상한 300.
 * 명예 뿌리기는 수령자를 5명으로 줄이는 대신 ×1.6.
 */
object SprinkleMath {
    const val DAILY_CAP = 300
    const val RECIPIENT_CAP = 20
    const val HONOR_RECIPIENTS = 5
    const val HONOR_MULTIPLIER = 1.6f

    fun bulkPrestige(recipients: Int, rare: Int, legendary: Int): Int =
        prestige(min(recipients, RECIPIENT_CAP), rare, legendary, 1f)

    fun honorPrestige(recipients: Int, rare: Int, legendary: Int): Int =
        prestige(min(recipients, HONOR_RECIPIENTS), rare, legendary, HONOR_MULTIPLIER)

    private fun prestige(recipients: Int, rare: Int, legendary: Int, multiplier: Float): Int {
        val base = 30 + 6 * recipients.coerceAtLeast(0) + 4 * (rare.coerceAtLeast(0) + 2 * legendary.coerceAtLeast(0))
        return (base * multiplier).roundToInt().coerceIn(0, DAILY_CAP)
    }
}
