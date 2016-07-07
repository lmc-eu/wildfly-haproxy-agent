package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Add "server" element
 */
class ServerAddHandler extends AbstractAddStepHandler {

    public static final ServerAddHandler INSTANCE = new ServerAddHandler();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        ServerDefinition.SOCKET_BINDING_ATTR.validateAndSet(operation, model);
        ServerDefinition.FILE_ATTR.validateAndSet(operation, model);
        ServerDefinition.THREAD_FACTORY_ATTR.validateAndSet(operation, model);
    }


}
