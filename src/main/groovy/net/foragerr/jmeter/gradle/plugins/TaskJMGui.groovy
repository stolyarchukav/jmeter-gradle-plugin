package net.foragerr.jmeter.gradle.plugins

import net.foragerr.jmeter.gradle.plugins.utils.JMUtils
import net.foragerr.jmeter.gradle.plugins.worker.JMeterRunner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

abstract class TaskJMGui extends DefaultTask {

    protected final Logger log = Logging.getLogger(getClass())
    private List<File> jmeterSystemPropertiesFiles = project.jmeter.jmSystemPropertiesFiles

    @Inject
    abstract WorkerExecutor getWorkerExecutor()

    @TaskAction
    jmGui() throws IOException {
        try {
            JMPluginExtension jmeter = project.jmeter as JMPluginExtension

            List<String> args = new ArrayList<String>()
            args.addAll(Arrays.asList(
                    "-p", JMUtils.getJmeterPropsFile(project).getCanonicalPath()
            ))

            if (jmeter.jmAddProp)
                args.addAll(Arrays.asList("-q", jmeter.jmAddProp.getCanonicalPath()))

            List<File> testFiles = JMUtils.getListOfTestFiles(project)
            if (testFiles.size() > 0) args.addAll(Arrays.asList("-t", testFiles[0].getCanonicalPath()))

            //User provided sysprops
            List<String> userSysProps = new ArrayList<String>()
            if (jmeterSystemPropertiesFiles != null) {
                for (File PropertyFile : jmeterSystemPropertiesFiles) {
                    if (PropertyFile.exists() && PropertyFile.isFile()) {
                        args.addAll(Arrays.asList("-S", PropertyFile.getCanonicalPath()))
                    }
                }
            }

            if (jmeter.jmSystemProperties != null) {
                for (String systemProperty : jmeter.jmSystemProperties) {
                    userSysProps.addAll(Arrays.asList(systemProperty))
                }
            }

            initUserProperties(args)

            log.debug("JMeter is called with the following command line arguments: " + args.toString())

            JMSpecs specs = new JMSpecs()
            specs.getUserSystemProperties().addAll(userSysProps)
            specs.getSystemProperties().put('search_paths',
                    System.getProperty('search_paths'))
            specs.getSystemProperties().put('jmeter.home',
                    jmeter.workDir.getAbsolutePath())
            specs.getSystemProperties().put('saveservice_properties',
                    new File(jmeter.workDir, 'saveservice.properties').getAbsolutePath())
            specs.getSystemProperties().put('upgrade_properties',
                    new File(jmeter.workDir, 'upgrade.properties').getAbsolutePath())
            specs.getSystemProperties().put('log_file', jmeter.jmLog.getAbsolutePath())
            specs.getSystemProperties().put("log4j.configurationFile",
                    new File(jmeter.workDir, 'log4j2.xml').getAbsolutePath())
            specs.getSystemProperties().put('keytool.directory', System.getProperty("java.home") + File.separator + "bin")
            specs.getSystemProperties().put('proxy.cert.directory', jmeter.workDir.getAbsolutePath())
            specs.getJmeterProperties().addAll(args)
            specs.setMaxHeapSize(jmeter.maxHeapSize.toString())
            specs.setMinHeapSize(jmeter.minHeapSize.toString())
            Iterable<File> jmeterConfiguration = project.buildscript.configurations.classpath
            new JMeterRunner(workerExecutor, jmeterConfiguration).executeJmeterCommand(specs, jmeter.workDir.getAbsolutePath())

        } catch (IOException e) {
            throw new GradleException("Error Launching JMeter GUI", e)
        }
    }

    //TODO should probably also be in JMUtils
    private void initUserProperties(List<String> jmeterArgs) {
        if (project.jmeter.jmUserProperties != null) {
            project.jmeter.jmUserProperties.each { property -> jmeterArgs.add("-J" + property) }
        }
    }

    private void initGlobalProperties(List<String> jmeterArgs) {
        if (project.jmeter.jmGlobalProperties != null) {
            project.jmeter.jmGlobalProperties.each { property -> jmeterArgs.add("-G" + property) }
        }
    }
}
