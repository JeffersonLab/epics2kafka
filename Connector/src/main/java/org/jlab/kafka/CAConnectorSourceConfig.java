package org.jlab.kafka;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CAConnectorSourceConfig extends AbstractConfig {
    public CAConnectorSourceConfig(Map originals) {
        super(configDef(), originals);
    }

    protected static ConfigDef configDef() {
        return new ConfigDef()
                .define(CAConnectorSource.EPICS_CA_ADDR_LIST_CONFIG,
                        ConfigDef.Type.STRING,
                        ConfigDef.Importance.HIGH,
                        "List of CA Addresses")
                .define(CAConnectorSource.PVS_LIST_CONFIG,
                        ConfigDef.Type.STRING,
                        ConfigDef.Importance.HIGH,
                        "List of PV Names");
    }
}
