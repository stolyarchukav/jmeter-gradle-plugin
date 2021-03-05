package net.foragerr.jmeter.gradle.plugins

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by @author foragerr@gmail.com on 7/17/2015.
 */
@CompileDynamic
@Slf4j
class JMPlugin implements Plugin<Project> {
    static final String TASK_GROUP_NAME = 'JMeter'
    static final String TASK_NAME_INIT = 'jmInit'

    void apply(Project project) {
        project.extensions.create('jmeter', JMPluginExtension)

        project.task(TASK_NAME_INIT, type: TaskJMInit) {
            group = null //hide this task
            description = 'Init task - pointless to run by itself'
        }

        project.task('jmRun', type: TaskJMRun, dependsOn: TASK_NAME_INIT) {
            group = TASK_GROUP_NAME
            description = 'Execute JMeter Tests'
        }

        project.task('jmGui', type: TaskJMGui, dependsOn: TASK_NAME_INIT) {
            group = TASK_GROUP_NAME
            description = 'Launch JMeter GUI to edit tests'
        }

        project.task('jmReport', type: TaskJMReports, dependsOn: TASK_NAME_INIT) {
            group = TASK_GROUP_NAME
            description = 'Create JMeter test Reports'
        }

        project.task('jmClean', type: TaskJMClean) {
            group = TASK_GROUP_NAME
            description = 'Clean JMeter test Reports'
        }
    }
}
