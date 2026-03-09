/*******************************************************************************
 * Copyright (c) 2022, 2026 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.test.compiler;

import org.apache.maven.it.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

public class FragmentsTest extends AbstractTychoIntegrationTest {
	@Test
	public void testFragment() throws Exception {
		Verifier verifier = getVerifier("compiler.fragments", false);
		verifier.executeGoal("compile");
		verifier.verifyErrorFreeLog();
	}

	/**
	 * Verifies that sibling fragments (other fragments with the same Fragment-Host)
	 * are not added as standalone extra classpath entries when compiling a fragment
	 * bundle. This is a regression test for
	 * eclipse-platform/eclipse.platform.swt#3129 where SWT tests failed because
	 * sibling fragments were incorrectly added to the test/runtime classpath.
	 */
	@Test
	public void testSiblingFragmentsNotOnClasspath() throws Exception {
		Verifier verifier = getVerifier("compiler.siblingFragments", false);
		verifier.executeGoal("compile");
		verifier.verifyErrorFreeLog();
		// Verify that fragment.b (sibling) is NOT listed as a classpath entry
		// for fragment.a. The debug log would include "Skipping sibling fragment"
		// for fragment.b when compiling fragment.a.
		verifier.verifyTextInLog("Skipping sibling fragment");
	}
}

