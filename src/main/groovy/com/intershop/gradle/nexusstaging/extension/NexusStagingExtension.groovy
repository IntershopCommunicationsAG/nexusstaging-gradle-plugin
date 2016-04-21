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

@Slf4j
class NexusStagingExtension {
    private Project project

    public static final String NEXUSSTAGING_EXTENSION_NAME = 'nexusStaging'

    // run on CI server
    public final static String RUNONCI_ENV = 'RUNONCI'
    public final static String RUNONCI_PRJ = 'runOnCI'

    public static final String NEXUSCOPY_TASK_NAME = 'upload'
    public static final String NEXUSCLOSE_TASK_NAME = 'closeRepos'

    public static final String NEXUSSTAGING_GROUP_NAME = 'Nexus Staging'

    NexusStagingExtension(Project project) {
        this.project = project
        // initialize server configuration
        server = new Server(project)

        resultPropertiesFile = new File(project.getBuildDir(), 'staging/results/repotransfer.properties')
        repositoryDir = new File(project.getBuildDir(), 'staging/repo')
        description = "${project.getName()} transfer to repo for version ${project.getVersion()}"

        // init default value for runOnCI
        if(! runOnCI) {
            runOnCI = Boolean.parseBoolean(getVariable(project, RUNONCI_ENV, RUNONCI_PRJ, 'false'))
            if(runOnCI) {
                log.warn('Nexus Staging task will be executed on a CI build environment for {}.', project.name)
            }
        }
    }

    /**
     * <p>Configuration for the execution on the CI server</p>
     *
     * <p>Can be configured/overwritten with environment variable RUNONCI;
     * java environment RUNONCI or project variable runOnCI</p>
     */
    boolean runOnCI

    /**
     * Snapshots will be not staged
     */
    boolean forSnapshots

    /**
     * Server configuration
     */
    Server server

    Server server(Closure closure) {
        project.configure(server, closure)
    }

    /**
     * description for staging repo
     */
    String description

    /**
     * Properties with staging information
     */
    File resultPropertiesFile

    /**
     * Local repository directory
     */
    File repositoryDir

    /**
     * Calculates the setting for special configuration from the system
     * or java environment or project properties.
     *
     * @param envVar        name of environment variable
     * @param projectVar    name of project variable
     * @param defaultValue  default value
     * @return              the string configuration
     */
    static String getVariable(Project project, String envVar, String projectVar, String defaultValue = '') {
        if(System.properties[envVar]) {
            log.debug('Specified from system property {}.', envVar)
            return System.properties[envVar].toString().trim()
        } else if(System.getenv(envVar)) {
            log.debug('Specified from system environment property {}.', envVar)
            return System.getenv(envVar).toString().trim()
        } else if(project.hasProperty(projectVar) && project."${projectVar}") {
            log.debug('Specified from project property {}.', projectVar)
            return project."${projectVar}".toString().trim()
        }
        return defaultValue
    }
}
