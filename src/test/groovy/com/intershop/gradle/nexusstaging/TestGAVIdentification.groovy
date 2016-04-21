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

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestGAVIdentification extends AbstractIntegrationSpec {

    def 'test GAV ivy'() {
        given:
        copyResources('gav-tests/ivy', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = CopyToRepo.getModulesFromIvy(project.fileTree(dir: 'test'))
                    list.each {
                        println it
                    }
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('com.intershop.test:moduleTest:1.2.3.4')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    def 'test GAV pom'() {
        given:
        copyResources('gav-tests/pom', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = CopyToRepo.getModulesFromPom(project.fileTree(dir: 'test'))
                    list.each {
                        println it
                    }
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('com.intershop.test:moduleTest:1.2.3.4')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    def 'test GAV mix'() {
        given:
        copyResources('gav-tests/mix', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = []
                    list.addAll(CopyToRepo.getModulesFromIvy(project.fileTree(dir: 'test')))
                    list.addAll(CopyToRepo.getModulesFromPom(project.fileTree(dir: 'test')))
                    list.each {
                        println it
                    }
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('com.intershop.test:modulePomTest:1.2.3.4')
        result.output.contains('com.intershop.test:moduleIvyTest:1.2.3.4')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    def 'test GAV empty'() {
        given:
        copyResources('gav-tests/empty', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = []
                    list.addAll(CopyToRepo.getModulesFromIvy(project.fileTree(dir: 'test')))
                    list.addAll(CopyToRepo.getModulesFromPom(project.fileTree(dir: 'test')))
                    println "ListSize = \${list.size()}"
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('ListSize = 0')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    def 'test GAV wrong_ivy'() {
        given:
        copyResources('gav-tests/wrong_ivy', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = []
                    list.addAll(CopyToRepo.getModulesFromIvy(project.fileTree(dir: 'test')))
                    list.addAll(CopyToRepo.getModulesFromPom(project.fileTree(dir: 'test')))
                    println "ListSize = \${list.size()}"
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('ListSize = 0')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    def 'test GAV wrong_pom'() {
        given:
        copyResources('gav-tests/wrong_pom', 'test')

        buildFile << """
            import com.intershop.gradle.nexusstaging.tasks.CopyToRepo

            task testMethod {
                doLast {
                    def list = []
                    list.addAll(CopyToRepo.getModulesFromIvy(project.fileTree(dir: 'test')))
                    list.addAll(CopyToRepo.getModulesFromPom(project.fileTree(dir: 'test')))
                    println "ListSize = \${list.size()}"
                }
            }
            buildscript {
                dependencies {
                    classpath files(${getClasspathString()})
                }
            }
        """

        when:
        def result = getPreparedGradleRunner()
                .withArguments('testMethod')
                .build()

        then:
        result.output.contains('ListSize = 0')
        result.output.contains('testMethod')
        result.task(":testMethod").outcome == SUCCESS
    }

    private String getClasspathString() {
        String cpString = pluginClasspath
                .collect { it.absolutePath.replace('\\', '\\\\') } // escape backslashes in Windows paths
                .collect { "'$it'" }
                .join(", ")
        return cpString
    }
}
