package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.io.IOServices;
import org.xnio.XnioWorker;

/**
 * Add "server" element
 */
class ServerAddHandler extends AbstractAddStepHandler {

    private static Logger log = Logger.getLogger(ServerAddHandler.class);

    public static final ServerAddHandler INSTANCE = new ServerAddHandler();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        ServerDefinition.NAME_ATTR.validateAndSet(operation, model);
        ServerDefinition.SOURCE_ATTR.validateAndSet(operation, model);
        ServerDefinition.SOCKET_BINDING_ATTR.validateAndSet(operation, model);
        ServerDefinition.WORKER_ATTR.validateAndSet(operation, model);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode model = resource.getModel();
        final ModelNode socketBinding = ServerDefinition.SOCKET_BINDING_ATTR.resolveModelAttribute(context, model);
        final ModelNode workerBinding = ServerDefinition.WORKER_ATTR.resolveModelAttribute(context, model);
        final String source = ServerDefinition.SOURCE_ATTR.resolveModelAttribute(context, model).asString();
        //source name is special, because it's key
        final String srvName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();
        final HaProxyAgentService service = new HaProxyAgentService(srvName, source);

        final ServiceName name = HaProxyAgentService.createServiceName(srvName);
        final ServiceBuilder<HaProxyAgentService> sb = context.getServiceTarget().addService(name, service);

        if (socketBinding.isDefined()) {
            sb.addDependency(SocketBinding.JBOSS_BINDING_NAME.append(socketBinding.asString()), SocketBinding.class, service.getInjectedSocketBinding());
        }
        if (workerBinding.isDefined()) {
            sb.addDependency(IOServices.WORKER.append(workerBinding.asString()),
                    XnioWorker.class, service.getInjectedXnioWorker());
        }

        final ServiceController<HaProxyAgentService> controller = sb
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
        log.log(Logger.Level.INFO, "server controller added: " + controller);
    }

}
