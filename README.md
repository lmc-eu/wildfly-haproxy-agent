# wildfly-haproxy-agent
wildfly module that provides port for haxproxy agent-check

This module opens TCP port(s) for listening and server few bytes of content, completely ingoring any received data.
For details about this "protocol", see haproxy documentation (http://www.haproxy.org/download/1.5/doc/configuration.txt)
and search for `agent-check`

Several ports can be open, each one serving content from different source. Source can be either http URL or file on local
filesystem (recommended). If the source is not URL and no such file exists, static value `ready` is sent.

## installation
1. build and copy module into wildfly server
  ```shell
  mvn clean package && rsync -avz target/module/ $WILDFLY_HOME/modules/system/layers/base/
  
  ```
2. add extension module to `standalone.xml`
  ```xml
  <extension module="eu.lmc.wildfly.haproxy-agent"/>

  ```
  and subsystem configuration
  ```xml
  <subsystem xmlns="urn:eu.lmc:haproxy-agent:1.0">
  </subsystem>
  
  ```
  and check that the server starts without error
2. define new port binding (inside element `socket-binding-group`)
  ```xml
        <socket-binding name="haproxy-socket-A" port="11990"/>
  
  ```
2. define new XNIO worker (effectively thread pool) in subsystem 
  ```xml
        <worker name="haproxy-agent" task-max-threads="3" io-threads="3">
                <!-- worker used for haproxy agent -->
        </worker>
  
  ```
4. and define this port with source (file in this example)
  ```xml
        <subsystem xmlns="urn:eu.lmc:haproxy-agent:1.0">
            <server name="primary" source="/srv/wildfly/wildfly-status-haproxy"
                    worker="haproxy-agent"
                    socket-binding="haproxy-socket-A"
            />
        </subsystem>

  ```
