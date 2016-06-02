/*
 * Copyright (c) 2016 Cisco Systems, Inc.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.lispflowmapping.southbound.lisp;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.lispflowmapping.lisp.util.LispAddressUtil;
import org.opendaylight.lispflowmapping.mapcache.SimpleMapCache;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.lisp.address.types.rev151105.lisp.address.address.Ipv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.lisp.address.types.rev151105.lisp.address.address.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.lisp.address.types.rev151105.lisp.address.address.Ipv6;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.lisp.address.types.rev151105.lisp.address.address.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.lfm.lisp.proto.rev151105.eid.container.Eid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.lfm.mappingservice.rev150906.MappingDatabase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.lfm.mappingservice.rev150906.db.instance.AuthenticationKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.lfm.mappingservice.rev150906.db.instance.AuthenticationKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.lfm.mappingservice.rev150906.mapping.database.VirtualNetworkIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataListener for all AuthenticationKey modification events.
 *
 */
public class AuthenticationKeyDataListener implements DataTreeChangeListener<AuthenticationKey> {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationKeyDataListener.class);

    private final SimpleMapCache smc;
    private final DataBroker broker;
    private final InstanceIdentifier<AuthenticationKey> path;
    private ListenerRegistration<DataTreeChangeListener<AuthenticationKey>> registration;


    public AuthenticationKeyDataListener(final DataBroker broker, final SimpleMapCache smc) {
        this.broker = broker;
        this.smc = smc;
        this.path = InstanceIdentifier.create(MappingDatabase.class).child(VirtualNetworkIdentifier.class)
                .child(AuthenticationKey.class);
        LOG.trace("Registering AuthenticationKey listener.");
        final DataTreeIdentifier<AuthenticationKey> dataTreeIdentifier = new DataTreeIdentifier<>
                (LogicalDatastoreType.CONFIGURATION, path);
        registration = broker.registerDataTreeChangeListener(dataTreeIdentifier, this);
    }

    public void closeDataChangeListener() {
        registration.close();
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<AuthenticationKey>> changes) {
        for (DataTreeModification<AuthenticationKey> change : changes) {
            final DataObjectModification<AuthenticationKey> mod = change.getRootNode();

            if (ModificationType.DELETE == mod.getModificationType()) {
                final AuthenticationKey authKey = mod.getDataBefore();

                LOG.trace("Received deleted data");
                LOG.trace("Key: {}", change.getRootPath().getRootIdentifier());
                LOG.trace("Value: {}", authKey);

                final AuthenticationKey convertedAuthKey = convertToBinaryIfNecessary(authKey);

                smc.removeAuthenticationKey(convertedAuthKey.getEid());
            } else if (ModificationType.WRITE == mod.getModificationType() || ModificationType.SUBTREE_MODIFIED == mod
                    .getModificationType()) {
                if (ModificationType.WRITE == mod.getModificationType()) {
                    LOG.trace("Received created data");
                } else {
                    LOG.trace("Received updated data");
                }
                // Process newly created or updated authentication keys
                final AuthenticationKey authKey = mod.getDataAfter();

                LOG.trace("Key: {}", change.getRootPath().getRootIdentifier());
                LOG.trace("Value: {}", authKey);

                final AuthenticationKey convertedAuthKey = convertToBinaryIfNecessary(authKey);

                smc.addAuthenticationKey(convertedAuthKey.getEid(), convertedAuthKey.getMappingAuthkey());
            } else {
                LOG.warn("Ignoring unhandled modification type {}", mod.getModificationType());
            }
        }
    }

    private static AuthenticationKey convertToBinaryIfNecessary(AuthenticationKey authKey) {
        Eid originalEid = authKey.getEid();
        if (originalEid.getAddress() instanceof Ipv4Prefix || originalEid.getAddress() instanceof Ipv6Prefix ||
                originalEid.getAddress() instanceof Ipv4 || originalEid.getAddress() instanceof Ipv6) {
            AuthenticationKeyBuilder akb = new AuthenticationKeyBuilder(authKey);
            akb.setEid(LispAddressUtil.convertToBinary(originalEid));
            return akb.build();
        }
        return authKey;
    }

}
