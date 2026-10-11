package com.goldarte.mavlinkjoystick.mavlink

import java.net.DatagramSocket

/**
 * Port the manager is actually listening on. The transport deliberately falls back to an ephemeral
 * port when the configured one is still held by the socket it just closed (a pending `receive()`
 * defers the release), so a fake controller has to ask instead of assuming [MavlinkManager.listenPort].
 */
internal fun MavlinkManagerAndroid.listeningPort(): Int =
    boundListenPort.takeIf { it > 0 } ?: listenPort

/**
 * Stands in for ConnectivityManager in tests: a small set of named Wi-Fi networks that can appear,
 * receive an address later, carry traffic, or disappear.
 */
internal open class FakeNetworkBinder : NetworkBinder {

    private val networks = LinkedHashMap<String, WifiNetwork>()

    /** Name of the network the OS would route through, like ConnectivityManager.activeNetwork. */
    private var activeName: String? = null
    private val listeners = ArrayList<NetworkBinder.Listener>()
    private val boundSockets = ArrayList<Pair<WifiNetwork, DatagramSocket>>()
    private var nextHandle = 0

    /** Simulates a Wi-Fi network becoming connected. */
    fun connect(name: String, usable: Boolean = true): WifiNetwork {
        val network = WifiNetwork(
            handle = "net:${name}:${nextHandle++}",
            description = name,
            localAddresses = if (usable) listOf("192.168.4.20") else emptyList(),
        )
        networks[name] = network
        activeName = name
        notifyAvailable(network)
        return network
    }

    /**
     * Simulates DHCP completing on an already connected access point. The handle is kept, so the
     * manager recognizes it as the network it already knew about.
     */
    fun makeUsable(name: String): WifiNetwork {
        val network = requireNotNull(networks[name]) { "no network named $name" }
        require(!network.isUsable) { "$name is already usable" }
        val usable = network.copy(localAddresses = listOf("192.168.4.20"))
        networks[name] = usable
        notifyAvailable(usable)
        return usable
    }

    /** Simulates a Wi-Fi network disconnecting. */
    fun lose(name: String) {
        val network = networks.remove(name) ?: return
        if (activeName == name) activeName = networks.keys.lastOrNull()
        for (listener in listeners.toList()) listener.onNetworkLost(network)
    }

    /** Network the manager's current socket was bound to, if any. */
    fun currentNetwork(): WifiNetwork? = boundSockets.lastOrNull()?.first

    fun socketsCreatedOn(): List<WifiNetwork> = boundSockets.map { it.first }

    override fun attachListener(listener: NetworkBinder.Listener): NetworkBinder.Listener {
        listeners.add(listener)
        // Report what is already connected, like ConnectivityManager does for a new callback.
        for (network in candidateNetworks()) listener.onNetworkAvailable(network)
        return listener
    }

    override fun detachListener(listener: NetworkBinder.Listener) {
        listeners.remove(listener)
    }

    override fun candidateNetworks(): List<WifiNetwork> {
        val active = networks[activeName]
        return networks.values
            .filter { it.isUsable }
            .sortedByDescending { it.handle == active?.handle }
    }

    override fun bindSocket(network: WifiNetwork, socket: DatagramSocket) {
        boundSockets.add(network to socket)
    }

    private fun notifyAvailable(network: WifiNetwork) {
        for (listener in listeners.toList()) listener.onNetworkAvailable(network)
    }
}
