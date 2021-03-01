package net.foragerr.jmeter.gradle.plugins.worker

import groovy.util.logging.Slf4j
import org.apache.jmeter.JMeter
import org.gradle.workers.WorkAction

@Slf4j
abstract class JMeterAction implements WorkAction<JMeterParameters> {
    @Override
    void execute() {
        // JMeter starts a non-daemon thread to run the test and exists. Running jmeter from the command line
        // only exits when the non-daemon thread exits, this is how the JVM works. We are running in a Gradle
        // daemon and so need to watch the thread ourselves. We're making an assumption on the name of the
        // thread which is not be guaranteed.

        Set<Thread> preExistingThreads = Thread.getAllStackTraces().keySet().findAll {
            it.name.toLowerCase().contains('jmeter')
        }

        log.info 'JMeter starting'

        JMeter jMeter = new JMeter()
        jMeter.start(getParameters().args.getOrElse(new String[0]))

        Set<Thread> newThreads = Thread.getAllStackTraces().keySet().findAll {
            it.name.toLowerCase().contains('jmeter') && !preExistingThreads.contains(it)
        }

        while (newThreads.any { it.isAlive() }) {
            log.info "Waiting for JMeter threads to end: \"${newThreads*.name.join('", "')}\""
            Thread.sleep(10000)
        }

        log.info 'JMeter ended'
    }
}
