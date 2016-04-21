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
package com.intershop.gradle.nexusstaging

import com.intershop.gradle.nexusstaging.util.GAVObject
import com.intershop.gradle.nexusstaging.util.NexusConnector
import com.intershop.gradle.nexusstaging.util.NexusRestClientFactory
import com.intershop.gradle.nexusstaging.util.UploadHttpClientFactory
import com.intershop.gradle.test.AbstractIntegrationSpec
import com.squareup.okhttp.mockwebserver.Dispatcher
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import com.squareup.okhttp.mockwebserver.RecordedRequest
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.junit.Rule

class NexusConnectionSpec extends AbstractIntegrationSpec {

    @Rule
    public final MockWebServer server = new MockWebServer()

    def 'test upload'() {
        given:
        copyResources('publish-test', 'publish-dir')

        String urlStr = server.url('nexus/content/repository').toString()

        final List<RecordedRequest> requestsMade = []
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                requestsMade.add(request)
                return new MockResponse()
            }
        }
        server.setDispatcher(dispatcher)

        when:

        HTTPBuilder http = UploadHttpClientFactory.create(urlStr, 'admin', 'admin123')
        UploadHttpClientFactory.upload(http, new File(testProjectDir, 'publish-dir'))

        RecordedRequest recordedRequest = server.takeRequest()

        then:
        requestsMade.size() == 1
        requestsMade[0].bodySize == new File(testProjectDir, 'publish-dir').directorySize()
        requestsMade[0].getHeader('Authorization') == 'Basic YWRtaW46YWRtaW4xMjM='
    }

    def 'test rest with staging profiles'() {
        given:
        String urlStr = server.url('nexus/service/local/staging/profiles').toString()
        String hostUrlStr = urlStr - '/service/local/staging/profiles'

        MockResponse response = new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Cache-Control", "no-cache")
                .setBody(
                """
                {"data":
                    [
                        {"resourceURI":"${hostUrlStr}/service/local/staging/profiles/19124939d9014",
                            "id":"19124939d9014","name":"Payment Sources",
                            "repositoryTemplateId":"default_hosted_release","repositoryType":"maven2",
                            "repositoryTargetId":"servicesSources","inProgress":false,
                            "order":2,"deployURI":"${hostUrlStr}/service/local/staging/deploy/maven2",
                            "targetGroups":["payment_dev"],"finishNotifyRoles":[],"promotionNotifyRoles":[],
                            "dropNotifyRoles":[],"closeRuleSets":[""],"promoteRuleSets":[""],
                            "promotionTargetRepository":"payment_sources","mode":"DEPLOY",
                            "finishNotifyCreator":true,"promotionNotifyCreator":true,"dropNotifyCreator":true,
                            "autoStagingDisabled":false,"repositoriesSearchable":true,
                            "properties":{"@class":"linked-hash-map"}},
                        {"resourceURI":"${hostUrlStr}/service/local/staging/profiles/191249aea1553",
                            "id":"191249aea1553","name":"Payment Build Information",
                            "repositoryTemplateId":"default_hosted_release","repositoryType":"maven2",
                            "repositoryTargetId":"servicesBuildInfo","inProgress":false,
                            "order":1,"deployURI":"${hostUrlStr}/service/local/staging/deploy/maven2",
                            "targetGroups":["payment_dev"],"finishNotifyRoles":[],"promotionNotifyRoles":[],
                            "dropNotifyRoles":[],"closeRuleSets":[""],"promoteRuleSets":[""],
                            "promotionTargetRepository":"payment_buildinfo","mode":"DEPLOY","finishNotifyCreator":true,
                            "promotionNotifyCreator":true,"dropNotifyCreator":true,"autoStagingDisabled":false,
                            "repositoriesSearchable":true,"properties":{"@class":"linked-hash-map"}},
                        {"resourceURI":"${hostUrlStr}/service/local/staging/profiles/19124894924ac",
                            "id":"19124894924ac","name":"Payment Releases",
                            "repositoryTemplateId":"default_hosted_release","repositoryType":"maven2",
                            "repositoryTargetId":"servicesReleases","inProgress":false,
                            "order":0,"deployURI":"${hostUrlStr}/service/local/staging/deploy/maven2",
                            "targetGroups":["payment_dev"],"finishNotifyRoles":[],"promotionNotifyRoles":[],
                            "dropNotifyRoles":[],"finishNotifyEmails":"amischur@intershop.de","promotionNotifyEmails":"amischur@intershop.de",
                            "dropNotifyEmails":"amischur@intershop.de","closeRuleSets":[""],"promoteRuleSets":[""],
                            "promotionTargetRepository":"payment_releases","mode":"DEPLOY","finishNotifyCreator":true,
                            "promotionNotifyCreator":true,"dropNotifyCreator":true,"autoStagingDisabled":false,"repositoriesSearchable":true,
                            "properties":{"@class":"linked-hash-map"}},
                        {"resourceURI":"${hostUrlStr}/service/local/staging/profiles/19124a3bc1582",
                            "id":"19124a3bc1582","name":"Payment Test",
                            "repositoryTemplateId":"default_group","repositoryType":"maven2","inProgress":false,
                            "order":3,"deployURI":"${hostUrlStr}/service/local/staging/deploy/maven2",
                            "targetGroups":["payment_test"],"finishNotifyRoles":[],"promotionNotifyRoles":[],
                            "dropNotifyRoles":[],"finishNotifyEmails":"amischur@intershop.de","promotionNotifyEmails":"amischur@intershop.de",
                            "dropNotifyEmails":"amischur@intershop.de","closeRuleSets":[""],"promoteRuleSets":[""],
                            "mode":"GROUP","finishNotifyCreator":true,"promotionNotifyCreator":true,"dropNotifyCreator":true,
                            "autoStagingDisabled":false,"repositoriesSearchable":true,"properties":{"@class":"linked-hash-map"}}
                    ]
                }""".stripIndent())

                server.enqueue(response)


        when:
        RESTClient http = NexusRestClientFactory.create(hostUrlStr, 'admin', 'admin123')

        Map<String, Object> requestConf = NexusRestClientFactory.defaultRequestConfig
        requestConf['path'] = NexusConnector.STAGINGPROFILE_TASK
        def result = NexusRestClientFactory.exec(http, requestConf)

        RecordedRequest recordedRequest = server.takeRequest()

        then:
        recordedRequest.method == 'GET'
        println result
    }

    def 'test rest with profile for GAV - empty list'() {
        given:
        String urlStr = server.url('nexus/service/local/staging/profile_evaluate').toString()
        String hostUrlStr = urlStr - '/service/local/staging/profile_evaluate'

        MockResponse response = new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Cache-Control", "no-cache")
                .setBody('{"errors":[{"id":"*","msg":"Unable to find matching profile"}]}')

        server.enqueue(response)

        when:
        RESTClient http = NexusRestClientFactory.create(hostUrlStr, 'admin', 'admin123')

        NexusConnector nc = new NexusConnector(http)
        Set l = nc.getProfileForGAV(new GAVObject('com.intershop.test', 'moduleTest', '1.2.3.4'))

        RecordedRequest recordedRequest = server.takeRequest()

        then:
        recordedRequest.method == 'GET'
        l.size() == 0

    }

    def 'test rest with profile for GAV with profile'() {
        given:
        String urlStr = server.url('nexus/service/local/staging/profile_evaluate').toString()
        String hostUrlStr = urlStr - '/service/local/staging/profile_evaluate'

        MockResponse response = new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Cache-Control", "no-cache")
                .setBody("""
                {"data": [
                    {
                      "resourceURI": "${hostUrlStr}/service/local/staging/profile_evaluate/19124894924ac",
                      "id": "19124894924ac",
                      "name": "Payment Releases",
                      "repositoryTemplateId": "default_hosted_release",
                      "repositoryType": "maven2",
                      "repositoryTargetId": "servicesReleases",
                      "inProgress": false,
                      "order": 0,
                      "deployURI": "${hostUrlStr}/service/local/staging/deploy/maven2",
                      "targetGroups": [
                        "payment_dev"
                      ],
                      "finishNotifyRoles": [],
                      "promotionNotifyRoles": [],
                      "dropNotifyRoles": [],
                      "finishNotifyEmails": "test@intershop.de",
                      "promotionNotifyEmails": "test@intershop.de",
                      "dropNotifyEmails": "test@intershop.de",
                      "closeRuleSets": [
                        ""
                      ],
                      "promoteRuleSets": [
                        ""
                      ],
                      "promotionTargetRepository": "payment_releases",
                      "mode": "DEPLOY",
                      "finishNotifyCreator": true,
                      "promotionNotifyCreator": true,
                      "dropNotifyCreator": true,
                      "autoStagingDisabled": false,
                      "repositoriesSearchable": true,
                      "properties": {
                        "@class": "linked-hash-map"
                      }
                    }
                  ]
                }""".stripIndent())

        server.enqueue(response)

        when:
        RESTClient http = NexusRestClientFactory.create(hostUrlStr, 'admin', 'admin123')

        NexusConnector nc = new NexusConnector(http)
        Set l = nc.getProfileForGAV(new GAVObject('com.intershop.test', 'moduleTest', '1.2.3.4'))

        RecordedRequest recordedRequest = server.takeRequest()

        then:
        recordedRequest.method == 'GET'
        l.size() == 1
        l[0].profileID == '19124894924ac'

    }

    def 'test rest open repository'() {
        given:
        String urlStr = server.url('nexus/service/local/staging/profile_repositories/19124894924ac').toString()
        String hostUrlStr = urlStr - '/service/local/staging/profile_repositories/19124894924ac'

        MockResponse response = new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Cache-Control", "no-cache")
                .setBody("""
                {"data": [
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1008",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1008",
                         "created": "2015-09-10T09:02:03.897+02:00",
                         "createdDate": "Thu Sep 10 09:02:03 CEST 2015",
                         "createdTimestamp": 1441868523897,
                         "updated": "2015-09-10T09:02:46.398+02:00",
                         "updatedDate": "Thu Sep 10 09:02:46 CEST 2015",
                         "updatedTimestamp": 1441868566398,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1008",
                         "parentGroupName": "payment_test-1008 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1009",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1009",
                         "created": "2015-09-10T09:02:21.964+02:00",
                         "createdDate": "Thu Sep 10 09:02:21 CEST 2015",
                         "createdTimestamp": 1441868541964,
                         "updated": "2015-09-10T09:03:06.111+02:00",
                         "updatedDate": "Thu Sep 10 09:03:06 CEST 2015",
                         "updatedTimestamp": 1441868586111,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1009",
                         "parentGroupName": "payment_test-1009 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1010",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1010",
                         "created": "2015-09-10T09:02:36.600+02:00",
                         "createdDate": "Thu Sep 10 09:02:36 CEST 2015",
                         "createdTimestamp": 1441868556600,
                         "updated": "2015-09-10T09:03:26.489+02:00",
                         "updatedDate": "Thu Sep 10 09:03:26 CEST 2015",
                         "updatedTimestamp": 1441868606489,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1010",
                         "parentGroupName": "payment_test-1010 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1011",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1011",
                         "created": "2015-09-10T09:02:56.100+02:00",
                         "createdDate": "Thu Sep 10 09:02:56 CEST 2015",
                         "createdTimestamp": 1441868576100,
                         "updated": "2015-09-10T09:03:34.726+02:00",
                         "updatedDate": "Thu Sep 10 09:03:34 CEST 2015",
                         "updatedTimestamp": 1441868614726,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1011",
                         "parentGroupName": "payment_test-1011 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1012",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1012",
                         "created": "2015-09-10T09:03:16.394+02:00",
                         "createdDate": "Thu Sep 10 09:03:16 CEST 2015",
                         "createdTimestamp": 1441868596394,
                         "updated": "2015-09-10T09:03:42.873+02:00",
                         "updatedDate": "Thu Sep 10 09:03:42 CEST 2015",
                         "updatedTimestamp": 1441868622873,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1012",
                         "parentGroupName": "payment_test-1012 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1013",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1013",
                         "created": "2015-10-20T11:11:01.602+02:00",
                         "createdDate": "Tue Oct 20 11:11:01 CEST 2015",
                         "createdTimestamp": 1445332261602,
                         "updated": "2015-10-20T11:11:14.728+02:00",
                         "updatedDate": "Tue Oct 20 11:11:14 CEST 2015",
                         "updatedTimestamp": 1445332274728,
                         "description": "Release Payment",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1013",
                         "parentGroupName": "payment_test-1013 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1014",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1014",
                         "created": "2015-10-21T14:59:00.137+02:00",
                         "createdDate": "Wed Oct 21 14:59:00 CEST 2015",
                         "createdTimestamp": 1445432340137,
                         "updated": "2015-10-21T14:59:11.266+02:00",
                         "updatedDate": "Wed Oct 21 14:59:11 CEST 2015",
                         "updatedTimestamp": 1445432351266,
                         "description": "Release Payment",
                         "provider": "maven2",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1014",
                         "parentGroupName": "payment_test-1014 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1015",
                         "type": "promoted",
                         "policy": "release",
                         "userId": "deploy",
                         "ipAddress": "10.0.29.214",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1015",
                         "created": "2015-10-26T16:41:12.793+01:00",
                         "createdDate": "Mon Oct 26 16:41:12 CET 2015",
                         "createdTimestamp": 1445874072793,
                         "updated": "2015-10-26T16:41:23.416+01:00",
                         "updatedDate": "Mon Oct 26 16:41:23 CET 2015",
                         "updatedTimestamp": 1445874083416,
                         "description": "Release Payment",
                         "provider": "maven2",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "parentGroupId": "payment_test-1015",
                         "parentGroupName": "payment_test-1015 (staging: group)",
                         "notifications": 0,
                         "transitioning": false
                     },
                     {
                         "profileId": "19124894924ac",
                         "profileName": "Payment Releases",
                         "profileType": "repository",
                         "repositoryId": "payment_releases-1016",
                         "type": "open",
                         "policy": "release",
                         "userId": "admin",
                         "ipAddress": "10.0.10.10",
                         "repositoryURI": "${hostUrlStr}/content/repositories/payment_releases-1016",
                         "created": "2015-11-28T21:10:43.174+01:00",
                         "createdDate": "Sat Nov 28 21:10:43 CET 2015",
                         "createdTimestamp": 1448741443174,
                         "updated": "2015-11-28T21:10:43.471+01:00",
                         "updatedDate": "Sat Nov 28 21:10:43 CET 2015",
                         "updatedTimestamp": 1448741443471,
                         "description": "Implicitly created (auto staging).",
                         "provider": "maven2",
                         "releaseRepositoryId": "payment_releases",
                         "releaseRepositoryName": "Payment Releases",
                         "notifications": 0,
                         "transitioning": false
                     }]
                }""".stripIndent())

        server.enqueue(response)

        when:
        RESTClient http = NexusRestClientFactory.create(hostUrlStr, 'admin', 'admin123')

        NexusConnector nc = new NexusConnector(http)
        List l = nc.getOpenRepos('19124894924ac')

        RecordedRequest recordedRequest = server.takeRequest()

        then:
        recordedRequest.method == 'GET'
        l.size() == 1
        l[0].description == 'Implicitly created (auto staging).'
        l[0].createdTimestamp == 1448741443174
        l[0].repositoryId == 'payment_releases-1016'
    }
}
