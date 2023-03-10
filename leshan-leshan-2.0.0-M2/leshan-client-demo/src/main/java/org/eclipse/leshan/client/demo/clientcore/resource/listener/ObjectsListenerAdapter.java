/*******************************************************************************
 * Copyright (c) 2019 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.demo.clientcore.resource.listener;

import org.eclipse.leshan.client.demo.core.object.LwM2mObjectEnabler;

public class ObjectsListenerAdapter implements ObjectsListener {

    @Override
    public void objectInstancesAdded(LwM2mObjectEnabler object, int... instanceIds) {
    }

    @Override
    public void objectInstancesRemoved(LwM2mObjectEnabler object, int... instanceIds) {
    }

    @Override
    public void objectAdded(LwM2mObjectEnabler object) {
    }

    @Override
    public void objectRemoved(LwM2mObjectEnabler object) {
    }

    @Override
    public void resourceChanged(LwM2mObjectEnabler object, int instanceId, int... resourcesIds) {
    }
}
