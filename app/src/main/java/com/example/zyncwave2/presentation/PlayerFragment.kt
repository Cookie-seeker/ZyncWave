package com.example.zyncwave2.presentation

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.zyncwave2.R
import com.example.zyncwave2.data.PlayerState
import com.example.zyncwave2.ui.theme.PlayerContent
import com.example.zyncwave2.ui.theme.QueueContent
import com.example.zyncwave2.ui.theme.ZyncWave2Theme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    // Evita bucle: cuando la View se sincroniza DESDE el ViewModel,
    // no queremos que el callback de la View vuelva a escribir al ViewModel.
    private var syncingFromViewModel = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainCompose  = view.findViewById<ComposeView>(R.id.playerContentCompose)
        val sheetView     = view.findViewById<View>(R.id.queueSheet)
        val sheetCompose  = view.findViewById<ComposeView>(R.id.queueContentCompose)

        mainCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        sheetCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        mainCompose.setContent {
            ZyncWave2Theme {
                PlayerContent(viewModel = playerViewModel)
            }
        }
        sheetCompose.setContent {
             ZyncWave2Theme {
                QueueContent(viewModel = playerViewModel)
            }
        }


        val behavior = BottomSheetBehavior.from(sheetView)
        bottomSheetBehavior = behavior
        behavior.isDraggable = false

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                android.util.Log.d("QueueDrag", "onStateChanged: $newState")
                if (syncingFromViewModel) return
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        PlayerState.isQueueExpanded = true
                        playerViewModel.setShowQueue(true)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        PlayerState.isQueueExpanded = false
                        playerViewModel.setShowQueue(false)
                    }
                    else -> { /* DRAGGING / SETTLING */ }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                android.util.Log.d("QueueDrag", "onSlide: $slideOffset")
            }
        })

        // Restaurar estado si veníamos de una sesión con la cola abierta
        behavior.state = if (PlayerState.isQueueExpanded)
            BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED

        // Cierre PROGRAMÁTICO (ej: tocar una canción de la lista → playFromQueue
        // llama setShowQueue(false) desde el ViewModel) → sincroniza la View.
        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.state
                .map { it.showQueue }
                .distinctUntilChanged()
                .collect { wantExpanded ->
                    val behavior = bottomSheetBehavior ?: return@collect
                    val currentState = behavior.state
                    if (currentState == BottomSheetBehavior.STATE_DRAGGING ||
                        currentState == BottomSheetBehavior.STATE_SETTLING) return@collect

                    val isExpanded = currentState == BottomSheetBehavior.STATE_EXPANDED
                    if (wantExpanded != isExpanded) {
                        syncingFromViewModel = true
                        behavior.state = if (wantExpanded)
                            BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
                        syncingFromViewModel = false
                    }
                }
        }
    }
}