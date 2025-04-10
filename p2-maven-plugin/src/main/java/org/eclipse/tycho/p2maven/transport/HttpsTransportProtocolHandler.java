/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven.transport;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.transport.TransportProtocolHandler;

@Component(role = TransportProtocolHandler.class, hint = "https")
public class HttpsTransportProtocolHandler extends HttpTransportProtocolHandler {

}
