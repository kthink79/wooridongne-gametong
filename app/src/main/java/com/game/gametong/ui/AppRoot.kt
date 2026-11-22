package com.game.gametong.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.game.gametong.game.DeskSurface
import com.game.gametong.game.EraserType
import com.game.gametong.game.RoundMode
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.ui.screens.EraserBattleScreen
import com.game.gametong.ui.screens.EraserMenuScreen
import com.game.gametong.ui.screens.GonggiScreen
import com.game.gametong.ui.screens.IntroScreen
import com.game.gametong.ui.screens.LobbyScreen
import com.game.gametong.ui.screens.MarbleGameScreen
import com.game.gametong.ui.screens.MarbleMode
import com.game.gametong.ui.screens.MarbleScreen
import com.game.gametong.ui.screens.RankingScreen
import com.game.gametong.ui.screens.RoundTtakjiScreen
import com.game.gametong.ui.screens.SettingsScreen
import com.game.gametong.ui.screens.SprinkleScreen
import com.game.gametong.ui.screens.SquareTtakjiScreen
import com.game.gametong.ui.screens.TtakjiScreen
import com.game.gametong.ui.theme.Inks

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
        val surface: DeskSurface = DeskSurface.WOOD,
    ) : Route

    data object Ttakji : Route
    data class SquareTtakji(
        val surface: TtakjiSurface,
        val playerMaterial: TtakjiMaterial,
        val playerThickness: Int,
        val aiMaterial: TtakjiMaterial,
        val aiThickness: Int,
        val twoPlayer: Boolean,
    ) : Route

    data class RoundTtakji(val mode: RoundMode, val twoPlayer: Boolean) : Route

    data object Marble : Route
    data class MarblePlay(val mode: MarbleMode, val twoPlayer: Boolean) : Route

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

/**
 * 앱의 최상단.
 *
 * 배경은 화면 끝(상태 바·내비게이션 바 뒤)까지 칠하고, 내용은 **안전 영역 안**에만 놓는다.
 * 그래야 어떤 기기에서도 맨 아래 버튼이 안드로이드 시스템 내비게이션 바에 가려 눌리지 않는다.
 * (Android 15 부터는 edge-to-edge 가 강제라 이 처리가 없으면 하단 버튼이 바 밑으로 들어간다.)
 */
@Composable
fun GametongApp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            GametongContent()
        }
    }
}

@Composable
private fun GametongContent() {
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
                onStart = { mode, playerType, aiType, twoPlayer, surface ->
                    route = Route.EraserBattle(mode, playerType, aiType, twoPlayer, surface)
                },
            )

            is Route.EraserBattle -> EraserBattleScreen(
                mode = current.mode,
                playerType = current.playerType,
                aiType = current.aiType,
                twoPlayer = current.twoPlayer,
                surface = current.surface,
                aimAssist = aimAssist,
                haptics = haptics,
                onExit = { route = Route.EraserMenu },
            )

            Route.Ttakji -> TtakjiScreen(
                onBack = { route = Route.Lobby },
                onStartSquare = { surface, playerMaterial, playerThickness, aiMaterial, aiThickness, twoPlayer ->
                    route = Route.SquareTtakji(
                        surface = surface,
                        playerMaterial = playerMaterial,
                        playerThickness = playerThickness,
                        aiMaterial = aiMaterial,
                        aiThickness = aiThickness,
                        twoPlayer = twoPlayer,
                    )
                },
                onStartRound = { mode, twoPlayer -> route = Route.RoundTtakji(mode, twoPlayer) },
            )

            is Route.SquareTtakji -> SquareTtakjiScreen(
                surface = current.surface,
                playerMaterial = current.playerMaterial,
                playerThickness = current.playerThickness,
                aiMaterial = current.aiMaterial,
                aiThickness = current.aiThickness,
                twoPlayer = current.twoPlayer,
                onExit = { route = Route.Ttakji },
            )

            is Route.RoundTtakji -> RoundTtakjiScreen(
                mode = current.mode,
                twoPlayer = current.twoPlayer,
                onExit = { route = Route.Ttakji },
            )

            Route.Marble -> MarbleScreen(
                onBack = { route = Route.Lobby },
                onStart = { mode, twoPlayer -> route = Route.MarblePlay(mode, twoPlayer) },
            )

            is Route.MarblePlay -> MarbleGameScreen(
                mode = current.mode,
                twoPlayer = current.twoPlayer,
                onExit = { route = Route.Marble },
            )

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
