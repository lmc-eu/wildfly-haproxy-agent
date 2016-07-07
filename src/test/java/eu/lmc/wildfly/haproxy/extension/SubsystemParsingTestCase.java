package eu.lmc.wildfly.haproxy.extension;


import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;


/**
 * Tests all management expects for subsystem, parsing, marshaling, model definition and other
 * Here is an example that allows you a fine grained controler over what is tested and how. So it can give you ideas what can be done and tested.
 * If you have no need for advanced testing of subsystem you look at {@link SubsystemBaseParsingTestCase} that testes same stuff but most of the code
 * is hidden inside of test harness
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SubsystemParsingTestCase extends AbstractSubsystemTest {

    public SubsystemParsingTestCase() {
        super(SubsystemExtension.SUBSYSTEM_NAME, new SubsystemExtension());
    }

    /**
     * Tests that the xml is parsed into the correct operations
     */
    @Test
    public void testParseSubsystem() throws Exception {
        //Parse the subsystem xml into operations
        String subsystemXml =
                "<subsystem xmlns=\"" + SubsystemExtension.NAMESPACE + "\">" +
                        "<server name=\"x1\" source=\"/tmp/wildfly-status-haproxy\"" +
                        " thread-pool-size=\"12\"" +
                        " socket-binding=\"haproxy-socket-1\"" +
                        "/>" +
                        "<server name=\"x2\" source=\"x2\"/>" +
                        "</subsystem>";
        List<ModelNode> operations = super.parse(subsystemXml);

        ///Check that we have the expected number of operations
        Assert.assertEquals(3, operations.size());

        //Check that each operation has the correct content
        int cnt = 0;
        for (ModelNode operation : operations) {
            Assert.assertEquals(ADD, operation.get(OP).asString());
            PathAddress addr = PathAddress.pathAddress(operation.get(OP_ADDR));

            PathElement element = addr.getElement(0);
            Assert.assertEquals(SUBSYSTEM, element.getKey());
            Assert.assertEquals(SubsystemExtension.SUBSYSTEM_NAME, element.getValue());

            if (cnt == 0) {
                Assert.assertEquals(1, addr.size());
            } else {
                Assert.assertEquals(2, addr.size());
                element = addr.getElement(1);
                Assert.assertEquals(SubsystemExtension.SERVER, element.getKey());
                Assert.assertEquals("x" + cnt, element.getValue());
            }

            cnt++;
        }
    }

    /**
     * Test that the model created from the xml looks as expected
     */
    @Test
    public void testInstallIntoController() throws Exception {
        //Parse the subsystem xml and install into the controller
        String subsystemXml =
                "<subsystem xmlns=\"" + SubsystemExtension.NAMESPACE + "\">" +
//                        "<server/>" +
                        "</subsystem>";
        KernelServices services = super.createKernelServicesBuilder(null).setSubsystemXml(subsystemXml).build();

        //Read the whole model and make sure it looks as expected
        ModelNode model = services.readWholeModel();
        Assert.assertTrue(model.get(SUBSYSTEM).hasDefined(SubsystemExtension.SUBSYSTEM_NAME));
    }

    /**
     * Starts a controller with a given subsystem xml and then checks that a second
     * controller started with the xml marshalled from the first one results in the same model
     */
    @Test
    public void testParseAndMarshalModel() throws Exception {
        //Parse the subsystem xml and install into the first controller
        String subsystemXml =
                "<subsystem xmlns=\"" + SubsystemExtension.NAMESPACE + "\">" +
                        "<server name=\"x1\" source=\"/tmp/wildfly-status-haproxy\"" +
                        " thread-pool-size=\"1001\"" +
                        " socket-binding=\"haproxy-socket-1\"" +
                        ">" +
                        "</server>" +
                        "</subsystem>";
        KernelServices servicesA = super.createKernelServicesBuilder(null).setSubsystemXml(subsystemXml).build();
        //Get the model and the persisted xml from the first controller
        ModelNode modelA = servicesA.readWholeModel();
        String marshalled = servicesA.getPersistedSubsystemXml();

        //Install the persisted xml from the first controller into a second controller
        KernelServices servicesB = super.createKernelServicesBuilder(null).setSubsystemXml(marshalled).build();
        ModelNode modelB = servicesB.readWholeModel();

        //Make sure the models from the two controllers are identical
        super.compare(modelA, modelB);
    }

    /**
     * Tests that the subsystem can be removed
     */
    @Test
    public void testSubsystemRemoval() throws Exception {
        //Parse the subsystem xml and install into the first controller
        String subsystemXml =
                "<subsystem xmlns=\"" + SubsystemExtension.NAMESPACE + "\">" +
                        "</subsystem>";
        KernelServices services = super.createKernelServicesBuilder(null).setSubsystemXml(subsystemXml).build();
        //Checks that the subsystem was removed from the model
        super.assertRemoveSubsystemResources(services);

        //TODO Chek that any services that were installed were removed here
    }
}
