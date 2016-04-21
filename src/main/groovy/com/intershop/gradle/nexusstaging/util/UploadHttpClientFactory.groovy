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

import groovy.io.FileType
import groovy.util.logging.Slf4j
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.URIBuilder
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.HttpContext
import org.gradle.api.GradleException

import static groovyx.net.http.ContentType.BINARY

/**
 * This is a fork of the http client factory of the gradle-bintray-plugin.
 *
 * It creates a http client
 */
@Slf4j
class UploadHttpClientFactory {

    static HTTPBuilder create(String url, String user, String password) {

        HTTPClientHelper.validateParameters(url, user, password)

        def http = new HTTPBuilder(new URIBuilder(url))

        HTTPClientHelper.configureAuthentication(http, user, password)

        //Set an entity with a length for a stream that has the totalBytes method on it
        def er = new EncoderRegistry() {
            @Override
            InputStreamEntity encodeStream(Object data, Object contentType) throws UnsupportedEncodingException {
                if (data.metaClass.getMetaMethod("totalBytes")) {
                    InputStreamEntity entity = new InputStreamEntity((InputStream) data, data.totalBytes())
                    entity.setContentType(contentType.toString())
                    entity
                } else {
                    super.encodeStream(data, contentType)
                }
            }
        }
        http.encoders = er

        //No point in retrying non-repeatable upload requests
        http.client.httpRequestRetryHandler = new DefaultHttpRequestRetryHandler(0, false)

        //Follow permanent redirects for PUTs
        http.client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                def redirected = super.isRedirected(request, response, context)
                return redirected || response.getStatusLine().getStatusCode() == 301
            }

            @Override
            HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context)
                    throws org.apache.http.ProtocolException {
                URI uri = getLocationURI(request, response, context)
                String method = request.requestLine.method
                if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
                    return new HttpHead(uri)
                } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
                    return new HttpPut(uri)
                } else {
                    return new HttpGet(uri)
                }
            }
        })

        // proxy configuration
        HTTPClientHelper.configureProxy(http, url, 'http')
        HTTPClientHelper.configureProxy(http, url, 'https')
        return http
    }

    public static void upload(HTTPBuilder http, File baseDirectory, boolean dryRun = false) {
        baseDirectory.eachFileRecurse(FileType.FILES) { File file ->
            if(! file.name.startsWith('maven-metadata')) {
                try {
                    file.withInputStream { InputStream is ->
                        is.metaClass.totalBytes = {
                            file.length()
                        }
                        log.debug('Start upload of {}', file.absolutePath)
                        if (dryRun) {
                            logger.info("(Dry run) Uploaded to '$apiUrl$uploadUri'.")
                            return
                        }

                        String uriPath = file.toURI().toString() - baseDirectory.toURI().toString()

                        http.request(Method.PUT) {
                            uri.path = uriPath
                            requestContentType = BINARY
                            body = is

                            response.success = { resp ->
                                log.debug("Uploaded to '{}'.", uriPath)
                            }
                            response.failure = { resp, reader ->
                                throw new GradleException("Could not upload to '${uriPath}': ${resp.statusLine} ${reader}")
                            }
                        }
                    }
                }catch (Exception ex) {
                    ex.printStackTrace()
                    throw new GradleException("Could not upload '${file.absolutePath}'")
                }
            }
        }
    }
}

