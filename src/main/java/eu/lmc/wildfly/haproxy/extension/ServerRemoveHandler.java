package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Remove "server" element
 */
class ServerRemoveHandler extends AbstractRemoveStepHandler {

    public static final ServerRemoveHandler INSTANCE = new ServerRemoveHandler();

}
