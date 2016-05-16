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
import com.intershop.gradle.nexusstaging.util.UploadHttpClientFactory
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class CopyToRepo extends AbstractRepoTask {

    CopyToRepo() {
        this.description = 'Transfers artifacts to Sonatype Nexus Repository'
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

        HashSet profiles = getProfiles(nc, gavs)

        if(profiles.size() == 0) {
            throw new GradleException('No profile found for prepared artifacts. Please check your configuration!')
        }

        project.logger.info('Check for open repos and close.')
        Set openRepoIDs = getOpenRepos(nc, profiles)

        if (openRepoIDs.size() > 0) {
            boolean prepareClose = nc.closeRepos(openRepoIDs, "Closed before publishing '(${getRepoDescription()})'")
            if (!prepareClose) {
                throw new GradleException('Publishing to staging repo failed! (open repos)')
            }
        }

        project.logger.info('Upload files from {} to {}', getSrc().absolutePath, getBaseURL() + (getBaseURL().endsWith('/') ? '' : '/') + STAGINGPATH)
        UploadHttpClientFactory.upload(getUploaderClient(), getSrc())

        project.logger.info('Close repos after publishing')
        Set<String> publishedOpenRepoIDs = getOpenRepos(nc, profiles)

        project.logger.info('Publish open repos: {}', publishedOpenRepoIDs.size())

        if (publishedOpenRepoIDs.size() > 0) {
            boolean closedSuccess = false
            closedSuccess = nc.closeRepos(publishedOpenRepoIDs, getRepoDescription())

            if (closedSuccess) {

                println """
                    -------
                    Artifacts are published to staging repositories ${publishedOpenRepoIDs.toString()} on ${
                    getBaseURL()
                }
                     - The status of the listed repositories is closed
                     - The description is '${getRepoDescription()}'
                     - This information is stored also in ${getResultProperties().absolutePath}
                    -------
                """.stripIndent()

                project.logger.info('Write properties with infos to {}', getResultProperties().absolutePath)
                Properties props = new Properties()
                props.setProperty('nexus.staging.repoIDs', publishedOpenRepoIDs.toString())
                props.setProperty('nexus.staging.message', getRepoDescription())
                props.setProperty('nexus.url', getBaseURL())

                props.store(getResultProperties().newWriter(), 'Write properties after publish')
            } else {

                println """
                    -------
                    ! Artifacts are published to staging repo(s) ${publishedOpenRepoIDs.toString()} on ${
                    getBaseURL()
                }
                    ! - The status of the listed repositories is unknown!
                    -------
                """.stripIndent()

                throw new GradleException('Publishing to staging repo failed! (close failed)')
            }
        }
    }
}
