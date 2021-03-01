package net.foragerr.jmeter.gradle.plugins.worker

import groovy.io.FileType
import net.foragerr.jmeter.gradle.plugins.JMSpecs
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import java.util.regex.Pattern

class JMeterRunner {

    final WorkerExecutor workerExecutor
    final Iterable<File> jmeterConfiguration

    JMeterRunner(WorkerExecutor workerExecutor, Iterable<File> jmeterConfiguration) {
        this.workerExecutor = workerExecutor
        this.jmeterConfiguration = jmeterConfiguration
    }

    void executeJmeterCommand(JMSpecs specs, String workingDirectory) {
        WorkQueue workQueue = workerExecutor.processIsolation { ProcessWorkerSpec spec ->
            populateClassLoader(spec, workingDirectory)
            populateProcessSpecs(spec, specs, workingDirectory)
        }

        workQueue.submit(JMeterAction) {
            List<String> cliArgs = []
            cliArgs.addAll(specs.jmeterProperties)
            specs.getUserSystemProperties().each { userSysProp ->
                cliArgs << "-J${userSysProp}".toString()
            }

            getArgs().set(cliArgs as String[])
        }

        workQueue.await()
    }

    private void populateClassLoader(ClassLoaderWorkerSpec spec, String workDir) {
        // openjfx for non-Oracle JDK
        final Pattern openjfxPattern = ~/\/javafx-.*\.jar/
        final Pattern openjfxOSPattern = ~/\/javafx-.*-${operatingSystemClassifier()}\.jar/

        jmeterConfiguration
                .findAll {!it.name.find(openjfxPattern) || it.name.find(openjfxOSPattern) }
                .each { spec.classpath.from(it) }

        spec.classpath.with {
            File lib = new File(workDir, 'lib')
            File ext = new File(lib, 'ext')
            from(lib)
            from(ext)

            lib.eachFileRecurse(FileType.FILES) { file ->
                from(file)
            }
            ext.eachFileRecurse(FileType.FILES) { file ->
                from(file)
            }
        }
    }

    private void populateProcessSpecs(ProcessWorkerSpec spec, JMSpecs specs, String workingDirectory) {
        spec.forkOptions {
//            workingDir(workingDirectory)
            setMinHeapSize(specs.minHeapSize)
            setMaxHeapSize(specs.maxHeapSize)
            specs.getSystemProperties().each { k, v ->
                spec.forkOptions.systemProperty(k, v)
            }
        }
    }

    private String operatingSystemClassifier() {
        String platform = 'unsupported'
        int javaMajorVersion = System.properties['java.runtime.version'].split('[^0-9]+')[0] as int
        if (javaMajorVersion < 11) {
            return platform
        }
        OperatingSystem currentOS = org.gradle.internal.os.OperatingSystem.current()
        if (currentOS.isWindows()) {
            platform = 'win'
        } else if (currentOS.isLinux()) {
            platform = 'linux'
        } else if (currentOS.isMacOsX()) {
            platform = 'mac'
        }
        platform
    }
}
