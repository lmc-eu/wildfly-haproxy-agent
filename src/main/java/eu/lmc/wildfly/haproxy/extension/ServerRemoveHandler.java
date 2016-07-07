package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Remove "server" element
 */
class ServerRemoveHandler extends AbstractRemoveStepHandler {

    public static final ServerRemoveHandler INSTANCE = new ServerRemoveHandler();

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String key = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();
        final ServiceName name = HaProxyAgentService.createServiceName(key);
        context.removeService(name);
    }
}
