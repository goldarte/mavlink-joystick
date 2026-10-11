package com.goldarte.mavlinkjoystick.mavlink

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Wi-Fi network that MAVLink traffic can use.
 *
 * The manager never touches [Network] directly, which keeps the link logic free of Android
 * framework classes and therefore testable on the host JVM.
 */
internal data class WifiNetwork(
    val handle: Any,
    val description: String,
    val localAddresses: List<String> = emptyList(),
    /** Subnet broadcast address, used to greet a drone whose address is not known yet. */
    val broadcastAddress: String? = null,
) {
    /** The network has a routable IPv4 address and can actually carry MAVLink packets. */
    val isUsable: Boolean get() = localAddresses.isNotEmpty()

    override fun equals(other: Any?): Boolean = other is WifiNetwork && other.handle == handle

    override fun hashCode(): Int = handle.hashCode()
}

/**
 * Platform side of the Android transport: reports which Wi-Fi networks MAVLink traffic may use and
 * binds a UDP socket to one of them.
 *
 * Telemetry Wi-Fi (the drone access point) usually has no internet access, so the transport
 * deliberately ignores the default network selection of the OS and always works with a Wi-Fi
 * network that is currently up.
 */
internal interface NetworkBinder {

    /** Starts reporting network changes; returns the token used to stop it again. */
    fun attachListener(listener: Listener): Listener

    fun detachListener(listener: Listener)

    /**
     * Wi-Fi networks MAVLink traffic may use, most preferred first. Empty while no Wi-Fi network
     * with an address is connected.
     */
    fun candidateNetworks(): List<WifiNetwork>

    /** Binds [socket] so that its traffic leaves through [network]. */
    @Throws(Exception::class)
    fun bindSocket(network: WifiNetwork, socket: java.net.DatagramSocket)

    interface Listener {
        /** A usable Wi-Fi network appeared, or a known one became usable again. */
        fun onNetworkAvailable(network: WifiNetwork)

        /** A Wi-Fi network disappeared. */
        fun onNetworkLost(network: WifiNetwork)
    }
}

internal class AndroidNetworkBinder(
    context: Context?,
) : NetworkBinder {

    private val connectivityManager =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val listeners = CopyOnWriteArrayList<NetworkBinder.Listener>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publishAvailable(network)
        }

        override fun onLost(network: Network) {
            val wifi = describeNetwork(network)
            for (listener in listeners) {
                listener.onNetworkLost(wifi)
            }
        }

        // A Wi-Fi network can appear before it owns an address (and therefore before it can carry
        // packets). These callbacks mark the moment it became usable, so the transport can re-arm.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            publishAvailable(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            publishAvailable(network)
        }
    }

    private var started = false

    override fun attachListener(listener: NetworkBinder.Listener): NetworkBinder.Listener {
        listeners.add(listener)
        start()
        // Report what is already connected: a callback registered later would otherwise stay silent
        // until the next network change, which is exactly the stuck state this code must avoid.
        for (network in candidateNetworks()) listener.onNetworkAvailable(network)
        return listener
    }

    override fun detachListener(listener: NetworkBinder.Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) stop()
    }

    private fun start() {
        if (started) return
        started = true
        try {
            // NET_CAPABILITY_INTERNET is deliberately not requested: a telemetry access point has
            // no internet and would otherwise be filtered out.
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            if (connectivityManager == null) {
                started = false
            } else {
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            started = false
            Log.e("MavlinkManager", "Failed to register network callback", e)
        }
    }

    private fun stop() {
        if (!started) return
        started = false
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Registration may have failed.
        }
    }

    @Suppress("DEPRECATION") // allNetworks is still the simplest way to see every Wi-Fi network.
    override fun candidateNetworks(): List<WifiNetwork> {
        val manager = connectivityManager ?: return emptyList()
        val candidates = LinkedHashMap<Network, WifiNetwork>()
        val activeNetwork = try {
            manager.activeNetwork
        } catch (_: Exception) {
            null
        }
        // Prefer the network Android currently routes through when it is Wi-Fi.
        if (activeNetwork != null && manager.isWifi(activeNetwork)) {
            addNetwork(manager, candidates, activeNetwork)
        }
        try {
            for (network in manager.allNetworks) {
                if (network != activeNetwork && manager.isWifi(network)) {
                    addNetwork(manager, candidates, network)
                }
            }
        } catch (e: Exception) {
            Log.w("MavlinkManager", "Cannot enumerate available networks", e)
        }
        return candidates.values.filter { it.isUsable }
    }

    override fun bindSocket(network: WifiNetwork, socket: java.net.DatagramSocket) {
        val handle = network.handle
        if (handle is Network) handle.bindSocket(socket)
    }

    private fun publishAvailable(network: Network) {
        val wifi = describeNetwork(network)
        for (listener in listeners) {
            listener.onNetworkAvailable(wifi)
        }
    }

    private fun addNetwork(
        manager: ConnectivityManager,
        into: MutableMap<Network, WifiNetwork>,
        network: Network,
    ) {
        into.getOrPut(network) { describeNetwork(network) }
    }

    private fun describeNetwork(network: Network): WifiNetwork {
        val linkProperties = try {
            connectivityManager?.getLinkProperties(network)
        } catch (_: Exception) {
            null
        }
        return WifiNetwork(
            handle = network,
            description = describe(network),
            localAddresses = localAddresses(linkProperties),
            broadcastAddress = broadcastAddress(linkProperties),
        )
    }

    private fun describe(network: Network): String = "wifi(netId=${network.networkHandle})"

    private fun localAddresses(linkProperties: LinkProperties?): List<String> =
        linkProperties?.linkAddresses.orEmpty().mapNotNull { linkAddress ->
            val address = linkAddress.address ?: return@mapNotNull null
            // MAVLink here is IPv4 only, and an IPv6-only interface cannot carry it.
            if (address.address?.size != IPV4_ADDRESS_BYTES) return@mapNotNull null
            address.hostAddress
        }

    /** Subnet broadcast address of the joined network, if it has an IPv4 address. */
    private fun broadcastAddress(linkProperties: LinkProperties?): String? {
        if (linkProperties == null) return null
        val linkAddress = linkProperties.linkAddresses.firstOrNull {
            it.address?.address?.size == IPV4_ADDRESS_BYTES
        } ?: return null
        return broadcastAddressFor(
            hostAddress = linkAddress.address?.hostAddress ?: return null,
            prefixLength = linkAddress.prefixLength,
        )
    }

    private fun ConnectivityManager.isWifi(network: Network): Boolean = try {
        getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Exception) {
        false
    }
}

/**
 * Broadcast address of the subnet [hostAddress] belongs to, or `null` when the address or prefix
 * cannot describe an IPv4 subnet.
 *
 * Pure string arithmetic on purpose: it keeps the calculation independent of the platform
 * [InetAddress] implementation and verifiable on the host JVM.
 */
internal fun broadcastAddressFor(hostAddress: String, prefixLength: Int): String? {
    if (prefixLength !in 1..IPV4_ADDRESS_BITS) return null
    val octets = hostAddress.split(".")
    if (octets.size != IPV4_ADDRESS_BYTES) return null
    val hostBytes = octets.map { octet ->
        val value = octet.toIntOrNull() ?: return null
        if (value !in 0..MAX_OCTET) return null
        value
    }

    // Build the address as an unsigned 32 bit number with bit 31 as the leftmost octet, so the
    // subnet mask and its inverted host bits line up with the octets without any byte shuffling.
    var host = 0L
    for (octet in hostBytes) {
        host = (host shl 8) or octet.toLong()
    }
    val hostBits = (1L shl (IPV4_ADDRESS_BITS - prefixLength)) - 1L
    val broadcast = host or hostBits

    return (IPV4_ADDRESS_BYTES - 1 downTo 0).joinToString(".") { index ->
        ((broadcast shr (8 * index)) and MAX_OCTET.toLong()).toString()
    }
}

private const val IPV4_ADDRESS_BYTES = 4
private const val IPV4_ADDRESS_BITS = 32
private const val MAX_OCTET = 0xFF
