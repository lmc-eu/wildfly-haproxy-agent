/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.lmc.wildfly.haproxy.extension;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerDefinition extends SimpleResourceDefinition {

    public static final int DEFAULT_PORT = 11990;

    public enum Element {
        UNKNOWN(null),
        SOCKET_BINDING("socket-binding"),
        WORKER("worker"),
        SOURCE("source"),
        NAME("name");

        private final String xmlName;

        Element(final String xmlName) {
            this.xmlName = xmlName;
        }

        public String getXmlName() {
            return xmlName;
        }

        private static final Map<String, Element> MAP =
                Stream.of(values()).collect(Collectors.toMap(Element::getXmlName, e -> e));

        public static Element findByXmlName(final String xmlName) {
            return MAP.getOrDefault(xmlName, UNKNOWN);
        }

    }

    protected static final SimpleAttributeDefinition SOCKET_BINDING_ATTR = new SimpleAttributeDefinitionBuilder(
            Element.SOCKET_BINDING.getXmlName(), ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(false)
            .build();
    protected static final SimpleAttributeDefinition WORKER_ATTR = new SimpleAttributeDefinitionBuilder(
            Element.WORKER.getXmlName(), ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(false)
            .build();
    protected static final SimpleAttributeDefinition SOURCE_ATTR = new SimpleAttributeDefinitionBuilder(
            Element.SOURCE.getXmlName(), ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .build();
    protected static final SimpleAttributeDefinition NAME_ATTR = new SimpleAttributeDefinitionBuilder(
            Element.NAME.getXmlName(), ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(false)
            .build();

    public static final ServerDefinition INSTANCE = new ServerDefinition();

    private ServerDefinition() {
        super(SubsystemExtension.SERVER_PATH,
                SubsystemExtension.getResourceDescriptionResolver(SubsystemExtension.SERVER),
                ServerAddHandler.INSTANCE,
                ServerRemoveHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(WORKER_ATTR, null, DummyHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING_ATTR, null, DummyHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SOURCE_ATTR, null, DummyHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(NAME_ATTR, null, DummyHandler.INSTANCE);
    }
}
