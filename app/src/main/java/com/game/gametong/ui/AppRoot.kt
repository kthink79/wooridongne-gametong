package com.game.gametong.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.game.gametong.game.EraserType
import com.game.gametong.ui.screens.EraserBattleScreen
import com.game.gametong.ui.screens.EraserMenuScreen
import com.game.gametong.ui.screens.GonggiScreen
import com.game.gametong.ui.screens.IntroScreen
import com.game.gametong.ui.screens.LobbyScreen
import com.game.gametong.ui.screens.MarbleScreen
import com.game.gametong.ui.screens.RankingScreen
import com.game.gametong.ui.screens.SettingsScreen
import com.game.gametong.ui.screens.SprinkleScreen
import com.game.gametong.ui.screens.TtakjiScreen

enum class EraserMode(val title: String, val subtitle: String) {
    ALKKAEGI("알까기(낙하)", "상대 지우개를 책상 밖으로 떨어뜨리면 라운드 승리"),
    WRESTLING("지우개 레슬링", "번갈아 한 번씩 — 상대 위에 올라타면 1카운트, 3카운트 승리"),
}

sealed interface Route {
    data object Intro : Route
    data object Lobby : Route
    data object EraserMenu : Route
    data class EraserBattle(
        val mode: EraserMode,
        val playerType: EraserType,
        val aiType: EraserType,
        val twoPlayer: Boolean,
    ) : Route

    data object Ttakji : Route
    data object Marble : Route
    data object Gonggi : Route
    data object Sprinkle : Route
    data object Ranking : Route
    data object Settings : Route
}

data class PlayerState(
    val region: String = "아산시 탕정면",
    val nickname: String = "개구쟁이",
    val level: Int = 5,
    val fame: Int = 1240,
    val ttakjiRank: Int = 12,
    val marbleRank: Int = 34,
    val eraserRank: Int = 7,
)

@Composable
fun GametongApp() {
    var route by remember { mutableStateOf<Route>(Route.Intro) }
    var fame by remember { mutableIntStateOf(1240) }
    var aimAssist by rememberSaveable { mutableStateOf(true) }
    var haptics by rememberSaveable { mutableStateOf(true) }

    val player = PlayerState(fame = fame)

    Crossfade(targetState = route, animationSpec = tween(260), label = "route") { current ->
        when (current) {
            Route.Intro -> IntroScreen(onStart = { route = Route.Lobby })

            Route.Lobby -> LobbyScreen(
                player = player,
                onOpen = { route = it },
            )

            Route.EraserMenu -> EraserMenuScreen(
                onBack = { route = Route.Lobby },
                onStart = { mode, playerType, aiType, twoPlayer ->
                    route = Route.EraserBattle(mode, playerType, aiType, twoPlayer)
                },
            )

            is Route.EraserBattle -> EraserBattleScreen(
                mode = current.mode,
                playerType = current.playerType,
                aiType = current.aiType,
                twoPlayer = current.twoPlayer,
                aimAssist = aimAssist,
                haptics = haptics,
                onExit = { route = Route.EraserMenu },
            )

            Route.Ttakji -> TtakjiScreen(onBack = { route = Route.Lobby })
            Route.Marble -> MarbleScreen(onBack = { route = Route.Lobby })
            Route.Gonggi -> GonggiScreen(onBack = { route = Route.Lobby })
            Route.Sprinkle -> SprinkleScreen(
                owned = 24,
                onBack = { route = Route.Lobby },
                onSprinkled = { gained -> fame += gained },
            )

            Route.Ranking -> RankingScreen(player = player, onBack = { route = Route.Lobby })
            Route.Settings -> SettingsScreen(
                aimAssist = aimAssist,
                onAimAssistChange = { aimAssist = it },
                haptics = haptics,
                onHapticsChange = { haptics = it },
                onBack = { route = Route.Lobby },
            )
        }
    }
}
