package com.nearbygames.ui.rps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nearbygames.data.NearbyMessage
import com.nearbygames.data.NearbyMessageType
import com.nearbygames.data.RpsChoice
import com.nearbygames.data.RpsChoiceValue
import com.nearbygames.data.RpsState
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RockPaperScissorsViewModel(application: Application) : AndroidViewModel(application) {

    private val _gameState = MutableLiveData(RpsState())
    val gameState: LiveData<RpsState> = _gameState

    private val _statusMessage = MutableLiveData("Waiting for an opponent…")
    val statusMessage: LiveData<String> = _statusMessage

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private var opponentEndpointId: String? = null
    private var roundJob: Job? = null

    private val gson = Gson()

    private val connectionListener = object : NearbyConnectionsManager.ConnectionStateListener {
        override fun onConnected(endpointId: String, endpointName: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            if (opponentEndpointId == null) {
                opponentEndpointId = endpointId
                _statusMessage.postValue("Opponent found: $endpointName.  Choose rock, paper, or scissors!")
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            if (endpointId == opponentEndpointId) {
                opponentEndpointId = null
                roundJob?.cancel()
                _gameState.postValue(RpsState())
                _statusMessage.postValue("Opponent disconnected.  Waiting for a new opponent…")
            }
        }
    }

    private val messageListener = object : NearbyConnectionsManager.MessageListener {
        override fun onMessage(fromEndpointId: String, message: NearbyMessage) {
            if (message.type != NearbyMessageType.RPS_CHOICE) return
            if (fromEndpointId != opponentEndpointId) return
            val choice = gson.fromJson(message.payload, RpsChoice::class.java)
            val state = _gameState.value ?: RpsState()
            if (state.opponentChoice != null) return // ignore late duplicates
            val newState = state.copy(opponentChoice = choice.choice)
            _gameState.postValue(newState)
            maybeStartRound(newState)
        }
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    init {
        _connectedCount.value = NearbyConnectionsManager.getConnectedEndpoints().size
        NearbyConnectionsManager.getConnectedEndpoints().keys.firstOrNull()?.let { id ->
            opponentEndpointId = id
        }
        NearbyConnectionsManager.addConnectionListener(connectionListener)
        NearbyConnectionsManager.addMessageListener(messageListener)
    }

    override fun onCleared() {
        NearbyConnectionsManager.removeConnectionListener(connectionListener)
        NearbyConnectionsManager.removeMessageListener(messageListener)
        roundJob?.cancel()
    }

    // ---- Public API -------------------------------------------------------------------------

    fun choose(choice: String) {
        val opponentId = opponentEndpointId ?: return
        val state = _gameState.value ?: RpsState()
        if (state.myChoice != null || state.countdown != null || state.resultMessage != null) return

        val newState = state.copy(myChoice = choice)
        _gameState.value = newState
        _statusMessage.value = "Waiting for opponent's choice…"

        val message = NearbyMessage(
            type = NearbyMessageType.RPS_CHOICE,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = System.currentTimeMillis(),
            payload = gson.toJson(RpsChoice(choice))
        )
        NearbyConnectionsManager.sendMessage(opponentId, message)

        maybeStartRound(newState)
    }

    // ---- Private helpers --------------------------------------------------------------------

    private fun maybeStartRound(state: RpsState) {
        if (state.myChoice == null || state.opponentChoice == null) return
        if (roundJob?.isActive == true) return
        roundJob = viewModelScope.launch {
            for (count in 3 downTo 1) {
                _gameState.postValue((_gameState.value ?: state).copy(countdown = count))
                _statusMessage.postValue(count.toString())
                delay(1000)
            }
            val finalState = _gameState.value ?: state
            val my = finalState.myChoice
            val opponent = finalState.opponentChoice
            val outcome = determineOutcome(my, opponent)
            val resultState = finalState.copy(
                countdown = null,
                resultMessage = outcome,
                opponentChoiceRevealed = opponent
            )
            _gameState.postValue(resultState)
            _statusMessage.postValue("$outcome  Opponent chose ${opponent ?: "?"}.")
            delay(5000)
            _gameState.postValue(RpsState())
            _statusMessage.postValue(
                if (opponentEndpointId != null) "Choose rock, paper, or scissors!"
                else "Waiting for an opponent…"
            )
        }
    }

    private fun determineOutcome(my: String?, opponent: String?): String {
        if (my == null || opponent == null) return "No result"
        if (my == opponent) return "It's a tie!"
        val myWins = (my == RpsChoiceValue.ROCK && opponent == RpsChoiceValue.SCISSORS) ||
            (my == RpsChoiceValue.PAPER && opponent == RpsChoiceValue.ROCK) ||
            (my == RpsChoiceValue.SCISSORS && opponent == RpsChoiceValue.PAPER)
        return if (myWins) "You won!" else "You lose!"
    }
}
