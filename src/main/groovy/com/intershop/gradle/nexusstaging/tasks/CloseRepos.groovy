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


package com.intershop.gradle.nexusstaging.tasks

import com.intershop.gradle.nexusstaging.util.NexusConnector
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class CloseRepos extends AbstractRepoTask {

    CloseRepos() {
        this.description = 'Closes open staging repositories on Sonatype Nexus Repository'
        this.group = 'Nexus Staging Tasks'
        this.outputs.upToDateWhen { false }
    }

    @TaskAction
    void run() {
        // identify staging profiles
        NexusConnector nc = new NexusConnector(getClient())

        def gavs = []
        // find ivy files and identify groups, modules and versions
        gavs.addAll(getModulesFromIvy(project.fileTree(dir: getSrc().absolutePath)))
        // find pom files and identify groups, modules and versions
        gavs.addAll(getModulesFromPom(project.fileTree(dir: getSrc().absolutePath)))

        def profiles = getProfiles(nc, gavs)

        project.logger.info('Check for open repos and close.')
        Set openRepoIDs = getOpenRepos(nc, profiles)

        if(openRepoIDs.size() > 0) {
            boolean closedSuccess = nc.closeRepos(openRepoIDs, "${getRepoDescription()}")

            if(closedSuccess) {

                println """
                    -------
                    Repositories ${openRepoIDs.toString()} were closed on ${getBaseURL()}
                     - The description is '${getRepoDescription()}'
                     - This information is stored also in ${getResultProperties().absolutePath}
                    -------
                """.stripIndent()

                project.logger.info('Write properties with infos to {}', getResultProperties().absolutePath)
                Properties props = new Properties()
                props.setProperty('nexus.closed.repoIDs', openRepoIDs.toString())
                props.setProperty('nexus.closed.message', getRepoDescription())
                props.setProperty('nexus.url', getBaseURL())

                props.store(getResultProperties().newWriter(), 'Write properties after publish')
            } else {
                println """
                    -------
                    !  Closing of repositories ${openRepoIDs.toString()} on ${getBaseURL()} failed
                    ! - The status of the listed repositories is unknown!
                    -------
                """.stripIndent()

                throw new GradleException('Closing of open Repos failed!')
            }
        }
    }
}
