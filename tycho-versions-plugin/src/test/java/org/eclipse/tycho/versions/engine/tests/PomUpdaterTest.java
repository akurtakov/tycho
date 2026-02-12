/*******************************************************************************
 * Copyright (c) 2011, 2017 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Bachmann electronic GmbH. - Bug #512326 Support product file names other than artifact id
 *    Bachmann electronic GmbH. - #517664 Support for updating p2iu versions
 *******************************************************************************/
package org.eclipse.tycho.versions.engine.tests;

import java.io.File;

import org.eclipse.tycho.testing.TestUtil;
import org.eclipse.tycho.testing.TychoPlexusTestCase;
import org.eclipse.tycho.versions.engine.PomVersionUpdater;
import org.eclipse.tycho.versions.engine.ProjectMetadataReader;
import org.junit.Before;
import org.junit.Test;

public class PomUpdaterTest extends TychoPlexusTestCase {

    private ProjectMetadataReader reader;

    @Before
    public void setUp() throws Exception {
        reader = lookup(ProjectMetadataReader.class);
    }

    @Test
    public void test() throws Exception {
        File basedir = TestUtil.getBasedir("projects/updatepom");

        reader.addBasedir(basedir, true);

        PomVersionUpdater updater = lookup(PomVersionUpdater.class);
        updater.setProjects(reader.getProjects());
        updater.apply();

        AbstractVersionChangeTest.assertPom(new File(basedir, "bundle"));
        AbstractVersionChangeTest.assertBundleManifest(new File(basedir, "bundle"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "feature"));
        AbstractVersionChangeTest.assertFeatureXml(new File(basedir, "feature"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "product"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "product"), "product.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "repository"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "repository"), "repository.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "repositoryWithOneProductFile"));
        AbstractVersionChangeTest.assertProductFile(new File(basedir, "repositoryWithOneProductFile"), "anotherNameThanArtifactId.product");

        AbstractVersionChangeTest.assertPom(new File(basedir, "repositoryWith2ProductFiles"));

        AbstractVersionChangeTest.assertPom(new File(basedir, "iu"));
        AbstractVersionChangeTest.assertP2IuXml(new File(basedir, "iu"));
    }
}
