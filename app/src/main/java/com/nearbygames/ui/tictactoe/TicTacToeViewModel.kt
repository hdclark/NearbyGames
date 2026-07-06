package com.nearbygames.ui.tictactoe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.nearbygames.data.NearbyMessage
import com.nearbygames.data.NearbyMessageType
import com.nearbygames.data.TicTacToeInit
import com.nearbygames.data.TicTacToeMove
import com.nearbygames.data.TicTacToeState
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager
import kotlin.random.Random

class TicTacToeViewModel(application: Application) : AndroidViewModel(application) {

    private val _gameState = MutableLiveData<TicTacToeState?>()
    val gameState: LiveData<TicTacToeState?> = _gameState

    private val _statusMessage = MutableLiveData("Waiting for an opponent…")
    val statusMessage: LiveData<String> = _statusMessage

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private var opponentEndpointId: String? = null
    private var sessionXScore = 0
    private var sessionOScore = 0

    private val gson = Gson()

    // ---- Lifecycle --------------------------------------------------------------------------

    init {
        _connectedCount.value = NearbyConnectionsManager.getConnectedEndpoints().size

        // If already connected when the fragment is opened, set up immediately
        NearbyConnectionsManager.getConnectedEndpoints().keys.firstOrNull()?.let { existingId ->
            val name = NearbyConnectionsManager.getConnectedEndpoints()[existingId] ?: existingId
            handleNewOpponent(existingId, name)
        }

        NearbyConnectionsManager.addConnectionListener(connectionListener)
        NearbyConnectionsManager.addMessageListener(messageListener)
    }

    override fun onCleared() {
        NearbyConnectionsManager.removeConnectionListener(connectionListener)
        NearbyConnectionsManager.removeMessageListener(messageListener)
    }

    // ---- Public API -------------------------------------------------------------------------

    fun makeMove(position: Int) {
        val state = _gameState.value ?: return
        val mySymbol = mySymbol(state) ?: return

        if (!state.gameActive) return
        if (state.winner != null) return
        if (state.board[position] != null) return
        if (state.currentPlayer != mySymbol) return

        val newBoard = state.board.toMutableList().also { it[position] = mySymbol }
        val winner = checkWinner(newBoard)
        val newState = state.copy(
            board = newBoard,
            currentPlayer = if (mySymbol == "X") "O" else "X",
            winner = winner,
            xScore = if (winner == "X") state.xScore + 1 else state.xScore,
            oScore = if (winner == "O") state.oScore + 1 else state.oScore
        )
        _gameState.value = newState
        updateStatus(newState)

        val move = TicTacToeMove(position, mySymbol)
        val message = NearbyMessage(
            type = NearbyMessageType.TICTACTOE_MOVE,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = System.currentTimeMillis(),
            payload = gson.toJson(move)
        )
        opponentEndpointId?.let { NearbyConnectionsManager.sendMessage(it, message) }
    }

    fun resetGame() {
        val message = NearbyMessage(
            type = NearbyMessageType.TICTACTOE_RESET,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = System.currentTimeMillis(),
            payload = ""
        )
        opponentEndpointId?.let { NearbyConnectionsManager.sendMessage(it, message) }
        startFreshBoard()
    }

    // ---- Private helpers --------------------------------------------------------------------

    private fun mySymbol(state: TicTacToeState): String? {
        val myId = DeviceIdManager.getDeviceId(getApplication())
        return when (myId) {
            state.xDeviceId -> "X"
            state.oDeviceId -> "O"
            else -> null
        }
    }

    /**
     * Called when a new opponent device connects.  The device with the lexicographically
     * smaller ID sends the TICTACTOE_INIT message that randomly assigns X and O.
     */
    private fun handleNewOpponent(endpointId: String, endpointName: String) {
        if (opponentEndpointId != null) return // already have an opponent
        opponentEndpointId = endpointId

        val myId = DeviceIdManager.getDeviceId(getApplication())
        if (myId < endpointId) {
            // We initiate: randomly assign X / O
            val weAreX = Random.nextBoolean()
            val xDeviceId = if (weAreX) myId else endpointId
            val oDeviceId = if (weAreX) endpointId else myId

            val init = TicTacToeInit(xDeviceId, oDeviceId)
            val message = NearbyMessage(
                type = NearbyMessageType.TICTACTOE_INIT,
                senderId = myId,
                timestamp = System.currentTimeMillis(),
                payload = gson.toJson(init)
            )
            NearbyConnectionsManager.sendMessage(endpointId, message)
            applyInit(xDeviceId, oDeviceId)
        }
        // else: wait for the other side's TICTACTOE_INIT message
        _statusMessage.postValue("Opponent found: $endpointName.  Starting game…")
    }

    private fun applyInit(xDeviceId: String, oDeviceId: String) {
        sessionXScore = 0
        sessionOScore = 0
        val state = TicTacToeState(
            board = List(9) { null },
            currentPlayer = "X",
            winner = null,
            xDeviceId = xDeviceId,
            oDeviceId = oDeviceId,
            xScore = 0,
            oScore = 0,
            gameActive = true
        )
        _gameState.postValue(state)
        updateStatus(state)
    }

    private fun startFreshBoard() {
        val state = _gameState.value ?: return
        val fresh = state.copy(
            board = List(9) { null },
            currentPlayer = "X",
            winner = null,
            gameActive = true
        )
        _gameState.value = fresh
        updateStatus(fresh)
    }

    private fun updateStatus(state: TicTacToeState) {
        val myId = DeviceIdManager.getDeviceId(getApplication())
        val mySymbol = mySymbol(state) ?: "?"
        val status = when {
            !state.gameActive -> "Waiting for an opponent…"
            state.winner == "DRAW" -> "It's a draw!  Score: X ${state.xScore} – O ${state.oScore}"
            state.winner != null -> {
                val winnerLabel = if (state.winner == mySymbol) "You win!" else "Opponent wins!"
                "$winnerLabel  Score: X ${state.xScore} – O ${state.oScore}"
            }
            state.currentPlayer == mySymbol -> "Your turn ($mySymbol)"
            else -> "Opponent's turn (${state.currentPlayer})"
        }
        _statusMessage.postValue(status)
    }

    companion object {
        private val WIN_LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )

        fun checkWinner(board: List<String?>): String? {
            for (line in WIN_LINES) {
                val (a, b, c) = line
                if (board[a] != null && board[a] == board[b] && board[b] == board[c]) {
                    return board[a]
                }
            }
            return if (board.none { it == null }) "DRAW" else null
        }
    }

    // ---- Nearby listeners -------------------------------------------------------------------

    private val connectionListener = object : NearbyConnectionsManager.ConnectionStateListener {
        override fun onConnected(endpointId: String, endpointName: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            handleNewOpponent(endpointId, endpointName)
        }

        override fun onDisconnected(endpointId: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            if (endpointId == opponentEndpointId) {
                opponentEndpointId = null
                val endedState = _gameState.value?.copy(gameActive = false)
                _gameState.postValue(endedState)
                _statusMessage.postValue("Opponent disconnected.  Waiting for a new opponent…")
            }
        }
    }

    private val messageListener = object : NearbyConnectionsManager.MessageListener {
        override fun onMessage(fromEndpointId: String, message: NearbyMessage) {
            when (message.type) {
                NearbyMessageType.TICTACTOE_INIT -> {
                    val init = gson.fromJson(message.payload, TicTacToeInit::class.java)
                    opponentEndpointId = fromEndpointId
                    applyInit(init.xDeviceId, init.oDeviceId)
                }
                NearbyMessageType.TICTACTOE_MOVE -> {
                    if (fromEndpointId != opponentEndpointId) return
                    val move = gson.fromJson(message.payload, TicTacToeMove::class.java)
                    val state = _gameState.value ?: return
                    if (!state.gameActive || state.winner != null) return
                    val newBoard = state.board.toMutableList().also { it[move.position] = move.symbol }
                    val winner = checkWinner(newBoard)
                    val newState = state.copy(
                        board = newBoard,
                        currentPlayer = if (move.symbol == "X") "O" else "X",
                        winner = winner,
                        xScore = if (winner == "X") state.xScore + 1 else state.xScore,
                        oScore = if (winner == "O") state.oScore + 1 else state.oScore
                    )
                    _gameState.postValue(newState)
                    updateStatus(newState)
                }
                NearbyMessageType.TICTACTOE_RESET -> {
                    if (fromEndpointId != opponentEndpointId) return
                    startFreshBoard()
                }
            }
        }
    }
}
