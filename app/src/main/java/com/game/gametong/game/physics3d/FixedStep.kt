package com.game.gametong.game.physics3d

/**
 * 고정 타임스텝 적분기.
 *
 * 화면은 기기마다 60·90·120Hz 로 제각각 그려지지만, 물리는 언제나 [step] 초 단위로만
 * 나아가게 한다. 그래서
 * - 같은 입력이면 기기 성능과 무관하게 같은 결과가 나오고,
 * - 프레임이 한 번 끊겨도(예: 30fps 로 떨어져도) 물리 속도가 느려지지 않는다.
 *
 * 남은 시간은 [alpha] 로 꺼내 두 스텝 사이를 보간해 그릴 수 있다.
 */
class FixedTimestep(
    val step: Float = 1f / 120f,
    /** 한 프레임에 돌리는 최대 스텝 수. 이보다 오래 멈췄다면 시간을 버린다(터널링·폭주 방지). */
    val maxSteps: Int = 8,
) {
    /** 아직 소화하지 못한 시간(초). */
    var accumulator: Float = 0f
        private set

    /** 지난 [advance] 에서 실제로 돈 스텝 수. */
    var lastSteps: Int = 0
        private set

    /** 지난 한 프레임에 버려진 시간(초). 디버그·성능 표시에 쓴다. */
    var droppedSeconds: Float = 0f
        private set

    /** 누적 시간이 스텝보다 모자라 두 스텝 사이에 있는 정도(0~1). */
    val alpha: Float get() = (accumulator / step).coerceIn(0f, 1f)

    fun reset() {
        accumulator = 0f
        lastSteps = 0
        droppedSeconds = 0f
    }

    /**
     * [deltaSeconds] 만큼 시간을 넣고, 그동안 [onStep] 을 고정 간격으로 돌린다.
     * 돌린 스텝 수를 돌려준다.
     */
    fun advance(deltaSeconds: Float, onStep: (Float) -> Unit): Int {
        if (deltaSeconds <= 0f) {
            lastSteps = 0
            return 0
        }
        // 한 프레임에 받아들이는 최대 시간: maxSteps × step. 이보다 큰 값은 버린다.
        val cap = step * maxSteps
        val clamped = deltaSeconds.coerceAtMost(cap)
        droppedSeconds = (deltaSeconds - clamped).coerceAtLeast(0f)
        accumulator += clamped

        var steps = 0
        while (accumulator >= step && steps < maxSteps) {
            onStep(step)
            accumulator -= step
            steps++
        }
        // 부동소수 오차로 남은 찌꺼기 정리
        if (accumulator < step * 1e-4f) accumulator = 0f
        lastSteps = steps
        return steps
    }
}
