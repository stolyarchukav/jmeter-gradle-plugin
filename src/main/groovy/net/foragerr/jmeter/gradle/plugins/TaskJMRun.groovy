package net.foragerr.jmeter.gradle.plugins

import net.foragerr.jmeter.gradle.plugins.utils.ErrorScanner
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

abstract class TaskJMRun extends DefaultTask {

    protected final Logger log = Logging.getLogger(getClass())

    @Inject
    abstract WorkerExecutor getWorkerExecutor()

    @TaskAction
    jmRun() {

        //Get List of test files to run
        List<File> testFiles = JMUtils.getListOfTestFiles(project)

        //Run Tests
        List<File> resultList = new ArrayList<File>()
        for (File testFile : testFiles) resultList.add(executeJmeterTest(testFile))

        //Scan for errors
        checkForErrors(resultList)
        project.jmeter.jmResultFiles = resultList

    }

    private void checkForErrors(List<File> results) {
        ErrorScanner scanner = new ErrorScanner(project.jmeter.ignoreErrors, project.jmeter.ignoreFailures, project.jmeter.failBuildOnError)
        try {
            for (File file : results) {
                if (scanner.scanForProblems(file)) {
                    log.warn("There were test errors.  See the jmeter logs for details")

                    if (project.jmeter.failBuildOnError) 
						throw new GradleException("Errors during JMeter test")             
                }
            }
        } catch (IOException e) {
            throw new GradleException("Can't read log file", e)
        }
    }

    private File executeJmeterTest(File testFile) {
        try {
            JMPluginExtension jmeter = project.jmeter as JMPluginExtension

            log.info('Executing jMeter test : ' + testFile.getCanonicalPath())
            File resultFile = JMUtils.getResultFile(testFile, project)
            resultFile.delete()

            //Build Jmeter command args
            List<String> args = new ArrayList<String>()
            args.addAll(Arrays.asList("-n",
                    "-t", testFile.getCanonicalPath(),
                    "-l", resultFile.getCanonicalPath(),
                    "-p", JMUtils.getJmeterPropsFile(project).getCanonicalPath()
            ))

            // additional properties from file
            if (jmeter.jmAddProp)
                args.addAll(Arrays.asList("-q", jmeter.jmAddProp.getCanonicalPath()))

            //User provided sysprops
            List<String> userSysProps = new ArrayList<String>()
            if (jmeter.jmSystemPropertiesFiles != null) {
                for (File systemPropertyFile : jmeter.jmSystemPropertiesFiles) {
                    if (systemPropertyFile.exists() && systemPropertyFile.isFile()) {
                        args.addAll(Arrays.asList("-S", systemPropertyFile.getCanonicalPath()))
                    }
                }
            }

            // jmSystemProperties
            if (jmeter.jmSystemProperties != null) {
                for (String systemProperty : jmeter.jmSystemProperties) {
                    userSysProps.addAll(Arrays.asList(systemProperty))
                    log.info(systemProperty)
                }
            }

            //jmUserProperties
            if (jmeter.jmUserProperties != null) {
                jmeter.jmUserProperties.each { property -> args.add("-J" + property) }
            }

            //jmGlobalProperties
            if (jmeter.jmGlobalProperties != null) {
                jmeter.jmGlobalProperties.each { property -> args.add("-G" + property) }
            }

            if (jmeter.remote) {
                args.add("-r")
            }

            log.info("JMeter is called with the following command line arguments: " + args.toString())
            JMSpecs specs = new JMSpecs()
            specs.getUserSystemProperties().addAll(userSysProps)
            specs.getSystemProperties().put('jmeter.home',
                    jmeter.workDir.getAbsolutePath())
            specs.getSystemProperties().put('search_paths',
                    System.getProperty('search_paths'))
            specs.getSystemProperties().put('saveservice_properties',
                    new File(jmeter.workDir, 'saveservice.properties').getAbsolutePath())
            specs.getSystemProperties().put('upgrade_properties',
                    new File(jmeter.workDir, 'upgrade.properties').getAbsolutePath())
            specs.getSystemProperties().put('log_file', jmeter.jmLog.getAbsolutePath())
            specs.getSystemProperties().put('java.awt.headless', 'true')
            specs.getSystemProperties().put("log4j.configurationFile",
                    new File(jmeter.workDir, 'log4j2.xml').getAbsolutePath())

            if (jmeter.csvLogFile)
            	specs.getSystemProperties().put("jmeter.save.saveservice.output_format", "csv")
            else 
            	specs.getSystemProperties().put("jmeter.save.saveservice.output_format", "xml")

            //enable summarizer
            if (jmeter.showSummarizer) {
                specs.getSystemProperties().put('summariser.name','summary')
                specs.getSystemProperties().put('summariser.interval','30')
                specs.getSystemProperties().put('summariser.log','true')
                specs.getSystemProperties().put('summariser.out','true')
            }

            specs.getJmeterProperties().addAll(args)
            specs.setMaxHeapSize(jmeter.maxHeapSize.toString())
            specs.setMinHeapSize(jmeter.minHeapSize.toString())
            Iterable<File> jmeterConfiguration = project.buildscript.configurations.classpath
            new JMeterRunner(workerExecutor, jmeterConfiguration).executeJmeterCommand(specs, jmeter.workDir.getAbsolutePath())
            return resultFile
        } catch (IOException e) {
            throw new GradleException("Can't execute test", e)
        }
    }
}
