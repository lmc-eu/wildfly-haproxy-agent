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
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

import java.util.HashMap;
import java.util.Map;

public class ServerDefinition extends SimpleResourceDefinition {

    public enum Element {
        UNKNOWN(null),
        SOCKET_BINDING("socket-binding"),
        THREAD_FACTORY("thread-factory"),
        FILE("file");

        private final String name;

        Element(final String name) {
            this.name = name;
        }

        public String localName() {
            return name;
        }

        private static final Map<String, Element> MAP;

        static {
            final Map<String, Element> map = new HashMap<>();
            for (Element element : values()) {
                final String name = element.localName();
                if (name != null) map.put(name, element);
            }
            MAP = map;
        }

        public static Element of(final String localName) {
            final Element element = MAP.get(localName);
            return element == null ? UNKNOWN : element;
        }

    }

    protected static final SimpleAttributeDefinition SOCKET_BINDING_ATTR = new SimpleAttributeDefinition(
            Element.SOCKET_BINDING.localName(), ModelType.STRING, false);
    protected static final SimpleAttributeDefinition THREAD_FACTORY_ATTR = new SimpleAttributeDefinition(
            Element.THREAD_FACTORY.localName(), ModelType.STRING, true);
    protected static final SimpleAttributeDefinition FILE_ATTR = new SimpleAttributeDefinition(
            Element.FILE.localName(), ModelType.STRING, true);

    public static final ServerDefinition INSTANCE = new ServerDefinition();

    private ServerDefinition() {
        super(SubsystemExtension.SERVER_PATH,
                SubsystemExtension.getResourceDescriptionResolver(SubsystemExtension.SERVER),
                ServerAddHandler.INSTANCE,
                ServerRemoveHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING_ATTR, null, DummyHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(THREAD_FACTORY_ATTR, null, DummyHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(FILE_ATTR, null, DummyHandler.INSTANCE);
    }
}
