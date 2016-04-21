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

import com.intershop.gradle.nexusstaging.tasks.AbstractRepoTask
import com.intershop.gradle.nexusstaging.util.UploadHttpClientFactory
import com.intershop.gradle.test.AbstractIntegrationSpec
import groovyx.net.http.HTTPBuilder
import spock.lang.Requires

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class IntPluginSpec extends AbstractIntegrationSpec {

    @Requires({ System.properties['nexus_url_config'] &&
                System.properties['nexus_user_config'] &&
                System.properties['nexus_passwd_config'] })
    def 'test publish functionality with real server'() {
        given:
        directory('build/repository-paymentorg')
        copyResources('repository-paymentorg', 'build/repository-paymentorg')

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            nexusStaging {
                server {
                    baseURL = '${System.properties['nexus_url_config']}'
                    username = '${System.properties['nexus_user_config']}'
                    password = '${System.properties['nexus_passwd_config']}'
                }

                repositoryDir = file('build/repository-paymentorg')
                description = 'publishing test'
                resultPropertiesFile = file('build/result/result.properties')
            }


            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('upload', '-PrunOnCI=true', '--stacktrace', '-i')
                .build()
        File properties = new File(testProjectDir, 'build/result/result.properties')

        // this is necessary, so that the repos is really closed!
        sleep(60000)

        then:
        result.task(":upload").outcome == SUCCESS
        properties.exists()
    }

    @Requires({ System.properties['nexus_url_config'] &&
            System.properties['nexus_user_config'] &&
            System.properties['nexus_passwd_config'] })
    def 'test close repo functionality with real server'() {
        given:
        directory('build/repository-paymentorg')
        copyResources('repository-paymentorg', 'build/repository-paymentorg')

        String hostURL = System.properties['nexus_url_config']
        String uploadURL = hostURL + (hostURL.endsWith('/') ? '' : '/') + AbstractRepoTask.STAGINGPATH
        HTTPBuilder http = UploadHttpClientFactory.create(uploadURL, System.properties['nexus_user_config'], System.properties['nexus_passwd_config'])
        UploadHttpClientFactory.upload(http, new File(testProjectDir, 'build/repository-paymentorg'))


        //wait so that repo state is ok
        sleep(3000)

        buildFile << """
            plugins {
                id 'com.intershop.gradle.nexusstaging'
            }

            nexusStaging {
                server {
                    baseURL = '${System.properties['nexus_url_config']}'
                    username = '${System.properties['nexus_user_config']}'
                    password = '${System.properties['nexus_passwd_config']}'
                }

                repositoryDir = file('build/repository-paymentorg')
                description = 'close test'
                resultPropertiesFile = file('build/result/result.properties')
            }


            repositories {
                mavenCentral()
            }
        """.stripIndent()

        when:
        def result = getPreparedGradleRunner()
                .withArguments('closeRepos', '-PrunOnCI=true', '--stacktrace', '-i', '-x', 'upload')
                .build()
        File properties = new File(testProjectDir, 'build/result/result.properties')

        then:
        result.task(":closeRepos").outcome == SUCCESS
        properties.exists()
    }
}
