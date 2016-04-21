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


package com.intershop.gradle.nexusstaging.extension

import groovy.util.logging.Slf4j
import org.gradle.api.Project

/**
 * This is the basic Nexus server configuration.
 */
@Slf4j
class Server {

    private Project project

    public final static String SERVER_USER_NAME_ENV = 'NEXUSUSERNAME'
    public final static String SERVER_USER_NAME_PRJ = 'nexusUserName'

    public final static String SERVER_USER_PASSWORD_ENV = 'NEXUSUSERPASSWD'
    public final static String SERVER_USER_PASSWORD_PRJ = 'nexusUserPASSWD'

    public final static String SERVER_BASEURL_ENV = 'NEXUSBASEURL'
    public final static String SERVER_BASEURL_PRJ = 'nexusBaseURL'

    Server(Project project) {
        baseURL = NexusStagingExtension.getVariable(project, SERVER_BASEURL_ENV, SERVER_BASEURL_PRJ)
        username = NexusStagingExtension.getVariable(project, SERVER_USER_NAME_ENV, SERVER_USER_NAME_PRJ)
        password = NexusStagingExtension.getVariable(project, SERVER_USER_PASSWORD_ENV, SERVER_USER_PASSWORD_PRJ)
    }

    /**
     * Base URL of the nexus instance
     */
    String baseURL

    /**
     * Login of the user witht the correct privileges
     */
    String username

    /**
     * Password of the user
     */
    String password
}
