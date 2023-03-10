/*******************************************************************************
 * Copyright (c) 2015, 2016 Institute for Pervasive Computing, ETH Zurich and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * <p>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.html.
 * <p>
 * Contributors:
 * Matthias Kovatsch - creator and main architect
 * Martin Lanter - architect and re-implementation
 * Dominique Im Obersteg - parsers and initial implementation
 * Daniel Pauli - parsers and initial implementation
 * Kai Hudalla - logging
 * Bosch Software Innovations GmbH - turn into utility class with static methods only
 * Joe Magerramov (Amazon Web Services) - CoAP over TCP support.
 * Achim Kraus (Bosch Software Innovations GmbH) - replace byte array token by Token
 ******************************************************************************/
package org.eclipse.californium.core.network.serialization;

import static org.eclipse.californium.core.coap.CoAP.MessageFormat.*;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Message;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DataSerialized serializes outgoing messages to byte arrays.
 */
public final class UdpDataSerializer extends DataSerializer {
	public static final Logger LOG = LoggerFactory.getLogger(UdpDataSerializer.class);

	/**
	 * {@inheritDoc}
	 * 
	 * The serialized length is not relevant for UDP. Therefore write message
	 * direct to writer.
	 * 
	 * @since 2.6
	 */
	@Override
	protected void serializeMessage(DatagramWriter writer, Message message) {
		MessageHeader header = new MessageHeader(CoAP.VERSION, message.getType(), message.getToken(),
				message.getRawCode(), message.getMID(), -1);
		serializeHeader(writer, header);
		writer.writeCurrentByte();
		serializeOptionsAndPayload(writer, message.getOptions(), message.getPayload());
	}

	@Override 
	protected void serializeHeader(final DatagramWriter writer, final MessageHeader header) {
		writer.write(VERSION, VERSION_BITS);
		writer.write(header.getType().value, TYPE_BITS);
		writer.write(header.getToken().length(), TOKEN_LENGTH_BITS);
		writer.write(header.getCode(), CODE_BITS);
		writer.write(header.getMID(), MESSAGE_ID_BITS);
		LOG.error("??????---->> "+"??????header, ver: {},type: {},token length: {},code: {},message id: {}",VERSION,header.getType().value,header.getToken().length(),
				header.getCode(),header.getMID());
		writer.writeBytes(header.getToken().getBytes());
		LOG.error("??????---->> "+"??????token, token: {}",header.getToken().toString());
	}
}
