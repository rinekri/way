package ru.kode.way

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal val NavigationState.active get() = this.regions.values.first().active.toString()
internal val NavigationState.alive get() = this.regions.values.first().alive.map { it.toString() }
internal val NavigationState.nodes get() = this.regions.values.first().nodes
internal val NavigationState.aliveNodes: Map<String, Node> get() {
  val m = nodes.toMutableMap()
  m.keys.retainAll(this.regions.values.first().alive.toSet())
  return m.mapKeys { it.key.toString() }
}

internal fun NavigationService<*>.collectTransitions(rootNodePayload: Any? = null): Flow<NavigationState> {
  return callbackFlow {
    val listener = { state: NavigationState ->
      trySend(state)
      Unit
    }
    this@collectTransitions.addTransitionListener(listener)
    start(rootNodePayload)
    awaitClose { this@collectTransitions.removeTransitionListener(listener) }
  }
}
