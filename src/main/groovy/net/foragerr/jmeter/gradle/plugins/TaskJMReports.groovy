package net.foragerr.jmeter.gradle.plugins

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import kg.apc.jmeter.PluginsCMDWorker
import net.foragerr.jmeter.gradle.plugins.utils.JMUtils
import net.foragerr.jmeter.gradle.plugins.utils.ReportTransformer
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.jmeter.util.JMeterUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.rendersnake.HtmlAttributes
import org.rendersnake.HtmlCanvas
import org.rendersnake.tools.PrettyWriter

import javax.xml.transform.TransformerException

import static org.rendersnake.HtmlAttributesFactory.class_
import static org.rendersnake.HtmlAttributesFactory.lang

@CompileDynamic
@Slf4j
class TaskJMReports extends DefaultTask {

    public static final List<String> PLUGIN_TYPES = Arrays.asList(
            'ResponseTimesOverTime',
            'HitsPerSecond',
            'BytesThroughputOverTime',
            'LatenciesOverTime',
            'ResponseCodesPerSecond',
            'TransactionsPerSecond',
            'ResponseTimesDistribution',
            'ResponseTimesPercentiles',
            'ThreadsStateOverTime',
            'TimesVsThreads',
            'ThroughputVsThreads'
    )

    // TODO: createReports should only kick-in if there are new jtl files to process.
    @OutputDirectory
    File reportDir = null

    @TaskAction
    void jmCreateReport() {
        final JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        reportDir = jmeter.reportDir ?: new File(project.buildDir, 'jmeter-report')

        //Get List of resultFiles
        List<File> jmResultFiles = []
        jmResultFiles.addAll(JMUtils.scanDir(project, ['**/*.xml'] as String[], [] as String[], reportDir))

        if (jmResultFiles.size() == 0) {
            log.warn('There are no results file to create reports from')
        }

        if (jmeter.enableReports) {
            makeHTMLReport(jmResultFiles)
        }
        if (jmeter.enableExtendedReports) {
            makeExtendedReports(jmResultFiles)
        }
    }

    private void makeExtendedReports(List<File> results) throws IOException {
        for (File resultFile : results) {
            try {
                log.info('Creating Extended Reports {}', resultFile.getName())
                createExtendedReport(resultFile)
            } catch (Throwable e) {
                log.error('Failed to create extended report for {}', resultFile, e)
            }
        }
    }

    private void createExtendedReport(File resultFile) {
        File workDir = project.jmeter.workDir

        String name = FilenameUtils.removeExtension(resultFile.getName())
        initializeJMeter(name, JMUtils.getJmeterPropsFile(project), workDir, reportDir)

        File imgDir = new File(reportDir, 'extReport-img')
        File csvDir = new File(reportDir, 'extReport-csv')
        imgDir.mkdirs()
        csvDir.mkdirs()

        PluginsCMDWorker worker = new PluginsCMDWorker()
        for (String plugin : PLUGIN_TYPES) {
            try {
                worker.setPluginType(plugin)
                worker.addExportMode(PluginsCMDWorker.EXPORT_PNG)
                worker.setOutputPNGFile("${imgDir.getCanonicalPath()}$File.separator$name-${plugin}.png")
                worker.addExportMode(PluginsCMDWorker.EXPORT_CSV)
                worker.setOutputCSVFile("${csvDir.getCanonicalPath()}$File.separator$name-${plugin}.csv")
                worker.setInputFile(resultFile.getAbsolutePath())
                worker.doJob()
            } catch (Exception e) {
                log.error("Failed to create report: $plugin for $name due to: ", e)
            }
        }

        //generate HTML
        makeHTMLExtendedReport(resultFile)
    }

    private static void initializeJMeter(String name, File jmProps, File jmHome, File reportBaseDir) {
        // Initialize JMeter settings..
        JMeterUtils.setJMeterHome(jmHome.getAbsolutePath())
        JMeterUtils.loadJMeterProperties(jmProps.getAbsolutePath())
        JMeterUtils.setProperty('log_file', "${reportBaseDir.getCanonicalPath()}$File.separator${name}.log")
        JMeterUtils.initLocale()
    }

    private void makeHTMLReport(List<File> results) {
        try {
            ReportTransformer transformer
            transformer = new ReportTransformer(getXslt())
            log.info('Building HTML Report.')
            for (File resultFile : results) {
                String reportTitle = project.jmeter.reportTitle ?: "Generated from: ${resultFile.getName()}"
                final File outputFile = new File(toOutputFileName(resultFile.getAbsolutePath()))
                log.info('transforming: {} to {}', resultFile, outputFile)
                transformer.transform(resultFile, outputFile, reportTitle)
            }
        } catch (FileNotFoundException e) {
            log.error('Can\'t transform result', e)
            throw new GradleException("Error writing report file jmeter file.", e)
        } catch (TransformerException e) {
            log.error('Can\'t transform result', e)
            throw new GradleException("Error transforming jmeter results", e)
        } catch (IOException e) {
            log.error('Can\'t transform result', e)
            throw new GradleException('Error copying resources to jmeter results', e)
        } catch (Exception e) {
            log.error('Can\'t transform result', e)
        }
    }

    private String toOutputFileName(String fileName) {
        JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        if (fileName.endsWith('.xml')) {
            return fileName.replace('.xml', jmeter.reportPostfix + '.html')
        } else {
            return fileName + jmeter.reportPostfix
        }
    }

    private InputStream getXslt() throws IOException {
        JMPluginExtension jmeter = project.jmeter as JMPluginExtension

        if (jmeter.reportXslt == null) {
            //if we are using the default report, also copy the images out.
            IOUtils.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream('reports/collapse.jpg'), new FileOutputStream("${reportDir.getPath()}${File.separator}collapse.jpg"))
            IOUtils.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream('reports/expand.jpg'), new FileOutputStream("${reportDir.getPath()}${File.separator}expand.jpg"))
            log.debug('Using reports/jmeter-results-detail-report_21.xsl for building report')
            return Thread.currentThread().getContextClassLoader().getResourceAsStream('reports/jmeter-results-detail-report_21.xsl')
        } else {
            log.debug('Using {} for building report', jmeter.reportXslt)
            return new FileInputStream(jmeter.reportXslt)
        }
    }

    private makeHTMLExtendedReport(File resultFile) {
        //Get list of images
        File imgDir = new File(reportDir, "extReport-img")
        String includePattern = "**/*" + FilenameUtils.removeExtension(resultFile.getName()) + "*.png"
        File[] listOfImages = JMUtils.scanDir(project, [includePattern] as String[], [] as String[], imgDir)

        //create HTML
        HtmlCanvas html = new HtmlCanvas(new PrettyWriter())
        html
                .html(lang("en"))
                .head()
                .title()
                .content('Extended Test Report - generated by jmeter-gradle-plugin')
                ._head()
                .body()
                .div(class_('simple'))
                .h1().content('Extended Test Report')
                .p().content('Generated by jmeter-gradle-plugin')
                ._div()

        listOfImages.each { File image ->
            html.div(class_('image'))
                    .img(new HtmlAttributes().src(imgDir.getName() + File.separator + image.getName()))
                    .p().content(image.getName())
                    ._div()
        }
        html._body()
                ._html()

        new File(reportDir, "${resultFile.getName()}-extReport.html").write(html.toHtml())
    }

}