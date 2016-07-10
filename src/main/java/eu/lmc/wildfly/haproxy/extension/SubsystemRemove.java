package eu.lmc.wildfly.haproxy.extension;

import eu.lmc.wildfly.haproxy.server.HaProxyAgentService;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;

import java.util.Set;

/**
 * Handler responsible for removing the subsystem resource from the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class SubsystemRemove extends AbstractRemoveStepHandler {

    private final Logger logger = Logger.getLogger(SubsystemRemove.class);

    static final SubsystemRemove INSTANCE = new SubsystemRemove();


    private SubsystemRemove() {
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        logger.info("removing subsystem...");

        final Resource submodel = context.getOriginalRootResource().navigate(context.getCurrentAddress());
        String childType = SubsystemExtension.SERVER_PATH.getKey();
        final Set<Resource.ResourceEntry> children = submodel.getChildren(childType);
        for (Resource.ResourceEntry serverResource : children) {
            String key = serverResource.getPathElement().getValue();
            final ServiceName name = HaProxyAgentService.createServiceName(key);
            logger.debug("removing: " + serverResource);
            context.removeService(name);
        }
    }


}
