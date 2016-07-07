package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.threads.ThreadsServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

import java.util.List;
import java.util.concurrent.ThreadFactory;

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
        ServerDefinition.SOURCE_ATTR.validateAndSet(operation, model);
        ServerDefinition.THREAD_FACTORY_ATTR.validateAndSet(operation, model);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers) throws OperationFailedException {
        final ModelNode socketBinding = ServerDefinition.SOCKET_BINDING_ATTR.resolveModelAttribute(context, model);
        final ModelNode threadFactoryNode = ServerDefinition.THREAD_FACTORY_ATTR.resolveModelAttribute(context, model);
        //source name is special, because it's key
        final String sourceName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();
        final HaProxyAgentService service = new HaProxyAgentService(sourceName);

        final ServiceName name = HaProxyAgentService.createServiceName(sourceName);
        final ServiceBuilder<HaProxyAgentService> sb = context.getServiceTarget().addService(name, service);

        if (socketBinding.isDefined()) {
            sb.addDependency(SocketBinding.JBOSS_BINDING_NAME.append(socketBinding.asString()), SocketBinding.class, service.getInjectedSocketBinding());
        }
        if (threadFactoryNode.isDefined()) {
            sb.addDependency(ThreadsServices.threadFactoryName(threadFactoryNode.asString()),
                    ThreadFactory.class, service.getInjectedThreadFactory());
        }

        final ServiceController<HaProxyAgentService> controller = sb.addListener(verificationHandler)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
        newControllers.add(controller);
    }

}
