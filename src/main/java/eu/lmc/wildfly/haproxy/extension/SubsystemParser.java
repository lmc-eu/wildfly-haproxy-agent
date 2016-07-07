package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.util.Collections;
import java.util.List;

import static eu.lmc.wildfly.haproxy.extension.SubsystemExtension.SUBSYSTEM_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 */
class SubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    /**
     * {@inheritDoc}
     */
    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
//        ParseUtils.requireNoContent(reader);
        list.add(createAddSubsystemOperation());
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            if (!reader.getLocalName().equals(SERVER)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            readServerType(reader, list);
        }
    }

    private static ModelNode createAddSubsystemOperation() {
        final ModelNode subsystem = new ModelNode();
        subsystem.get(OP).set(ADD);
        subsystem.get(OP_ADDR).add(SUBSYSTEM, SubsystemExtension.SUBSYSTEM_NAME);
        return subsystem;
    }

    private void readServerType(final XMLExtendedStreamReader reader, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode addServerOperation = new ModelNode();
        addServerOperation.get(OP).set(ADD);

        final int count = reader.getAttributeCount();
        String file = null;
        for (int i = 0; i < count; i++) {
            final String name = reader.getAttributeLocalName(i);
            final String value = reader.getAttributeValue(i);
            switch (ServerDefinition.Element.findByXmlName(name)) {
                case SOCKET_BINDING:
                    ServerDefinition.SOCKET_BINDING_ATTR.parseAndSetParameter(value, addServerOperation, reader);
                    break;
                case THREAD_FACTORY:
                    ServerDefinition.THREAD_FACTORY_ATTR.parseAndSetParameter(value, addServerOperation, reader);
                    break;
                case SOURCE:
                    ServerDefinition.FILE_ATTR.parseAndSetParameter(value, addServerOperation, reader);
                    file = value;
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        ParseUtils.requireNoContent(reader);

        if (file == null) {
            throw ParseUtils.missingRequiredElement(reader, Collections.singleton(ServerDefinition.Element.SOURCE.getXmlName()));
        }
        PathAddress addr = PathAddress.pathAddress(SUBSYSTEM_PATH, PathElement.pathElement(SERVER, file));
        addServerOperation.get(OP_ADDR).set(addr.toModelNode());

        list.add(addServerOperation);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(SubsystemExtension.NAMESPACE, false);
        final ModelNode node = context.getModelNode();
        final ModelNode server = node.get(SERVER);
        if (server.isDefined()) {
            for (Property property : server.asPropertyList()) {
                writer.writeStartElement(SERVER);
                writer.writeAttribute(ServerDefinition.Element.SOURCE.getXmlName(), property.getName());
                final ModelNode entry = property.getValue();
                ServerDefinition.SOCKET_BINDING_ATTR.marshallAsAttribute(entry, true, writer);
                ServerDefinition.THREAD_FACTORY_ATTR.marshallAsAttribute(entry, true, writer);
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

}
