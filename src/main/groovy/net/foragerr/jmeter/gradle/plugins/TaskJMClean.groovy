package net.foragerr.jmeter.gradle.plugins

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

@CompileDynamic
@Slf4j
class TaskJMClean extends DefaultTask {

    //TODO should probably do a better job of deleting specific file types
    // instead of deleting the entire directory. This behavior is dangerous when
    // reportDir is set to a pre existing directory  #65

    @TaskAction
    void jmClean() throws IOException {
        JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        File reportDir = jmeter.reportDir ?: new File(project.getBuildDir(), 'jmeter-report')
        log.info('Cleaning out folder: {}', reportDir)
        reportDir.deleteDir()
        reportDir.mkdirs()

        //if jmeter log is in custom location, delete that as well
        File logfile = jmeter.jmLog ?: new File(reportDir, 'jmeter.log')
        logfile.delete()
    }
}
