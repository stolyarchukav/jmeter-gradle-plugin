package net.foragerr.jmeter.gradle.plugins

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

@CompileDynamic
@Slf4j
class TaskJMInit extends DefaultTask {

    private String thisPluginVersion
    private String jmeterVersion
    private String jmeterPluginsVersion

    @TaskAction
    void jmInit() {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        //Init plugin settings
        File buildDir = project.getBuildDir()
        File workDir = new File(buildDir, 'jmeter')
        File binDir = new File(workDir, 'bin')
        jmeter.workDir = workDir
        jmeter.binDir = binDir

        // Test Files //
        jmeter.testFileDir = jmeter.testFileDir == null ? new File(project.getProjectDir(), 'src/test/jmeter') : jmeter.testFileDir

        // Logs //
        jmeter.reportDir = jmeter.reportDir ?: new File(buildDir, 'jmeter-report')
        jmeter.jmLog = jmeter.jmLog ?: new File(jmeter.reportDir, 'jmeter.log')

        // Java Properties //
        jmeter.maxHeapSize = jmeter.maxHeapSize ?: '512M'
        jmeter.minHeapSize = jmeter.minHeapSize ?: '512M'

        // Plugin Options
        jmeter.ignoreErrors = jmeter.ignoreErrors == null ? false : jmeter.ignoreErrors
        jmeter.ignoreFailures = jmeter.ignoreFailures == null ? false : jmeter.ignoreFailures
        jmeter.csvLogFile = jmeter.csvLogFile == null ? true : jmeter.csvLogFile
        jmeter.showSummarizer = jmeter.showSummarizer == null ? true : jmeter.showSummarizer
        jmeter.failBuildOnError = jmeter.failBuildOnError == null ? true : jmeter.failBuildOnError

        loadPluginProperties()
        jmeter.jmVersion = this.jmeterVersion

        //Create required folders
        binDir.mkdirs()

        File jmeterJUnitFolder = new File(workDir, 'lib/junit')
        jmeterJUnitFolder.mkdirs()

        File jmeterExtFolder = new File(workDir, 'lib/ext')
        jmeterExtFolder.mkdirs()
        jmeter.reportDir.mkdirs()

        initTempProperties()
        resolveJmeterSearchPath()

        //print version info
        log.info("""------------------------
Using
   jmeter-gradle-plugin version: ${this.thisPluginVersion}
   jmeter version: ${this.jmeterVersion}
   jmeter jp@gc plugins version: ${this.jmeterPluginsVersion}
------------------------""")
    }

    protected void initTempProperties() throws IOException {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension
        List<File> tempProperties = []

        [
                'saveservice.properties',
                'system.properties',
                'reportgenerator.properties',
                'user.properties',
                'jmeter-plugin.properties',
                'log4j2.xml'
        ].each {
            File f = new File(jmeter.workDir as File, it)
            log.debug('{} location is {}', it, f.absolutePath)
            tempProperties << f
        }

        File upgradeProperties = new File(jmeter.workDir as File, 'upgrade.properties')
        System.setProperty('upgrade_properties', '/' + upgradeProperties.getName())
        tempProperties.add(upgradeProperties)

        File defaultJmeterProperties = new File(jmeter.workDir as File, 'jmeter.properties')
        System.setProperty('default_jm_properties', '/' + defaultJmeterProperties.getName())
        tempProperties.add(defaultJmeterProperties)

        //Copy files from jar to workDir
        for (File f : tempProperties) {
            try {
                f.withWriter { Writer writer ->
                    IOUtils.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream(f.getName()), writer, 'UTF-8')
                }
            } catch (IOException ioe) {
                throw new GradleException("Couldn't create temporary property file ${f.getName()} in directory ${f.getParent()}", ioe)
            }
        }
    }

    protected void resolveJmeterSearchPath() {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension
        StringBuilder cp = new StringBuilder()
        FileCollection files = project.buildscript.configurations.classpath
        String jmeterVersionPattern = jmeter.jmVersion.replaceAll('[.]', '[.]')
        String pathSeparator = ';' //intentionally not File.PathSeparator - JMeter parses for ; on all platforms
        for (File dep : files) {
            if (dep.getPath().matches('^.*[/]ApacheJMeter.*' + jmeterVersionPattern + '.jar$')) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
            } else if (dep.getPath().matches('^.*bsh.*[.]jar$')) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
                //add jp@gc plugins to search_path
            } else if (dep.getPath().matches('^.*jmeter-.*$')) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
            }
        }
        cp.append(new File(jmeter.workDir as File, 'lib' + File.separator + 'ext').getCanonicalPath())
        System.setProperty('search_paths', cp.toString())
        log.debug('Search path is set to {}', System.getProperty('search_paths'))
    }

    private void loadPluginProperties() {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream('jmeter-plugin.properties')
            if (is == null) {
                log.error('Error fetching jmeter version')
                throw new GradleException('Error fetching jmeter version')
            }
            Properties pluginProps = new Properties()
            pluginProps.load(is)
            is.close()

            this.thisPluginVersion = pluginProps.getProperty('thisPlugin.version')
            this.jmeterVersion = pluginProps.getProperty('jmeter.version')
            this.jmeterPluginsVersion = pluginProps.getProperty('plugin.version')
        } catch (Exception e) {
            log.error('Can\'t load JMeter version, build will stop', e)
            throw new GradleException('Can\'t load JMeter version, build will stop', e)
        }
    }
}
