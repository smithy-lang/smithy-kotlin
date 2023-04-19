/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.net.Url
import aws.smithy.kotlin.runtime.util.*

/**
 * Select a proxy via environment. This selector will look for
 *
 * **JVM System Properties**:
 * - `http.proxyHost`
 * - `http.proxyPort`
 * - `https.proxyHost`
 * - `https.proxyPort`
 * - `http.noProxyHosts`
 *
 * **Environment variables in the given order**:
 * - `http_proxy`, `HTTP_PROXY`
 * - `https_proxy`, `HTTPS_PROXY`
 * - `no_proxy`, `NO_PROXY`
 */
internal class EnvironmentProxySelector(provider: PlatformEnvironProvider = PlatformProvider.System) : ProxySelector {
    private val httpProxy =
        resolveProxyByProperty(provider, Scheme.HTTP) ?: resolveProxyByEnvironment(provider, Scheme.HTTP)
    private val httpsProxy =
        resolveProxyByProperty(provider, Scheme.HTTPS) ?: resolveProxyByEnvironment(provider, Scheme.HTTPS)
    private val noProxyHosts = resolveNoProxyHosts(provider)

    override fun select(url: Url): ProxyConfig {
        if (httpProxy == null && httpsProxy == null || noProxy(url)) return ProxyConfig.Direct

        val proxyConfig = when (url.scheme) {
            Scheme.HTTP -> httpProxy
            Scheme.HTTPS -> httpsProxy
            else -> null
        }

        return proxyConfig ?: ProxyConfig.Direct
    }

    private fun noProxy(url: Url): Boolean = noProxyHosts.any { it.matches(url) }
}

private fun resolveProxyByProperty(provider: PropertyProvider, scheme: Scheme): ProxyConfig? {
    val hostPropName = "${scheme.protocolName}.proxyHost"
    val hostPortPropName = "${scheme.protocolName}.proxyPort"

    val proxyHostProp = provider.getProperty(hostPropName)
    val proxyPortProp = provider.getProperty(hostPortPropName)

    return proxyHostProp?.let { hostName ->
        // we don't support connecting to the proxy over TLS, we expect engines would support
        // tunneling https traffic via HTTP Connect to the proxy
        val proxyProtocol = Scheme.HTTP

        val url = try {
            Url(proxyProtocol, Host.parse(hostName), proxyPortProp?.toInt() ?: scheme.defaultPort)
        } catch (e: Exception) {
            val parsed = buildString {
                append("""$hostPropName="$proxyHostProp"""")
                proxyPortProp?.let { append(""", $hostPortPropName="$it"""") }
            }
            throw ClientException("Could not parse $parsed into a valid proxy URL", e)
        }

        ProxyConfig.Http(url)
    }
}

private fun resolveProxyByEnvironment(provider: EnvironmentProvider, scheme: Scheme): ProxyConfig? =
    // lowercase takes precedence: https://about.gitlab.com/blog/2021/01/27/we-need-to-talk-no-proxy/
    listOf("${scheme.protocolName.lowercase()}_proxy", "${scheme.protocolName.uppercase()}_PROXY")
        .firstNotNullOfOrNull { envVar ->
            provider.getenv(envVar)?.let { proxyUrlString ->
                val url = try {
                    Url.parse(proxyUrlString)
                } catch (e: Exception) {
                    val parsed = """$envVar="$proxyUrlString""""
                    throw ClientException("Could not parse $parsed into a valid proxy URL", e)
                }
                ProxyConfig.Http(url)
            }
        }

internal data class NoProxyHost(val hostMatch: String, val port: Int? = null) {
    fun matches(url: Url): Boolean {
        // any host
        if (hostMatch == "*") return true

        // specific port to proxy otherwise matches all ports
        if (port != null && url.port != port) return false

        val name = url.host.toString()

        if (hostMatch.length > name.length) return false

        val match = name.endsWith(hostMatch)
        // either -1 or will point to the first index in name that differs
        val startIdx = name.length - hostMatch.length - 1

        // either exact match or subdomain
        return match && (startIdx < 0 || name[startIdx] == '.')
    }
}

private fun parseNoProxyHost(raw: String): NoProxyHost {
    val pair = raw.split(':', limit = 2)
    return when (pair.size) {
        1 -> NoProxyHost(pair[0])
        2 -> NoProxyHost(pair[0], pair[1].toInt())
        else -> error("invalid no proxy host: $raw")
    }
}

private fun resolveNoProxyHosts(provider: PlatformEnvironProvider): Set<NoProxyHost> {
    // http.nonProxyHosts:a list of hosts that should be reached directly, bypassing the proxy. This is a list of
    // patterns separated by '|'. The patterns may start or end with a '*' for wildcards. Any host matching one of
    // these patterns will be reached through a direct connection instead of through a proxy.
    val noProxyHostProps = provider.getProperty("http.noProxyHosts")
        ?.split('|')
        ?.map { it.trim() }
        ?.map { it.trimStart('.') }
        ?.map(::parseNoProxyHost)
        ?.toSet() ?: emptySet()

    // `no_proxy` is a comma or space-separated list of machine or domain names, with optional :port part.
    // If no :port part is present, it applies to all ports on that domain.
    val noProxyEnv = listOf("no_proxy", "NO_PROXY")
        .mapNotNull { provider.getenv(it) }
        .flatMap { it.split(',', ' ') }
        .map { it.trim() }
        .map { it.trimStart('.') }
        .map(::parseNoProxyHost)
        .toSet()

    return noProxyHostProps + noProxyEnv
}
