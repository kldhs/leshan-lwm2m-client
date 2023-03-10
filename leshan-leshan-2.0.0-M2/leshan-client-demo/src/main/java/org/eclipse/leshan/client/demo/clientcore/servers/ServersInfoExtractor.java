/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
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
 *     Achim Kraus (Bosch Software Innovations GmbH) - use ServerIdentity.SYSTEM
 *******************************************************************************/
package org.eclipse.leshan.client.demo.clientcore.servers;

import static org.eclipse.leshan.client.demo.clientcore.servers.ServerIdentity.SYSTEM;
import static org.eclipse.leshan.core.LwM2mId.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.Map;

import org.eclipse.leshan.client.demo.core.instance.LwM2mInstanceEnabler;
import org.eclipse.leshan.client.demo.core.object.LwM2mObjectEnabler;
import org.eclipse.leshan.core.CertificateUsage;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.SecurityMode;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.util.SecurityUtil;
import org.eclipse.leshan.core.util.datatype.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extract from LwM2m object tree all the servers information like server uri, security mode, ...
 */
public class ServersInfoExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(ServersInfoExtractor.class);

    public static ServersInfo getInfo(Map<Integer, LwM2mObjectEnabler> objectEnablers) {
        LwM2mObjectEnabler securityEnabler = objectEnablers.get(SECURITY);
        LwM2mObjectEnabler serverEnabler = objectEnablers.get(SERVER);

        if (securityEnabler == null || serverEnabler == null)
            return null;

        ServersInfo infos = new ServersInfo();
        LwM2mObject securities = (LwM2mObject) securityEnabler.read(SYSTEM, new ReadRequest(SECURITY)).getContent();
        LwM2mObject servers = (LwM2mObject) serverEnabler.read(SYSTEM, new ReadRequest(SERVER)).getContent();

        for (LwM2mObjectInstance security : securities.getInstances().values()) {
            try {
                if ((boolean) security.getResource(SEC_BOOTSTRAP).getValue()) {
                    if (infos.bootstrap != null) {
                        LOG.warn("There is more than one bootstrap configuration in security object.");
                    } else {
                        // create bootstrap info
                        ServerInfo info = new ServerInfo();
                        info.bootstrap = true;
                        LwM2mResource serverIdResource = security.getResource(SEC_SERVER_ID);
                        if (serverIdResource != null && serverIdResource.getValue() != null)
                            info.serverId = (long) serverIdResource.getValue();
                        else
                            info.serverId = 0;
                        info.serverUri = new URI((String) security.getResource(SEC_SERVER_URI).getValue());
                        info.secureMode = getSecurityMode(security);
                        if (info.secureMode == SecurityMode.PSK) {
                            info.pskId = getPskIdentity(security);
                            info.pskKey = getPskKey(security);
                        } else if (info.secureMode == SecurityMode.RPK) {
                            info.publicKey = getPublicKey(security);
                            info.privateKey = getPrivateKey(security);
                            info.serverPublicKey = getServerPublicKey(security);
                        } else if (info.secureMode == SecurityMode.X509) {
                            info.clientCertificate = getClientCertificate(security);
                            info.serverCertificate = getServerCertificate(security);
                            info.privateKey = getPrivateKey(security);
                            info.certificateUsage = getCertificateUsage(security);
                        }
                        infos.bootstrap = info;
                    }
                } else {
                    // create device management info
                    DmServerInfo info = new DmServerInfo();
                    info.bootstrap = false;
                    info.serverUri = new URI((String) security.getResource(SEC_SERVER_URI).getValue());
                    info.serverId = (long) security.getResource(SEC_SERVER_ID).getValue();
                    info.secureMode = getSecurityMode(security);
                    if (info.secureMode == SecurityMode.PSK) {
                        info.pskId = getPskIdentity(security);
                        info.pskKey = getPskKey(security);
                    } else if (info.secureMode == SecurityMode.RPK) {
                        info.publicKey = getPublicKey(security);
                        info.privateKey = getPrivateKey(security);
                        info.serverPublicKey = getServerPublicKey(security);
                    } else if (info.secureMode == SecurityMode.X509) {
                        info.clientCertificate = getClientCertificate(security);
                        info.serverCertificate = getServerCertificate(security);
                        info.privateKey = getPrivateKey(security);
                        info.certificateUsage = getCertificateUsage(security);
                    }
                    // search corresponding device management server
                    for (LwM2mObjectInstance server : servers.getInstances().values()) {
                        if (info.serverId == (Long) server.getResource(SRV_SERVER_ID).getValue()) {
                            info.lifetime = (long) server.getResource(SRV_LIFETIME).getValue();
                            info.binding = BindingMode.parse((String) server.getResource(SRV_BINDING).getValue());

                            infos.deviceManagements.put(info.serverId, info);
                            break;
                        }
                    }
                }
            } catch (URISyntaxException e) {
                LOG.error(String.format("Invalid URI %s", (String) security.getResource(SEC_SERVER_URI).getValue()), e);
            }
        }
        return infos;
    }

    public static DmServerInfo getDMServerInfo(Map<Integer, LwM2mObjectEnabler> objectEnablers, Long shortID) {
        ServersInfo info = getInfo(objectEnablers);
        if (info == null)
            return null;

        return info.deviceManagements.get(shortID);
    }

    public static ServerInfo getBootstrapServerInfo(Map<Integer, LwM2mObjectEnabler> objectEnablers) {
        ServersInfo info = getInfo(objectEnablers);
        if (info == null)
            return null;

        return info.bootstrap;
    }

    public static Long getLifeTime(LwM2mObjectEnabler serverEnabler, int instanceId) {
        ReadResponse response = serverEnabler.read(ServerIdentity.SYSTEM,
                new ReadRequest(SERVER, instanceId, SRV_LIFETIME));
        if (response.isSuccess()) {
            return (Long) ((LwM2mResource) response.getContent()).getValue();
        } else {
            return null;
        }
    }

    public static EnumSet<BindingMode> getServerBindingMode(LwM2mObjectEnabler serverEnabler, int instanceId) {
        ReadResponse response = serverEnabler.read(ServerIdentity.SYSTEM,
                new ReadRequest(SERVER, instanceId, SRV_BINDING));
        if (response.isSuccess()) {
            return BindingMode.parse((String) ((LwM2mResource) response.getContent()).getValue());
        } else {
            return null;
        }
    }

    public static EnumSet<BindingMode> getDeviceSupportedBindingMode(LwM2mObjectEnabler serverEnabler, int instanceId) {
        ReadResponse response = serverEnabler.read(ServerIdentity.SYSTEM,
                new ReadRequest(DEVICE, instanceId, DVC_SUPPORTED_BINDING));
        if (response.isSuccess()) {
            return BindingMode.parse((String) ((LwM2mResource) response.getContent()).getValue());
        } else {
            return null;
        }
    }

    public static Boolean isBootstrapServer(LwM2mObjectEnabler objectEnabler, int instanceId) {
        ReadResponse response = objectEnabler.read(ServerIdentity.SYSTEM,
                new ReadRequest(SECURITY, instanceId, SEC_BOOTSTRAP));
        if (response != null && response.isSuccess()) {
            return (Boolean) ((LwM2mResource) response.getContent()).getValue();
        } else {
            return null;
        }
    }

    public static Long getServerId(LwM2mObjectEnabler objectEnabler, int instanceId) {
        ReadResponse response = null;
        if (objectEnabler.getId() == SERVER) {
            response = objectEnabler.read(ServerIdentity.SYSTEM, new ReadRequest(SERVER, instanceId, SRV_SERVER_ID));
        } else if (objectEnabler.getId() == SECURITY) {
            response = objectEnabler.read(ServerIdentity.SYSTEM, new ReadRequest(SECURITY, instanceId, SEC_SERVER_ID));
        }
        if (response != null && response.isSuccess()) {
            return (Long) ((LwM2mResource) response.getContent()).getValue();
        } else {
            return null;
        }
    }

    public static SecurityMode getSecurityMode(LwM2mObjectInstance securityInstance) {
        return SecurityMode.fromCode((long) securityInstance.getResource(SEC_SECURITY_MODE).getValue());
    }

    public static CertificateUsage getCertificateUsage(LwM2mObjectInstance securityInstance) {
        return CertificateUsage.fromCode((ULong) securityInstance.getResource(SEC_CERTIFICATE_USAGE).getValue());
    }

    public static String getPskIdentity(LwM2mObjectInstance securityInstance) {
        byte[] pubKey = (byte[]) securityInstance.getResource(SEC_PUBKEY_IDENTITY).getValue();
        return new String(pubKey);
    }

    public static byte[] getPskKey(LwM2mObjectInstance securityInstance) {
        return (byte[]) securityInstance.getResource(SEC_SECRET_KEY).getValue();
    }

    private static PublicKey getPublicKey(LwM2mObjectInstance securityInstance) {
        byte[] encodedKey = (byte[]) securityInstance.getResource(SEC_PUBKEY_IDENTITY).getValue();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
        String algorithm = "EC";
        try {
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException e) {
            LOG.debug("Failed to instantiate key factory for algorithm " + algorithm, e);
        } catch (InvalidKeySpecException e) {
            LOG.debug("Failed to decode RFC7250 public key with algorithm " + algorithm, e);
        }
        return null;
    }

    private static PrivateKey getPrivateKey(LwM2mObjectInstance securityInstance) {
        byte[] encodedKey = (byte[]) securityInstance.getResource(SEC_SECRET_KEY).getValue();
        try {
            return SecurityUtil.privateKey.decode(encodedKey);
        } catch (IOException | GeneralSecurityException e) {
            LOG.debug("Failed to decode RFC5958 private key", e);
            return null;
        }
    }

    private static PublicKey getServerPublicKey(LwM2mObjectInstance securityInstance) {
        byte[] encodedKey = (byte[]) securityInstance.getResource(SEC_SERVER_PUBKEY).getValue();
        try {
            return SecurityUtil.publicKey.decode(encodedKey);
        } catch (IOException | GeneralSecurityException e) {
            LOG.debug("Failed to decode RFC7250 public key", e);
            return null;
        }
    }

    private static Certificate getServerCertificate(LwM2mObjectInstance securityInstance) {
        byte[] encodedCert = (byte[]) securityInstance.getResource(SEC_SERVER_PUBKEY).getValue();
        try {
            return SecurityUtil.certificate.decode(encodedCert);
        } catch (IOException | GeneralSecurityException e) {
            LOG.debug("Failed to decode X.509 certificate", e);
            return null;
        }
    }

    private static Certificate getClientCertificate(LwM2mObjectInstance securityInstance) {
        byte[] encodedCert = (byte[]) securityInstance.getResource(SEC_PUBKEY_IDENTITY).getValue();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (ByteArrayInputStream in = new ByteArrayInputStream(encodedCert)) {
                return cf.generateCertificate(in);
            }
        } catch (CertificateException | IOException e) {
            LOG.debug("Failed to decode X.509 certificate", e);
            return null;
        }
    }

    public static boolean isBootstrapServer(LwM2mInstanceEnabler instance) {
        ReadResponse response = instance.read(ServerIdentity.SYSTEM, LwM2mId.SEC_BOOTSTRAP);
        if (response == null || response.isFailure()) {
            return false;
        }

        LwM2mResource isBootstrap = (LwM2mResource) response.getContent();
        return (Boolean) isBootstrap.getValue();
    }
}
