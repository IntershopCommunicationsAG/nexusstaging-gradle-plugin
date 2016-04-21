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

import com.intershop.gradle.test.AbstractIntegrationSpec
import com.squareup.okhttp.mockwebserver.Dispatcher
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import com.squareup.okhttp.mockwebserver.RecordedRequest
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Unroll
class PluginMockSpec extends AbstractIntegrationSpec {

    @Rule
    public final MockWebServer server = new MockWebServer()

    def 'test publish functionality - build file config - #gradleVersion'(gradleVersion) {
        given:
        directory('build/repository-testmodule')
        copyResources('repository-test', 'build/repository-testmodule')

        String urlStr = server.url('nexus').toString()
        final List<RecordedRequest> requestsMade = []
        server.setDispatcher(getPublishTestDispatcher(urlStr, requestsMade))

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file('build/repository-testmodule')
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }


            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('upload', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(":upload").outcome == SUCCESS
        properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish functionality - parameter config - #gradleVersion'(gradleVersion) {
        given:
        copyResources('repository-test', 'build/staging/repo')

        String urlStr = server.url('nexus').toString()
        final List<RecordedRequest> requestsMade = []
        server.setDispatcher(getPublishTestDispatcher(urlStr, requestsMade))

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:

        def result = getPreparedGradleRunner()
                .withArguments('upload', '-PrunOnCI=true', "-PnexusBaseURL=${urlStr}", '-PnexusUserName=admin', '-PnexusUserPASSWD=admin123', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/staging/results/repotransfer.properties')

        then:
        result.task(":upload").outcome == SUCCESS
        properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test repo close functionality - build file config - #gradleVersion'(gradleVersion) {
        given:
        directory('build/repository-testmodule')
        copyResources('repository-test', 'build/repository-testmodule')

        String urlStr = server.url('nexus').toString()

        final List<RecordedRequest> requestsMade = []
        server.setDispatcher(getPublishTestDispatcher(urlStr, requestsMade))

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file('build/repository-testmodule')
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }


            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('closeRepos', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(":closeRepos").outcome == SUCCESS
        properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish with publish integration on multiproject - #gradleVersion'(gradleVersion) {
        given:
        String urlStr = server.url('nexus').toString()
        List<String> upLoadList = []
        server.setDispatcher(getPublishTestDispatchForTest(urlStr, upLoadList))

        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
                id 'com.intershop.gradle.nexusstaging'
            }

            group = 'com.intershop'
            version = '1.0.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            subprojects {
                apply plugin: 'ivy-publish'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file("\${rootProject.buildDir}/repo")
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsfile = file('settings.gradle')
        settingsfile << """
            // define root proejct name
            rootProject.name = 'testProject'
        """.stripIndent()
        createSubProjectJava('project1a', settingsfile, 'com.intereshop.a')
        createSubProjectJava('project2a', settingsfile, 'com.intereshop.b')
        createSubProjectJava('project2b', settingsfile, 'com.intereshop.b')
        createSubProjectJava('project2c', settingsfile, 'com.intereshop.b')
        writeJavaTestClass('com.intershop.root')

        when:
        def result = getPreparedGradleRunner()
                .withArguments('publish', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(':project1a:publishIvyPublicationToIvyNexusStagingRepository').outcome == SUCCESS
        result.task(':project2a:publishIvyPublicationToIvyNexusStagingRepository').outcome == SUCCESS
        result.task(':upload').outcome == SUCCESS
        properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish with publish integration on single project - #gradleVersion'(gradleVersion) {
        given:
        String urlStr = server.url('nexus').toString()
        List<String> upLoadList = []
        server.setDispatcher(getPublishTestDispatchForTest(urlStr, upLoadList))

        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
                id 'com.intershop.gradle.nexusstaging'
            }

            group = 'com.intershop'
            version = '1.0.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            subprojects {
                apply plugin: 'ivy-publish'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file("\${rootProject.buildDir}/repo")
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsfile = file('settings.gradle')
        settingsfile << """
            // define root proejct name
            rootProject.name = 'testProject'
        """.stripIndent()
        writeJavaTestClass('com.intershop.root')

        when:
        def result = getPreparedGradleRunner()
                .withArguments('publish', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(':publishIvyPublicationToIvyNexusStagingRepository').outcome == SUCCESS
        result.task(":upload").outcome == SUCCESS
        properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish with publish integration on multiproject - snapshot version - #gradleVersion'(gradleVersion) {
        given:
        String urlStr = server.url('nexus').toString()
        List<String> upLoadList = []
        server.setDispatcher(getPublishTestDispatchForTest(urlStr, upLoadList))

        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
                id 'com.intershop.gradle.nexusstaging'
            }

            group = 'com.intershop'
            version = '1.0.0-SNAPSHOT'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            subprojects {
                apply plugin: 'ivy-publish'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file("\${rootProject.buildDir}/repo")
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsfile = file('settings.gradle')
        settingsfile << """
            // define root proejct name
            rootProject.name = 'testProject'
        """.stripIndent()
        createSubProjectJava('project1a', settingsfile, 'com.intereshop.a', '1.0.0-SNAPSHOT')
        createSubProjectJava('project2b', settingsfile, 'com.intereshop.b', '1.0.0-SNAPSHOT')
        writeJavaTestClass('com.intershop.root')

        when:
        def result = getPreparedGradleRunner()
                .withArguments('publish', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        ! result.tasks.contains(':project1a:publishIvyPublicationToIvyNexusStagingRepository')
        ! result.tasks.contains(':project2b:publishIvyPublicationToIvyNexusStagingRepository')
        result.task(':upload').outcome == SKIPPED
        ! properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish with publish integration on single project - snapshot version - #gradleVersion'(gradleVersion) {
        given:
        String urlStr = server.url('nexus').toString()
        List<String> upLoadList = []
        server.setDispatcher(getPublishTestDispatchForTest(urlStr, upLoadList))

        buildFile << """
            plugins {
                id 'java'
                id 'ivy-publish'
                id 'com.intershop.gradle.nexusstaging'
            }

            group = 'com.intershop'
            version = '1.0.0-SNAPSHOT'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            subprojects {
                apply plugin: 'ivy-publish'
            }

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                repositoryDir = file("\${rootProject.buildDir}/repo")
                description = 'test staging'
                resultPropertiesFile = file('build/result/result.properties')
            }

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        File settingsfile = file('settings.gradle')
        settingsfile << """
            // define root proejct name
            rootProject.name = 'testProject'
        """.stripIndent()
        writeJavaTestClass('com.intershop.root')

        when:
        def result = getPreparedGradleRunner()
                .withArguments('publish', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        ! result.tasks.contains(':publishIvyPublicationToIvyNexusStagingRepository')
        result.task(":upload").outcome == SKIPPED
        ! properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    private Dispatcher getPublishTestDispatchForTest(String urlStr, List<String> uploadList) {
        Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = request.getPath()
                if(path.startsWith('/nexus/service/local/staging/profile_evaluate')) {
                    MockResponse profile_evaluate_response = new MockResponse()
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .addHeader("Cache-Control", "no-cache")
                            .setBody("""
                                {"data": [
                                    {
                                      "resourceURI": "${urlStr}/service/local/staging/profile_evaluate/19124894924ac",
                                      "id": "19124894924ac",
                                      "name": "Test Releases",
                                      "repositoryTemplateId": "default_hosted_release",
                                      "repositoryType": "maven2",
                                      "repositoryTargetId": "testReleases",
                                      "inProgress": false,
                                      "order": 0,
                                      "deployURI": "${urlStr}/service/local/staging/deploy/maven2",
                                      "targetGroups": [
                                        "test_dev"
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
                                      "promotionTargetRepository": "test_releases",
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
                    return profile_evaluate_response
                } else if(path.startsWith('/nexus/service/local/staging/profile_repositories/19124894924ac')) {
                    MockResponse profile_repositories_response = new MockResponse()
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .addHeader("Cache-Control", "no-cache")
                            .setBody("""
                                {"data": [
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1008",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1008",
                                         "created": "2015-09-10T09:02:03.897+02:00",
                                         "createdDate": "Thu Sep 10 09:02:03 CEST 2015",
                                         "createdTimestamp": 1441868523897,
                                         "updated": "2015-09-10T09:02:46.398+02:00",
                                         "updatedDate": "Thu Sep 10 09:02:46 CEST 2015",
                                         "updatedTimestamp": 1441868566398,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1008",
                                         "parentGroupName": "test_test-1008 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1009",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1009",
                                         "created": "2015-09-10T09:02:21.964+02:00",
                                         "createdDate": "Thu Sep 10 09:02:21 CEST 2015",
                                         "createdTimestamp": 1441868541964,
                                         "updated": "2015-09-10T09:03:06.111+02:00",
                                         "updatedDate": "Thu Sep 10 09:03:06 CEST 2015",
                                         "updatedTimestamp": 1441868586111,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1009",
                                         "parentGroupName": "test_test-1009 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1010",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1010",
                                         "created": "2015-09-10T09:02:36.600+02:00",
                                         "createdDate": "Thu Sep 10 09:02:36 CEST 2015",
                                         "createdTimestamp": 1441868556600,
                                         "updated": "2015-09-10T09:03:26.489+02:00",
                                         "updatedDate": "Thu Sep 10 09:03:26 CEST 2015",
                                         "updatedTimestamp": 1441868606489,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1010",
                                         "parentGroupName": "test_test-1010 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1011",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1011",
                                         "created": "2015-09-10T09:02:56.100+02:00",
                                         "createdDate": "Thu Sep 10 09:02:56 CEST 2015",
                                         "createdTimestamp": 1441868576100,
                                         "updated": "2015-09-10T09:03:34.726+02:00",
                                         "updatedDate": "Thu Sep 10 09:03:34 CEST 2015",
                                         "updatedTimestamp": 1441868614726,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1011",
                                         "parentGroupName": "test_test-1011 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1012",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1012",
                                         "created": "2015-09-10T09:03:16.394+02:00",
                                         "createdDate": "Thu Sep 10 09:03:16 CEST 2015",
                                         "createdTimestamp": 1441868596394,
                                         "updated": "2015-09-10T09:03:42.873+02:00",
                                         "updatedDate": "Thu Sep 10 09:03:42 CEST 2015",
                                         "updatedTimestamp": 1441868622873,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1012",
                                         "parentGroupName": "test_test-1012 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1013",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1013",
                                         "created": "2015-10-20T11:11:01.602+02:00",
                                         "createdDate": "Tue Oct 20 11:11:01 CEST 2015",
                                         "createdTimestamp": 1445332261602,
                                         "updated": "2015-10-20T11:11:14.728+02:00",
                                         "updatedDate": "Tue Oct 20 11:11:14 CEST 2015",
                                         "updatedTimestamp": 1445332274728,
                                         "description": "Release Test",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1013",
                                         "parentGroupName": "test_test-1013 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1014",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1014",
                                         "created": "2015-10-21T14:59:00.137+02:00",
                                         "createdDate": "Wed Oct 21 14:59:00 CEST 2015",
                                         "createdTimestamp": 1445432340137,
                                         "updated": "2015-10-21T14:59:11.266+02:00",
                                         "updatedDate": "Wed Oct 21 14:59:11 CEST 2015",
                                         "updatedTimestamp": 1445432351266,
                                         "description": "Release Test",
                                         "provider": "maven2",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1014",
                                         "parentGroupName": "test_test-1014 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1015",
                                         "type": "promoted",
                                         "policy": "release",
                                         "userId": "deploy",
                                         "ipAddress": "10.0.29.214",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1015",
                                         "created": "2015-10-26T16:41:12.793+01:00",
                                         "createdDate": "Mon Oct 26 16:41:12 CET 2015",
                                         "createdTimestamp": 1445874072793,
                                         "updated": "2015-10-26T16:41:23.416+01:00",
                                         "updatedDate": "Mon Oct 26 16:41:23 CET 2015",
                                         "updatedTimestamp": 1445874083416,
                                         "description": "Release Test",
                                         "provider": "maven2",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": "Test Releases",
                                         "parentGroupId": "test_test-1015",
                                         "parentGroupName": "test_test-1015 (staging: group)",
                                         "notifications": 0,
                                         "transitioning": false
                                     },
                                     {
                                         "profileId": "19124894924ac",
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1016",
                                         "type": "open",
                                         "policy": "release",
                                         "userId": "admin",
                                         "ipAddress": "10.0.10.10",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1016",
                                         "created": "2015-11-28T21:10:43.174+01:00",
                                         "createdDate": "Sat Nov 28 21:10:43 CET 2015",
                                         "createdTimestamp": 1448741443174,
                                         "updated": "2015-11-28T21:10:43.471+01:00",
                                         "updatedDate": "Sat Nov 28 21:10:43 CET 2015",
                                         "updatedTimestamp": 1448741443471,
                                         "description": "Implicitly created (auto staging).",
                                         "provider": "maven2",
                                         "releaseRepositoryId": "test_releases",
                                         "releaseRepositoryName": " Releases",
                                         "notifications": 0,
                                         "transitioning": false
                                     }]
                                }""".stripIndent())
                    return profile_repositories_response
                } else if(path.startsWith('/nexus/service/local/staging/bulk/close')){
                    MockResponse close_response = new MockResponse()
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .addHeader("Cache-Control", "no-cache")

                    return close_response
                } else {
                    uploadList.add(path)
                }

                return new MockResponse()
            }
        }
        return dispatcher
    }

    /**
     * Creates a java sub project
     */
    private File createSubProjectJava(String projectPath, File settingsGradle, String packageName, String version = '1.0.0'){
        String buildFileContent = """
                    plugins {
                        id 'java'
                        id 'ivy-publish'
                    }
                    sourceCompatibility = 1.7
                    targetCompatibility = 1.7

                    group = 'com.intershop.project'
                    version = '${version}'

                    publishing {
                        publications {
                            ivy(IvyPublication) {
                                from components.java
                            }
                        }
                    }
                    """.stripIndent()

        File subProject = createSubProject(projectPath, settingsGradle, buildFileContent)
        writeJavaTestClass(packageName, subProject)
        return subProject
    }

    def 'test publish functionality with snapshot version - #gradleVersion'(gradleVersion) {
        given:
        directory('build/repository-testmodule')
        copyResources('repository-test', 'build/repository-testmodule')

        String urlStr = server.url('nexus').toString()

        final List<RecordedRequest> requestsMade = []
        final Dispatcher dispatcher = getPublishTestDispatcher(urlStr, requestsMade)
        server.setDispatcher(dispatcher)

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            version = '1.2.3.4-SNAPSHOT'

            nexusStaging {
                server {
                    baseURL = '${urlStr}'
                    username = 'admin'
                    password = 'admin123'
                }

                //repositoryDir = file('build/repository-testmodule')
                description = 'test staging'
                //resultPropertiesFile = file('build/result/result.properties')
            }


            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('upload', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(":upload").outcome == SKIPPED
        ! properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    def 'test publish functionality without configuration - #gradleVersion'(gradleVersion) {
        given:
        directory('build/repository-testmodule')
        copyResources('repository-test', 'build/repository-testmodule')

        String urlStr = server.url('nexus').toString()
        final List<RecordedRequest> requestsMade = []
        final Dispatcher dispatcher = getPublishTestDispatcher(urlStr, requestsMade)
        server.setDispatcher(dispatcher)

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            version = '1.2.3.4-SNAPSHOT'

            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('upload', '-PrunOnCI=true', '--stacktrace', '-i')
                .withGradleVersion(gradleVersion)
                .build()

        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(":upload").outcome == SKIPPED
        ! properties.exists()

        where:
        gradleVersion << supportedGradleVersions
    }

    private Dispatcher getPublishTestDispatcher(String urlStr, List<RecordedRequest> requestsMade) {
        Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = request.getPath()
                if(path.startsWith('/nexus/service/local/staging/profile_evaluate')) {
                    MockResponse profile_evaluate_response = new MockResponse()
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .addHeader("Cache-Control", "no-cache")
                            .setBody("""
                                {"data": [
                                    {
                                      "resourceURI": "${urlStr}/service/local/staging/profile_evaluate/19124894924ac",
                                      "id": "19124894924ac",
                                      "name": "Test Releases",
                                      "repositoryTemplateId": "default_hosted_release",
                                      "repositoryType": "maven2",
                                      "repositoryTargetId": "testReleases",
                                      "inProgress": false,
                                      "order": 0,
                                      "deployURI": "${urlStr}/service/local/staging/deploy/maven2",
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
                                      "promotionTargetRepository": "test_releases",
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
                    return profile_evaluate_response
                } else if(path.startsWith('/nexus/service/local/staging/profile_repositories/19124894924ac')) {
                    MockResponse profile_repositories_response = new MockResponse()
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1008",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1009",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1010",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1011",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1012",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1013",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1014",
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
                                         "repositoryURI": "${urlStr}/content/repositories/payment_releases-1015",
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
                                         "profileName": "Test Releases",
                                         "profileType": "repository",
                                         "repositoryId": "test_releases-1016",
                                         "type": "open",
                                         "policy": "release",
                                         "userId": "admin",
                                         "ipAddress": "10.0.10.10",
                                         "repositoryURI": "${urlStr}/content/repositories/test_releases-1016",
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
                    return profile_repositories_response
                } else if(path.startsWith('/nexus/service/local/staging/bulk/close')){
                    MockResponse close_response = new MockResponse()
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .addHeader("Cache-Control", "no-cache")

                    return close_response
                } else {
                    println path
                }

                requestsMade.add(request)
                return new MockResponse()
            }
        }
        return dispatcher
    }
}
