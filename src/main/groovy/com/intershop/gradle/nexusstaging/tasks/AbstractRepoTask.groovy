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

import com.intershop.gradle.nexusstaging.util.GAVObject
import com.intershop.gradle.nexusstaging.util.NexusConnector
import com.intershop.gradle.nexusstaging.util.NexusRestClientFactory
import com.intershop.gradle.nexusstaging.util.UploadHttpClientFactory
import groovy.util.logging.Slf4j
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile

@Slf4j
class AbstractRepoTask extends DefaultTask {

    final static String STAGINGPATH = 'service/local/staging/deploy/maven2/'

    @Input
    String baseURL

    @Input
    String username

    @Input
    String password

    @InputDirectory
    File src

    @Input
    String repoDescription

    @OutputFile
    File resultProperties

    protected RESTClient getClient() {
        return NexusRestClientFactory.create(getBaseURL(), getUsername(), getPassword())
    }

    protected HTTPBuilder getUploaderClient() {
        String uploadURL = getBaseURL() + (getBaseURL().endsWith('/') ? '' : '/') + STAGINGPATH
        return UploadHttpClientFactory.create(uploadURL, getUsername(), getPassword())
    }

    /**
     * Identify ivy based modules of the directory
     **/
    static List<GAVObject> getModulesFromIvy(FileTree srcdir) {
        List<GAVObject> gavs = []
        FileTree ivyFiles = srcdir.matching { include '**/ivy*.xml' }
        ivyFiles.each {File f ->
            try {
                def rootNode = new XmlSlurper().parse(f)
                gavs.add(new GAVObject(rootNode.info.@organisation.toString(), rootNode.info.@module.toString(), rootNode.info.@revision.toString()))
            }catch(Exception ex) {
                log.warn('Analysis of {} failed!', f.getAbsolutePath())
            }
        }
        return gavs
    }

    /**
     * Identify maven based modules of the directory
     **/
    static List<GAVObject> getModulesFromPom(FileTree srcdir) {
        List<GAVObject> gavs = []
        FileTree ivyFiles = srcdir.matching { include '**/pom*.xml' }
        ivyFiles.each {File f ->
            try {
                def rootNode = new XmlSlurper().parse(f)
                gavs.add(new GAVObject(rootNode.groupId.toString(), rootNode.artifactId.toString(), rootNode.version.toString()))
            }catch(Exception ex) {
                log.warn('Analysis of {} failed!', f.getAbsolutePath())
            }
        }
        return gavs
    }

    /**
     * Get List of profiles for GAVs
     *
     * @param gavs
     * @return List of IDs of profiles
     */
    Set getProfiles(NexusConnector nc, List gavs) throws GradleException {
        HashSet profiles = new HashSet()

        gavs.each { GAVObject gav ->
            Set ps = nc.getProfileForGAV(gav)
            if(ps.size() < 1) {
                throw new GradleException("There is no profile for ${gav}!")
            }
            profiles.addAll(ps)
            project.logger.info('{} profile(s) for {} added to profile list.', ps.size(), gav )
        }

        return profiles
    }

    /**
     * List of open repos for profiles
     *
     * @param profiles
     * @return List of IDs of open repos
     */
    Set<String> getOpenRepos(NexusConnector nc, Set profiles) {
        HashSet openRepoIDs = new HashSet<String>()

        profiles.each {p ->
            List openRepos = nc.getOpenRepos(p.profileID)
            if(openRepos.size() > 0) {
                project.logger.info('There are {} open staging repositories for {}.', openRepos.size(), p.profileName)
                openRepos.each {or ->
                    openRepoIDs.add("'${or.repositoryId}'")
                    project.logger.info("Repo with description '{}' and timesstamp '{}' will be closed [{}].", or.description, or.createdTimestamp, or.repositoryId)
                }
            }
        }
        return openRepoIDs
    }


}
