/*******************************************************************************
 * Copyright (c) 2008, 2022 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *     Sebastien Arod - update version ranges
 *******************************************************************************/
package org.eclipse.tycho.versions.engine.tests;

import static org.junit.Assert.assertThrows;

import java.io.File;

import org.eclipse.tycho.testing.TestUtil;
import org.eclipse.tycho.testing.TychoPlexusTestCase;
import org.eclipse.tycho.versions.engine.IllegalVersionChangeException;
import org.eclipse.tycho.versions.engine.ProjectMetadataReader;
import org.eclipse.tycho.versions.engine.Versions;
import org.eclipse.tycho.versions.engine.VersionsEngine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VersionsEngineTest extends TychoPlexusTestCase {
    @Test
    public void testSimple() throws Exception {
        File basedir = TestUtil.getBasedir("projects/simple");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("simple", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);
        AbstractVersionChangeTest.assertBundleManifest(basedir);
    }

    @Test
    public void testExportPackage() throws Exception {
        File basedir = TestUtil.getBasedir("projects/exportpackage");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("exportpackage", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);
        AbstractVersionChangeTest.assertBundleManifest(basedir);
    }

    @Test
    public void testExportPackageNoBump() throws Exception {
        File basedir = TestUtil.getBasedir("projects/exportpackage-nobump");

        VersionsEngine engine = newEngine(basedir);
        engine.setUpdatePackageVersions(false);
        engine.addVersionChange("exportpackage-nobump", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);
        AbstractVersionChangeTest.assertBundleManifest(basedir);
    }

    @Test
    public void testMultimodule() throws Exception {
        File basedir = TestUtil.getBasedir("projects/multimodule");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "feature01"));
        AbstractVersionChangeTest.assertFeatureXml(new File(basedir, "feature01"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "feature02"));
        AbstractVersionChangeTest.assertFeatureXml(new File(basedir, "feature02"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "feature03"));
        AbstractVersionChangeTest.assertFeatureXml(new File(basedir, "feature03"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "product"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "product"), "product.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "repository"));
        AbstractVersionChangeTest.assertCategoryXml(new File(basedir, "repository"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "repository"), "product.product");
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "repository"), "differentversion.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "repository-product-only"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "repository-product-only"), "product2.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "iu"));
        AbstractVersionChangeTest.assertP2IuXml(new File(basedir, "iu"));

    }

    @Test
    public void testUpdateVersionRanges() throws Exception {
        File basedir = TestUtil.getBasedir("projects/versionranges");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.setUpdateVersionRangeMatchingBounds(true);
        engine.apply();

        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle1"));

        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle2"));

        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle3"));

        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "fragment"));

    }

    @Test
    public void testProfile() throws Exception {
        File basedir = TestUtil.getBasedir("projects/profile");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle01"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle01"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle02"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle02"));
    }

    @Test
    public void testAggregator() throws Exception {
        File basedir = TestUtil.getBasedir("projects/aggregator");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("aggregator", "1.0.1.qualifier");
        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "parent"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "detached"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "detached"));
    }

    @Test
    public void testDependencySimple() throws Exception {
        File basedir = TestUtil.getBasedir("projects/dependencysimple");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("someproject", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "someproject"));
    }

    @Test
    public void testDependencyOtherVersion() throws Exception {
        File basedir = TestUtil.getBasedir("projects/dependencyotherversion");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("someproject", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "someproject"));
    }

    @Test
    public void testDependencyManagmentSimple() throws Exception {
        File basedir = TestUtil.getBasedir("projects/dependencymanagementsimple");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("someproject", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "someproject"));
    }

    @Test
    public void testDependencyManagmentOtherVersion() throws Exception {
        File basedir = TestUtil.getBasedir("projects/dependencymanagementotherversion");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("someproject", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "someproject"));
    }

    @Test
    public void testDeepNesting() throws Exception {
        File basedir = TestUtil.getBasedir("projects/deepnesting");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "child"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "child/grandchild"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "child/grandchild/bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "child/grandchild/bundle"));
    }

    @Test
    public void testDeepNestingInverseOrder() throws Exception {
        File basedir = TestUtil.getBasedir("projects/deepnesting");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("child", "1.0.1.qualifier");
        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "child"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "child/grandchild"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "child/grandchild/bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "child/grandchild/bundle"));
    }

    @Test
    public void testExplicitVersion() throws Exception {
        File basedir = TestUtil.getBasedir("projects/exlicitversion");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "otherversion"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "otherversion"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "sameversion"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "sameversion"));
    }

    @Test
    public void testPomDependencyNoVersion() throws Exception {
        File basedir = TestUtil.getBasedir("projects/dependencynoversion");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("testmodule", "4.8");
        engine.apply();

        AbstractVersionChangeTest.assertPom(new File(basedir, "module"));
    }

    @Test
    public void testWrongSnapshotVersion() throws Exception {
        try {
            Versions.assertIsOsgiVersion("1.2.3_SNAPSHOT");
            fail("invalid version accepted");
        } catch (NumberFormatException e) {
            // thrown by equinox <3.8M5
        } catch (IllegalArgumentException e) {
            // thrown by equinox 3.8M5
        }
    }

    @Test
    public void testAssertOsgiVersion() {
        Versions.assertIsOsgiVersion("1.2.3.qualifier");
    }

    @Test
    public void testBuildPluginManagement() throws Exception {
        File basedir = TestUtil.getBasedir("projects/pluginmanagement");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("parent", "1.0.1.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);
        AbstractVersionChangeTest.assertPom(new File(basedir, "plugin"));
        AbstractVersionChangeTest.assertPom(new File(basedir, "jar"));
    }

    @Test
    public void testPomProperties() throws Exception {
        File basedir = TestUtil.getBasedir("projects/pomproperties");

        VersionsEngine engine = newEngine(basedir);

        engine.addPropertyChange("pomproperties", "p1", "changed");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);
    }

    @Test
    public void testNonOsgiVersionOsgiProject() throws Exception {
        assertNonOsgiVersionOsgiProject("bundle");
        assertNonOsgiVersionOsgiProject("feature");
        assertNonOsgiVersionOsgiProject("product");
        assertNonOsgiVersionOsgiProject("repository");
    }

    private void assertNonOsgiVersionOsgiProject(String artifactId) throws Exception {
        File basedir = TestUtil.getBasedir("projects/nonosgiversion/" + artifactId);

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange(artifactId, "1.0.1-01");
        IllegalVersionChangeException e = assertThrows(IllegalVersionChangeException.class, () -> engine.apply());
        // not a valid osgi version
        assertEquals(1, e.getErrors().size());
    }

    @Test
    public void testNonOsgiVersionNonOsgiProject() throws Exception {
        File basedir = TestUtil.getBasedir("projects/nonosgiversion/maven");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("maven", "1.0.1-01");
        engine.apply();
    }

    @Test
    public void testBuildPluginNoGroupId() throws Exception {
        File basedir = TestUtil.getBasedir("projects/buildpluginnogroupid");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("buildpluginnogroupid", "1.0.1-01");
        engine.apply();
    }

    @Test
    public void testProfileNoId() throws Exception {
        File basedir = TestUtil.getBasedir("projects/profilenoid");

        VersionsEngine engine = newEngine(basedir);

        engine.addVersionChange("profilenoid", "1.0.1-01");
        engine.apply();
    }

    @Test
    public void testTargetPlatform() throws Exception {
        File basedir = TestUtil.getBasedir("projects/targetplatform");

        VersionsEngine engine = newEngine(basedir);
        engine.addVersionChange("parent", "0.2.0.qualifier");
        engine.apply();

        AbstractVersionChangeTest.assertPom(basedir);

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle01"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle01"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "targetplatform"));
    }

    private VersionsEngine newEngine(File basedir) throws Exception {
        VersionsEngine engine = lookup(VersionsEngine.class);
        ProjectMetadataReader reader = lookup(ProjectMetadataReader.class);

        reader.addBasedir(basedir, true);

        engine.setProjects(reader.getProjects());

        return engine;
    }
}
