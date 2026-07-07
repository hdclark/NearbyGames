package com.nearbygames.ui.numbersscrap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nearbygames.data.NearbyMessage
import com.nearbygames.data.NearbyMessageType
import com.nearbygames.data.NumbersScrapChoice
import com.nearbygames.data.NumbersScrapState
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NumbersScrapViewModel(application: Application) : AndroidViewModel(application) {

    private val _gameState = MutableLiveData(NumbersScrapState())
    val gameState: LiveData<NumbersScrapState> = _gameState

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
                _statusMessage.postValue("Opponent found: $endpointName.  Pick a digit!")
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            if (endpointId == opponentEndpointId) {
                opponentEndpointId = null
                roundJob?.cancel()
                _gameState.postValue(NumbersScrapState())
                _statusMessage.postValue("Opponent disconnected.  Waiting for a new opponent…")
            }
        }
    }

    private val messageListener = object : NearbyConnectionsManager.MessageListener {
        override fun onMessage(fromEndpointId: String, message: NearbyMessage) {
            if (message.type != NearbyMessageType.NUMBERS_SCRAP_CHOICE) return
            if (fromEndpointId != opponentEndpointId) return
            val choice = gson.fromJson(message.payload, NumbersScrapChoice::class.java)
            val state = _gameState.value ?: NumbersScrapState()
            if (state.opponentChoice != null) return // ignore late duplicates
            val newState = state.copy(opponentChoice = choice.digit)
            _gameState.postValue(newState)
            maybeResolveRound(newState)
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

    fun choose(digit: Int) {
        val opponentId = opponentEndpointId ?: return
        val state = _gameState.value ?: NumbersScrapState()
        if (digit in state.usedDigits) return
        if (state.myChoice != null || state.roundResultMessage != null || state.gameResultMessage != null) return

        val newState = state.copy(myChoice = digit)
        _gameState.value = newState
        _statusMessage.value = "Waiting for opponent's pick…"

        val message = NearbyMessage(
            type = NearbyMessageType.NUMBERS_SCRAP_CHOICE,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = System.currentTimeMillis(),
            payload = gson.toJson(NumbersScrapChoice(digit))
        )
        NearbyConnectionsManager.sendMessage(opponentId, message)

        maybeResolveRound(newState)
    }

    // ---- Private helpers --------------------------------------------------------------------

    private fun maybeResolveRound(state: NumbersScrapState) {
        val my = state.myChoice ?: return
        val opponent = state.opponentChoice ?: return
        if (roundJob?.isActive == true) return
        roundJob = viewModelScope.launch {
            val newRound = state.round + 1
            var myScore = state.myScore
            var opponentScore = state.opponentScore
            val roundMessage: String
            when {
                my > opponent -> {
                    myScore++
                    roundMessage = "You: $my vs Opponent: $opponent — You win this round!"
                }
                opponent > my -> {
                    opponentScore++
                    roundMessage = "You: $my vs Opponent: $opponent — You lose this round!"
                }
                else -> {
                    roundMessage = "You: $my vs Opponent: $opponent — Round tied!"
                }
            }
            val usedDigits = state.usedDigits + my
            val roundState = state.copy(
                usedDigits = usedDigits,
                round = newRound,
                myScore = myScore,
                opponentScore = opponentScore,
                roundResultMessage = roundMessage
            )
            _gameState.postValue(roundState)
            _statusMessage.postValue(roundMessage)
            delay(2000)

            if (newRound >= TOTAL_ROUNDS) {
                val gameMessage = when {
                    myScore > opponentScore -> "You won!"
                    opponentScore > myScore -> "You lose!"
                    else -> "It's a tie!"
                }
                val finalState = roundState.copy(
                    myChoice = null,
                    opponentChoice = null,
                    roundResultMessage = null,
                    gameResultMessage = gameMessage
                )
                _gameState.postValue(finalState)
                _statusMessage.postValue(gameMessage)
                delay(5000)
                _gameState.postValue(NumbersScrapState())
                _statusMessage.postValue(
                    if (opponentEndpointId != null) "Pick a digit!" else "Waiting for an opponent…"
                )
            } else {
                _gameState.postValue(
                    roundState.copy(myChoice = null, opponentChoice = null, roundResultMessage = null)
                )
                _statusMessage.postValue(
                    if (opponentEndpointId != null) "Pick a digit!" else "Waiting for an opponent…"
                )
            }
        }
    }

    companion object {
        private const val TOTAL_ROUNDS = 10
    }
}
