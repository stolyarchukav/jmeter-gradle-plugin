package net.foragerr.jmeter.gradle.plugins.utils

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import net.foragerr.jmeter.gradle.plugins.JMPluginExtension
import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.GradleException
import org.gradle.api.Project

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * Created by foragerr@gmail.com on 7/19/2015.
 */
@Slf4j
@CompileDynamic
class JMUtils {

    static List<File> getListOfTestFiles(Project project) {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        List<File> testFiles = new ArrayList<File>()
        if (jmeter.jmTestFiles != null) {
            jmeter.jmTestFiles.each { File file ->
                if (file.exists() && file.isFile()) {
                    testFiles.add(file)
                } else {
                    throw new GradleException("Test file ${file.getCanonicalPath()} does not exists")
                }
            }
        } else {
            String[] excludes = jmeter.excludes == null ? (new String[0]) : (jmeter.excludes as String[])
            String[] includes = jmeter.includes == null ? (['**/*.jmx'] as String[]) : (jmeter.includes as String[])
            log.info('includes: {}', includes)
            log.info('excludes: {}', excludes)
            testFiles.addAll(scanDir(project, includes, excludes, jmeter.testFileDir))
            log.info('{} test files found in folder scan', testFiles.size())
        }

        return testFiles
    }

    static File getJmeterPropsFile(Project project) {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        File propsInSrcDir = new File(jmeter.testFileDir, 'jmeter.properties')

        //1. Is jmeterPropertyFile defined?
        if (jmeter.jmPropertyFile != null) {
            return jmeter.jmPropertyFile
        }

        //2. Does jmeter.properties exist in $srcDir/test/jmeter
        else if (propsInSrcDir.exists()) {
            return propsInSrcDir
        }

        //3. If neither, use the default jmeter.properties
        else {
            File defPropsFile = new File(jmeter.workDir, System.getProperty('default_jm_properties'))
            return defPropsFile
        }
    }

    static File getResultFile(File testFile, Project project) {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension
        if (jmeter.resultsLog == null) {
            DateFormat fmt = new SimpleDateFormat('yyyyMMdd-HHmm')
            String fileExtension = jmeter.csvLogFile ? '.csv' : '.xml'
            return new File(jmeter.reportDir, "${testFile.name}-${fmt.format(new Date())}$fileExtension")
        } else {
            return jmeter.resultsLog
        }
    }

    @SuppressWarnings('UnusedMethodParameter')
    static List<File> scanDir(Project project, String[] includes, String[] excludes, File baseDir) {
        List<File> scanResults = []
        DirectoryScanner scanner = new DirectoryScanner()
        scanner.basedir = baseDir
        scanner.includes = includes
        scanner.excludes = excludes
        scanner.scan()
        for (String result : scanner.getIncludedFiles()) {
            scanResults.add(new File(scanner.getBasedir(), result))
        }
        return scanResults
    }
}
