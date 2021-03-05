package net.foragerr.jmeter.gradle.plugins

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import net.foragerr.jmeter.gradle.plugins.utils.JMUtils
import net.foragerr.jmeter.gradle.plugins.worker.JMeterRunner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

@CompileDynamic
@Slf4j
abstract class TaskJMGui extends DefaultTask {

    private List<File> jmeterSystemPropertiesFiles = project.jmeter.jmSystemPropertiesFiles

    @Inject
    abstract WorkerExecutor getWorkerExecutor()

    @TaskAction
    void jmGui() throws IOException {
        try {
            JMPluginExtension jmeter = project.jmeter as JMPluginExtension

            List<String> args = []
            args.addAll(Arrays.asList(
                    '-p', JMUtils.getJmeterPropsFile(project).getCanonicalPath()
            ))

            if (jmeter.jmAddProp) {
                args.addAll(Arrays.asList('-q', jmeter.jmAddProp.getCanonicalPath()))
            }

            List<File> testFiles = JMUtils.getListOfTestFiles(project)
            if (testFiles.size() > 0) {
                args.addAll(Arrays.asList('-t', testFiles[0].getCanonicalPath()))
            }

            //User provided sysprops
            List<String> userSysProps = []
            if (jmeterSystemPropertiesFiles != null) {
                for (File PropertyFile : jmeterSystemPropertiesFiles) {
                    if (PropertyFile.exists() && PropertyFile.isFile()) {
                        args.addAll(Arrays.asList('-S', PropertyFile.getCanonicalPath()))
                    }
                }
            }

            if (jmeter.jmSystemProperties != null) {
                for (String systemProperty : jmeter.jmSystemProperties) {
                    userSysProps.addAll(Arrays.asList(systemProperty))
                }
            }

            initUserProperties(args)

            log.debug('JMeter is called with the following command line arguments: {}', args.toString())

            JMSpecs specs = new JMSpecs()
            specs.userSystemProperties.addAll(userSysProps)
            specs.systemProperties.put('search_paths',
                    System.getProperty('search_paths'))
            specs.systemProperties.put('jmeter.home',
                    jmeter.workDir.getAbsolutePath())
            specs.systemProperties.put('saveservice_properties',
                    new File(jmeter.workDir, 'saveservice.properties').getAbsolutePath())
            specs.systemProperties.put('upgrade_properties',
                    new File(jmeter.workDir, 'upgrade.properties').getAbsolutePath())
            specs.systemProperties.put('log_file', jmeter.jmLog.getAbsolutePath())
            specs.systemProperties.put("log4j.configurationFile",
                    new File(jmeter.workDir, 'log4j2.xml').getAbsolutePath())
            specs.systemProperties.put('keytool.directory', System.getProperty('java.home') + File.separator + 'bin')
            specs.systemProperties.put('proxy.cert.directory', jmeter.workDir.getAbsolutePath())
            specs.jmeterProperties.addAll(args)
            specs.maxHeapSize = jmeter.maxHeapSize.toString()
            specs.minHeapSize = jmeter.minHeapSize.toString()
            Iterable<File> jmeterConfiguration = project.buildscript.configurations.classpath
            new JMeterRunner(workerExecutor, jmeterConfiguration).executeJmeterCommand(specs, jmeter.workDir.getAbsolutePath())
        } catch (IOException e) {
            throw new GradleException('Error Launching JMeter GUI', e)
        }
    }

    //TODO should probably also be in JMUtils
    private void initUserProperties(List<String> jmeterArgs) {
        if (project.jmeter.jmUserProperties != null) {
            project.jmeter.jmUserProperties.each { property -> jmeterArgs.add('-J' + property) }
        }
    }

    private void initGlobalProperties(List<String> jmeterArgs) {
        if (project.jmeter.jmGlobalProperties != null) {
            project.jmeter.jmGlobalProperties.each { property -> jmeterArgs.add('-G' + property) }
        }
    }
}
