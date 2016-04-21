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

import com.intershop.gradle.nexusstaging.extension.NexusStagingExtension
import com.intershop.gradle.nexusstaging.tasks.CloseRepos
import com.intershop.gradle.nexusstaging.tasks.CopyToRepo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * This is the implementation of the plugin.
 */
class NexusStagingPlugin implements Plugin<Project> {

    private final static String ivyPublicationName = 'ivyNexusStaging'
    private final static String mvnPublicationName = 'mvnNexusStaging'

    private NexusStagingExtension extension

    public void apply(Project project) {
        if(project.name != project.rootProject.name) {
            project.logger.warn("Don't apply this Nexus Staging plugin to a sub project. All configurations will be applied to the root project.")
        }

        Project configProject = project.rootProject

        configProject.logger.info('Create extension {} for {}', NexusStagingExtension.NEXUSSTAGING_EXTENSION_NAME, configProject.name)
        extension = configProject.extensions.findByType(NexusStagingExtension) ?: configProject.extensions.create(NexusStagingExtension.NEXUSSTAGING_EXTENSION_NAME, NexusStagingExtension, configProject)

        if (extension.runOnCI) {
            // copy binaries to target
            configureCopyToRepoTask(configProject)

            // close created repositories
            configureCloseReposTask(configProject)
        }
    }

    private Task configureCopyToRepoTask(Project project) {

        project.plugins.apply(PublishingPlugin)

        def task = project.tasks.maybeCreate(NexusStagingExtension.NEXUSCOPY_TASK_NAME, CopyToRepo.class)

        task.group = NexusStagingExtension.NEXUSSTAGING_GROUP_NAME
        task.description = "Copy sources to Nexus staging repo"

        task.conventionMapping.baseURL = { extension.server.getBaseURL() }
        task.conventionMapping.username = { extension.server.getUsername() }
        task.conventionMapping.password = { extension.server.getPassword() }

        task.conventionMapping.src = {
            if(extension.getRepositoryDir() != null && extension.getRepositoryDir().exists()) {
                return extension.getRepositoryDir()
            }
        }
        task.conventionMapping.repoDescription = { extension.getDescription() }
        task.conventionMapping.resultProperties = { extension.getResultPropertiesFile() }

        // write always output properties
        task.outputs.upToDateWhen { false }

        task.onlyIf {
            extension.server.getBaseURL() &&
                    extension.server.getUsername() &&
                    extension.server.getPassword() &&
                    extension.getRepositoryDir().exists() &&
                    ! project.getVersion().toString().toLowerCase().endsWith('snapshot')
        }

        task.dependsOn project.tasks.withType(GenerateIvyDescriptor)
        task.dependsOn project.tasks.withType(GenerateMavenPom)
        task.dependsOn project.tasks.withType(PublishToIvyRepository)
        task.dependsOn project.tasks.withType(PublishToMavenRepository)
        task.dependsOn project.tasks.withType(PublishToMavenLocal)

        configureRepo(project)

        project.subprojects {p ->
            p.tasks.whenTaskAdded {
                if(it.name == 'publish') {
                    task.dependsOn it
                    task.mustRunAfter it
                }
            }
            task.dependsOn p.tasks.withType(GenerateIvyDescriptor)
            task.dependsOn p.tasks.withType(GenerateMavenPom)
            task.dependsOn p.tasks.withType(PublishToIvyRepository)
            task.dependsOn p.tasks.withType(PublishToMavenRepository)
            task.dependsOn p.tasks.withType(PublishToMavenLocal)

            configureRepo(p)
        }

        Task publish = project.tasks.findByName('publish')
        if (publish) {
            publish.dependsOn task
        }
        return task
    }

    private void configureCloseReposTask(Project project) {
        def task = project.tasks.maybeCreate(NexusStagingExtension.NEXUSCLOSE_TASK_NAME, CloseRepos.class)

        task.group = NexusStagingExtension.NEXUSSTAGING_GROUP_NAME
        task.description = "Close a open Nexus staging repo"

        task.conventionMapping.baseURL = { extension.server.getBaseURL() }
        task.conventionMapping.username = { extension.server.getUsername() }
        task.conventionMapping.password = { extension.server.getPassword() }

        task.conventionMapping.src = { extension.getRepositoryDir() }
        task.conventionMapping.repoDescription = { extension.getDescription() }
        task.conventionMapping.resultProperties = { extension.getResultPropertiesFile() }

        // write always output properties
        task.outputs.upToDateWhen { false }

        task.onlyIf {
            extension.server.getBaseURL() &&
                    extension.server.getUsername() &&
                    extension.server.getPassword() &&
                    extension.getRepositoryDir().exists() &&
                    ! project.getVersion().toString().toLowerCase().endsWith('snapshot')
        }
    }

    private void configureRepo(Project p) {
        p.plugins.withType(IvyPublishPlugin) {
            p.publishing {
                if (!repositories.findByName(ivyPublicationName) && !p.getVersion().toString().toLowerCase().endsWith('snapshot')) {
                    p.logger.info('Added publishing configuration [{}] for {}', ivyPublicationName, p.name)
                    extension.getRepositoryDir().mkdirs()
                    repositories {
                        ivy {
                            name ivyPublicationName
                            url extension.getRepositoryDir().toURI().toURL()
                        }
                    }
                }
            }


            p.repositories {
                if(!p.repositories.findByName(ivyPublicationName) && ! p.getVersion().toString().toLowerCase().endsWith('snapshot')) {
                    ivy {
                        name ivyPublicationName
                        url extension.getRepositoryDir().toURI().toURL()
                    }
                }
            }
        }
        p.plugins.withType(MavenPublishPlugin) {
            p.publishing {
                if (!repositories.findByName(mvnPublicationName) && !p.getVersion().toString().toLowerCase().endsWith('snapshot')) {
                    p.logger.info('Added publishing configuration [{}] for {}', mvnPublicationName, p.name)
                    repositories {
                        maven {
                            name mvnPublicationName
                            url extension.getRepositoryDir().toURI().toURL()
                        }
                    }
                }
            }


            p.repositories {
                if(!p.repositories.findByName(mvnPublicationName) && ! p.getVersion().toString().toLowerCase().endsWith('snapshot')) {
                    maven {
                        name mvnPublicationName
                        url extension.getRepositoryDir().toURI().toURL()
                    }
                }
            }
        }
    }
}
