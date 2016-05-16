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
import groovyx.net.http.Method
import groovyx.net.http.RESTClient
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException


@Slf4j
class NexusConnector {

    public final static String RESTPATH = 'service/local/'

    public final static String STAGINGPROFILE_TASK = RESTPATH + 'staging/profiles'
    public final static String STAGINGEVALUATE_TASK = RESTPATH + 'staging/profile_evaluate'
    public final static String STAGINGREPOSITORIES_TASK = RESTPATH + 'staging/profile_repositories'
    public final static String STAGINGBULK_TASK = RESTPATH + 'staging/bulk'

    public final static String STAGINGORDER_TASK = RESTPATH + 'staging/profile_order'
    public final static String STAGINGREPO_TASK = RESTPATH + 'staging/repository'
    public final static String STAGINGRULESETS = RESTPATH + 'staging/rule_sets'

    private RESTClient restClient

    NexusConnector(RESTClient restClient) {
        this.restClient = restClient
    }

    def assertWithoutSpace = { String name, String val ->
        if (val.contains(' ')) {
            throw new InvalidUserDataException("Spaces in id '${name}' are not allowed!")
        }
    }

    private static Map checkForID(List list, String id) {
        String resultID = ''
        String resultName = ''
        if (list) {
            list.each { def obj ->
                if (obj?.id && id == obj?.id) {
                    resultID = obj.id
                    resultName = obj.name
                    true
                }
            }
        }
        return [id: resultID, name: resultName]
    }

    private static List getResultObj(def resultObj) {
        def list = []
        if (resultObj != null && resultObj.containsKey('errors')) {
            List errors = resultObj.errors
            errors.each { def e ->
                log.error('Error Response: {}', e.msg)
                throw new GradleException("Request failed! Error messag: ${e.msg}")
            }
        } else if (resultObj != null && resultObj.containsKey('data')) {
            list = resultObj.data
        }
        return list
    }

    private List getResultList(String path) {
        def result =  NexusRestClientFactory.exec(restClient, [ path: path,
                                                      method: Method.GET])
        def list = (List)getResultObj(result)
        return list
    }

    public Map getProfileByID(String id) {
        def sp = checkForID(getResultList(STAGINGPROFILE_TASK, id))
        return sp
    }

    public Set getProfileForGAV(GAVObject gav) {
        String cg = gav.group ?: 'aaa.aaaaaaaaaa.aaaaaa'
        String ca = gav.artifact ?: 'aaaaaaaaaaaaa'
        String cv = gav.version ?: '0.0.0.0.00000000000000'

        def response =  NexusRestClientFactory.exec(restClient, [ path: STAGINGEVALUATE_TASK,
                                                        method: Method.GET,
                                                        requestQuery: [t: 'maven2', g: cg, a: ca, v: cv]])

        def stagingProfiles = []
        try {
            stagingProfiles = getResultObj(response)
        } catch (Exception ex) {
            log.error('No profiles found. Finished with exception.', ex)
        }

        if (stagingProfiles) {
            HashSet rSet= new HashSet()
            stagingProfiles.each { p ->
                rSet.add([profileID: p.id, profileName: p.name])
            }
            return rSet
        } else {
            log.info('No staging profiles available.')
        }
        return []
    }

    public List getOpenRepos(String profileID){
        return getReposForProfileID(profileID, 'open')
    }

    public List getReposByProfileID(String profileID, String type) {
        def cr = getProfileByID(profileID)
        if (!cr) {
            throw new InvalidUserDataException("Staging profile with ${profileID} does not exists.")
        }
        return getReposForProfileID(cr.id, type)
    }

    public boolean closeRepos(Set stagingRepositoryIDs, String message) {
        String body = """{"data": {"description": "${message}", "stagedRepositoryIds": ${stagingRepositoryIDs.unique()}}}""".stripIndent()
        
        def response =  NexusRestClientFactory.exec(restClient, [ path: "${STAGINGBULK_TASK}/close".toString(),
                                                        method: Method.POST,
                                                        requestBody: body])

        if (response && response instanceof Map && response.containsKey('errors')) {
            List errors = response.errors
            errors.each { def e ->
                log.error(e.msg)
            }
            return false
        }
        return true
    }

    public List getReposForProfileID(String profileID, String type) {
        def response =  NexusRestClientFactory.exec(restClient, [ path: "${STAGINGREPOSITORIES_TASK}/${profileID}",
                                                        method: Method.GET])
        def list = getResultObj(response)

        List srs = []
        if (list) {
            list.each { def sr ->
                if (sr.type == type) {
                    srs.add(sr)
                }
            }
        }
        return srs
    }

}
