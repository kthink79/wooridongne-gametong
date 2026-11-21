package com.game.gametong.game

/** 책상·손맛 관련 공통 수치. 모든 단위는 책상 좌표(1000 × 1500) 기준. */
object Board {
    const val WIDTH = 1000f
    const val HEIGHT = 1500f

    /** 고정 스텝. 기기 성능과 무관하게 같은 입력이면 같은 결과가 나오도록 한다. */
    const val DT = 1f / 120f

    /** 드래그 최대 길이(책상 단위)와 최대 발사 속도. */
    const val MAX_DRAG = 430f
    const val MAX_FLICK_SPEED = 1600f

    /** 이 속도 아래면 멈춘 것으로 본다. */
    const val REST_SPEED = 22f

    /** 레슬링: 이 사이 세기로 부딪히면 상대 위로 올라탄다. */
    const val CLIMB_MIN_SPEED = 210f
    const val CLIMB_MAX_SPEED = 980f

    /** 레슬링: 올라탄 지우개가 이만큼 덮이면 1카운트. */
    const val PIN_OVERLAP = 0.30f

    /** 한 번의 움직임을 계산하는 최대 시간(초). */
    const val MAX_SIM_SECONDS = 6f

    /** 턴이 끝난 뒤 두 지우개가 이보다 멀어지면 자리를 다시 잡는다. */
    const val RESET_DISTANCE = 620f

    /** 알까기: 한 라운드에서 비기지 않게 두는 최대 턴 수. */
    const val MAX_TURNS_PER_ROUND = 16

    /** 올라탄 상태에서 겹칠수록 더 세게 붙는 감속(지우개가 파고들어 멈추는 느낌). */
    const val CLIMB_EXTRA_DECEL = 2600f
}

enum class Side {
    PLAYER,
    AI;

    fun other(): Side = if (this == PLAYER) AI else PLAYER
}

enum class MatchPhase {
    /** 내(또는 상대) 차례 — 조준 대기 */
    AIMING,
    /** 지우개가 움직이는 중 */
    MOVING,
    /** 카운트 연출 중 (레슬링) */
    COUNTING,
    /** 라운드 결과 연출 (알까기) */
    ROUND_END,
    /** 경기 종료 */
    FINISHED,
}
