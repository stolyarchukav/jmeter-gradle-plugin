package net.foragerr.jmeter.gradle.plugins.worker

import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

interface JMeterParameters extends WorkParameters {
    Property<String[]> getArgs()
}
