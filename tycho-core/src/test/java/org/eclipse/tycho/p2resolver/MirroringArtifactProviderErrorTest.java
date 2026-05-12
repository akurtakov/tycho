/*******************************************************************************
 * Copyright (c) 2013, 2022 SAP SE and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Tobias Oberlies (SAP SE) - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2resolver;

import static java.util.Collections.singletonList;
import static org.eclipse.tycho.test.util.ProbeArtifactSink.newArtifactSinkFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.tycho.p2.repository.ArtifactTransferPolicies;
import org.eclipse.tycho.p2.repository.LocalArtifactRepository;
import org.eclipse.tycho.p2.repository.MirroringArtifactProvider;
import org.eclipse.tycho.p2.repository.MirroringArtifactProvider.MirroringFailedException;
import org.eclipse.tycho.p2.repository.RepositoryArtifactProvider;
import org.eclipse.tycho.test.util.LogVerifier;
import org.eclipse.tycho.test.util.MockMavenContext;
import org.eclipse.tycho.test.util.ProbeArtifactSink;
import org.eclipse.tycho.test.util.TemporaryLocalMavenRepository;
import org.eclipse.tycho.test.util.TestRepositoryContent;
import org.eclipse.tycho.testing.TychoPlexusTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MirroringArtifactProviderErrorTest extends TychoPlexusTestCase {

    private static final IArtifactKey CORRUPT_ARTIFACT = TestRepositoryContent.BUNDLE_A_KEY;

    @Rule
    public LogVerifier logVerifier = new LogVerifier();
    @Rule
    public TemporaryLocalMavenRepository tempLocalMavenRepository = new TemporaryLocalMavenRepository();

    private LocalArtifactRepository localRepository;
    private ProbeArtifactSink testSink;

    MirroringArtifactProvider subject;

    @Before
    public void before() throws Exception {
        localRepository = tempLocalMavenRepository.getLocalArtifactRepository();

        subject = MirroringArtifactProvider.createInstance(localRepository,
                new RepositoryArtifactProvider(singletonList(TestRepositoryContent.REPO_BUNLDE_AB_PACK_CORRUPT),
                        ArtifactTransferPolicies.forLocalArtifacts(), lookup(IProvisioningAgent.class)),
                new MockMavenContext(null, logVerifier.getLogger()));
    }

    @Test
    public void testMirrorCorruptArtifact() throws Exception {
        logVerifier.expectError(CORRUPT_ARTIFACT.toString());

        testSink = newArtifactSinkFor(CORRUPT_ARTIFACT);
        // here we expect an exception, an not (!) an error status, to be consistent with other methods that mirror but don't return a status
        assertThrows(MirroringFailedException.class, () -> subject.getArtifact(testSink, null));
        assertNotMirrored(CORRUPT_ARTIFACT);
        assertFalse(testSink.writeIsStarted());
    }

    @Test
    public void testBrokenLocalArtifactIsRemovedOnFailedMirror() throws Exception {
        File localArtifactFile = new File(tempLocalMavenRepository.getLocalRepositoryRoot(), localRepoPathOf(CORRUPT_ARTIFACT));
        assertTrue("Failed to create parent directory",
                localArtifactFile.getParentFile().mkdirs() || localArtifactFile.getParentFile().isDirectory());
        Files.writeString(localArtifactFile.toPath(), "broken", StandardCharsets.UTF_8);
        assertTrue(localArtifactFile.isFile());

        testSink = newArtifactSinkFor(CORRUPT_ARTIFACT);
        assertThrows(MirroringFailedException.class, () -> subject.getArtifact(testSink, null));

        assertFalse("broken local artifact file should be removed", localArtifactFile.exists());
    }

    private void assertNotMirrored(IArtifactKey key) {
        assertEquals(0, localRepository.getArtifactDescriptors(key).length);
    }

    private static String localRepoPathOf(IArtifactKey key) {
        return "p2/" + key.getClassifier().replace('.', '/') + "/" + key.getId() + "/" + key.getVersion() + "/"
                + key.getId() + "-" + key.getVersion() + ".jar";
    }

}
