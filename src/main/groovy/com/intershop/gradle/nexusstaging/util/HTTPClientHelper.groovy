/*
 * Copyright 2015 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intershop.gradle.nexusstaging.util

import groovy.util.logging.Slf4j
import groovyx.net.http.HTTPBuilder
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials

@Slf4j
class HTTPClientHelper {

    static validateParameters(String url, String user, String password) {
        def assertNotEmpty = { String name, String val ->
            if (val?.isEmpty()) {
                throw new IllegalArgumentException("Nexus configuration $name cannot be empty!")
            }
        }

        assertNotEmpty('url', url)
        assertNotEmpty('user', user)
        assertNotEmpty('password', password)
    }

    static configureAuthentication(HTTPBuilder http, String user, String password) {
        // Must use preemptive auth for non-repeatable upload requests
        http.headers.Authorization = "Basic ${"$user:$password".toString().bytes.encodeBase64()}"
        http.auth.basic(user, password)
    }

    static configureProxy(HTTPBuilder http, String url, String protocol) {
        if(System.getProperty("${protocol}.proxyHost") && url.startsWith("${protocol}:")) {
            String proxyHost = System.getProperty("${protocol}.proxyHost")
            Integer proxyPort = Integer.parseInt(System.getProperty("${protocol}.proxyPort", (protocol == 'http' ? 80 : 443)))
            String proxyUser = System.getProperty("${protocol}.proxyUser")
            String proxyPassword = System.getProperty("${protocol}.proxyPassword", '')
            log.info "Using proxy ${proxyUser}:XXX@${proxyHost}:${proxyPort}"
            if (proxyUser) {
                http.client.getCredentialsProvider().setCredentials(
                        new AuthScope(proxyHost, proxyPort),
                        new UsernamePasswordCredentials(proxyUser, proxyPassword)
                )
            }
            http.setProxy(proxyHost, proxyPort, protocol)
        }
    }
}
