package com.squareup.spoon;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import fr.xgouchet.axml.CompressedXmlParser;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Detailed instrumentation information.
 */
final class SpoonInstrumentationInfo {
    private String appPackage;
    private Integer minSdkVersion;
    private String testPackage;
    private String testRunnerClass;


    private SpoonInstrumentationInfo() {
    }

    String getApplicationPackage() {
        return appPackage;
    }

    Integer getMinSdkVersion() {
        return minSdkVersion;
    }

    String getInstrumentationPackage() {
        return testPackage;
    }

    String getTestRunnerClass() {
        return testRunnerClass;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    /**
     * Parse key information from an instrumentation APK's manifest.
     */
    static SpoonInstrumentationInfo parseFromFile(File apkTestFile) throws ParserConfigurationException {
        try (ZipFile zip = new ZipFile(apkTestFile)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            InputStream is = zip.getInputStream(entry);
            Document document = new CompressedXmlParser().parseDOM(is);

            SpoonInstrumentationInfo spoonInstrumentationInfo = findInfo(document.getDocumentElement(), new SpoonInstrumentationInfo());

            checkNotNull(spoonInstrumentationInfo.testPackage, "Could not find test application package.");
            checkNotNull(spoonInstrumentationInfo.appPackage, "Could not find application package.");
            checkNotNull(spoonInstrumentationInfo.testRunnerClass, "Could not find test runner class.");

            // Support relative declaration of instrumentation test runner.
            if (spoonInstrumentationInfo.testRunnerClass.startsWith(".")) {
                spoonInstrumentationInfo.testRunnerClass = spoonInstrumentationInfo.testPackage + spoonInstrumentationInfo.testRunnerClass;
            } else if (!spoonInstrumentationInfo.testRunnerClass.contains(".")) {
                spoonInstrumentationInfo.testRunnerClass = spoonInstrumentationInfo.testPackage + "." + spoonInstrumentationInfo.testRunnerClass;
            }

            return spoonInstrumentationInfo;

        } catch (IOException e) {
            throw new RuntimeException("Unable to parse test app AndroidManifest.xml.", e);
        }
    }

    static private SpoonInstrumentationInfo findInfo(Node node, SpoonInstrumentationInfo spoonInstrumentationInfo) {
        String nodeName = node.getNodeName();

        boolean isManifest = "manifest".equals(nodeName);
        boolean isUsesSdk = "uses-sdk".equals(nodeName);
        boolean isInstrumentation = "instrumentation".equals(nodeName);

        if (isManifest || isInstrumentation || isUsesSdk) {

            for (int j = 0; j < node.getAttributes().getLength(); j++) {
                String attributeName = node.getAttributes().item(j).getNodeName();

                if (isManifest && "package".equals(attributeName)) {
                    spoonInstrumentationInfo.testPackage = node.getAttributes().item(j).getNodeValue();
                } else if (isUsesSdk && "android:minSdkVersion".equals(attributeName)) {
                    spoonInstrumentationInfo.minSdkVersion = Integer.valueOf(node.getAttributes().item(j).getNodeValue());
                } else if (isInstrumentation && "android:targetPackage".equals(attributeName)) {
                    spoonInstrumentationInfo.appPackage = node.getAttributes().item(j).getNodeValue();
                } else if (isInstrumentation && "android:name".equals(attributeName)) {
                    spoonInstrumentationInfo.testRunnerClass = node.getAttributes().item(j).getNodeValue();
                }
            }
        }

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            findInfo(node.getChildNodes().item(i), spoonInstrumentationInfo);
        }

        return spoonInstrumentationInfo;
    }
}
