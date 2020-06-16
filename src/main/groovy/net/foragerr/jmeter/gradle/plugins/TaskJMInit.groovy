package net.foragerr.jmeter.gradle.plugins

import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

class TaskJMInit extends DefaultTask {

    protected final Logger log = Logging.getLogger(getClass())

    private String thisPluginVersion
    private String jmeterVersion
    private String jmeterPluginsVersion

    @TaskAction
    jmInit() {

        //Init plugin settings
        File buildDir = project.getBuildDir()
        File workDir = new File(buildDir, "jmeter")
        File binDir = new File(workDir, "bin")
        project.jmeter.workDir = workDir
        project.jmeter.binDir = binDir

        // Test Files //
        project.jmeter.testFileDir = project.jmeter.testFileDir == null ? new File(project.getProjectDir(), "src/test/jmeter") : project.jmeter.testFileDir

        // Logs //
        project.jmeter.reportDir = project.jmeter.reportDir ?: new File(buildDir, "jmeter-report")
        project.jmeter.jmLog = project.jmeter.jmLog ?:  new File(project.jmeter.reportDir, "jmeter.log")

        // Java Properties //
        project.jmeter.maxHeapSize = project.jmeter.maxHeapSize ?: "512M"
        project.jmeter.minHeapSize = project.jmeter.minHeapSize ?: "512M"

        // Plugin Options
        project.jmeter.ignoreErrors = project.jmeter.ignoreErrors == null ? false : project.jmeter.ignoreErrors
        project.jmeter.ignoreFailures = project.jmeter.ignoreFailures == null ? false : project.jmeter.ignoreFailures
        project.jmeter.csvLogFile = project.jmeter.csvLogFile == null ? true : project.jmeter.csvLogFile
        project.jmeter.showSummarizer = project.jmeter.showSummarizer == null ? true : project.jmeter.showSummarizer
		project.jmeter.failBuildOnError = project.jmeter.failBuildOnError == null ? true : project.jmeter.failBuildOnError
        
        LoadPluginProperties()
        project.jmeter.jmVersion = this.jmeterVersion

        //Create required folders
        binDir.mkdirs()

        def jmeterJUnitFolder = new File(workDir, "lib/junit")
        jmeterJUnitFolder.mkdirs()

        def jmeterExtFolder = new File(workDir, "lib/ext")
        jmeterExtFolder.mkdirs()
        project.jmeter.reportDir.mkdirs()

        initTempProperties()
        resolveJmeterSearchPath()

        //print version info
        log.info("------------------------")
        log.info("Using")
        log.info("   jmeter-gradle-plugin version:" + this.thisPluginVersion)
        log.info("   jmeter version:" + this.jmeterVersion)
        log.info("   jmeter jp@gc plugins version:" + this.jmeterPluginsVersion)
        log.info("------------------------")
    }

    protected void initTempProperties() throws IOException {
        List<File> tempProperties = []

        [
                'saveservice.properties',
                'system.properties',
                'reportgenerator.properties',
                'user.properties',
                'jmeter-plugin.properties',
                'log4j2.xml'
        ].each {
            File f = new File(project.jmeter.workDir as File, it)
            log.debug('{} location is {}', it, f.absolutePath)
            tempProperties << f
        }

        File upgradeProperties = new File(project.jmeter.workDir as File, 'upgrade.properties')
        System.setProperty('upgrade_properties', '/' + upgradeProperties.getName())
        tempProperties.add(upgradeProperties)

        File defaultJmeterProperties = new File(project.jmeter.workDir as File, 'jmeter.properties')
        System.setProperty('default_jm_properties', '/' + defaultJmeterProperties.getName())
        tempProperties.add(defaultJmeterProperties)

        //Copy files from jar to workDir
        for (File f : tempProperties) {
            try {
                f.withWriter { Writer writer ->
                    IOUtils.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream(f.getName()), writer, 'UTF-8')
                }
            } catch (IOException ioe) {
                throw new GradleException("Couldn't create temporary property file " + f.getName() + " in directory " + f.getParent(), ioe)
            }
        }
    }

    protected void resolveJmeterSearchPath() {
        StringBuilder cp = new StringBuilder()
        URL[] classPath = ((URLClassLoader) this.getClass().getClassLoader()).getURLs()
        String jmeterVersionPattern = project.jmeter.jmVersion.replaceAll("[.]", "[.]")
        String pathSeparator = ';' //intentionally not File.PathSeparator - JMeter parses for ; on all platforms
        for (URL dep : classPath) {
            if (dep.getPath().matches("^.*org[./]apache[./]jmeter[/]ApacheJMeter.*" +
                    jmeterVersionPattern + ".jar\$")) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
            } else if (dep.getPath().matches("^.*bsh.*[.]jar\$")) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
                //add jp@gc plugins to search_path
            } else if (dep.getPath().matches("^.*jmeter-.*\$")) {
                cp.append(dep.getPath())
                cp.append(pathSeparator)
            }
        }
        cp.append(new File(project.jmeter.workDir as File, "lib" + File.separator + "ext").getCanonicalPath())
        System.setProperty("search_paths", cp.toString())
        log.debug("Search path is set to " + System.getProperty("search_paths"))
    }

    private void LoadPluginProperties() {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("jmeter-plugin.properties")
            if (is == null) {
                log.error("Error fetching jmeter version")
                throw new GradleException("Error fetching jmeter version")
            }
            Properties pluginProps = new Properties()
            pluginProps.load(is)
            is.close()

            this.thisPluginVersion = pluginProps.getProperty("thisPlugin.version")
            this.jmeterVersion = pluginProps.getProperty("jmeter.version")
            this.jmeterPluginsVersion = pluginProps.getProperty("plugin.version")
        } catch (Exception e) {
            log.error("Can't load JMeter version, build will stop", e)
            throw new GradleException("Can't load JMeter version, build will stop", e)
        }
    }
}
