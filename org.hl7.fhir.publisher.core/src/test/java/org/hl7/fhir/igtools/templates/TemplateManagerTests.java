package org.hl7.fhir.igtools.templates;

import org.apache.poi.ss.formula.functions.T;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TemplateManagerTests {

	public static final String HL7_TEMPLATE_NAME = "hl7.fhir.template";

	public static final String CUSTOM_TEMPLATE_NAME = "my.custom.template";
	public static final String CURRENT_VERSION = "current";
	public static final String HL7_TEMPLATE = HL7_TEMPLATE_NAME + "#" + CURRENT_VERSION;

	public static final String CUSTOM_TEMPLATE = CUSTOM_TEMPLATE_NAME + "#" + CURRENT_VERSION;
	FilesystemPackageCacheManager packageCacheManager;

	NpmPackage hl7Package = mock(NpmPackage.class);

	@BeforeEach
	public void beforeEach() throws IOException {
		packageCacheManager = new FilesystemPackageCacheManager(true) {
			public NpmPackage loadPackage(String id, String version) throws FHIRException, IOException {
				if (id.equals(CUSTOM_TEMPLATE_NAME) && version.equals(CURRENT_VERSION)) {

					String path = new File("src/test/resources/template/custom-template/custom-template").getAbsolutePath();
					return NpmPackage.fromFolder(path);
				}
				return super.loadPackage(id, version);
			}
		};
	}

	@Test
	public void testLoadHL7Template() throws IOException {
		IWorkerContext.ILoggingService loggingService  = mock(IWorkerContext.ILoggingService.class);
		TemplateManager templateManager = new TemplateManager(packageCacheManager, loggingService);
		File root = Files.createTempDirectory("testHl7Template").toFile();
		Template template = templateManager.loadTemplate(HL7_TEMPLATE, root.getAbsolutePath(), "dummyPackage", true);
		template.onLoadEvent(getImplementationGuide(), new HashMap<>());
	}

	@Test
	public void testLoadCustomTemplate() throws IOException {
		IWorkerContext.ILoggingService loggingService  = mock(IWorkerContext.ILoggingService.class);
		TemplateManager templateManager = new TemplateManager(packageCacheManager, loggingService);
		File root = Files.createTempDirectory("testCustomTemplate").toFile();
		Template template = templateManager.loadTemplate(CUSTOM_TEMPLATE, root.getAbsolutePath(), "dummyPackage", true);
		template.onLoadEvent(getImplementationGuide(), new HashMap<>());
	}
	@Nonnull
	private static ImplementationGuide getImplementationGuide() {
		ImplementationGuide implementationGuide = new ImplementationGuide();
		implementationGuide.setVersion("1.2.3");
		return implementationGuide;
	}
}
