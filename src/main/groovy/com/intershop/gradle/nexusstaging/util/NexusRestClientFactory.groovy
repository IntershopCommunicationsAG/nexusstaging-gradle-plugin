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
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import groovyx.net.http.URIBuilder
import org.gradle.api.GradleException

/**
 * This is the client for all Rest calls.
 */
@Slf4j
class NexusRestClientFactory {

    public final static String JSON = "application/json"
    public final static String TEXT = "application/text"
    public final static String PLAINTEXT = "text/plain"

    static Map<String, Object> defaultRequestConfig = [ method : RequestMethod.GET,
                                                        path : '',
                                                        requestQuery : [:],
                                                        requestHeaders : [:],
                                                        requestBody : null,
                                                        requestCT : ContentType.JSON,
                                                        responseCT : ContentType.JSON ]

    static RESTClient create(String url, String user, String password) {
        String pURL = url + (url.endsWith('/') ? '' : '/')
        HTTPClientHelper.validateParameters(pURL, user, password)
        RESTClient http = new RESTClient(new URIBuilder(pURL))

        // Must use preemptive auth for non-repeatable upload requests
        http.headers.Authorization = "Basic ${"$user:$password".toString().bytes.encodeBase64()}"

        HTTPClientHelper.configureAuthentication(http, user, password)

        // proxy configuration
        HTTPClientHelper.configureProxy(http, url, 'http')
        HTTPClientHelper.configureProxy(http, url, 'https')
        return http
    }

    static Object exec(RESTClient http, Map<String, Object> requestConfig) {

        // set default values
        defaultRequestConfig.each { key, value ->
            if(! requestConfig.containsKey(key)) {
                requestConfig.put(key, value)
            }
        }

        Map<String,?> args = [:]
        if(! requestConfig.path) {
            throw new IllegalArgumentException("client configuration 'path' cannot be empty!")
        } else {
            args.put('path', requestConfig.path)
        }
        if(requestConfig.requestQuery) {
            args.put('query', requestConfig.requestQuery)
        }
        if(requestConfig.requestHeaders) {
            args.put('headers', requestConfig.requestHeaders)
        }
        args.put('contentType', requestConfig.responseCT)
        args.put('requestContentType', requestConfig.requestCT)
        if(requestConfig.requestBody) {
            args.put('body', requestConfig.requestBody)
        }

        try {

            HttpResponseDecorator serverResponse = http."${requestConfig.method.toString().toLowerCase()}"(args)

            if (serverResponse.success && serverResponse.containsHeader('content-type')) {
                if (serverResponse.contentType == JSON.toString()) {
                    return serverResponse.data
                } else if (serverResponse.contentType == TEXT.toString() || serverResponse.contentType == PLAINTEXT.toString()) {
                    return serverResponse.data.toString()
                } else {
                    throw new GradleException("${serverResponse.contentType} is not supported for responses!")
                }
            }

            return [:]
        }catch(Exception ex) {

            if (ex instanceof HttpResponseException) {
                if (ex.getMessage().contains('Unauthorized')) {
                    return [errors: [[id: '*', msg: "Unauthorized"]]]
                } else {
                    return ex.response.data
                }
            } else {
                return [errors: [[id: '*', msg: "Unhandled: ${ex.message}\n" + ex.printStackTrace()]]]
            }
        }
    }
}
